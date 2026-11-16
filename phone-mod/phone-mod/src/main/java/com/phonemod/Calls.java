package com.phonemod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** A tiny 16x20 picture used for slow video over chat (320 characters = 2 whispers). */
final class Frame {
    static final int W = 16, H = 20;
    final int[] px = new int[W * H];

    static Frame from(Photo p) {
        Frame f = new Frame();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) f.px[y * W + x] = p.px[(y * 2) * Photo.W + x * 2];
        return f;
    }
    String encode() {
        StringBuilder sb = new StringBuilder();
        for (int c : px) sb.append(MusicEngine.B64.charAt(Photo.code(c)));
        return sb.toString();
    }
    static Frame decode(String s) {
        if (s == null || s.length() != W * H) return null;
        Frame f = new Frame();
        for (int i = 0; i < s.length(); i++) { int c = MusicEngine.B64.indexOf(s.charAt(i)); if (c < 0) return null; f.px[i] = Photo.expand(c); }
        return f;
    }
    Photo toPhoto() {
        Photo p = new Photo();
        for (int y = 0; y < Photo.H; y++) for (int x = 0; x < Photo.W; x++) p.px[y * Photo.W + x] = px[(y / 2) * W + x / 2];
        return p;
    }
}

/**
 * Phone calls and FaceTime. Ringing is signalled over whispers. Voice uses the Simple Voice Chat mod:
 * the caller keeps a voice group named "Call-<their name>", the callee joins it with /voicechat join.
 * Video is rendered locally from the other player when they are loaded on your screen, and otherwise
 * arrives as a slow 16x20 picture every ~3.3 seconds.
 */
final class Calls {
    enum State { IDLE, OUT, IN, ACTIVE }

    static State state = State.IDLE;
    static String peer = "", peerGroup = "", note = "";
    static boolean video, callee, myFront = true, peerFront = true;
    static long since, activeSince, lastRing, lastFrame, remoteFrameMs;
    static Photo remoteFrame;
    static LongSupplier clock = System::currentTimeMillis;
    static java.util.function.BooleanSupplier svcLoaded = () -> FabricLoader.getInstance().isModLoaded("voicechat");

    private static final Map<String, String[]> frameParts = new HashMap<>();
    private static int frameSeq;

    static boolean svc() { return svcLoaded.getAsBoolean(); }
    static String myGroup() { return "Call-" + PhoneNet.myName(); }
    static boolean setupDone() { return Scores.get("call_setup") == 1; }

    // ---------- starting and ending ----------
    static void call(String name, boolean vid) {
        if (state != State.IDLE) return;
        state = State.OUT; peer = name; video = vid; callee = false; since = clock.getAsLong(); note = "";
        myFront = true; peerFront = true; remoteFrame = null; frameParts.clear(); lastRing = 0;
        PhoneNet.enqueueFront(PhoneNet.cmd(name, 'T', "R" + (vid ? 'V' : 'A') + myGroup()));
    }

    static void accept() {
        if (state != State.IN) return;
        state = State.ACTIVE; activeSince = clock.getAsLong(); lastFrame = 0; note = "";
        String ok = PhoneNet.cmd(peer, 'T', "A");
        if (svc() && !peerGroup.isEmpty()) PhoneNet.enqueueFront(ok, "voicechat join " + peerGroup);
        else PhoneNet.enqueueFront(ok);
    }

    static void decline() { hangup(); }

    static void hangup() {
        if (state == State.IDLE) return;
        String bye = PhoneNet.cmd(peer, 'T', "D");
        if (state == State.ACTIVE && callee && svc()) PhoneNet.enqueueFront(bye, "voicechat leave");
        else PhoneNet.enqueueFront(bye);
        state = State.IDLE; note = "Call ended";
    }

    static void flip() {
        myFront = !myFront;
        if (state != State.IDLE) PhoneNet.enqueueFront(PhoneNet.cmd(peer, 'T', "C" + (myFront ? 'F' : 'B')));
    }

