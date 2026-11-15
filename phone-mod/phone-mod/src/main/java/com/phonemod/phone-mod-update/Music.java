package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A song made of Minecraft note-block notes (public-domain melodies and one original). */
final class Song {
    final String title, artist;
    final float beat, length;
    final int[] note;
    final float[] start;

    Song(String title, String artist, float beat, String score) {
        this.title = title; this.artist = artist; this.beat = beat;
        List<Integer> ns = new ArrayList<>();
        List<Float> ss = new ArrayList<>();
        float t = 0;
        for (String tok : score.trim().split("\\s+")) {
            String[] p = tok.split(":");
            float beats = p.length > 1 ? Float.parseFloat(p[1]) : 1f;
            if (!p[0].equals("-")) { ns.add(index(p[0])); ss.add(t); }
            t += beats * beat;
        }
        length = t;
        note = new int[ns.size()]; start = new float[ns.size()];
        for (int i = 0; i < note.length; i++) { note[i] = ns.get(i); start[i] = ss.get(i); }
    }

    /** note name -> note-block index (0 = F#3 ... 24 = F#5), folded into range */
    static int index(String n) {
        int semi = switch (n.charAt(0)) { case 'C' -> 0; case 'D' -> 2; case 'E' -> 4; case 'F' -> 5; case 'G' -> 7; case 'A' -> 9; default -> 11; };
        int i = 1;
        if (n.charAt(i) == '#') { semi++; i++; }
        int octave = Integer.parseInt(n.substring(i));
        int idx = 12 * (octave + 1) + semi - 54;
        while (idx < 0) idx += 12;
        while (idx > 24) idx -= 12;
        return idx;
    }
}

/** Music playback that keeps going when the phone is closed, and can be heard by nearby players with the mod. */
final class MusicEngine {
    static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    static final String[] INSTR = {"harp", "pling", "bit", "banjo", "guitar", "flute", "bell", "chime", "xylophone"};
    static final String[] INSTR_NAMES = {"Harp", "Pling", "Bit", "Banjo", "Guitar", "Flute", "Bell", "Chime", "Xylophone"};
    static final Song[] SONGS = {
        new Song("Twinkle Twinkle", "Traditional", 0.42f, "C4 C4 G4 G4 A4 A4 G4:2 F4 F4 E4 E4 D4 D4 C4:2 G4 G4 F4 F4 E4 E4 D4:2 G4 G4 F4 F4 E4 E4 D4:2 C4 C4 G4 G4 A4 A4 G4:2 F4 F4 E4 E4 D4 D4 C4:2"),
        new Song("Ode to Joy", "Beethoven", 0.4f, "E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 E4:1.5 D4:0.5 D4:2 E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 D4:1.5 C4:0.5 C4:2"),
        new Song("Mary Had a Little Lamb", "Traditional", 0.4f, "E4 D4 C4 D4 E4 E4 E4:2 D4 D4 D4:2 E4 G4 G4:2 E4 D4 C4 D4 E4 E4 E4 E4 D4 D4 E4 D4 C4:4"),
        new Song("Jingle Bells", "Pierpont", 0.32f, "E4 E4 E4:2 E4 E4 E4:2 E4 G4 C4:1.5 D4:0.5 E4:4 F4 F4 F4:1.5 F4:0.5 F4 E4 E4 E4:0.5 E4:0.5 E4 D4 D4 E4 D4:2 G4:2"),
        new Song("Happy Birthday", "Hill", 0.4f, "C4:0.75 C4:0.25 D4 C4 F4 E4:2 C4:0.75 C4:0.25 D4 C4 G4 F4:2 C4:0.75 C4:0.25 C5 A4 F4 E4 D4 A#4:0.75 A#4:0.25 A4 F4 G4 F4:2"),
        new Song("Frere Jacques", "Traditional", 0.4f, "C4 D4 E4 C4 C4 D4 E4 C4 E4 F4 G4:2 E4 F4 G4:2 G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4 C4 G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4 C4 C4 G3 C4:2 C4 G3 C4:2"),
        new Song("Amazing Grace", "Newton", 0.5f, "D4 G4:2 B4:0.5 G4:0.5 B4:2 A4 G4:2 E4 D4:2 D4 G4:2 B4:0.5 G4:0.5 B4:2 A4 D5:3 B4:3 D5 B4 G4:2 D4 E4 G4:0.5 E4:0.5 D4 D4 G4:2 B4:0.5 G4:0.5 B4:2 A4 G4:3"),
        new Song("Greensleeves", "Traditional", 0.35f, "A4 C5:2 D5 E5:1.5 F5:0.5 E5 D5:2 B4 G4:1.5 A4:0.5 B4 C5:2 A4 A4:1.5 G#4:0.5 A4 B4:2 G#4 E4:2 A4 C5:2 D5 E5:1.5 F5:0.5 E5 D5:2 B4 G4:1.5 A4:0.5 B4 C5:1.5 B4:0.5 A4 G#4:1.5 F#4:0.5 G#4 A4:3"),
        new Song("Fur Elise", "Beethoven", 0.22f, "E5 D#5 E5 D#5 E5 B4 D5 C5 A4:2 - C4 E4 A4 B4:2 - E4 G#4 B4 C5:2 - E4 E5 D#5 E5 D#5 E5 B4 D5 C5 A4:2 - C4 E4 A4 B4:2 - E4 C5 B4 A4:3"),
        new Song("Canon in C", "Pachelbel", 0.45f, "E5:2 D5:2 C5:2 B4:2 A4:2 G4:2 A4:2 B4:2 C5:2 B4:2 A4:2 G4:2 F4:2 E4:2 F4:2 G4:2 C5 B4 C5 G4 A4 B4 C5 B4 A4 G4 F4 E4 F4 E4 D4 E4 F4 G4 A4 B4 C5 B4 A4 B4 C5 B4 A4 G4:2"),
        new Song("When the Saints", "Traditional", 0.35f, "C4 E4 F4 G4:4 C4 E4 F4 G4:4 C4 E4 F4 G4:2 E4:2 C4:2 E4:2 D4:4 E4 E4 D4 C4:2 C4:2 E4 G4 G4:2 F4 - E4 F4 G4 E4 C4 D4 C4:4"),
        new Song("Pixel Parade", "Phone Mod", 0.3f, "C4 E4 G4 C5 G4 E4 C4:2 D4 F4 A4 D5 A4 F4 D4:2 E4 G4 B4 E5 B4 G4 E4:2 C5 B4 A4 G4 F4 E4 D4 C4:3")
    };

