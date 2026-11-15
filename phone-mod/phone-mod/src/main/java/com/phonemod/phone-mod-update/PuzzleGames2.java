package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Mahjong solitaire: match free pairs. A tile is free if nothing is on top and its left or right side is open. */
final class MahjongApp extends Game {
    static final class T { int c, r, layer, sym; boolean gone; }
    static final String SYMS = "123456789ABCDEFGHK";
    static final int[] COLS = {0xFFD32F2F, 0xFF1976D2, 0xFF388E3C, 0xFF7B1FA2, 0xFFEF6C00, 0xFF00838F};
    final List<T> tiles = new ArrayList<>();
    T sel;
    String msg = "";
    boolean stuck;

    MahjongApp() { super("mahjong", "Mahjong", "MJ", 0xFF00897B); reset(); }

    @Override void reset() {
        tiles.clear(); sel = null; msg = "";
        for (int r = 0; r < 4; r++) for (int c = 0; c < 6; c++) add(c, r, 0);
        for (int r = 0; r < 3; r++) for (int c = 1; c < 5; c++) add(c, r, 1);
        deal();
    }
    private void add(int c, int r, int l) { T t = new T(); t.c = c; t.r = r; t.layer = l; tiles.add(t); }
    /** Give the remaining tiles symbols in matching pairs, built by simulating a full clear so a solution always exists. */
    private void deal() {
        List<T> live = new ArrayList<>();
        for (T t : tiles) if (!t.gone) live.add(t);
        if (live.isEmpty()) return;
        for (int attempt = 0; attempt < 300; attempt++) {
            boolean[] removed = new boolean[live.size()];
            int[] sym = new int[live.size()];
            int left = live.size(), pair = 0;
            boolean ok = true;
            while (left > 0) {
                List<Integer> free = new ArrayList<>();
                for (int i = 0; i < live.size(); i++) if (!removed[i] && freeIn(live, removed, i)) free.add(i);
                if (free.size() < 2) { ok = false; break; }
                java.util.Collections.shuffle(free, RND);
                int a = free.get(0), b = free.get(1);
                sym[a] = pair % SYMS.length(); sym[b] = pair % SYMS.length(); pair++;
                removed[a] = true; removed[b] = true; left -= 2;
            }
            if (ok) { for (int i = 0; i < live.size(); i++) live.get(i).sym = sym[i]; stuck = false; return; }
        }
        stuck = true;   // these tiles can never all be cleared (for example the last two are stacked)
        List<Integer> syms = new ArrayList<>();
        for (int i = 0; i < live.size() / 2; i++) { syms.add(i % SYMS.length()); syms.add(i % SYMS.length()); }
        java.util.Collections.shuffle(syms, RND);
        for (int i = 0; i < live.size(); i++) live.get(i).sym = syms.get(i);
    }
    private static boolean freeIn(List<T> live, boolean[] removed, int i) {
        T t = live.get(i);
        boolean leftBlocked = false, rightBlocked = false;
        for (int j = 0; j < live.size(); j++) {
            if (removed[j] || j == i) continue;
            T o = live.get(j);
            if (t.layer == 0 && o.layer == 1 && o.c == t.c && o.r == t.r) return false;
            if (o.layer == t.layer && o.r == t.r) { if (o.c == t.c - 1) leftBlocked = true; if (o.c == t.c + 1) rightBlocked = true; }
        }
        return !(leftBlocked && rightBlocked);
    }
    private T at(int c, int r, int l) { for (T t : tiles) if (!t.gone && t.c == c && t.r == r && t.layer == l) return t; return null; }
    private boolean free(T t) {
        if (t.layer == 0 && at(t.c, t.r, 1) != null) return false;
        return at(t.c - 1, t.r, t.layer) == null || at(t.c + 1, t.r, t.layer) == null;
    }
    private boolean hasMove() {
        List<T> f = new ArrayList<>();
        for (T t : tiles) if (!t.gone && free(t)) f.add(t);
        for (int i = 0; i < f.size(); i++) for (int j = i + 1; j < f.size(); j++) if (f.get(i).sym == f.get(j).sym) return true;
        return false;
    }
    private boolean cleared() { for (T t : tiles) if (!t.gone) return false; return true; }
    float tx(T t) { return 8 + t.c * 17 - t.layer * 3; }
    float ty(T t) { return 40 + t.r * 22 - t.layer * 3; }

