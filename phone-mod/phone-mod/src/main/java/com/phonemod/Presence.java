package com.phonemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** Tells nearby players with the mod which app you have open, so they can see your phone in your hand. */
final class Presence {
    static final class Entry { int code; long seen; }
    static final Map<String, Entry> others = new HashMap<>();
    static final Set<String> told = new HashSet<>();
    static LongSupplier clock = System::currentTimeMillis;
    static int lastCode = -1;
    static long lastSent;
    static final int HOME = 60;

    static int codeOf(String id) {
        if (id == null) return -1;
        if (id.equals("home")) return HOME;
        for (int i = 0; i < Games.DEFAULT_ORDER.length; i++) if (Games.DEFAULT_ORDER[i].equals(id)) return i;
        return HOME;
    }
    static String idOf(int code) {
        if (code == HOME) return "home";
        return code >= 0 && code < Games.DEFAULT_ORDER.length ? Games.DEFAULT_ORDER[code] : "home";
    }

    static void onRemote(String from, String body) {
        if (body.isEmpty()) return;
        if (body.charAt(0) == 'X') { others.remove(from); return; }
        int c = MusicEngine.B64.indexOf(body.charAt(0));
        if (c < 0) return;
        Entry e = others.computeIfAbsent(from, k -> new Entry());
        e.code = c; e.seen = clock.getAsLong();
    }

    /** every client tick: tell nearby mod users when my phone comes out, changes app, or goes away */
    static void tick() {
        long now = clock.getAsLong();
        others.values().removeIf(e -> now - e.seen > 45000);
        int code = Settings.nearby ? codeOf(PhoneScreen.presenceId()) : -1;
        List<Sfx.Near> near = Sfx.nearby(48, 6);
        Set<String> names = new HashSet<>();
        for (Sfx.Near n : near) names.add(n.name());
        if (PhoneNet.queueSize() < 8) {
            if (code != lastCode) {
                Set<String> to = new HashSet<>(told); to.addAll(names);
                for (String n : to) send(n, code);
                lastCode = code; lastSent = now; told.clear(); told.addAll(names);
            } else if (code >= 0) {
                boolean keep = now - lastSent > 20000;
                for (String n : names) if (keep || !told.contains(n)) send(n, code);
                if (keep) lastSent = now;
                told.addAll(names);
            }
        }
        told.retainAll(names.isEmpty() && code < 0 ? new HashSet<>() : names);
    }

    private static void send(String to, int code) {
        PhoneNet.enqueueLazy(() -> PhoneNet.cmd(to, 'U', code < 0 ? "X" : String.valueOf(MusicEngine.B64.charAt(code))));
    }
}

/** Screen position of a point in the world, for drawing 2D things on the HUD. */
final class Projector {
    /** @return {screenX, screenY, depth} or null when the point is behind the camera */
    static double[] project(double ex, double ey, double ez, float yawDeg, float pitchDeg, double fovDeg, int w, int h, double px, double py, double pz) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -fz, rz = fx, rl = Math.hypot(rx, rz);
        if (rl < 1e-6) return null;
        rx /= rl; rz /= rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double dx = px - ex, dy = py - ey, dz = pz - ez;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.3) return null;
        double tanV = Math.tan(Math.toRadians(fovDeg) / 2), tanH = tanV * w / h;
        double cx = dx * rx + dz * rz, cy = dx * ux + dy * uy + dz * uz;
        return new double[]{w / 2.0 + (cx / depth) / tanH * (w / 2.0), h / 2.0 - (cy / depth) / tanV * (h / 2.0), depth};
    }
}

/** Draws a flat phone at the hand of every nearby player who has their phone out. */
final class HandPhones {
    static void render(GuiGraphicsExtractor g) {
        if (Presence.others.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double fov = 70;
        try { fov = mc.options.fov().get(); } catch (Throwable ignored) { }
        double ex = mc.player.getX(), ey = mc.player.getEyeY(), ez = mc.player.getZ();
        for (var e : Presence.others.entrySet()) {
            Sfx.Near n = Sfx.find(e.getKey());
            if (n == null || n.dist() > 40) continue;
            draw(g, n, e.getValue().code, ex, ey, ez, mc.player.getYRot(), mc.player.getXRot(), fov, w, h);
        }
    }

    static void draw(GuiGraphicsExtractor g, Sfx.Near n, int code, double ex, double ey, double ez, float yaw, float pitch, double fov, int w, int h) {
        double a = Math.toRadians(n.yaw());
        double hx = n.x() + (-Math.cos(a)) * 0.42 + (-Math.sin(a)) * 0.38, hz = n.z() + (-Math.sin(a)) * 0.42 + Math.cos(a) * 0.38, hy = n.y() + 1.05;
        double[] p = Projector.project(ex, ey, ez, yaw, pitch, fov, w, h, hx, hy, hz);
        if (p == null) return;
        double tanV = Math.tan(Math.toRadians(fov) / 2);
        int ph = (int) Math.max(6, Math.min(90, 0.34 / p[2] / tanV * (h / 2.0))), pw = Math.max(4, ph / 2);
        int x0 = (int) p[0] - pw / 2, y0 = (int) p[1] - ph / 2;
        String id = Presence.idOf(code);
        Game gm = Games.byId(id);
        int screen = gm != null ? gm.color : id.equals("messages") ? 0xFF2E86DE : id.equals("settings") ? 0xFF5A5F6B : 0xFF3A2A6A;
        g.fill(x0 - 1, y0 - 1, x0 + pw + 1, y0 + ph + 1, 0xFF0A0A0C);
        g.fill(x0, y0, x0 + pw, y0 + ph, 0xFF000000);
        g.fill(x0 + 1, y0 + 2, x0 + pw - 1, y0 + ph - 1, 0xFF000000 | screen);
        if (ph >= 22) {
            String label = gm != null ? gm.label : id.equals("messages") ? "Messages" : id.equals("settings") ? "Settings" : "Phone";
            var font = Minecraft.getInstance().font;
            g.text(font, label, x0 + pw / 2 - font.width(label) / 2, y0 + ph + 3, 0xFFFFFFFF);
            if (gm != null && ph >= 30) g.text(font, gm.abbr, x0 + pw / 2 - font.width(gm.abbr) / 2, y0 + ph / 2 - 3, 0xFFFFFFFF);
        }
    }
}
