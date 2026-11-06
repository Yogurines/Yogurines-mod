package com.yogurines.mod.scratch.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.yogurines.mod.Yogurines;
import com.yogurines.mod.client.PixelScreen;
import com.yogurines.mod.item.Money;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * Rasca y gana: arrastras una moneda por el cartón y se va rascando. Cuando has rascado algo más
 * de un tercio de una casilla se destapa entera sola, para que no sea pesado.
 */
public class ScratchScreen extends PixelScreen {
    private static final ResourceLocation SYMBOLS = new ResourceLocation(Yogurines.MOD_ID, "textures/gui/scratch_symbols.png");
    private static final ResourceLocation COIN = new ResourceLocation(Yogurines.MOD_ID, "textures/item/coin_1.png");
    private static final ResourceLocation OVERLAY = new ResourceLocation(Yogurines.MOD_ID, "scratch_overlay");
    private static final String[] NAMES = {"BRONCE", "PLATA", "ORO"};
    private static final long[] GOLD_PRIZES = {100, 200, 500, 2500, 10000, 50000};
    private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

    private final int tier;
    private final long prize;
    private final int[][] zones;
    private final int[] vals, win, mine, pz, sym;
    private final int[] left, total;
    private final boolean[] done;
    private final boolean[] highlight;
    private DynamicTexture overlay;
    private boolean finished, claimed;
    private long finishTime;
    private int lastX = Integer.MIN_VALUE, lastY;
    private long lastScratch;
    private SoundInstance cur;
    private long curStart;
    private int mx = -100, my = -100;

    public static void handle(String type, CompoundTag t) {
        if (type.equals("scr_open")) Minecraft.getInstance().setScreen(new ScratchScreen(t));
    }

    public ScratchScreen(CompoundTag t) {
        super("Rasca y gana");
        tier = Math.max(0, Math.min(2, t.getInt("tier")));
        prize = t.getLong("prize");
        vals = t.getIntArray("vals");
        win = t.getIntArray("win");
        mine = t.getIntArray("mine");
        pz = t.getIntArray("pz");
        sym = t.getIntArray("sym");
        zones = switch (tier) {
            case 0 -> grid(11, 92, 44, 40, 40, 36, 3, 2);
            case 1 -> concat(grid(30, 72, 32, 0, 26, 26, 3, 1), grid(9, 116, 34, 36, 31, 32, 4, 2));
            default -> grid(24, 76, 36, 33, 34, 30, 3, 3);
        };
        left = new int[zones.length];
        total = new int[zones.length];
        done = new boolean[zones.length];
        highlight = new boolean[zones.length];
        for (int i = 0; i < zones.length; i++) { total[i] = zones[i][2] * zones[i][3]; left[i] = total[i]; }
    }

    private static int[][] grid(int x0, int y0, int dx, int dy, int w, int h, int cols, int rows) {
        int[][] z = new int[cols * rows][];
        for (int j = 0; j < rows; j++) for (int i = 0; i < cols; i++) z[j * cols + i] = new int[]{x0 + i * dx, y0 + j * dy, w, h};
        return z;
    }

