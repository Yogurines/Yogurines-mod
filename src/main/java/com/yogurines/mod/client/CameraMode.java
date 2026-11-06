package com.yogurines.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.registry.ModSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;

/**
 * Modo cámara del móvil. No es una pantalla: el jugador sigue moviéndose y mirando,
 * y el mod intercepta los clics, la rueda y las flechas.
 */
public final class CameraMode {
    private static boolean active, selfie, guideHidden, capture;
    private static float zoom = 1f;
    private static float offX, offY;
    private static CameraType previous;
    private static long flashStart, savedMsg;

    public static boolean active() { return active; }
    public static boolean selfie() { return active && selfie; }
    public static boolean capturing() { return capture; }
    public static float zoom() { return zoom; }
    public static float offX() { return offX; }
    public static float offY() { return offY; }

    public static void enter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(null);
        active = true;
        selfie = false;
        zoom = 1f;
        offX = offY = 0;
        previous = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        sendMode();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TAP.get(), 1f, 0.7f));
    }

    public static void exit(boolean backToPhone) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        active = false;
        capture = false;
        mc.options.setCameraType(previous == null ? CameraType.FIRST_PERSON : previous);
        sendMode();
        if (backToPhone) ClientHooks.openPhoneAt(PhoneScreen.Page.GALLERY);
    }

    private static void sendMode() {
        if (Minecraft.getInstance().getConnection() == null) return;
        CompoundTag o = new CompoundTag();
        o.putInt("m", active ? (selfie ? 2 : 1) : 0);
        Net.toServer("cam", o);
    }

    public static void toggleSelfie() {
        selfie = !selfie;
        offX = offY = 0;
        zoom = 1f;
        Minecraft.getInstance().options.setCameraType(selfie ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        sendMode();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TAP.get(), 1.2f, 0.6f));
    }

    public static void scroll(double delta) {
        zoom = Mth.clamp(zoom + (float) delta * 0.25f, 1f, selfie ? 2.5f : 5f);
    }

    public static void toggleGuide() {
        guideHidden = !guideHidden;
    }

    public static void shoot() {
        if (capture || System.currentTimeMillis() - flashStart < 400) return;
        capture = true;
    }

    /** Cada tick del cliente: flechas para mover el encuadre del selfie. */
    public static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { active = false; return; }
        if (!(mc.player.getMainHandItem().getItem() instanceof com.yogurines.mod.item.PhoneItem)
                && !(mc.player.getOffhandItem().getItem() instanceof com.yogurines.mod.item.PhoneItem)
                && !hasPhone()) { exit(false); return; }
        if (selfie && mc.screen == null) {
            long w = mc.getWindow().getWindow();
            float sp = 0.06f;
            if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT)) offX -= sp;
            if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT)) offX += sp;
            if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_UP)) offY += sp;
            if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_DOWN)) offY -= sp;
            offX = Mth.clamp(offX, -1f, 1f);
            offY = Mth.clamp(offY, -0.6f, 0.8f);
        }
        if (selfie && mc.options.getCameraType() != CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (!selfie && mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    private static boolean hasPhone() {
        var inv = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof com.yogurines.mod.item.PhoneItem) return true;
        }
        return false;
    }

    /** Al final del fotograma (sin HUD ni nombres) se copia la imagen. */
    public static void afterFrame() {
        if (!capture) return;
        capture = false;
        Minecraft mc = Minecraft.getInstance();
        NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        int w = shot.getWidth(), h = shot.getHeight();
        // recorte 16:9 centrado y reducción a 960x540
        int cw = w, ch = w * 9 / 16;
        if (ch > h) { ch = h; cw = h * 16 / 9; }
        int x0 = (w - cw) / 2, y0 = (h - ch) / 2;
        int ow = 960, oh = 540;
        BufferedImage out = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
                int r = 0, gg = 0, b = 0, n = 0;
                int sx0 = x0 + x * cw / ow, sx1 = Math.max(sx0 + 1, x0 + (x + 1) * cw / ow);
                int sy0 = y0 + y * ch / oh, sy1 = Math.max(sy0 + 1, y0 + (y + 1) * ch / oh);
                for (int sy = sy0; sy < sy1; sy += Math.max(1, (sy1 - sy0) / 2)) {
                    for (int sx = sx0; sx < sx1; sx += Math.max(1, (sx1 - sx0) / 2)) {
                        int c = shot.getPixelRGBA(Math.min(sx, w - 1), Math.min(sy, h - 1));
                        r += c & 255; gg += (c >> 8) & 255; b += (c >> 16) & 255; n++;
                    }
                }
                out.setRGB(x, y, ((r / n) << 16) | ((gg / n) << 8) | (b / n));
            }
        }
        shot.close();
        flashStart = System.currentTimeMillis();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SHUTTER.get(), 1f, 1f));
        new Thread(() -> {
            if (Gallery.add(out) != null) savedMsg = System.currentTimeMillis();
        }, "yogurines-foto").start();
    }

    /** Visor: esquinas, modo, zoom, flash y guía de controles pequeña abajo a la izquierda. */
    public static void render(GuiGraphics g, int sw, int sh) {
        if (!active || capture) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int c = 0xCCFFFFFF, m = 14, L = 16;
        // esquinas del visor
        g.fill(m, m, m + L, m + 1, c); g.fill(m, m, m + 1, m + L, c);
        g.fill(sw - m - L, m, sw - m, m + 1, c); g.fill(sw - m - 1, m, sw - m, m + L, c);
        g.fill(m, sh - m - 1, m + L, sh - m, c); g.fill(m, sh - m - L, m + 1, sh - m, c);
        g.fill(sw - m - L, sh - m - 1, sw - m, sh - m, c); g.fill(sw - m - 1, sh - m - L, sw - m, sh - m, c);
        // centro
        g.fill(sw / 2 - 4, sh / 2, sw / 2 + 5, sh / 2 + 1, 0x88FFFFFF);
        g.fill(sw / 2, sh / 2 - 4, sw / 2 + 1, sh / 2 + 5, 0x88FFFFFF);
        // modo y zoom arriba
        String mode = selfie ? "SELFIE" : "FOTO";
        int mw = font.width(mode) + 10;
        Toasts.card(g, sw / 2 - mw / 2, m, mw, 13, 0x99000000);
        g.drawString(font, mode, sw / 2 - mw / 2 + 5, m + 3, 0xFFFFD84A, false);
        String z = String.format("%.1fx", zoom);
        g.drawString(font, z, sw - m - 6 - font.width(z), m + 6, 0xFFFFFFFF, true);
        // punto rojo "REC" de cámara
        g.fill(m + 6, m + 6, m + 10, m + 10, 0xFFFF4040);
        // guía
        g.pose().pushPose();
        float s = 0.75f;
        g.pose().scale(s, s, 1);
        int gx = Math.round((m + 4) / s), gy;
        if (guideHidden) {
            gy = Math.round((sh - m - 14) / s);
            Toasts.card(g, gx, gy, font.width("H  Mostrar controles") + 10, 13, 0x88000000);
            g.drawString(font, "H", gx + 5, gy + 3, 0xFFFFD84A, false);
            g.drawString(font, "Mostrar controles", gx + 5 + font.width("H  "), gy + 3, 0xFFFFFFFF, false);
        } else {
            String[][] rows = selfie
                    ? new String[][]{{"Clic dcho", "Foto"}, {"Clic izq", "Cámara normal"}, {"Rueda", "Zoom"}, {"Flechas", "Mover encuadre"}, {"H", "Ocultar guía"}, {"Esc", "Salir"}}
                    : new String[][]{{"Clic dcho", "Foto"}, {"Clic izq", "Selfie"}, {"Rueda", "Zoom"}, {"H", "Ocultar guía"}, {"Esc", "Salir"}};
            int kw = 0, vw = 0;
            for (String[] r : rows) { kw = Math.max(kw, font.width(r[0])); vw = Math.max(vw, font.width(r[1])); }
            int bw = kw + vw + 18, bh = rows.length * 11 + 6;
            gy = Math.round((sh - m - 4) / s) - bh;
            Toasts.card(g, gx, gy, bw, bh, 0x88000000);
            int y = gy + 4;
            for (String[] r : rows) {
                g.drawString(font, r[0], gx + 6, y, 0xFFFFD84A, false);
                g.drawString(font, r[1], gx + 12 + kw, y, 0xFFFFFFFF, false);
                y += 11;
            }
        }
        g.pose().popPose();
        long now = System.currentTimeMillis();
        long fa = now - flashStart;
        if (fa < 350) {
            int a = Math.round((1 - fa / 350f) * 220);
            g.fill(0, 0, sw, sh, (a << 24) | 0xFFFFFF);
        }
        if (now - savedMsg < 1800) {
            String t = "Guardada en la galería";
            int tw = font.width(t) + 12;
            Toasts.card(g, sw / 2 - tw / 2, sh - 40, tw, 14, 0xAA000000);
            g.drawString(font, t, sw / 2 - tw / 2 + 6, sh - 37, 0xFFFFFFFF, false);
        }
    }

    private CameraMode() {}
}
