package com.phonemod;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Real screenshots and screen recordings of what is on your screen (not the pixel-art camera).
 * Uses Minecraft's own Screenshot class through reflection, so the rest of the mod still compiles if a
 * name changes; everything that touches the game is in {@link McBackend}.
 */
final class Capture {
    interface Backend {
        void grab(Consumer<Object> callback);   // asks the game for its current frame; callback gets a NativeImage-like object
        boolean hideGui();
        void setHideGui(boolean hide);
        boolean thirdPersonFront();
        void setThirdPersonFront(boolean front);
    }

    static Backend backend = new McBackend();
    static boolean recording, hideUi;
    static final int MAX_FRAMES = 30, EVERY_TICKS = 7, W = 320, H = 180;
    static Runnable onLimit = () -> { };
    private static final List<BufferedImage> frames = new ArrayList<>();
    private static int ticks, inFlight, stage;
    private static boolean prevHide;
    private static Consumer<BufferedImage> shotDone;
    private static long recStart;
    static final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "phonemod-io"); t.setDaemon(true); return t; });

    // ---------- screenshot: hide the phone (and the game's HUD) for one frame, then grab ----------
    static boolean screenshot(Consumer<BufferedImage> done) {
        if (shotDone != null) return false;
        shotDone = done; stage = 0;
        return true;
    }

    static void startRecording() { frames.clear(); ticks = 0; inFlight = 0; recording = true; recStart = System.currentTimeMillis(); }

    static List<BufferedImage> stopRecording() {
        recording = false;
        List<BufferedImage> out = new ArrayList<>(frames);
        frames.clear();
        return out;
    }

    static float recSeconds() { return (System.currentTimeMillis() - recStart) / 1000f; }

    /** every client tick (render thread) */
    static void tick() {
        if (shotDone != null) {
            if (stage == 0) { hideUi = true; prevHide = backend.hideGui(); backend.setHideGui(true); stage = 1; }
            else if (stage == 1) {
                stage = 2;
                backend.grab(img -> {
                    Consumer<BufferedImage> cb = shotDone;
                    shotDone = null; stage = 0;
                    try { BufferedImage full = toImage(img, 0, 0); if (cb != null) cb.accept(full); }
                    catch (Throwable t) { if (cb != null) cb.accept(null); }
                    finally { close(img); }
                });
                backend.setHideGui(prevHide);
                hideUi = false;
            }
        }
        if (recording) {
            ticks++;
            if (ticks % EVERY_TICKS == 0 && inFlight < 2 && frames.size() < MAX_FRAMES) {
                inFlight++;
                backend.grab(img -> {
                    try { if (recording) frames.add(toImage(img, W, H)); } catch (Throwable ignored) { }
                    finally { inFlight--; close(img); }
                });
            }
            if (frames.size() >= MAX_FRAMES) onLimit.run();
        }
    }

    static void flipView() { backend.setThirdPersonFront(!backend.thirdPersonFront()); }

    // ---------- turning the game's image into something Java can use ----------
    private static void close(Object img) { try { if (img instanceof AutoCloseable c) c.close(); } catch (Exception ignored) { } }

    /** @param tw,th target size (0 = same size as the game's image) */
    static BufferedImage toImage(Object img, int tw, int th) throws Exception {
        Class<?> c = img.getClass();
        int w = ((Number) c.getMethod("getWidth").invoke(img)).intValue(), h = ((Number) c.getMethod("getHeight").invoke(img)).intValue();
        int[] src = null; boolean abgr = true;
        try { src = (int[]) c.getMethod("getPixelsABGR").invoke(img); }
        catch (NoSuchMethodException e) {
            Method gp = c.getMethod("getPixel", int.class, int.class);
            src = new int[w * h]; abgr = false;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) src[y * w + x] = ((Number) gp.invoke(img, x, y)).intValue();
        }
        if (tw <= 0) tw = w;
        if (th <= 0) th = h;
        int[] out = new int[tw * th];
        for (int y = 0; y < th; y++) {
            int sy = (int) ((y + 0.5) * h / th);
            for (int x = 0; x < tw; x++) {
                int v = src[Math.min(h - 1, sy) * w + Math.min(w - 1, (int) ((x + 0.5) * w / tw))];
                out[y * tw + x] = abgr ? ((v & 0xFF) << 16) | (v & 0xFF00) | ((v >> 16) & 0xFF) : v & 0xFFFFFF;
            }
        }
        BufferedImage bi = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        bi.setRGB(0, 0, tw, th, out, 0, tw);
        return bi;
    }

    // ---------- the game side (reflection) ----------
    static final class McBackend implements Backend {
        private Object mc() throws Exception { return Class.forName("net.minecraft.client.Minecraft").getMethod("getInstance").invoke(null); }

        private Object mainTarget() throws Exception {
            Object mc = mc();
            for (Method m : mc.getClass().getMethods()) if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("RenderTarget")) return m.invoke(mc);
            Object gr = mc.getClass().getField("gameRenderer").get(mc);
            for (Method m : gr.getClass().getMethods()) if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("RenderTarget")) return m.invoke(gr);
            throw new IllegalStateException("no main render target");
        }

        @Override public void grab(Consumer<Object> callback) {
            try {
                Class<?> sc = Class.forName("net.minecraft.client.Screenshot");
                for (Method m : sc.getMethods()) {
                    if (m.getName().equals("takeScreenshot") && m.getParameterCount() == 2 && m.getParameterTypes()[1] == Consumer.class) {
                        m.invoke(null, mainTarget(), callback);
                        return;
                    }
                }
                throw new IllegalStateException("Screenshot.takeScreenshot not found");
            } catch (Throwable t) { callback.accept(null); }
        }

        private Object options() throws Exception { Object mc = mc(); return mc.getClass().getField("options").get(mc); }

        @Override public boolean hideGui() { try { return options().getClass().getField("hideGui").getBoolean(options()); } catch (Throwable t) { return false; } }
        @Override public void setHideGui(boolean hide) { try { Object o = options(); o.getClass().getField("hideGui").setBoolean(o, hide); } catch (Throwable ignored) { } }

        @Override public boolean thirdPersonFront() {
            try {
                Object o = options();
                Object t = o.getClass().getMethod("getCameraType").invoke(o);
                return String.valueOf(t).contains("FRONT");
            } catch (Throwable t) { return false; }
        }
        @Override public void setThirdPersonFront(boolean front) {
            try {
                Object o = options();
                for (Method m : o.getClass().getMethods()) {
                    if (m.getName().equals("setCameraType") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isEnum()) {
                        m.invoke(o, Enum.valueOf((Class<Enum>) m.getParameterTypes()[0].asSubclass(Enum.class), front ? "THIRD_PERSON_FRONT" : "FIRST_PERSON"));
                        return;
                    }
                }
            } catch (Throwable ignored) { }
        }
    }
}
