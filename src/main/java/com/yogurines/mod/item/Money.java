package com.yogurines.mod.item;

import com.yogurines.mod.registry.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Utilidades de dinero: valores, desglose en billetes y recuento del inventario. */
public final class Money {
    /** Valores de menor a mayor (índice 0 = moneda de 1). */
    public static final int[] VALUES = {1, 5, 10, 20, 50, 100, 200, 500};

    public static Item item(int index) {
        return ModItems.MONEY.get(index).get();
    }

    public static int indexOf(int value) {
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i] == value) return i;
        return -1;
    }

    public static int valueOf(ItemStack stack) {
        return stack.getItem() instanceof MoneyItem m ? m.getValue() * stack.getCount() : 0;
    }

    /** Desglosa una cantidad en billetes y monedas, empezando por el billete más grande. */
    public static int[] breakdown(long amount) {
        int[] out = new int[VALUES.length];
        for (int i = VALUES.length - 1; i >= 0 && amount > 0; i--) {
            out[i] = (int) (amount / VALUES[i]);
            amount -= (long) out[i] * VALUES[i];
        }
        return out;
    }

    /** Da al jugador los billetes de una cantidad (lo que no quepa cae al suelo). */
    public static void give(Player player, long amount) {
        int[] parts = breakdown(amount);
        for (int i = 0; i < parts.length; i++) {
            int n = parts[i];
            while (n > 0) {
                int c = Math.min(64, n);
                ItemStack s = new ItemStack(item(i), c);
                if (!player.getInventory().add(s)) player.drop(s, false);
                n -= c;
            }
        }
    }

    public static String fmt(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append('.');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    private Money() {}
}
