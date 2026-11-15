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

    private static int code(int rgb) { return (((rgb >> 22) & 3) << 4) | (((rgb >> 14) & 3) << 2) | ((rgb >> 6) & 3); }
    private static int expand(int c) { return (((c >> 4) & 3) * 85 << 16) | (((c >> 2) & 3) * 85 << 8) | ((c & 3) * 85); }

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

/** VoxCam.World backed by the real Minecraft world. */
final class McWorld implements VoxCam.World {
    private final Minecraft mc = Minecraft.getInstance();
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

    static McWorld current() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null ? null : new McWorld();
    }
    @Override public boolean solidAt(int x, int y, int z) { return !mc.level.getBlockState(mp.set(x, y, z)).isAir(); }
    @Override public int colorAt(int x, int y, int z) {
        int c = mc.level.getBlockState(mp.set(x, y, z)).getMapColor(mc.level, mp).col;
        return c == 0 ? 0x808080 : c;
    }
    @Override public boolean night() { long t = mc.level.getDayTime() % 24000L; return t > 13000 && t < 23000; }
    @Override public List<double[]> others() {
        List<double[]> out = new ArrayList<>();
        for (var p : mc.level.players()) if (p != mc.player) out.add(new double[]{p.getX(), p.getY(), p.getZ()});
        return out;
    }
    double eyeX() { return mc.player.getX(); }
    double eyeY() { return mc.player.getEyeY(); }
    double eyeZ() { return mc.player.getZ(); }
    float yaw() { return mc.player.getYRot(); }
    float pitch() { return mc.player.getXRot(); }
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

/** Camera app: live viewfinder, photos, recordings (saved as GIF), gallery, send a photo to a friend. */
final class CameraApp extends Game {
    private enum Mode { VIEW, GALLERY, DETAIL, PICK }
    static final class Item { Photo photo; List<Photo> frames; String file; boolean clip; }
    static final List<Item> items = new ArrayList<>();
    static Path dirOverride;   // tests only

    private Mode mode = Mode.VIEW;
    private Photo live = pattern();
    private float acc, recTime, recAcc, flash, statusT;
    private String status = "";
    private boolean recording;
    private final List<Photo> recFrames = new ArrayList<>();
    private Item sel;

    CameraApp() { super("camera", "Camera", "CAM", 0xFF37474F); resetOnOpen = false; }

    static Path dir() { return dirOverride != null ? dirOverride : FabricLoader.getInstance().getGameDir().resolve("phonemod"); }

    @Override void icon(GuiGraphicsExtractor g, int x, int y) {
        box(g, x, y, x + 30, y + 30, 0xFF37474F);
        box(g, x + 4, y + 9, x + 26, y + 25, 0xFF212121); box(g, x + 9, y + 6, x + 17, y + 9, 0xFF212121);
        box(g, x + 10, y + 12, x + 20, y + 22, 0xFF90A4AE); box(g, x + 12, y + 14, x + 18, y + 20, 0xFF1565C0);
        box(g, x + 22, y + 11, x + 24, y + 13, 0xFFE53935);
    }

    private static Photo pattern() {
        Photo p = new Photo();
        for (int y = 0; y < Photo.H; y++) for (int x = 0; x < Photo.W; x++) p.px[y * Photo.W + x] = ((x / 4 + y / 4) & 1) == 0 ? 0x555555 : 0x333333;
        p.quantize();
        return p;
    }

    private Photo capture() {
        try {
            McWorld w = McWorld.current();
            if (w == null) return pattern();
            return VoxCam.render(w, w.eyeX(), w.eyeY(), w.eyeZ(), w.yaw(), w.pitch());
        } catch (Throwable t) { return pattern(); }
    }

    private void say(String s) { status = s; statusT = 4f; }

    @Override void update(float dt) {
        flash = Math.max(0, flash - dt * 2.5f);
        statusT -= dt;
        if (mode != Mode.VIEW) return;
        acc += dt;
        if (acc >= 0.17f) {
            acc = 0; live = capture();
            if (recording) { recAcc += 0.17f; if (recAcc >= 0.2f) { recAcc = 0; recFrames.add(live); } }
        }
        if (recording) { recTime += dt; if (recTime >= 6f || recFrames.size() >= 30) stopRec(); }
    }

    private void addItem(Item it) { items.add(0, it); while (items.size() > 12) items.remove(items.size() - 1); }
    private static String stamp() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")); }

    private void shutter() {
        Item it = new Item(); it.photo = live.copy();
        flash = 1f; Sfx.share('b');
        try {
            Path d = dir().resolve("photos"); Files.createDirectories(d);
            Path f = d.resolve("photo_" + stamp() + ".png");
            javax.imageio.ImageIO.write(it.photo.toImage(8), "png", f.toFile());
            it.file = f.getFileName().toString();
            say("Saved " + it.file);
        } catch (Throwable t) { say("Photo kept in gallery (couldn't write file)"); }
        addItem(it);
    }

    private void startRec() { recording = true; recTime = 0; recAcc = 0.2f; recFrames.clear(); Sfx.share('c'); say("Recording..."); }

    private void stopRec() {
        recording = false;
        if (recFrames.size() < 2) { say("Clip too short"); return; }
        Item it = new Item(); it.clip = true; it.frames = new ArrayList<>(recFrames); it.photo = it.frames.get(0);
        try {
            Path d = dir().resolve("clips"); Files.createDirectories(d);
            Path f = d.resolve("clip_" + stamp() + ".gif");
            List<BufferedImage> imgs = new ArrayList<>();
            for (Photo p : it.frames) imgs.add(p.toImage(6));
            GifWriter.write(f, imgs, 200);
            it.file = f.getFileName().toString();
            say("Saved " + it.file);
        } catch (Throwable t) { say("Clip kept in gallery (couldn't write GIF)"); }
        addItem(it);
        Sfx.play('w');
    }

