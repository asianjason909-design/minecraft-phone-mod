package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Two-player turn game: vs the computer (opponent == null) or a friend over the phone network. */
abstract class NetGame extends Game implements PhoneNet.NetPlay {
    final String opponent;
    final char gid;
    boolean meFirst, firstTurn = true, over, rematchSent, rematchIn;
    String status = "";
    float aiTimer;

    NetGame(String id, String title, String abbr, int color, char gid, String opponent, boolean meFirst) {
        super(id, title, abbr, color);
        this.gid = gid; this.opponent = opponent; this.meFirst = meFirst;
        resetOnOpen = opponent == null;
    }

    abstract void newBoard();
    abstract void boardClick(float x, float y);
    abstract void aiMove();
    abstract void drawBoard(GuiGraphicsExtractor g);

    boolean myTurn() { return !over && firstTurn == meFirst; }
    final void startGame(boolean first) {
        meFirst = first; firstTurn = true; over = false; status = ""; rematchSent = false; rematchIn = false; aiTimer = 0;
        newBoard();
    }
    @Override void reset() { startGame(true); }
    void flip() { firstTurn = !firstTurn; }
    void sendMove(String body) { if (opponent != null) PhoneNet.sendGame(opponent, gid, "V" + body); }
    void finish(String s) { over = true; status = s; }
    String whoWon(boolean firstWon) { return firstWon == meFirst ? "You win!" : (opponent == null ? "Computer wins" : opponent + " wins!"); }

    @Override void update(float dt) {
        if (opponent == null && !over && !myTurn()) { aiTimer += dt; if (aiTimer > 0.45f) { aiTimer = 0; aiMove(); } }
    }
    @Override void press(float x, float y) {
        if (in(x, y, 4, 196, 56, 212)) { resign(); return; }
        if (in(x, y, 62, 196, 114, 212)) { if (opponent == null) startGame(true); else if (over) requestRematch(); return; }
        if (myTurn()) boardClick(x, y);
    }
    void resign() { if (over) return; finish("You resigned"); if (opponent != null) PhoneNet.sendGame(opponent, gid, "R"); }
    @Override public void remoteResign() { if (!over) finish("Opponent resigned - you win!"); }
    private void requestRematch() {
        if (rematchSent) return;
        rematchSent = true;
        PhoneNet.sendGame(opponent, gid, "X");
        if (rematchIn) startGame(!meFirst);
    }
    @Override public void remoteRematch() { rematchIn = true; if (rematchSent) startGame(!meFirst); }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF1B1D24);
        txt(g, opponent == null ? title + " vs Computer" : title + " vs " + opponent, 5, 5, WHITE);
        String t = over ? status : (myTurn() ? "Your move" : (opponent == null ? "Thinking..." : "Waiting for " + opponent));
        txt(g, t, 5, 16, over ? 0xFFFFAA00 : GRAY);
        drawBoard(g);
        box(g, 4, 196, 56, 212, 0xFF8E2A2A); ctr(g, "Resign", 30, 200, WHITE, 1f);
        if (opponent == null) { box(g, 62, 196, 114, 212, 0xFF2A6E8E); ctr(g, "New game", 88, 200, WHITE, 1f); }
        else if (over) { box(g, 62, 196, 114, 212, 0xFF2A6E8E); ctr(g, rematchSent ? "Waiting..." : (rematchIn ? "Accept" : "Rematch"), 88, 200, WHITE, 1f); }
    }
}

/** Tic Tac Toe */
final class TicTacToeGame extends NetGame {
    static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
    final int[] b = new int[9];   // 0 empty, 1 first player (X), 2 second (O)

    TicTacToeGame(String opp, boolean first) { super("tictactoe", "Tic Tac Toe", "XO", 0xFF5E35B1, 'T', opp, first); startGame(first); }

    @Override void newBoard() { java.util.Arrays.fill(b, 0); }

