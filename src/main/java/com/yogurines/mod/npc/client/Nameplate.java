package com.yogurines.mod.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yogurines.mod.Yogurines;
import com.yogurines.mod.npc.NpcEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Locale;

/**
 * Nombre del NPC encima de la cabeza: tipografía, fondo (vanilla, placa, holograma, cinta o banderín),
 * título dorado pegado debajo e icono holográfico opcional encima.
 */
public final class Nameplate {
    public static final ResourceLocation RIBBON = new ResourceLocation(Yogurines.MOD_ID, "textures/npc/ribbon.png");
    public static final ResourceLocation ICONS = new ResourceLocation(Yogurines.MOD_ID, "textures/npc/icons.png");
    private static final ResourceLocation TECH = new ResourceLocation(Yogurines.MOD_ID, "tech");
    private static final int FULL = 0xF000F0;
    private static final float T = 0.75f; // tamaño de un píxel de la cinta en unidades de texto

    public static ResourceLocation fontFor(String f) {
        return switch (f) {
            case "tech" -> TECH;
            case "medieval" -> new ResourceLocation(Yogurines.MOD_ID, "medieval");
            case "cartel" -> new ResourceLocation(Yogurines.MOD_ID, "cartel");
            default -> Style.DEFAULT_FONT;
        };
    }

    public static MutableComponent styled(String text, String font) {
        return Component.literal(text).withStyle(Style.EMPTY.withFont(fontFor(font)));
    }

