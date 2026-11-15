package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Dino Run */
final class DinoApp extends Arcade {
    static final int GY = 160;
    float y, vy, speed, dist, spawn, anim;
    boolean duck;
    final List<float[]> obs = new ArrayList<>();   // x, w, h, bird(0/1)

    DinoApp() { super("dino", "Dino Run", "DINO", 0xFF6D6D6D); scoreSfx = false; reset(); }

    @Override void reset() { y = 0; vy = 0; speed = 90; dist = 0; score = 0; over = false; spawn = 1f; obs.clear(); }
    @Override void tap(float x, float yy) { jump(); }
    @Override boolean key(int k) {
        if (k == K_SPACE || k == K_UP || k == K_W) { if (over) reset(); else jump(); return true; }
        return false;
    }
    private void jump() { if (y == 0) { vy = 250; Sfx.play('j'); } }

    @Override void step(float dt) {
        duck = goDown() && y == 0;
        vy -= 700 * dt; y += vy * dt;
        if (y <= 0) { y = 0; vy = 0; }
        speed = 90 + dist * 0.02f;
        dist += speed * dt; score = (int) (dist / 10); anim += dt * 10;
        spawn -= dt;
        if (spawn <= 0) {
            spawn = rnd(0.9f, 1.7f) * 100f / speed * 1.4f;
            if (score > 30 && RND.nextFloat() < 0.3f) obs.add(new float[]{W + 10, 12, 8, 1});
            else obs.add(new float[]{W + 10, rnd(7, 13), rnd(12, 22), 0});
        }
        float dh = duck ? 10 : 18;
        for (float[] o : obs) {
            o[0] -= speed * dt;
            float oy = o[3] == 1 ? GY - 13 - o[2] : GY - o[2];
            if (overlap(18, GY - y - dh, 12, dh, o[0] + 1, oy, o[1] - 2, o[2])) die();
        }
        obs.removeIf(o -> o[0] < -20);
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFFF4F4F4);
        box(g, 0, GY, W, GY + 1, 0xFF555555);
        box(g, 0, GY + 8, W, GY + 9, 0xFFCCCCCC);
        hud(g, "Score " + score, "Best " + best);
        float dh = duck ? 10 : 18;
        float top = GY - y - dh;
        box(g, 18, top, 30, GY - y, 0xFF444444);
        if (!duck) box(g, 24, top - 3, 32, top + 5, 0xFF444444);
        box(g, 28, top + 1, 30, top + 3, 0xFFFFFFFF);
        if (y == 0) { boolean f = ((int) anim & 1) == 0; box(g, f ? 19 : 25, GY - 3, f ? 22 : 28, GY, 0xFF222222); }
        for (float[] o : obs) {
            if (o[3] == 1) {
                float oy = GY - 13 - o[2];
                box(g, o[0], oy + 2, o[0] + o[1], oy + 6, 0xFF444444);
                box(g, o[0] + 2, oy + (((int) anim & 1) == 0 ? 0 : 6), o[0] + 7, oy + (((int) anim & 1) == 0 ? 3 : 8), 0xFF444444);
            } else {
                box(g, o[0] + o[1] / 2 - 2, GY - o[2], o[0] + o[1] / 2 + 2, GY, 0xFF2E7D32);
                box(g, o[0], GY - o[2] * 0.7f, o[0] + o[1], GY - o[2] * 0.5f, 0xFF2E7D32);
            }
        }
        if (over) overlay(g, "Game Over", "Score " + score);
        else if (score == 0 && dist < 5) ctr(g, "Tap / Space = jump, Down = duck", W / 2, 60, GRAY, 0.8f);
    }
}

/** Pong vs the computer (first to 7). */
final class PongApp extends Arcade {
    float bx, by, vx, vy, px, ax;
    int ai;

    PongApp() { super("pong", "Pong", "PONG", 0xFF222222); reset(); }

    @Override void reset() { score = 0; ai = 0; over = false; px = W / 2f; ax = W / 2f; serve(1); }
    private void serve(int dir) { bx = W / 2f; by = H / 2f; vy = 85 * dir; vx = rnd(-45, 45); }