    private int winner() { for (int[] l : LINES) if (b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]]; return 0; }
    private void apply(int idx, boolean byFirst) {
        b[idx] = byFirst ? 1 : 2;
        int w = winner();
        if (w != 0) finish(whoWon(w == 1));
        else { boolean full = true; for (int v : b) if (v == 0) full = false; if (full) finish("It's a draw"); }
    }
    @Override void boardClick(float x, float y) {
        if (!in(x, y, 8, 56, 110, 158)) return;
        int idx = (int) ((y - 56) / 34) * 3 + (int) ((x - 8) / 34);
        if (b[idx] != 0) return;
        apply(idx, meFirst); sendMove(String.valueOf(idx)); flip();
    }
    @Override public void remoteMove(String s) {
        if (over || myTurn() || s.isEmpty()) return;
        int idx = s.charAt(0) - '0';
        if (idx < 0 || idx > 8 || b[idx] != 0) return;
        apply(idx, !meFirst); flip();
    }
    @Override void aiMove() {
        int me = meFirst ? 2 : 1, you = meFirst ? 1 : 2, pick = -1;
        for (int pass = 0; pass < 2 && pick < 0; pass++) {
            int who = pass == 0 ? me : you;
            for (int[] l : LINES) {
                int cnt = 0, empty = -1;
                for (int i : l) { if (b[i] == who) cnt++; else if (b[i] == 0) empty = i; }
                if (cnt == 2 && empty >= 0) { pick = empty; break; }
            }
        }
        if (pick < 0 && b[4] == 0 && RND.nextInt(4) != 0) pick = 4;
        if (pick < 0) {
            List<Integer> e = new ArrayList<>();
            for (int i = 0; i < 9; i++) if (b[i] == 0) e.add(i);
            pick = e.get(RND.nextInt(e.size()));
        }
        apply(pick, !meFirst); flip();
    }
    @Override void drawBoard(GuiGraphicsExtractor g) {
        box(g, 8, 56, 110, 158, 0xFF2A2E3A);
        for (int i = 1; i < 3; i++) { box(g, 8 + i * 34 - 1, 56, 8 + i * 34 + 1, 158, 0xFF6C7289); box(g, 8, 56 + i * 34 - 1, 110, 56 + i * 34 + 1, 0xFF6C7289); }
        for (int i = 0; i < 9; i++) if (b[i] != 0) ctr(g, b[i] == 1 ? "X" : "O", 8 + (i % 3) * 34 + 17, 56 + (i / 3) * 34 + 6, b[i] == 1 ? 0xFF42A5F5 : 0xFFEF5350, 3f);
        for (int[] l : LINES) if (b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]])
            for (int i : l) box(g, 8 + (i % 3) * 34 + 2, 56 + (i / 3) * 34 + 2, 8 + (i % 3) * 34 + 32, 56 + (i / 3) * 34 + 32, 0x33FFEB3B);
        ctr(g, "You are " + (meFirst ? "X" : "O"), W / 2, 172, GRAY, 0.9f);
    }
}

/** Connect 4 */
final class Connect4Game extends NetGame {
    static final int R = 6, C = 7, CS = 15, X0 = 6, Y0 = 60;
    final int[][] b = new int[R][C];   // [row from bottom][col]; 1 first, 2 second

    Connect4Game(String opp, boolean first) { super("connect4", "Connect 4", "C4", 0xFF1565C0, '4', opp, first); startGame(first); }

    @Override void newBoard() { for (int[] r : b) java.util.Arrays.fill(r, 0); }

