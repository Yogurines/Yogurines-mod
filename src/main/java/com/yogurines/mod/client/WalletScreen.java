package com.yogurines.mod.client;

import com.yogurines.mod.Yogurines;
import com.yogurines.mod.item.Money;
import com.yogurines.mod.item.WalletItem;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Cartera de cuero abierta (diseño A): bolsillos con cada billete, monedero y total. */
public class WalletScreen extends PixelScreen {
    private static final ResourceLocation BG = rl("textures/gui/wallet_bg.png");
    private static final ResourceLocation POCKET = rl("textures/gui/wallet_pocket.png");
    private static final ResourceLocation FLAP = rl("textures/gui/wallet_coinflap.png");
    private static final ResourceLocation COIN = rl("textures/item/coin_1.png");
    private final InteractionHand hand;
    private boolean opened;

    public WalletScreen(InteractionHand hand) {
        super(L.t("Cartera"));
        this.hand = hand;
    }

    private static ResourceLocation rl(String p) {
        return new ResourceLocation(Yogurines.MOD_ID, p);
    }

    @Override protected int vw() { return 200; }
    @Override protected int vh() { return 146; }
    @Override protected float heightFraction() { return 0.6f; }

    @Override
    protected void init() {
        if (!opened) {
            opened = true;
            sound(ModSounds.WALLET_OPEN.get(), 1f, 0.9f);
        }
    }

    @Override
    public void removed() {
        sound(ModSounds.WALLET_CLOSE.get(), 1f, 0.9f);
    }

    private ItemStack wallet() {
        var p = Minecraft.getInstance().player;
        return p == null ? ItemStack.EMPTY : p.getItemInHand(hand);
    }

    private static int[] pocket(int i) {
        return new int[]{10 + (i % 2) * 45, 18 + (i / 2) * 24};
    }

    @Override
    protected void draw(int mx, int my, float pt) {
        ItemStack w = wallet();
        if (!(w.getItem() instanceof WalletItem)) { onClose(); return; }
        int[] c = WalletItem.counts(w);
        tex(BG, 0, 0, 200, 120, 200, 120);
        textScaled(L.t("BILLETES"), 10, 9, 0.5f, 0xFFE9D3B0, false);
        textScaled(L.t("MONEDAS"), 110, 9, 0.5f, 0xFFE9D3B0, false);
        for (int i = 0; i < 7; i++) {
            int[] p = pocket(i);
            int n = c[i + 1];
            boolean hover = in(mx, my, p[0], p[1], 42, 21);
            if (n > 0) {
                int lift = hover ? 2 : 0;
                tex(NoteTooltipComponent.texture(Money.VALUES[i + 1]), p[0] + 2, p[1] + 1 - lift, 38, 19, 256, 128);
            } else {
                rect(p[0] + 3, p[1] + 3, 36, 9, 0x33000000);
            }
            tex(POCKET, p[0], p[1] + 12, 42, 9, 42, 9);
            String label = Money.VALUES[i + 1] + " Y";
            textScaled(label, p[0] + 3, p[1] + 14.5f, 0.5f, 0xFFF4E4C8, false);
            String cnt = "x" + n;
            textScaled(cnt, p[0] + 39 - font.width(cnt) * 0.5f, p[1] + 14.5f, 0.5f, n > 0 ? 0xFFFFE08A : 0x99C9B497, false);
        }
        // monedero
        boolean hoverCoin = in(mx, my, 110, 18, 78, 44);
        for (int k = 0; k < Math.min(c[0], 6); k++) {
            tex(COIN, 118 + k * 9, 22 + (k % 2) * 2 - (hoverCoin ? 1 : 0), 16, 16, 16, 16);
        }
        if (c[0] == 0) textScaled(L.t("vacío"), 149, 28, 0.5f, 0x99C9B497, true);
        tex(FLAP, 110, 40, 78, 18, 78, 18);
        textScaled(L.t("Monedas de 1 Y"), 115, 46, 0.5f, 0xFFF4E4C8, false);
        String cc = "x" + c[0];
        textScaled(cc, 183 - font.width(cc) * 0.5f, 46, 0.5f, c[0] > 0 ? 0xFFFFE08A : 0x99C9B497, false);
        // total
        textScaled(L.t("TOTAL"), 114, 74, 0.5f, 0xFFE9D3B0, false);
        String total = Money.fmt(WalletItem.total(w)) + " Y";
        textScaled(total, 149, 84, total.length() > 9 ? 0.75f : 1f, 0xFFFFE08A, true);
        // botones bajo la cartera
        boolean hs = in(mx, my, 40, 124, 120, 14);
        round(40, 124, 120, 14, 3, hs ? 0xFF7A4A28 : 0xFF5E3820);
        center(L.t("Guardar todo"), 100, 127, 0xFFF4E4C8);
        textScaled(L.t("Clic: sacar 1   ·   Mayús + clic: sacar todos"), 100, 140, 0.5f, 0xFFD8D8D8, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = vx(mouseX), my = vy(mouseY);
        if (in(mx, my, 40, 124, 120, 14)) {
            CompoundTag o = new CompoundTag();
            o.putBoolean("off", hand == InteractionHand.OFF_HAND);
            Net.toServer("wal_store", o);
            return true;
        }
        int idx = -1;
        for (int i = 0; i < 7; i++) {
            int[] p = pocket(i);
            if (in(mx, my, p[0], p[1], 42, 21)) idx = i + 1;
        }
        if (in(mx, my, 110, 18, 78, 44)) idx = 0;
        if (idx >= 0) {
            CompoundTag o = new CompoundTag();
            o.putBoolean("off", hand == InteractionHand.OFF_HAND);
            o.putInt("i", idx);
            o.putBoolean("all", hasShiftDown());
            Net.toServer("wal_take", o);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
