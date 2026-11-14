package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Tetris: arrows/WASD + space, or the buttons on the right. */
final class TetrisApp extends Arcade {
    static final int CW = 10, CH = 20, CS = 9, BX = 4, BY = 22;
    static final int[][][] BASE = {
        {{-1, 0}, {0, 0}, {1, 0}, {2, 0}}, {{0, 0}, {1, 0}, {0, 1}, {1, 1}}, {{-1, 0}, {0, 0}, {1, 0}, {0, 1}},
        {{0, 0}, {1, 0}, {-1, 1}, {0, 1}}, {{-1, 0}, {0, 0}, {0, 1}, {1, 1}}, {{-1, 0}, {0, 0}, {1, 0}, {-1, 1}}, {{-1, 0}, {0, 0}, {1, 0}, {1, 1}}};
    static final int[] COL = {0xFF00E5FF, 0xFFFFEB3B, 0xFFAB47BC, 0xFF66BB6A, 0xFFEF5350, 0xFF42A5F5, 0xFFFFA726};
    final int[][] board = new int[CH][CW];
    int[][] cur = new int[4][2];
    int type, nextType, cx, cy, lines, level;
    float fall;

    TetrisApp() { super("tetris", "Tetris", "TET", 0xFF6A1B9A); reset(); }

    @Override void reset() {
        for (int[] r : board) java.util.Arrays.fill(r, 0);
        score = 0; lines = 0; level = 1; over = false; fall = 0; nextType = RND.nextInt(7); spawn();
    }
    private void spawn() {
        type = nextType; nextType = RND.nextInt(7);
        for (int i = 0; i < 4; i++) cur[i] = BASE[type][i].clone();
        cx = 4; cy = 0;
        if (collides(cur, cx, cy)) die();
    }
    private boolean collides(int[][] cells, int x, int y) {
        for (int[] c : cells) {
            int gx = x + c[0], gy = y + c[1];
            if (gx < 0 || gx >= CW || gy >= CH) return true;
            if (gy >= 0 && board[gy][gx] != 0) return true;
        }
        return false;
    }
    private void move(int dx) { if (!collides(cur, cx + dx, cy)) cx += dx; }
    private void rotate() {
        if (type == 1) return;
        int[][] n = new int[4][2];
        for (int i = 0; i < 4; i++) { n[i][0] = -cur[i][1]; n[i][1] = cur[i][0]; }
        for (int k : new int[]{0, 1, -1, 2, -2}) if (!collides(n, cx + k, cy)) { cur = n; cx += k; return; }
    }
    private boolean stepDown() { if (!collides(cur, cx, cy + 1)) { cy++; return true; } lockPiece(); return false; }
    private void drop() { int n = 0; while (!collides(cur, cx, cy + 1)) { cy++; n++; } score += n * 2; lockPiece(); }
    private void lockPiece() {
        for (int[] c : cur) { int gy = cy + c[1]; if (gy < 0) { die(); return; } board[gy][cx + c[0]] = type + 1; }
        int cleared = 0;
        for (int r = CH - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < CW; c++) if (board[r][c] == 0) full = false;
            if (full) { cleared++; for (int rr = r; rr > 0; rr--) board[rr] = board[rr - 1].clone(); board[0] = new int[CW]; r++; }
        }
        if (cleared > 0) { score += new int[]{0, 100, 300, 500, 800}[cleared] * level; lines += cleared; level = lines / 10 + 1; }
        fall = 0;
        spawn();
    }

    @Override boolean key(int k) {
        if (over) return false;
        if (k == K_LEFT || k == K_A) move(-1); else if (k == K_RIGHT || k == K_D) move(1);
        else if (k == K_UP || k == K_W) rotate(); else if (k == K_DOWN || k == K_S) { if (stepDown()) score++; }
        else if (k == K_SPACE) drop(); else return false;
        return true;
    }
    @Override void tap(float x, float y) {
        if (in(x, y, 96, 70, 116, 86)) move(-1); else if (in(x, y, 96, 90, 116, 106)) move(1);
        else if (in(x, y, 96, 110, 116, 126)) rotate(); else if (in(x, y, 96, 130, 116, 146)) drop();
    }
    @Override void step(float dt) {
        fall += dt;
        float iv = Math.max(0.06f, 0.65f - 0.05f * level);
        while (fall >= iv && !over) { fall -= iv; stepDown(); }
    }

    private void cell(GuiGraphicsExtractor g, float x, float y, float s, int c) {
        box(g, x, y, x + s, y + s, 0xFF000000 | ((c & 0xFEFEFE) >> 1));
        box(g, x + 1, y + 1, x + s - 1, y + s - 1, c);
    }
    private void btn(GuiGraphicsExtractor g, String t, int y) { box(g, 96, y, 116, y + 16, 0xFF37474F); ctr(g, t, 106, y + 4, WHITE, 1f); }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF0D0D1A);
        box(g, BX - 1, BY - 1, BX + CW * CS + 1, BY + CH * CS + 1, 0xFF3A3A5A);
        box(g, BX, BY, BX + CW * CS, BY + CH * CS, 0xFF14142A);
        for (int r = 0; r < CH; r++) for (int c = 0; c < CW; c++) if (board[r][c] != 0) cell(g, BX + c * CS, BY + r * CS, CS, COL[board[r][c] - 1]);
        if (!over) {
            int gy = cy; while (!collides(cur, cx, gy + 1)) gy++;
            for (int[] c : cur) { box(g, BX + (cx + c[0]) * CS, BY + (gy + c[1]) * CS, BX + (cx + c[0] + 1) * CS, BY + (gy + c[1] + 1) * CS, 0x33FFFFFF);
                if (cy + c[1] >= 0) cell(g, BX + (cx + c[0]) * CS, BY + (cy + c[1]) * CS, CS, COL[type]); }
        }
        txt(g, "Score", 96, 22, GRAY); txt(g, String.valueOf(score), 96, 31, WHITE);
        txt(g, "Lvl " + level, 96, 44, GRAY);
        for (int[] c : BASE[nextType]) cell(g, 98 + (c[0] + 1) * 5, 56 + c[1] * 5, 5, COL[nextType]);
        btn(g, "<", 70); btn(g, ">", 90); btn(g, "R", 110); btn(g, "v", 130);
        txt(g, "Best", 96, 156, GRAY); txt(g, String.valueOf(best), 96, 165, WHITE);
        if (over) overlay(g, "Game Over", "Score " + score);
    }
}