    @Override void step(float dt) {
        if (goLeft() || goRight()) px += (goRight() ? 1 : 0) * 120 * dt - (goLeft() ? 1 : 0) * 120 * dt;
        else px = mx;
        px = clamp(px, 12, W - 12);
        float target = bx + (by < H / 2f ? 0 : 0);
        ax += clamp(target - ax, -62 * dt, 62 * dt);
        ax = clamp(ax, 12, W - 12);
        bx += vx * dt; by += vy * dt;
        if (bx < 2) { bx = 2; vx = Math.abs(vx); }
        if (bx > W - 2) { bx = W - 2; vx = -Math.abs(vx); }
        float sp = Math.min(220, (float) Math.hypot(vx, vy));
        if (vy > 0 && by + 2 >= H - 18 && by - 2 <= H - 14 && Math.abs(bx - px) <= 14) {
            vy = -Math.abs(vy) * 1.06f; vx = (bx - px) * 5.5f; by = H - 19;
        }
        if (vy < 0 && by - 2 <= 18 && by + 2 >= 14 && Math.abs(bx - ax) <= 14) {
            vy = Math.abs(vy) * 1.04f; vx = (bx - ax) * 5.5f; by = 19;
        }
        if (Math.abs(vy) > 220) vy = Math.signum(vy) * 220;
        if (by > H + 4) { ai++; serve(-1); }
        if (by < -4) { score++; serve(1); }
        if (score >= 7 || ai >= 7) die();
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF0B0B10);
        for (int x = 0; x < W; x += 8) box(g, x, H / 2f - 1, x + 4, H / 2f + 1, 0xFF444444);
        box(g, ax - 12, 14, ax + 12, 18, 0xFFE57373);
        box(g, px - 12, H - 18, px + 12, H - 14, 0xFF81C784);
        box(g, bx - 2, by - 2, bx + 2, by + 2, 0xFFFFFFFF);
        ctr(g, String.valueOf(ai), W / 2, 60, 0xFF666666, 2f);
        ctr(g, String.valueOf(score), W / 2, 140, 0xFF666666, 2f);
        hud(g, "You " + score + " - CPU " + ai, null);
        if (over) overlay(g, score >= 7 ? "You win!" : "You lose", score + " - " + ai);
    }
}

/** Brick Breaker */
final class BrickApp extends Arcade {
    static final int COLS = 8, ROWS = 5, BW = 14, BH = 6, BX = 3, BY = 28;
    final boolean[][] bricks = new boolean[ROWS][COLS];
    float px, bx, by, vx, vy;
    boolean launched;
    int lives, level;

    BrickApp() { super("brick", "Brick Breaker", "BRK", 0xFFE65100); reset(); }

    @Override void reset() { score = 0; lives = 3; level = 1; over = false; fill(); stick(); px = W / 2f; }
    private void fill() { for (boolean[] r : bricks) java.util.Arrays.fill(r, true); }
    private void stick() { launched = false; }
    private int left() { int n = 0; for (boolean[] r : bricks) for (boolean b : r) if (b) n++; return n; }

    @Override void tap(float x, float y) {
        if (!launched) { launched = true; Sfx.play('s'); float sp = 110 + level * 10; vx = rnd(-40, 40); vy = -sp; }
    }
    @Override boolean key(int k) { if (k == K_SPACE) { tap(0, 0); return true; } return false; }

