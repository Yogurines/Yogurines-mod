package com.yogurines.mod.npc;

import com.yogurines.mod.item.Money;
import com.yogurines.mod.item.MoneyItem;
import com.yogurines.mod.item.WalletItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Dinero en efectivo que lleva un jugador encima (billetes, monedas y carteras). */
public final class Cash {
    public static long count(Player p) {
        long v = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof MoneyItem) v += Money.valueOf(s);
            else if (s.getItem() instanceof WalletItem) v += WalletItem.total(s);
        }
        return v;
    }

    /** Cobra una cantidad en efectivo: primero billetes sueltos, luego carteras. Devuelve el cambio. */
    public static boolean take(Player p, long amount) {
        if (amount <= 0) return true;
        if (count(p) < amount) return false;
        var inv = p.getInventory();
        long taken = 0;
        // de los billetes pequeños a los grandes, para gastar primero la calderilla
        for (int idx = 0; idx < Money.VALUES.length && taken < amount; idx++) {
            for (int i = 0; i < inv.getContainerSize() && taken < amount; i++) {
                ItemStack s = inv.getItem(i);
                if (s.getItem() instanceof MoneyItem m && m.getValue() == Money.VALUES[idx]) {
                    while (!s.isEmpty() && taken < amount) {
                        s.shrink(1);
                        taken += m.getValue();
                    }
                }
            }
        }
        for (int i = 0; i < inv.getContainerSize() && taken < amount; i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof WalletItem) {
                int[] c = WalletItem.counts(s);
                for (int idx = 0; idx < c.length && taken < amount; idx++) {
                    while (c[idx] > 0 && taken < amount) {
                        c[idx]--;
                        taken += Money.VALUES[idx];
                    }
                }
                WalletItem.setCounts(s, c);
            }
        }
        if (taken > amount) Money.give(p, taken - amount);
        inv.setChanged();
        return true;
    }

    /** Cuántas unidades de un objeto (mismo objeto y mismos datos) lleva el jugador. */
    public static int countItem(Player p, ItemStack like) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, like)) n += s.getCount();
        }
        return n;
    }

    public static void takeItem(Player p, ItemStack like, int count) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, like)) {
                int k = Math.min(count, s.getCount());
                s.shrink(k);
                count -= k;
            }
        }
        inv.setChanged();
    }

    public static void giveItem(Player p, ItemStack stack) {
        ItemStack s = stack.copy();
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private Cash() {}
}
