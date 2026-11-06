package com.yogurines.mod.npc.client;

import com.yogurines.mod.item.Money;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.npc.NpcEntity;
import com.yogurines.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Tienda holográfica: verde neón sobre negro, con interruptor de pago efectivo / banco. */
public class NpcShopScreen extends Screen {
    private static boolean useBank;
    private static final int G = 0xFF39FF7A, G2 = 0xFF8DFFB0, DIM = 0xFF1D6B39;

    private final NpcEntity npc;
    private ListTag offers;
    private long cash, bank;
    private String name;
    private int sel, scroll;
    private String msg = "";
    private boolean msgOk;
    private long msgTime;
    private int age;
    private ItemStack hoverStack = ItemStack.EMPTY;

    public NpcShopScreen(NpcEntity npc, CompoundTag t) {
        super(Component.literal(T.t("shop.title")));
        this.npc = npc;
        update(t);
        msg = "";
    }

    public int npcId() { return npc.getId(); }

    public void update(CompoundTag t) {
        offers = t.getList("offers", Tag.TAG_COMPOUND);
        cash = t.getLong("cash");
        bank = t.getLong("bank");
        name = t.getString("name");
        if (sel >= offers.size()) sel = Math.max(0, offers.size() - 1);
        String m = t.getString("msg");
        if (!m.isEmpty()) {
            msg = T.t(m, t.getString("arg"));
            msgOk = t.getBoolean("ok");
            msgTime = System.currentTimeMillis();
            PixelSound.play(msgOk ? ModSounds.SHOP_BUY.get() : ModSounds.SHOP_ERROR.get(), 1f, 0.8f);
        }
    }

    @Override
    protected void init() {
        if (age == 0) PixelSound.play(ModSounds.SHOP_OPEN.get(), 1f, 0.7f);
    }

    @Override
    public void tick() {
        age++;
        if (npc.isRemoved() || npc.distanceToSqr(Minecraft.getInstance().player) > 12 * 12) onClose();
    }

    private int pw() { return Math.min(width - 20, 330); }
    private int ph() { return Math.min(height - 20, 210); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int rowsTop() { return py() + 30; }
    private int rowH() { return 21; }
    private int visibleRows() { return Math.max(1, (ph() - 30 - 34) / rowH()); }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x88000A04);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hoverStack = ItemStack.EMPTY;
        int x = px(), y = py(), w = pw(), h = ph();
        // aparición con parpadeo de holograma
        boolean flick = age < 6 && (age % 2 == 0);
        int bgA = flick ? 0x55 : 0xE6;
        for (int i = 3; i >= 1; i--) g.fill(x - i, y - i, x + w + i, y + h + i, ((0x18 / i) << 24) | 0x39FF7A);
        g.fill(x, y, x + w, y + h, (bgA << 24) | 0x03120A);
        for (int ly = y + 1; ly < y + h; ly += 3) g.fill(x + 1, ly, x + w - 1, ly + 1, 0x0F39FF7A);
        border(g, x, y, w, h, G);
        // cabecera
        String title = "> " + (name.isEmpty() ? "NPC" : name).toUpperCase(Locale.ROOT).replace(' ', '_') + ".exe";
        g.drawString(font, Nameplate.styled(title, "tech"), x + 8, y + 8, G2, false);
        String bal = T.t(useBank ? "shop.bank" : "shop.cash").toUpperCase(Locale.ROOT) + " Y" + Money.fmt(useBank ? bank : cash);
        Component balC = Nameplate.styled(bal, "tech");
        g.drawString(font, balC, x + w - 8 - font.width(balC), y + 8, G2, false);
        for (int dx = x + 6; dx < x + w - 6; dx += 4) g.fill(dx, y + 21, dx + 2, y + 22, 0x8839FF7A);

