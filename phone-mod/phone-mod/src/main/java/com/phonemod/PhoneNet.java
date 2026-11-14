package com.phonemod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.*;

/**
 * Phone messaging over normal /msg whispers. Every phone message starts with two invisible
 * characters (MARK). Whispers that start with MARK are hidden from chat and shown on the phone.
 * Only players who answer a "ping" (i.e. have this mod) ever appear as contacts.
 */
final class PhoneNet {
    static final String MARK = "\u2062\u2063";

    record Msg(boolean mine, String text) { }

    static final Set<String> contacts = new LinkedHashSet<>();
    static final Map<String, List<Msg>> threads = new HashMap<>();
    static final Map<String, Integer> unread = new HashMap<>();
    static final Map<String, ChessGame> games = new HashMap<>();
    static final Set<String> invitesIn = new HashSet<>(), invitesOut = new HashSet<>();

    private static final Deque<String> outbox = new ArrayDeque<>();
    private static long lastSend;

    static void init() {
        // hide + process phone whispers (vanilla /msg style)
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, ts) -> !handle(message));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !handle(message));
        // send queued whispers slowly so servers don't flag command spam
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
            case 'C' -> chess(from, rest);
            default -> { }
        }
    }

    private static void chess(String from, String rest) {
        if (rest.isEmpty()) return;
        contacts.add(from);
        switch (rest.charAt(0)) {
            case 'N' -> { invitesIn.add(from); notify("[Phone] " + from + " challenged you to chess"); }
            case 'A' -> {
                if (invitesOut.remove(from)) {
                    games.put(from, new ChessGame(from, true));
                    notify("[Phone] " + from + " accepted your chess challenge");
                }
            }
            case 'V' -> { ChessGame g = games.get(from); if (g != null) g.remoteMove(rest.substring(1)); }
            case 'R' -> { ChessGame g = games.get(from); if (g != null) g.remoteResign(); }
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

    static void sendChess(String to, String body) { enqueue(to, 'C', body); }

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
