package com.phonemod;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Best scores and win counts, kept in config/phonemod-scores.txt so they survive closing the game. */
final class Scores {
    private static final Map<String, Integer> M = new HashMap<>();
    static Path fileOverride;   // tests only

    static { load(); }

    private static Path file() { return fileOverride != null ? fileOverride : FabricLoader.getInstance().getConfigDir().resolve("phonemod-scores.txt"); }

    static void load() {
        M.clear();
        try {
            List<String> ls = Files.readAllLines(file());
            for (String l : ls) { int i = l.indexOf('='); if (i > 0) M.put(l.substring(0, i), Integer.parseInt(l.substring(i + 1).trim())); }
        } catch (Exception ignored) { }
    }

    static void save() {
        try {
            StringBuilder sb = new StringBuilder();
            for (var e : M.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            Files.writeString(file(), sb.toString());
        } catch (Exception ignored) { }
    }

    static int get(String key) { return M.getOrDefault(key, 0); }

    /** higher is better; returns true if this is a new record */
    static boolean raise(String key, int v) {
        if (v > get(key)) { M.put(key, v); save(); return true; }
        return false;
    }

    /** lower is better (0 = none yet); returns true if this is a new record */
    static boolean lower(String key, int v) {
        int c = get(key);
        if (v > 0 && (c == 0 || v < c)) { M.put(key, v); save(); return true; }
        return false;
    }

    static void add(String key, int n) { M.merge(key, n, Integer::sum); save(); }

    /** write every game's unsaved best on exit */
    static void installExitHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Game g : Games.ALL) { try { g.saveStats(); } catch (Throwable ignored) { } }
        }));
    }
}
