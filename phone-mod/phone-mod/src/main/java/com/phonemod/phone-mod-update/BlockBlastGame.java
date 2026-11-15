package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Arrays;
import java.util.Random;

/** 8x8 board, drag the three pieces on, full rows/columns clear. */
final class BlockBlastGame {
    static final int N = 8, CELL = 13, GX = 7, GY = 28, TRAY_Y = 172, TCELL = 7, FINGER = 22;
    static final int[] COLORS = {0xFFE74C3C, 0xFFF39C12, 0xFFF1C40F, 0xFF2ECC71, 0xFF3498DB, 0xFF9B59B6, 0xFFE91E8C};
    static final String[][] SHAPES = {
        {"#"}, {"##"}, {"#", "#"}, {"###"}, {"#", "#", "#"}, {"####"}, {"#", "#", "#", "#"},
        {"#####"}, {"#", "#", "#", "#", "#"}, {"##", "##"}, {"###", "###", "###"},
        {"###", "###"}, {"##", "##", "##"},
        {"#.", "##"}, {".#", "##"}, {"##", "#."}, {"##", ".#"},
        {"#..", "#..", "###"}, {"..#", "..#", "###"}, {"###", "#..", "#.."}, {"###", "..#", "..#"},
        {"###", ".#."}, {".#.", "###"}, {"#.", "##", "#."}, {".#", "##", ".#"},
        {"##.", ".##"}, {".##", "##."}
    };

    record Piece(boolean[][] cells, int color) {
        int w() { return cells[0].length; }
        int h() { return cells.length; }
    }

    private final int[][] grid = new int[N][N];     // 0 = empty, else colour index + 1
    private final Piece[] tray = new Piece[3];
    private final Random rnd = new Random();
    private int score, best, drag = -1;
    private float cx, cy;
    private boolean over;

    BlockBlastGame() { reset(); }

    int getBest() { return best; }
    void setBest(int b) { best = b; }
    int getScore() { return score; }
    boolean isOver() { return over; }

    void reset() {
        for (int[] row : grid) Arrays.fill(row, 0);
        score = 0; over = false; drag = -1;
        refill();
    }

    private void refill() {
        for (int i = 0; i < 3; i++) {
            String[] s = SHAPES[rnd.nextInt(SHAPES.length)];
            boolean[][] c = new boolean[s.length][s[0].length()];
            for (int y = 0; y < s.length; y++)
                for (int x = 0; x < s[y].length(); x++) c[y][x] = s[y].charAt(x) == '#';
            tray[i] = new Piece(c, rnd.nextInt(COLORS.length));
        }
        checkOver();
    }

    // ---------- input ----------
    void press(float x, float y) {
        if (over) { reset(); return; }
        for (int i = 0; i < 3; i++) {
            if (tray[i] != null && Math.abs(x - (20 + 39 * i)) <= 19 && Math.abs(y - TRAY_Y) <= 30) {
                drag = i; cx = x; cy = y; return;
            }
        }
    }

    void release(float x, float y) {
        if (drag < 0) return;
        Piece p = tray[drag];
        int gx = targetX(p, x), gy = targetY(p, y);
        if (canPlace(p, gx, gy)) {
            int count = 0;
            for (int r = 0; r < p.h(); r++)
                for (int c = 0; c < p.w(); c++)
                    if (p.cells()[r][c]) { grid[gy + r][gx + c] = p.color() + 1; count++; }
            score += count + clearLines();
            best = Math.max(best, score);
            tray[drag] = null;
            if (tray[0] == null && tray[1] == null && tray[2] == null) refill(); else checkOver();
        }
        drag = -1;
    }

    private int targetX(Piece p, float x) { return Math.round((x - p.w() * CELL / 2f - GX) / CELL); }
    private int targetY(Piece p, float y) { return Math.round((y - FINGER - p.h() * CELL / 2f - GY) / CELL); }

    // ---------- rules ----------
    private boolean canPlace(Piece p, int gx, int gy) {
        for (int r = 0; r < p.h(); r++)
            for (int c = 0; c < p.w(); c++) {
                if (!p.cells()[r][c]) continue;
                int x = gx + c, y = gy + r;
                if (x < 0 || y < 0 || x >= N || y >= N || grid[y][x] != 0) return false;
            }
        return true;
    }

