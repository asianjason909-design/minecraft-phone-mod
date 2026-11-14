package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Base class for every app on the phone that is a game. Coordinates are display-local (118 x 214). */
abstract class Game {
    static final int K_LEFT = 263, K_RIGHT = 262, K_UP = 265, K_DOWN = 264, K_SPACE = 32,
            K_ENTER = 257, K_BACKSPACE = 259, K_A = 65, K_D = 68, K_W = 87, K_S = 83;
    static final int W = PhoneScreen.SW, H = PhoneScreen.SH;
    static final Random RND = new Random();
    static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA;

    final String id, title, abbr;
    String label;
    final int color;
    boolean resetOnOpen = true;
    float mx, my;                 // mouse position (display-local), set by the phone every frame
    boolean down;                 // left mouse button held
    final Set<Integer> keys = new HashSet<>();

    Game(String id, String title, String abbr, int color) {
        this.id = id; this.title = title; this.abbr = abbr; this.color = color; this.label = title;
    }

    Game lbl(String s) { label = s; return this; }

    void reset() { }
    void update(float dt) { }
    abstract void draw(GuiGraphicsExtractor g);
    void press(float x, float y) { }
    void release(float x, float y) { }
    boolean key(int k) { return false; }
    boolean capturesText() { return false; }
    void typed(String s) { }

    boolean held(int k) { return keys.contains(k); }
    boolean goLeft() { return held(K_LEFT) || held(K_A); }
    boolean goRight() { return held(K_RIGHT) || held(K_D); }
    boolean goUp() { return held(K_UP) || held(K_W); }
    boolean goDown() { return held(K_DOWN) || held(K_S); }

    void icon(GuiGraphicsExtractor g, int x, int y) {
        box(g, x, y, x + 30, y + 30, color);
        ctr(g, abbr, x + 15, abbr.length() <= 2 ? y + 10 : y + 11, WHITE, abbr.length() <= 2 ? 1.6f : 1f);
    }

    // ---------- helpers ----------
    static void box(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int c) { PhoneScreen.box(g, x0, y0, x1, y1, c); }
    static void txt(GuiGraphicsExtractor g, String s, int x, int y, int c) { PhoneScreen.txt(g, s, x, y, c); }
    static void ctr(GuiGraphicsExtractor g, String s, int cx, int y, int c, float sc) { PhoneScreen.ctr(g, s, cx, y, c, sc); }
    static void left(GuiGraphicsExtractor g, String s, int x, int y, int c, float sc) { PhoneScreen.left(g, s, x, y, c, sc); }
    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    static boolean in(float x, float y, float x0, float y0, float x1, float y1) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }
    static boolean overlap(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }
    static float rnd(float lo, float hi) { return lo + RND.nextFloat() * (hi - lo); }

    static void hud(GuiGraphicsExtractor g, String l, String r) {
        txt(g, l, 5, 5, WHITE);
        if (r != null) txt(g, r, W - 5 - net.minecraft.client.Minecraft.getInstance().font.width(r), 5, GRAY);
    }

    static void overlay(GuiGraphicsExtractor g, String a, String b) {
        box(g, 10, 76, W - 10, 134, 0xDD000000);
        ctr(g, a, W / 2, 86, 0xFFFF5555, 1.4f);
        ctr(g, b, W / 2, 106, WHITE, 0.9f);
        ctr(g, "Tap to play again", W / 2, 119, GRAY, 0.8f);
    }
}
