package com.phonemod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.*;

/**
 * Phone messaging and duels over normal /msg whispers. Every phone whisper starts with two invisible
 * characters (MARK). Whispers that start with MARK are hidden from chat and handled here.
 * Only players who answer a "ping" (i.e. have this mod) ever appear as contacts.
 */
final class PhoneNet {
    static final String MARK = "\u2062\u2063";

    /** A two-player game that can receive moves from a friend. */
    interface NetPlay {
        void remoteMove(String s);
        void remoteResign();
        void remoteRematch();
    }

    record Msg(boolean mine, String text) { }

    // games you can duel in: id char, full name, short button label
    static final char[] GAME_IDS = {'C', 'T', '4', 'K'};
    static final String[] GAME_NAMES = {"Chess", "Tic Tac Toe", "Connect 4", "Checkers"};
    static final String[] GAME_LABELS = {"Chess", "TicTac", "Conn 4", "Chkrs"};

    static final Set<String> contacts = new LinkedHashSet<>();
    static final Map<String, List<Msg>> threads = new HashMap<>();
    static final Map<String, Integer> unread = new HashMap<>();
    static final Map<String, NetPlay> games = new HashMap<>();          // key(opponent, gameId)
    static final Set<String> invitesIn = new HashSet<>(), invitesOut = new HashSet<>();

    private static final Deque<String> outbox = new ArrayDeque<>();
    private static long lastSend;

    static String key(String opponent, char gid) { return opponent + "|" + gid; }

    static void init() {
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, ts) -> !handle(message));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !handle(message));
        ClientTickEvents.END_CLIENT_TICK.register(PhoneNet::tick);
    }

    private static String str(Object o) { return o instanceof Component c ? c.getString() : String.valueOf(o); }

    /** @return true if this was one of our phone whispers (so it should be hidden from chat) */
    private static boolean handle(Component msg) {
        if (!(msg.getContents() instanceof TranslatableContents tc)) return false;
        String key = tc.getKey();
        boolean incoming = key.equals("commands.message.display.incoming");
        boolean outgoing = key.equals("commands.message.display.outgoing");
        if (!incoming && !outgoing) return false;
        Object[] a = tc.getArgs();
        if (a.length < 2) return false;
        String text = str(a[1]);
        if (!text.startsWith(MARK)) return false;
        if (incoming) receive(str(a[0]), text.substring(MARK.length()));
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
            }
            case 'G' -> game(from, rest);
            default -> { }
        }
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
            case 'N' -> { invitesIn.add(k); notify("[Phone] " + from + " challenged you to " + gameName(gid)); }
            case 'A' -> {
                if (invitesOut.remove(k)) {
                    Game g = Games.create(gid, from, true);
                    if (g instanceof NetPlay np) { games.put(k, np); notify("[Phone] " + from + " accepted your " + gameName(gid) + " challenge"); }
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

    private static void notify(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(s));
    }

    // ---------- sending ----------
    static void enqueue(String to, char type, String text) {
        outbox.add("msg " + to + " " + MARK + type + text);
    }

    static void sendText(String to, String text) {
        threads.computeIfAbsent(to, k -> new ArrayList<>()).add(new Msg(true, text));
        enqueue(to, 'M', text);
    }

    /** body examples: "N" invite, "A" accept, "V..." a move, "R" resign, "X" rematch */
    static void sendGame(String to, char gid, String body) { enqueue(to, 'G', "" + gid + body); }

    /** Pings everyone online; only players with the mod answer, and they become contacts. */
    static int scan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return 0;
        String me = mc.player.getGameProfile().name();
        int n = 0;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (!name.equals(me)) { enqueue(name, 'P', ""); n++; }
        }
        return n;
    }

    static int totalUnread() {
        int n = 0;
        for (int v : unread.values()) n += v;
        return n;
    }

    private static void tick(Minecraft mc) {
        if (outbox.isEmpty() || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSend < 700) return;
        lastSend = now;
        mc.getConnection().sendCommand(outbox.poll());
    }
}
