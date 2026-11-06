package com.yogurines.mod.npc.client;

import com.yogurines.mod.net.Net;
import com.yogurines.mod.npc.NpcEntity;
import com.yogurines.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Diálogo estilo RPG: caja abajo con la cara del NPC, texto letra a letra y respuestas. */
public class NpcDialogScreen extends Screen {
    private final NpcEntity npc;
    private final ListTag nodes;
    private final String first;
    private final boolean hasShop;
    private int node;
    private String text = "";
    private float shown;
    private int sel;
    private boolean waiting;
    private String error = "";
    private long errorTime;
    private int lastSoundAt;

    public NpcDialogScreen(NpcEntity npc, ListTag nodes, String first, boolean hasShop) {
        super(Component.literal("Diálogo"));
        this.npc = npc;
        this.nodes = nodes;
        this.first = first;
        this.hasShop = hasShop;
    }

    @Override
    protected void init() {
        if (text.isEmpty()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.NPC_DIALOG_OPEN.get(), 1f, 0.6f));
            go(0, true);
        }
    }

    private CompoundTag cur() {
        return node >= 0 && node < nodes.size() ? nodes.getCompound(node) : new CompoundTag();
    }

    private ListTag opts() {
        return cur().getList("opts", Tag.TAG_COMPOUND);
    }

    private void go(int i, boolean start) {
        if (i < 0 || i >= nodes.size()) { onClose(); return; }
        node = i;
        text = start && !first.isEmpty() ? first : cur().getString("text");
        shown = 0;
        sel = 0;
        lastSoundAt = 0;
    }

    private boolean typing() {
        return shown < text.length();
    }

    @Override
    public void tick() {
        if (npc.isRemoved() || npc.distanceToSqr(Minecraft.getInstance().player) > 12 * 12) { onClose(); return; }
        if (typing()) {
            shown = Math.min(text.length(), shown + 1.6f);
            int n = (int) shown;
            if (n - lastSoundAt >= 2 && n < text.length() && text.charAt(n - 1) != ' ') {
                lastSoundAt = n;
                float pitch = 0.95f + (float) Math.random() * 0.1f;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.NPC_LETTER.get(), pitch, 0.35f));
            }
        }
    }

    // ---------- dibujo ----------

    private int boxX() { return Math.max(6, width / 2 - 210); }
    private int boxW() { return Math.min(width - 12, 420); }
    private int boxH() { return 92; }
    private int boxY() { return height - boxH() - 8; }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fillGradient(0, height - 150, width, height, 0x00000000, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = boxX(), y = boxY(), w = boxW(), h = boxH();
        // caja
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
        g.fill(x, y, x + w, y + h, 0xEE100A1E);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF5A4D8A);
        // retrato
        int ps = 54;
        int px = x + 10, py = y + 10;
        g.fill(px - 2, py - 2, px + ps + 2, py + ps + 2, 0xFFFFFFFF);
        g.fill(px - 1, py - 1, px + ps + 1, py + ps + 1, 0xFF2A2147);
        ResourceLocation skin = NpcSkins.texture(npc.look());
        g.blit(skin, px, py, ps, ps, 8, 8, 8, 8, 64, 64);
        g.blit(skin, px, py, ps, ps, 40, 8, 8, 8, 64, 64);
        // nombre
        NpcEntity.Look l = npc.look();
        Component name = Nameplate.styled(l.name.isEmpty() ? "NPC" : l.name, l.font);
        int nw = font.width(name) + 12;
        g.fill(x + 8, y - 14, x + 8 + nw, y + 1, 0xFFFFFFFF);
        g.fill(x + 9, y - 13, x + 7 + nw, y, 0xFF5A4D8A);
        g.drawString(font, name, x + 14, y - 10, 0xFFFFFFFF, false);
        if (!l.title.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + 14 + nw, y - 9, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, Nameplate.styled(l.title.toUpperCase(), "tech"), 0, 0, 0xFFFFD84A, true);
            g.pose().popPose();
        }
        // texto
        int tx = px + ps + 12, tw = x + w - 12 - tx;
        String vis = text.substring(0, (int) shown);
        List<FormattedCharSequence> lines = font.split(Component.literal(vis), tw);
        int ty = y + 10;
        for (int i = 0; i < Math.min(lines.size(), 4); i++) g.drawString(font, lines.get(i), tx, ty + i * 11, 0xFFFFFFFF, false);
        // respuestas
        if (!typing()) {
            ListTag o = opts();
            int oy = y + h - 10 - Math.max(1, o.size()) * 11;
            if (o.isEmpty()) {
                boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
                g.drawString(font, (blink ? "▶ " : "  ") + "Continuar", tx, y + h - 16, 0xFFCFC6FF, false);
            } else {
                for (int i = 0; i < o.size(); i++) {
                    int ly = oy + i * 11;
                    boolean hover = mx >= tx && mx < tx + tw && my >= ly - 1 && my < ly + 10;
                    if (hover) sel = i;
                    boolean on = i == sel;
                    g.drawString(font, (on ? "▶ " : "  ") + o.getCompound(i).getString("label"), tx, ly, on ? 0xFFFFD84A : 0xFFCFC6FF, false);
                }
            }
        } else {
            g.drawString(font, "…", x + w - 14, y + h - 14, 0xFF8A80B8, false);
        }
        if (!error.isEmpty() && System.currentTimeMillis() - errorTime < 2500)
            g.drawString(font, error, tx, y - 26, 0xFFFF6A5A, true);
        super.render(g, mx, my, pt);
    }

    // ---------- entrada ----------

    private void advance() {
        if (waiting) return;
        if (typing()) { shown = text.length(); return; }
        ListTag o = opts();
        if (o.isEmpty()) {
            int next = cur().contains("next") ? cur().getInt("next") : -1;
            if (next >= 0) go(next, false); else onClose();
            return;
        }
        choose(sel);
    }

    private void choose(int i) {
        ListTag o = opts();
        if (i < 0 || i >= o.size()) return;
        CompoundTag op = o.getCompound(i);
        PixelSound.tap();
        switch (op.getString("act")) {
            case "goto" -> go(op.getInt("target"), false);
            case "shop" -> {
                CompoundTag t = new CompoundTag();
                t.putInt("id", npc.getId());
                Net.toServer("npc_shop_open", t);
            }
            case "give", "pay" -> {
                waiting = true;
                CompoundTag t = new CompoundTag();
                t.putInt("id", npc.getId());
                t.putInt("node", node);
                t.putInt("opt", i);
                Net.toServer("npc_act", t);
            }
            default -> onClose();
        }
    }

    public void actionResult(boolean ok, int target, String msg) {
        waiting = false;
        if (!ok) {
            error = msg.isEmpty() ? "No se ha podido" : msg;
            errorTime = System.currentTimeMillis();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SHOP_ERROR.get(), 1f, 0.7f));
            return;
        }
        if (target >= 0 && target < nodes.size()) go(target, false); else onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) { advance(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        ListTag o = opts();
        if (!typing() && !o.isEmpty()) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) { sel = (sel + o.size() - 1) % o.size(); PixelSound.tick(); return true; }
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) { sel = (sel + 1) % o.size(); PixelSound.tick(); return true; }
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && key - GLFW.GLFW_KEY_1 < o.size()) { choose(key - GLFW.GLFW_KEY_1); return true; }
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_E) { advance(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