    // ---------- messages from the other phone ----------
    static void onSignal(String from, String body) {
        if (body.isEmpty()) return;
        String rest = body.substring(1);
        long now = clock.getAsLong();
        switch (body.charAt(0)) {
            case 'R' -> {
                if (state != State.IDLE) { PhoneNet.enqueueFront(PhoneNet.cmd(from, 'T', "D")); return; }
                state = State.IN; peer = from; callee = true; since = now; lastRing = 0; note = "";
                video = rest.startsWith("V"); peerGroup = rest.length() > 1 ? rest.substring(1) : "";
                myFront = true; peerFront = true; remoteFrame = null; frameParts.clear();
                PhoneNet.notify("[Phone] " + from + (video ? " is FaceTiming you" : " is calling you") + " - open the phone");
            }
            case 'A' -> { if (state == State.OUT && from.equals(peer)) { state = State.ACTIVE; activeSince = now; lastFrame = 0; note = ""; } }
            case 'D' -> {
                if (state != State.IDLE && from.equals(peer)) {
                    if (state == State.ACTIVE && callee && svc()) PhoneNet.enqueueFront("voicechat leave");
                    note = state == State.IN ? "Missed call from " + from : state == State.OUT ? from + " didn't pick up" : "Call ended";
                    state = State.IDLE;
                }
            }
            case 'C' -> { if (state != State.IDLE && from.equals(peer) && !rest.isEmpty()) peerFront = rest.charAt(0) == 'F'; }
            case 'P' -> framePart(from, rest, now);
            default -> { }
        }
    }

    private static void framePart(String from, String rest, long now) {
        if (state != State.ACTIVE || !from.equals(peer) || rest.length() < 3) return;
        int idx = MusicEngine.B64.indexOf(rest.charAt(1));
        if (idx < 0 || idx > 1) return;
        String k = from + "#" + rest.charAt(0);
        if (frameParts.size() > 8) frameParts.clear();
        String[] parts = frameParts.computeIfAbsent(k, x -> new String[2]);
        parts[idx] = rest.substring(2);
        if (parts[0] == null || parts[1] == null) return;
        frameParts.remove(k);
        Frame f = Frame.decode(parts[0] + parts[1]);
        if (f != null) { remoteFrame = f.toPhoto(); remoteFrameMs = now; }
    }

    // ---------- clock (runs even when the phone is closed) ----------
    static void tick() {
        long now = clock.getAsLong();
        if (state == State.IN || state == State.OUT) {
            if (now - lastRing > (state == State.IN ? 1700 : 3200)) { lastRing = now; Sfx.play('r'); }
            if (now - since > 30000) {
                if (state == State.OUT) { PhoneNet.enqueueFront(PhoneNet.cmd(peer, 'T', "D")); note = peer + " didn't pick up"; }
                else { note = "Missed call from " + peer; PhoneNet.notify("[Phone] Missed call from " + peer); }
                state = State.IDLE;
            }
        }
        if (state == State.ACTIVE && video && now - lastFrame > 3300 && PhoneNet.queueSize() < 3 && Sfx.find(peer) == null) {
            lastFrame = now;
            Photo p = FaceTimeApp.feed(myFront);
            if (p != null) {
                String d = Frame.from(p).encode();
                char seq = MusicEngine.B64.charAt(frameSeq++ % 64);
                for (int i = 0; i < 2; i++) PhoneNet.enqueue(peer, 'T', "P" + seq + MusicEngine.B64.charAt(i) + d.substring(i * 160, i * 160 + 160));
            }
        }
    }

