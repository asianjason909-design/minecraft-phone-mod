package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Full-rules chess (castling, en passant, auto-queen promotion). Opponent = a friend, or the computer when null. */
final class ChessGame extends Game implements PhoneNet.NetPlay {
    static final int CELL = 14, BX = 3, BY = 34;
    static final int[] VAL = {0, 100, 320, 330, 500, 900, 0};
    static final String LETTERS = " PNBRQK";
    static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    static final int[][] KN = {{2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};

    final String opponent;   // null = computer
    boolean meWhite;         // side the human plays
    private final int[] b;   // index = rank*8+file, rank 0 = white's first rank; +white, -black; 1P 2N 3B 4R 5Q 6K
    private boolean whiteTurn = true, wK = true, wQ = true, bK = true, bQ = true;
    private int ep = -1, sel = -1, lastFrom = -1, lastTo = -1, half;
    private List<Integer> selMoves = new ArrayList<>();
    private boolean over;
    private String status = "";
    private float aiTimer;
    private final Random rnd = new Random();

    private boolean rematchSent, rematchIn;

    ChessGame(String opponent, boolean meWhite) {
        super("chess", "Chess", "CH", 0xFF769656);
        resetOnOpen = false;
        this.opponent = opponent;
        this.b = new int[64];
        reset(meWhite);
    }

    /** Fresh board in place (so screens holding this game keep working). */
    void reset(boolean newMeWhite) {
        meWhite = newMeWhite;
        java.util.Arrays.fill(b, 0);
        int[] back = {4, 2, 3, 5, 6, 3, 2, 4};
        for (int f = 0; f < 8; f++) {
            b[f] = back[f]; b[8 + f] = 1; b[48 + f] = -1; b[56 + f] = -back[f];
        }
        whiteTurn = true; wK = true; wQ = true; bK = true; bQ = true;
        ep = -1; sel = -1; lastFrom = -1; lastTo = -1; half = 0;
        selMoves = new ArrayList<>();
        over = false; status = ""; aiTimer = 0;
        rematchSent = false; rematchIn = false;
    }

    private void requestRematch() {
        if (rematchSent) return;
        rematchSent = true;
        PhoneNet.sendGame(opponent, 'C', "X");
        if (rematchIn) reset(!meWhite);      // both want it: swap colours and go
    }

    @Override public void remoteRematch() {
        rematchIn = true;
        if (rematchSent) reset(!meWhite);
    }

    private ChessGame(ChessGame o) {
        super("chess", "Chess", "CH", 0xFF769656);
        opponent = o.opponent; meWhite = o.meWhite; b = o.b.clone();
        whiteTurn = o.whiteTurn; wK = o.wK; wQ = o.wQ; bK = o.bK; bQ = o.bQ; ep = o.ep; half = o.half;
    }

    // ---------- rules ----------
    private static boolean in(int r, int f) { return r >= 0 && r < 8 && f >= 0 && f < 8; }

    private int kingSq(boolean white) {
        int k = white ? 6 : -6;
        for (int i = 0; i < 64; i++) if (b[i] == k) return i;
        return -1;
    }

    private boolean attacked(int sq, boolean byWhite) {
        if (sq < 0) return false;
        int r = sq / 8, f = sq % 8;
        int pr = byWhite ? r - 1 : r + 1, pawn = byWhite ? 1 : -1;
        if (pr >= 0 && pr < 8) {
            if (f > 0 && b[pr * 8 + f - 1] == pawn) return true;
            if (f < 7 && b[pr * 8 + f + 1] == pawn) return true;
        }
        int kn = byWhite ? 2 : -2;
        for (int[] d : KN) if (in(r + d[0], f + d[1]) && b[(r + d[0]) * 8 + f + d[1]] == kn) return true;
        int kg = byWhite ? 6 : -6;
        for (int[] d : DIRS) if (in(r + d[0], f + d[1]) && b[(r + d[0]) * 8 + f + d[1]] == kg) return true;
        for (int di = 0; di < 8; di++) {
            int rr = r + DIRS[di][0], ff = f + DIRS[di][1];
            while (in(rr, ff)) {
                int p = b[rr * 8 + ff];
                if (p != 0) {
                    if ((p > 0) == byWhite) {
                        int t = Math.abs(p);
                        if (t == 5 || (di < 4 && t == 4) || (di >= 4 && t == 3)) return true;
                    }
                    break;
                }
                rr += DIRS[di][0]; ff += DIRS[di][1];
            }
        }
        return false;
    }

    private static int mv(int from, int to, int flag) { return from | to << 6 | flag << 12; }

    private List<Integer> pseudo(boolean white) {
        List<Integer> out = new ArrayList<>();
        for (int sq = 0; sq < 64; sq++) {
            int p = b[sq];
            if (p == 0 || (p > 0) != white) continue;
            int type = Math.abs(p), r = sq / 8, f = sq % 8;
            if (type == 1) {
                int dir = white ? 1 : -1, startR = white ? 1 : 6, lastR = white ? 7 : 0, nr = r + dir;
                if (nr >= 0 && nr < 8) {
                    int to = nr * 8 + f;
                    if (b[to] == 0) {
                        out.add(mv(sq, to, nr == lastR ? 4 : 0));
                        if (r == startR && b[to + dir * 8] == 0) out.add(mv(sq, to + dir * 8, 5));
                    }
                    for (int df = -1; df <= 1; df += 2) {
                        int nf = f + df;
                        if (nf < 0 || nf > 7) continue;
                        int t = nr * 8 + nf;
                        if (b[t] != 0 && (b[t] > 0) != white) out.add(mv(sq, t, nr == lastR ? 4 : 0));
                        else if (t == ep) out.add(mv(sq, t, 1));
                    }
                }
            } else if (type == 2 || type == 6) {
                int[][] offs = type == 2 ? KN : DIRS;
                for (int[] d : offs) {
                    int rr = r + d[0], ff = f + d[1];
                    if (!in(rr, ff)) continue;
                    int t = rr * 8 + ff;
                    if (b[t] == 0 || (b[t] > 0) != white) out.add(mv(sq, t, 0));
                }
                int base = white ? 0 : 56;
                if (type == 6 && sq == base + 4 && !attacked(sq, !white)) {
                    boolean k = white ? wK : bK, q = white ? wQ : bQ;
                    int rook = white ? 4 : -4;
                    if (k && b[base + 5] == 0 && b[base + 6] == 0 && b[base + 7] == rook
                            && !attacked(base + 5, !white) && !attacked(base + 6, !white)) out.add(mv(sq, base + 6, 2));
                    if (q && b[base + 3] == 0 && b[base + 2] == 0 && b[base + 1] == 0 && b[base] == rook
                            && !attacked(base + 3, !white) && !attacked(base + 2, !white)) out.add(mv(sq, base + 2, 3));
                }
            } else {
                int from = type == 3 ? 4 : 0, to = type == 4 ? 4 : 8;
                for (int di = from; di < to; di++) {
                    int rr = r + DIRS[di][0], ff = f + DIRS[di][1];
                    while (in(rr, ff)) {
                        int t = rr * 8 + ff;
                        if (b[t] == 0) out.add(mv(sq, t, 0));
                        else { if ((b[t] > 0) != white) out.add(mv(sq, t, 0)); break; }
                        rr += DIRS[di][0]; ff += DIRS[di][1];
                    }
                }
            }
        }
        return out;
    }

    private void apply(int m) {
        int from = m & 63, to = (m >> 6) & 63, flag = m >> 12;
        int orig = b[from], p = orig;
        boolean white = p > 0;
        if (flag == 1) b[to + (white ? -8 : 8)] = 0;
        if (flag == 2) { b[to - 1] = b[to + 1]; b[to + 1] = 0; }
        if (flag == 3) { b[to + 1] = b[to - 2]; b[to - 2] = 0; }
        if (flag == 4) p = white ? 5 : -5;
        if (orig == 6) { wK = false; wQ = false; }
        if (orig == -6) { bK = false; bQ = false; }
        if (from == 0 || to == 0) wQ = false;
        if (from == 7 || to == 7) wK = false;
        if (from == 56 || to == 56) bQ = false;
        if (from == 63 || to == 63) bK = false;
        half = (Math.abs(orig) == 1 || b[to] != 0 || flag == 1) ? 0 : half + 1;
        ep = flag == 5 ? (from + to) / 2 : -1;
        b[to] = p; b[from] = 0;
        whiteTurn = !whiteTurn;
    }

    private List<Integer> legal() {
        List<Integer> out = new ArrayList<>();
        boolean white = whiteTurn;
        for (int m : pseudo(white)) {
            ChessGame c = new ChessGame(this);
            c.apply(m);
            if (!c.attacked(c.kingSq(white), !white)) out.add(m);
        }
        return out;
    }

    private boolean inCheck() { return attacked(kingSq(whiteTurn), !whiteTurn); }

    private boolean bareKings() { int n = 0; for (int v : b) if (v != 0) n++; return n == 2; }

    private void updateStatus() {
        boolean chk = inCheck();
        if (legal().isEmpty()) {
            over = true;
            status = chk ? (whiteTurn ? "Black" : "White") + " wins - checkmate" : "Stalemate";
        } else if (half >= 100) { over = true; status = "Draw - 50 move rule"; }
        else if (bareKings()) { over = true; status = "Draw - only kings left"; }
        else status = chk ? "Check!" : "";
    }

    // ---------- playing ----------
    private boolean myTurn() { return !over && whiteTurn == meWhite; }

    private static String name(int sq) { return "" + (char) ('a' + sq % 8) + (char) ('1' + sq / 8); }

    private static int parse(String s, int i) { return (s.charAt(i + 1) - '1') * 8 + (s.charAt(i) - 'a'); }

    private void play(int m, boolean local) {
        apply(m);
        lastFrom = m & 63; lastTo = (m >> 6) & 63;
        sel = -1; selMoves.clear(); aiTimer = 0;
        updateStatus();
        if (over) { boolean won = status.contains(meWhite ? "White wins" : "Black wins"); if (won) Scores.add("chess_wins", 1); Sfx.share(won ? 'w' : 'x'); } else Sfx.play('k');
        if (local && opponent != null) PhoneNet.sendGame(opponent, 'C', "V" + name(lastFrom) + name(lastTo));
    }

    @Override public void remoteMove(String s) {
        if (over || s.length() < 4 || whiteTurn == meWhite) return;
        try {
            int from = parse(s, 0), to = parse(s, 2);
            for (int m : legal()) if ((m & 63) == from && ((m >> 6) & 63) == to) { play(m, false); return; }
        } catch (RuntimeException ignored) { }
    }

    @Override public void remoteResign() { if (!over) { over = true; status = "Opponent resigned - you win!"; Scores.add("chess_wins", 1); Sfx.share('w'); } }

    private void resign() {
        if (over) return;
        over = true; status = "You resigned";
        if (opponent != null) PhoneNet.sendGame(opponent, 'C', "R");
    }

    void update(float dt) {
        if (opponent != null || over || whiteTurn == meWhite) return;
        aiTimer += dt;
        if (aiTimer > 0.5f) aiMove();
    }

    private int eval() {   // white's point of view
        int s = 0;
        for (int sq = 0; sq < 64; sq++) {
            int p = b[sq];
            if (p == 0) continue;
            int t = Math.abs(p), r = sq / 8, f = sq % 8, v = VAL[t];
            if (t == 1) v += 2 * (p > 0 ? r : 7 - r);
            if (t == 2 || t == 3) v += (int) (10 - (Math.abs(f - 3.5) + Math.abs(r - 3.5)) * 3);
            s += p > 0 ? v : -v;
        }
        return s;
    }

    private void aiMove() {
        List<Integer> moves = legal();
        if (moves.isEmpty()) return;
        int sgn = whiteTurn ? 1 : -1, best = Integer.MIN_VALUE, bm = moves.get(0);
        for (int m : moves) {
            ChessGame c = new ChessGame(this);
            c.apply(m);
            List<Integer> replies = c.legal();
            int score;
            if (replies.isEmpty()) score = c.inCheck() ? 100000 : 0;
            else {
                score = Integer.MAX_VALUE;
                for (int r : replies) {
                    ChessGame d = new ChessGame(c);
                    d.apply(r);
                    score = Math.min(score, sgn * d.eval());
                }
            }
            score += rnd.nextInt(8);
            if (score > best) { best = score; bm = m; }
        }
        play(bm, false);
    }

    String dump() {
        StringBuilder sb = new StringBuilder();
        for (int r = 7; r >= 0; r--) { for (int f = 0; f < 8; f++) { int v = b[r * 8 + f]; sb.append(v == 0 ? '.' : (v > 0 ? "PNBRQK" : "pnbrqk").charAt(Math.abs(v) - 1)); } sb.append('\n'); }
        sb.append("whiteTurn=").append(whiteTurn).append(" ep=").append(ep).append(" over=").append(over).append(" half=").append(half).append(" legal=").append(legal().size()).append(" meWhite=").append(meWhite).append(" sel=").append(sel);
        return sb.toString();
    }
    boolean canMove() { return myTurn(); }
    boolean over() { return over; }
    String statusText() { return status; }

    @Override void press(float x, float y) {
        int r = buttons(x, y);
        if (r == 2) reset(true); else if (r == 0) click(x, y);
    }

    @Override void icon(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y) { PhoneScreen.builtinIcon(g, 3, x, y); }

    // ---------- input ----------
    private int squareAt(float x, float y) {
        int col = (int) Math.floor((x - BX) / CELL), row = (int) Math.floor((y - BY) / CELL);
        if (col < 0 || col > 7 || row < 0 || row > 7) return -1;
        int r = meWhite ? 7 - row : row, f = meWhite ? col : 7 - col;
        return r * 8 + f;
    }

    void click(float x, float y) {
        if (over || !myTurn()) return;
        int sq = squareAt(x, y);
        if (sq < 0) return;
        if (sel >= 0) for (int m : selMoves) if (((m >> 6) & 63) == sq) { play(m, true); return; }
        int p = b[sq];
        if (p != 0 && (p > 0) == meWhite) {
            sel = sq;
            selMoves = new ArrayList<>();
            for (int m : legal()) if ((m & 63) == sq) selMoves.add(m);
        } else { sel = -1; selMoves.clear(); }
    }

    /** @return 0 = nothing, 1 = resign pressed, 2 = "new game" pressed (computer games only) */
    int buttons(float x, float y) {
        if (y < 154 || y > 170) return 0;
        if (x >= 4 && x <= 56) { resign(); return 1; }
        if (x >= 62 && x <= 114) {
            if (opponent == null) return 2;
            if (over) { requestRematch(); return 1; }
        }
        return 0;
    }

    // ---------- drawing ----------
    void draw(GuiGraphicsExtractor g) {
        PhoneScreen.box(g, 0, 0, PhoneScreen.SW, PhoneScreen.SH, 0xFF1B1D24);
        PhoneScreen.txt(g, opponent == null ? "Chess vs Computer" : "Chess vs " + opponent, 6, 6, 0xFFFFFFFF);
        String turn = over ? status : (myTurn() ? "Your move" : (opponent == null ? "Thinking..." : "Waiting for " + opponent));
        PhoneScreen.txt(g, turn, 6, 18, over ? 0xFFFFAA00 : 0xFFAAAAAA);
        if (!over && !status.isEmpty()) PhoneScreen.txt(g, status, 84, 18, 0xFFFF6666);

        for (int row = 0; row < 8; row++)
            for (int col = 0; col < 8; col++) {
                int r = meWhite ? 7 - row : row, f = meWhite ? col : 7 - col, sq = r * 8 + f;
                boolean light = ((r + f) & 1) == 1;
                int color = light ? 0xFFEEEED2 : 0xFF769656;
                if (sq == lastFrom || sq == lastTo) color = light ? 0xFFF7F783 : 0xFFBACA2B;
                if (sq == sel) color = 0xFF7FC7FF;
                int x = BX + col * CELL, y = BY + row * CELL;
                PhoneScreen.box(g, x, y, x + CELL, y + CELL, color);
                int p = b[sq];
                if (p != 0) {
                    boolean white = p > 0;
                    int disc = white ? 0xFFF8F8F8 : 0xFF222222;
                    PhoneScreen.box(g, x + 3, y + 1, x + CELL - 3, y + CELL - 1, disc);
                    PhoneScreen.box(g, x + 1, y + 3, x + CELL - 1, y + CELL - 3, disc);
                    PhoneScreen.box(g, x + 2, y + 2, x + CELL - 2, y + CELL - 2, disc);
                    PhoneScreen.ctr(g, String.valueOf(LETTERS.charAt(Math.abs(p))), x + 7, y + 3,
                            white ? 0xFF222222 : 0xFFF8F8F8, 1f);
                }
            }
        for (int m : selMoves) {
            int to = (m >> 6) & 63, r = to / 8, f = to % 8;
            int col = meWhite ? f : 7 - f, row = meWhite ? 7 - r : r;
            int cx = BX + col * CELL + CELL / 2, cy = BY + row * CELL + CELL / 2;
            PhoneScreen.box(g, cx - 2, cy - 2, cx + 2, cy + 2, b[to] != 0 ? 0xCCD32F2F : 0x88000000);
        }

        PhoneScreen.box(g, 4, 154, 56, 170, 0xFF8E2A2A);
        PhoneScreen.ctr(g, "Resign", 30, 158, 0xFFFFFFFF, 1f);
        if (opponent == null) {
            PhoneScreen.box(g, 62, 154, 114, 170, 0xFF2A6E8E);
            PhoneScreen.ctr(g, "New game", 88, 158, 0xFFFFFFFF, 1f);
        } else if (over) {
            String t = rematchSent ? "Waiting..." : (rematchIn ? "Accept" : "Rematch");
            PhoneScreen.box(g, 62, 154, 114, 170, 0xFF2A6E8E);
            PhoneScreen.ctr(g, t, 88, 158, 0xFFFFFFFF, 1f);
            if (rematchIn && !rematchSent)
                PhoneScreen.ctr(g, opponent + " wants a rematch", PhoneScreen.SW / 2, 192, 0xFF8FD6DD, 0.8f);
        }
        PhoneScreen.ctr(g, "Tap a piece, then a square", PhoneScreen.SW / 2, 180, 0xFF6F7585, 0.8f);
    }
}