    static int song = -1, instr = 0, next;
    static boolean playing, shuffle, repeat;
    static float pos;
    static long lastNs = 0;
    private static float listenTimer;
    static final Set<String> listeners = new HashSet<>();

    static final class Remote { Song s; String instr; float startPos; long startMs; int next; }
    static final Map<String, Remote> remote = new HashMap<>();

    // ---------- controls ----------
    static void play(int s) { song = Math.floorMod(s, SONGS.length); pos = 0; next = 0; playing = true; listeners.clear(); listenTimer = 1f; }
    static void toggle() {
        if (song < 0) { play(0); return; }
        playing = !playing;
        if (playing) { listeners.clear(); listenTimer = 1f; } else stopListeners();
    }
    static void skip(int dir) {
        if (song < 0) { play(0); return; }
        play(shuffle ? (song + 1 + (int) (Math.random() * (SONGS.length - 1))) : song + dir);
    }
    static int listenerCount() { return listeners.size(); }

    private static void stopListeners() {
        for (String n : listeners) PhoneNet.enqueueLazy(() -> PhoneNet.cmd(n, 'Z', "X"));
        listeners.clear();
    }

    // ---------- clock ----------
    static void tick(long nowNs) {
        float dt = lastNs == 0 ? 0f : Math.min(0.25f, (nowNs - lastNs) / 1e9f);
        lastNs = nowNs;
        double[] me = Sfx.world.self();
        if (playing && song >= 0 && me != null) {
            Song sg = SONGS[song];
            pos += dt;
            while (next < sg.note.length && sg.start[next] <= pos) { Sfx.note(INSTR[instr], sg.note[next], 0.9f, me); next++; }
            if (pos >= sg.length + 0.6f) {
                if (repeat) play(song);
                else if (shuffle) skip(1);
                else if (song + 1 < SONGS.length) play(song + 1);
                else { playing = false; stopListeners(); }
            }
            listenTimer += dt;
            if (listenTimer >= 1f) { listenTimer = 0; updateListeners(); }
        }
        tickRemote();
    }