    static String timer() {
        long s = Math.max(0, (clock.getAsLong() - activeSince) / 1000);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    // ---------- shared drawing ----------
    static void drawContacts(GuiGraphicsExtractor g, int y0, String label, int color) {
        int i = 0;
        for (String n : PhoneNet.contacts) {
            if (i >= 7) break;
            int y = y0 + i * 22;
            Game.box(g, 4, y, 114, y + 20, 0xFF222633);
            Game.txt(g, n, 8, y + 6, Game.WHITE);
            Game.box(g, 76, y + 3, 110, y + 17, color);
            Game.ctr(g, label, 93, y + 6, Game.WHITE, 0.9f);
            i++;
        }
        if (PhoneNet.contacts.isEmpty()) {
            Game.ctr(g, "No friends yet.", Game.W / 2, 80, Game.WHITE, 1f);
            Game.ctr(g, "Search a name in Messages", Game.W / 2, 96, Game.GRAY, 0.8f);
        }
    }

    static String contactAt(float x, float y, int y0) {
        int i = 0;
        for (String n : PhoneNet.contacts) {
            if (i >= 7) break;
            if (Game.in(x, y, 4, y0 + i * 22, 114, y0 + i * 22 + 20)) return n;
            i++;
        }
        return null;
    }

    /** the big "ringing / in call" card shared by both apps */
    static void drawCallCard(GuiGraphicsExtractor g, boolean withVideoBlock) {
        Game.ctr(g, peer, Game.W / 2, 44, Game.WHITE, 1.6f);
        String s = state == State.IN ? (video ? "FaceTime incoming..." : "Incoming call...") : state == State.OUT ? "Calling..." : "In call " + timer();
        Game.ctr(g, s, Game.W / 2, 72, state == State.ACTIVE ? 0xFF69F0AE : Game.GRAY, 1f);
        if (state == State.ACTIVE) {
            if (!svc()) Game.ctr(g, "No voice: install Simple Voice Chat", Game.W / 2, 92, 0xFFFFCC80, 0.75f);
            else if (callee) Game.ctr(g, "Joined voice group " + peerGroup, Game.W / 2, 92, Game.GRAY, 0.7f);
            else Game.ctr(g, "Stay in your voice group " + myGroup(), Game.W / 2, 92, Game.GRAY, 0.7f);
        }
        if (state == State.IN) {
            Game.box(g, 4, 150, 58, 172, 0xFF2E7D32); Game.ctr(g, "Accept", 31, 157, Game.WHITE, 1f);
            Game.box(g, 62, 150, 114, 172, 0xFFC62828); Game.ctr(g, "Decline", 88, 157, Game.WHITE, 1f);
        } else {
            Game.box(g, 24, 150, 94, 172, 0xFFC62828); Game.ctr(g, "Hang up", 59, 157, Game.WHITE, 1f);
        }
    }

    /** @return true if a button of the call card was pressed */
    static boolean pressCallCard(float x, float y) {
        if (state == State.IN) {
            if (Game.in(x, y, 4, 150, 58, 172)) { accept(); return true; }
            if (Game.in(x, y, 62, 150, 114, 172)) { decline(); return true; }
        } else if (state != State.IDLE && Game.in(x, y, 24, 150, 94, 172)) { hangup(); return true; }
        return false;
    }
}

/** Call app: voice calls through Simple Voice Chat. */
final class CallApp extends Game {
    private boolean showSetup;

    CallApp() { super("call", "Call", "CALL", 0xFF2E7D32); resetOnOpen = false; }

    @Override void icon(GuiGraphicsExtractor g, int x, int y) {
        box(g, x, y, x + 30, y + 30, 0xFF2E7D32);
        box(g, x + 6, y + 6, x + 14, y + 12, 0xFFFFFFFF); box(g, x + 6, y + 12, x + 10, y + 20, 0xFFFFFFFF);
        box(g, x + 10, y + 18, x + 20, y + 22, 0xFFFFFFFF); box(g, x + 18, y + 20, x + 24, y + 26, 0xFFFFFFFF);
    }

    @Override void press(float x, float y) {
        if (Calls.state != Calls.State.IDLE) { if (!Calls.video) Calls.pressCallCard(x, y); return; }
        if (!Calls.svc()) return;
        if (in(x, y, 100, 4, 114, 18)) { showSetup = !showSetup; return; }
        if (!Calls.setupDone() || showSetup) {
            if (in(x, y, 4, 156, 114, 174)) { Scores.raise("call_setup", 1); showSetup = false; Sfx.play('w'); }
            return;
        }
        String n = Calls.contactAt(x, y, 24);
        if (n != null) { Calls.call(n, false); Sfx.play('c'); }
    }