    @Override void press(float x, float y) {
        if (in(x, y, 4, 160, 56, 176)) { deal(); sel = null; msg = stuck ? "Stuck - tap New" : "Shuffled"; return; }
        if (in(x, y, 62, 160, 114, 176)) { reset(); return; }
        T hit = null;
        for (int l = 1; l >= 0 && hit == null; l--)
            for (T t : tiles) if (!t.gone && t.layer == l && in(x, y, tx(t), ty(t), tx(t) + 16, ty(t) + 20)) { hit = t; break; }
        if (hit == null) return;
        if (!free(hit)) { msg = "That tile is blocked"; return; }
        msg = "";
        if (sel == hit) { sel = null; return; }
        if (sel != null && sel.sym == hit.sym) {
            sel.gone = true; hit.gone = true; sel = null;
            Sfx.play('p');
            if (cleared()) { msg = "Cleared! Tap New"; Scores.add("mahjong", 1); Sfx.share('w'); }
            else if (!hasMove()) msg = "No moves - shuffle!";
        } else sel = hit;
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF0F3D2E);
        int left = 0; for (T t : tiles) if (!t.gone) left++;
        txt(g, "Mahjong  Tiles " + left, 5, 6, WHITE); left(g, "Cleared " + Scores.get("mahjong"), 80, 16, GRAY, 0.7f);
        for (int l = 0; l <= 1; l++) for (T t : tiles) if (!t.gone && t.layer == l) {
            float x = tx(t), y = ty(t);
            boolean f = free(t);
            box(g, x + 1, y + 1, x + 17, y + 21, 0xFF000000);
            box(g, x, y, x + 16, y + 20, t == sel ? 0xFFFFF176 : f ? 0xFFF5F0E1 : 0xFFB8B2A0);
            box(g, x + 1, y + 1, x + 15, y + 19, t == sel ? 0xFFFFF59D : f ? 0xFFFFFBF0 : 0xFFC9C3B1);
            ctr(g, String.valueOf(SYMS.charAt(t.sym)), (int) x + 8, (int) y + 6, COLS[t.sym % COLS.length], 1.3f);
        }
        box(g, 4, 160, 56, 176, 0xFF37474F); ctr(g, "Shuffle", 30, 164, WHITE, 1f);
        box(g, 62, 160, 114, 176, 0xFF455A64); ctr(g, "New", 88, 164, WHITE, 1f);
        ctr(g, msg.isEmpty() ? "Match free pairs" : msg, W / 2, 186, msg.isEmpty() ? GRAY : 0xFFFFD54F, 0.9f);
    }
}

/** Wordle: type letters (or tap the keyboard), Enter to guess. */
final class WordleApp extends Game {
    static final String[] WORDS = ("about above actor adapt admit adult after again agent agree ahead alarm album alert alien alive allow alone along angle angry apple apply arena argue arise armor array arrow aside asset avoid awake award aware badge basic beach beard beast begin being below bench black blade blame blank blast blaze blend block blood board boost brain brave bread break brick brief bring broad brown brush build burst cabin cable candy carry catch cause chain chair chalk charm chase cheap check chess chest chief child choir civil claim class clean clear clerk climb clock close cloud coach coast color count court cover craft crane crash crazy cream crime cross crowd crown cycle daily dance death delay depth dirty doubt dozen draft drain drama dream dress drink drive eager early earth eight elite empty enemy enjoy enter entry equal error event every exact exist extra faith false fault feast fiber field fifth fight final first flame flash fleet floor fluid focus force forge forth found frame fresh front fruit giant glass globe glory grace grade grain grand grant grape grass great green group guard guess guest guide happy heart heavy hello honey horse hotel house human ideal image index inner input issue jelly joint judge juice knife knock label large laser later laugh layer learn least leave legal lemon level light limit local logic loose lucky lunch magic major maple march match maybe mayor media mercy metal model money month moral motor mount mouse mouth movie music nerve never night noble noise north novel nurse ocean offer often olive onion opera orbit order other outer owner paint panel paper party pasta patch pause peace pearl phase phone photo piano pilot pitch pizza place plain plane plant plate point power press price pride prize proof proud queen quest quick quiet quite radio raise range rapid ratio reach ready realm relax reply river robot rocky round route royal rural salad scale scene scope score sense serve seven shade shake shape share sharp sheep sheet shelf shell shift shine shirt shock shoot short sight silly since skill sleep slide small smart smile smoke snake solid solve sound south space spare speak speed spend spice spoon sport staff stage stair stand start state steam steel stick still stock stone store storm story strip study stuff style sugar super sweet swift table taste teach thank theme there thick thing think third those three throw thumb tiger tight title toast today tooth topic total touch tough tower trace track trade trail train trash treat trend trial tribe trick truck truly trust truth twice uncle under union unity until upper urban usage usual valid value video virus visit vital voice waste watch water wheel where which while white whole whose woman world worry worth would write wrong young youth zebra").split(" ");
    static final String[] ROWS = {"QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"};
    final String[] guesses = new String[6];
    String cur = "", answer = "";
    int n;
    boolean won, lost;
    String msg = "";
    final java.util.Map<Character, Integer> keyState = new java.util.HashMap<>();

