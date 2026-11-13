package com.phonemod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

/** The phone: a hand swings it up from the bottom-right, then home screen + apps. */
public class PhoneScreen extends Screen {
    static final int PW = 130, PH = 250;                  // whole phone (virtual size)
    static final int SX = 6, SY = 14, SW = 118, SH = 214; // display rectangle

    private enum App { HOME, FLAPPY, BLAST, SNAKE, SETTINGS }

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

        // the arm holding the phone (drawn first so the phone covers its top)
        g.fill(PW / 2 - 17, PH - 40, PW / 2 + 17, PH + 30, 0xFFC68A5E);   // hand
        g.fill(PW / 2 - 17, PH + 30, PW / 2 + 17, PH + 700, 0xFF2A9D9D);  // sleeve

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
        }

        round(g, PW / 2 - 12, 5, PW / 2 + 12, 10, 0xFF000000);                  // camera pill
        g.fill(PW / 2 - 16, PH - 8, PW / 2 + 16, PH - 6, app == App.HOME ? 0xFF555555 : 0xFFDDDDDD); // home bar

        // fingers wrapped around the sides
        for (int y = 168; y <= 192; y += 12) {
            g.fill(-4, y, 3, y + 9, 0xFFC68A5E);
            g.fill(PW - 3, y, PW + 4, y + 9, 0xFFC68A5E);
        }
        g.pose().popMatrix();
    }

    private void drawHome(GuiGraphicsExtractor g) {
        g.fillGradient(SX, SY, SX + SW, SY + SH, WALLS[wallpaper][0], WALLS[wallpaper][1]);
        LocalTime t = LocalTime.now();
        ctr(g, String.format("%02d:%02d", t.getHour(), t.getMinute()), SW / 2, 14, 0xFFFFFFFF, 2.5f);

        // Block Blast
        box(g, 14, 60, 50, 96, 0xFF263056);
        box(g, 18, 64, 31, 77, 0xFFE74C3C); box(g, 33, 64, 46, 77, 0xFFF1C40F);
        box(g, 18, 79, 31, 92, 0xFF3498DB); box(g, 33, 79, 46, 92, 0xFF2ECC71);
        ctr(g, "Block Blast", 32, 100, 0xFFFFFFFF, 0.8f);

        // Flappy Bird
        box(g, 68, 60, 104, 96, 0xFF70C5CE);
        box(g, 92, 60, 100, 72, 0xFF5AB031); box(g, 92, 84, 100, 96, 0xFF5AB031);
        box(g, 76, 74, 88, 82, 0xFFF7D308); box(g, 84, 75, 86, 77, 0xFF000000); box(g, 88, 77, 91, 79, 0xFFFF8800);
        ctr(g, "Flappy Bird", 86, 100, 0xFFFFFFFF, 0.8f);

        // Snake
        box(g, 14, 118, 50, 154, 0xFF2E7D32);
        box(g, 20, 142, 28, 150, 0xFF1E88E5); box(g, 28, 142, 36, 150, 0xFF1E88E5); box(g, 36, 142, 44, 150, 0xFF1E88E5);
        box(g, 36, 134, 44, 142, 0xFF1E88E5); box(g, 36, 126, 44, 134, 0xFF64B5F6);
        box(g, 38, 128, 40, 130, 0xFF000000); box(g, 21, 126, 27, 132, 0xFFE53935);
        ctr(g, "Snake", 32, 158, 0xFFFFFFFF, 0.8f);

        // Settings (gear)
        box(g, 68, 118, 104, 154, 0xFF5A5F6B);
        box(g, 80, 130, 92, 142, 0xFFE0E0E0);
        box(g, 84, 126, 88, 130, 0xFFE0E0E0); box(g, 84, 142, 88, 146, 0xFFE0E0E0);
        box(g, 76, 134, 80, 138, 0xFFE0E0E0); box(g, 92, 134, 96, 138, 0xFFE0E0E0);
        box(g, 84, 134, 88, 138, 0xFF5A5F6B);
        ctr(g, "Settings", 86, 158, 0xFFFFFFFF, 0.8f);
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
        if (PhoneModClient.OPEN_PHONE.matches(e)) { startClose(); return true; }
        if (app == App.FLAPPY && e.key() == GLFW.GLFW_KEY_SPACE) { flappy.flap(); return true; }
        if (app == App.SNAKE && snake.key(e.key())) return true;
        return super.keyPressed(e);   // Esc -> onClose()
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
                if (hit(x, y, 14, 60)) { blast.reset(); app = App.BLAST; }
                else if (hit(x, y, 68, 60)) { flappy.reset(); app = App.FLAPPY; }
                else if (hit(x, y, 14, 118)) { snake.reset(); app = App.SNAKE; }
                else if (hit(x, y, 68, 118)) { app = App.SETTINGS; }
            }
            case FLAPPY -> flappy.flap();
            case BLAST -> blast.press(x, y);
            case SNAKE -> snake.press(x, y);
            case SETTINGS -> {
                for (int i = 0; i < WALLS.length; i++) {
                    int sx = 4 + (i % 3) * 38, sy = 34 + (i / 3) * 66;
                    if (x >= sx && x <= sx + 34 && y >= sy && y <= sy + 54) { wallpaper = i; saveWallpaper(); }
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        blast.release(localX(e.x()) - SX, localY(e.y()) - SY);
        return true;
    }

    private static boolean hit(float x, float y, int ix, int iy) {
        return x >= ix && x <= ix + 36 && y >= iy && y <= iy + 36;
    }
}