    @Override void step(float dt) {
        if (goLeft() || goRight()) px += ((goRight() ? 1 : 0) - (goLeft() ? 1 : 0)) * 130 * dt; else px = mx;
        px = clamp(px, 13, W - 13);
        if (!launched) { bx = px; by = 193; return; }
        bx += vx * dt; by += vy * dt;
        if (bx < 2) { bx = 2; vx = Math.abs(vx); }
        if (bx > W - 2) { bx = W - 2; vx = -Math.abs(vx); }
        if (by < 22) { by = 22; vy = Math.abs(vy); }
        if (vy > 0 && by + 2 >= 196 && by - 2 <= 200 && Math.abs(bx - px) <= 15) {
            vy = -Math.abs(vy); vx = (bx - px) * 6; by = 195;
        }
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                if (!bricks[r][c]) continue;
                float x0 = BX + c * BW, y0 = BY + r * (BH + 2);
                if (bx + 2 > x0 && bx - 2 < x0 + BW - 1 && by + 2 > y0 && by - 2 < y0 + BH) {
                    bricks[r][c] = false; score += 10;
                    float ox = Math.min(bx + 2 - x0, x0 + BW - (bx - 2)), oy = Math.min(by + 2 - y0, y0 + BH - (by - 2));
                    if (ox < oy) vx = -vx; else vy = -vy;
                    r = ROWS; break;
                }
            }
        if (left() == 0) { level++; fill(); stick(); }
        if (by > H + 4) { lives--; if (lives <= 0) die(); else stick(); }
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF101826);
        int[] cols = {0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5};
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (bricks[r][c]) box(g, BX + c * BW, BY + r * (BH + 2), BX + c * BW + BW - 1, BY + r * (BH + 2) + BH, cols[r]);
        box(g, px - 13, 198, px + 13, 202, 0xFFEEEEEE);
        box(g, bx - 2, by - 2, bx + 2, by + 2, 0xFFFFFFFF);
        hud(g, "Score " + score, "Lives " + lives);
        if (!launched && !over) ctr(g, "Tap to launch", W / 2, 150, GRAY, 0.9f);
        if (over) overlay(g, "Game Over", "Score " + score);
    }
}

/** Paddle Ball: keep the ball(s) alive, gets faster and adds balls. */
final class PaddleApp extends Arcade {
    float px;
    final List<float[]> balls = new ArrayList<>();   // x, y, vx, vy

    PaddleApp() { super("paddle", "Paddle Ball", "PAD", 0xFF00838F); reset(); }

    @Override void reset() { score = 0; over = false; px = W / 2f; balls.clear(); addBall(); }
    private void addBall() { balls.add(new float[]{rnd(20, W - 20), 40, rnd(-50, 50), 90}); }

    @Override void step(float dt) {
        if (goLeft() || goRight()) px += ((goRight() ? 1 : 0) - (goLeft() ? 1 : 0)) * 140 * dt; else px = mx;
        px = clamp(px, 14, W - 14);
        for (float[] b : balls) {
            b[0] += b[2] * dt; b[1] += b[3] * dt;
            if (b[0] < 3) { b[0] = 3; b[2] = Math.abs(b[2]); }
            if (b[0] > W - 3) { b[0] = W - 3; b[2] = -Math.abs(b[2]); }
            if (b[1] < 3) { b[1] = 3; b[3] = Math.abs(b[3]); }
            if (b[3] > 0 && b[1] + 3 >= 198 && b[1] - 3 <= 203 && Math.abs(b[0] - px) <= 17) {
                b[3] = -(Math.abs(b[3]) + 5); b[2] = (b[0] - px) * 7; b[1] = 197; score++;
                if ((score == 10 || score == 25) && balls.size() < 3) { addBall(); break; }
            }
            if (b[1] > H + 4) { die(); return; }
        }
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF0A1F24);
        box(g, px - 14, 200, px + 14, 204, 0xFF4DD0E1);
        for (float[] b : balls) box(g, b[0] - 3, b[1] - 3, b[0] + 3, b[1] + 3, 0xFFFFFFFF);
        hud(g, "Hits " + score, "Best " + best);
        if (over) overlay(g, "Game Over", "Hits " + score);
    }
}

/** Helicopter: hold to rise, release to fall. */
final class HeliApp extends Arcade {
    static final int COLW = 4;
    final List<float[]> cols = new ArrayList<>();   // top, bottom, obstacleTop(-1 none), obstacleBottom
    float hy, hv, off, center, gap, dist;

    HeliApp() { super("heli", "Helicopter", "HELI", 0xFF6A1B9A); scoreSfx = false; reset(); }