    WordleApp() { super("wordle", "Wordle", "WRD", 0xFF538D4E); reset(); }

    @Override void reset() {
        answer = WORDS[RND.nextInt(WORDS.length)].toUpperCase();
        java.util.Arrays.fill(guesses, null); cur = ""; n = 0; won = false; lost = false; msg = ""; keyState.clear();
    }
    @Override boolean capturesText() { return true; }
    @Override void typed(String s) {
        if (won || lost) return;
        char c = Character.toUpperCase(s.charAt(0));
        if (c >= 'A' && c <= 'Z' && cur.length() < 5) { cur += c; msg = ""; }
    }
    @Override boolean key(int k) {
        if (k == K_BACKSPACE) { if (!cur.isEmpty()) cur = cur.substring(0, cur.length() - 1); return true; }
        if (k == K_ENTER || k == 335) { submit(); return true; }
        return true;
    }
    private int[] score(String guess) {
        int[] r = new int[5];
        boolean[] used = new boolean[5];
        for (int i = 0; i < 5; i++) if (guess.charAt(i) == answer.charAt(i)) { r[i] = 2; used[i] = true; }
        for (int i = 0; i < 5; i++) if (r[i] == 0) for (int j = 0; j < 5; j++) if (!used[j] && guess.charAt(i) == answer.charAt(j)) { r[i] = 1; used[j] = true; break; }
        return r;
    }
    private void submit() {
        if (won || lost) return;
        if (cur.length() < 5) { msg = "Not enough letters"; return; }
        guesses[n++] = cur;
        int[] sc = score(cur);
        for (int i = 0; i < 5; i++) keyState.merge(cur.charAt(i), sc[i] + 1, Math::max);
        Sfx.play('c');
        if (cur.equals(answer)) { won = true; msg = "You got it!"; Scores.add("wordle", 1); Sfx.share('w'); }
        else if (n >= 6) { lost = true; msg = "It was " + answer; Sfx.share('x'); }
        cur = "";
    }
    private float kx(int row, int i) {
        int len = ROWS[row].length();
        if (row == 2) return 4 + 17 + i * 11 + 1;
        return (W - len * 11) / 2f + i * 11;
    }
    @Override void press(float x, float y) {
        if (won || lost) { reset(); return; }
        for (int row = 0; row < 3; row++) {
            float ky = 146 + row * 15;
            for (int i = 0; i < ROWS[row].length(); i++) if (in(x, y, kx(row, i), ky, kx(row, i) + 10, ky + 13)) { typed(String.valueOf(ROWS[row].charAt(i))); return; }
        }
        if (in(x, y, 4, 176, 21, 189)) submit();
        else if (in(x, y, 97, 176, 114, 189)) key(K_BACKSPACE);
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF121213);
        ctr(g, "WORDLE", W / 2, 6, WHITE, 1.3f); left(g, "Wins " + Scores.get("wordle"), 5, 7, GRAY, 0.8f);
        for (int r = 0; r < 6; r++) {
            String gs = r < n ? guesses[r] : r == n ? cur : "";
            int[] sc = r < n ? score(gs) : null;
            for (int c = 0; c < 5; c++) {
                float x = 13 + c * 19, y = 22 + r * 19;
                int col = sc == null ? 0xFF3A3A3C : sc[c] == 2 ? 0xFF538D4E : sc[c] == 1 ? 0xFFB59F3B : 0xFF3A3A3C;
                box(g, x, y, x + 17, y + 17, sc == null ? 0xFF565758 : col);
                box(g, x + 1, y + 1, x + 16, y + 16, sc == null ? 0xFF121213 : col);
                if (c < gs.length()) ctr(g, String.valueOf(gs.charAt(c)), (int) x + 8, (int) y + 5, WHITE, 1.2f);
            }
        }
        ctr(g, msg.isEmpty() ? "Type letters, Enter = guess" : msg, W / 2, 137, msg.isEmpty() ? 0xFF818384 : WHITE, 0.8f);
        for (int row = 0; row < 3; row++) for (int i = 0; i < ROWS[row].length(); i++) {
            char ch = ROWS[row].charAt(i);
            int st = keyState.getOrDefault(ch, 0);
            box(g, kx(row, i), 146 + row * 15, kx(row, i) + 10, 159 + row * 15, st == 3 ? 0xFF538D4E : st == 2 ? 0xFFB59F3B : st == 1 ? 0xFF3A3A3C : 0xFF818384);
            ctr(g, String.valueOf(ch), (int) kx(row, i) + 5, 149 + row * 15, WHITE, 0.9f);
        }
        box(g, 4, 176, 21, 189, 0xFF538D4E); ctr(g, "OK", 12, 179, WHITE, 0.9f);
        box(g, 97, 176, 114, 189, 0xFF818384); ctr(g, "Del", 105, 179, WHITE, 0.9f);
        if (won || lost) ctr(g, "Tap to play again", W / 2, 198, GRAY, 0.8f);
    }
}

