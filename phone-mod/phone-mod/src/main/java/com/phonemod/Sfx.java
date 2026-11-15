package com.phonemod;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * All phone sounds. Everything that touches Minecraft's sound / world API is in this file
 * (McSoundWorld + mcPlay), so it is the only place to fix if a name changes.
 * Nearby sharing: key moments (phone open, game over, win, record, shutter) and music are whispered to
 * nearby players who have the mod, and played at YOUR position on their side.
 */
final class Sfx {
    interface Sink { void play(String sound, float pitch, float vol, double x, double y, double z); }
    record Near(String name, double x, double y, double z, double dist) { }
    interface World { double[] self(); List<Near> players(); }
    record Snd(String name, float pitch, float vol) { }

    static Sink sink = Sfx::mcPlay;
    static World world = new McSoundWorld();
    static final String SHARED = "owxlbe";
    private static long lastShare, lastPickup;
    private static final java.util.Map<String, Long> lastRemote = new java.util.HashMap<>();

    static Snd snd(char id) {
        return switch (id) {
            case 'c' -> new Snd("ui.button.click", 1.0f, 0.7f);
            case 'k' -> new Snd("block.wood.place", 1.3f, 0.7f);
            case 'p' -> new Snd("entity.experience_orb.pickup", 1.0f, 0.5f);
            case 'l' -> new Snd("entity.player.levelup", 1.0f, 0.6f);
            case 'w' -> new Snd("ui.toast.challenge_complete", 1.0f, 0.7f);
            case 'x' -> new Snd("entity.villager.no", 1.0f, 0.7f);
            case 'e' -> new Snd("entity.generic.explode", 1.0f, 0.5f);
            case 's' -> new Snd("entity.arrow.shoot", 1.3f, 0.5f);
            case 'j' -> new Snd("entity.rabbit.jump", 1.0f, 0.6f);
            case 'o' -> new Snd("entity.item.pickup", 1.6f, 0.7f);
            case 'b' -> new Snd("block.stone_button.click_on", 1.9f, 0.9f);
            default -> null;
        };
    }

    /** play for yourself only */
    static void play(char id) {
        if (!Settings.sound) return;
        Snd s = snd(id);
        double[] p = world.self();
        if (s == null || p == null) return;
        if (id == 'p') { long now = System.currentTimeMillis(); if (now - lastPickup < 80) return; lastPickup = now; }
        sink.play(s.name, s.pitch, s.vol, p[0], p[1], p[2]);
    }

    /** play for yourself, and for nearby players who have the mod (at most one share every 3 seconds) */
    static void share(char id) {
        play(id);
        if (!Settings.sound || !Settings.nearby || SHARED.indexOf(id) < 0) return;
        long now = System.currentTimeMillis();
        if (now - lastShare < 3000 || PhoneNet.queueSize() > 6) return;
        List<Near> near = nearby(16, 3);
        if (near.isEmpty()) return;
        lastShare = now;
        for (Near n : near) PhoneNet.enqueueLazy(() -> PhoneNet.cmd(n.name(), 'F', String.valueOf(id)));
    }

    /** a sound another player shared: played at THEIR position so it fades with distance */
    static void remote(String from, char id) {
        if (!Settings.sound || !Settings.nearby) return;
        Snd s = snd(id);
        if (s == null || SHARED.indexOf(id) < 0) return;
        long now = System.currentTimeMillis();
        Long last = lastRemote.get(from);
        if (last != null && now - last < 1500) return;
        Near n = find(from);
        if (n == null || n.dist() > 24) return;
        lastRemote.put(from, now);
        sink.play(s.name, s.pitch, Math.min(1f, s.vol + 0.2f), n.x(), n.y(), n.z());
    }

    static void note(String instrument, int note, float vol, double[] pos) {
        if (!Settings.sound || pos == null) return;
        sink.play("block.note_block." + instrument, (float) Math.pow(2.0, (note - 12) / 12.0), vol, pos[0], pos[1], pos[2]);
    }

    static Near find(String name) {
        for (Near n : world.players()) if (n.name().equals(name)) return n;
        return null;
    }

    /** nearby players who are known to have the mod, closest first */
    static List<Near> nearby(double maxDist, int max) {
        List<Near> out = new ArrayList<>();
        for (Near n : world.players()) if (n.dist() <= maxDist && PhoneNet.contacts.contains(n.name())) out.add(n);
        out.sort(Comparator.comparingDouble(Near::dist));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    // ---------- Minecraft side ----------
    static final class McSoundWorld implements World {
        @Override public double[] self() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player == null ? null : new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()};
        }
        @Override public List<Near> players() {
            List<Near> out = new ArrayList<>();
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return out;
            for (var p : mc.level.players()) {
                if (p == mc.player) continue;
                out.add(new Near(p.getGameProfile().name(), p.getX(), p.getY(), p.getZ(), p.distanceTo(mc.player)));
            }
            return out;
        }
    }

    private static void mcPlay(String sound, float pitch, float vol, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        SoundEvent ev = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", sound));
        mc.level.playLocalSound(x, y, z, ev, SoundSource.PLAYERS, vol, pitch, false);
    }
}
