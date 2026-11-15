package com.phonemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The Messages app: contact list (mod users only) and chat threads. */
final class MessagesApp {
    String open;                 // null = contact list, else the contact being chatted with
    Game pendingGame;            // set when the user taps into a duel; PhoneScreen picks it up
    private String input = "";
    private long scanUntil;
    private int scanCount;

    // ---------- drawing ----------
    void draw(GuiGraphicsExtractor g) {
        g.fillGradient(PhoneScreen.SX, PhoneScreen.SY, PhoneScreen.SX + PhoneScreen.SW,
                PhoneScreen.SY + PhoneScreen.SH, 0xFF101218, 0xFF1A1D26);
        if (open == null) drawList(g); else drawThread(g);
    }

    private void drawList(GuiGraphicsExtractor g) {
        PhoneScreen.left(g, "Messages", 6, 6, 0xFFFFFFFF, 1.3f);
        PhoneScreen.box(g, 78, 4, 114, 19, 0xFF2E86DE);
        PhoneScreen.ctr(g, "Scan", 96, 8, 0xFFFFFFFF, 1f);

        if (PhoneNet.contacts.isEmpty()) {
            PhoneScreen.ctr(g, "No friends yet.", PhoneScreen.SW / 2, 70, 0xFFFFFFFF, 1f);
            PhoneScreen.ctr(g, "Tap Scan to find players", PhoneScreen.SW / 2, 86, 0xFFAAAAAA, 0.8f);
            PhoneScreen.ctr(g, "who have the phone mod.", PhoneScreen.SW / 2, 97, 0xFFAAAAAA, 0.8f);
        }
        int i = 0;
        for (String name : PhoneNet.contacts) {
            if (i >= 8) break;
            int y = 26 + i * 22;
            PhoneScreen.box(g, 4, y, 114, y + 20, 0xFF222633);
            PhoneScreen.txt(g, name, 8, y + 3, 0xFFFFFFFF);
            List<PhoneNet.Msg> t = PhoneNet.threads.get(name);
            String last = (t == null || t.isEmpty()) ? "" : (t.get(t.size() - 1).photo() != null ? "[photo]" : t.get(t.size() - 1).text());
            if (last.length() > 28) last = last.substring(0, 28) + "...";
            PhoneScreen.left(g, last, 8, y + 12, 0xFF8A8F9E, 0.7f);
            int un = PhoneNet.unread.getOrDefault(name, 0);
            if (un > 0) {
                PhoneScreen.box(g, 98, y + 4, 110, y + 16, 0xFFE53935);
                PhoneScreen.ctr(g, String.valueOf(Math.min(un, 9)), 104, y + 6, 0xFFFFFFFF, 0.9f);
            }
            i++;
        }
        if (System.currentTimeMillis() < scanUntil)
            PhoneScreen.ctr(g, "Pinged " + scanCount + " player(s)...", PhoneScreen.SW / 2, 204, 0xFF8FD6DD, 0.8f);
    }

