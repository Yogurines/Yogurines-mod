package com.yogurines.mod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.yogurines.mod.Yogurines;
import com.yogurines.mod.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Texturas dinámicas de fotos y avatares, pedidas al servidor según hacen falta. */
public final class Images {
    public record Tex(ResourceLocation rl, int w, int h) {}

    private static final Map<String, Tex> CACHE = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    private static int counter;

    /** Textura de una imagen del servidor; si aún no está, la pide y devuelve null. */
    public static Tex get(String id) {
        if (id == null || id.isEmpty()) return null;
        Tex t = CACHE.get(id);
        if (t == null && REQUESTED.add(id)) {
            CompoundTag o = new CompoundTag();
            o.putString("id", id);
            Net.toServer("img", o);
        }
        return t;
    }

    public static Tex cached(String key) {
        return CACHE.get(key);
    }

    /** Llega una imagen del servidor. */
    public static void receive(String id, byte[] bytes) {
        BufferedImage img = decode(bytes);
        if (img != null) put(id, img);
    }

    public static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    /** Sube una imagen de Java a la GPU y la guarda con esa clave. */
    public static Tex put(String key, BufferedImage img) {
        Tex old = CACHE.remove(key);
        if (old != null) Minecraft.getInstance().getTextureManager().release(old.rl());
        int w = img.getWidth(), h = img.getHeight();
        NativeImage ni = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
                ni.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        DynamicTexture tex = new DynamicTexture(ni);
        ResourceLocation rl = new ResourceLocation(Yogurines.MOD_ID, "dyn/" + (counter++));
        Minecraft.getInstance().getTextureManager().register(rl, tex);
        tex.setFilter(true, false);
        Tex t = new Tex(rl, w, h);
        CACHE.put(key, t);
        return t;
    }

    public static void forget(String key) {
        Tex old = CACHE.remove(key);
        if (old != null) Minecraft.getInstance().getTextureManager().release(old.rl());
    }

    public static void clear() {
        for (Tex t : CACHE.values()) Minecraft.getInstance().getTextureManager().release(t.rl());
        CACHE.clear();
        REQUESTED.clear();
    }

    /** Reescala (rellenando y recortando al centro) a un tamaño exacto. */
    public static BufferedImage cover(BufferedImage src, int w, int h) {
        double s = Math.max((double) w / src.getWidth(), (double) h / src.getHeight());
        int dw = (int) Math.ceil(src.getWidth() * s), dh = (int) Math.ceil(src.getHeight() * s);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        g.dispose();
        return out;
    }

    public static BufferedImage crop(BufferedImage src, int x, int y, int size, int out) {
        BufferedImage o = new BufferedImage(out, out, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, out, out, x, y, x + size, y + size, null);
        g.dispose();
        return o;
    }

    public static byte[] jpeg(BufferedImage img, float quality) {
        BufferedImage rgb = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                w.setOutput(ios);
                w.write(null, new IIOImage(rgb, null, null), p);
            }
            w.dispose();
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Sube una imagen al servidor en trozos (los paquetes cliente→servidor son pequeños). */
    public static String upload(byte[] bytes) {
        String uid = Long.toHexString(System.nanoTime()) + Integer.toHexString(new Random().nextInt());
        int chunk = 20000;
        int n = (bytes.length + chunk - 1) / chunk;
        for (int i = 0; i < n; i++) {
            CompoundTag o = new CompoundTag();
            o.putString("uid", uid);
            o.putInt("i", i);
            o.putInt("n", n);
            o.putByteArray("b", Arrays.copyOfRange(bytes, i * chunk, Math.min(bytes.length, (i + 1) * chunk)));
            Net.toServer("up", o);
        }
        return uid;
    }

    private Images() {}
}
