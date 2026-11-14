package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Space Invaders */
final class InvadersApp extends Arcade {
    static final int ROWS = 5, COLS = 8;
    final boolean[][] alive = new boolean[ROWS][COLS];
    float ox, oy, dir, px, cool, enemyCool;
    int lives, level;
    final List<float[]> shots = new ArrayList<>();    // x, y, vy   (vy<0 = player)

    InvadersApp() { super("invaders", "Space Invaders", "INV", 0xFF2E7D32); reset(); }

    @Override void reset() { score = 0; lives = 3; level = 1; over = false; px = W / 2f; shots.clear(); wave(); }
    private void wave() { for (boolean[] r : alive) java.util.Arrays.fill(r, true); ox = 6; oy = 28; dir = 1; cool = 0; enemyCool = 1f; shots.clear(); }
    private int count() { int n = 0; for (boolean[] r : alive) for (boolean b : r) if (b) n++; return n; }
    private void fire() { if (cool <= 0) { shots.add(new float[]{px, 190, -190}); cool = 0.4f; } }

    @Override void tap(float x, float y) { fire(); }
    @Override boolean key(int k) { if (k == K_SPACE) { fire(); return true; } return false; }

    @Override void step(float dt) {
        if (goLeft() || goRight()) px += ((goRight() ? 1 : 0) - (goLeft() ? 1 : 0)) * 110 * dt; else px = mx;
        px = clamp(px, 7, W - 7);
        cool -= dt;
        if (down || held(K_SPACE)) fire();
        int n = count();
        float sp = (14 + (ROWS * COLS - n) * 1.4f + level * 4) * dir;
        ox += sp * dt;
        float minX = 99, maxX = -99, maxY = 0;
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) if (alive[r][c]) {
            float x = ox + c * 13, y = oy + r * 11;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x + 9); maxY = Math.max(maxY, y + 7);
        }
        if ((dir > 0 && maxX > W - 3) || (dir < 0 && minX < 3)) { dir = -dir; oy += 7; }
        if (maxY >= 184) { die(); return; }
        enemyCool -= dt;
        if (enemyCool <= 0 && n > 0) {
            enemyCool = rnd(0.5f, 1.3f);
            int c = RND.nextInt(COLS);
            for (int k = 0; k < COLS; k++) {
                int cc = (c + k) % COLS;
                for (int r = ROWS - 1; r >= 0; r--) if (alive[r][cc]) { shots.add(new float[]{ox + cc * 13 + 4, oy + r * 11 + 8, 90}); k = COLS; break; }
            }
        }
        for (float[] s : shots) {
            s[1] += s[2] * dt;
            if (s[2] < 0) {
                for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) if (alive[r][c]) {
                    float x = ox + c * 13, y = oy + r * 11;
                    if (s[0] > x && s[0] < x + 9 && s[1] > y && s[1] < y + 7) { alive[r][c] = false; s[1] = -99; score += 10 * (ROWS - r); }
                }
            } else if (overlap(s[0] - 1, s[1], 2, 5, px - 7, 192, 14, 6)) { s[1] = 999; lives--; if (lives <= 0) { die(); return; } }
        }
        shots.removeIf(s -> s[1] < -10 || s[1] > H);
        if (count() == 0) { level++; wave(); }
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF05050F);
        int[] cols = {0xFFE91E63, 0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50, 0xFF29B6F6};
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) if (alive[r][c]) {
            float x = ox + c * 13, y = oy + r * 11;
            box(g, x, y + 1, x + 9, y + 6, cols[r]);
            box(g, x + 2, y, x + 7, y + 1, cols[r]);
            box(g, x + 2, y + 2, x + 3, y + 3, 0xFF000000); box(g, x + 6, y + 2, x + 7, y + 3, 0xFF000000);
            box(g, x + (((int) (ox) & 2) == 0 ? 0 : 1), y + 6, x + 2, y + 8, cols[r]);
            box(g, x + 7, y + 6, x + 9, y + 8, cols[r]);
        }
        for (float[] s : shots) box(g, s[0] - 1, s[1], s[0] + 1, s[1] + 5, s[2] < 0 ? 0xFFFFFFFF : 0xFFFF5252);
        box(g, px - 7, 192, px + 7, 198, 0xFF66BB6A); box(g, px - 1, 189, px + 1, 192, 0xFF66BB6A);
        hud(g, "Score " + score, "Lives " + lives);
        if (over) overlay(g, "Invaded!", "Score " + score);
    }
}

/** Galaga: enemies hold formation, then dive at you. */
final class GalagaApp extends Arcade {
    static final class E { float hx, hy, x, y, t, sx, dir; int state; boolean alive = true; }   // state 0 form, 1 dive, 2 return
    final List<E> es = new ArrayList<>();
    final List<float[]> shots = new ArrayList<>();   // x, y, vy
    final float[][] stars = new float[28][3];
    float px, cool, diveCool, time, invuln;
    int lives, wave;

    GalagaApp() {
        super("galaga", "Galaga", "GAL", 0xFF1565C0);
        for (float[] s : stars) { s[0] = rnd(0, W); s[1] = rnd(0, H); s[2] = rnd(15, 50); }
        reset();
    }

