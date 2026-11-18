package com.phonemod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** A small pixel-art picture (what the phone camera takes). 64 colours, so one text character per pixel. */
final class Photo {
    static final int W = 32, H = 40;
    final int[] px = new int[W * H];   // 0xRRGGBB, already reduced to 4 levels per channel

    void quantize() { for (int i = 0; i < px.length; i++) px[i] = expand(code(px[i])); }

    static int code(int rgb) { return (((rgb >> 22) & 3) << 4) | (((rgb >> 14) & 3) << 2) | ((rgb >> 6) & 3); }
    static int expand(int c) { return (((c >> 4) & 3) * 85 << 16) | (((c >> 2) & 3) * 85 << 8) | ((c & 3) * 85); }

    String encode() {
        StringBuilder sb = new StringBuilder(px.length);
        for (int c : px) sb.append(MusicEngine.B64.charAt(code(c)));
        return sb.toString();
    }

    static Photo decode(String s) {
        if (s == null || s.length() != W * H) return null;
        Photo p = new Photo();
        for (int i = 0; i < s.length(); i++) { int c = MusicEngine.B64.indexOf(s.charAt(i)); if (c < 0) return null; p.px[i] = expand(c); }
        return p;
    }

    /** a real picture shrunk to the postage-stamp size we can send over chat (letterboxed, 4 levels per colour) */
    static Photo fromImage(BufferedImage im) {
        Photo p = new Photo();
        int iw = im.getWidth(), ih = im.getHeight();
        int th = Math.max(1, Math.min(H, Math.round(W * (float) ih / iw))), top = (H - th) / 2;
        Pic.Px px = new Pic.Px(im);
        for (int y = 0; y < th; y++) for (int x = 0; x < W; x++) p.px[(top + y) * W + x] = px.area(x * iw / W, y * ih / th, Math.max(1, iw / W), Math.max(1, ih / th));
        p.quantize();
        return p;
    }

    Photo copy() { Photo p = new Photo(); System.arraycopy(px, 0, p.px, 0, px.length); return p; }

    BufferedImage toImage(int scale) {
        BufferedImage im = new BufferedImage(W * scale, H * scale, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H * scale; y++) for (int x = 0; x < W * scale; x++) im.setRGB(x, y, px[(y / scale) * W + x / scale]);
        return im;
    }

    /** draw at display position (x0,y0), each pixel s x s; runs of one colour become a single box */
    void draw(GuiGraphicsExtractor g, int x0, int y0, int s) {
        for (int y = 0; y < H; y++) {
            int x = 0;
            while (x < W) {
                int c = px[y * W + x], x1 = x + 1;
                while (x1 < W && px[y * W + x1] == c) x1++;
                Game.box(g, x0 + x * s, y0 + y * s, x0 + x1 * s, y0 + (y + 1) * s, 0xFF000000 | c);
                x = x1;
            }
        }
    }
}

/** A small real picture (a thumbnail of a screenshot or a frame of a recording). */
final class Pic {
    final int w, h;
    final int[] rgb;
    Pic(int w, int h) { this.w = w; this.h = h; this.rgb = new int[w * h]; }

    static final class Px {
        final int[] d; final int w, h;
        Px(BufferedImage im) { w = im.getWidth(); h = im.getHeight(); d = im.getRGB(0, 0, w, h, null, 0, w); }
        int area(int x0, int y0, int bw, int bh) {
            long r = 0, g = 0, b = 0; int n = 0;
            for (int y = y0; y < Math.min(h, y0 + bh); y++) for (int x = x0; x < Math.min(w, x0 + bw); x++) { int v = d[y * w + x]; r += (v >> 16) & 255; g += (v >> 8) & 255; b += v & 255; n++; }
            if (n == 0) return 0;
            return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
        }
    }

    static Pic of(BufferedImage im, int tw, int th) {
        Pic p = new Pic(tw, th);
        Px px = new Px(im);
        int bw = Math.max(1, px.w / tw), bh = Math.max(1, px.h / th);
        for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) p.rgb[y * tw + x] = px.area(x * px.w / tw, y * px.h / th, bw, bh);
        return p;
    }

    void draw(GuiGraphicsExtractor g, int x0, int y0, int s) {
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                int c = rgb[y * w + x], x1 = x + 1;
                while (x1 < w && rgb[y * w + x1] == c) x1++;
                Game.box(g, x0 + x * s, y0 + y * s, x0 + x1 * s, y0 + (y + 1) * s, 0xFF000000 | c);
                x = x1;
            }
        }
    }
}