    private int clearLines() {
        boolean[] rows = new boolean[N], cols = new boolean[N];
        int lines = 0;
        for (int i = 0; i < N; i++) {
            rows[i] = true; cols[i] = true;
            for (int j = 0; j < N; j++) { if (grid[i][j] == 0) rows[i] = false; if (grid[j][i] == 0) cols[i] = false; }
            if (rows[i]) lines++;
            if (cols[i]) lines++;
        }
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++) if (rows[i] || cols[j]) grid[i][j] = 0;
        return lines * lines * 10;
    }

    private void checkOver() {
        over = true;
        for (Piece p : tray) {
            if (p == null) continue;
            for (int y = 0; y < N && over; y++)
                for (int x = 0; x < N && over; x++) if (canPlace(p, x, y)) over = false;
        }
    }

    // ---------- drawing ----------
    private static void cell(GuiGraphicsExtractor g, float x, float y, float s, int color) {
        PhoneScreen.box(g, x, y, x + s, y + s, 0xFF000000 | ((color & 0xFEFEFE) >> 1));
        PhoneScreen.box(g, x + 1, y + 1, x + s - 1, y + s - 1, color);
    }

    void draw(GuiGraphicsExtractor g, float mx, float my) {
        if (drag >= 0) { cx = mx; cy = my; }
        g.fillGradient(PhoneScreen.SX, PhoneScreen.SY, PhoneScreen.SX + PhoneScreen.SW,
                PhoneScreen.SY + PhoneScreen.SH, 0xFF2B3563, 0xFF141830);

        PhoneScreen.txt(g, "Score " + score, 8, 6, 0xFFFFFFFF);
        PhoneScreen.txt(g, "Best " + best, 8, 16, 0xFFAAAAAA);

        PhoneScreen.box(g, GX - 2, GY - 2, GX + N * CELL + 2, GY + N * CELL + 2, 0xFF11142B);
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) {
                int v = grid[r][c];
                if (v == 0) PhoneScreen.box(g, GX + c * CELL + 1, GY + r * CELL + 1, GX + (c + 1) * CELL - 1, GY + (r + 1) * CELL - 1, 0xFF30385F);
                else cell(g, GX + c * CELL, GY + r * CELL, CELL, COLORS[v - 1]);
            }

        // ghost preview
        if (drag >= 0) {
            Piece p = tray[drag];
            int gx = targetX(p, cx), gy = targetY(p, cy);
            if (canPlace(p, gx, gy))
                for (int r = 0; r < p.h(); r++)
                    for (int c = 0; c < p.w(); c++)
                        if (p.cells()[r][c])
                            PhoneScreen.box(g, GX + (gx + c) * CELL + 1, GY + (gy + r) * CELL + 1,
                                    GX + (gx + c + 1) * CELL - 1, GY + (gy + r + 1) * CELL - 1, 0x88FFFFFF);
        }

        // tray
        for (int i = 0; i < 3; i++) {
            Piece p = tray[i];
            if (p == null || i == drag) continue;
            float x0 = 20 + 39 * i - p.w() * TCELL / 2f, y0 = TRAY_Y - p.h() * TCELL / 2f;
            for (int r = 0; r < p.h(); r++)
                for (int c = 0; c < p.w(); c++)
                    if (p.cells()[r][c]) cell(g, x0 + c * TCELL, y0 + r * TCELL, TCELL, COLORS[p.color()]);
        }

        // dragged piece
        if (drag >= 0) {
            Piece p = tray[drag];
            float x0 = cx - p.w() * CELL / 2f, y0 = cy - FINGER - p.h() * CELL / 2f;
            for (int r = 0; r < p.h(); r++)
                for (int c = 0; c < p.w(); c++)
                    if (p.cells()[r][c]) cell(g, x0 + c * CELL, y0 + r * CELL, CELL, COLORS[p.color()]);
        }

        if (over) {
            PhoneScreen.box(g, 10, 70, PhoneScreen.SW - 10, 130, 0xDD000000);
            PhoneScreen.ctr(g, "Game Over", PhoneScreen.SW / 2, 80, 0xFFFF5555, 1.4f);
            PhoneScreen.ctr(g, "Score " + score, PhoneScreen.SW / 2, 98, 0xFFFFFFFF, 1f);
            PhoneScreen.ctr(g, "Tap to retry", PhoneScreen.SW / 2, 112, 0xFFAAAAAA, 0.9f);
        }
    }
}