    @Override void reset() { score = 0; lives = 3; wave = 1; over = false; px = W / 2f; shots.clear(); invuln = 0; spawn(); }
    private void spawn() {
        es.clear(); shots.clear(); diveCool = 1.5f;
        for (int r = 0; r < 4; r++) for (int c = 0; c < 6; c++) {
            E e = new E(); e.hx = 19 + c * 16; e.hy = 34 + r * 14; e.x = e.hx; e.y = -20 - r * 14; e.state = 2; es.add(e);
        }
    }

    @Override void step(float dt) {
        time += dt; invuln -= dt;
        for (float[] s : stars) { s[1] += s[2] * dt; if (s[1] > H) { s[1] = 0; s[0] = rnd(0, W); } }
        if (goLeft() || goRight()) px += ((goRight() ? 1 : 0) - (goLeft() ? 1 : 0)) * 110 * dt; else px = mx;
        px = clamp(px, 7, W - 7);
        cool -= dt;
        if (cool <= 0) { shots.add(new float[]{px, 190, -230}); cool = 0.28f; }
        int divers = 0, alive = 0;
        for (E e : es) if (e.alive) { alive++; if (e.state == 1) divers++; }
        diveCool -= dt;
        if (diveCool <= 0 && divers < 1 + wave / 2 + 1) {
            diveCool = Math.max(0.5f, 1.6f - wave * 0.12f);
            List<E> pool = new ArrayList<>();
            for (E e : es) if (e.alive && e.state == 0) pool.add(e);
            if (!pool.isEmpty()) { E e = pool.get(RND.nextInt(pool.size())); e.state = 1; e.t = 0; e.sx = e.x; e.dir = RND.nextBoolean() ? 1 : -1; }
        }
        for (E e : es) {
            if (!e.alive) continue;
            float sway = (float) Math.sin(time * 1.5f) * 6;
            if (e.state == 0) { e.x = e.hx + sway; e.y = e.hy; }
            else if (e.state == 1) {
                e.t += dt * (0.7f + wave * 0.05f);
                e.y = e.hy + e.t * 190; e.x = e.sx + (float) Math.sin(e.t * 7) * 28 * e.dir + (px - e.sx) * e.t * 0.5f;
                if (RND.nextInt(160) == 0 && e.y < 150) shots.add(new float[]{e.x, e.y + 6, 95});
                if (e.y > H + 10) { e.state = 2; e.y = -12; e.x = e.hx; }
            } else {
                float tx = e.hx + sway;
                e.y += 110 * dt; e.x += clamp(tx - e.x, -60 * dt, 60 * dt);
                if (e.y >= e.hy) { e.y = e.hy; e.state = 0; }
            }
            if (invuln <= 0 && overlap(px - 6, 190, 12, 7, e.x - 4, e.y, 9, 8)) { e.alive = false; hit(); if (over) return; }
        }
        for (float[] s : shots) {
            s[1] += s[2] * dt;
            if (s[2] < 0) for (E e : es) if (e.alive && s[0] > e.x - 4 && s[0] < e.x + 5 && s[1] > e.y && s[1] < e.y + 8) {
                e.alive = false; s[1] = -99; score += e.state == 1 ? 200 : 100;
            }
            else if (false) { }
            if (s[2] > 0 && invuln <= 0 && overlap(s[0] - 1, s[1], 2, 5, px - 6, 190, 12, 7)) { s[1] = 999; hit(); if (over) return; }
        }
        shots.removeIf(s -> s[1] < -10 || s[1] > H);
        boolean any = false;
        for (E e : es) if (e.alive) any = true;
        if (!any) { wave++; spawn(); }
    }

    private void hit() { lives--; invuln = 2f; if (lives <= 0) die(); }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF03030C);
        for (float[] s : stars) box(g, s[0], s[1], s[0] + 1, s[1] + 1, s[2] > 35 ? 0xFFFFFFFF : 0xFF666688);
        for (E e : es) if (e.alive) {
            int c = e.state == 1 ? 0xFFFF5252 : 0xFF7E57C2;
            box(g, e.x - 4, e.y + 1, e.x + 5, e.y + 7, c);
            box(g, e.x - 6, e.y, e.x - 4, e.y + 5, 0xFFFFD54F); box(g, e.x + 5, e.y, e.x + 7, e.y + 5, 0xFFFFD54F);
            box(g, e.x - 2, e.y + 2, e.x - 1, e.y + 3, 0xFFFFFFFF); box(g, e.x + 2, e.y + 2, e.x + 3, e.y + 3, 0xFFFFFFFF);
        }
        for (float[] s : shots) box(g, s[0] - 1, s[1], s[0] + 1, s[1] + 5, s[2] < 0 ? 0xFFFFFFFF : 0xFFFF8A80);
        if (invuln <= 0 || ((int) (time * 10) & 1) == 0) {
            box(g, px - 1, 188, px + 1, 197, 0xFFE0E0E0);
            box(g, px - 6, 194, px + 6, 198, 0xFFE0E0E0);
            box(g, px - 4, 192, px - 2, 196, 0xFFEF5350); box(g, px + 2, 192, px + 4, 196, 0xFFEF5350);
        }
        hud(g, "Score " + score, "Lives " + lives);
        if (over) overlay(g, "Game Over", "Score " + score);
    }
}