/** Renders the world around you as a low-res voxel picture (no screenshot API needed, works while the phone is open). */
final class VoxCam {
    interface World {
        boolean solidAt(int x, int y, int z);
        int colorAt(int x, int y, int z);
        boolean night();
        List<double[]> others();   // feet positions of other players
    }

    static final double MAX = 64;
    static final double[][] BODY = {{0.45, 0.42, 0x3F51B5}, {1.05, 0.45, 0x2A9D9D}, {1.62, 0.34, 0xC68A5E}};   // y offset, radius, colour

    static Photo render(World w, double ex, double ey, double ez, float yawDeg, float pitchDeg) {
        Photo ph = new Photo();
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -fz, rz = fx, rl = Math.hypot(rx, rz);
        if (rl < 1e-6) { rx = 1; rz = 0; rl = 1; }
        rx /= rl; rz /= rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double tanV = Math.tan(Math.toRadians(35)), tanH = tanV * Photo.W / Photo.H;
        List<double[]> others = w.others();
        boolean night = w.night();
        for (int py = 0; py < Photo.H; py++) {
            for (int px = 0; px < Photo.W; px++) {
                double sx = ((px + 0.5) / Photo.W * 2 - 1) * tanH, sy = (1 - (py + 0.5) / Photo.H * 2) * tanV;
                double dx = fx + rx * sx + ux * sy, dy = fy + uy * sy, dz = fz + rz * sx + uz * sy;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                ph.px[py * Photo.W + px] = trace(w, ex, ey, ez, dx / len, dy / len, dz / len, others, night);
            }
        }
        ph.quantize();
        return ph;
    }

    /** a picture of the player from the front (camera floats ~2.6 blocks ahead of them, looking back) */
    static Photo selfie(World w, double ex, double ey, double ez, float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double d = 2.6;
        for (double t = 0.3; t <= 2.6; t += 0.25) {
            if (w.solidAt((int) Math.floor(ex + fx * t), (int) Math.floor(ey + fy * t), (int) Math.floor(ez + fz * t))) { d = Math.max(0.7, t - 0.3); break; }
        }
        return render(w, ex + fx * d, ey + fy * d, ez + fz * d, yawDeg + 180f, -pitchDeg);
    }

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int sky(double dy, boolean night) {
        double t = Math.pow(Math.max(0, dy), 0.6);
        return night ? mix(0x1B2150, 0x050818, t) : mix(0xBFD9FF, 0x5F9BFF, t);
    }

    private static int trace(World w, double ex, double ey, double ez, double dx, double dy, double dz, List<double[]> others, boolean night) {
        double tEnt = Double.MAX_VALUE; int entCol = 0;
        for (double[] o : others) for (double[] b : BODY) {
            double cx = o[0], cy = o[1] + b[0], cz = o[2];
            double ox = ex - cx, oy = ey - cy, oz = ez - cz, bb = ox * dx + oy * dy + oz * dz, cc = ox * ox + oy * oy + oz * oz - b[1] * b[1], disc = bb * bb - cc;
            if (disc >= 0) { double t = -bb - Math.sqrt(disc); if (t > 0.1 && t < tEnt) { tEnt = t; entCol = (int) b[2]; } }
        }
        int x = (int) Math.floor(ex), y = (int) Math.floor(ey), z = (int) Math.floor(ez);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double inf = Double.MAX_VALUE;
        double tdx = dx == 0 ? inf : Math.abs(1 / dx), tdy = dy == 0 ? inf : Math.abs(1 / dy), tdz = dz == 0 ? inf : Math.abs(1 / dz);
        double tmx = dx == 0 ? inf : (dx > 0 ? x + 1 - ex : ex - x) * tdx;
        double tmy = dy == 0 ? inf : (dy > 0 ? y + 1 - ey : ey - y) * tdy;
        double tmz = dz == 0 ? inf : (dz > 0 ? z + 1 - ez : ez - z) * tdz;
        int horizon = sky(0, night);
        for (int i = 0; i < 140; i++) {
            int axis; double t;
            if (tmx <= tmy && tmx <= tmz) { axis = 0; t = tmx; x += stepX; tmx += tdx; }
            else if (tmy <= tmz) { axis = 1; t = tmy; y += stepY; tmy += tdy; }
            else { axis = 2; t = tmz; z += stepZ; tmz += tdz; }
            if (t > MAX) break;
            if (tEnt < t) return entCol;
            if (w.solidAt(x, y, z)) {
                double shade = axis == 1 ? (stepY < 0 ? 1.0 : 0.5) : axis == 0 ? 0.78 : 0.62;
                if (night) shade *= 0.45;
                int c = w.colorAt(x, y, z);
                int shaded = (((int) (((c >> 16) & 255) * shade)) << 16) | (((int) (((c >> 8) & 255) * shade)) << 8) | (int) ((c & 255) * shade);
                return mix(shaded, horizon, Math.pow(t / MAX, 1.6));
            }
        }
        return tEnt < MAX ? entCol : sky(dy, night);
    }
}