/** Unblock: slide the cars so the red one can leave on the right. Drag a car along its row/column. */
final class UnblockApp extends Game {
    static final String[][] LEVELS = {
        {"HHHIII", "BB.GG.", ".AAC.F", ".J.C.F", "DJEC.F", "D.E..."},
        {"B.III.", "BEEEGJ", "BAADGJ", "...DC.", ".H.DC.", ".HFFF."},
        {"..H.B.", "..HEB.", ".AAEB.", ".F.CCC", ".FD.GG", "..D..."},
        {"EEE.FF", "..IK.G", "AAIK.G", ".DBBBC", ".D..JC", "HH..J."},
        {"HHK..I", ".JK..I", "CJAA.I", "C.DGGG", "C.DBEE", ".FFB.."},
        {"..D.II", "..DHHH", "JAAK..", "JEEK.C", "FFGGGC", "..BB.C"}};
    static final int CS = 16, X0 = 11, Y0 = 44;
    static final class Car { int r, c, len; boolean horiz; char ch; }
    final List<Car> cars = new ArrayList<>();
    int level, moves, drag = -1;
    float anchorX, anchorY;
    boolean solved;

    UnblockApp() { super("unblock", "Unblock Puzzle", "UNB", 0xFFBF360C); resetOnOpen = false; load(Math.min(Scores.get("unblock"), LEVELS.length - 1)); }

    @Override void reset() { load(level); }

    private void load(int lv) {
        level = lv % LEVELS.length; moves = 0; solved = false; drag = -1; cars.clear();
        String[] g = LEVELS[level];
        for (char ch = 'A'; ch <= 'Z'; ch++) {
            Car car = null; int r1 = 9, c1 = 9, r2 = -1, c2 = -1;
            for (int r = 0; r < 6; r++) for (int c = 0; c < 6; c++) if (g[r].charAt(c) == ch) { r1 = Math.min(r1, r); c1 = Math.min(c1, c); r2 = Math.max(r2, r); c2 = Math.max(c2, c); }
            if (r2 < 0) continue;
            car = new Car(); car.ch = ch; car.r = r1; car.c = c1; car.horiz = r1 == r2; car.len = car.horiz ? c2 - c1 + 1 : r2 - r1 + 1;
            cars.add(car);
        }
    }
    private Car at(int r, int c) {
        for (Car k : cars) for (int i = 0; i < k.len; i++) if ((k.horiz ? k.r : k.r + i) == r && (k.horiz ? k.c + i : k.c) == c) return k;
        return null;
    }
    /** how far (in cells) a car can move in direction dir (-1/+1) */
    private int room(Car k, int dir) {
        int n = 0;
        while (true) {
            int r = k.horiz ? k.r : (dir < 0 ? k.r - n - 1 : k.r + k.len + n), c = k.horiz ? (dir < 0 ? k.c - n - 1 : k.c + k.len + n) : k.c;
            if (r < 0 || c < 0 || r >= 6 || c >= 6 || at(r, c) != null) return n;
            n++;
        }
    }
    private float delta() {
        Car k = cars.get(drag);
        float d = k.horiz ? mx - anchorX : my - anchorY;
        return clamp(d, -room(k, -1) * CS, room(k, 1) * CS);
    }