/** 2048: swipe (drag) or arrow keys. */
final class Game2048App extends Game {
    final int[][] t = new int[4][4];
    int score, best;
    boolean over;
    float sx, sy;
    static final int[] COL = {0xFFCDC1B4, 0xFFEEE4DA, 0xFFEDE0C8, 0xFFF2B179, 0xFFF59563, 0xFFF67C5F, 0xFFF65E3B, 0xFFEDCF72, 0xFFEDCC61, 0xFFEDC850, 0xFFEDC53F, 0xFFEDC22E, 0xFF3C3A32};

    Game2048App() { super("2048", "2048", "2048", 0xFFEDC22E); reset(); }

    @Override void reset() { for (int[] r : t) java.util.Arrays.fill(r, 0); score = 0; over = false; add(); add(); }
    private void add() {
        List<int[]> e = new ArrayList<>();
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) if (t[r][c] == 0) e.add(new int[]{r, c});
        if (e.isEmpty()) return;
        int[] p = e.get(RND.nextInt(e.size()));
        t[p[0]][p[1]] = RND.nextInt(10) == 0 ? 4 : 2;
    }
    /** dir: 0 up 1 right 2 down 3 left */
    private boolean slide(int dir) {
        boolean moved = false;
        for (int i = 0; i < 4; i++) {
            int[] line = new int[4];
            for (int k = 0; k < 4; k++) { int r = dir == 0 ? k : dir == 2 ? 3 - k : i, c = dir == 3 ? k : dir == 1 ? 3 - k : i; line[k] = t[r][c]; }
            int[] out = new int[4]; int n = 0;
            for (int k = 0; k < 4; k++) if (line[k] != 0) {
                if (n > 0 && out[n - 1] == line[k] && out[n - 1] > 0) { out[n - 1] *= -2; score += -out[n - 1]; } else out[n++] = line[k];
            }
            for (int k = 0; k < 4; k++) {
                out[k] = Math.abs(out[k]);
                int r = dir == 0 ? k : dir == 2 ? 3 - k : i, c = dir == 3 ? k : dir == 1 ? 3 - k : i;
                if (t[r][c] != out[k]) moved = true;
                t[r][c] = out[k];
            }
        }
        return moved;
    }
    private void play(int dir) {
        if (over) return;
        if (slide(dir)) add();
        best = Math.max(best, score);
        boolean can = false;
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) if (t[r][c] == 0 || (c < 3 && t[r][c] == t[r][c + 1]) || (r < 3 && t[r][c] == t[r + 1][c])) can = true;
        over = !can;
    }
    @Override boolean key(int k) {
        if (k == K_UP || k == K_W) play(0); else if (k == K_RIGHT || k == K_D) play(1);
        else if (k == K_DOWN || k == K_S) play(2); else if (k == K_LEFT || k == K_A) play(3); else return false;
        return true;
    }
    @Override void press(float x, float y) {
        if (over) { reset(); return; }
        if (in(x, y, 4, 166, 114, 184)) { reset(); return; }
        sx = x; sy = y;
    }
    @Override void release(float x, float y) {
        float dx = x - sx, dy = y - sy;
        if (over || Math.max(Math.abs(dx), Math.abs(dy)) < 10) return;
        if (Math.abs(dx) > Math.abs(dy)) play(dx > 0 ? 1 : 3); else play(dy > 0 ? 2 : 0);
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFFFAF8EF);
        txt(g, "2048", 5, 6, 0xFF776E65); txt(g, "Score " + score, 40, 6, 0xFF776E65); txt(g, "Best " + best, 40, 16, 0xFF9E948A);
        box(g, 1, 38, 117, 154, 0xFFBBADA0);
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) {
            int v = t[r][c], idx = 0; while ((1 << idx) < v && v > 0) idx++;
            int col = v == 0 ? COL[0] : COL[Math.min(idx, 12)];
            float x = 4 + c * 28, y = 41 + r * 28;
            box(g, x, y, x + 25, y + 25, col);
            if (v != 0) ctr(g, String.valueOf(v), (int) x + 12, (int) y + (v < 100 ? 8 : 9), v <= 4 ? 0xFF776E65 : WHITE, v < 100 ? 1.3f : v < 1000 ? 1f : 0.8f);
        }
        box(g, 4, 166, 114, 184, 0xFF8F7A66); ctr(g, "New game", W / 2, 171, WHITE, 1f);
        ctr(g, "Swipe or arrow keys", W / 2, 195, 0xFF9E948A, 0.8f);
        if (over) { box(g, 1, 38, 117, 154, 0xAAEEE4DA); ctr(g, "Game over", W / 2, 88, 0xFF776E65, 1.4f); ctr(g, "Tap to retry", W / 2, 106, 0xFF776E65, 0.9f); }
    }
}

