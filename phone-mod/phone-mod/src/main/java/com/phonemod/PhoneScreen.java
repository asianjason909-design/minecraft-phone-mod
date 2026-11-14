package com.phonemod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

/** The phone: swings up from the bottom-right, then home screen + apps. */
public class PhoneScreen extends Screen {
    static final int PW = 130, PH = 250;                  // whole phone (virtual size)
    static final int SX = 6, SY = 14, SW = 118, SH = 214; // display rectangle

    private enum App { HOME, FLAPPY, BLAST, SNAKE, CHESS, MESSAGES, SETTINGS }

    // ---------- wallpapers (saved to config/phonemod.txt) ----------
    private static final String[] WALL_NAMES = {"Dusk", "Sunset", "Ocean", "Forest", "Candy", "Lava"};
    private static final int[][] WALLS = {
        {0xFF1B2A49, 0xFF7A3FA0}, {0xFFFF9966, 0xFF5B247A}, {0xFF2193B0, 0xFF0B2A4A},
        {0xFF56AB2F, 0xFF0F3D1F}, {0xFFFF9A9E, 0xFFA18CD1}, {0xFFF12711, 0xFF2B0A0A}
    };
    private static int wallpaper = loadWallpaper();

    private static Path cfg() { return FabricLoader.getInstance().getConfigDir().resolve("phonemod.txt"); }
    private static int loadWallpaper() {
        try { return Math.floorMod(Integer.parseInt(Files.readString(cfg()).trim()), WALLS.length); }
        catch (Exception e) { return 0; }
    }
    private static void saveWallpaper() {
        try { Files.writeString(cfg(), String.valueOf(wallpaper)); } catch (Exception ignored) { }
    }

    // ---------- state ----------
    private App app = App.HOME;
    private final long openNs = System.nanoTime();
    private long closeNs = -1, lastNs = openNs;
    private float closeFrom = 1f;
    private final FlappyGame flappy = new FlappyGame();
    private final BlockBlastGame blast = new BlockBlastGame();
    private final SnakeGame snake = new SnakeGame();
    private final MessagesApp messages = new MessagesApp();
    private ChessGame chess;
    private float scale = 1f, pcx, pcy;   // phone centre on screen + scale

    public PhoneScreen() { super(Component.literal("Phone")); }

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

    private void startClose() {
        if (closeNs >= 0) return;
        closeFrom = Math.min(1f, progress(System.nanoTime()));
        closeNs = System.nanoTime();
    }

    @Override public void onClose() { startClose(); }