/** Asteroids: aim with the mouse (or A/D), hold to thrust, auto-fire. */
final class AsteroidsApp extends Arcade {
    static final class R { float x, y, vx, vy; int size; }
    final List<R> rocks = new ArrayList<>();
    final List<float[]> bullets = new ArrayList<>();   // x, y, vx, vy, life
    float sx, sy, svx, svy, ang, cool, invuln;
    int lives, level;

    AsteroidsApp() { super("asteroids", "Asteroids", "AST", 0xFF455A64); reset(); }

    @Override void reset() { score = 0; lives = 3; level = 1; over = false; respawn(); bullets.clear(); wave(); }
    private void respawn() { sx = W / 2f; sy = H / 2f; svx = 0; svy = 0; ang = -(float) Math.PI / 2; invuln = 2f; }
    private void wave() {
        rocks.clear();
        for (int i = 0; i < 3 + level; i++) {
            R r = new R(); r.size = 3;
            do { r.x = rnd(0, W); r.y = rnd(0, H); } while (Math.hypot(r.x - sx, r.y - sy) < 50);
            float a = rnd(0, 6.28f), sp = rnd(10, 28); r.vx = (float) Math.cos(a) * sp; r.vy = (float) Math.sin(a) * sp; rocks.add(r);
        }
    }
    private static float rad(int size) { return size == 3 ? 10 : size == 2 ? 6 : 3; }

    @Override void step(float dt) {
        if (goLeft() || goRight()) ang += ((goRight() ? 1 : 0) - (goLeft() ? 1 : 0)) * 3.5f * dt;
        else ang = (float) Math.atan2(my - sy, mx - sx);
        if (down || goUp()) { svx += (float) Math.cos(ang) * 110 * dt; svy += (float) Math.sin(ang) * 110 * dt; }
        float fr = (float) Math.exp(-0.5f * dt); svx *= fr; svy *= fr;
        sx = wrap(sx + svx * dt, W); sy = wrap(sy + svy * dt, H);
        invuln -= dt; cool -= dt;
        if (cool <= 0) { bullets.add(new float[]{sx, sy, (float) Math.cos(ang) * 170 + svx, (float) Math.sin(ang) * 170 + svy, 1.1f}); cool = 0.35f; }
        for (float[] b : bullets) { b[0] = wrap(b[0] + b[2] * dt, W); b[1] = wrap(b[1] + b[3] * dt, H); b[4] -= dt; }
        bullets.removeIf(b -> b[4] <= 0);
        List<R> add = new ArrayList<>();
        for (R r : rocks) {
            r.x = wrap(r.x + r.vx * dt, W); r.y = wrap(r.y + r.vy * dt, H);
            for (float[] b : bullets) if (b[4] > 0 && Math.hypot(b[0] - r.x, b[1] - r.y) < rad(r.size) + 1) {
                b[4] = 0; r.size = -r.size; score += r.size == -3 ? 20 : r.size == -2 ? 50 : 100;
                if (-r.size > 1) for (int k = 0; k < 2; k++) {
                    R n = new R(); n.size = -r.size - 1; n.x = r.x; n.y = r.y;
                    float a = rnd(0, 6.28f), sp = rnd(25, 50); n.vx = (float) Math.cos(a) * sp; n.vy = (float) Math.sin(a) * sp; add.add(n);
                }
                break;
            }
            if (r.size > 0 && invuln <= 0 && Math.hypot(sx - r.x, sy - r.y) < rad(r.size) + 3) { lives--; if (lives <= 0) { die(); return; } respawn(); }
        }
        rocks.removeIf(r -> r.size < 0);
        rocks.addAll(add);
        if (rocks.isEmpty()) { level++; wave(); }
    }
    private static float wrap(float v, float max) { return v < 0 ? v + max : v >= max ? v - max : v; }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF000000);
        for (R r : rocks) {
            float q = rad(r.size);
            int c = 0xFFB0BEC5;
            box(g, r.x - q, r.y - q * 0.55f, r.x + q, r.y + q * 0.55f, c);
            box(g, r.x - q * 0.55f, r.y - q, r.x + q * 0.55f, r.y + q, c);
            box(g, r.x - q * 0.8f, r.y - q * 0.8f, r.x + q * 0.8f, r.y + q * 0.8f, c);
            box(g, r.x - q * 0.3f, r.y - q * 0.3f, r.x + q * 0.3f, r.y + q * 0.3f, 0xFF607D8B);
        }
        for (float[] b : bullets) box(g, b[0] - 1, b[1] - 1, b[0] + 1, b[1] + 1, 0xFFFFFFFF);
        if (invuln <= 0 || ((int) (invuln * 10) & 1) == 0) {
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            for (int k = -4; k <= 5; k++) box(g, sx + ca * k - 1, sy + sa * k - 1, sx + ca * k + 1, sy + sa * k + 1, 0xFFFFFFFF);
            for (int k = 0; k <= 4; k++) {
                float bx = sx - ca * 4 - ca * (-k * 0.0f), by = sy - sa * 4;
                box(g, bx - sa * k - 1, by + ca * k - 1, bx - sa * k + 1, by + ca * k + 1, 0xFFE0E0E0);
                box(g, bx + sa * k - 1, by - ca * k - 1, bx + sa * k + 1, by - ca * k + 1, 0xFFE0E0E0);
            }
        }
        hud(g, "Score " + score, "Lives " + lives);
        if (over) overlay(g, "Game Over", "Score " + score);
    }
}

