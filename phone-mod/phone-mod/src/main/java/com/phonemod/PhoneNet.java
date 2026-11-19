package com.phonemod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.*;
import java.util.function.Supplier;

/**
 * Phone messaging, photos, duels, shared sounds and music over normal /msg whispers (one server only).
 * Every phone whisper starts with two invisible characters (MARK). Whispers that start with MARK are
 * hidden from chat and handled here. Only players who answer a "ping" (i.e. have this mod) become contacts.
 * Servers kick for chat spam above about 1 message per second, so the outbox sends at most one every 1.1 s.
 */
final class PhoneNet {
    static final String MARK = "\u2062\u2063";
    static final String MARK_PLAIN = "~pm~";
    static final int CHUNK = 190;   // photo characters per whisper (7 whispers per photo)

    /** A two-player game that can receive moves from a friend. */
    interface NetPlay {
        void remoteMove(String s);
        void remoteResign();
        void remoteRematch();
    }

    record Msg(boolean mine, String text, Photo photo) {
        Msg(boolean mine, String text) { this(mine, text, null); }
    }

    // games you can duel in: id char, full name, short button label
    static final char[] GAME_IDS = {'C', 'T', '4', 'K', 'P'};
    static final String[] GAME_NAMES = {"Chess", "Tic Tac Toe", "Connect 4", "Checkers", "Ping Pong"};
    static final String[] GAME_LABELS = {"Chess", "TicTac", "Conn4", "Chkrs", "Pong"};

    static final Set<String> contacts = new LinkedHashSet<>();
    static final Map<String, List<Msg>> threads = new HashMap<>();
    static final Map<String, Integer> unread = new HashMap<>();
    static final Map<String, NetPlay> games = new HashMap<>();          // key(opponent, gameId)
    static final Set<String> invitesIn = new HashSet<>(), invitesOut = new HashSet<>();
    private static final Map<String, String[]> photoParts = new HashMap<>();

    private static final Deque<Supplier<String>> outbox = new ArrayDeque<>();
    /** tests can replace these */
    static Supplier<String> meName = () -> { Minecraft mc = Minecraft.getInstance(); return mc.player == null ? "Me" : mc.player.getGameProfile().name(); };
    static Supplier<List<String>> online = PhoneNet::mcOnline;
    private static long lastSend;

    static String key(String opponent, char gid) { return opponent + "|" + gid; }