    private static void updateListeners() {
        if (!playing || !Settings.nearby || !Settings.sound) { listeners.clear(); return; }
        Set<String> now = new HashSet<>();
        for (Sfx.Near n : Sfx.nearby(24, 5)) now.add(n.name());
        for (String n : now) if (!listeners.contains(n)) {
            final int s = song;
            PhoneNet.enqueueLazy(() -> playing && song == s ? PhoneNet.cmd(n, 'Z', "S" + B64.charAt(s) + B64.charAt(instr) + enc2((int) (pos * 10))) : null);
        }
        listeners.retainAll(now);
        listeners.addAll(now);
    }

    static String enc2(int v) { v = Math.max(0, Math.min(4095, v)); return "" + B64.charAt(v / 64) + B64.charAt(v % 64); }

    // ---------- other players' music ----------
    /** body: "S" song instr off1 off2   or   "X" */
    static void onRemote(String from, String body) {
        if (body.isEmpty()) return;
        if (body.charAt(0) == 'X') { remote.remove(from); return; }
        if (body.charAt(0) != 'S' || body.length() < 5 || !Settings.nearby || !Settings.sound) return;
        int s = B64.indexOf(body.charAt(1)), in = B64.indexOf(body.charAt(2));
        int o1 = B64.indexOf(body.charAt(3)), o2 = B64.indexOf(body.charAt(4));
        if (s < 0 || s >= SONGS.length || in < 0 || in >= INSTR.length || o1 < 0 || o2 < 0) return;
        if (remote.size() >= 3 && !remote.containsKey(from)) return;
        Sfx.Near n = Sfx.find(from);
        if (n == null || n.dist() > 32) return;
        Remote r = new Remote();
        r.s = SONGS[s]; r.instr = INSTR[in]; r.startPos = (o1 * 64 + o2) / 10f; r.startMs = System.currentTimeMillis();
        while (r.next < r.s.note.length && r.s.start[r.next] < r.startPos) r.next++;
        remote.put(from, r);
    }

    static void tickRemote() {
        if (remote.isEmpty()) return;
        long now = System.currentTimeMillis();
        var it = remote.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Remote r = e.getValue();
            Sfx.Near n = Sfx.find(e.getKey());
            float p = r.startPos + (now - r.startMs) / 1000f;
            if (n == null || n.dist() > 40 || !Settings.nearby || !Settings.sound || p > r.s.length + 1.5f) { it.remove(); continue; }
            double[] at = {n.x(), n.y(), n.z()};
            while (r.next < r.s.note.length && r.s.start[r.next] <= p) { Sfx.note(r.instr, r.s.note[r.next], 0.9f, at); r.next++; }
        }
    }
}

/** Spotify-style music app. */
final class MusicApp extends Game {
    int top;

    MusicApp() { super("music", "Music", "MUS", 0xFF1DB954); resetOnOpen = false; }

    @Override void icon(GuiGraphicsExtractor g, int x, int y) {
        box(g, x, y, x + 30, y + 30, 0xFF191414);
        box(g, x + 4, y + 8, x + 26, y + 11, 0xFF1DB954); box(g, x + 6, y + 14, x + 24, y + 17, 0xFF1DB954); box(g, x + 8, y + 20, x + 22, y + 23, 0xFF1DB954);
    }

    private static String time(float s) { int t = (int) s; return (t / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60); }

