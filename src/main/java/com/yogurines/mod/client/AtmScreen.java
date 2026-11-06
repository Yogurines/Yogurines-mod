package com.yogurines.mod.client;

import com.yogurines.mod.item.Money;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import org.lwjgl.glfw.GLFW;

/** Interfaz del cajero: saldo, teclado para retirar y botón de ingresar todo. */
public class AtmScreen extends PixelScreen {
    private static String flashText = "";
    private static long flashTime;
    private String amount = "";

    public AtmScreen() {
        super("Cajero");
    }

    public static void flash(String t) {
        flashText = t;
        flashTime = System.currentTimeMillis();
    }

    @Override protected int vw() { return 160; }
    @Override protected int vh() { return 230; }
    @Override protected float heightFraction() { return 0.8f; }

    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};
    private static final int[] QUICK = {10, 20, 50, 100};

    private int[] key(int i) {
        return new int[]{22 + (i % 3) * 40, 120 + (i / 3) * 22};
    }

    @Override
    protected void draw(int mx, int my, float pt) {
        // carcasa de acero
        round(0, 0, 160, 230, 6, 0xFF1E2328);
        round(2, 2, 156, 226, 5, 0xFF8C959E);
        gradient(4, 4, 152, 222, 0xFFB7C0C8, 0xFF7E8790);
        // franja verde con el nombre
        round(8, 8, 144, 16, 3, 0xFF1F8A4C);
        center("BANCO YOGURÍN", 80, 12, 0xFFFFFFFF);
        // pantalla
        round(12, 30, 136, 72, 3, 0xFF0D1410);
        rect(14, 32, 132, 68, 0xFF10261A);
        textScaled("SALDO", 20, 37, 0.5f, 0xFF7FE0A4, false);
        String bal = Money.fmt(ClientData.balance) + " Y";
        textScaled(bal, 80, 44, bal.length() > 10 ? 1f : 1.5f, 0xFFB8FFD1, true);
        rect(20, 60, 120, 1, 0x553FD17A);
        textScaled("RETIRAR", 20, 64, 0.5f, 0xFF7FE0A4, false);
        String a = (amount.isEmpty() ? "0" : Money.fmt(Long.parseLong(amount))) + " Y";
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        text(a + (blink ? "_" : ""), 20, 71, 0xFFFFFFFF);
        long now = System.currentTimeMillis();
        String msg = "";
        int mc = 0xFF7FE0A4;
        if (now - ClientData.bankErrorTime < 2500) { msg = ClientData.bankError; mc = 0xFFFF7A7A; }
        else if (now - flashTime < 2500) msg = flashText;
        if (!msg.isEmpty()) textScaled(fit(msg, 240), 80, 88, 0.5f, mc, true);
        else textScaled("Teclea una cantidad y pulsa OK", 80, 88, 0.5f, 0xFF4E9A6C, true);
        // cantidades rápidas
        for (int i = 0; i < 4; i++) {
            int x = 12 + i * 35, y = 106;
            boolean h = in(mx, my, x, y, 32, 11);
            round(x, y, 32, 11, 2, h ? 0xFF2BA35E : 0xFF1F8A4C);
            textScaled(QUICK[i] + " Y", x + 16, y + 3, 0.5f, 0xFFFFFFFF, true);
        }
        // teclado
        for (int i = 0; i < KEYS.length; i++) {
            int[] k = key(i);
            boolean h = in(mx, my, k[0], k[1], 36, 19);
            int col = i == 9 ? 0xFFC9A227 : i == 11 ? 0xFF1F8A4C : 0xFF3A4048;
            if (h) col = brighten(col);
            round(k[0], k[1] + 1, 36, 19, 3, 0xFF1A1D21);
            round(k[0], k[1], 36, 18, 3, col);
            center(KEYS[i], k[0] + 18, k[1] + 5, 0xFFFFFFFF);
        }
        // ingresar todo
        boolean hd = in(mx, my, 12, 210, 136, 14);
        round(12, 210, 136, 14, 3, hd ? 0xFF2BA35E : 0xFF1F8A4C);
        center("Ingresar todo mi dinero", 80, 213, 0xFFFFFFFF);
        if (ClientData.synced && !ClientData.users.isEmpty()) return;
        textScaled("Conectando…", 80, 52, 0.5f, 0xFF7FE0A4, true);
    }

    private static int brighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 30), g = Math.min(255, ((c >> 8) & 255) + 30), b = Math.min(255, (c & 255) + 30);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void press(String k) {
        sound(ModSounds.ATM_KEY.get(), 1f, 0.8f);
        switch (k) {
            case "C" -> amount = "";
            case "OK" -> {
                if (amount.isEmpty()) return;
                CompoundTag o = new CompoundTag();
                o.putLong("v", Long.parseLong(amount));
                Net.toServer("atm_take", o);
                amount = "";
            }
            default -> {
                if (amount.length() < 6 && !(amount.isEmpty() && k.equals("0"))) amount += k;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = vx(mouseX), my = vy(mouseY);
        for (int i = 0; i < KEYS.length; i++) {
            int[] k = key(i);
            if (in(mx, my, k[0], k[1], 36, 19)) { press(KEYS[i]); return true; }
        }
        for (int i = 0; i < 4; i++) {
            if (in(mx, my, 12 + i * 35, 106, 32, 11)) {
                sound(ModSounds.ATM_KEY.get(), 1f, 0.8f);
                amount = Integer.toString(QUICK[i]);
                return true;
            }
        }
        if (in(mx, my, 12, 210, 136, 14)) {
            sound(ModSounds.ATM_KEY.get(), 1f, 0.8f);
            Net.toServer("atm_all", new CompoundTag());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) { press(Integer.toString(key - GLFW.GLFW_KEY_0)); return true; }
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) { press(Integer.toString(key - GLFW.GLFW_KEY_KP_0)); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!amount.isEmpty()) amount = amount.substring(0, amount.length() - 1);
            sound(ModSounds.ATM_KEY.get(), 1f, 0.8f);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { press("OK"); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
