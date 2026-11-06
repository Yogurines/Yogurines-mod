package com.yogurines.mod.scratch;

import com.yogurines.mod.item.Money;
import com.yogurines.mod.net.Net;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Sorteo de los rascas: el servidor decide el premio con probabilidades realistas
 * (muchas veces te devuelve lo jugado, casi la mitad nada y los gordos muy difíciles; devuelven ~80%) y monta el cartón para que cuadre.
 */
public final class ScratchServer {
    public static final long[] PRICE = {1, 10, 100};
    public static final long[] MAX = {100, 2000, 50000};

    /** Premios y probabilidad (en tanto por millón) de cada nivel. */
    private static final long[][] PRIZES = {
            {1, 2, 5, 20, 100},
            {10, 20, 50, 200, 2000},
            {100, 200, 500, 2500, 10000, 50000}};
    private static final int[][] ODDS_PPM = {
            {400000, 80000, 25000, 4000, 300},
            {380000, 80000, 25000, 4000, 200},
            {370000, 80000, 25000, 4000, 600, 20}};

    /** Símbolos del rasca de oro: moneda, Y, lingote, diamante, corona, fundadores (mismo orden que PRIZES[2]). */
    public static final int SYMBOLS = 6;
    private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

    private static final Random RNG = new Random();
    private static final Map<UUID, long[]> PENDING = new HashMap<>();

    public static long roll(int tier) {
        int r = RNG.nextInt(1_000_000);
        int acc = 0;
        for (int i = 0; i < PRIZES[tier].length; i++) {
            acc += ODDS_PPM[tier][i];
            if (r < acc) return PRIZES[tier][i];
        }
        return 0;
    }

    public static void open(ServerPlayer p, int tier) {
        claim(p); // si había uno sin cobrar, se cobra antes
        long prize = roll(tier);
        CompoundTag t = new CompoundTag();
        t.putInt("tier", tier);
        t.putLong("prize", prize);
        switch (tier) {
            case 0 -> t.putIntArray("vals", bronze(prize));
            case 1 -> silver(prize, t);
            default -> t.putIntArray("sym", gold(prize));
        }
        PENDING.put(p.getUUID(), new long[]{prize});
        Net.toPlayer(p, "scr_open", t);
    }

    public static void handle(ServerPlayer p, String type, CompoundTag t) {
        if (type.equals("scr_claim")) claim(p);
    }

    public static void claim(ServerPlayer p) {
        long[] v = PENDING.remove(p.getUUID());
        if (v == null || v[0] <= 0) return;
        Money.give(p, v[0]);
        CompoundTag fx = new CompoundTag();
        fx.putString("k", "bill");
        Net.toPlayer(p, "fx", fx);
    }

    /** Tres iguales: 6 cantidades; si hay premio aparece 3 veces y ninguna otra se repite 3 veces. */
    private static int[] bronze(long prize) {
        int[] pool = {1, 2, 5, 20, 100};
        List<Integer> v = new ArrayList<>();
        if (prize > 0) for (int i = 0; i < 3; i++) v.add((int) prize);
        while (v.size() < 6) {
            int o = pool[RNG.nextInt(pool.length)];
            if (o == prize) continue;
            if (Collections.frequency(v, o) < 2) v.add(o);
        }
        Collections.shuffle(v, RNG);
        return v.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Números de la suerte: 3 ganadores y 8 tuyos con premio; si toca, coincide uno con ese premio. */
    private static void silver(long prize, CompoundTag t) {
        List<Integer> nums = new ArrayList<>();
        for (int i = 1; i <= 30; i++) nums.add(i);
        Collections.shuffle(nums, RNG);
        int[] win = {nums.get(0), nums.get(1), nums.get(2)};
        int[] mine = new int[8];
        int[] pz = new int[8];
        int k = 3;
        long[] decoy = PRIZES[1];
        for (int i = 0; i < 8; i++) {
            mine[i] = nums.get(k++);
            pz[i] = (int) decoy[RNG.nextInt(decoy.length)];
        }
        if (prize > 0) {
            int at = RNG.nextInt(8);
            mine[at] = win[RNG.nextInt(3)];
            pz[at] = (int) prize;
        }
        t.putIntArray("win", win);
        t.putIntArray("mine", mine);
        t.putIntArray("pz", pz);
    }

    /** Cámara acorazada: 3x3 símbolos; si toca, hay una línea de 3 del símbolo del premio y ninguna otra. */
    private static int[] gold(long prize) {
        int target = -1;
        for (int i = 0; i < PRIZES[2].length; i++) if (PRIZES[2][i] == prize) target = i;
        for (int attempt = 0; attempt < 500; attempt++) {
            int[] g = new int[9];
            for (int i = 0; i < 9; i++) g[i] = weightedSymbol();
            int[] line = null;
            if (target >= 0) {
                line = LINES[RNG.nextInt(LINES.length)];
                for (int c : line) g[c] = target;
            }
            if (linesIn(g) == (target >= 0 ? 1 : 0)) return g;
        }
        // por si acaso: cartón sin líneas fijo
        return target >= 0 ? new int[]{target, target, target, 1, 2, 0, 3, 0, 1} : new int[]{0, 1, 2, 1, 2, 0, 2, 0, 1};
    }

    private static int weightedSymbol() {
        int r = RNG.nextInt(100);
        if (r < 30) return 0;
        if (r < 55) return 1;
        if (r < 75) return 2;
        if (r < 88) return 3;
        if (r < 96) return 4;
        return 5;
    }

    private static int linesIn(int[] g) {
        int n = 0;
        for (int[] l : LINES) if (g[l[0]] == g[l[1]] && g[l[1]] == g[l[2]]) n++;
        return n;
    }

    public static void logout(ServerPlayer p) {
        claim(p);
    }

    private ScratchServer() {}
}
