package com.phonemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The phone: swings up from the bottom-right. Swipe pages, hold + drag icons, open apps. */
public class PhoneScreen extends Screen {
    static final int PW = 130, PH = 250;                  // whole phone (virtual size)
    static final int SX = 6, SY = 14, SW = 118, SH = 214; // display rectangle
    static final int PER_PAGE = 9;

    private enum App { HOME, GAME, MESSAGES, SETTINGS }
    private enum Mode { NONE, SWIPE, DRAG }

    // ---------- saved settings (config/phonemod.txt: wallpaper index, then icon order) ----------
    private static final String[] WALL_NAMES = {"Dusk", "Sunset", "Ocean", "Forest", "Candy", "Lava"};
    private static final int[][] WALLS = {
        {0xFF1B2A49, 0xFF7A3FA0}, {0xFFFF9966, 0xFF5B247A}, {0xFF2193B0, 0xFF0B2A4A},
        {0xFF56AB2F, 0xFF0F3D1F}, {0xFFFF9A9E, 0xFFA18CD1}, {0xFFF12711, 0xFF2B0A0A}
    };
    private static final List<String> order = Settings.order;

    // ---------- state ----------
    private App app = App.HOME;
    private Game game;
    private final MessagesApp messages = new MessagesApp();
    private long openNs = System.nanoTime(), closeNs = -1, lastNs = System.nanoTime();
    private float closeFrom = 1f;
    private boolean visible, docked, dockPlaced;   // visible = the Screen is open; docked = small HUD phone
    private float scale = 1f, pcx, pcy;   // phone centre on screen + scale

    // home-screen touch state
    private int page;
    private boolean homeDown;
    private float pdx, pdy, swipeDx, dragX, dragY;
    private long pdNs, edgeNs;
    private int pIdx = -1;
    private boolean gamePress;
    private float offX, offY, dragMx, dragMy, dragOffX0, dragOffY0;   // the phone itself can be dragged by its frame
    private boolean dragPhone;
    private Mode mode = Mode.NONE;

    private static PhoneScreen instance;
    static final float DOCK = 0.55f;

    private PhoneScreen() { super(Component.literal("Phone")); }

    /** one phone object for the whole session, so docking keeps your app, call or recording alive */
    static PhoneScreen get() { if (instance == null) instance = new PhoneScreen(); return instance; }

    /** bring the phone up (from nothing or from the docked corner) */
    void show() {
        openNs = System.nanoTime(); closeNs = -1; lastNs = openNs; closeFrom = 1f;
        visible = true; docked = false; homeDown = false; dragPhone = false; mode = Mode.NONE;
        Sfx.share('o');
        if (Calls.state == Calls.State.IN) openApp(Calls.video ? "facetime" : "call");
        Minecraft.getInstance().setScreenAndShow(this);
    }

    private boolean keepDocked() {
        return Calls.state != Calls.State.IDLE || Capture.recording || (app == App.GAME && game != null && game.dockable());
    }

    @Override public void removed() { visible = false; }

    /** which app other players should see in your hand (null = phone put away) */
    static String presenceId() {
        PhoneScreen s = instance;
        if (s == null || !(s.visible || s.docked)) return null;
        return switch (s.app) { case HOME -> "home"; case MESSAGES -> "messages"; case SETTINGS -> "settings"; case GAME -> s.game == null ? "home" : s.game.id; };
    }

    /** key K: shutter / hang up / stop recording */
    static void action() {
        if (Capture.recording) { CameraApp.stopRec(); return; }
        if (Calls.state == Calls.State.IN) { Calls.accept(); return; }
        if (Calls.state != Calls.State.IDLE) { Calls.hangup(); return; }
        PhoneScreen s = instance;
        if (s != null && s.docked && s.app == App.GAME && s.game instanceof CameraApp) CameraApp.shutter();
    }

    /** key R: start / stop recording */
    static void recordKey() {
        if (Capture.recording) { CameraApp.stopRec(); return; }
        PhoneScreen s = instance;
        if (s != null && s.docked && s.app == App.GAME && s.game instanceof CameraApp) CameraApp.startRec();
    }