    public static void render(NpcEntity e, NpcEntity.Look l, float top, PoseStack ps, MultiBufferSource buf,
                              Quaternionf cam, Font font, float pt) {
        float t = (System.currentTimeMillis() % 1_000_000L) / 1000f;
        ps.pushPose();
        ps.translate(0, top, 0);
        ps.mulPose(cam);
        ps.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f m = ps.last().pose();

        MutableComponent name = styled(l.name, l.font);
        int w = font.width(name);
        float x = -w / 2f;
        float ny = -12f; // arriba del texto del nombre
        boolean ribbon = l.bg.equals("ribbon") || l.bg.equals("banner");
        float iconBase = ny - 3;

        switch (l.bg) {
            case "plate" -> {
                float x0 = x - 4, x1 = x + w + 3, y0 = ny - 2, y1 = ny + 9;
                quad(buf, m, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0.03f, 0xFF000000);
                quad(buf, m, x0, y0, x1, y1, 0.02f, 0xFF000000 | l.c1);
                quad(buf, m, x0, y0, x1, y0 + 1, 0.015f, 0xFF000000 | shade(l.c1, 0.35f));
                quad(buf, m, x0, y1 - 1, x1, y1, 0.015f, 0xFF000000 | shade(l.c1, -0.3f));
                font.drawInBatch(name, x, ny, 0xFF000000 | l.c2, true, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
                iconBase = y0 - 2;
            }
            case "holo" -> {
                holo(l, name, x, ny, w, t, m, buf, font);
                iconBase = ny - 3;
            }
            case "ribbon", "banner" -> iconBase = ribbon(l, name, w, ny, t, m, buf, font, l.bg.equals("banner"));
            default -> {
                font.drawInBatch(name, x, ny, 0x20FFFFFF, false, m, buf, Font.DisplayMode.SEE_THROUGH, 0x40000000, FULL);
                font.drawInBatch(name, x, ny, 0xFF000000 | l.c2, false, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
                iconBase = ny - 3;
            }
        }

        if (!l.title.isEmpty()) {
            MutableComponent title = styled(l.title.toUpperCase(Locale.ROOT), "tech");
            ps.pushPose();
            // fuente pixel nítida, sin sombra y sin escalar (la escala la emborronaba)
            float ty = ribbon ? ny + 8f : ny + 8f + (l.bg.equals("plate") ? 2f : 0f);
            ps.translate(0, ty, 0);
            int tw = font.width(title);
            font.drawInBatch(title, -tw / 2f, 0, 0xFFFFD84A, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, FULL);
            ps.popPose();
        }

        int icon = iconIndex(l);
        if (icon >= 0) {
            float bob = Mth.sin(t * 3f) * 1.2f;
            float cy = iconBase - 6 + bob;
            int a = (Mth.sin(t * 9f) > 0.93f) ? 0x90 : 0xFF;
            texQuad(buf, ICONS, m, -6.1f, cy - 6.1f, 6.1f, cy + 6.1f, icon * 9 / 48f, 0, (icon * 9 + 9) / 48f, 1, 0.01f, (0x40 << 24) | 0x39FF7A);
            texQuad(buf, ICONS, m, -4.5f, cy - 4.5f, 4.5f, cy + 4.5f, icon * 9 / 48f, 0, (icon * 9 + 9) / 48f, 1, 0f, (a << 24) | 0xFFFFFF);
        }
        ps.popPose();
    }

    private static int iconIndex(NpcEntity.Look l) {
        return switch (l.icon) {
            case "talk" -> 0;
            case "shop" -> 1;
            case "quest" -> 2;
            case "bank" -> 3;
            case "info" -> 4;
            case "auto" -> l.hasDialog ? 0 : (l.hasShop ? 1 : -1);
            default -> -1;
        };
    }

    // ---------- holograma ----------

    private static int hash(int v) {
        v ^= v >>> 16; v *= 0x7feb352d; v ^= v >>> 15; v *= 0x846ca68b; v ^= v >>> 16;
        return v & 0x7fffffff;
    }

    private static void holo(NpcEntity.Look l, MutableComponent name, float x, float y, int w, float t,
                             Matrix4f m, MultiBufferSource buf, Font font) {
        int slot = (int) (t * 9);
        switch (l.holo) {
            case "projection" -> {
                float dy = Mth.sin(t * 2.6f) * 0.8f;
                int a = (hash(slot / 4) % 23 == 0) ? 0x66 : 0xD8;
                font.drawInBatch(name, x, y + dy, (a << 24) | 0xA8FFC6, false, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
                for (int k = 0; k < 3; k++) {
                    float ly = y + dy - 1 + ((t * 9 + k * 3.7f) % 11f);
                    quad(buf, m, x - 1, ly, x + w + 1, ly + 0.7f, -0.01f, 0x5539FF7A);
                }
            }
            case "interference" -> {
                float p = t % 2.2f;
                boolean burst = (p > 0.8f && p < 0.97f) || (p > 1.7f && p < 1.78f);
                if (burst) {
                    float j = (hash(slot) % 3) - 1;
                    font.drawInBatch(name, x - 3 + j, y, 0x8800FFF0, false, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
                    font.drawInBatch(name, x + 4 - j, y, 0x66B4FF00, false, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
                    float ty = y + (hash(slot + 7) % 9);
                    quad(buf, m, x - 4, ty, x + w + 5, ty + 0.8f, -0.01f, 0x8839FF7A);
                    font.drawInBatch(name, x + j, y, 0xCC7DFFAE, false, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
                } else {
                    font.drawInBatch(name, x, y, 0xEE7DFFAE, false, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
                }
            }
            default -> { // glitch de color
                int h = hash(slot);
                float d1 = (h % 5 == 0) ? -2f : (h % 7 == 0 ? 1f : 0f);
                float d2 = (hash(slot + 31) % 6 == 0) ? 2f : 0f;
                int a = (hash(slot / 3) % 17 == 0) ? 0x70 : 0xEE;
                font.drawInBatch(name, x + d1, y, 0x9900FFF0, false, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
                font.drawInBatch(name, x + d2, y - (d2 > 0 ? 1 : 0), 0x99B4FF00, false, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
                font.drawInBatch(name, x, y, (a << 24) | 0x7DFFAE, false, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
            }
        }
    }

    // ---------- cinta y banderín ----------

    /** Devuelve la altura por encima de la cinta (para colocar el icono). */
    private static float ribbon(NpcEntity.Look l, MutableComponent name, int w, float ny, float t,
                                Matrix4f m, MultiBufferSource buf, Font font, boolean wave) {
        int W = 18 + (int) Math.ceil((w + 3) / T);
        int M = Math.max(0, W - 24);
        W = 24 + M;
        float total = W * T;
        float x0 = -total / 2f;
        float top = ny + 4f - 6.5f * T; // centro de la banda alineado con el centro del texto
        int tint = 0xFF000000 | shade(l.c1, 0.12f);
        VertexConsumer vc = buf.getBuffer(RenderType.text(RIBBON));
        float minTop = top;
        float centerOff = 0;
        for (int i = 0; i < W; i++) {
            int u;
            if (i < 12) u = i;
            else if (i >= W - 12) u = 20 + (i - (W - 12));
            else u = 12 + ((i - 12) % 8);
            float off = 0;
            if (wave) {
                off = -Math.round(Mth.sin((float) Math.PI * i / (W - 1)) * 3f) + Math.round(Mth.sin(i / 7f - t * 2.4f) * 0.8f);
                if (i == W / 2) centerOff = off;
            }
            float cx0 = x0 + i * T, cx1 = cx0 + T;
            float cy0 = top + off * T, cy1 = cy0 + 16 * T;
            minTop = Math.min(minTop, cy0);
            float u0 = u / 32f, u1 = (u + 1) / 32f;
            vertexQuad(vc, m, cx0, cy0, cx1, cy1, u0, 0, u1, 1, 0.03f, tint, FULL);
        }
        font.drawInBatch(name, -w / 2f, ny + centerOff * T, 0xFF000000 | l.c2, true, m, buf, Font.DisplayMode.NORMAL, 0, FULL);
        return minTop - 1;
    }

    // ---------- utilidades de dibujo ----------

    public static int shade(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        if (f >= 0) { r += (int) ((255 - r) * f); g += (int) ((255 - g) * f); b += (int) ((255 - b) * f); }
        else { r = (int) (r * (1 + f)); g = (int) (g * (1 + f)); b = (int) (b * (1 + f)); }
        return (r << 16) | (g << 8) | b;
    }

    private static void quad(MultiBufferSource buf, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        VertexConsumer vc = buf.getBuffer(RenderType.textBackground());
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(FULL).endVertex();
        // también por detrás, por si la cara se ve del revés
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(FULL).endVertex();
    }

    private static void texQuad(MultiBufferSource buf, ResourceLocation tex, Matrix4f m, float x0, float y0, float x1, float y1,
                                float u0, float v0, float u1, float v1, float z, int argb) {
        vertexQuad(buf.getBuffer(RenderType.text(tex)), m, x0, y0, x1, y1, u0, v0, u1, v1, z, argb, FULL);
    }

    private static void vertexQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1,
                                   float u0, float v0, float u1, float v1, float z, int argb, int light) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv(u0, v0).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv(u0, v1).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv(u1, v1).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv(u1, v0).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv(u1, v0).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv(u1, v1).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv(u0, v1).uv2(light).endVertex();
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv(u0, v0).uv2(light).endVertex();
    }

    private Nameplate() {}
}