    private void drawThread(GuiGraphicsExtractor g) {
        PhoneNet.unread.remove(open);
        var font = Minecraft.getInstance().font;
        List<PhoneNet.Msg> list = PhoneNet.threads.getOrDefault(open, List.of());

        int y = 158;
        for (int i = list.size() - 1; i >= 0 && y > 26; i--) {
            PhoneNet.Msg m = list.get(i);
            if (m.photo() != null) {
                int pbw = 72, pbh = 88;
                y -= pbh + 3;
                int px = m.mine() ? PhoneScreen.SW - 4 - pbw : 4;
                PhoneScreen.box(g, px, y, px + pbw, y + pbh, m.mine() ? 0xFF2E86DE : 0xFF3A3F4F);
                m.photo().draw(g, px + 4, y + 4, 2);
                continue;
            }
            List<String> lines = wrap(m.text(), 78);
            int w = 0;
            for (String l : lines) w = Math.max(w, font.width(l));
            int bw = w + 8, bh = lines.size() * 9 + 5;
            y -= bh + 3;
            int x = m.mine() ? PhoneScreen.SW - 4 - bw : 4;
            PhoneScreen.box(g, x, y, x + bw, y + bh, m.mine() ? 0xFF2E86DE : 0xFF3A3F4F);
            for (int k = 0; k < lines.size(); k++)
                PhoneScreen.txt(g, lines.get(k), x + 4, y + 3 + k * 9, 0xFFFFFFFF);
        }

        // header (drawn last so old bubbles slide under it)
        PhoneScreen.box(g, 0, 0, PhoneScreen.SW, 22, 0xFF1F2330);
        PhoneScreen.box(g, 4, 4, 18, 18, 0xFF333847);
        PhoneScreen.txt(g, "<", 9, 6, 0xFFFFFFFF);
        PhoneScreen.txt(g, open, 24, 7, 0xFFFFFFFF);

        // duel buttons: green = open game, gold = they challenged you, gray = waiting, blue = challenge them
        for (int i = 0; i < PhoneNet.GAME_IDS.length; i++) {
            String k = PhoneNet.key(open, PhoneNet.GAME_IDS[i]);
            int col = PhoneNet.games.containsKey(k) ? 0xFF3F6E3F : PhoneNet.invitesIn.contains(k) ? 0xFFB8860B
                    : PhoneNet.invitesOut.contains(k) ? 0xFF555555 : 0xFF2E5C9E;
            PhoneScreen.box(g, 4 + i * 28, 162, 4 + i * 28 + 26, 176, col);
            PhoneScreen.ctr(g, PhoneNet.GAME_LABELS[i], 4 + i * 28 + 13, 166, 0xFFFFFFFF, 0.65f);
        }

        // input row
        PhoneScreen.box(g, 4, 180, 86, 196, 0xFF222633);
        String shown = input;
        while (shown.length() > 0 && font.width(shown + "_") > 76) shown = shown.substring(1);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        PhoneScreen.txt(g, shown + (blink ? "_" : ""), 7, 184, 0xFFFFFFFF);
        PhoneScreen.box(g, 88, 180, 114, 196, 0xFF2E86DE);
        PhoneScreen.txt(g, "Send", 91, 184, 0xFFFFFFFF);
        PhoneScreen.ctr(g, "Duel: tap a game  |  Esc = back", PhoneScreen.SW / 2, 201, 0xFF6F7585, 0.7f);
    }

    // ---------- input ----------
    void click(float x, float y) {
        Sfx.play('c');
        if (open == null) {
            if (x >= 78 && x <= 114 && y >= 4 && y <= 19) {
                scanCount = PhoneNet.scan();
                scanUntil = System.currentTimeMillis() + 4000;
                return;
            }
            int i = 0;
            for (String name : PhoneNet.contacts) {
                if (i >= 8) break;
                int ry = 26 + i * 22;
                if (x >= 4 && x <= 114 && y >= ry && y <= ry + 20) { open = name; input = ""; return; }
                i++;
            }
            return;
        }
        if (x >= 4 && x <= 18 && y >= 4 && y <= 18) { open = null; return; }
        if (x >= 88 && x <= 114 && y >= 180 && y <= 196) { send(); return; }
        if (y >= 162 && y <= 176) {
            int i = (int) ((x - 4) / 28);
            if (i >= 0 && i < PhoneNet.GAME_IDS.length && x <= 4 + i * 28 + 26) gameAction(i);
        }
    }

    private void gameAction(int i) {
        char gid = PhoneNet.GAME_IDS[i];
        String k = PhoneNet.key(open, gid);
        PhoneNet.NetPlay existing = PhoneNet.games.get(k);
        if (existing instanceof Game gm) { pendingGame = gm; return; }
        if (PhoneNet.invitesIn.remove(k)) {
            Game gm = Games.create(gid, open, false);
            if (gm instanceof PhoneNet.NetPlay np) {
                PhoneNet.games.put(k, np);
                PhoneNet.sendGame(open, gid, "A");
                pendingGame = gm;
            }
            return;
        }
        if (PhoneNet.invitesOut.add(k)) PhoneNet.sendGame(open, gid, "N");
    }

    private void send() {
        String t = input.trim();
        if (t.isEmpty()) return;
        PhoneNet.sendText(open, t);
        input = "";
    }

    /** @return true if the key was consumed (always, while a thread is open, so typing never triggers shortcuts) */
    boolean key(int k) {
        if (open == null) return false;
        if (k == GLFW.GLFW_KEY_ESCAPE) { open = null; return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { send(); return true; }
        if (k == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        return true;
    }

    void typed(String s) {
        if (open == null || s.isEmpty() || s.charAt(0) < 32 || input.length() >= 150) return;
        input += s;
    }

    private static List<String> wrap(String s, int maxW) {
        var font = Minecraft.getInstance().font;
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            while (font.width(word) > maxW) {
                int cut = word.length();
                while (cut > 1 && font.width(word.substring(0, cut)) > maxW) cut--;
                if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
                out.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            String test = line.length() == 0 ? word : line + " " + word;
            if (font.width(test) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(test);
        }
        if (line.length() > 0 || out.isEmpty()) out.add(line.toString());
        return out;
    }
}