    private static Object lastConnection;

    /** call every tick with the current connection; when it changes (another server) everything is reset */
    static void checkServerChange(Object connection) {
        if (connection == lastConnection) return;
        lastConnection = connection;
        resetSession();
    }

    static void resetSession() {
        PhoneScreen s = instance;
        if (s != null) { s.visible = false; s.docked = false; s.closeNs = -1; s.dragPhone = false; s.homeDown = false; s.mode = Mode.NONE; s.app = App.HOME; }
        Capture.reset();
        cameraTip = false;
        Calls.state = Calls.State.IDLE;
        MusicEngine.playing = false; MusicEngine.remote.clear(); MusicEngine.listeners.clear();
        Presence.others.clear(); Presence.told.clear(); Presence.lastCode = -1;
        PhoneNet.clearSession();
    }

    private static boolean cameraTip;

    /** key J: flip the camera */
    static void flipKey() {
        if (Calls.state != Calls.State.IDLE && Calls.video) { Calls.flip(); return; }
        PhoneScreen s = instance;
        if (s != null && s.docked && s.app == App.GAME && s.game instanceof CameraApp) CameraApp.flip();
    }

    @Override public boolean isPauseScreen() { return false; }

    // ---------- drawing helpers (display-local coordinates, clipped to the display) ----------
    static void box(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        int a = Math.round(Math.max(0, x0)), b = Math.round(Math.max(0, y0));
        int c = Math.round(Math.min(SW, x1)), d = Math.round(Math.min(SH, y1));
        if (c > a && d > b) g.fill(SX + a, SY + b, SX + c, SY + d, color);
    }

    static void txt(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(Minecraft.getInstance().font, s, SX + x, SY + y, color);
    }

    static void left(GuiGraphicsExtractor g, String s, int x, int y, int color, float sc) {
        g.pose().pushMatrix();
        g.pose().translate(SX + x, SY + y);
        g.pose().scale(sc, sc);
        g.text(Minecraft.getInstance().font, s, 0, 0, color);
        g.pose().popMatrix();
    }

    static void ctr(GuiGraphicsExtractor g, String s, int cx, int y, int color, float sc) {
        var font = Minecraft.getInstance().font;
        g.pose().pushMatrix();
        g.pose().translate(SX + cx, SY + y);
        g.pose().scale(sc, sc);
        g.text(font, s, -font.width(s) / 2, 0, color);
        g.pose().popMatrix();
    }