/** Pac-Man (arrow keys / WASD / on-screen pad). */
final class PacApp extends Arcade {
    static final String[] MAZE = {
        "################",
        "#o.....##.....o#",
        "#.###.#..#.###.#",
        "#..............#",
        "#.##.##..##.##.#",
        "#....#....#....#",
        "####.#....#.####",
        "#.....#..#.....#",
        "#.###.#  #.###.#",
        "#.....#  #.....#",
        "#.###.####.###.#",
        "#..............#",
        "#.##.######.##.#",
        "#..#........#..#",
        "##.#.######.#.##",
        "#....#....#....#",
        "#.####.##.####.#",
        "#o............o#",
        "################"};
    static final int C = 16, R = 19, CS = 7, OX = 3, OY = 22;
    static final int[][] DIR = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};   // up right down left
    static final class Ent { int cx, cy, d = 1; float t; boolean dead; float fright; }
    final char[][] map = new char[R][C];
    final Ent pac = new Ent();
    final Ent[] ghosts = new Ent[3];
    int want = 1, lives, dots, level;
    float frightT, mouth;

    PacApp() { super("pac", "Pac Man", "PAC", 0xFFFFC107); for (int i = 0; i < 3; i++) ghosts[i] = new Ent(); reset(); }

    @Override void reset() { score = 0; lives = 3; level = 1; over = false; loadMaze(); }

    private void loadMaze() {
        dots = 0;
        for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) { map[r][c] = MAZE[r].charAt(c); if (map[r][c] == '.' || map[r][c] == 'o') dots++; }
        place();
    }
    private void place() {
        pac.cx = 7; pac.cy = 11; pac.d = 1; pac.t = 0; want = 1;
        int[][] gp = {{7, 9}, {8, 9}, {7, 8}};
        for (int i = 0; i < 3; i++) { Ent e = ghosts[i]; e.cx = gp[i][0]; e.cy = gp[i][1]; e.d = 0; e.t = -0.4f * i; e.dead = false; }
        frightT = 0;
    }
    static boolean wall(char[][] m, int x, int y) { return x < 0 || y < 0 || x >= C || y >= R || m[y][x] == '#'; }
    private boolean open(int x, int y, int d) { return !wall(map, x + DIR[d][0], y + DIR[d][1]); }

    @Override boolean key(int k) {
        if (k == K_UP || k == K_W) want = 0; else if (k == K_RIGHT || k == K_D) want = 1;
        else if (k == K_DOWN || k == K_S) want = 2; else if (k == K_LEFT || k == K_A) want = 3; else return false;
        return true;
    }
    @Override void tap(float x, float y) {
        if (in(x, y, 54, 168, 64, 178)) want = 0; else if (in(x, y, 65, 178, 75, 188)) want = 1;
        else if (in(x, y, 54, 188, 64, 198)) want = 2; else if (in(x, y, 43, 178, 53, 188)) want = 3;
    }

    @Override void step(float dt) {
        mouth += dt * 12; frightT -= dt;
        float pp = 0.15f;
        pac.t += dt;
        while (pac.t >= pp) {
            pac.t -= pp;
            if (open(pac.cx, pac.cy, want)) pac.d = want;
            if (open(pac.cx, pac.cy, pac.d)) { pac.cx += DIR[pac.d][0]; pac.cy += DIR[pac.d][1]; }
            char ch = map[pac.cy][pac.cx];
            if (ch == '.') { map[pac.cy][pac.cx] = ' '; score += 10; dots--; }
            else if (ch == 'o') { map[pac.cy][pac.cx] = ' '; score += 50; dots--; frightT = 6f; }
            if (dots <= 0) { level++; loadMaze(); return; }
        }
        for (Ent e : ghosts) {
            float gp = (frightT > 0 && !e.dead ? 0.26f : e.dead ? 0.1f : Math.max(0.12f, 0.2f - level * 0.015f));
            e.t += dt;
            while (e.t >= gp) {
                e.t -= gp;
                ghostStep(e);
            }
        }
        for (Ent e : ghosts) {
            if (e.cx == pac.cx && e.cy == pac.cy && !e.dead) {
                if (frightT > 0) { e.dead = true; score += 200; }
                else { lives--; if (lives <= 0) { die(); return; } place(); return; }
            }
        }
    }

    private void ghostStep(Ent e) {
        if (e.dead) {
            int tx = 7, ty = 8, best = -1, bd = 9999;
            for (int d = 0; d < 4; d++) if (open(e.cx, e.cy, d)) { int nx = e.cx + DIR[d][0], ny = e.cy + DIR[d][1], dd = Math.abs(nx - tx) + Math.abs(ny - ty); if (dd < bd) { bd = dd; best = d; } }
            if (best >= 0) { e.d = best; e.cx += DIR[best][0]; e.cy += DIR[best][1]; }
            if (e.cx == 7 && e.cy == 8) e.dead = false;
            return;
        }
        List<Integer> opts = new ArrayList<>();
        for (int d = 0; d < 4; d++) if (open(e.cx, e.cy, d) && d != (e.d + 2) % 4) opts.add(d);
        if (opts.isEmpty()) opts.add((e.d + 2) % 4);
        int pick = opts.get(RND.nextInt(opts.size()));
        if (RND.nextInt(10) < 7) {
            int bd = frightT > 0 ? -1 : 9999, bs = pick;
            for (int d : opts) {
                int nx = e.cx + DIR[d][0], ny = e.cy + DIR[d][1], dd = Math.abs(nx - pac.cx) + Math.abs(ny - pac.cy);
                if (frightT > 0 ? dd > bd : dd < bd) { bd = dd; bs = d; }
            }
            pick = bs;
        }
        e.d = pick; e.cx += DIR[pick][0]; e.cy += DIR[pick][1];
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF000010);
        for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) {
            float x = OX + c * CS, y = OY + r * CS;
            char ch = map[r][c];
            if (ch == '#') { box(g, x, y, x + CS, y + CS, 0xFF1A237E); box(g, x + 1, y + 1, x + CS - 1, y + CS - 1, 0xFF283593); }
            else if (ch == '.') box(g, x + 3, y + 3, x + 4, y + 4, 0xFFFFE0B2);
            else if (ch == 'o') box(g, x + 1, y + 1, x + 6, y + 6, 0xFFFFE0B2);
        }
        float pp = 0.15f;
        float px = OX + (pac.cx + (open(pac.cx, pac.cy, pac.d) ? DIR[pac.d][0] * pac.t / pp : 0)) * CS;
        float py = OY + (pac.cy + (open(pac.cx, pac.cy, pac.d) ? DIR[pac.d][1] * pac.t / pp : 0)) * CS;
        box(g, px, py, px + CS, py + CS, 0xFFFFC107);
        if (((int) mouth & 1) == 0) box(g, px + (pac.d == 3 ? 0 : pac.d == 1 ? 4 : 2), py + (pac.d == 0 ? 0 : pac.d == 2 ? 4 : 2), px + (pac.d == 3 ? 3 : pac.d == 1 ? CS : 5), py + (pac.d == 0 ? 3 : pac.d == 2 ? CS : 5), 0xFF000010);
        int[] gc = {0xFFEF5350, 0xFFF48FB1, 0xFF4DD0E1};
        for (int i = 0; i < 3; i++) {
            Ent e = ghosts[i];
            float x = OX + e.cx * CS, y = OY + e.cy * CS;
            int c = e.dead ? 0xFF90A4AE : frightT > 0 ? (frightT < 2 && ((int) (frightT * 6) & 1) == 0 ? 0xFFFFFFFF : 0xFF3949AB) : gc[i];
            box(g, x, y + 1, x + CS, y + CS, c); box(g, x + 1, y, x + CS - 1, y + 1, c);
            box(g, x + 1, y + 2, x + 3, y + 4, 0xFFFFFFFF); box(g, x + 4, y + 2, x + 6, y + 4, 0xFFFFFFFF);
        }
        hud(g, "Score " + score, "Lives " + lives);
        // pad
        int pad = 0xFF37474F;
        box(g, 54, 168, 64, 178, pad); txt(g, "^", 57, 169, WHITE);
        box(g, 43, 178, 53, 188, pad); txt(g, "<", 46, 179, WHITE);
        box(g, 54, 188, 64, 198, pad); txt(g, "v", 57, 189, WHITE);
        box(g, 65, 178, 75, 188, pad); txt(g, ">", 68, 179, WHITE);
        if (over) overlay(g, "Game Over", "Score " + score);
    }
}