    private static int[][] concat(int[][] a, int[][] b) {
        int[][] r = new int[a.length + b.length][];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    @Override protected int vw() { return 246; }
    @Override protected int vh() { return 204; }
    @Override protected float heightFraction() { return 0.9f; }

    @Override
    protected void init() {
        super.init();
        if (overlay == null) {
            NativeImage img = new NativeImage(150, 200, true);
            img.fillRect(0, 0, 150, 200, 0);
            for (int[] z : zones) {
                for (int y = 0; y < z[3]; y++) for (int x = 0; x < z[2]; x++) {
                    int v = (((x >> 1) + (y >> 1)) % 4 < 2) ? 198 : 178;
                    if (y == 0) v = 230;
                    if (y == z[3] - 1) v = 150;
                    img.setPixelRGBA(z[0] + x, z[1] + y, 0xFF000000 | (v + 6) << 16 | v << 8 | v);
                }
            }
            overlay = new DynamicTexture(img);
            Minecraft.getInstance().getTextureManager().register(OVERLAY, overlay);
        }
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public void removed() {
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        if (cur != null) Minecraft.getInstance().getSoundManager().stop(cur);
        Minecraft.getInstance().getTextureManager().release(OVERLAY);
        overlay = null;
        if (!claimed) { claimed = true; Net.toServer("scr_claim", new CompoundTag()); }
        super.removed();
    }

    // ---------- dibujo ----------

    @Override
    protected void draw(int mouseX, int mouseY, float pt) {
        mx = mouseX; my = mouseY;
        // sombra y cartón
        rect(3, 4, 150, 200, 0x66000000);
        tex(new ResourceLocation(Yogurines.MOD_ID, "textures/gui/scratch_" + tier + ".png"), 0, 0, 150, 200, 150, 200);
        // contenido de las casillas (debajo del plateado)
        for (int i = 0; i < zones.length; i++) contents(i);
        if (overlay != null) g.blit(OVERLAY, 0, 0, 150, 200, 0, 0, 150, 200, 150, 200);
        if (finished) for (int i = 0; i < zones.length; i++) if (highlight[i]) {
            int[] z = zones[i];
            outline(z[0] - 1, z[1] - 1, z[2] + 2, z[3] + 2, ((System.currentTimeMillis() / 250) % 2 == 0) ? 0xFFFFE14A : 0xFF3FD13A);
        }
        side();
        // moneda en el ratón
        tex(COIN, mx - 8, my - 8, 16, 16, 16, 16);
        scratchSound();
    }

    private void contents(int i) {
        int[] z = zones[i];
        int cx = z[0] + z[2] / 2, cy = z[1] + z[3] / 2;
        int ink = 0xFF5A2A08;
        switch (tier) {
            case 0 -> {
                if (i < vals.length) textScaled("Y" + Money.fmt(vals[i]), cx, cy - 6, 1.4f, ink, true);
            }
            case 1 -> {
                if (i < 3) { if (i < win.length) textScaled(String.valueOf(win[i]), cx, cy - 6, 1.4f, 0xFF1F3550, true); }
                else {
                    int k = i - 3;
                    if (k < mine.length) textScaled(String.valueOf(mine[k]), cx, z[1] + 5, 1.3f, 0xFF1F3550, true);
                    if (k < pz.length) textScaled("Y" + Money.fmt(pz[k]), cx, z[1] + z[3] - 9, 0.75f, 0xFF3B4A60, true);
                }
            }
            default -> {
                if (i < sym.length) g.blit(SYMBOLS, cx - 8, cy - 8, 16, 16, sym[i] * 16, 0, 16, 16, 96, 16);
            }
        }
    }

    private void outline(int x, int y, int w, int h, int c) {
        rect(x, y, w, 1, c); rect(x, y + h - 1, w, 1, c); rect(x, y, 1, h, c); rect(x + w - 1, y, 1, h, c);
    }

    private int sx() { return 158; }

    private void side() {
        int x = sx(), w = 88;
        round(x, 0, w, 204, 4, 0xEE1A1C21);
        center("RASCA " + NAMES[tier], x + w / 2, 8, 0xFFFFD84A);
        int y = 24;
        switch (tier) {
            case 0 -> { y = para("Si salen 3 cantidades iguales, ganas esa cantidad.", x + 6, y, w - 12); }
            case 1 -> { y = para("Cada número tuyo que esté entre los ganadores te da el premio que tiene debajo.", x + 6, y, w - 12); }
            default -> {
                y = para("3 símbolos iguales en raya (también en diagonal):", x + 6, y, w - 12) + 2;
                for (int s = 5; s >= 0; s--) {
                    g.blit(SYMBOLS, x + 8, y, 10, 10, s * 16, 0, 16, 16, 96, 16);
                    textScaled("Y" + Money.fmt(GOLD_PRIZES[s]), x + 22, y + 2, 0.8f, 0xFFE0E0E0, false);
                    y += 12;
                }
            }
        }
        if (finished) {
            int ry = 140;
            if (prize > 0) {
                center("¡HAS GANADO!", x + w / 2, ry, 0xFF7FD36A);
                textScaled("Y" + Money.fmt(prize), x + w / 2f, ry + 12, 1.6f, 0xFFFFD84A, true);
            } else {
                center("Esta vez", x + w / 2, ry, 0xFFBBBBBB);
                center("no ha tocado…", x + w / 2, ry + 11, 0xFFBBBBBB);
            }
            boolean hover = in(mx, my, x + 6, 180, w - 12, 16);
            round(x + 6, 180, w - 12, 16, 3, hover ? 0xFF4CB43A : 0xFF3F9A2E);
            center(prize > 0 ? "RECLAMAR" : "CERRAR", x + w / 2, 184, 0xFFFFFFFF);
        } else {
            int n = 0;
            for (boolean d : done) if (!d) n++;
            center("Quedan " + n, x + w / 2, 186, 0xFF8A8A8A);
        }
    }

    private int para(String s, int x, int y, int w) {
        for (var line : font.split(net.minecraft.network.chat.Component.literal(s), (int) (w / 0.8f))) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(0.8f, 0.8f, 1);
            g.drawString(font, line, 0, 0, 0xFFD0D0D0, false);
            g.pose().popPose();
            y += 8;
        }
        return y;
    }

    // ---------- rascar ----------

    private void scratchAt(int x, int y) {
        if (overlay == null || finished) return;
        NativeImage img = overlay.getPixels();
        if (img == null) return;
        boolean changed = false;
        for (int i = 0; i < zones.length; i++) {
            if (done[i]) continue;
            int[] z = zones[i];
            if (x < z[0] - 7 || y < z[1] - 7 || x > z[0] + z[2] + 7 || y > z[1] + z[3] + 7) continue;
            for (int dy = -6; dy <= 6; dy++) for (int dx = -6; dx <= 6; dx++) {
                if (dx * dx + dy * dy > 38) continue;
                int px = x + dx, py = y + dy;
                if (px < z[0] || py < z[1] || px >= z[0] + z[2] || py >= z[1] + z[3]) continue;
                if ((img.getPixelRGBA(px, py) >>> 24) != 0) {
                    img.setPixelRGBA(px, py, 0);
                    left[i]--;
                    changed = true;
                }
            }
            if (left[i] < total[i] * 0.62f) {
                for (int yy = 0; yy < z[3]; yy++) for (int xx = 0; xx < z[2]; xx++) img.setPixelRGBA(z[0] + xx, z[1] + yy, 0);
                done[i] = true;
                left[i] = 0;
                changed = true;
            }
        }
        if (changed) {
            overlay.upload();
            lastScratch = System.currentTimeMillis();
            checkFinished();
        }
    }

    /** Rasca a lo largo del trazo para que no queden huecos si mueves rápido el ratón. */
    private void strokeTo(int x, int y) {
        if (lastX == Integer.MIN_VALUE) { scratchAt(x, y); }
        else {
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(x - lastX, y - lastY) / 3));
            for (int s = 1; s <= steps; s++) scratchAt(lastX + (x - lastX) * s / steps, lastY + (y - lastY) * s / steps);
        }
        lastX = x; lastY = y;
    }

    private void checkFinished() {
        for (boolean d : done) if (!d) return;
        finished = true;
        finishTime = System.currentTimeMillis();
        markWinners();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(prize > 0 ? ModSounds.SCRATCH_WIN.get() : ModSounds.SCRATCH_LOSE.get(), 1f, 0.8f));
    }

    private void markWinners() {
        if (prize <= 0) return;
        switch (tier) {
            case 0 -> { for (int i = 0; i < vals.length; i++) highlight[i] = vals[i] == prize; }
            case 1 -> {
                for (int k = 0; k < mine.length; k++)
                    for (int w : win) if (mine[k] == w) { highlight[3 + k] = true; for (int j = 0; j < 3; j++) if (win[j] == w) highlight[j] = true; }
            }
            default -> {
                for (int[] l : LINES) if (sym.length == 9 && sym[l[0]] == sym[l[1]] && sym[l[1]] == sym[l[2]]) for (int c : l) highlight[c] = true;
            }
        }
    }

    private void scratchSound() {
        long now = System.currentTimeMillis();
        var sm = Minecraft.getInstance().getSoundManager();
        if (now - lastScratch < 130) {
            // suena seguido: cada trozo empieza antes de que acabe el anterior y con tono y variante distintos
            if (cur == null || now - curStart > 360) {
                float pitch = 0.9f + (float) Math.random() * 0.2f;
                cur = SimpleSoundInstance.forUI(ModSounds.SCRATCH.get(), pitch, 0.5f);
                sm.play(cur);
                curStart = now;
            }
        } else if (cur != null && now - lastScratch > 170) {
            sm.stop(cur);
            cur = null;
        }
    }

    // ---------- entrada ----------

    @Override
    public boolean mouseClicked(double gx, double gy, int button) {
        int x = vx(gx), y = vy(gy);
        if (finished && in(x, y, sx() + 6, 180, 76, 16)) {
            if (!claimed) { claimed = true; Net.toServer("scr_claim", new CompoundTag()); }
            onClose();
            return true;
        }
        if (button == 0) { lastX = Integer.MIN_VALUE; strokeTo(x, y); }
        return true;
    }

    @Override
    public boolean mouseDragged(double gx, double gy, int button, double dx, double dy) {
        if (button == 0) strokeTo(vx(gx), vy(gy));
        return true;
    }

    @Override
    public boolean mouseReleased(double gx, double gy, int button) {
        lastX = Integer.MIN_VALUE;
        return true;
    }
}