    @Override void reset() {
        score = 0; over = false; hy = 100; hv = 0; off = 0; dist = 0; center = 107; gap = 120; cols.clear();
        for (int i = 0; i < W / COLW + 3; i++) cols.add(new float[]{center - gap / 2, center + gap / 2, -1, -1});
    }

    private void addCol() {
        gap = Math.max(62, 120 - dist * 0.012f);
        center = clamp(center + rnd(-7, 7), 30 + gap / 2, 190 - gap / 2);
        float[] c = {center - gap / 2, center + gap / 2, -1, -1};
        if (RND.nextInt(55) == 0) {
            float h = rnd(14, 24), oy = rnd(c[0] + 8, c[1] - 8 - h);
            for (int k = 0; k < 4; k++) { float[] cc = k == 0 ? c : new float[]{c[0], c[1], -1, -1}; cc[2] = oy; cc[3] = oy + h; if (k > 0) cols.add(cc); }
        }
        cols.add(c);
    }

    @Override void step(float dt) {
        boolean up = down || held(K_SPACE) || goUp();
        hv += (up ? -420 : 330) * dt; hv = clamp(hv, -170, 190); hy += hv * dt;
        float sp = 70 + dist * 0.01f;
        off += sp * dt; dist += sp * dt; score = (int) (dist / 10);
        while (off >= COLW) { off -= COLW; cols.remove(0); addCol(); }
        int idx = (int) ((22 - 0) / COLW);
        for (int i = idx; i <= idx + 3 && i < cols.size(); i++) {
            float[] c = cols.get(i);
            if (hy < c[0] || hy + 7 > c[1]) die();
            if (c[2] >= 0 && hy + 7 > c[2] && hy < c[3]) die();
        }
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF1A1A2E);
        for (int i = 0; i < cols.size(); i++) {
            float[] c = cols.get(i);
            float x = i * COLW - off;
            box(g, x, 0, x + COLW, c[0], 0xFF3E7C47);
            box(g, x, c[1], x + COLW, H, 0xFF3E7C47);
            if (c[2] >= 0) box(g, x, c[2], x + COLW, c[3], 0xFFB0BEC5);
        }
        box(g, 20, hy, 34, hy + 7, 0xFFFFCA28);
        box(g, 34, hy + 2, 40, hy + 4, 0xFFFFCA28);
        box(g, 22, hy - 2, 36, hy - 1, 0xFFEEEEEE);
        hud(g, "Dist " + score, "Best " + best);
        if (score < 3) ctr(g, "Hold to rise", W / 2, 100, GRAY, 0.9f);
        if (over) overlay(g, "Crashed!", "Distance " + score);
    }
}

/** Retro Race: dodge traffic. */
final class RaceApp extends Arcade {
    static final int RL = 14, RR = 104;
    float px, dist, spawn, speed;
    final List<float[]> cars = new ArrayList<>();   // x, y, color

    RaceApp() { super("race", "Retro Race", "RACE", 0xFFC62828); scoreSfx = false; reset(); }

    @Override void reset() { score = 0; over = false; px = W / 2f; dist = 0; spawn = 0.6f; speed = 100; cars.clear(); }