/** Crossy Road: hop forward, dodge cars. Arrows/WASD, or tap left/middle/right. */
final class CrossyApp extends Arcade {
    static final int COLS = 11, CELL = 10, X0 = 4;
    static final class Row { int type; boolean[] tree = new boolean[COLS]; float speed; List<float[]> cars = new ArrayList<>(); }
    final List<Row> rows = new ArrayList<>();
    int pc, pr, maxRow;
    float cam, hop;

    CrossyApp() { super("crossy", "Crossy Road", "XRD", 0xFF8BC34A); reset(); }

    @Override void reset() {
        score = 0; over = false; rows.clear(); pc = 5; pr = 0; maxRow = 0; cam = 0; hop = 0;
        for (int i = 0; i < 40; i++) rows.add(makeRow(i));
    }
    private Row makeRow(int i) {
        Row r = new Row();
        if (i < 3 || RND.nextInt(10) < 4) { r.type = 0; for (int c = 0; c < COLS; c++) r.tree[c] = i >= 3 && RND.nextInt(6) == 0 && c != 5; }
        else {
            r.type = 1; r.speed = rnd(18, 55) * (RND.nextBoolean() ? 1 : -1);
            float x = rnd(0, 40);
            while (x < W + 30) { r.cars.add(new float[]{x, rnd(14, 22)}); x += rnd(40, 80); }
        }
        return r;
    }
    private float sy(float row) { return 150 - (row - cam) * CELL; }