        // ofertas
        int vis = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, offers.size() - vis)));
        if (offers.isEmpty()) centered(g, Component.literal(T.t("shop.empty")), x + w / 2, rowsTop() + 30, G2);
        for (int r = 0; r < vis && r + scroll < offers.size(); r++) {
            int i = r + scroll;
            CompoundTag o = offers.getCompound(i);
            int ry = rowsTop() + r * rowH();
            boolean on = i == sel;
            boolean hover = mx >= x + 6 && mx < x + w - 6 && my >= ry && my < ry + rowH() - 2;
            if (on) g.fill(x + 6, ry, x + w - 6, ry + rowH() - 2, 0x2239FF7A);
            border(g, x + 6, ry, w - 12, rowH() - 2, on ? G : (hover ? 0xFF2FA85A : DIM));
            ItemStack it = ItemStack.of(o.getCompound("item"));
            g.renderItem(it, x + 9, ry + 1);
            g.renderItemDecorations(font, it, x + 9, ry + 1);
            if (in(mx, my, x + 9, ry + 1, 16, 16)) hoverStack = it;
            boolean sell = !"buy".equals(o.getString("mode"));
            String label = it.getHoverName().getString();
            int maxLabel = w - 150;
            if (font.width(label) > maxLabel) label = font.plainSubstrByWidth(label, maxLabel - 6) + "…";
            g.drawString(font, label, x + 30, ry + 2, 0xFFCFFFE0, false);
            g.drawString(font, Nameplate.styled(T.t(sell ? "shop.buy" : "shop.sell").toUpperCase(Locale.ROOT), "tech"), x + 30, ry + 11, sell ? 0xFF39FF7A : 0xFFFFD84A, false);
            // precio y stock
            int max = o.getInt("max"), stock = o.getInt("stock"), left = o.getInt("left");
            String st = max < 0 ? "[∞]" : "[" + stock + "]";
            if (left >= 0) st += " L" + left;
            int rx = x + w - 12;
            g.drawString(font, st, rx - font.width(st), ry + 6, max >= 0 && stock <= 0 ? 0xFFFF6A5A : 0xFF6FCF8F, false);
            rx -= font.width(st) + 8;
            if (o.getBoolean("payY")) {
                String pr = (sell ? "" : "+") + "Y" + Money.fmt(o.getLong("price"));
                g.drawString(font, pr, rx - font.width(pr), ry + 6, G2, false);
            } else {
                ItemStack pi = ItemStack.of(o.getCompound("priceItem"));
                g.renderItem(pi, rx - 16, ry + 1);
                g.renderItemDecorations(font, pi, rx - 16, ry + 1);
                if (in(mx, my, rx - 16, ry + 1, 16, 16)) hoverStack = pi;
            }
        }
        if (offers.size() > vis) {
            int barH = vis * rowH();
            int th = Math.max(10, barH * vis / offers.size());
            int ty = rowsTop() + (barH - th) * scroll / Math.max(1, offers.size() - vis);
            g.fill(x + w - 4, ty, x + w - 2, ty + th, G);
        }

        // pie: interruptor y botón
        int fy = y + h - 24;
        g.drawString(font, Nameplate.styled(T.t("shop.exit").toUpperCase(Locale.ROOT), "tech"), x + 8, fy + 6, 0xFF6FCF8F, false);
        int tx = x + 78, tw1 = 56, tw2 = 50;
        boolean showPay = !offers.isEmpty() && offers.getCompound(sel).getBoolean("payY");
        if (showPay) {
            border(g, tx, fy, tw1 + tw2, 18, G);
            g.fill(useBank ? tx + tw1 : tx + 1, fy + 1, useBank ? tx + tw1 + tw2 - 1 : tx + tw1, fy + 17, G);
            centered(g, Nameplate.styled(T.t("shop.cash").toUpperCase(Locale.ROOT), "tech"), tx + tw1 / 2, fy + 6, useBank ? G2 : 0xFF04140A);
            centered(g, Nameplate.styled(T.t("shop.bank").toUpperCase(Locale.ROOT), "tech"), tx + tw1 + tw2 / 2, fy + 6, useBank ? 0xFF04140A : G2);
        }
        boolean sell = offers.isEmpty() || !"buy".equals(offers.getCompound(sel).getString("mode"));
        Component btn = Nameplate.styled(T.t(sell ? "shop.doBuy" : "shop.doSell").toUpperCase(Locale.ROOT), "tech");
        int bw = font.width(btn) + 16, bx = x + w - 8 - bw;
        boolean bh = in(mx, my, bx, fy, bw, 18);
        if (bh) g.fill(bx, fy, bx + bw, fy + 18, 0x3339FF7A);
        border(g, bx, fy, bw, 18, G);
        g.drawString(font, btn, bx + 8, fy + 6, G, false);
        if (!msg.isEmpty() && System.currentTimeMillis() - msgTime < 2600) {
            Component mc = Nameplate.styled(msg.toUpperCase(Locale.ROOT), "tech");
            centered(g, mc, x + w / 2, fy - 11, msgOk ? G : 0xFFFF6A5A);
        }
        super.render(g, mx, my, pt);
        if (!hoverStack.isEmpty()) g.renderTooltip(font, hoverStack, mx, my);
    }

    private void centered(GuiGraphics g, Component c, int cx, int y, int color) {
        g.drawString(font, c, cx - font.width(c) / 2, y, color, false);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ---------- entrada ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = px(), y = py(), w = pw(), h = ph();
        int vis = visibleRows();
        for (int r = 0; r < vis && r + scroll < offers.size(); r++) {
            int ry = rowsTop() + r * rowH();
            if (in(mx, my, x + 6, ry, w - 12, rowH() - 2)) {
                if (sel != r + scroll) PixelSound.tap();
                sel = r + scroll;
                return true;
            }
        }
        int fy = y + h - 24;
        int tx = x + 78;
        if (in(mx, my, tx, fy, 106, 18)) {
            useBank = mx >= tx + 56;
            PixelSound.tap();
            return true;
        }
        if (!offers.isEmpty()) {
            boolean sell = !"buy".equals(offers.getCompound(sel).getString("mode"));
            int bw = font.width(Nameplate.styled(T.t(sell ? "shop.doBuy" : "shop.doSell").toUpperCase(Locale.ROOT), "tech")) + 16;
            if (in(mx, my, x + w - 8 - bw, fy, bw, 18)) { buy(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void buy() {
        if (offers.isEmpty()) return;
        CompoundTag t = new CompoundTag();
        t.putInt("id", npc.getId());
        t.putInt("i", sel);
        t.putBoolean("bank", useBank);
        Net.toServer("npc_buy", t);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!offers.isEmpty()) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) { sel = (sel + offers.size() - 1) % offers.size(); ensureVisible(); return true; }
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) { sel = (sel + 1) % offers.size(); ensureVisible(); return true; }
            if (key == GLFW.GLFW_KEY_ENTER) { buy(); return true; }
        }
        if (key == GLFW.GLFW_KEY_TAB) { useBank = !useBank; PixelSound.tap(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private void ensureVisible() {
        int vis = visibleRows();
        if (sel < scroll) scroll = sel;
        if (sel >= scroll + vis) scroll = sel - vis + 1;
        PixelSound.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
