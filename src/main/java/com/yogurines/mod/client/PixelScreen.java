package com.yogurines.mod.client;

import com.yogurines.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;

/**
 * Pantalla dibujada en coordenadas "virtuales" que se escalan a píxeles exactos de la ventana,
 * así el móvil, la cartera y el cajero se ven nítidos con cualquier escala de interfaz.
 */
public abstract class PixelScreen extends Screen {
    protected float s = 1;
    protected float ox, oy;
    protected int P = 1;
    protected GuiGraphics g;

    protected PixelScreen(String title) {
        super(Component.literal(title));
    }

    /** Ancho y alto virtuales y qué parte de la altura de la ventana ocupa. */
    protected abstract int vw();
    protected abstract int vh();
    protected float heightFraction() { return 0.92f; }

    protected abstract void draw(int mx, int my, float pt);

    protected void layout() {
        var win = Minecraft.getInstance().getWindow();
        int wh = win.getHeight(), ww = win.getWidth();
        P = Math.max(1, Math.min((int) Math.floor(wh * heightFraction() / vh()), (int) Math.floor(ww * 0.95 / vw())));
        double gs = win.getGuiScale();
        s = (float) (P / gs);
        int px = (ww - vw() * P) / 2, py = (wh - vh() * P) / 2;
        ox = (float) (px / gs);
        oy = (float) (py / gs);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float pt) {
        layout();
        renderBackground(graphics);
        g = graphics;
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(s, s, 1);
        draw(vx(mouseX), vy(mouseY), pt);
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected int vx(double mx) { return (int) Math.floor((mx - ox) / s); }
    protected int vy(double my) { return (int) Math.floor((my - oy) / s); }

    /** Pasa de coordenadas virtuales a coordenadas de interfaz (para el recorte). */
    protected int gx(int v) { return Math.round(ox + v * s); }
    protected int gy(int v) { return Math.round(oy + v * s); }

    protected void clip(int x, int y, int w, int h) {
        g.enableScissor(gx(x), gy(y), gx(x + w), gy(y + h));
    }

    protected void unclip() {
        g.disableScissor();
    }

    // ---------- dibujo ----------

    protected void rect(int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
    }

    protected void round(int x, int y, int w, int h, int r, int c) {
        if (r <= 0 || w < 2 * r || h < 2 * r) { rect(x, y, w, h, c); return; }
        rect(x, y + r, w, h - 2 * r, c);
        for (int i = 0; i < r; i++) {
            int inset = r - (int) Math.round(Math.sqrt(r * r - (r - i - 0.5) * (r - i - 0.5)));
            rect(x + inset, y + i, w - 2 * inset, 1, c);
            rect(x + inset, y + h - 1 - i, w - 2 * inset, 1, c);
        }
    }

    protected void gradient(int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    protected void text(String t, int x, int y, int c) {
        g.drawString(font, t, x, y, c, false);
    }

    protected void text(FormattedCharSequence t, int x, int y, int c) {
        g.drawString(font, t, x, y, c, false);
    }

    protected void textShadow(String t, int x, int y, int c) {
        g.drawString(font, t, x, y, c, true);
    }

    protected void center(String t, int cx, int y, int c) {
        g.drawString(font, t, cx - font.width(t) / 2, y, c, false);
    }

    protected void right(String t, int rx, int y, int c) {
        g.drawString(font, t, rx - font.width(t), y, c, false);
    }

    /** Texto a escala distinta (por ejemplo el reloj grande o letras pequeñas). */
    protected void textScaled(String t, float x, float y, float k, int c, boolean centered) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, t, centered ? -font.width(t) / 2 : 0, 0, c, false);
        g.pose().popPose();
    }

    protected String fit(String t, int w) {
        if (font.width(t) <= w) return t;
        return font.plainSubstrByWidth(t, w - font.width("…")) + "…";
    }

    protected void image(Images.Tex t, int x, int y, int w, int h) {
        if (t == null) return;
        g.blit(t.rl(), x, y, w, h, 0, 0, t.w(), t.h(), t.w(), t.h());
    }

    /** Imagen que rellena el hueco recortando lo que sobra (como object-fit: cover). */
    protected void imageCover(Images.Tex t, int x, int y, int w, int h) {
        if (t == null) return;
        float k = Math.max((float) w / t.w(), (float) h / t.h());
        float uw = w / k, vh = h / k;
        float u = (t.w() - uw) / 2, v = (t.h() - vh) / 2;
        g.blit(t.rl(), x, y, w, h, u, v, Math.round(uw), Math.round(vh), t.w(), t.h());
    }

    protected void tex(ResourceLocation rl, int x, int y, int w, int h, int tw, int th) {
        g.blit(rl, x, y, w, h, 0, 0, tw, th, tw, th);
    }

    protected static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    protected static void sound(SoundEvent e, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    protected static void tap() {
        sound(ModSounds.TAP.get(), 1f, 0.6f);
    }
}