    private void move(int dc, int dr) {
        if (over) return;
        int nc = pc + dc, nr = pr + dr;
        if (nc < 0 || nc >= COLS || nr < 0 || nr < (int) cam) return;
        while (rows.size() <= nr + 20) rows.add(makeRow(rows.size()));
        if (rows.get(nr).type == 0 && rows.get(nr).tree[nc]) return;
        pc = nc; pr = nr; hop = 0.12f;
        if (pr > maxRow) { maxRow = pr; score = maxRow; }
    }
    @Override boolean key(int k) {
        if (k == K_UP || k == K_W || k == K_SPACE) move(0, 1); else if (k == K_DOWN || k == K_S) move(0, -1);
        else if (k == K_LEFT || k == K_A) move(-1, 0); else if (k == K_RIGHT || k == K_D) move(1, 0); else return false;
        return true;
    }
    @Override void tap(float x, float y) { if (y > 190) move(0, -1); else if (x < 40) move(-1, 0); else if (x > 78) move(1, 0); else move(0, 1); }

    @Override void step(float dt) {
        hop = Math.max(0, hop - dt);
        cam = Math.max(cam, Math.max(cam + (0.12f + maxRow * 0.004f) * dt, pr - 4f));
        for (int i = Math.max(0, (int) cam - 1); i < Math.min(rows.size(), (int) cam + 20); i++) {
            Row r = rows.get(i);
            for (float[] c : r.cars) {
                c[0] += r.speed * dt;
                if (c[0] > W + 40) c[0] -= W + 90; if (c[0] < -50) c[0] += W + 90;
                if (i == pr && overlap(X0 + pc * CELL + 1, 0, CELL - 2, 1, c[0], 0, c[1], 1)) die();
            }
        }
        if (sy(pr) > H - 12) die();
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF7CB342);
        for (int i = Math.max(0, (int) cam - 1); i < Math.min(rows.size(), (int) cam + 20); i++) {
            Row r = rows.get(i); float y = sy(i);
            if (r.type == 0) {
                box(g, 0, y, W, y + CELL, (i & 1) == 0 ? 0xFF8BC34A : 0xFF7CB342);
                for (int c = 0; c < COLS; c++) if (r.tree[c]) { box(g, X0 + c * CELL + 3, y + 6, X0 + c * CELL + 7, y + 10, 0xFF6D4C41); box(g, X0 + c * CELL + 1, y, X0 + c * CELL + 9, y + 7, 0xFF2E7D32); }
            } else {
                box(g, 0, y, W, y + CELL, 0xFF455A64);
                box(g, 0, y + CELL - 1, W, y + CELL, 0xFF78909C);
                for (float[] c : r.cars) { box(g, c[0], y + 2, c[0] + c[1], y + 9, r.speed > 0 ? 0xFFEF5350 : 0xFFFFCA28); box(g, c[0] + (r.speed > 0 ? c[1] - 6 : 2), y + 3, c[0] + (r.speed > 0 ? c[1] - 2 : 6), y + 6, 0xFF90CAF9); }
            }
        }
        float y = sy(pr) - (hop > 0 ? 3 : 0);
        box(g, X0 + pc * CELL + 2, y + 2, X0 + pc * CELL + 8, y + 9, 0xFFFFFFFF);
        box(g, X0 + pc * CELL + 3, y, X0 + pc * CELL + 7, y + 3, 0xFFFFFFFF);
        box(g, X0 + pc * CELL + 6, y + 1, X0 + pc * CELL + 8, y + 2, 0xFFFF9800);
        box(g, X0 + pc * CELL + 3, y + 1, X0 + pc * CELL + 4, y + 2, 0xFFE53935);
        hud(g, "Hops " + score, "Best " + best);
        if (over) overlay(g, "Splat!", "Hops " + score);
        else if (score == 0) ctr(g, "Arrows/WASD or tap", W / 2, 195, 0xFF33691E, 0.8f);
    }
}

/** Rhythm Platformer: auto-run, jump on the beat. */
final class RhythmApp extends Arcade {
    static final int GY = 170;
    static final float SPEED = 90, BEAT = 0.5f, G = 900, V0 = 450;
    float y, vy, t, nextObs;
    final List<float[]> obs = new ArrayList<>();   // x, w, h, type(0 spike,1 block)
    float lastX;

    RhythmApp() { super("rhythm", "Rhythm Platformer", "RHY", 0xFFD81B60); reset(); }

    @Override void reset() { score = 0; over = false; y = 0; vy = 0; t = 0; obs.clear(); lastX = W + 40; fillObs(); }