/** VoxCam.World backed by the real Minecraft world, seen from any player we can see. */
final class McWorld implements VoxCam.World {
    private final Minecraft mc = Minecraft.getInstance();
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
    private final net.minecraft.client.player.AbstractClientPlayer who;
    private final boolean showWho;

    private McWorld(net.minecraft.client.player.AbstractClientPlayer who, boolean showWho) { this.who = who; this.showWho = showWho; }

    /** your own point of view; showSelf = also draw your own body (for front-camera selfies) */
    static McWorld forSelf(boolean showSelf) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null ? null : new McWorld(mc.player, showSelf);
    }
    static McWorld current() { return forSelf(false); }

    /** another player's point of view, only possible while they are loaded on your screen */
    static McWorld forPlayer(String name, boolean showThem) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (var p : mc.level.players()) if (p.getGameProfile().name().equals(name)) return new McWorld(p, showThem);
        return null;
    }

    @Override public boolean solidAt(int x, int y, int z) { return !mc.level.getBlockState(mp.set(x, y, z)).isAir(); }
    @Override public int colorAt(int x, int y, int z) {
        int c = mc.level.getBlockState(mp.set(x, y, z)).getMapColor(mc.level, mp).col;
        return c == 0 ? 0x808080 : c;
    }
    @Override public boolean night() { return false; }   // 26.2 has no getDayTime(); pictures are always daytime
    @Override public List<double[]> others() {
        List<double[]> out = new ArrayList<>();
        for (var p : mc.level.players()) if (p != who || showWho) out.add(new double[]{p.getX(), p.getY(), p.getZ()});
        return out;
    }
    double eyeX() { return who.getX(); }
    double eyeY() { return who.getEyeY(); }
    double eyeZ() { return who.getZ(); }
    float yaw() { return who.getYRot(); }
    float pitch() { return who.getXRot(); }
}

/** Writes an animated GIF with the JDK's built-in ImageIO. */
final class GifWriter {
    static void write(Path file, List<BufferedImage> frames, int delayMs) throws Exception {
        javax.imageio.ImageWriter w = javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
        javax.imageio.ImageWriteParam p = w.getDefaultWriteParam();
        javax.imageio.ImageTypeSpecifier t = javax.imageio.ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
        javax.imageio.metadata.IIOMetadata meta = w.getDefaultImageMetadata(t, p);
        String fmt = meta.getNativeMetadataFormatName();
        javax.imageio.metadata.IIOMetadataNode root = (javax.imageio.metadata.IIOMetadataNode) meta.getAsTree(fmt);
        javax.imageio.metadata.IIOMetadataNode gce = node(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none"); gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE"); gce.setAttribute("delayTime", String.valueOf(Math.max(1, delayMs / 10)));
        gce.setAttribute("transparentColorIndex", "0");
        javax.imageio.metadata.IIOMetadataNode apps = node(root, "ApplicationExtensions");
        javax.imageio.metadata.IIOMetadataNode app = new javax.imageio.metadata.IIOMetadataNode("ApplicationExtension");
        app.setAttribute("applicationID", "NETSCAPE"); app.setAttribute("authenticationCode", "2.0");
        app.setUserObject(new byte[]{1, 0, 0});
        apps.appendChild(app);
        meta.setFromTree(fmt, root);
        try (javax.imageio.stream.ImageOutputStream out = javax.imageio.ImageIO.createImageOutputStream(file.toFile())) {
            w.setOutput(out);
            w.prepareWriteSequence(null);
            for (BufferedImage f : frames) w.writeToSequence(new javax.imageio.IIOImage(f, null, meta), p);
            w.endWriteSequence();
        } finally { w.dispose(); }
    }