    // ---------- input ----------
    @Override void press(float x, float y) {
        switch (mode) {
            case VIEW -> {
                if (in(x, y, 12, 150, 40, 176)) { if (recording) stopRec(); else startRec(); }
                else if (in(x, y, 46, 148, 72, 176)) { if (!recording) shutter(); else say("Stop recording first"); }
                else if (in(x, y, 78, 150, 106, 176)) { if (recording) say("Stop recording first"); else { mode = Mode.GALLERY; Sfx.play('c'); } }
            }
            case GALLERY -> {
                if (in(x, y, 4, 4, 18, 18)) { mode = Mode.VIEW; return; }
                for (int i = 0; i < items.size(); i++) {
                    int tx = 4 + (i % 3) * 38, ty = 24 + (i / 3) * 44;
                    if (in(x, y, tx, ty, tx + 32, ty + 40)) { sel = items.get(i); mode = Mode.DETAIL; Sfx.play('c'); return; }
                }
            }
            case DETAIL -> {
                if (in(x, y, 4, 4, 18, 18)) { mode = Mode.GALLERY; return; }
                if (in(x, y, 4, 152, 58, 168)) {
                    if (sel.clip) say("Clips stay on your computer (too big to send)");
                    else if (PhoneNet.contacts.isEmpty()) say("No friends yet - Scan in Messages");
                    else mode = Mode.PICK;
                } else if (in(x, y, 62, 152, 114, 168)) { items.remove(sel); sel = null; mode = Mode.GALLERY; Sfx.play('c'); }
            }
            case PICK -> {
                if (in(x, y, 4, 4, 18, 18)) { mode = Mode.DETAIL; return; }
                int i = 0;
                for (String n : PhoneNet.contacts) {
                    if (i >= 8) break;
                    if (in(x, y, 4, 26 + i * 22, 114, 44 + i * 22)) {
                        PhoneNet.sendPhoto(n, sel.photo);
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
        box(g, 0, 0, W, H, 0xFF101114);
        switch (mode) {
            case VIEW -> {
                txt(g, "Camera", 5, 5, WHITE);
                if (recording && ((int) (recTime * 2) & 1) == 0) box(g, 94, 7, 100, 13, 0xFFE53935);
                if (recording) left(g, String.format("REC %.1fs", recTime), 66, 6, 0xFFE53935, 0.8f);
                live.draw(g, 11, 20, 3);
                box(g, 10, 19, 108, 20, 0xFF777777); box(g, 10, 140, 108, 141, 0xFF777777);
                if (flash > 0) box(g, 11, 20, 107, 140, (((int) (flash * 220)) << 24) | 0xFFFFFF);
                box(g, 12, 150, 40, 176, recording ? 0xFF7A1F1F : 0xFF2A2F3A);
                box(g, 20, 156, 32, 170, recording ? 0xFFFF5252 : 0xFFE53935);
                if (recording) ctr(g, "STOP", 26, 178, WHITE, 0.6f); else ctr(g, "REC", 26, 178, GRAY, 0.6f);
                box(g, 46, 148, 72, 176, 0xFFEEEEEE); box(g, 49, 151, 69, 173, 0xFF101114); box(g, 51, 153, 67, 171, 0xFFFFFFFF);
                box(g, 78, 150, 106, 176, 0xFF2A2F3A);
                if (!items.isEmpty()) items.get(0).photo.draw(g, 85, 152, 1);
                ctr(g, "Gallery " + items.size(), 92, 178, GRAY, 0.6f);
                if (statusT > 0) ctr(g, status.length() > 30 ? status.substring(0, 30) : status, W / 2, 190, 0xFF8FD6DD, 0.7f);
                else ctr(g, "Pixel-art snapshots of what you see", W / 2, 190, 0xFF6F7585, 0.7f);
            }
            case GALLERY -> {
                back(g); txt(g, "Gallery", 24, 7, WHITE);
                if (items.isEmpty()) ctr(g, "Nothing here yet", W / 2, 90, GRAY, 0.9f);
                for (int i = 0; i < items.size(); i++) {
                    int tx = 4 + (i % 3) * 38, ty = 24 + (i / 3) * 44;
                    items.get(i).photo.draw(g, tx, ty, 1);
                    if (items.get(i).clip) { box(g, tx + 1, ty + 1, tx + 15, ty + 9, 0xAA000000); left(g, "GIF", tx + 3, ty + 2, WHITE, 0.6f); }
                }
            }
            case DETAIL -> {
                back(g);
                txt(g, sel.clip ? "Clip" : "Photo", 24, 7, WHITE);
                Photo p = sel.photo;
                if (sel.clip) p = sel.frames.get((int) (System.nanoTime() / 200_000_000L) % sel.frames.size());
                p.draw(g, 11, 24, 3);
                box(g, 4, 152, 58, 168, sel.clip ? 0xFF444B58 : 0xFF2E86DE); ctr(g, "Send", 31, 156, WHITE, 1f);
                box(g, 62, 152, 114, 168, 0xFF8E2A2A); ctr(g, "Delete", 88, 156, WHITE, 1f);
                String line = status.isEmpty() || statusT <= 0 ? (sel.file == null ? "" : sel.file) : status;
                ctr(g, line.length() > 30 ? line.substring(0, 30) : line, W / 2, 176, statusT > 0 ? 0xFF8FD6DD : GRAY, 0.7f);
                if (sel.clip) ctr(g, sel.frames.size() + " frames - GIF in phonemod/clips", W / 2, 188, GRAY, 0.7f);
                else ctr(g, "Saved in phonemod/photos", W / 2, 188, GRAY, 0.7f);
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
