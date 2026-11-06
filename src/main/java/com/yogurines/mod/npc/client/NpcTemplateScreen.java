package com.yogurines.mod.npc.client;

import com.yogurines.mod.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Elegir con qué plantilla pone NPCs el modificador (o NPC nuevo en blanco). */
public class NpcTemplateScreen extends Screen {
    private List<String> names;
    private String current;
    private int scroll;

    public NpcTemplateScreen(List<String> names, String current) {
        super(Component.literal("Plantillas de NPC"));
        this.names = new ArrayList<>(names);
        this.current = current;
    }

    public void update(List<String> names, String current) {
        this.names = new ArrayList<>(names);
        this.current = current;
    }

    private int pw() { return Math.min(width - 20, 240); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return Math.max(10, height / 2 - 110); }
    private int ph() { return Math.min(height - 20, 220); }
    private int rows() { return Math.max(1, (ph() - 60) / 18); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = px(), y = py(), w = pw(), h = ph();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xF21A1C21);
        g.fill(x, y, x + w, y + 2, 0xFF7FD36A);
        g.drawString(font, Nameplate.styled("PLANTILLAS DE NPC", "tech"), x + 8, y + 9, 0xFFFFFFFF, false);
        g.drawString(font, "Elige qué pone el modificador al hacer clic en un bloque", x + 8, y + 22, 0xFF9A9A9A, false);
        int ry = y + 38;
        row(g, mx, my, x, ry, w, "+ NPC nuevo (en blanco)", current.isEmpty(), false);
        int vis = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, names.size() - vis)));
        for (int i = 0; i < vis && i + scroll < names.size(); i++) {
            String n = names.get(i + scroll);
            row(g, mx, my, x, ry + 18 * (i + 1), w, n, n.equalsIgnoreCase(current), true);
        }
        if (names.isEmpty()) g.drawString(font, "Aún no hay plantillas. Guárdalas desde el editor.", x + 8, ry + 24, 0xFF777777, false);
        super.render(g, mx, my, pt);
    }

    private void row(GuiGraphics g, int mx, int my, int x, int y, int w, String label, boolean on, boolean deletable) {
        boolean hover = mx >= x + 6 && mx < x + w - 6 && my >= y && my < y + 16;
        g.fill(x + 6, y, x + w - 6, y + 16, on ? 0xFF2F5A28 : (hover ? 0xFF33373F : 0xFF262A31));
        g.drawString(font, label, x + 12, y + 4, on ? 0xFFB8FFA8 : 0xFFE0E0E0, false);
        if (deletable) {
            boolean hx = mx >= x + w - 22 && mx < x + w - 8 && my >= y && my < y + 16;
            g.drawString(font, "✕", x + w - 18, y + 4, hx ? 0xFFFF6A5A : 0xFF8A8A8A, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = px(), y = py(), w = pw();
        int ry = y + 38;
        if (mx >= x + 6 && mx < x + w - 6 && my >= ry && my < ry + 16) { pick(""); return true; }
        int vis = rows();
        for (int i = 0; i < vis && i + scroll < names.size(); i++) {
            int yy = ry + 18 * (i + 1);
            if (my < yy || my >= yy + 16) continue;
            String n = names.get(i + scroll);
            if (mx >= x + w - 22 && mx < x + w - 8) {
                CompoundTag t = new CompoundTag();
                t.putString("name", n);
                Net.toServer("npc_tpl_del", t);
                PixelSound.tap();
                return true;
            }
            if (mx >= x + 6 && mx < x + w - 6) { pick(n); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void pick(String n) {
        CompoundTag t = new CompoundTag();
        t.putString("name", n);
        Net.toServer("npc_tpl_pick", t);
        PixelSound.tap();
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
