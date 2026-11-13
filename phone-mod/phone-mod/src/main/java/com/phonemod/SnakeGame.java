package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

final class SnakeGame {
    static final int COLS = 14, ROWS = 19, CELL = 8, GX = 3, GY = 30;

    private final Deque<int[]> body = new ArrayDeque<>(); // head first
    private final Random rnd = new Random();
    private int dx, dy, ndx, ndy, fx, fy, score, best;
    private float acc;
    private boolean started, dead;

    SnakeGame() { reset(); }

    void reset() {
        body.clear();
        body.addLast(new int[]{5, 9});
        body.addLast(new int[]{4, 9});
        body.addLast(new int[]{3, 9});
        dx = 1; dy = 0; ndx = 1; ndy = 0;
        score = 0; acc = 0; started = false; dead = false;
        placeFood();
    }

    private void placeFood() {
        while (true) {
            fx = rnd.nextInt(COLS); fy = rnd.nextInt(ROWS);
            boolean free = true;
            for (int[] s : body) if (s[0] == fx && s[1] == fy) { free = false; break; }
            if (free) return;
        }
    }

    private void steer(int x, int y) {
        if (dead) return;
        if (x == -dx && y == -dy) return;   // no instant U-turn
        ndx = x; ndy = y; started = true;
    }

    boolean key(int k) {
        if (dead) {
            if (k == GLFW.GLFW_KEY_SPACE || k == GLFW.GLFW_KEY_ENTER) { reset(); return true; }
            return false;
        }
        switch (k) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> steer(0, -1);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> steer(0, 1);
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> steer(-1, 0);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> steer(1, 0);
            default -> { return false; }
        }
        return true;
    }

    // on-screen D-pad: up (54,183) left (43,193) down (54,203) right (65,193), 10x10 each
    private static boolean in(float x, float y, int bx, int by) { return x >= bx && x <= bx + 10 && y >= by && y <= by + 10; }

    void press(float x, float y) {
        if (dead) { reset(); return; }
        if (in(x, y, 54, 183)) steer(0, -1);
        else if (in(x, y, 54, 203)) steer(0, 1);
        else if (in(x, y, 43, 193)) steer(-1, 0);
        else if (in(x, y, 65, 193)) steer(1, 0);
    }

    void update(float dt) {
        if (!started || dead) return;
        acc += dt;
        float interval = Math.max(0.07f, 0.16f - score * 0.003f);
        while (acc >= interval && !dead) { acc -= interval; step(); }
    }

    private void step() {
        dx = ndx; dy = ndy;
        int[] h = body.peekFirst();
        int nx = h[0] + dx, ny = h[1] + dy;
        if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) { dead = true; return; }
        boolean eat = nx == fx && ny == fy;
        int size = body.size(), i = 0;
        for (int[] s : body) {
            boolean tail = i++ == size - 1;
            if (s[0] == nx && s[1] == ny && !(tail && !eat)) { dead = true; return; }
        }
        body.addFirst(new int[]{nx, ny});
        if (eat) { score++; best = Math.max(best, score); placeFood(); }
        else body.removeLast();
    }

    void draw(GuiGraphicsExtractor g) {
        PhoneScreen.box(g, 0, 0, PhoneScreen.SW, PhoneScreen.SH, 0xFF1B2A1E);
        PhoneScreen.txt(g, "Score " + score, 8, 8, 0xFFFFFFFF);
        PhoneScreen.txt(g, "Best " + best, 8, 18, 0xFFAAAAAA);

        PhoneScreen.box(g, GX - 2, GY - 2, GX + COLS * CELL + 2, GY + ROWS * CELL + 2, 0xFF0D1710);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                PhoneScreen.box(g, GX + c * CELL, GY + r * CELL, GX + (c + 1) * CELL, GY + (r + 1) * CELL,
                        ((r + c) & 1) == 0 ? 0xFF2E7D32 : 0xFF388E3C);

        // food
        PhoneScreen.box(g, GX + fx * CELL + 1, GY + fy * CELL + 2, GX + (fx + 1) * CELL - 1, GY + (fy + 1) * CELL - 1, 0xFFE53935);
        PhoneScreen.box(g, GX + fx * CELL + 3, GY + fy * CELL, GX + fx * CELL + 5, GY + fy * CELL + 2, 0xFF8D6E63);

        // snake
        boolean head = true;
        for (int[] s : body) {
            int x = GX + s[0] * CELL, y = GY + s[1] * CELL;
            PhoneScreen.box(g, x, y, x + CELL, y + CELL, head ? 0xFF42A5F5 : 0xFF1565C0);
            PhoneScreen.box(g, x + 1, y + 1, x + CELL - 1, y + CELL - 1, head ? 0xFF64B5F6 : 0xFF1E88E5);
            if (head) {
                PhoneScreen.box(g, x + 2, y + 2, x + 4, y + 4, 0xFF000000);
                PhoneScreen.box(g, x + 5, y + 2, x + 7, y + 4, 0xFF000000);
            }
            head = false;
        }

        // D-pad
        int pad = 0xFF37474F;
        PhoneScreen.box(g, 54, 183, 64, 193, pad); PhoneScreen.txt(g, "^", 57, 185, 0xFFFFFFFF);
        PhoneScreen.box(g, 43, 193, 53, 203, pad); PhoneScreen.txt(g, "<", 46, 195, 0xFFFFFFFF);
        PhoneScreen.box(g, 54, 203, 64, 213, pad); PhoneScreen.txt(g, "v", 57, 205, 0xFFFFFFFF);
        PhoneScreen.box(g, 65, 193, 75, 203, pad); PhoneScreen.txt(g, ">", 68, 195, 0xFFFFFFFF);

        if (!started && !dead) PhoneScreen.ctr(g, "Arrows / WASD / pad", PhoneScreen.SW / 2, 100, 0xFFFFFFFF, 0.9f);
        if (dead) {
            PhoneScreen.box(g, 10, 70, PhoneScreen.SW - 10, 130, 0xDD000000);
            PhoneScreen.ctr(g, "Game Over", PhoneScreen.SW / 2, 80, 0xFFFF5555, 1.4f);
            PhoneScreen.ctr(g, "Score " + score, PhoneScreen.SW / 2, 98, 0xFFFFFFFF, 1f);
            PhoneScreen.ctr(g, "Tap to retry", PhoneScreen.SW / 2, 112, 0xFFAAAAAA, 0.9f);
        }
    }
}