    private static int drop(int[][] bd, int col, int who) {
        for (int r = 0; r < R; r++) if (bd[r][col] == 0) { bd[r][col] = who; return r; }
        return -1;
    }
    private static boolean wins(int[][] bd, int who) {
        int[][] d = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) for (int[] v : d) {
            int k = 0;
            while (k < 4) { int rr = r + v[0] * k, cc = c + v[1] * k; if (rr < 0 || cc < 0 || rr >= R || cc >= C || bd[rr][cc] != who) break; k++; }
            if (k == 4) return true;
        }
        return false;
    }
    private static boolean full(int[][] bd) { for (int c = 0; c < C; c++) if (bd[R - 1][c] == 0) return false; return true; }

    private void apply(int col, boolean byFirst) {
        int who = byFirst ? 1 : 2;
        drop(b, col, who);
        if (wins(b, who)) finish(whoWon(byFirst)); else if (full(b)) finish("It's a draw");
    }
    @Override void boardClick(float x, float y) {
        if (!in(x, y, X0, Y0 - 12, X0 + C * CS, Y0 + R * CS)) return;
        int col = (int) ((x - X0) / CS);
        if (b[R - 1][col] != 0) return;
        apply(col, meFirst); sendMove(String.valueOf(col)); flip();
    }
    @Override public void remoteMove(String s) {
        if (over || myTurn() || s.isEmpty()) return;
        int col = s.charAt(0) - '0';
        if (col < 0 || col >= C || b[R - 1][col] != 0) return;
        apply(col, !meFirst); flip();
    }

    // ---- computer: alpha-beta, depth 4 ----
    private static int eval(int[][] bd, int me) {
        int you = 3 - me, s = 0;
        for (int r = 0; r < R; r++) if (bd[r][3] == me) s += 3;
        int[][] d = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) for (int[] v : d) {
            int m = 0, y = 0, e = 0; boolean ok = true;
            for (int k = 0; k < 4; k++) {
                int rr = r + v[0] * k, cc = c + v[1] * k;
                if (rr < 0 || cc < 0 || rr >= R || cc >= C) { ok = false; break; }
                if (bd[rr][cc] == me) m++; else if (bd[rr][cc] == you) y++; else e++;
            }
            if (!ok) continue;
            if (m == 3 && e == 1) s += 5; else if (m == 2 && e == 2) s += 2;
            if (y == 3 && e == 1) s -= 6;
        }
        return s;
    }
    private int search(int[][] bd, int depth, int alpha, int beta, boolean maxing, int me) {
        int you = 3 - me;
        if (wins(bd, me)) return 100000 + depth;
        if (wins(bd, you)) return -100000 - depth;
        if (full(bd) || depth == 0) return eval(bd, me);
        int best = maxing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int col : new int[]{3, 2, 4, 1, 5, 0, 6}) {
            if (bd[R - 1][col] != 0) continue;
            int[][] nb = new int[R][];
            for (int r = 0; r < R; r++) nb[r] = bd[r].clone();
            drop(nb, col, maxing ? me : you);
            int v = search(nb, depth - 1, alpha, beta, !maxing, me);
            if (maxing) { best = Math.max(best, v); alpha = Math.max(alpha, best); } else { best = Math.min(best, v); beta = Math.min(beta, best); }
            if (alpha >= beta) break;
        }
        return best;
    }
    @Override void aiMove() {
        int me = meFirst ? 2 : 1, bestCol = -1, best = Integer.MIN_VALUE;
        for (int col : new int[]{3, 2, 4, 1, 5, 0, 6}) {
            if (b[R - 1][col] != 0) continue;
            int[][] nb = new int[R][];
            for (int r = 0; r < R; r++) nb[r] = b[r].clone();
            drop(nb, col, me);
            int v = search(nb, 3, Integer.MIN_VALUE, Integer.MAX_VALUE, false, me) + RND.nextInt(3);
            if (v > best) { best = v; bestCol = col; }
        }
        if (bestCol < 0) return;
        apply(bestCol, !meFirst); flip();
    }

    @Override void drawBoard(GuiGraphicsExtractor g) {
        box(g, X0 - 2, Y0 - 2, X0 + C * CS + 2, Y0 + R * CS + 2, 0xFF1E4FA8);
        for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) {
            float x = X0 + c * CS, y = Y0 + (R - 1 - r) * CS;
            int col = b[r][c] == 0 ? 0xFF0D1B3A : b[r][c] == 1 ? 0xFFE53935 : 0xFFFDD835;
            box(g, x + 2, y + 1, x + CS - 2, y + CS - 1, col); box(g, x + 1, y + 2, x + CS - 1, y + CS - 2, col);
        }
        if (myTurn() && in(mx, my, X0, Y0 - 14, X0 + C * CS, Y0 + R * CS)) {
            int c = (int) ((mx - X0) / CS);
            box(g, X0 + c * CS + 3, Y0 - 10, X0 + c * CS + CS - 3, Y0 - 3, meFirst ? 0xFFE53935 : 0xFFFDD835);
        }
        ctr(g, "You are " + (meFirst ? "red" : "yellow"), W / 2, 172, GRAY, 0.9f);
    }
}