    @Override void draw(GuiGraphicsExtractor g) {
        g.fillGradient(PhoneScreen.SX, PhoneScreen.SY, PhoneScreen.SX + W, PhoneScreen.SY + H, 0xFF10181A, 0xFF0A0F10);
        txt(g, "Call", 6, 6, WHITE);
        if (!Calls.svc()) {
            ctr(g, "Calls need the", W / 2, 70, WHITE, 1f);
            ctr(g, "Simple Voice Chat mod", W / 2, 84, 0xFF69F0AE, 1f);
            ctr(g, "Install it (client + server),", W / 2, 106, GRAY, 0.8f);
            ctr(g, "then restart the game.", W / 2, 117, GRAY, 0.8f);
            return;
        }
        if (Calls.state != Calls.State.IDLE) {
            if (Calls.video) ctr(g, "FaceTime in progress", W / 2, 80, GRAY, 1f);
            else Calls.drawCallCard(g, false);
            return;
        }
        box(g, 100, 4, 114, 18, 0xFF333847); txt(g, "?", 105, 6, WHITE);
        if (!Calls.setupDone() || showSetup) {
            box(g, 4, 24, 114, 150, 0xFF1B2326);
            ctr(g, "One-time setup", W / 2, 29, 0xFF69F0AE, 1.1f);
            left(g, "1. Open the Simple Voice", 8, 44, WHITE, 0.75f);
            left(g, "Chat menu (default key V)", 8, 54, WHITE, 0.75f);
            left(g, "2. Create a group named:", 8, 70, WHITE, 0.75f);
            ctr(g, Calls.myGroup(), W / 2, 83, 0xFFFFEB3B, 1.2f);
            left(g, "3. Leave the password empty", 8, 100, WHITE, 0.75f);
            left(g, "Keep that group while you", 8, 116, GRAY, 0.7f);
            left(g, "wait for calls. Friends join", 8, 125, GRAY, 0.7f);
            left(g, "it automatically.", 8, 134, GRAY, 0.7f);
            box(g, 4, 156, 114, 174, 0xFF2E7D32); ctr(g, Calls.setupDone() ? "OK" : "I made the group", W / 2, 161, WHITE, 1f);
        } else {
            Calls.drawContacts(g, 24, "Call", 0xFF2E7D32);
            ctr(g, Calls.note.isEmpty() ? "Tap Call to ring a friend" : Calls.note, W / 2, 204, Calls.note.isEmpty() ? GRAY : 0xFF8FD6DD, 0.7f);
        }
    }
}

/** FaceTime: video call. Flip between a selfie and what is in front of you, drag your own picture around. */
final class FaceTimeApp extends Game {
    private float acc, pipX = 82, pipY = 26;
    private boolean dragPip;
    private float dragDx, dragDy;
    private Photo self, remoteLive;

    FaceTimeApp() { super("facetime", "FaceTime", "FT", 0xFF00897B); resetOnOpen = false; }

    @Override void icon(GuiGraphicsExtractor g, int x, int y) {
        box(g, x, y, x + 30, y + 30, 0xFF00897B);
        box(g, x + 5, y + 9, x + 19, y + 21, 0xFFFFFFFF); box(g, x + 19, y + 12, x + 25, y + 18, 0xFFFFFFFF);
        box(g, x + 8, y + 12, x + 12, y + 18, 0xFF00897B);
    }

    /** my own camera: front = selfie, back = what is in front of me */
    static Photo feed(boolean front) {
        try {
            McWorld w = McWorld.forSelf(front);
            if (w == null) return null;
            return front ? VoxCam.selfie(w, w.eyeX(), w.eyeY(), w.eyeZ(), w.yaw(), w.pitch())
                         : VoxCam.render(w, w.eyeX(), w.eyeY(), w.eyeZ(), w.yaw(), w.pitch());
        } catch (Throwable t) { return null; }
    }

    private static Photo liveRemote() {
        try {
            McWorld w = McWorld.forPlayer(Calls.peer, Calls.peerFront);
            if (w == null) return null;
            return Calls.peerFront ? VoxCam.selfie(w, w.eyeX(), w.eyeY(), w.eyeZ(), w.yaw(), w.pitch())
                                   : VoxCam.render(w, w.eyeX(), w.eyeY(), w.eyeZ(), w.yaw(), w.pitch());
        } catch (Throwable t) { return null; }
    }

    @Override void update(float dt) {
        if (dragPip && down) { pipX = clamp(mx - dragDx, 0, W - 32); pipY = clamp(my - dragDy, 16, 150 - 40); }
        if (Calls.state == Calls.State.IDLE || !Calls.video) return;
        acc += dt;
        if (acc >= 0.2f) {
            acc = 0;
            self = feed(Calls.myFront);
            remoteLive = Calls.state == Calls.State.ACTIVE ? liveRemote() : null;
        }
    }

