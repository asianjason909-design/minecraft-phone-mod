package com.phonemod;

/** Score-based game: tap after game over restarts. */
abstract class Arcade extends Game {
    int score, best;
    boolean over;

    Arcade(String id, String title, String abbr, int color) { super(id, title, abbr, color); }

    void die() { over = true; best = Math.max(best, score); }

    @Override void press(float x, float y) { if (over) { reset(); return; } tap(x, y); }
    void tap(float x, float y) { }
    @Override void update(float dt) { if (!over) step(Math.min(dt, 0.05f)); }
    abstract void step(float dt);
}