/** Minesweeper: Dig / Flag buttons. */
final class MinesApp extends Game {
    static final int N = 9, CS = 12, GX = 5, GY = 24, MINES = 10;
    static final int[] NC = {0, 0xFF1976D2, 0xFF388E3C, 0xFFD32F2F, 0xFF512DA8, 0xFF8D6E63, 0xFF00838F, 0xFF212121, 0xFF757575};
    final boolean[][] mine = new boolean[N][N], open = new boolean[N][N], flag = new boolean[N][N];
    final int[][] near = new int[N][N];
    boolean started, lost, won, flagMode;
    float time;

    MinesApp() { super("mines", "Minesweeper", "MINE", 0xFF607D8B); reset(); }

    @Override void reset() {
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) { mine[r][c] = false; open[r][c] = false; flag[r][c] = false; near[r][c] = 0; }
        started = false; lost = false; won = false; time = 0;
    }
    private void plant(int sr, int sc) {
        int n = 0;
        while (n < MINES) {
            int r = RND.nextInt(N), c = RND.nextInt(N);
            if (mine[r][c] || (Math.abs(r - sr) <= 1 && Math.abs(c - sc) <= 1)) continue;
            mine[r][c] = true; n++;
        }
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            int k = 0;
            for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) { int rr = r + dr, cc = c + dc; if (rr >= 0 && cc >= 0 && rr < N && cc < N && mine[rr][cc]) k++; }
            near[r][c] = k;
        }
    }
    private void reveal(int r0, int c0) {
        List<int[]> st = new ArrayList<>(); st.add(new int[]{r0, c0});
        while (!st.isEmpty()) {
            int[] p = st.remove(st.size() - 1);
            int r = p[0], c = p[1];
            if (r < 0 || c < 0 || r >= N || c >= N || open[r][c] || flag[r][c]) continue;
            open[r][c] = true;
            if (mine[r][c]) { lost = true; continue; }
            if (near[r][c] == 0) for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) st.add(new int[]{r + dr, c + dc});
        }
        if (!lost) { int o = 0; for (boolean[] row : open) for (boolean b : row) if (b) o++; if (o == N * N - MINES) won = true; }
    }
    @Override void update(float dt) { if (started && !lost && !won) time += dt; }
    @Override void press(float x, float y) {
        if (in(x, y, 4, 142, 58, 158)) { flagMode = false; return; }
        if (in(x, y, 62, 142, 114, 158)) { flagMode = true; return; }
        if (in(x, y, 4, 164, 114, 180)) { reset(); return; }
        if (lost || won || !in(x, y, GX, GY, GX + N * CS, GY + N * CS)) return;
        int c = (int) ((x - GX) / CS), r = (int) ((y - GY) / CS);
        if (flagMode) { if (!open[r][c]) flag[r][c] = !flag[r][c]; return; }
        if (flag[r][c] || open[r][c]) return;
        if (!started) { started = true; plant(r, c); }
        reveal(r, c);
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF1E2228);
        int fl = 0; for (boolean[] row : flag) for (boolean b : row) if (b) fl++;
        txt(g, "Mines " + (MINES - fl), 5, 6, WHITE); txt(g, (int) time + "s", 90, 6, GRAY);
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            float x = GX + c * CS, y = GY + r * CS;
            boolean show = open[r][c] || (lost && mine[r][c]);
            box(g, x, y, x + CS - 1, y + CS - 1, show ? (mine[r][c] ? 0xFFEF5350 : 0xFFCFD8DC) : 0xFF78909C);
            if (show) {
                if (mine[r][c]) box(g, x + 3, y + 3, x + 8, y + 8, 0xFF000000);
                else if (near[r][c] > 0) ctr(g, String.valueOf(near[r][c]), (int) x + 5, (int) y + 2, NC[near[r][c]], 1f);
            } else if (flag[r][c]) { box(g, x + 5, y + 2, x + 6, y + 9, 0xFFEEEEEE); box(g, x + 3, y + 2, x + 6, y + 5, 0xFFE53935); }
        }
        box(g, 4, 142, 58, 158, flagMode ? 0xFF37474F : 0xFF2E86DE); ctr(g, "Dig", 31, 146, WHITE, 1f);
        box(g, 62, 142, 114, 158, flagMode ? 0xFFE67E22 : 0xFF37474F); ctr(g, "Flag", 88, 146, WHITE, 1f);
        box(g, 4, 164, 114, 180, 0xFF455A64); ctr(g, lost ? "Boom! New game" : won ? "You win! New game" : "New game", W / 2, 168, WHITE, 1f);
    }
}