    // ---------- render ----------
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNs) / 1e9f, 0.05f);
        lastNs = now;
        float p = progress(now);
        if (closeNs >= 0 && p <= 0.001f) { minecraft.setScreenAndShow(null); return; }

        scale = Math.min(height * 0.82f / PH, 3f);
        // swings in from the bottom-right, tilted, and straightens out
        pcx = width / 2f + (1f - p) * width * 0.22f;
        pcy = height / 2f + (1f - p) * (height * 0.5f + PH * scale * 0.5f + 12f);
        float lx = (mx - pcx) / scale + PW / 2f - SX;
        float ly = (my - pcy) / scale + PH / 2f - SY;

        g.fill(0, 0, width, height, ((int) (110 * Math.max(0f, Math.min(1f, p)))) << 24);

        g.pose().pushMatrix();
        g.pose().translate(pcx, pcy);
        g.pose().rotate((1f - p) * 0.45f);
        g.pose().scale(scale, scale);
        g.pose().translate(-PW / 2f, -PH / 2f);

        round(g, 0, 0, PW, PH, 0xFF3A3A44);
        round(g, 1, 1, PW - 1, PH - 1, 0xFF111114);
        g.fill(SX, SY, SX + SW, SY + SH, 0xFF000000);

        boolean live = closeNs < 0 && p > 0.9f;
        switch (app) {
            case HOME -> drawHome(g);
            case SETTINGS -> drawSettings(g);
            case FLAPPY -> { if (live) flappy.update(dt); flappy.draw(g); }
            case BLAST -> blast.draw(g, lx, ly);
            case SNAKE -> { if (live) snake.update(dt); snake.draw(g); }
            case CHESS -> { if (live) chess.update(dt); chess.draw(g); }
            case MESSAGES -> messages.draw(g);
        }

        round(g, PW / 2 - 12, 5, PW / 2 + 12, 10, 0xFF000000);                  // camera pill
        g.fill(PW / 2 - 16, PH - 8, PW / 2 + 16, PH - 6, app == App.HOME ? 0xFF555555 : 0xFFDDDDDD); // home bar
        g.pose().popMatrix();
    }

    private static final String[] LABELS = {"Blast", "Flappy", "Snake", "Chess", "Messages", "Settings"};

    private void drawHome(GuiGraphicsExtractor g) {
        g.fillGradient(SX, SY, SX + SW, SY + SH, WALLS[wallpaper][0], WALLS[wallpaper][1]);
        LocalTime t = LocalTime.now();
        ctr(g, String.format("%02d:%02d", t.getHour(), t.getMinute()), SW / 2, 14, 0xFFFFFFFF, 2.5f);

        for (int i = 0; i < 6; i++) {
            int x = iconX(i), y = iconY(i);
            drawIcon(g, i, x, y);
            ctr(g, LABELS[i], x + 15, y + 34, 0xFFFFFFFF, 0.7f);
        }
        int un = PhoneNet.totalUnread();
        if (un > 0) {
            int x = iconX(4), y = iconY(4);
            box(g, x + 23, y - 3, x + 34, y + 8, 0xFFE53935);
            ctr(g, String.valueOf(Math.min(un, 9)), x + 28, y - 2, 0xFFFFFFFF, 0.8f);
        }
    }

    private static int iconX(int i) { return 8 + (i % 3) * 36; }
    private static int iconY(int i) { return 58 + (i / 3) * 56; }

    private void drawIcon(GuiGraphicsExtractor g, int i, int x, int y) {
        switch (i) {
            case 0 -> {   // Block Blast
                box(g, x, y, x + 30, y + 30, 0xFF263056);
                box(g, x + 3, y + 3, x + 14, y + 14, 0xFFE74C3C); box(g, x + 16, y + 3, x + 27, y + 14, 0xFFF1C40F);
                box(g, x + 3, y + 16, x + 14, y + 27, 0xFF3498DB); box(g, x + 16, y + 16, x + 27, y + 27, 0xFF2ECC71);
            }
            case 1 -> {   // Flappy Bird
                box(g, x, y, x + 30, y + 30, 0xFF70C5CE);
                box(g, x + 22, y, x + 30, y + 10, 0xFF5AB031); box(g, x + 22, y + 20, x + 30, y + 30, 0xFF5AB031);
                box(g, x + 6, y + 11, x + 18, y + 19, 0xFFF7D308); box(g, x + 14, y + 12, x + 16, y + 14, 0xFF000000);
                box(g, x + 18, y + 14, x + 21, y + 16, 0xFFFF8800);
            }
            case 2 -> {   // Snake
                box(g, x, y, x + 30, y + 30, 0xFF2E7D32);
                box(g, x + 4, y + 20, x + 10, y + 26, 0xFF1E88E5); box(g, x + 10, y + 20, x + 16, y + 26, 0xFF1E88E5);
                box(g, x + 16, y + 20, x + 22, y + 26, 0xFF1E88E5); box(g, x + 16, y + 14, x + 22, y + 20, 0xFF1E88E5);
                box(g, x + 16, y + 8, x + 22, y + 14, 0xFF64B5F6); box(g, x + 5, y + 5, x + 10, y + 10, 0xFFE53935);
            }
            case 3 -> {   // Chess
                box(g, x, y, x + 30, y + 30, 0xFF769656);
                box(g, x, y, x + 15, y + 15, 0xFFEEEED2); box(g, x + 15, y + 15, x + 30, y + 30, 0xFFEEEED2);
                box(g, x + 8, y + 6, x + 22, y + 24, 0xFF222222);
                ctr(g, "K", x + 15, y + 11, 0xFFF8F8F8, 1.2f);
            }
            case 4 -> {   // Messages
                box(g, x, y, x + 30, y + 30, 0xFF2E86DE);
                box(g, x + 5, y + 7, x + 25, y + 21, 0xFFFFFFFF); box(g, x + 8, y + 21, x + 14, y + 25, 0xFFFFFFFF);
                box(g, x + 9, y + 13, x + 12, y + 16, 0xFF2E86DE); box(g, x + 14, y + 13, x + 17, y + 16, 0xFF2E86DE);
                box(g, x + 19, y + 13, x + 22, y + 16, 0xFF2E86DE);
            }
            default -> {  // Settings gear
                box(g, x, y, x + 30, y + 30, 0xFF5A5F6B);
                box(g, x + 9, y + 9, x + 21, y + 21, 0xFFE0E0E0);
                box(g, x + 13, y + 5, x + 17, y + 9, 0xFFE0E0E0); box(g, x + 13, y + 21, x + 17, y + 25, 0xFFE0E0E0);
                box(g, x + 5, y + 13, x + 9, y + 17, 0xFFE0E0E0); box(g, x + 21, y + 13, x + 25, y + 17, 0xFFE0E0E0);
                box(g, x + 13, y + 13, x + 17, y + 17, 0xFF5A5F6B);
            }
        }
    }

    private void drawSettings(GuiGraphicsExtractor g) {
        g.fillGradient(SX, SY, SX + SW, SY + SH, 0xFF14161C, 0xFF0B0C10);
        ctr(g, "Wallpaper", SW / 2, 10, 0xFFFFFFFF, 1.3f);
        for (int i = 0; i < WALLS.length; i++) {
            int x = 4 + (i % 3) * 38, y = 34 + (i / 3) * 66;
            if (i == wallpaper) box(g, x - 2, y - 2, x + 36, y + 56, 0xFFFFFFFF);
            g.fillGradient(SX + x, SY + y, SX + x + 34, SY + y + 54, WALLS[i][0], WALLS[i][1]);
            ctr(g, WALL_NAMES[i], x + 17, y + 58, 0xFFFFFFFF, 0.75f);
        }
        ctr(g, "Tap one to apply it", SW / 2, 180, 0xFFAAAAAA, 0.8f);
    }

    // ---------- input ----------
    @Override
    public boolean keyPressed(KeyEvent e) {
        boolean typing = app == App.MESSAGES && messages.open != null;
        if (typing) {
            if (messages.key(e.key())) return true;   // swallow keys so typing never triggers shortcuts
        } else if (PhoneModClient.OPEN_PHONE.matches(e)) { startClose(); return true; }
        if (app == App.FLAPPY && e.key() == GLFW.GLFW_KEY_SPACE) { flappy.flap(); return true; }
        if (app == App.SNAKE && snake.key(e.key())) return true;
        return super.keyPressed(e);   // Esc -> onClose()
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (app == App.MESSAGES && messages.open != null) {
            messages.typed(Character.toString(e.codepoint()));
            return true;
        }
        return super.charTyped(e);
    }

    private float localX(double mx) { return (float) ((mx - pcx) / scale) + PW / 2f; }
    private float localY(double my) { return (float) ((my - pcy) / scale) + PH / 2f; }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (closeNs >= 0 || e.button() != 0 || progress(System.nanoTime()) < 0.9f) return true;
        float lx = localX(e.x()), ly = localY(e.y());
        if (app != App.HOME && ly > SY + SH && ly < PH) { app = App.HOME; return true; } // home bar area
        float x = lx - SX, y = ly - SY;
        switch (app) {
            case HOME -> {
                for (int i = 0; i < 6; i++) {
                    if (x >= iconX(i) && x <= iconX(i) + 30 && y >= iconY(i) && y <= iconY(i) + 30) { openApp(i); break; }
                }
            }
            case FLAPPY -> flappy.flap();
            case BLAST -> blast.press(x, y);
            case SNAKE -> snake.press(x, y);
            case CHESS -> {
                int r = chess.buttons(x, y);
                if (r == 2) chess = new ChessGame(null, true);
                else if (r == 0) chess.click(x, y);
            }
            case MESSAGES -> {
                messages.click(x, y);
                if (messages.pendingChess != null) {
                    chess = messages.pendingChess;
                    messages.pendingChess = null;
                    app = App.CHESS;
                }
            }
            case SETTINGS -> {
                for (int i = 0; i < WALLS.length; i++) {
                    int sx = 4 + (i % 3) * 38, sy = 34 + (i / 3) * 66;
                    if (x >= sx && x <= sx + 34 && y >= sy && y <= sy + 54) { wallpaper = i; saveWallpaper(); }
                }
            }
        }
        return true;
    }

    private void openApp(int i) {
        switch (i) {
            case 0 -> { blast.reset(); app = App.BLAST; }
            case 1 -> { flappy.reset(); app = App.FLAPPY; }
            case 2 -> { snake.reset(); app = App.SNAKE; }
            case 3 -> {
                if (chess == null || chess.opponent != null) chess = new ChessGame(null, true);
                app = App.CHESS;
            }
            case 4 -> app = App.MESSAGES;
            default -> app = App.SETTINGS;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        blast.release(localX(e.x()) - SX, localY(e.y()) - SY);
        return true;
    }
}
