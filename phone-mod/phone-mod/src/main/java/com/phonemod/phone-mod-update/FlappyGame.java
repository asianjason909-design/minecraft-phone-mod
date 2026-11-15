package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class FlappyGame {
    static final int W = PhoneScreen.SW, GROUND = 196, PIPE_W = 20;
    static final float GAP = 58f, GRAV = 520f, FLAP = -165f, SPEED = 55f, BX = 30f;

    private final List<float[]> pipes = new ArrayList<>(); // {x, gapTop, scored}
    private final Random rnd = new Random();
    private float by, vy, spawn, t, deadFor;
    private int score, best;
    private boolean started, dead;

    FlappyGame() { reset(); }

    int getBest() { return best; }
    void setBest(int b) { best = b; }
    int getScore() { return score; }
    boolean isDead() { return dead; }

    void reset() {
        pipes.clear();
        by = 90; vy = 0; spawn = 1.2f; score = 0; deadFor = 0;
        started = false; dead = false;
    }

    void flap() {
        if (dead) { if (deadFor > 0.35f) reset(); return; }
        started = true;
        vy = FLAP;
    }

    void update(float dt) {
        t += dt;
        if (!started) { by = 90 + (float) Math.sin(t * 5) * 4; return; }
        vy += GRAV * dt;
        by += vy * dt;
        if (dead) { deadFor += dt; by = Math.min(by, GROUND - 8); return; }

        spawn -= dt;
        if (spawn <= 0) {
            spawn = 1.7f;
            pipes.add(new float[]{W, 28 + rnd.nextFloat() * (GROUND - GAP - 56), 0});
        }
        for (float[] p : pipes) {
            p[0] -= SPEED * dt;
            if (p[2] == 0 && p[0] + PIPE_W < BX) { p[2] = 1; score++; best = Math.max(best, score); }
            if (BX + 10 > p[0] && BX < p[0] + PIPE_W && (by < p[1] || by + 8 > p[1] + GAP)) dead = true;
        }
        pipes.removeIf(p -> p[0] < -PIPE_W - 2);
        if (by < 0) { by = 0; vy = 0; }
        if (by + 8 >= GROUND) { by = GROUND - 8; dead = true; }
    }

    void draw(GuiGraphicsExtractor g) {
        PhoneScreen.box(g, 0, 0, W, GROUND, 0xFF70C5CE);
        PhoneScreen.box(g, 10, 150, 40, 160, 0xFF8FD6DD);   // little cloud-ish hills
        PhoneScreen.box(g, 60, 160, 118, 170, 0xFF8FD6DD);

        for (float[] p : pipes) {
            float x = p[0], gt = p[1], gb = p[1] + GAP;
            PhoneScreen.box(g, x - 1, 0, x + PIPE_W + 1, gt - 7, 0xFF3A7A1E);
            PhoneScreen.box(g, x, 0, x + PIPE_W, gt - 8, 0xFF5AB031);
            PhoneScreen.box(g, x - 3, gt - 9, x + PIPE_W + 3, gt, 0xFF3A7A1E);
            PhoneScreen.box(g, x - 2, gt - 8, x + PIPE_W + 2, gt - 1, 0xFF73BF2E);
            PhoneScreen.box(g, x - 1, gb + 7, x + PIPE_W + 1, GROUND, 0xFF3A7A1E);
            PhoneScreen.box(g, x, gb + 8, x + PIPE_W, GROUND, 0xFF5AB031);
            PhoneScreen.box(g, x - 3, gb, x + PIPE_W + 3, gb + 9, 0xFF3A7A1E);
            PhoneScreen.box(g, x - 2, gb + 1, x + PIPE_W + 2, gb + 8, 0xFF73BF2E);
        }

        PhoneScreen.box(g, 0, GROUND, W, GROUND + 4, 0xFF73BF2E);
        PhoneScreen.box(g, 0, GROUND + 4, W, PhoneScreen.SH, 0xFFDED895);

        // bird
        PhoneScreen.box(g, BX, by, BX + 10, by + 8, 0xFFF7D308);
        PhoneScreen.box(g, BX + 1, by + 4, BX + 6, by + 7, 0xFFE0A500);
        PhoneScreen.box(g, BX + 6, by + 1, BX + 9, by + 4, 0xFFFFFFFF);
        PhoneScreen.box(g, BX + 8, by + 2, BX + 9, by + 3, 0xFF000000);
        PhoneScreen.box(g, BX + 10, by + 4, BX + 13, by + 6, 0xFFFF8800);

        PhoneScreen.ctr(g, String.valueOf(score), W / 2, 14, 0xFFFFFFFF, 2.5f);
        if (!started) PhoneScreen.ctr(g, "Tap / Space to flap", W / 2, 120, 0xFFFFFFFF, 0.9f);
        if (dead) {
            PhoneScreen.box(g, 14, 70, W - 14, 124, 0xCC000000);
            PhoneScreen.ctr(g, "Game Over", W / 2, 78, 0xFFFF5555, 1.4f);
            PhoneScreen.ctr(g, "Score " + score + "  Best " + best, W / 2, 96, 0xFFFFFFFF, 0.9f);
            PhoneScreen.ctr(g, "Tap to retry", W / 2, 109, 0xFFAAAAAA, 0.9f);
        }
    }
}