    @Override void press(float x, float y) {
        if (in(x, y, 4, 168, 56, 184)) { load(level); return; }
        if (in(x, y, 62, 168, 114, 184)) { load(level + 1); return; }
        if (solved) { load(level + 1); return; }
        if (!in(x, y, X0, Y0, X0 + 6 * CS, Y0 + 6 * CS)) return;
        Car k = at((int) ((y - Y0) / CS), (int) ((x - X0) / CS));
        if (k != null) { drag = cars.indexOf(k); anchorX = x; anchorY = y; }
    }
    @Override void release(float x, float y) {
        if (drag < 0) return;
        Car k = cars.get(drag);
        int cells = Math.round(delta() / CS);
        if (cells != 0) { if (k.horiz) k.c += cells; else k.r += cells; moves++; }
        drag = -1;
        Car t = cars.get(0);
        if (cells != 0) Sfx.play('k');
        if (t.c + t.len == 6 && !solved) { solved = true; Scores.raise("unblock", level + 1); Sfx.share('w'); }
    }

    @Override void draw(GuiGraphicsExtractor g) {
        box(g, 0, 0, W, H, 0xFF2B1B12);
        txt(g, "Level " + (level + 1) + "/" + LEVELS.length, 5, 6, WHITE); txt(g, "Moves " + moves, 5, 17, GRAY);
        box(g, X0 - 3, Y0 - 3, X0 + 6 * CS + 3, Y0 + 6 * CS + 3, 0xFF5D4037);
        box(g, X0, Y0, X0 + 6 * CS, Y0 + 6 * CS, 0xFF8D6E63);
        box(g, X0 + 6 * CS, Y0 + 2 * CS + 2, X0 + 6 * CS + 8, Y0 + 3 * CS - 2, 0xFF8D6E63);
        ctr(g, ">", X0 + 6 * CS + 4, Y0 + 2 * CS + 4, 0xFFFFD54F, 1f);
        int[] pal = {0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835, 0xFF8E24AA, 0xFFFB8C00, 0xFF00ACC1, 0xFF6D4C41, 0xFF546E7A, 0xFFD81B60};
        for (int i = 0; i < cars.size(); i++) {
            Car k = cars.get(i);
            float off = i == drag ? delta() : 0;
            float x = X0 + k.c * CS + (k.horiz ? off : 0), y = Y0 + k.r * CS + (k.horiz ? 0 : off);
            int col = i == 0 ? pal[0] : pal[1 + (i - 1) % 9];
            float w = k.horiz ? k.len * CS : CS, h = k.horiz ? CS : k.len * CS;
            box(g, x + 1, y + 1, x + w - 1, y + h - 1, col);
            box(g, x + 3, y + 3, x + w - 3, y + h - 3, 0x33FFFFFF);
        }
        box(g, 4, 168, 56, 184, 0xFF455A64); ctr(g, "Restart", 30, 172, WHITE, 1f);
        box(g, 62, 168, 114, 184, 0xFF455A64); ctr(g, "Skip >", 88, 172, WHITE, 1f);
        if (solved) { box(g, 10, 96, W - 10, 150, 0xDD000000); ctr(g, "Solved!", W / 2, 104, 0xFF8FD6DD, 1.4f); ctr(g, moves + " moves", W / 2, 122, WHITE, 1f); ctr(g, "Tap for next level", W / 2, 135, GRAY, 0.8f); }
        else ctr(g, "Drag the red car out ->", W / 2, 196, GRAY, 0.8f);
    }
}