    private static void round(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0 + 2, y0, x1 - 2, y1, c);
        g.fill(x0, y0 + 2, x1, y1 - 2, c);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, c);
    }

    private static float clampF(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---------- animation ----------
    private float progress(long now) {
        if (closeNs >= 0) {
            float t = Math.min(1f, (now - closeNs) / 1e9f / 0.30f);
            return closeFrom * (1f - t * t * t);
        }
        float t = Math.min(1f, (now - openNs) / 1e9f / 0.55f);
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;          // ease-out-back
    }

    private void leaveGame() { if (app == App.GAME && game != null) game.saveStats(); }

    private void finishClose() {
        visible = false;
        docked = keepDocked();
        minecraft.setScreenAndShow(null);
    }

    private void startClose() {
        if (closeNs >= 0) return;
        leaveGame();
        Sfx.play('c');
        closeFrom = Math.min(1f, progress(System.nanoTime()));
        closeNs = System.nanoTime();
    }

    @Override public void onClose() { startClose(); }

    // ---------- render ----------
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) { renderPhone(g, width, height, mx, my, true); }

    /** the docked phone: drawn by the HUD, no mouse, keeps running */
    static void hud(GuiGraphicsExtractor g) {
        PhoneScreen s = instance;
        if (s == null || s.visible || Capture.hideUi) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        if (Capture.recording) {   // while recording only a small badge shows; Esc earlier docked the phone away
            g.fill(w - 64, 6, w - 6, 19, 0x99000000);
            if (((int) (Capture.recSeconds() * 2) & 1) == 0) g.fill(w - 60, 10, w - 54, 16, 0xFFE53935);
            g.text(mc.font, String.format("REC %.0fs", Capture.recSeconds()), w - 50, 9, 0xFFFFFFFF);
            return;
        }
        if (s.docked) s.renderPhone(g, w, h, -10000, -10000, false);
    }

    private void renderPhone(GuiGraphicsExtractor g, int sw, int sh, int mx, int my, boolean interactive) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNs) / 1e9f, 0.05f);
        lastNs = now;
        float p = interactive ? progress(now) : 1f;
        if (interactive && closeNs >= 0 && p <= 0.001f) { finishClose(); return; }
        if (Capture.hideUi) return;

        float base = Math.min(sh * 0.82f / PH, 3f), sc = interactive ? base : base * DOCK;
        if (interactive && dragPhone) {
            offX = Math.max(-sw / 2f, Math.min(sw / 2f, dragOffX0 + (mx - dragMx)));
            offY = Math.max(-sh / 2f, Math.min(sh / 2f, dragOffY0 + (my - dragMy)));
        }
        if (!interactive && !dockPlaced) { if (offX == 0 && offY == 0) { offX = sw * 0.30f; offY = sh * 0.18f; } dockPlaced = true; }
        // swings in from the bottom-right, tilted, and straightens out
        float cx = sw / 2f + offX + (1f - p) * sw * 0.22f;
        float cy = sh / 2f + offY + (1f - p) * (sh * 0.5f + PH * sc * 0.5f + 12f);
        if (interactive) { scale = sc; pcx = cx; pcy = cy; }
        float lx = (mx - cx) / sc + PW / 2f - SX;
        float ly = (my - cy) / sc + PH / 2f - SY;
        boolean clear = app == App.GAME && game != null && game.transparent();   // camera: see the real game through the phone

        if (interactive && !clear) g.fill(0, 0, sw, sh, ((int) (110 * Math.max(0f, Math.min(1f, p)))) << 24);

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate((1f - p) * 0.45f);
        g.pose().scale(sc, sc);
        g.pose().translate(-PW / 2f, -PH / 2f);

        if (!clear) {
            round(g, 0, 0, PW, PH, 0xFF3A3A44);
            round(g, 1, 1, PW - 1, PH - 1, 0xFF111114);
            g.fill(SX, SY, SX + SW, SY + SH, 0xFF000000);
        } else {   // frame only, with a hole where the screen is
            g.fill(0, 0, PW, SY, 0xFF111114); g.fill(0, SY + SH, PW, PH, 0xFF111114);
            g.fill(0, SY, SX, SY + SH, 0xFF111114); g.fill(SX + SW, SY, PW, SY + SH, 0xFF111114);
            g.fill(SX - 1, SY - 1, SX + SW + 1, SY, 0xFF3A3A44); g.fill(SX - 1, SY + SH, SX + SW + 1, SY + SH + 1, 0xFF3A3A44);
        }

        boolean live = closeNs < 0 && p > 0.9f;
        switch (app) {
            case HOME -> { if (homeDown && interactive) homeTick(lx, ly, now); drawHome(g); }
            case SETTINGS -> drawSettings(g);
            case MESSAGES -> messages.draw(g);
            case GAME -> {
                game.mx = lx; game.my = ly;
                if (live) game.update(dt);
                game.draw(g);
            }
        }

        round(g, PW / 2 - 12, 5, PW / 2 + 12, 10, 0xFF000000);                  // camera pill
        g.fill(PW / 2 - 16, PH - 8, PW / 2 + 16, PH - 6, app == App.HOME ? 0xFF555555 : 0xFFDDDDDD); // home bar
        g.pose().popMatrix();
    }

    // ---------- home screen ----------
    private int pages() { return Math.max(1, (order.size() + PER_PAGE - 1) / PER_PAGE); }
    private static int cellX(int col) { return 8 + col * 36; }
    private static int cellY(int row) { return 52 + row * 52; }

    private int iconAt(float x, float y) {
        for (int k = 0; k < PER_PAGE; k++) {
            int idx = page * PER_PAGE + k;
            if (idx >= order.size()) break;
            int ix = cellX(k % 3), iy = cellY(k / 3);
            if (x >= ix && x <= ix + 30 && y >= iy && y <= iy + 30) return idx;
        }
        return -1;
    }

    private void homeTick(float lx, float ly, long now) {
        float dx = lx - pdx, dy = ly - pdy;
        if (mode == Mode.NONE) {
            if (Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(dy)) mode = Mode.SWIPE;
            else if (pIdx >= 0 && now - pdNs > 300_000_000L && Math.hypot(dx, dy) < 6) { mode = Mode.DRAG; dragX = lx; dragY = ly; }
        }
        if (mode == Mode.SWIPE) {
            boolean resist = (page == 0 && dx > 0) || (page == pages() - 1 && dx < 0);
            swipeDx = clampF(resist ? dx * 0.3f : dx, -SW, SW);
        } else if (mode == Mode.DRAG) {
            dragX = lx; dragY = ly;
            boolean l = lx < 8 && page > 0, r = lx > SW - 8 && page < pages() - 1;
            if (l || r) {
                if (edgeNs == 0) edgeNs = now;
                else if (now - edgeNs > 600_000_000L) { page += l ? -1 : 1; edgeNs = now; }
            } else edgeNs = 0;
        }
    }

    private static String labelOf(String id) {
        if (id.equals("messages")) return "Messages";
        if (id.equals("settings")) return "Settings";
        Game gm = Games.byId(id);
        return gm == null ? id : gm.label;
    }

    private void drawAppIcon(GuiGraphicsExtractor g, String id, int x, int y, boolean withLabel) {
        if (id.equals("messages")) builtinIcon(g, 4, x, y);
        else if (id.equals("settings")) builtinIcon(g, 5, x, y);
        else { Game gm = Games.byId(id); if (gm != null) gm.icon(g, x, y); }
        if (withLabel && x >= 0 && x + 30 <= SW) ctr(g, labelOf(id), x + 15, y + 34, 0xFFFFFFFF, 0.7f);
        if (withLabel && (id.equals("call") || id.equals("facetime")) && Calls.state == Calls.State.IN) {
            box(g, x + 23, y - 3, x + 34, y + 8, 0xFFE53935);
            ctr(g, "!", x + 28, y - 2, 0xFFFFFFFF, 0.8f);
        }
        if (withLabel && id.equals("messages") && PhoneNet.totalUnread() > 0) {
            box(g, x + 23, y - 3, x + 34, y + 8, 0xFFE53935);
            ctr(g, String.valueOf(Math.min(PhoneNet.totalUnread(), 9)), x + 28, y - 2, 0xFFFFFFFF, 0.8f);
        }
    }

    private void drawHome(GuiGraphicsExtractor g) {
        g.fillGradient(SX, SY, SX + SW, SY + SH, WALLS[Settings.wallpaper][0], WALLS[Settings.wallpaper][1]);
        LocalTime t = LocalTime.now();
        ctr(g, String.format("%02d:%02d", t.getHour(), t.getMinute()), SW / 2, 14, 0xFFFFFFFF, 2.5f);

        float shift = mode == Mode.SWIPE ? swipeDx : 0;
        for (int pg = Math.max(0, page - 1); pg <= Math.min(pages() - 1, page + 1); pg++) {
            float offX = (pg - page) * SW + shift;
            for (int k = 0; k < PER_PAGE; k++) {
                int idx = pg * PER_PAGE + k;
                if (idx >= order.size()) break;
                if (mode == Mode.DRAG && idx == pIdx) continue;
                int x = Math.round(cellX(k % 3) + offX), y = cellY(k / 3);
                if (x < -34 || x > SW + 4) continue;
                drawAppIcon(g, order.get(idx), x, y, true);
            }
        }
        if (mode == Mode.DRAG && pIdx >= 0 && pIdx < order.size()) {
            int col = (int) clampF((float) Math.floor((dragX - 8) / 36f), 0, 2), row = (int) clampF((float) Math.floor((dragY - 52) / 52f), 0, 2);
            box(g, cellX(col) - 3, cellY(row) - 3, cellX(col) + 33, cellY(row) + 33, 0x44FFFFFF);
            drawAppIcon(g, order.get(pIdx), Math.round(dragX - 15), Math.round(dragY - 15), false);
        }
        int n = pages(), start = (SW - n * 8) / 2;
        for (int i = 0; i < n; i++) box(g, start + i * 8 + 1, 203, start + i * 8 + 5, 207, i == page ? 0xFFFFFFFF : 0x66FFFFFF);
    }

    /** Icons for the built-in apps: 0 Block Blast, 1 Flappy, 2 Snake, 3 Chess, 4 Messages, 5 Settings. */
    static void builtinIcon(GuiGraphicsExtractor g, int i, int x, int y) {
        switch (i) {
            case 0 -> {
                box(g, x, y, x + 30, y + 30, 0xFF263056);
                box(g, x + 3, y + 3, x + 14, y + 14, 0xFFE74C3C); box(g, x + 16, y + 3, x + 27, y + 14, 0xFFF1C40F);
                box(g, x + 3, y + 16, x + 14, y + 27, 0xFF3498DB); box(g, x + 16, y + 16, x + 27, y + 27, 0xFF2ECC71);
            }
            case 1 -> {
                box(g, x, y, x + 30, y + 30, 0xFF70C5CE);
                box(g, x + 22, y, x + 30, y + 10, 0xFF5AB031); box(g, x + 22, y + 20, x + 30, y + 30, 0xFF5AB031);
                box(g, x + 6, y + 11, x + 18, y + 19, 0xFFF7D308); box(g, x + 14, y + 12, x + 16, y + 14, 0xFF000000);
                box(g, x + 18, y + 14, x + 21, y + 16, 0xFFFF8800);
            }
            case 2 -> {
                box(g, x, y, x + 30, y + 30, 0xFF2E7D32);
                box(g, x + 4, y + 20, x + 10, y + 26, 0xFF1E88E5); box(g, x + 10, y + 20, x + 16, y + 26, 0xFF1E88E5);
                box(g, x + 16, y + 20, x + 22, y + 26, 0xFF1E88E5); box(g, x + 16, y + 14, x + 22, y + 20, 0xFF1E88E5);
                box(g, x + 16, y + 8, x + 22, y + 14, 0xFF64B5F6); box(g, x + 5, y + 5, x + 10, y + 10, 0xFFE53935);
            }
            case 3 -> {
                box(g, x, y, x + 30, y + 30, 0xFF769656);
                box(g, x, y, x + 15, y + 15, 0xFFEEEED2); box(g, x + 15, y + 15, x + 30, y + 30, 0xFFEEEED2);
                box(g, x + 8, y + 6, x + 22, y + 24, 0xFF222222);
                ctr(g, "K", x + 15, y + 11, 0xFFF8F8F8, 1.2f);
            }
            case 4 -> {
                box(g, x, y, x + 30, y + 30, 0xFF2E86DE);
                box(g, x + 5, y + 7, x + 25, y + 21, 0xFFFFFFFF); box(g, x + 8, y + 21, x + 14, y + 25, 0xFFFFFFFF);
                box(g, x + 9, y + 13, x + 12, y + 16, 0xFF2E86DE); box(g, x + 14, y + 13, x + 17, y + 16, 0xFF2E86DE);
                box(g, x + 19, y + 13, x + 22, y + 16, 0xFF2E86DE);
            }
            default -> {
                box(g, x, y, x + 30, y + 30, 0xFF5A5F6B);
                box(g, x + 9, y + 9, x + 21, y + 21, 0xFFE0E0E0);
                box(g, x + 13, y + 5, x + 17, y + 9, 0xFFE0E0E0); box(g, x + 13, y + 21, x + 17, y + 25, 0xFFE0E0E0);
                box(g, x + 5, y + 13, x + 9, y + 17, 0xFFE0E0E0); box(g, x + 21, y + 13, x + 25, y + 17, 0xFFE0E0E0);
                box(g, x + 13, y + 13, x + 17, y + 17, 0xFF5A5F6B);
            }
        }
    }

    private static int swatchX(int i) { return 4 + (i % 3) * 38; }
    private static int swatchY(int i) { return 18 + (i / 3) * 40; }

    private void drawSettings(GuiGraphicsExtractor g) {
        g.fillGradient(SX, SY, SX + SW, SY + SH, 0xFF14161C, 0xFF0B0C10);
        ctr(g, "Wallpaper", SW / 2, 5, 0xFFFFFFFF, 1.1f);
        for (int i = 0; i < WALLS.length; i++) {
            int x = swatchX(i), y = swatchY(i);
            if (i == Settings.wallpaper) box(g, x - 2, y - 2, x + 36, y + 34, 0xFFFFFFFF);
            g.fillGradient(SX + x, SY + y, SX + x + 34, SY + y + 32, WALLS[i][0], WALLS[i][1]);
            ctr(g, WALL_NAMES[i], x + 17, y + 25, 0xFFFFFFFF, 0.65f);
        }
        String[] labels = {
            "Sounds: " + (Settings.sound ? "ON" : "OFF"), "Nearby sharing: " + (Settings.nearby ? "ON" : "OFF"),
            "Custom server mode: " + (Settings.customMode ? "ON" : "OFF"), "Whisper command: /" + Settings.COMMANDS[Settings.cmdIndex],
            "Reset icon layout"};
        boolean[] on = {Settings.sound, Settings.nearby, Settings.customMode, true, false};
        for (int i = 0; i < labels.length; i++) {
            int y = 102 + i * 19;
            box(g, 4, y, 114, y + 16, i == 4 ? 0xFF37474F : on[i] ? 0xFF2E7D32 : 0xFF555555);
            ctr(g, labels[i], SW / 2, y + 4, 0xFFFFFFFF, 0.85f);
        }
        ctr(g, "Custom mode: use on plugin servers", SW / 2, 199, 0xFF8A8F9E, 0.65f);
        ctr(g, "that change how whispers look", SW / 2, 207, 0xFF8A8F9E, 0.65f);
    }

    // ---------- input ----------
    private void goHome() {
        leaveGame();
        Sfx.play('c');
        if (game != null) { game.down = false; game.keys.clear(); }
        app = App.HOME; homeDown = false; mode = Mode.NONE; pIdx = -1;
    }

    private void openApp(String id) {
        Sfx.play('c');
        if (id.equals("messages")) { app = App.MESSAGES; return; }
        if (id.equals("settings")) { app = App.SETTINGS; return; }
        Game gm = Games.byId(id);
        if (gm == null) return;
        game = gm; gm.keys.clear(); gm.down = false;
        if (gm.resetOnOpen) gm.reset();
        app = App.GAME;
        if (gm instanceof CameraApp) {   // an open phone locks your movement, so the camera docks straight away
            visible = false; docked = true; homeDown = false; mode = Mode.NONE;
            if (!cameraTip) { cameraTip = true; PhoneNet.notify("[Phone] Camera ready - you can walk around. K = screenshot, R = record, J = flip camera, P = open the phone"); }
            Minecraft.getInstance().setScreenAndShow(null);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (app == App.MESSAGES && messages.typing()) { if (k == GLFW.GLFW_KEY_ESCAPE && messages.open == null && !messages.focus) return super.keyPressed(e); messages.key(k); return true; }
        if (app == App.GAME && game.capturesText()) {
            if (k == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(e);
            game.key(k);
            return true;
        }
        if (PhoneModClient.OPEN_PHONE.matches(e)) { startClose(); return true; }
        if (app == App.GAME) { game.keys.add(k); if (game.key(k)) return true; }
        return super.keyPressed(e);   // Esc -> onClose()
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        if (app == App.GAME) game.keys.remove(e.key());
        return super.keyReleased(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        String s = Character.toString(e.codepoint());
        if (app == App.MESSAGES && messages.typing()) { messages.typed(s); return true; }
        if (app == App.GAME && game.capturesText()) { game.typed(s); return true; }
        return super.charTyped(e);
    }

    private float localX(double mx) { return (float) ((mx - pcx) / scale) + PW / 2f; }
    private float localY(double my) { return (float) ((my - pcy) / scale) + PH / 2f; }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (closeNs >= 0 || e.button() != 0 || progress(System.nanoTime()) < 0.9f) return true;
        float fx = localX(e.x()), fy = localY(e.y());
        float x = fx - SX, y = fy - SY;
        if (app != App.HOME && fy > SY + SH && fy < PH && Math.abs(fx - PW / 2f) < 40) { goHome(); return true; }   // home bar
        boolean inPhone = fx >= 0 && fx <= PW && fy >= 0 && fy <= PH, inDisplay = x >= 0 && x <= SW && y >= 0 && y <= SH;
        if (inPhone && !inDisplay) {   // grab the phone by its frame and move it around
            dragPhone = true; dragMx = (float) e.x(); dragMy = (float) e.y(); dragOffX0 = offX; dragOffY0 = offY;
            return true;
        }
        switch (app) {
            case HOME -> {
                homeDown = true; pdx = x; pdy = y; pdNs = System.nanoTime();
                pIdx = iconAt(x, y); mode = Mode.NONE; swipeDx = 0; edgeNs = 0;
            }
            case GAME -> { gamePress = true; game.down = true; game.press(x, y); }
            case MESSAGES -> {
                messages.click(x, y);
                if (messages.pendingGame != null) {
                    game = messages.pendingGame; messages.pendingGame = null;
                    game.keys.clear(); game.down = false;
                    app = App.GAME;
                }
            }
            case SETTINGS -> {
                Sfx.play('c');
                for (int i = 0; i < WALLS.length; i++)
                    if (x >= swatchX(i) && x <= swatchX(i) + 34 && y >= swatchY(i) && y <= swatchY(i) + 32) { Settings.wallpaper = i; Settings.save(); }
                for (int i = 0; i < 5; i++) {
                    int by = 102 + i * 19;
                    if (x < 4 || x > 114 || y < by || y > by + 16) continue;
                    switch (i) {
                        case 0 -> Settings.sound = !Settings.sound;
                        case 1 -> Settings.nearby = !Settings.nearby;
                        case 2 -> Settings.customMode = !Settings.customMode;
                        case 3 -> Settings.cmdIndex = (Settings.cmdIndex + 1) % Settings.COMMANDS.length;
                        default -> { Settings.resetOrder(); page = 0; }
                    }
                    Settings.save();
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragPhone) { dragPhone = false; return true; }
        float x = localX(e.x()) - SX, y = localY(e.y()) - SY;
        if (app == App.HOME) homeRelease(x, y);
        else if (app == App.GAME && gamePress) { gamePress = false; game.down = false; game.release(x, y); }
        return true;
    }

    private void homeRelease(float x, float y) {
        if (!homeDown) return;
        homeDown = false;
        if (mode == Mode.SWIPE) {
            if (swipeDx < -20 && page < pages() - 1) page++;
            else if (swipeDx > 20 && page > 0) page--;
        } else if (mode == Mode.DRAG) {
            int col = (int) clampF((float) Math.floor((x - 8) / 36f), 0, 2), row = (int) clampF((float) Math.floor((y - 52) / 52f), 0, 2);
            int target = Math.max(0, Math.min(order.size() - 1, page * PER_PAGE + row * 3 + col));
            if (pIdx >= 0 && pIdx < order.size() && target != pIdx) {
                String id = order.remove(pIdx);
                order.add(target, id);
                Settings.save();
            }
        } else if (pIdx >= 0 && pIdx < order.size() && Math.hypot(x - pdx, y - pdy) < 8) {
            int idx = pIdx;
            mode = Mode.NONE; swipeDx = 0; pIdx = -1; edgeNs = 0;
            openApp(order.get(idx));
            return;
        }
        mode = Mode.NONE; swipeDx = 0; pIdx = -1; edgeNs = 0;
    }
}