    private static javax.imageio.metadata.IIOMetadataNode node(javax.imageio.metadata.IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) if (root.item(i).getNodeName().equalsIgnoreCase(name)) return (javax.imageio.metadata.IIOMetadataNode) root.item(i);
        javax.imageio.metadata.IIOMetadataNode n = new javax.imageio.metadata.IIOMetadataNode(name);
        root.appendChild(n);
        return n;
    }
}

/**
 * Camera app: the phone window is see-through, so you look at the real game. Shutter takes a real screenshot,
 * REC records your screen to a GIF. Press Esc to dock the phone and walk around (K = shutter, J = flip).
 */
final class CameraApp extends Game {
    private enum Mode { VIEW, GALLERY, DETAIL, PICK }
    static final class Item { Pic thumb, preview; List<Pic> clip; Photo small; String file; boolean isClip; }
    static final List<Item> items = new ArrayList<>();
    static Path dirOverride, shotsOverride;   // tests only
    static String status = "";
    static long statusUntil;

    private Mode mode = Mode.VIEW;
    private Item sel;

    CameraApp() { super("camera", "Camera", "CAM", 0xFF37474F); Capture.onLimit = CameraApp::stopRec; }

    @Override void reset() { mode = Mode.VIEW; sel = null; }   // opening the app always starts at the viewfinder

    static CameraApp get() { return (CameraApp) Games.byId("camera"); }
    static Path dir() { return dirOverride != null ? dirOverride : FabricLoader.getInstance().getGameDir().resolve("phonemod"); }
    static Path shots() { return shotsOverride != null ? shotsOverride : FabricLoader.getInstance().getGameDir().resolve("screenshots"); }

    @Override boolean transparent() { return mode == Mode.VIEW; }
    @Override boolean dockable() { return mode == Mode.VIEW; }

    @Override void icon(GuiGraphicsExtractor g, int x, int y) {
        box(g, x, y, x + 30, y + 30, 0xFF37474F);
        box(g, x + 4, y + 9, x + 26, y + 25, 0xFF212121); box(g, x + 9, y + 6, x + 17, y + 9, 0xFF212121);
        box(g, x + 10, y + 12, x + 20, y + 22, 0xFF90A4AE); box(g, x + 12, y + 14, x + 18, y + 20, 0xFF1565C0);
        box(g, x + 22, y + 11, x + 24, y + 13, 0xFFE53935);
    }

    static void say(String s) { status = s; statusUntil = System.currentTimeMillis() + 4000; }
    private static String stamp() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")); }
    private static void addItem(Item it) { items.add(0, it); while (items.size() > 12) items.remove(items.size() - 1); }

    // ---------- actions (also used by the dock keys) ----------
    static void shutter() {
        if (Capture.recording) { say("Stop recording first"); return; }
        if (!Capture.screenshot(full -> {
            if (full == null) { say("Screenshot not available on this version"); return; }
            Item it = new Item();
            it.thumb = Pic.of(full, 32, 18); it.preview = Pic.of(full, 48, 27); it.small = Photo.fromImage(full);
            String name = "phone_" + stamp() + ".png";
            it.file = name;
            Capture.io.execute(() -> {
                try { Files.createDirectories(shots()); javax.imageio.ImageIO.write(full, "png", shots().resolve(name).toFile()); }
                catch (Throwable t) { say("Couldn't write " + name); }
            });
            addItem(it);
            say("Saved screenshots/" + name);
        })) return;
        Sfx.share('b');
    }

    static void startRec() {
        if (Capture.recording) return;
        Capture.startRecording();
        Sfx.share('c');
        say("Recording... Esc to dock, K to stop");
    }