    private void fillObs() {
        while (lastX < W + 220) {
            lastX += SPEED * BEAT * (RND.nextInt(3) + 2);       // 2-4 beats apart (always jumpable)
            int kind = RND.nextInt(5);
            if (kind <= 1) obs.add(new float[]{lastX, 10, 10, 0});
            else if (kind == 2) obs.add(new float[]{lastX, 22, 10, 0});
            else if (kind == 3) obs.add(new float[]{lastX, 12, 12, 1});
            else { obs.add(new float[]{lastX, 10, 10, 0}); lastX += 36; obs.add(new float[]{lastX, 10, 10, 0}); }
        }
    }
    private void jump() { if (over) { reset(); return; } if (y <= 0.01f) vy = V0; }
    @Override void tap(float x, float yy) { jump(); }
    @Override boolean key(int k) { if (k == K_SPACE || k == K_UP || k == K_W) { jump(); return true; } return false; }

    @Override void step(float dt) {
        t += dt;
        if (down || held(K_SPACE) || goUp()) { if (y <= 0.01f) vy = V0; }
        vy -= G * dt; y += vy * dt;
        if (y <= 0) { y = 0; vy = 0; }
        for (float[] o : obs) {
            o[0] -= SPEED * dt;
            float h = o[2] - (o[3] == 0 ? 3 : 0), w = o[1] - (o[3] == 0 ? 4 : 0), x = o[0] + (o[3] == 0 ? 2 : 0);
            if (overlap(24, GY - y - 12, 12, 12, x, GY - h, w, h)) die();
        }
        lastX -= SPEED * dt;
        obs.removeIf(o -> o[0] < -30);
        fillObs();
        score = (int) (t / BEAT);
    }

    @Override void draw(GuiGraphicsExtractor g) {
        float phase = (t % BEAT) / BEAT;
        int pulse = (int) (40 * (1 - phase));
        box(g, 0, 0, W, H, 0xFF000000 | ((24 + pulse) << 16) | (10 << 8) | (50 + pulse));
        box(g, 0, GY, W, H, 0xFF1A1A2E); box(g, 0, GY, W, GY + 2, 0xFFFF4081);
        for (float x = -((t * SPEED) % 45); x < W; x += 45) box(g, x, GY + 6, x + 2, GY + 10, 0xFF7C4DFF);
        float bs = 1 + (1 - phase) * 0.0f;
        box(g, 24, GY - y - 12, 36, GY - y, 0xFFFFEB3B);
        box(g, 26, GY - y - 9, 28, GY - y - 7, 0xFF000000); box(g, 32, GY - y - 9, 34, GY - y - 7, 0xFF000000);
        for (float[] o : obs) {
            if (o[3] == 1) { box(g, o[0], GY - o[2], o[0] + o[1], GY, 0xFF00E5FF); box(g, o[0] + 2, GY - o[2] + 2, o[0] + o[1] - 2, GY - 2, 0xFF006064); }
            else {
                int n = (int) (o[1] / 10);
                for (int s = 0; s < n; s++) {
                    float x = o[0] + s * 10;
                    box(g, x, GY - 3, x + 10, GY, 0xFFFF1744); box(g, x + 2, GY - 6, x + 8, GY - 3, 0xFFFF1744); box(g, x + 4, GY - 10, x + 6, GY - 6, 0xFFFF1744);
                }
            }
        }
        hud(g, "Beats " + score, "Best " + best);
        if (score < 3) ctr(g, "Tap / Space to jump", W / 2, 60, GRAY, 0.9f);
        if (over) overlay(g, "Missed the beat!", "Beats " + score);
    }
}

/** Mini Golf: drag back from the ball and let go. */
final class GolfApp extends Game {
    static final int X0 = 4, Y0 = 24, X1 = 114, Y1 = 198;
    // per hole: ball x,y, hole x,y, par, then walls (x,y,w,h)...
    static final float[][] HOLES = {
        {59, 180, 59, 40, 2},
        {30, 180, 88, 40, 3, 36, 100, 74, 6},
        {59, 185, 59, 35, 3, 4, 140, 70, 6, 44, 90, 70, 6},
        {20, 185, 95, 40, 3, 59, 60, 6, 100, 20, 60, 25, 6},
        {95, 185, 25, 40, 4, 4, 150, 60, 6, 54, 110, 60, 6, 4, 70, 60, 6}};
    float bx, by, vx, vy;
    int hole, strokes, total;
    boolean aiming, sunk, finished;
    float ax, ay, sunkT;

    GolfApp() { super("golf", "Mini Golf", "GLF", 0xFF388E3C); reset(); }

    @Override void reset() { hole = 0; total = 0; finished = false; load(); }
    private void load() { float[] h = HOLES[hole]; bx = h[0]; by = h[1]; vx = 0; vy = 0; strokes = 0; sunk = false; aiming = false; }
    private boolean moving() { return Math.abs(vx) + Math.abs(vy) > 0.1f; }

