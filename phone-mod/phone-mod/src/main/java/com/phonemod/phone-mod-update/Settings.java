package com.phonemod;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Saved phone settings (config/phonemod.txt): wallpaper, icon order, sound on/off, nearby sharing on/off. */
final class Settings {
    static final int WALLPAPERS = 6;
    static int wallpaper = 0;
    static boolean sound = true, nearby = true;
    static final List<String> order = new ArrayList<>();
    static Path fileOverride;   // tests only

    static { load(); }

    private static Path file() { return fileOverride != null ? fileOverride : FabricLoader.getInstance().getConfigDir().resolve("phonemod.txt"); }

    static void load() {
        List<String> saved = new ArrayList<>();
        try {
            List<String> ls = Files.readAllLines(file());
            wallpaper = Math.floorMod(Integer.parseInt(ls.get(0).trim()), WALLPAPERS);
            if (ls.size() > 1) for (String s : ls.get(1).split(",")) if (!s.isBlank()) saved.add(s.trim());
            if (ls.size() > 2) sound = !ls.get(2).trim().equals("0");
            if (ls.size() > 3) nearby = !ls.get(3).trim().equals("0");
        } catch (Exception ignored) { }
        List<String> valid = Arrays.asList(Games.DEFAULT_ORDER);
        order.clear();
        for (String s : saved) if (valid.contains(s) && !order.contains(s)) order.add(s);
        for (String s : valid) if (!order.contains(s)) order.add(s);
    }

    static void save() {
        try { Files.writeString(file(), wallpaper + "\n" + String.join(",", order) + "\n" + (sound ? 1 : 0) + "\n" + (nearby ? 1 : 0)); }
        catch (Exception ignored) { }
    }

    static void resetOrder() { order.clear(); order.addAll(Arrays.asList(Games.DEFAULT_ORDER)); save(); }
}