/** Checkers: forced captures, multi-jumps, kings. First player (red) moves up the board. */
final class CheckersGame extends NetGame {
    static final int CS = 14, BX = 3, BY = 34;
    static final int[][] DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    static final class Mv { int from, to; List<Integer> path = new ArrayList<>(), caps = new ArrayList<>(); }
    final int[] b = new int[64];   // +1/+2 first side man/king, -1/-2 second side
    int sel = -1, lastTo = -1, quiet;

    CheckersGame(String opp, boolean first) { super("checkers", "Checkers", "CHK", 0xFFB71C1C, 'K', opp, first); startGame(first); }

    @Override void newBoard() {
        java.util.Arrays.fill(b, 0);
        for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++) if ((r + f) % 2 == 0) { if (r < 3) b[r * 8 + f] = 1; else if (r > 4) b[r * 8 + f] = -1; }
        sel = -1; lastTo = -1; quiet = 0;
    }
    private int side() { return firstTurn ? 1 : -1; }
    private static String nm(int sq) { return "" + (char) ('a' + sq % 8) + (char) ('1' + sq / 8); }
    private static String pathStr(Mv m) { StringBuilder s = new StringBuilder(nm(m.from)); for (int p : m.path) s.append(nm(p)); return s.toString(); }

    private void jumps(int[] bd, int sq, int p, int side, Mv cur, List<Mv> out) {
        boolean king = Math.abs(p) == 2, extended = false;
        int r = sq / 8, f = sq % 8;
        for (int[] d : DIRS) {
            if (!king && d[0] != side) continue;
            int mr = r + d[0], mf = f + d[1], lr = r + 2 * d[0], lf = f + 2 * d[1];
            if (lr < 0 || lr > 7 || lf < 0 || lf > 7) continue;
            int mid = mr * 8 + mf, land = lr * 8 + lf;
            if (bd[mid] * side >= 0 || bd[land] != 0) continue;
            extended = true;
            int saved = bd[mid]; bd[mid] = 0;
            cur.path.add(land); cur.caps.add(mid);
            boolean crowned = !king && (lr == (side > 0 ? 7 : 0));
            if (crowned) out.add(copy(cur)); else jumps(bd, land, p, side, cur, out);
            cur.path.remove(cur.path.size() - 1); cur.caps.remove(cur.caps.size() - 1);
            bd[mid] = saved;
        }
        if (!extended && !cur.path.isEmpty()) out.add(copy(cur));
    }
    private static Mv copy(Mv m) { Mv n = new Mv(); n.from = m.from; n.path.addAll(m.path); n.caps.addAll(m.caps); n.to = m.path.get(m.path.size() - 1); return n; }

    List<Mv> legal(int side) {
        List<Mv> jumpsL = new ArrayList<>(), steps = new ArrayList<>();
        int[] bd = b.clone();
        for (int sq = 0; sq < 64; sq++) {
            int p = bd[sq];
            if (p * side <= 0) continue;
            bd[sq] = 0;
            Mv cur = new Mv(); cur.from = sq;
            jumps(bd, sq, p, side, cur, jumpsL);
            bd[sq] = p;
            boolean king = Math.abs(p) == 2;
            for (int[] d : DIRS) {
                if (!king && d[0] != side) continue;
                int r = sq / 8 + d[0], f = sq % 8 + d[1];
                if (r < 0 || r > 7 || f < 0 || f > 7 || b[r * 8 + f] != 0) continue;
                Mv m = new Mv(); m.from = sq; m.path.add(r * 8 + f); m.to = r * 8 + f; steps.add(m);
            }
        }
        return jumpsL.isEmpty() ? steps : jumpsL;
    }
    private void applyMv(Mv m) {
        int p = b[m.from]; b[m.from] = 0;
        for (int c : m.caps) b[c] = 0;
        int side = p > 0 ? 1 : -1;
        if (Math.abs(p) == 1 && m.to / 8 == (side > 0 ? 7 : 0)) p = 2 * side;
        b[m.to] = p; lastTo = m.to; sel = -1;
    }
    private void afterMove() {   // called after flip()
        if (legal(side()).isEmpty()) finish(whoWon(!firstTurn));
    }
    private void play(Mv m, boolean local) {
        boolean progress = !m.caps.isEmpty() || Math.abs(b[m.from]) == 1;
        applyMv(m);
        quiet = progress ? 0 : quiet + 1;
        if (local) sendMove(pathStr(m));
        flip(); afterMove();
        if (!over && quiet >= 60) finish("Draw - no progress");
    }
    @Override public void remoteMove(String s) {
        if (over || myTurn()) return;
        for (Mv m : legal(side())) if (pathStr(m).equals(s)) { play(m, false); return; }
    }

    private int squareAt(float x, float y) {
        int col = (int) Math.floor((x - BX) / CS), row = (int) Math.floor((y - BY) / CS);
        if (col < 0 || col > 7 || row < 0 || row > 7) return -1;
        int r = meFirst ? 7 - row : row, f = meFirst ? col : 7 - col;
        return r * 8 + f;
    }
    @Override void boardClick(float x, float y) {
        int sq = squareAt(x, y);
        if (sq < 0) return;
        int mine = meFirst ? 1 : -1;
        List<Mv> all = legal(mine);
        if (sel >= 0) for (Mv m : all) if (m.from == sel && m.to == sq) { play(m, true); return; }
        if (b[sq] * mine > 0) { for (Mv m : all) if (m.from == sq) { sel = sq; return; } }
        sel = -1;
    }
    @Override void aiMove() {
        int ai = meFirst ? -1 : 1;
        List<Mv> all = legal(ai);
        if (all.isEmpty()) return;
        Mv best = all.get(0); int bs = Integer.MIN_VALUE;
        for (Mv m : all) {
            int p = b[m.from], sc = m.caps.size() * 10 + RND.nextInt(4);
            if (Math.abs(p) == 1 && m.to / 8 == (ai > 0 ? 7 : 0)) sc += 6;
            if (Math.abs(p) == 1) sc += ai > 0 ? m.to / 8 : 7 - m.to / 8;
            int edge = m.to % 8; if (edge == 0 || edge == 7) sc += 1;
            if (sc > bs) { bs = sc; best = m; }
        }
        play(best, false);
    }

    @Override void drawBoard(GuiGraphicsExtractor g) {
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            int r = meFirst ? 7 - row : row, f = meFirst ? col : 7 - col, sq = r * 8 + f;
            boolean dark = (r + f) % 2 == 0;
            float x = BX + col * CS, y = BY + row * CS;
            int c = dark ? 0xFF6D4C41 : 0xFFE0C9A6;
            if (sq == lastTo) c = 0xFF8D6E3F;
            if (sq == sel) c = 0xFF4FC3F7;
            box(g, x, y, x + CS, y + CS, c);
            int p = b[sq];
            if (p != 0) {
                int pc = p > 0 ? 0xFFD32F2F : 0xFFEEEEEE;
                box(g, x + 3, y + 1, x + CS - 3, y + CS - 1, pc); box(g, x + 1, y + 3, x + CS - 1, y + CS - 3, pc); box(g, x + 2, y + 2, x + CS - 2, y + CS - 2, pc);
                if (Math.abs(p) == 2) box(g, x + 5, y + 5, x + 9, y + 9, 0xFFFFC107);
            }
        }
        if (sel >= 0 && myTurn()) for (Mv m : legal(meFirst ? 1 : -1)) if (m.from == sel) {
            int r = m.to / 8, f = m.to % 8, col = meFirst ? f : 7 - f, row = meFirst ? 7 - r : r;
            box(g, BX + col * CS + 5, BY + row * CS + 5, BX + col * CS + 9, BY + row * CS + 9, 0xFF69F0AE);
        }
        ctr(g, "You are " + (meFirst ? "red" : "white") + " - captures are forced", W / 2, 152, GRAY, 0.75f);
    }
}