    @Override void press(float x, float y) {
        if (in(x, y, 4, 74, 22, 94)) { MusicEngine.shuffle = !MusicEngine.shuffle; Sfx.play('c'); }
        else if (in(x, y, 26, 74, 44, 94)) { MusicEngine.skip(-1); Sfx.play('c'); }
        else if (in(x, y, 48, 74, 70, 94)) { MusicEngine.toggle(); Sfx.play('c'); }
        else if (in(x, y, 74, 74, 92, 94)) { MusicEngine.skip(1); Sfx.play('c'); }
        else if (in(x, y, 96, 74, 114, 94)) { MusicEngine.repeat = !MusicEngine.repeat; Sfx.play('c'); }
        else if (in(x, y, 4, 100, 22, 114)) { MusicEngine.instr = Math.floorMod(MusicEngine.instr - 1, MusicEngine.INSTR.length); Sfx.play('c'); }
        else if (in(x, y, 96, 100, 114, 114)) { MusicEngine.instr = (MusicEngine.instr + 1) % MusicEngine.INSTR.length; Sfx.play('c'); }
        else if (in(x, y, 98, 120, 114, 158)) top = Math.max(0, top - 1);
        else if (in(x, y, 98, 162, 114, 200)) top = Math.min(MusicEngine.SONGS.length - 4, top + 1);
        else for (int k = 0; k < 4; k++) if (in(x, y, 4, 120 + k * 20, 96, 138 + k * 20)) { MusicEngine.play(top + k); Sfx.play('c'); }
    }

    private void btn(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, String t, boolean on) {
        box(g, x0, y0, x1, y1, on ? 0xFF1DB954 : 0xFF2A2F3A);
        ctr(g, t, (x0 + x1) / 2, (y0 + y1) / 2 - 3, WHITE, 0.9f);
    }

    @Override void draw(GuiGraphicsExtractor g) {
        g.fillGradient(PhoneScreen.SX, PhoneScreen.SY, PhoneScreen.SX + W, PhoneScreen.SY + H, 0xFF191414, 0xFF0B0B0B);
        left(g, "Music", 6, 5, 0xFF1DB954, 1.3f);
        String share = !Settings.nearby || !Settings.sound ? "Nearby sharing off" : "Nearby: " + MusicEngine.listenerCount() + " listening";
        left(g, share, 6, 18, GRAY, 0.75f);

        box(g, 4, 28, 114, 70, 0xFF1E2430);
        Song s = MusicEngine.song >= 0 ? MusicEngine.SONGS[MusicEngine.song] : null;
        String title = s == null ? "Pick a song" : s.title;
        txt(g, title.length() > 17 ? title.substring(0, 17) : title, 8, 32, WHITE);
        left(g, s == null ? "Minecraft note blocks" : s.artist + "  -  " + MusicEngine.INSTR_NAMES[MusicEngine.instr], 8, 44, GRAY, 0.75f);
        float frac = s == null ? 0 : clamp(MusicEngine.pos / s.length, 0, 1);
        box(g, 8, 56, 106, 59, 0xFF3A404E); box(g, 8, 56, 8 + 98 * frac, 59, 0xFF1DB954);
        left(g, time(s == null ? 0 : Math.min(MusicEngine.pos, s.length)), 8, 61, GRAY, 0.65f);
        left(g, time(s == null ? 0 : s.length), 92, 61, GRAY, 0.65f);

        btn(g, 4, 74, 22, 94, "Shf", MusicEngine.shuffle);
        btn(g, 26, 74, 44, 94, "|<", false);
        btn(g, 48, 74, 70, 94, MusicEngine.playing ? "II" : ">", true);
        btn(g, 74, 74, 92, 94, ">|", false);
        btn(g, 96, 74, 114, 94, "Rep", MusicEngine.repeat);

        btn(g, 4, 100, 22, 114, "<", false); btn(g, 96, 100, 114, 114, ">", false);
        ctr(g, MusicEngine.INSTR_NAMES[MusicEngine.instr], W / 2, 103, WHITE, 1f);

        for (int k = 0; k < 4; k++) {
            int idx = top + k;
            if (idx >= MusicEngine.SONGS.length) break;
            boolean cur = idx == MusicEngine.song;
            box(g, 4, 120 + k * 20, 96, 138 + k * 20, cur ? 0xFF1B4D2E : 0xFF1E2430);
            Song sg = MusicEngine.SONGS[idx];
            txt(g, sg.title.length() > 14 ? sg.title.substring(0, 14) : sg.title, 8, 123 + k * 20, cur ? 0xFF1DB954 : WHITE);
            left(g, sg.artist, 8, 132 + k * 20, GRAY, 0.6f);
        }
        btn(g, 98, 120, 114, 158, "^", false); btn(g, 98, 162, 114, 200, "v", false);
    }
}