    @Override void press(float x, float y) {
        if (Calls.state == Calls.State.IDLE) {
            if (!Calls.svc() || Calls.setupDone() || true) {
                String n = Calls.contactAt(x, y, 24);
                if (n != null) { Calls.call(n, true); Sfx.play('c'); }
            }
            return;
        }
        if (!Calls.video) return;
        if (Calls.state != Calls.State.ACTIVE) { Calls.pressCallCard(x, y); return; }
        if (in(x, y, pipX, pipY, pipX + 32, pipY + 40)) { dragPip = true; dragDx = x - pipX; dragDy = y - pipY; return; }
        if (in(x, y, 8, 150, 52, 172)) { Calls.flip(); Sfx.play('c'); }
        else if (in(x, y, 66, 150, 110, 172)) Calls.hangup();
    }

    @Override void release(float x, float y) { dragPip = false; }

    @Override void draw(GuiGraphicsExtractor g) {
        g.fillGradient(PhoneScreen.SX, PhoneScreen.SY, PhoneScreen.SX + W, PhoneScreen.SY + H, 0xFF0C1716, 0xFF070D0C);
        if (Calls.state == Calls.State.IDLE) {
            txt(g, "FaceTime", 6, 6, WHITE);
            Calls.drawContacts(g, 24, "Video", 0xFF00897B);
            ctr(g, Calls.note.isEmpty() ? "Live when close, slow video when far" : Calls.note, W / 2, 204, Calls.note.isEmpty() ? GRAY : 0xFF8FD6DD, 0.7f);
            return;
        }
        if (!Calls.video) { ctr(g, "Voice call in progress", W / 2, 80, GRAY, 1f); ctr(g, "Use the Call app", W / 2, 94, GRAY, 0.8f); return; }
        if (Calls.state != Calls.State.ACTIVE) {
            if (self != null) self.draw(g, 11, 100, 1);
            Calls.drawCallCard(g, true);
            return;
        }
        // remote picture
        boolean live = remoteLive != null;
        Photo r = live ? remoteLive : (Calls.remoteFrame != null && Calls.clock.getAsLong() - Calls.remoteFrameMs < 15000 ? Calls.remoteFrame : null);
        if (r != null) r.draw(g, 11, 22, 3);
        else { box(g, 11, 22, 107, 142, 0xFF1E2A29); ctr(g, "Waiting for", W / 2, 70, GRAY, 0.9f); ctr(g, Calls.peer + "'s video...", W / 2, 82, GRAY, 0.9f); }
        box(g, 10, 21, 108, 22, 0xFF555555); box(g, 10, 142, 108, 143, 0xFF555555);
        left(g, live ? "LIVE" : (r != null ? "SLOW" : "..."), 14, 25, live ? 0xFF69F0AE : 0xFFFFCC80, 0.8f);
        txt(g, Calls.peer + "  " + Calls.timer(), 6, 5, WHITE);
        left(g, Calls.peerFront ? "front cam" : "back cam", 70, 8, GRAY, 0.7f);
        // my picture-in-picture (drag it anywhere)
        box(g, pipX - 1, pipY - 1, pipX + 33, pipY + 41, 0xFFFFFFFF);
        if (self != null) self.draw(g, (int) pipX, (int) pipY, 1); else box(g, pipX, pipY, pipX + 32, pipY + 40, 0xFF333333);
        left(g, Calls.myFront ? "you" : "view", (int) pipX + 2, (int) pipY + 31, 0xFFFFFFFF, 0.7f);
        // buttons
        box(g, 8, 150, 52, 172, 0xFF37474F); ctr(g, Calls.myFront ? "Flip: front" : "Flip: back", 30, 157, WHITE, 0.8f);
        box(g, 66, 150, 110, 172, 0xFFC62828); ctr(g, "End", 88, 157, WHITE, 1f);
        ctr(g, "Drag your picture anywhere", W / 2, 180, GRAY, 0.75f);
        if (!Calls.svc()) ctr(g, "No voice (needs Simple Voice Chat)", W / 2, 192, 0xFFFFCC80, 0.7f);
    }
}