/** Sudoku (puzzles may rarely have more than one solution). */
final class SudokuApp extends Game {
    static final int CS = 12, GX = 5, GY = 22;
    final int[][] sol = new int[9][9], grid = new int[9][9];
    final boolean[][] given = new boolean[9][9];
    int sr = -1, sc = -1;
    boolean won;

    SudokuApp() { super("sudoku", "Sudoku", "SDK", 0xFF3949AB); reset(); }

    @Override void reset() {
        for (int[] r : sol) java.util.Arrays.fill(r, 0);
        fill(0);
        int removed = 0;
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) { grid[r][c] = sol[r][c]; given[r][c] = true; }
        while (removed < 42) { int r = RND.nextInt(9), c = RND.nextInt(9); if (given[r][c]) { given[r][c] = false; grid[r][c] = 0; removed++; } }
        sr = -1; sc = -1; won = false;
    }
    private boolean ok(int[][] g, int r, int c, int d) {
        for (int i = 0; i < 9; i++) if (g[r][i] == d || g[i][c] == d) return false;
        int br = r / 3 * 3, bc = c / 3 * 3;
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) if (g[br + i][bc + j] == d) return false;
        return true;
    }
    private boolean fill(int pos) {
        if (pos == 81) return true;
        int r = pos / 9, c = pos % 9;
        int[] ds = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int i = 8; i > 0; i--) { int j = RND.nextInt(i + 1); int t = ds[i]; ds[i] = ds[j]; ds[j] = t; }
        for (int d : ds) if (ok(sol, r, c, d)) { sol[r][c] = d; if (fill(pos + 1)) return true; sol[r][c] = 0; }
        return false;
    }
    private boolean conflict(int r, int c) {
        int d = grid[r][c];
        if (d == 0) return false;
        for (int i = 0; i < 9; i++) { if (i != c && grid[r][i] == d) return true; if (i != r && grid[i][c] == d) return true; }
        int br = r / 3 * 3, bc = c / 3 * 3;
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) if ((br + i != r || bc + j != c) && grid[br + i][bc + j] == d) return true;
        return false;
    }
    private void put(int d) {
        if (sr < 0 || given[sr][sc] || won) return;
        grid[sr][sc] = d;
        boolean all = true;
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) if (grid[r][c] == 0 || conflict(r, c)) all = false;
        won = all;
    }
    @Override boolean key(int k) {
        if (k >= 49 && k <= 57) { put(k - 48); return true; }
        if (k == K_BACKSPACE || k == 48 || k == 261) { put(0); return true; }
        if (sr >= 0) {
            if (k == K_UP) sr = Math.max(0, sr - 1); else if (k == K_DOWN) sr = Math.min(8, sr + 1);
            else if (k == K_LEFT) sc = Math.max(0, sc - 1); else if (k == K_RIGHT) sc = Math.min(8, sc + 1); else return false;
            return true;
        }
        return false;
    }
    @Override void press(float x, float y) {
        if (in(x, y, GX, GY, GX + 9 * CS, GY + 9 * CS)) { sc = (int) ((x - GX) / CS); sr = (int) ((y - GY) / CS); return; }
        for (int i = 0; i < 10; i++) {
            int bx = 4 + (i % 5) * 22, by = 138 + (i / 5) * 18;
            if (in(x, y, bx, by, bx + 20, by + 16)) { put(i < 9 ? i + 1 : 0); return; }
        }
        if (in(x, y, 4, 178, 114, 194)) reset();
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF14161F);
        txt(g, won ? "Solved!" : "Sudoku", 5, 6, won ? 0xFF8FD6DD : WHITE);
        box(g, GX - 1, GY - 1, GX + 9 * CS + 1, GY + 9 * CS + 1, 0xFF90A4AE);
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) {
            float x = GX + c * CS, y = GY + r * CS;
            boolean sameBox = sr >= 0 && (r / 3 == sr / 3 && c / 3 == sc / 3);
            int bg = (r == sr && c == sc) ? 0xFF4FC3F7 : (sr >= 0 && (r == sr || c == sc || sameBox)) ? 0xFFCFD8DC : 0xFFECEFF1;
            box(g, x, y, x + CS - (c % 3 == 2 ? 0 : 1), y + CS - (r % 3 == 2 ? 0 : 1), bg);
            if (grid[r][c] != 0) ctr(g, String.valueOf(grid[r][c]), (int) x + 6, (int) y + 2, conflict(r, c) ? 0xFFD32F2F : given[r][c] ? 0xFF111111 : 0xFF1565C0, 1f);
        }
        for (int i = 0; i < 10; i++) {
            int bx = 4 + (i % 5) * 22, by = 138 + (i / 5) * 18;
            box(g, bx, by, bx + 20, by + 16, 0xFF37474F); ctr(g, i < 9 ? String.valueOf(i + 1) : "X", bx + 10, by + 4, WHITE, 1f);
        }
        box(g, 4, 178, 114, 194, 0xFF455A64); ctr(g, "New puzzle", W / 2, 182, WHITE, 1f);
    }
}
