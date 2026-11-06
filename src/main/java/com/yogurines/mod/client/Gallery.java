package com.yogurines.mod.client;

import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** Galería del móvil: las fotos se guardan en .minecraft/yogurines/galeria como JPG. */
public final class Gallery {
    private static List<String> names;

    public static File dir() {
        File d = new File(new File(Minecraft.getInstance().gameDirectory, "yogurines"), "galeria");
        d.mkdirs();
        return d;
    }

    public static List<String> list() {
        if (names == null) {
            names = new ArrayList<>();
            String[] f = dir().list((d, n) -> n.endsWith(".jpg"));
            if (f != null) names.addAll(Arrays.asList(f));
            names.sort(Comparator.reverseOrder());
        }
        return names;
    }

    public static String add(BufferedImage img) {
        String name = "foto_" + System.currentTimeMillis() + ".jpg";
        byte[] b = Images.jpeg(img, 0.9f);
        if (b == null) return null;
        try {
            Files.write(new File(dir(), name).toPath(), b);
        } catch (Exception e) {
            return null;
        }
        list().add(0, name);
        return name;
    }

    public static void delete(String name) {
        new File(dir(), name).delete();
        list().remove(name);
        Images.forget("gal:" + name);
    }

    public static BufferedImage read(String name) {
        try {
            return ImageIO.read(new File(dir(), name));
        } catch (Exception e) {
            return null;
        }
    }

    /** Textura de la foto (se carga del disco la primera vez). */
    public static Images.Tex tex(String name) {
        String key = "gal:" + name;
        Images.Tex t = Images.cached(key);
        if (t == null) {
            BufferedImage img = read(name);
            if (img == null) return null;
            t = Images.put(key, img);
        }
        return t;
    }

    private Gallery() {}
}