    @Override void step(float dt) {
        if (goLeft() || goRight()) px += ((goRight() ? 1 : 0) - (goLeft() ? 1 : 0)) * 110 * dt; else px = mx;
        px = clamp(px, RL + 6, RR - 6);
        speed = 100 + dist * 0.01f;
        dist += speed * dt; score = (int) (dist / 10);
        spawn -= dt;
        if (spawn <= 0) {
            spawn = rnd(0.5f, 1.1f) * 110f / speed;
            cars.add(new float[]{rnd(RL + 6, RR - 6), -20, RND.nextInt(3)});
        }
        for (float[] c : cars) {
            c[1] += (speed * 0.55f) * dt;
            if (overlap(px - 5, 180, 10, 16, c[0] - 5, c[1], 10, 16)) die();
        }
        cars.removeIf(c -> c[1] > H + 20);
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF2E7D32);
        box(g, RL, 0, RR, H, 0xFF424242);
        box(g, RL - 2, 0, RL, H, 0xFFEEEEEE); box(g, RR, 0, RR + 2, H, 0xFFEEEEEE);
        float off = dist % 24;
        for (int y = -24; y < H; y += 24) box(g, W / 2f - 1, y + off, W / 2f + 1, y + off + 12, 0xFFFFEB3B);
        int[] cc = {0xFF1E88E5, 0xFFFDD835, 0xFF8E24AA};
        for (float[] c : cars) { box(g, c[0] - 5, c[1], c[0] + 5, c[1] + 16, cc[(int) c[2]]); box(g, c[0] - 3, c[1] + 11, c[0] + 3, c[1] + 15, 0xFF90CAF9); }
        box(g, px - 5, 180, px + 5, 196, 0xFFE53935);
        box(g, px - 3, 183, px + 3, 187, 0xFF90CAF9);
        hud(g, "Dist " + score, "Best " + best);
        if (over) overlay(g, "Crash!", "Distance " + score);
    }
}

/** Doodle Jump */
final class DoodleApp extends Arcade {
    float px, py, vy, climbed;
    final List<float[]> plats = new ArrayList<>();   // x, y, moving(0/1), dir

    DoodleApp() { super("doodle", "Doodle Jump", "DJ", 0xFF7CB342); scoreSfx = false; reset(); }

    @Override void reset() {
        score = 0; over = false; px = W / 2f; py = 180; vy = -300; climbed = 0; plats.clear();
        plats.add(new float[]{W / 2f - 13, 196, 0, 1});
        float y = 196;
        while (y > -30) { y -= rnd(22, 48); plats.add(new float[]{rnd(0, W - 26), y, 0, 1}); }
    }

    @Override void step(float dt) {
        if (goLeft() || goRight()) px += ((goRight() ? 1 : 0) - (goLeft() ? 1 : 0)) * 130 * dt; else px += clamp(mx - px, -220 * dt, 220 * dt);
        if (px < -5) px = W + 5; if (px > W + 5) px = -5;
        float prevFeet = py + 12;
        vy += 520 * dt; py += vy * dt;
        float feet = py + 12;
        if (vy > 0) for (float[] p : plats)
            if (prevFeet <= p[1] + 1 && feet >= p[1] && px + 5 > p[0] && px - 5 < p[0] + 26) { vy = -300; Sfx.play('j'); break; }
        if (py < 90) { float d = 90 - py; py = 90; climbed += d; for (float[] p : plats) p[1] += d; }
        score = (int) (climbed / 5);
        for (float[] p : plats) if (p[2] == 1) { p[0] += p[3] * 30 * dt; if (p[0] < 0) { p[0] = 0; p[3] = 1; } if (p[0] > W - 26) { p[0] = W - 26; p[3] = -1; } }
        plats.removeIf(p -> p[1] > H + 6);
        float top = H;
        for (float[] p : plats) top = Math.min(top, p[1]);
        while (top > -10) {
            top -= rnd(22, Math.min(72, 40 + score * 0.05f));
            plats.add(new float[]{rnd(0, W - 26), top, score > 60 && RND.nextInt(4) == 0 ? 1 : 0, RND.nextBoolean() ? 1 : -1});
        }
        if (py > H) die();
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFFFFF8E1);
        for (float[] p : plats) box(g, p[0], p[1], p[0] + 26, p[1] + 5, p[2] == 1 ? 0xFF5C6BC0 : 0xFF66BB6A);
        box(g, px - 5, py, px + 5, py + 12, 0xFF9CCC65);
        box(g, px - 3, py + 2, px - 1, py + 4, 0xFF000000); box(g, px + 1, py + 2, px + 3, py + 4, 0xFF000000);
        box(g, px - 5, py + 10, px + 5, py + 13, 0xFF558B2F);
        hud(g, "Height " + score, "Best " + best);
        if (over) overlay(g, "You fell!", "Height " + score);
    }
}
