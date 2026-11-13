package com.phonemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;

/** The phone: slides up from the bottom, shows a home screen, runs the games. */
public class PhoneScreen extends Screen {
    // Phone is drawn in a fixed virtual size and scaled to fit the window.
    static final int PW = 130, PH = 250;            // whole phone
    static final int SX = 6, SY = 14, SW = 118, SH = 214; // display rectangle

    private enum App { HOME, FLAPPY, BLAST }

    private App app = App.HOME;
    private final long openNs = System.nanoTime();
    private long closeNs = -1, lastNs = openNs;
    private float closeFrom = 1f;
    private final FlappyGame flappy = new FlappyGame();
    private final BlockBlastGame blast = new BlockBlastGame();
    private float scale = 1f, ox, oy;

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
            float t = Math.min(1f, (now - closeNs) / 1e9f / 0.28f);
            return closeFrom * (1f - t * t * t);          // ease-in, slide back down
        }
        float t = Math.min(1f, (now - openNs) / 1e9f / 0.45f);
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;           // ease-out-back (slight overshoot)
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
        ox = (width - PW * scale) / 2f;
        float restY = (height - PH * scale) / 2f;
        oy = restY + (1f - p) * (height - restY + 8f);
        float lx = (mx - ox) / scale - SX, ly = (my - oy) / scale - SY;

        g.fill(0, 0, width, height, ((int) (110 * Math.max(0f, Math.min(1f, p)))) << 24);

        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(scale, scale);

        round(g, 0, 0, PW, PH, 0xFF3A3A44);
        round(g, 1, 1, PW - 1, PH - 1, 0xFF111114);
        g.fill(SX, SY, SX + SW, SY + SH, 0xFF000000);

        boolean live = closeNs < 0 && p > 0.9f;
        switch (app) {
            case HOME -> drawHome(g);
            case FLAPPY -> { if (live) flappy.update(dt); flappy.draw(g); }
            case BLAST -> blast.draw(g, lx, ly);
        }

        round(g, PW / 2 - 12, 5, PW / 2 + 12, 10, 0xFF000000);                  // camera pill
        g.fill(PW / 2 - 16, PH - 8, PW / 2 + 16, PH - 6, app == App.HOME ? 0xFF555555 : 0xFFDDDDDD); // home bar
        g.pose().popMatrix();
    }

    private void drawHome(GuiGraphicsExtractor g) {
        g.fillGradient(SX, SY, SX + SW, SY + SH, 0xFF1B2A49, 0xFF7A3FA0);
        LocalTime t = LocalTime.now();
        ctr(g, String.format("%02d:%02d", t.getHour(), t.getMinute()), SW / 2, 14, 0xFFFFFFFF, 2.5f);

        // Block Blast icon
        box(g, 14, 60, 50, 96, 0xFF263056);
        box(g, 18, 64, 31, 77, 0xFFE74C3C); box(g, 33, 64, 46, 77, 0xFFF1C40F);
        box(g, 18, 79, 31, 92, 0xFF3498DB); box(g, 33, 79, 46, 92, 0xFF2ECC71);
        ctr(g, "Block Blast", 32, 100, 0xFFFFFFFF, 0.8f);

        // Flappy Bird icon
        box(g, 68, 60, 104, 96, 0xFF70C5CE);
        box(g, 92, 60, 100, 72, 0xFF5AB031); box(g, 92, 84, 100, 96, 0xFF5AB031);
        box(g, 76, 74, 88, 82, 0xFFF7D308); box(g, 84, 75, 86, 77, 0xFF000000); box(g, 88, 77, 91, 79, 0xFFFF8800);
        ctr(g, "Flappy Bird", 86, 100, 0xFFFFFFFF, 0.8f);
    }

    // ---------- input ----------
    @Override
    public boolean keyPressed(KeyEvent e) {
        if (PhoneModClient.OPEN_PHONE.matches(e)) { startClose(); return true; }
        if (e.key() == GLFW.GLFW_KEY_SPACE && app == App.FLAPPY) { flappy.flap(); return true; }
        return super.keyPressed(e);   // Esc -> onClose()
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (closeNs >= 0 || e.button() != 0) return true;
        float lx = (float) ((e.x() - ox) / scale), ly = (float) ((e.y() - oy) / scale);
        if (app != App.HOME && ly > SY + SH && ly < PH) { app = App.HOME; return true; } // home bar area
        float x = lx - SX, y = ly - SY;
        switch (app) {
            case HOME -> {
                if (hit(x, y, 14, 60)) { blast.reset(); app = App.BLAST; }
                else if (hit(x, y, 68, 60)) { flappy.reset(); app = App.FLAPPY; }
            }
            case FLAPPY -> flappy.flap();
            case BLAST -> blast.press(x, y);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        float x = (float) ((e.x() - ox) / scale) - SX, y = (float) ((e.y() - oy) / scale) - SY;
        blast.release(x, y);
        return true;
    }

    private static boolean hit(float x, float y, int ix, int iy) {
        return x >= ix && x <= ix + 36 && y >= iy && y <= iy + 36;
    }
}