    static void stopRec() {
        if (!Capture.recording) return;
        List<BufferedImage> fr = Capture.stopRecording();
        if (fr.size() < 2) { say("Clip too short"); return; }
        Item it = new Item(); it.isClip = true; it.clip = new ArrayList<>();
        for (BufferedImage b : fr) it.clip.add(Pic.of(b, 48, 27));
        it.thumb = Pic.of(fr.get(0), 32, 18); it.preview = it.clip.get(0); it.small = Photo.fromImage(fr.get(0));
        String name = "clip_" + stamp() + ".gif";
        it.file = name;
        Capture.io.execute(() -> {
            try { Path d = dir().resolve("clips"); Files.createDirectories(d); GifWriter.write(d.resolve(name), fr, 350); }
            catch (Throwable t) { say("Couldn't write GIF " + name); }
        });
        addItem(it);
        say("Saved clip " + name);
        Sfx.play('w');
    }

    static void flip() { Capture.flipView(); say(Capture.backend.thirdPersonFront() ? "Front camera (selfie)" : "Back camera"); Sfx.play('c'); }

    // ---------- input ----------
    @Override void press(float x, float y) {
        switch (mode) {
            case VIEW -> {
                if (in(x, y, 4, 150, 28, 176)) flip();
                else if (in(x, y, 32, 150, 56, 176)) { if (Capture.recording) stopRec(); else startRec(); }
                else if (in(x, y, 60, 148, 88, 176)) shutter();
                else if (in(x, y, 92, 150, 114, 176)) { if (Capture.recording) say("Stop recording first"); else { mode = Mode.GALLERY; Sfx.play('c'); } }
            }
            case GALLERY -> {
                if (in(x, y, 4, 4, 18, 18)) { mode = Mode.VIEW; return; }
                for (int i = 0; i < items.size(); i++) {
                    int tx = 4 + (i % 3) * 38, ty = 26 + (i / 3) * 30;
                    if (in(x, y, tx, ty, tx + 32, ty + 18)) { sel = items.get(i); mode = Mode.DETAIL; Sfx.play('c'); return; }
                }
            }
            case DETAIL -> {
                if (in(x, y, 4, 4, 18, 18)) { mode = Mode.GALLERY; return; }
                if (in(x, y, 4, 152, 58, 168)) {
                    if (sel.isClip) say("Clips stay on your computer (too big to send)");
                    else if (PhoneNet.contacts.isEmpty()) say("No friends yet - add one in Messages");
                    else mode = Mode.PICK;
                } else if (in(x, y, 62, 152, 114, 168)) { items.remove(sel); sel = null; mode = Mode.GALLERY; Sfx.play('c'); }
            }
            case PICK -> {
                if (in(x, y, 4, 4, 18, 18)) { mode = Mode.DETAIL; return; }
                int i = 0;
                for (String n : PhoneNet.contacts) {
                    if (i >= 8) break;
                    if (in(x, y, 4, 26 + i * 22, 114, 44 + i * 22)) {
                        PhoneNet.sendPhoto(n, sel.small);
                        say("Sending photo to " + n + "...");
                        mode = Mode.DETAIL; Sfx.play('w');
                        return;
                    }
                    i++;
                }
            }
        }
    }

    // ---------- drawing ----------
    private void back(GuiGraphicsExtractor g) { box(g, 4, 4, 18, 18, 0xFF333847); txt(g, "<", 9, 6, WHITE); }

