package com.yogurines.mod.client;

import com.yogurines.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Avisos del móvil abajo a la derecha (estilo A: tarjeta de móvil). */
public final class Toasts {
    private record Toast(String app, String title, String text, long start) {}

    private static final List<Toast> LIST = new ArrayList<>();
    private static final long LIFE = 4500;
    public static final int W = 168;

    public static void push(String app, String title, String text) {
        boolean on = switch (app) {
            case "chat" -> ClientData.notifChat;
            case "social" -> ClientData.notifSocial;
            case "bank" -> ClientData.notifBank;
            default -> true;
        };
        if (!on) return;
        LIST.add(new Toast(app, title, text, System.currentTimeMillis()));
        while (LIST.size() > 3) LIST.remove(0);
        var sound = switch (app) {
            case "social" -> ModSounds.LIKE.get();
            case "bank" -> ModSounds.TRANSFER_SENT.get();
            default -> ModSounds.MESSAGE_RECEIVED.get();
        };
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1f, 0.8f));
    }

    public static int appColor(String app) {
        return switch (app) {
            case "chat" -> 0xFF25C26E;
            case "social" -> 0xFFE8457C;
            case "bank" -> 0xFF2E9E5B;
            default -> 0xFF5865F2;
        };
    }

    public static String appName(String app) {
        return switch (app) {
            case "chat" -> "YoguChat";
            case "social" -> "Yogugram";
            case "bank" -> "Banco Yogurín";
            default -> "Móvil";
        };
    }

    public static void render(GuiGraphics g, int sw, int sh) {
        if (LIST.isEmpty() || CameraMode.capturing()) return;
        long now = System.currentTimeMillis();
        LIST.removeIf(t -> now - t.start > LIFE);
        Font font = Minecraft.getInstance().font;
        int y = sh - 6;
        for (int i = LIST.size() - 1; i >= 0; i--) {
            Toast t = LIST.get(i);
            long age = now - t.start;
            float in = Mth.clamp(age / 250f, 0, 1), out = Mth.clamp((LIFE - age) / 300f, 0, 1);
            float k = Math.min(in, out);
            float ease = 1 - (1 - k) * (1 - k);
            List<FormattedCharSequence> lines = wrap(font, t.text, W - 30, 2);
            int h = 22 + lines.size() * 10;
            int x = sw - 6 - W + Math.round((1 - ease) * (W + 10));
            y -= h;
            int a = Math.round(ease * 235);
            card(g, x, y, W, h, (a << 24) | 0x1B1D24);
            g.fill(x + 6, y + 6, x + 18, y + 18, appColor(t.app) & 0x00FFFFFF | (Math.round(ease * 255) << 24));
            g.drawString(font, appLetter(t.app), x + 9, y + 8, 0xFFFFFFFF, false);
            String head = font.plainSubstrByWidth(t.title, W - 70);
            g.drawString(font, head, x + 23, y + 5, 0xFFFFFFFF, false);
            g.drawString(font, Component.literal("ahora"), x + W - 6 - font.width("ahora"), y + 5, 0xFF8A8F9C, false);
            g.drawString(font, Component.literal(appName(t.app)), x + 23, y + 14, 0xFF8A8F9C, false);
            int ly = y + 24;
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, x + 8, ly, 0xFFE3E5EA, false);
                ly += 10;
            }
            y -= 4;
        }
    }

    private static String appLetter(String app) {
        return switch (app) {
            case "chat" -> "C";
            case "social" -> "Y";
            case "bank" -> "$";
            default -> "M";
        };
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + 2, y + h - 2, color);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
    }

    /** Ajusta el texto a un ancho y un máximo de líneas; si no cabe termina en "…". */
    public static List<FormattedCharSequence> wrap(Font font, String text, int width, int maxLines) {
        List<FormattedCharSequence> out = new ArrayList<>();
        List<String> words = new ArrayList<>(List.of(text.split(" ")));
        StringBuilder line = new StringBuilder();
        List<String> lines = new ArrayList<>();
        for (String w : words) {
            while (font.width(w) > width) {
                String part = font.plainSubstrByWidth(w, width);
                if (part.isEmpty()) break;
                if (line.length() > 0) { lines.add(line.toString()); line.setLength(0); }
                lines.add(part);
                w = w.substring(part.length());
            }
            String test = line.length() == 0 ? w : line + " " + w;
            if (font.width(test) > width && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(w);
            } else {
                line.setLength(0);
                line.append(test);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            String l = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) {
                l = font.plainSubstrByWidth(l, width - font.width("…")) + "…";
            }
            out.add(Component.literal(l).getVisualOrderText());
        }
        return out;
    }

    private Toasts() {}
}