    static void init() {
        // phone whispers are caught whether the server shows them as chat or as a system message
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, ts) -> !handle(message));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !handle(message));
        ClientTickEvents.END_CLIENT_TICK.register(PhoneNet::tick);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { MusicEngine.tick(System.nanoTime()); Calls.tick(); });
        Scores.installExitHook();
    }

    private static String str(Object o) { return o instanceof Component c ? c.getString() : String.valueOf(o); }

    /** the invisible marker (or a plain one in custom-server mode) that starts every phone whisper */
    static String marker() { return Settings.customMode ? MARK_PLAIN : MARK; }

    /**
     * Looks at ANY chat or system message. Phone whispers carry "marker + senderName | type + body", so they
     * work with vanilla /msg and with the custom whisper formats of plugin servers. Returns true if the
     * message was one of ours (it is then hidden from chat).
     */
    static boolean handle(Component msg) { return handleText(msg.getString()); }

    static boolean handleText(String text) {
        int i = text.indexOf(MARK), len = MARK.length();
        int j = text.indexOf(MARK_PLAIN);
        if (i < 0 || (j >= 0 && j < i)) { i = j; len = MARK_PLAIN.length(); }
        if (i < 0) return false;
        String payload = text.substring(i + len);
        int bar = payload.indexOf('|');
        if (bar < 1 || bar > 16 || bar + 1 >= payload.length()) return true;         // ours but malformed: hide, ignore
        String claimed = payload.substring(0, bar), body = payload.substring(bar + 1);
        if (claimed.equals(myName())) return true;                                    // my own outgoing echo
        // the server wrote the sender's name before our text; if it did not, someone may be pretending
        if (!Settings.customMode && !text.substring(0, i).contains(claimed)) return true;
        receive(claimed, body);
        return true;
    }

    private static void receive(String from, String body) {
        if (body.isEmpty()) return;
        String rest = body.substring(1);
        switch (body.charAt(0)) {
            case 'P' -> { contacts.add(from); enqueue(from, 'O', ""); }
            case 'O' -> contacts.add(from);
            case 'M' -> {
                contacts.add(from);
                threads.computeIfAbsent(from, k -> new ArrayList<>()).add(new Msg(false, rest));
                unread.merge(from, 1, Integer::sum);
                notify("[Phone] " + from + ": " + rest);
                Sfx.play('p');
            }
            case 'I' -> { contacts.add(from); photoPart(from, rest); }
            case 'G' -> game(from, rest);
            case 'Z' -> { contacts.add(from); MusicEngine.onRemote(from, rest); }
            case 'T' -> { contacts.add(from); Calls.onSignal(from, rest); }
            case 'U' -> { contacts.add(from); Presence.onRemote(from, rest); }
            case 'F' -> { contacts.add(from); if (!rest.isEmpty()) Sfx.remote(from, rest.charAt(0)); }
            default -> { }
        }
    }

    private static void photoPart(String from, String rest) {
        if (rest.length() < 4) return;
        int idx = MusicEngine.B64.indexOf(rest.charAt(1)), cnt = MusicEngine.B64.indexOf(rest.charAt(2));
        if (idx < 0 || cnt <= 0 || cnt > 12 || idx >= cnt) return;
        String k = from + "#" + rest.charAt(0);
        if (photoParts.size() > 20) photoParts.clear();
        String[] parts = photoParts.computeIfAbsent(k, x -> new String[cnt]);
        if (parts.length != cnt) return;
        parts[idx] = rest.substring(3);
        for (String p : parts) if (p == null) return;
        photoParts.remove(k);
        Photo ph = Photo.decode(String.join("", parts));
        if (ph == null) return;
        threads.computeIfAbsent(from, x -> new ArrayList<>()).add(new Msg(false, "", ph));
        unread.merge(from, 1, Integer::sum);
        notify("[Phone] " + from + " sent you a photo");
        Sfx.play('p');
    }

    private static String gameName(char gid) {
        for (int i = 0; i < GAME_IDS.length; i++) if (GAME_IDS[i] == gid) return GAME_NAMES[i];
        return "a game";
    }

    private static void game(String from, String rest) {
        if (rest.length() < 2) return;
        char gid = rest.charAt(0), sub = rest.charAt(1);
        String payload = rest.substring(2), k = key(from, gid);
        contacts.add(from);
        switch (sub) {
            case 'N' -> { invitesIn.add(k); notify("[Phone] " + from + " challenged you to " + gameName(gid)); Sfx.play('p'); }
            case 'A' -> {
                if (invitesOut.remove(k)) {
                    Game g = Games.create(gid, from, true);
                    if (g instanceof NetPlay np) { games.put(k, np); notify("[Phone] " + from + " accepted your " + gameName(gid) + " challenge"); Sfx.play('p'); }
                }
            }
            case 'V' -> { NetPlay g = games.get(k); if (g != null) g.remoteMove(payload); }
            case 'R' -> { NetPlay g = games.get(k); if (g != null) g.remoteResign(); }
            case 'X' -> {
                NetPlay g = games.get(k);
                if (g != null) { g.remoteRematch(); notify("[Phone] " + from + " wants a rematch in " + gameName(gid)); }
            }
            default -> { }
        }
    }

    static void notify(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(s));
    }

    // ---------- sending ----------
    static String cmd(String to, char type, String text) { return Settings.COMMANDS[Settings.cmdIndex] + " " + to + " " + marker() + myName() + "|" + type + text; }

    static void enqueue(String to, char type, String text) { final String c = cmd(to, type, text); outbox.add(() -> c); }

    /** a whisper that is built at the moment it is actually sent (return null to skip it) */
    static void enqueueLazy(Supplier<String> s) { outbox.add(s); }

    /** calls and urgent signals jump the queue (kept in the order given) */
    static void enqueueFront(String... cmds) { for (int i = cmds.length - 1; i >= 0; i--) { final String c = cmds[i]; outbox.addFirst(() -> c); } }

    static void enqueueRaw(String command) { outbox.add(() -> command); }

    static int queueSize() { return outbox.size(); }

    /** called when you join another server: nothing queued for the old one should leak into the new one */
    static void clearSession() {
        outbox.clear(); photoParts.clear(); invitesIn.clear(); invitesOut.clear(); games.clear(); unread.clear();
    }

    static String myName() { return meName.get(); }

    static List<String> onlineNames() { return online.get(); }

    private static List<String> mcOnline() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return out;
        String me = mc.player.getGameProfile().name();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) { String n = info.getProfile().name(); if (!n.equals(me)) out.add(n); }
        return out;
    }

    /** ask one player whether they have the mod; they become a contact when they answer */
    static void ping(String name) { enqueue(name, 'P', ""); }

    static void sendText(String to, String text) {
        threads.computeIfAbsent(to, k -> new ArrayList<>()).add(new Msg(true, text));
        enqueue(to, 'M', text);
    }

    /** sends a photo in 6 whispers; the receiver rebuilds it when all have arrived */
    static void sendPhoto(String to, Photo p) {
        threads.computeIfAbsent(to, k -> new ArrayList<>()).add(new Msg(true, "", p));
        String data = p.encode();
        int cnt = (data.length() + CHUNK - 1) / CHUNK;
        char id = MusicEngine.B64.charAt((int) (Math.random() * 64));
        for (int i = 0; i < cnt; i++)
            enqueue(to, 'I', "" + id + MusicEngine.B64.charAt(i) + MusicEngine.B64.charAt(cnt) + data.substring(i * CHUNK, Math.min(data.length(), (i + 1) * CHUNK)));
    }

    /** body examples: "N" invite, "A" accept, "V..." a move, "R" resign, "X" rematch */
    static void sendGame(String to, char gid, String body) { enqueue(to, 'G', "" + gid + body); }

    static int totalUnread() {
        int n = 0;
        for (int v : unread.values()) n += v;
        return n;
    }

    private static void tick(Minecraft mc) {
        if (outbox.isEmpty() || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSend < 1100) return;
        while (!outbox.isEmpty()) {
            String c = outbox.poll().get();
            if (c != null) { lastSend = now; mc.getConnection().sendCommand(c); return; }
        }
    }

    /** tests only: evaluate and clear the queue */
    static List<String> drainCommands() {
        List<String> out = new ArrayList<>();
        while (!outbox.isEmpty()) { String c = outbox.poll().get(); if (c != null) out.add(c); }
        return out;
    }
}