    @Override void draw(GuiGraphicsExtractor g) {
        if (mode != Mode.VIEW) box(g, 0, 0, W, H, 0xFF101114);
        switch (mode) {
            case VIEW -> {
                box(g, 0, 0, W, 16, 0xAA000000);
                txt(g, "Camera", 5, 4, WHITE);
                if (Capture.recording) {
                    if (((int) (Capture.recSeconds() * 2) & 1) == 0) box(g, 62, 5, 68, 11, 0xFFE53935);
                    left(g, String.format("REC %.1fs", Capture.recSeconds()), 72, 5, 0xFFE53935, 0.85f);
                }
                int c = 0xCCFFFFFF;   // viewfinder corners
                for (int[] p : new int[][]{{8, 22, 1, 1}, {110, 22, -1, 1}, {8, 140, 1, -1}, {110, 140, -1, -1}}) {
                    box(g, Math.min(p[0], p[0] + 14 * p[2]), p[1], Math.max(p[0], p[0] + 14 * p[2]), p[1] + 1, c);
                    box(g, p[0], Math.min(p[1], p[1] + 14 * p[3]), p[0] + 1, Math.max(p[1], p[1] + 14 * p[3]), c);
                }
                box(g, 0, 144, W, H, 0xCC101114);
                box(g, 4, 150, 28, 176, 0xFF2A2F3A); ctr(g, Capture.backend.thirdPersonFront() ? "Front" : "Back", 16, 160, WHITE, 0.7f);
                box(g, 32, 150, 56, 176, Capture.recording ? 0xFF7A1F1F : 0xFF2A2F3A);
                box(g, 40, 157, 48, 169, Capture.recording ? 0xFFFF5252 : 0xFFE53935);
                box(g, 60, 148, 88, 176, 0xFFEEEEEE); box(g, 63, 151, 85, 173, 0xFF101114); box(g, 65, 153, 83, 171, 0xFFFFFFFF);
                box(g, 92, 150, 114, 176, 0xFF2A2F3A);
                if (!items.isEmpty()) items.get(0).thumb.draw(g, 94, 158, 1);
                ctr(g, "Flip", 16, 180, GRAY, 0.6f); ctr(g, Capture.recording ? "Stop" : "Rec", 44, 180, GRAY, 0.6f);
                ctr(g, "Shoot", 74, 180, GRAY, 0.6f); ctr(g, "Gallery", 103, 180, GRAY, 0.6f);
                boolean fresh = System.currentTimeMillis() < statusUntil;
                ctr(g, fresh ? (status.length() > 34 ? status.substring(0, 34) : status) : "Esc docks the phone: K shoot, J flip", W / 2, 194, fresh ? 0xFF8FD6DD : 0xFF9AA0AE, 0.7f);
            }
            case GALLERY -> {
                back(g); txt(g, "Gallery", 24, 7, WHITE);
                if (items.isEmpty()) ctr(g, "Nothing here yet", W / 2, 90, GRAY, 0.9f);
                for (int i = 0; i < items.size(); i++) {
                    int tx = 4 + (i % 3) * 38, ty = 26 + (i / 3) * 30;
                    items.get(i).thumb.draw(g, tx, ty, 1);
                    if (items.get(i).isClip) { box(g, tx + 1, ty + 1, tx + 15, ty + 8, 0xAA000000); left(g, "GIF", tx + 3, ty + 1, WHITE, 0.6f); }
                }
            }
            case DETAIL -> {
                back(g);
                txt(g, sel.isClip ? "Clip" : "Photo", 24, 7, WHITE);
                Pic p = sel.preview;
                if (sel.isClip) p = sel.clip.get((int) (System.nanoTime() / 350_000_000L) % sel.clip.size());
                p.draw(g, 11, 26, 2);
                box(g, 4, 152, 58, 168, sel.isClip ? 0xFF444B58 : 0xFF2E86DE); ctr(g, "Send", 31, 156, WHITE, 1f);
                box(g, 62, 152, 114, 168, 0xFF8E2A2A); ctr(g, "Delete", 88, 156, WHITE, 1f);
                boolean fresh = System.currentTimeMillis() < statusUntil;
                String line = fresh ? status : (sel.file == null ? "" : sel.file);
                ctr(g, line.length() > 30 ? line.substring(0, 30) : line, W / 2, 176, fresh ? 0xFF8FD6DD : GRAY, 0.7f);
                ctr(g, sel.isClip ? sel.clip.size() + " frames - GIF in phonemod/clips" : "Full size in the screenshots folder", W / 2, 188, GRAY, 0.7f);
            }
            case PICK -> {
                back(g); txt(g, "Send to...", 24, 7, WHITE);
                int i = 0;
                for (String n : PhoneNet.contacts) {
                    if (i >= 8) break;
                    box(g, 4, 26 + i * 22, 114, 44 + i * 22, 0xFF222633);
                    txt(g, n, 9, 31 + i * 22, WHITE);
                    i++;
                }
                if (PhoneNet.contacts.isEmpty()) ctr(g, "No friends yet", W / 2, 90, GRAY, 0.9f);
            }
        }
    }
}
