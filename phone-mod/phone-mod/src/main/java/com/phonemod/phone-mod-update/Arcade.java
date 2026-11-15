package com.phonemod;

/** Score-based game: tap after game over restarts. Best score is saved to disk. */
abstract class Arcade extends Game {
    int score, best;
    boolean over;
    boolean scoreSfx = true;      // play a pickup blip when the score goes up
    private int lastScore;

    Arcade(String id, String title, String abbr, int color) { super(id, title, abbr, color); best = Scores.get(id); }

    void die() {
        if (over) return;
        over = true;
        best = Math.max(best, score);
        if (Scores.raise(id, best) && score > 0) Sfx.share('l'); else Sfx.share('x');
    }

    @Override void saveStats() { best = Math.max(best, score); Scores.raise(id, best); }

    @Override void press(float x, float y) { if (over) { reset(); return; } tap(x, y); }
    void tap(float x, float y) { }

    @Override void update(float dt) {
        if (over) return;
        step(Math.min(dt, 0.05f));
        if (score > best) best = score;
        if (scoreSfx && score > lastScore && !over) Sfx.play(score - lastScore >= 100 ? 'l' : 'p');
        lastScore = score;
    }
    abstract void step(float dt);
}