    @Override void press(float x, float y) {
        if (finished) { reset(); return; }
        if (sunk) { total += strokes; if (hole + 1 >= HOLES.length) finished = true; else { hole++; load(); } return; }
        if (!moving() && Math.hypot(x - bx, y - by) < 16) { aiming = true; ax = x; ay = y; }
    }
    @Override void release(float x, float y) {
        if (!aiming) return;
        aiming = false;
        float dx = bx - x, dy = by - y, d = (float) Math.hypot(dx, dy);
        if (d < 3) return;
        float sp = Math.min(d * 3.4f, 230);
        vx = dx / d * sp; vy = dy / d * sp; strokes++;
    }

    @Override void update(float dt) {
        if (finished || sunk) return;
        dt = Math.min(dt, 0.03f);
        float[] h = HOLES[hole];
        if (moving()) {
            float sp = (float) Math.hypot(vx, vy), ns = Math.max(0, sp - 65 * dt);
            vx *= ns / sp; vy *= ns / sp;
            if (ns < 6) { vx = 0; vy = 0; }
            bx += vx * dt; by += vy * dt;
            if (bx < X0 + 3) { bx = X0 + 3; vx = Math.abs(vx); } if (bx > X1 - 3) { bx = X1 - 3; vx = -Math.abs(vx); }
            if (by < Y0 + 3) { by = Y0 + 3; vy = Math.abs(vy); } if (by > Y1 - 3) { by = Y1 - 3; vy = -Math.abs(vy); }
            for (int i = 5; i + 3 < h.length; i += 4) {
                float rx = h[i], ry = h[i + 1], rw = h[i + 2], rh = h[i + 3];
                float cx = clamp(bx, rx, rx + rw), cy = clamp(by, ry, ry + rh), dx = bx - cx, dy = by - cy, d = (float) Math.hypot(dx, dy);
                if (d < 3) {
                    if (d < 0.001f) { dx = 0; dy = -1; d = 1; }
                    float nx = dx / d, ny = dy / d;
                    bx = cx + nx * 3.1f; by = cy + ny * 3.1f;
                    float dot = vx * nx + vy * ny;
                    if (dot < 0) { vx -= 2 * dot * nx; vy -= 2 * dot * ny; }
                }
            }
            if (Math.hypot(bx - h[2], by - h[3]) < 4 && Math.hypot(vx, vy) < 150) { sunk = true; bx = h[2]; by = h[3]; vx = 0; vy = 0; }
        }
        if (strokes >= 12 && !moving() && !sunk) sunk = true;
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF1B5E20);
        box(g, X0, Y0, X1, Y1, 0xFF43A047);
        box(g, X0 - 2, Y0 - 2, X1 + 2, Y0, 0xFF6D4C41); box(g, X0 - 2, Y1, X1 + 2, Y1 + 2, 0xFF6D4C41);
        box(g, X0 - 2, Y0, X0, Y1, 0xFF6D4C41); box(g, X1, Y0, X1 + 2, Y1, 0xFF6D4C41);
        float[] h = HOLES[hole];
        for (int i = 5; i + 3 < h.length; i += 4) box(g, h[i], h[i + 1], h[i] + h[i + 2], h[i + 1] + h[i + 3], 0xFF8D6E63);
        box(g, h[2] - 4, h[3] - 4, h[2] + 4, h[3] + 4, 0xFF000000);
        box(g, h[2] - 1, h[3] - 14, h[2], h[3], 0xFFEEEEEE); box(g, h[2], h[3] - 14, h[2] + 6, h[3] - 10, 0xFFE53935);
        if (aiming) {
            float dx = bx - mx, dy = by - my, d = (float) Math.hypot(dx, dy);
            if (d > 2) for (int k = 1; k <= 6; k++) { float s = Math.min(d, 70) / 6 * k; box(g, bx + dx / d * s - 1, by + dy / d * s - 1, bx + dx / d * s + 1, by + dy / d * s + 1, 0xFFFFFFFF); }
        }
        if (!sunk) box(g, bx - 3, by - 3, bx + 3, by + 3, 0xFFFFFFFF);
        txt(g, "Hole " + (hole + 1) + "/" + HOLES.length + "  Par " + (int) h[4], 5, 5, WHITE);
        txt(g, "Strokes " + strokes + "  Total " + total, 5, 14, GRAY);
        if (sunk) { box(g, 10, 90, W - 10, 130, 0xDD000000); ctr(g, strokes >= 12 ? "Too many!" : "In " + strokes + "!", W / 2, 98, 0xFF8FD6DD, 1.4f); ctr(g, "Tap to continue", W / 2, 116, GRAY, 0.9f); }
        if (finished) { box(g, 10, 80, W - 10, 140, 0xDD000000); ctr(g, "Course done!", W / 2, 90, 0xFF8FD6DD, 1.3f); ctr(g, "Total " + total + " strokes", W / 2, 108, WHITE, 1f); ctr(g, "Tap to restart", W / 2, 122, GRAY, 0.9f); }
        else if (!moving() && strokes == 0 && !sunk) ctr(g, "Drag back from the ball, release", W / 2, 205, GRAY, 0.7f);
    }
}
