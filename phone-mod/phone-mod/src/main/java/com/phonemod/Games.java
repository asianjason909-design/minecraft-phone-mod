package com.phonemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

final class BlastApp extends Game {
    private final BlockBlastGame inner = new BlockBlastGame();
    private int lastScore, savedBest;
    private boolean wasOver;

    BlastApp() { super("blast", "Block Blast", "BB", 0xFF263056); inner.setBest(Scores.get(id)); savedBest = inner.getBest(); }
    @Override void reset() { inner.reset(); lastScore = 0; wasOver = false; }
    @Override void saveStats() { int b = inner.getBest(); if (b > savedBest) { savedBest = b; Scores.raise(id, b); } }
    @Override void draw(GuiGraphicsExtractor g) {
        inner.draw(g, mx, my);
        int s = inner.getScore();
        if (s > lastScore) Sfx.play(s - lastScore >= 20 ? 'l' : 'k');
        lastScore = s;
        if (inner.isOver() && !wasOver) { Sfx.share('x'); saveStats(); }
        wasOver = inner.isOver();
    }
    @Override void press(float x, float y) { inner.press(x, y); }
    @Override void release(float x, float y) { inner.release(x, y); }
    @Override void icon(GuiGraphicsExtractor g, int x, int y) { PhoneScreen.builtinIcon(g, 0, x, y); }
}

final class FlappyApp extends Game {
    private final FlappyGame inner = new FlappyGame();
    private int lastScore, savedBest;
    private boolean wasDead;

    FlappyApp() { super("flappy", "Flappy Bird", "FB", 0xFF70C5CE); inner.setBest(Scores.get(id)); savedBest = inner.getBest(); }
    @Override void reset() { inner.reset(); lastScore = 0; wasDead = false; }
    @Override void saveStats() { int b = inner.getBest(); if (b > savedBest) { savedBest = b; Scores.raise(id, b); } }
    @Override void update(float dt) {
        inner.update(dt);
        int s = inner.getScore();
        if (s > lastScore) Sfx.play('p');
        lastScore = s;
        if (inner.isDead() && !wasDead) { Sfx.share('x'); saveStats(); }
        wasDead = inner.isDead();
    }
    @Override void draw(GuiGraphicsExtractor g) { inner.draw(g); }
    @Override void press(float x, float y) { Sfx.play('j'); inner.flap(); }
    @Override boolean key(int k) { if (k == K_SPACE || k == K_UP || k == K_W) { Sfx.play('j'); inner.flap(); return true; } return false; }
    @Override void icon(GuiGraphicsExtractor g, int x, int y) { PhoneScreen.builtinIcon(g, 1, x, y); }
}

final class SnakeApp extends Game {
    private final SnakeGame inner = new SnakeGame();
    private int lastScore, savedBest;
    private boolean wasDead;

    SnakeApp() { super("snake", "Snake", "SN", 0xFF2E7D32); inner.setBest(Scores.get(id)); savedBest = inner.getBest(); }
    @Override void reset() { inner.reset(); lastScore = 0; wasDead = false; }
    @Override void saveStats() { int b = inner.getBest(); if (b > savedBest) { savedBest = b; Scores.raise(id, b); } }
    @Override void update(float dt) {
        inner.update(dt);
        int s = inner.getScore();
        if (s > lastScore) Sfx.play('p');
        lastScore = s;
        if (inner.isDead() && !wasDead) { Sfx.share('x'); saveStats(); }
        wasDead = inner.isDead();
    }
    @Override void draw(GuiGraphicsExtractor g) { inner.draw(g); }
    @Override void press(float x, float y) { inner.press(x, y); }
    @Override boolean key(int k) { return inner.key(k); }
    @Override void icon(GuiGraphicsExtractor g, int x, int y) { PhoneScreen.builtinIcon(g, 2, x, y); }
}

/** Every game on the phone, in default home-screen order. */
final class Games {
    static final String[] DEFAULT_ORDER = {
        "messages", "call", "facetime", "camera", "music", "blast", "flappy", "snake", "chess", "tictactoe", "connect4",
        "checkers", "tetris", "2048", "dino", "pong", "mines", "brick", "doodle", "invaders", "galaga",
        "pac", "asteroids", "rhythm", "crossy", "sudoku", "mahjong", "wordle", "unblock", "golf", "heli",
        "race", "paddle", "settings"};
    static final List<Game> ALL = new ArrayList<>();

    static {
        ALL.add(new CallApp());
        ALL.add(new FaceTimeApp().lbl("FaceTime"));
        ALL.add(new CameraApp());
        ALL.add(new MusicApp());
        ALL.add(new BlastApp().lbl("Blast"));
        ALL.add(new FlappyApp().lbl("Flappy"));
        ALL.add(new SnakeApp());
        ALL.add(new ChessGame(null, true));
        ALL.add(new TicTacToeGame(null, true).lbl("TicTac"));
        ALL.add(new Connect4Game(null, true).lbl("Connect4"));
        ALL.add(new CheckersGame(null, true));
        ALL.add(new TetrisApp());
        ALL.add(new Game2048App());
        ALL.add(new DinoApp().lbl("Dino"));
        ALL.add(new PongApp());
        ALL.add(new MinesApp().lbl("Mines"));
        ALL.add(new BrickApp().lbl("Bricks"));
        ALL.add(new DoodleApp().lbl("Doodle"));
        ALL.add(new InvadersApp().lbl("Invaders"));
        ALL.add(new GalagaApp());
        ALL.add(new PacApp().lbl("Pac-Man"));
        ALL.add(new AsteroidsApp());
        ALL.add(new RhythmApp().lbl("Rhythm"));
        ALL.add(new CrossyApp().lbl("Crossy"));
        ALL.add(new SudokuApp());
        ALL.add(new MahjongApp());
        ALL.add(new WordleApp());
        ALL.add(new UnblockApp().lbl("Unblock"));
        ALL.add(new GolfApp().lbl("Golf"));
        ALL.add(new HeliApp().lbl("Heli"));
        ALL.add(new RaceApp().lbl("Race"));
        ALL.add(new PaddleApp().lbl("Paddle"));
    }

    static Game byId(String id) {
        for (Game g : ALL) if (g.id.equals(id)) return g;
        return null;
    }

    /** A fresh two-player game against a friend (ids: C chess, T tic tac toe, 4 connect 4, K checkers). */
    static Game create(char gid, String opponent, boolean first) {
        return switch (gid) {
            case 'C' -> new ChessGame(opponent, first);
            case 'T' -> new TicTacToeGame(opponent, first);
            case '4' -> new Connect4Game(opponent, first);
            case 'K' -> new CheckersGame(opponent, first);
            case 'P' -> new PingPongGame(opponent, first);
            default -> null;
        };
    }
}
