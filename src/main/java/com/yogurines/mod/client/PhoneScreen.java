package com.yogurines.mod.client;

import com.yogurines.mod.item.Money;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.registry.ModSounds;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** El móvil: pantalla de inicio con reloj del juego y todas las apps. */
public class PhoneScreen extends PixelScreen {
    public enum Page { HOME, GALLERY, PHOTO, PICK_PHOTO, YOGUGRAM, POST, PROFILE, EDIT_PROFILE, CROP, NEW_POST, CHATS, CHAT, BANK, TRANSFER, SETTINGS }

    // medidas virtuales
    private static final int W = 180, H = 340;
    private static final int SX = 7, SY = 7, SW = 166, SH = 326;
    private static final int TOP = SY + 14;            // debajo de la barra de estado
    private static final int BOTTOM = SY + SH - 12;    // encima de la barra de inicio

    // colores
    private static final int BG = 0xFFF5F6F8, CARD = 0xFFFFFFFF, INK = 0xFF16181D, MUTED = 0xFF7C818C, LINE = 0xFFE3E5EA;
    private static final int PINK = 0xFFE8457C, GREEN = 0xFF1F9D57, BLUE = 0xFF3B82F6;

    private static final int[][] WALLS = {
            {0xFFFFB3C7, 0xFFFFD9A8}, {0xFF1E2A5A, 0xFF5B2A86}, {0xFF2BB5A0, 0xFF8BE08A},
            {0xFFFF8A4C, 0xFF7A3EA8}, {0xFF2B2E35, 0xFF15171B}, {0xFF6EC3FF, 0xFFE3F4FF}};
    private static final String[] WALL_NAMES = {"Yogur", "Noche", "Menta", "Atardecer", "Grafito", "Cielo"};
    private static final Map<UUID, Long> LAST_READ = new HashMap<>();

    private record Hit(int x, int y, int w, int h, Runnable r) {}

    private final List<Hit> hits = new ArrayList<>();
    private final Deque<Page> back = new ArrayDeque<>();
    private Page page;
    private int scroll, contentH, viewH;
    private int clipTop = 0, clipBottom = 10000;
    private int mx, my;

    // selección
    private String galName;
    private int postId;
    private UUID profileUser, chatUser, transferTo;
    private boolean pickForWallpaper;

    // campos de texto
    private static class Input {
        final StringBuilder b = new StringBuilder();
        final int max;
        final String hint;
        final boolean digits;
        Input(String hint, int max, boolean digits) { this.hint = hint; this.max = max; this.digits = digits; }
        String get() { return b.toString().trim(); }
        void set(String s) { b.setLength(0); b.append(s); }
    }

    private final Input inName = new Input("Nombre visible", 24, false);
    private final Input inBio = new Input("Bio (opcional)", 60, false);
    private final Input inCaption = new Input("Escribe un pie de foto…", 140, false);
    private final Input inComment = new Input("Añade un comentario…", 140, false);
    private final Input inMsg = new Input("Mensaje", 200, false);
    private final Input inAmount = new Input("Cantidad", 7, true);
    private final Input inConcept = new Input("Concepto (opcional)", 40, false);
    private Input focus;

    // recorte de avatar
    private BufferedImage cropSrc;
    private int cropX, cropY, cropSize;
    private BufferedImage pendingAvatar;
    private String status = "";
    private long statusTime;

    public PhoneScreen(Page start) {
        super("Móvil");
        page = start;
        if (start == Page.YOGUGRAM) openYogugram(false);
    }

    @Override protected int vw() { return W; }
    @Override protected int vh() { return H; }

    public boolean isShowing(String app, String title) {
        if (app.equals("chat") && page == Page.CHAT && chatUser != null) return title.equals("@" + ClientData.nick(chatUser));
        return false;
    }

    // ---------- navegación ----------

    private void go(Page p) {
        back.push(page);
        page = p;
        scroll = 0;
        focus = null;
        tap();
    }

    private void goBack() {
        focus = null;
        scroll = 0;
        if (back.isEmpty() || page == Page.HOME) {
            if (page == Page.HOME) onClose(); else page = Page.HOME;
            return;
        }
        page = back.pop();
        tap();
    }

    private void home() {
        back.clear();
        page = Page.HOME;
        scroll = 0;
        focus = null;
        tap();
    }

    private void openYogugram(boolean push) {
        ClientData.User me = ClientData.self();
        Page p = me != null && !me.created ? Page.EDIT_PROFILE : Page.YOGUGRAM;
        if (p == Page.EDIT_PROFILE && me != null) {
            inName.set(me.display);
            inBio.set(me.bio);
        }
        if (push) go(p); else page = p;
    }

    private void say(String s) {
        status = s;
        statusTime = System.currentTimeMillis();
    }

    // ---------- dibujo general ----------

    private void hit(int x, int y, int w, int h, Runnable r) {
        int y0 = Math.max(y, clipTop), y1 = Math.min(y + h, clipBottom);
        if (y1 > y0) hits.add(new Hit(x, y0, w, y1 - y0, r));
    }

    private boolean hover(int x, int y, int w, int h) {
        return in(mx, my, x, y, w, h) && my >= clipTop && my < clipBottom;
    }

    @Override
    protected void draw(int mouseX, int mouseY, float pt) {
        mx = mouseX;
        my = mouseY;
        hits.clear();
        clipTop = 0;
        clipBottom = 10000;
        // carcasa
        round(0, 0, W, H, 18, 0xFF0E0F12);
        round(1, 1, W - 2, H - 2, 17, 0xFF2A2D33);
        round(3, 3, W - 6, H - 6, 15, 0xFF0B0C0E);
        // botones laterales
        rect(W - 1, 70, 1, 26, 0xFF2A2D33);
        rect(0, 60, 1, 16, 0xFF2A2D33);
        rect(0, 82, 1, 16, 0xFF2A2D33);
        clip(SX, SY, SW, SH);
        boolean dark = page == Page.HOME || page == Page.CROP || page == Page.PHOTO;
        rect(SX, SY, SW, SH, dark ? 0xFF000000 : BG);
        switch (page) {
            case HOME -> drawHome();
            case GALLERY -> drawGallery(false);
            case PICK_PHOTO -> drawGallery(true);
            case PHOTO -> drawPhoto();
            case YOGUGRAM -> drawFeed();
            case POST -> drawPost();
            case PROFILE -> drawProfile();
            case EDIT_PROFILE -> drawEditProfile();
            case CROP -> drawCrop();
            case NEW_POST -> drawNewPost();
            case CHATS -> drawChats();
            case CHAT -> drawChat();
            case BANK -> drawBank();
            case TRANSFER -> drawTransfer();
            case SETTINGS -> drawSettings();
        }
        clipTop = 0;
        clipBottom = 10000;
        drawStatusBar(dark);
        // barra de inicio
        int hb = page == Page.HOME ? 0x99FFFFFF : dark ? 0x99FFFFFF : 0x55000000;
        round(SX + SW / 2 - 24, SY + SH - 6, 48, 3, 1, hb);
        hit(SX + SW / 2 - 34, SY + SH - 11, 68, 11, this::home);
        // aviso temporal
        if (!status.isEmpty() && System.currentTimeMillis() - statusTime < 2500) {
            int tw = Math.min(SW - 16, font.width(status) + 12);
            round(SX + SW / 2 - tw / 2, BOTTOM - 22, tw, 14, 4, 0xE0202227);
            center(fit(status, tw - 8), SX + SW / 2, BOTTOM - 19, 0xFFFFFFFF);
        }
        unclip();
        // muesca
        round(W / 2 - 18, SY + 2, 36, 8, 4, 0xFF000000);
    }

    private void drawStatusBar(boolean dark) {
        int c = dark ? 0xFFFFFFFF : INK;
        if (page != Page.HOME && !dark) rect(SX, SY, SW, 14, BG);
        text(clock(), SX + 12, SY + 4, c);
        // cobertura
        for (int i = 0; i < 4; i++) rect(SX + SW - 46 + i * 3, SY + 9 - i * 2, 2, 2 + i * 2, c);
        // batería
        rect(SX + SW - 30, SY + 4, 16, 8, c);
        rect(SX + SW - 29, SY + 5, 14, 6, dark ? 0xFF000000 : BG);
        rect(SX + SW - 28, SY + 6, 10, 4, c);
        rect(SX + SW - 14, SY + 6, 1, 4, c);
    }

    public static String clock() {
        var level = Minecraft.getInstance().level;
        if (level == null) return "--:--";
        long t = (level.getDayTime() + 6000) % 24000;
        int h = (int) (t / 1000), m = (int) ((t % 1000) * 60 / 1000);
        return String.format("%02d:%02d", h, m);
    }

    private static long day() {
        var level = Minecraft.getInstance().level;
        return level == null ? 1 : (level.getDayTime() + 6000) / 24000 + 1;
    }

    /** Barra superior de una app con flecha atrás y título. */
    private void appBar(String title, int color) {
        rect(SX, TOP, SW, 18, CARD);
        rect(SX, TOP + 18, SW, 1, LINE);
        boolean h = hover(SX, TOP, 22, 18);
        text("<", SX + 8, TOP + 5, h ? color : INK);
        hit(SX, TOP, 22, 18, this::goBack);
        text(fit(title, SW - 60), SX + 22, TOP + 5, color);
    }

    private void button(String label, int x, int y, int w, int h, int color, int textColor, Runnable r) {
        boolean hv = hover(x, y, w, h);
        round(x, y, w, h, 4, hv ? mix(color, 0xFFFFFFFF, 0.15f) : color);
        center(fit(label, w - 6), x + w / 2, y + (h - 8) / 2, textColor);
        hit(x, y, w, h, () -> { tap(); r.run(); });
    }

    private void field(Input in, int x, int y, int w) {
        boolean f = focus == in;
        round(x, y, w, 16, 4, f ? 0xFFFFFFFF : 0xFFECEEF2);
        if (f) {
            rect(x, y + 15, w, 1, BLUE);
        }
        String t = in.b.toString();
        boolean blink = f && (System.currentTimeMillis() / 500) % 2 == 0;
        if (t.isEmpty() && !f) text(fit(in.hint, w - 8), x + 4, y + 4, MUTED);
        else {
            String shown = t;
            while (font.width(shown + "_") > w - 8 && shown.length() > 0) shown = shown.substring(1);
            text(shown + (blink ? "_" : ""), x + 4, y + 4, INK);
        }
        hit(x, y, w, 16, () -> focus = in);
    }

    private void avatar(UUID u, int x, int y, int size) {
        ClientData.User us = ClientData.user(u);
        Images.Tex t = us == null ? null : Images.get(us.avatar);
        if (t != null) {
            imageCover(t, x, y, size, size);
        } else {
            int c = us == null ? 0xFF9AA0AA : 0xFF000000 | (us.nick.hashCode() & 0x7F7F7F) + 0x303030;
            rect(x, y, size, size, c);
            String l = us == null || us.display.isEmpty() ? "?" : us.display.substring(0, 1).toUpperCase(Locale.ROOT);
            if (size >= 12) center(l, x + size / 2 + 1, y + (size - 8) / 2, 0xFFFFFFFF);
        }
        // esquinas redondeadas
        int bg = 0x00000000;
        cornerMask(x, y, size, size);
    }

    private void cornerMask(int x, int y, int w, int h) {
        int c = currentBg();
        rect(x, y, 1, 1, c); rect(x + w - 1, y, 1, 1, c); rect(x, y + h - 1, 1, 1, c); rect(x + w - 1, y + h - 1, 1, 1, c);
        if (w >= 14) {
            rect(x + 1, y, 1, 1, c); rect(x, y + 1, 1, 1, c);
            rect(x + w - 2, y, 1, 1, c); rect(x + w - 1, y + 1, 1, 1, c);
            rect(x + 1, y + h - 1, 1, 1, c); rect(x, y + h - 2, 1, 1, c);
            rect(x + w - 2, y + h - 1, 1, 1, c); rect(x + w - 1, y + h - 2, 1, 1, c);
        }
    }

    private int currentBg() {
        return page == Page.HOME ? 0 : CARD;
    }

    private static int mix(int a, int b, float k) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | (Math.round(ar + (br - ar) * k) << 16) | (Math.round(ag + (bg - ag) * k) << 8) | Math.round(ab + (bb - ab) * k);
    }

    private static String ago(long time) {
        long d = (System.currentTimeMillis() - time) / 60000;
        if (d < 1) return "ahora";
        if (d < 60) return "hace " + d + " min";
        if (d < 1440) return "hace " + (d / 60) + " h";
        return "hace " + (d / 1440) + " d";
    }

    /** Empieza una zona con scroll: devuelve la y de inicio del contenido. */
    private int beginScroll(int top) {
        viewH = BOTTOM - top;
        clipTop = top;
        clipBottom = BOTTOM;
        clip(SX, top, SW, viewH);
        return top - scroll;
    }

    private void endScroll(int top, int endY) {
        contentH = endY + scroll - top;
        unclip();
        clip(SX, SY, SW, SH);
        clipTop = 0;
        clipBottom = 10000;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
    }

    // ---------- inicio ----------

    private void drawHome() {
        drawWallpaper(SX, SY, SW, SH);
        String time = clock();
        textScaled(time, SX + SW / 2f, SY + 34, 3f, 0xFFFFFFFF, true);
        textScaled("Día " + day() + "  ·  Yogurines", SX + SW / 2f, SY + 62, 1f, 0xDDFFFFFF, true);
        String[] names = {"Cámara", "Galería", "Yogugram", "YoguChat", "Banco", "Ajustes"};
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            int x = SX + 15 + col * 50, y = SY + 104 + row * 62;
            boolean h = hover(x, y, 36, 36);
            appIcon(i, x, y - (h ? 1 : 0));
            textShadow(names[i], x + 18 - font.width(names[i]) / 2, y + 40, 0xFFFFFFFF);
            final int idx = i;
            hit(x - 4, y - 2, 44, 52, () -> openApp(idx));
        }
        int unread = unreadTotal();
        if (unread > 0) {
            int x = SX + 15 + 50 * 0 + 32, y = SY + 104 + 62 - 4;
            x = SX + 15 + 0 * 50 + 32;
            round(x, y, 11, 11, 5, 0xFFFF3B30);
            center(unread > 9 ? "9+" : Integer.toString(unread), x + 6, y + 2, 0xFFFFFFFF);
        }
        // saldo rápido abajo
        round(SX + 14, BOTTOM - 40, SW - 28, 26, 8, 0x55000000);
        text("Saldo", SX + 22, BOTTOM - 36, 0xCCFFFFFF);
        text(Money.fmt(ClientData.balance) + " Y", SX + 22, BOTTOM - 26, 0xFFFFFFFF);
        right("@" + (ClientData.self() == null ? "" : ClientData.self().nick), SX + SW - 22, BOTTOM - 31, 0xCCFFFFFF);
    }

    private void drawWallpaper(int x, int y, int w, int h) {
        String wp = ClientData.wallpaper;
        if (wp.startsWith("gal:")) {
            Images.Tex t = Gallery.tex(wp.substring(4));
            if (t != null) {
                imageCover(t, x, y, w, h);
                rect(x, y, w, h, 0x33000000);
                return;
            }
        }
        int i = 0;
        try { i = Math.max(0, Math.min(WALLS.length - 1, Integer.parseInt(wp))); } catch (Exception ignored) {}
        gradient(x, y, w, h, WALLS[i][0], WALLS[i][1]);
        // círculos suaves de decoración
        round(x + w - 60, y + 40, 90, 90, 45, 0x18FFFFFF);
        round(x - 30, y + h - 140, 80, 80, 40, 0x14FFFFFF);
    }

    private void appIcon(int i, int x, int y) {
        switch (i) {
            case 0 -> { // cámara
                round(x, y, 36, 36, 9, 0xFF3A3D44);
                round(x + 6, y + 11, 24, 17, 3, 0xFFE8EAEE);
                rect(x + 12, y + 8, 8, 3, 0xFFE8EAEE);
                round(x + 12, y + 13, 12, 12, 6, 0xFF3A3D44);
                round(x + 14, y + 15, 8, 8, 4, 0xFF6E8CD8);
                rect(x + 25, y + 13, 2, 2, 0xFFFF6B6B);
            }
            case 1 -> { // galería
                gradient(x, y, 36, 36, 0xFF6EC3FF, 0xFF3B82F6);
                cornerIcon(x, y);
                round(x + 22, y + 7, 7, 7, 3, 0xFFFFE27A);
                for (int k = 0; k < 14; k++) rect(x + 4 + k, y + 28 - k, 1, k + 1, 0xFF2E7D4F);
                for (int k = 0; k < 10; k++) rect(x + 16 + k, y + 28 - k, 1, k + 1, 0xFF3FA866);
                for (int k = 0; k < 10; k++) rect(x + 26 + k / 2, y + 23 + k / 2, 1, 6 - k / 2, 0xFF3FA866);
                rect(x + 4, y + 28, 28, 4, 0xFF2E7D4F);
            }
            case 2 -> { // yogugram
                gradient(x, y, 36, 36, 0xFFFF7AA8, 0xFFFFB347);
                cornerIcon(x, y);
                round(x + 8, y + 8, 20, 20, 6, 0xFFFFFFFF);
                round(x + 10, y + 10, 16, 16, 5, 0xFFFF8FA0);
                round(x + 13, y + 13, 10, 10, 5, 0xFFFFFFFF);
                round(x + 15, y + 15, 6, 6, 3, 0xFFFF8FA0);
                rect(x + 22, y + 11, 2, 2, 0xFFFFFFFF);
            }
            case 3 -> { // yoguchat
                round(x, y, 36, 36, 9, 0xFF25C26E);
                round(x + 7, y + 8, 22, 16, 7, 0xFFFFFFFF);
                for (int k = 0; k < 4; k++) rect(x + 10 + k, y + 23 + k, 4 - k, 1, 0xFFFFFFFF);
                rect(x + 12, y + 15, 2, 2, 0xFF25C26E);
                rect(x + 17, y + 15, 2, 2, 0xFF25C26E);
                rect(x + 22, y + 15, 2, 2, 0xFF25C26E);
            }
            case 4 -> { // banco
                round(x, y, 36, 36, 9, 0xFF14532D);
                round(x + 8, y + 8, 20, 20, 10, 0xFFE9B949);
                round(x + 10, y + 10, 16, 16, 8, 0xFFF6D365);
                center("Y", x + 18, y + 14, 0xFF8A5A00);
            }
            default -> { // ajustes
                round(x, y, 36, 36, 9, 0xFF8E939C);
                round(x + 9, y + 9, 18, 18, 9, 0xFFE8EAEE);
                rect(x + 16, y + 5, 4, 26, 0xFFE8EAEE);
                rect(x + 5, y + 16, 26, 4, 0xFFE8EAEE);
                round(x + 13, y + 13, 10, 10, 5, 0xFF8E939C);
            }
        }
    }

    private void cornerIcon(int x, int y) {
        // redondea las esquinas de un icono con degradado tapándolas con el fondo
        for (int i = 0; i < 4; i++) {
            int inset = 4 - (int) Math.round(Math.sqrt(16 - (4 - i - 0.5) * (4 - i - 0.5)));
            if (inset <= 0) continue;
            maskWall(x, y + i, inset); maskWall(x + 36 - inset, y + i, inset);
            maskWall(x, y + 35 - i, inset); maskWall(x + 36 - inset, y + 35 - i, inset);
        }
    }

    private void maskWall(int x, int y, int w) {
        // pinta un poquito del fondo encima (aproximado con el color del degradado en esa altura)
        String wp = ClientData.wallpaper;
        int i = 0;
        try { i = Math.max(0, Math.min(WALLS.length - 1, Integer.parseInt(wp))); } catch (Exception ignored) {}
        float k = (float) (y - SY) / SH;
        int c = wp.startsWith("gal:") ? 0xFF404040 : mix(WALLS[i][0], WALLS[i][1], k);
        rect(x, y, w, 1, c);
    }

    private void openApp(int i) {
        switch (i) {
            case 0 -> CameraMode.enter();
            case 1 -> go(Page.GALLERY);
            case 2 -> openYogugram(true);
            case 3 -> go(Page.CHATS);
            case 4 -> go(Page.BANK);
            default -> go(Page.SETTINGS);
        }
    }

    private int unreadTotal() {
        int n = 0;
        for (UUID u : ClientData.chats.keySet()) if (unread(u)) n++;
        return n;
    }

    private boolean unread(UUID u) {
        List<ClientData.Msg> l = ClientData.chats.get(u);
        if (l == null || l.isEmpty()) return false;
        ClientData.Msg last = l.get(l.size() - 1);
        return !last.from.equals(ClientData.me) && last.time > LAST_READ.getOrDefault(u, 0L);
    }

    // ---------- galería ----------

    private void drawGallery(boolean pick) {
        appBar(pick ? (pickForWallpaper ? "Elige un fondo" : "Elige una foto") : "Galería", INK);
        List<String> list = Gallery.list();
        int top = TOP + 19;
        int y0 = beginScroll(top);
        int y = y0 + 3;
        if (list.isEmpty()) {
            center("Aún no tienes fotos", SX + SW / 2, y + 40, MUTED);
            center("Abre la Cámara para hacer una", SX + SW / 2, y + 52, MUTED);
            y += 70;
        }
        for (int i = 0; i < list.size(); i++) {
            int c = i % 3;
            int x = SX + 2 + c * 54;
            int yy = y + (i / 3) * 33;
            String n = list.get(i);
            if (yy + 31 >= top && yy < BOTTOM) {
                Images.Tex t = Gallery.tex(n);
                if (t != null) imageCover(t, x, yy, 54 - 2, 31);
                else rect(x, yy, 52, 31, LINE);
                if (hover(x, yy, 52, 31)) rect(x, yy, 52, 31, 0x30FFFFFF);
            }
            hit(x, yy, 52, 31, () -> {
                galName = n;
                if (!pick) go(Page.PHOTO);
                else if (pickForWallpaper) {
                    ClientData.wallpaper = "gal:" + n;
                    ClientData.save();
                    pickForWallpaper = false;
                    say("Fondo cambiado");
                    goBack();
                } else {
                    inCaption.set("");
                    page = Page.NEW_POST;
                    tap();
                }
            });
        }
        endScroll(top, y + ((list.size() + 2) / 3) * 33 + 4);
    }

    private void drawPhoto() {
        rect(SX, TOP, SW, 18, 0xFF000000);
        text("<", SX + 8, TOP + 5, 0xFFFFFFFF);
        hit(SX, TOP, 22, 18, this::goBack);
        Images.Tex t = galName == null ? null : Gallery.tex(galName);
        int ih = SW * 9 / 16;
        int iy = TOP + 60;
        if (t != null) image(t, SX, iy, SW, ih);
        int by = iy + ih + 24;
        button("Publicar en Yogugram", SX + 12, by, SW - 24, 18, PINK, 0xFFFFFFFF, () -> {
            ClientData.User me = ClientData.self();
            if (me != null && !me.created) { openYogugram(true); return; }
            inCaption.set("");
            go(Page.NEW_POST);
        });
        button("Poner de fondo", SX + 12, by + 24, SW - 24, 18, 0xFF3A3D44, 0xFFFFFFFF, () -> {
            ClientData.wallpaper = "gal:" + galName;
            ClientData.save();
            say("Fondo cambiado");
        });
        button("Borrar", SX + 12, by + 48, SW - 24, 18, 0xFF5A2020, 0xFFFF9A9A, () -> {
            if (ClientData.wallpaper.equals("gal:" + galName)) { ClientData.wallpaper = "0"; ClientData.save(); }
            Gallery.delete(galName);
            say("Foto borrada");
            goBack();
        });
    }

    // ---------- yogugram ----------

    private void drawFeed() {
        rect(SX, TOP, SW, 20, CARD);
        rect(SX, TOP + 20, SW, 1, LINE);
        textScaled("Yogugram", SX + 8, TOP + 4, 1.4f, PINK, false);
        // "+" y perfil
        int px = SX + SW - 42;
        boolean hp = hover(px, TOP + 3, 14, 14);
        round(px, TOP + 3, 14, 14, 4, hp ? PINK : 0xFF2A2D33);
        center("+", px + 7, TOP + 6, 0xFFFFFFFF);
        hit(px, TOP + 3, 14, 14, () -> { pickForWallpaper = false; go(Page.PICK_PHOTO); });
        avatar(ClientData.me, SX + SW - 22, TOP + 3, 14);
        hit(SX + SW - 22, TOP + 3, 14, 14, () -> { profileUser = ClientData.me; go(Page.PROFILE); });
        int top = TOP + 21;
        int y = beginScroll(top);
        if (ClientData.posts.isEmpty()) {
            center("Todavía no hay fotos", SX + SW / 2, y + 40, MUTED);
            y += 70;
        }
        for (ClientData.Post p : ClientData.posts) {
            y = postCard(p, y, false);
        }
        endScroll(top, y);
    }

    private int postCard(ClientData.Post p, int y, boolean detail) {
        int start = y;
        boolean visible = y < BOTTOM && y + 200 > TOP;
        rect(SX, y, SW, 1, LINE);
        y += 5;
        if (visible) {
            avatar(p.author, SX + 6, y, 16);
            text(fit(ClientData.display(p.author), SW - 80), SX + 26, y, INK);
            text(fit("@" + ClientData.nick(p.author), SW - 80), SX + 26, y + 9, MUTED);
            right(ago(p.time), SX + SW - 6, y + 4, MUTED);
        }
        UUID author = p.author;
        hit(SX, y - 2, SW - 50, 20, () -> { profileUser = author; go(Page.PROFILE); });
        y += 21;
        int ih = SW * 9 / 16;
        if (y + ih > TOP && y < BOTTOM) {
            Images.Tex t = Images.get(p.img);
            if (t != null) imageCover(t, SX, y, SW, ih);
            else { rect(SX, y, SW, ih, 0xFFE9EBEF); center("Cargando…", SX + SW / 2, y + ih / 2 - 4, MUTED); }
        }
        int pid = p.id;
        if (!detail) hit(SX, y, SW, ih, () -> { postId = pid; go(Page.POST); });
        y += ih + 4;
        boolean liked = p.likes.contains(ClientData.me);
        boolean hl = hover(SX + 4, y, 40, 12);
        text((liked ? "♥ " : "♡ ") + p.likes.size(), SX + 8, y + 2, liked || hl ? PINK : INK);
        hit(SX + 4, y, 40, 12, () -> {
            CompoundTag o = new CompoundTag();
            o.putInt("id", pid);
            Net.toServer("like", o);
            if (!liked) sound(ModSounds.LIKE.get(), 1f, 0.8f);
        });
        String cm = "Comentarios (" + p.comments.size() + ")";
        boolean hc = hover(SX + 50, y, font.width(cm) + 6, 12);
        text(cm, SX + 52, y + 2, hc ? BLUE : MUTED);
        if (!detail) hit(SX + 50, y, font.width(cm) + 6, 12, () -> { postId = pid; go(Page.POST); });
        y += 14;
        if (!p.caption.isEmpty()) {
            List<FormattedCharSequence> lines = Toasts.wrap(font, ClientData.display(p.author) + ": " + p.caption, SW - 14, detail ? 8 : 3);
            for (FormattedCharSequence l : lines) {
                if (y > TOP - 10 && y < BOTTOM) text(l, SX + 7, y, INK);
                y += 10;
            }
        }
        if (!detail && !p.comments.isEmpty()) {
            ClientData.Comment c = p.comments.get(p.comments.size() - 1);
            String s = ClientData.display(c.author) + ": " + c.text;
            if (y > TOP - 10 && y < BOTTOM) text(fit(s, SW - 14), SX + 7, y, MUTED);
            y += 10;
        }
        return y + 6 + (start == y ? 1 : 0);
    }

    private ClientData.Post findPost(int id) {
        for (ClientData.Post p : ClientData.posts) if (p.id == id) return p;
        return null;
    }

    private void drawPost() {
        appBar("Publicación", INK);
        ClientData.Post p = findPost(postId);
        if (p == null) return;
        int top = TOP + 19;
        int bottom = BOTTOM - 22;
        viewH = bottom - top;
        clipTop = top;
        clipBottom = bottom;
        clip(SX, top, SW, viewH);
        int y = top - scroll;
        rect(SX, top, SW, viewH + 22, CARD);
        y = postCard(p, y, true);
        rect(SX + 6, y, SW - 12, 1, LINE);
        y += 5;
        if (p.comments.isEmpty()) { text("Sé el primero en comentar", SX + 7, y, MUTED); y += 12; }
        for (ClientData.Comment c : p.comments) {
            if (y > top - 20 && y < bottom) avatar(c.author, SX + 6, y, 12);
            List<FormattedCharSequence> lines = Toasts.wrap(font, ClientData.display(c.author) + ": " + c.text, SW - 30, 4);
            int ly = y;
            for (FormattedCharSequence l : lines) {
                if (ly > top - 10 && ly < bottom) text(l, SX + 22, ly + 2, INK);
                ly += 10;
            }
            y = Math.max(ly, y + 14) + 3;
        }
        contentH = y + scroll - top;
        unclip();
        clip(SX, SY, SW, SH);
        clipTop = 0;
        clipBottom = 10000;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        rect(SX, bottom, SW, 22, CARD);
        rect(SX, bottom, SW, 1, LINE);
        field(inComment, SX + 5, bottom + 3, SW - 44);
        button("Enviar", SX + SW - 37, bottom + 3, 32, 16, PINK, 0xFFFFFFFF, this::sendComment);
    }

    private void sendComment() {
        String t = inComment.get();
        if (t.isEmpty()) return;
        CompoundTag o = new CompoundTag();
        o.putInt("id", postId);
        o.putString("x", t);
        Net.toServer("comment", o);
        inComment.set("");
    }

    private void drawProfile() {
        ClientData.User u = ClientData.user(profileUser);
        appBar(u == null ? "Perfil" : "@" + u.nick, INK);
        if (u == null) return;
        int top = TOP + 19;
        int y = beginScroll(top);
        rect(SX, y, SW, 120, CARD);
        avatar(u.id, SX + 10, y + 8, 40);
        int posts = 0;
        List<ClientData.Post> mine = new ArrayList<>();
        for (ClientData.Post p : ClientData.posts) if (p.author.equals(u.id)) { posts++; mine.add(p); }
        stat(SX + 76, y + 14, posts, "fotos");
        stat(SX + 106, y + 14, ClientData.followers(u.id), "seguid.");
        stat(SX + 140, y + 14, u.following.size(), "sigue");
        y += 52;
        text(fit(u.display, SW - 20), SX + 10, y, INK);
        if (u.online && !u.id.equals(ClientData.me)) right("● en línea", SX + SW - 10, y, GREEN);
        y += 10;
        if (!u.bio.isEmpty()) {
            for (FormattedCharSequence l : Toasts.wrap(font, u.bio, SW - 20, 3)) { text(l, SX + 10, y, MUTED); y += 10; }
        }
        y += 4;
        if (u.id.equals(ClientData.me)) {
            button("Editar perfil", SX + 10, y, SW - 20, 16, 0xFFE3E5EA, INK, () -> {
                inName.set(u.display);
                inBio.set(u.bio);
                pendingAvatar = null;
                go(Page.EDIT_PROFILE);
            });
        } else {
            ClientData.User me = ClientData.self();
            boolean following = me != null && me.following.contains(u.id);
            button(following ? "Siguiendo" : "Seguir", SX + 10, y, (SW - 26) / 2, 16, following ? 0xFFE3E5EA : BLUE, following ? INK : 0xFFFFFFFF, () -> {
                CompoundTag o = new CompoundTag();
                o.putString("u", u.id.toString());
                o.putBoolean("on", !following);
                Net.toServer("follow", o);
            });
            button("Mensaje", SX + 16 + (SW - 26) / 2, y, (SW - 26) / 2, 16, 0xFFE3E5EA, INK, () -> {
                chatUser = u.id;
                go(Page.CHAT);
            });
        }
        y += 22;
        rect(SX, y, SW, 1, LINE);
        y += 2;
        for (int i = 0; i < mine.size(); i++) {
            int x = SX + 1 + (i % 3) * 55;
            int yy = y + (i / 3) * 55;
            ClientData.Post p = mine.get(i);
            if (yy + 54 > top && yy < BOTTOM) {
                Images.Tex t = Images.get(p.img);
                if (t != null) imageCover(t, x, yy, 54, 54); else rect(x, yy, 54, 54, LINE);
            }
            int pid = p.id;
            hit(x, yy, 54, 54, () -> { postId = pid; go(Page.POST); });
        }
        if (mine.isEmpty()) { center("Sin fotos todavía", SX + SW / 2, y + 20, MUTED); y += 40; }
        endScroll(top, y + ((mine.size() + 2) / 3) * 55 + 4);
    }

    private void stat(int cx, int y, int n, String label) {
        center(Integer.toString(n), cx, y, INK);
        textScaled(label, cx, y + 11, 0.75f, MUTED, true);
    }

    private void drawEditProfile() {
        ClientData.User me = ClientData.self();
        boolean first = me == null || !me.created;
        appBar(first ? "Crea tu perfil" : "Editar perfil", PINK);
        int y = TOP + 28;
        if (first) {
            center("¡Bienvenid@ a Yogugram!", SX + SW / 2, y, INK);
            y += 12;
        }
        int ax = SX + SW / 2 - 25;
        if (pendingAvatar != null) {
            Images.Tex t = Images.cached("avatar_pending");
            if (t != null) image(t, ax, y, 50, 50);
        } else avatar(ClientData.me, ax, y, 50);
        cornerMask(ax, y, 50, 50);
        hit(ax, y, 50, 50, this::pickAvatar);
        y += 54;
        button("Elegir foto del PC", SX + 30, y, SW - 60, 16, 0xFFE3E5EA, INK, this::pickAvatar);
        y += 24;
        text("Nombre visible", SX + 10, y, MUTED);
        field(inName, SX + 8, y + 10, SW - 16);
        y += 32;
        text("@" + (me == null ? "" : me.nick) + "  (tu nick)", SX + 10, y, MUTED);
        y += 14;
        text("Bio", SX + 10, y, MUTED);
        field(inBio, SX + 8, y + 10, SW - 16);
        y += 36;
        button(first ? "Crear perfil" : "Guardar", SX + 10, y, SW - 20, 18, PINK, 0xFFFFFFFF, this::saveProfile);
    }

    private void saveProfile() {
        CompoundTag o = new CompoundTag();
        o.putString("disp", inName.get());
        o.putString("bio", inBio.get());
        if (pendingAvatar != null) {
            byte[] b = Images.jpeg(pendingAvatar, 0.9f);
            if (b != null) o.putString("avatar", Images.upload(b));
            pendingAvatar = null;
        }
        Net.toServer("profile", o);
        say("Perfil guardado");
        back.clear();
        back.push(Page.HOME);
        page = Page.YOGUGRAM;
        scroll = 0;
        focus = null;
    }

    private void pickAvatar() {
        Minecraft mc = Minecraft.getInstance();
        Thread t = new Thread(() -> {
            String path;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer f = stack.mallocPointer(3);
                f.put(stack.UTF8("*.png"));
                f.put(stack.UTF8("*.jpg"));
                f.put(stack.UTF8("*.jpeg"));
                f.flip();
                path = TinyFileDialogs.tinyfd_openFileDialog("Elige tu foto de perfil", "", f, "Imágenes (png, jpg)", false);
            } catch (Throwable e) {
                path = null;
            }
            if (path == null) return;
            BufferedImage img;
            try { img = ImageIO.read(new File(path)); } catch (Exception e) { img = null; }
            BufferedImage fin = img;
            mc.execute(() -> {
                if (fin == null) { say("No se ha podido abrir esa imagen"); return; }
                startCrop(fin);
            });
        }, "yogurines-elegir-foto");
        t.setDaemon(true);
        t.start();
    }

    private void startCrop(BufferedImage img) {
        // se reduce para que sea ligera
        int max = 512;
        double k = Math.min(1.0, (double) max / Math.max(img.getWidth(), img.getHeight()));
        if (k < 1) {
            BufferedImage r = new BufferedImage((int) (img.getWidth() * k), (int) (img.getHeight() * k), BufferedImage.TYPE_INT_RGB);
            var g2 = r.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img, 0, 0, r.getWidth(), r.getHeight(), null);
            g2.dispose();
            img = r;
        }
        cropSrc = img;
        cropSize = Math.min(img.getWidth(), img.getHeight());
        cropX = (img.getWidth() - cropSize) / 2;
        cropY = (img.getHeight() - cropSize) / 2;
        Images.put("crop", img);
        if (page != Page.CROP) go(Page.CROP);
    }

    private int[] cropArea() {
        int aw = SW - 16, ah = 180;
        double k = Math.min((double) aw / cropSrc.getWidth(), (double) ah / cropSrc.getHeight());
        int w = (int) (cropSrc.getWidth() * k), h = (int) (cropSrc.getHeight() * k);
        return new int[]{SX + 8 + (aw - w) / 2, TOP + 30 + (ah - h) / 2, w, h, (int) Math.round(k * 1000)};
    }

    private void drawCrop() {
        rect(SX, TOP, SW, 18, 0xFF000000);
        text("<", SX + 8, TOP + 5, 0xFFFFFFFF);
        hit(SX, TOP, 22, 18, this::goBack);
        center("Recorta tu foto", SX + SW / 2, TOP + 5, 0xFFFFFFFF);
        if (cropSrc == null) return;
        int[] a = cropArea();
        double k = a[4] / 1000.0;
        image(Images.cached("crop"), a[0], a[1], a[2], a[3]);
        int cx = a[0] + (int) (cropX * k), cy = a[1] + (int) (cropY * k), cs = (int) (cropSize * k);
        // oscurece fuera del recorte
        rect(a[0], a[1], a[2], cy - a[1], 0x99000000);
        rect(a[0], cy + cs, a[2], a[1] + a[3] - cy - cs, 0x99000000);
        rect(a[0], cy, cx - a[0], cs, 0x99000000);
        rect(cx + cs, cy, a[0] + a[2] - cx - cs, cs, 0x99000000);
        rect(cx, cy, cs, 1, 0xFFFFFFFF); rect(cx, cy + cs - 1, cs, 1, 0xFFFFFFFF);
        rect(cx, cy, 1, cs, 0xFFFFFFFF); rect(cx + cs - 1, cy, 1, cs, 0xFFFFFFFF);
        center("Arrastra para mover", SX + SW / 2, TOP + 216, 0xFFBBBBBB);
        center("Rueda para hacer zoom", SX + SW / 2, TOP + 226, 0xFFBBBBBB);
        button("Usar esta foto", SX + 20, TOP + 244, SW - 40, 18, PINK, 0xFFFFFFFF, () -> {
            pendingAvatar = Images.crop(cropSrc, cropX, cropY, cropSize, 128);
            Images.put("avatar_pending", pendingAvatar);
            goBack();
        });
    }

    private void drawNewPost() {
        appBar("Nueva publicación", PINK);
        int y = TOP + 24;
        Images.Tex t = galName == null ? null : Gallery.tex(galName);
        int ih = (SW - 12) * 9 / 16;
        if (t != null) image(t, SX + 6, y, SW - 12, ih);
        y += ih + 8;
        field(inCaption, SX + 6, y, SW - 12);
        if (focus == null) focus = inCaption;
        y += 24;
        button("Publicar", SX + 10, y, SW - 20, 18, PINK, 0xFFFFFFFF, this::publish);
    }

    private void publish() {
        BufferedImage img = galName == null ? null : Gallery.read(galName);
        if (img == null) return;
        byte[] b = Images.jpeg(Images.cover(img, 480, 270), 0.85f);
        if (b == null) return;
        CompoundTag o = new CompoundTag();
        o.putString("uid", Images.upload(b));
        o.putString("cap", inCaption.get());
        Net.toServer("post", o);
        inCaption.set("");
        say("¡Publicado!");
        back.clear();
        back.push(Page.HOME);
        page = Page.YOGUGRAM;
        scroll = 0;
        focus = null;
    }

    // ---------- yoguchat ----------

    private List<ClientData.User> contacts() {
        List<ClientData.User> l = new ArrayList<>();
        for (ClientData.User u : ClientData.users.values()) if (!u.id.equals(ClientData.me)) l.add(u);
        l.sort((a, b) -> Long.compare(lastTime(b.id), lastTime(a.id)));
        return l;
    }

    private long lastTime(UUID u) {
        List<ClientData.Msg> l = ClientData.chats.get(u);
        return l == null || l.isEmpty() ? 0 : l.get(l.size() - 1).time;
    }

    private void drawChats() {
        appBar("YoguChat", 0xFF25C26E);
        int top = TOP + 19;
        int y = beginScroll(top);
        List<ClientData.User> l = contacts();
        if (l.isEmpty()) { center("No hay nadie más todavía", SX + SW / 2, y + 40, MUTED); y += 60; }
        for (ClientData.User u : l) {
            boolean h = hover(SX, y, SW, 28);
            rect(SX, y, SW, 28, h ? 0xFFEFF1F4 : CARD);
            avatar(u.id, SX + 6, y + 5, 18);
            if (u.online) { rect(SX + 20, y + 19, 5, 5, CARD); rect(SX + 21, y + 20, 3, 3, GREEN); }
            text(fit(u.display, SW - 70), SX + 30, y + 5, INK);
            List<ClientData.Msg> m = ClientData.chats.get(u.id);
            String prev = m == null || m.isEmpty() ? "@" + u.nick : (m.get(m.size() - 1).from.equals(ClientData.me) ? "Tú: " : "") + m.get(m.size() - 1).text;
            text(fit(prev, SW - 46), SX + 30, y + 15, MUTED);
            if (m != null && !m.isEmpty()) right(shortAgo(m.get(m.size() - 1).time), SX + SW - 6, y + 5, MUTED);
            if (unread(u.id)) round(SX + SW - 12, y + 16, 6, 6, 3, 0xFF25C26E);
            rect(SX + 30, y + 27, SW - 30, 1, LINE);
            UUID id = u.id;
            hit(SX, y, SW, 28, () -> { chatUser = id; go(Page.CHAT); });
            y += 28;
        }
        endScroll(top, y);
    }

    private static String shortAgo(long time) {
        long d = (System.currentTimeMillis() - time) / 60000;
        if (d < 1) return "ahora";
        if (d < 60) return d + " min";
        if (d < 1440) return (d / 60) + " h";
        return (d / 1440) + " d";
    }

    private void drawChat() {
        ClientData.User u = ClientData.user(chatUser);
        if (u == null) { goBack(); return; }
        LAST_READ.put(u.id, System.currentTimeMillis());
        // barra con avatar
        rect(SX, TOP, SW, 22, CARD);
        rect(SX, TOP + 22, SW, 1, LINE);
        text("<", SX + 8, TOP + 7, INK);
        hit(SX, TOP, 22, 22, this::goBack);
        avatar(u.id, SX + 20, TOP + 3, 16);
        text(fit(u.display, SW - 70), SX + 40, TOP + 3, INK);
        text(u.online ? "en línea" : "desconectad@", SX + 40, TOP + 12, u.online ? GREEN : MUTED);
        UUID uid = u.id;
        hit(SX + 20, TOP, SW - 40, 22, () -> { profileUser = uid; go(Page.PROFILE); });
        int top = TOP + 23, bottom = BOTTOM - 22;
        rect(SX, top, SW, bottom - top, 0xFFECE5DD);
        List<ClientData.Msg> msgs = ClientData.chats.getOrDefault(u.id, List.of());
        // medir
        List<Object[]> laid = new ArrayList<>();
        int total = 6;
        for (ClientData.Msg m : msgs) {
            boolean transfer = "transfer".equals(m.kind);
            List<FormattedCharSequence> lines = Toasts.wrap(font, (transfer ? "$ " : "") + m.text, 110, 12);
            int w = 0;
            for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
            int h = lines.size() * 10 + 6;
            laid.add(new Object[]{m, lines, w, h});
            total += h + 4;
        }
        viewH = bottom - top;
        contentH = total;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        clipTop = top;
        clipBottom = bottom;
        clip(SX, top, SW, viewH);
        int y = top + Math.min(0, viewH - contentH) + scroll + 4;
        if (msgs.isEmpty()) center("Escribe el primer mensaje", SX + SW / 2, top + viewH / 2, MUTED);
        for (Object[] o : laid) {
            ClientData.Msg m = (ClientData.Msg) o[0];
            @SuppressWarnings("unchecked") List<FormattedCharSequence> lines = (List<FormattedCharSequence>) o[1];
            int w = (int) o[2] + 10, h = (int) o[3];
            boolean mine = m.from.equals(ClientData.me);
            boolean transfer = "transfer".equals(m.kind);
            int x = mine ? SX + SW - 6 - w : SX + 6;
            if (y + h > top && y < bottom) {
                int col = transfer ? 0xFFFFE9A8 : mine ? 0xFFD9FDD3 : 0xFFFFFFFF;
                round(x, y, w, h, 4, col);
                int ly = y + 3;
                for (FormattedCharSequence l : lines) { text(l, x + 5, ly, transfer ? 0xFF7A5200 : INK); ly += 10; }
            }
            y += h + 4;
        }
        unclip();
        clip(SX, SY, SW, SH);
        clipTop = 0;
        clipBottom = 10000;
        rect(SX, bottom, SW, 22, CARD);
        rect(SX, bottom, SW, 1, LINE);
        if (focus == null) focus = inMsg;
        field(inMsg, SX + 5, bottom + 3, SW - 44);
        button("Enviar", SX + SW - 37, bottom + 3, 32, 16, 0xFF25C26E, 0xFFFFFFFF, this::sendMsg);
    }

    private void sendMsg() {
        String t = inMsg.get();
        if (t.isEmpty() || chatUser == null) return;
        CompoundTag o = new CompoundTag();
        o.putString("to", chatUser.toString());
        o.putString("x", t);
        Net.toServer("msg", o);
        inMsg.set("");
        scroll = 0;
    }

    // ---------- banco ----------

    private void drawBank() {
        appBar("Banco Yogurín", GREEN);
        int y = TOP + 26;
        gradient(SX + 8, y, SW - 16, 62, 0xFF1F9D57, 0xFF0F5C33);
        cornerMask(SX + 8, y, SW - 16, 62);
        text("Saldo disponible", SX + 16, y + 8, 0xCCFFFFFF);
        String bal = Money.fmt(ClientData.balance) + " Y";
        textScaled(bal, SX + 16, y + 22, bal.length() > 10 ? 1.5f : 2f, 0xFFFFFFFF, false);
        text("@" + (ClientData.self() == null ? "" : ClientData.self().nick), SX + 16, y + 48, 0xAAFFFFFF);
        y += 70;
        button("Enviar dinero", SX + 8, y, SW - 16, 18, GREEN, 0xFFFFFFFF, () -> {
            transferTo = null;
            inAmount.set("");
            inConcept.set("");
            go(Page.TRANSFER);
        });
        y += 24;
        if (System.currentTimeMillis() - ClientData.bankErrorTime < 3000) {
            center(fit(ClientData.bankError, SW - 16), SX + SW / 2, y, 0xFFE53935);
            y += 12;
        }
        text("Movimientos", SX + 8, y, INK);
        y += 12;
        int top = y;
        int yy = beginScroll(top);
        List<ClientData.Tx> h = new ArrayList<>(ClientData.history);
        Collections.reverse(h);
        if (h.isEmpty()) { center("Sin movimientos", SX + SW / 2, yy + 10, MUTED); yy += 30; }
        for (ClientData.Tx x : h) {
            rect(SX + 8, yy, SW - 16, 24, CARD);
            String who;
            UUID o = ClientData.uuid(x.other);
            if (o != null) who = (x.amount < 0 ? "A @" : "De @") + ClientData.nick(o);
            else who = x.concept.isEmpty() ? "Cajero" : x.concept;
            text(fit(who, SW - 80), SX + 13, yy + 4, INK);
            String sub = o != null && !x.concept.isEmpty() ? x.concept : shortAgo(x.time);
            text(fit(sub, SW - 80), SX + 13, yy + 13, MUTED);
            String v = (x.amount > 0 ? "+" : "") + Money.fmt(x.amount) + " Y";
            right(v, SX + SW - 13, yy + 8, x.amount >= 0 ? GREEN : 0xFFE53935);
            yy += 26;
        }
        endScroll(top, yy);
    }

    private void drawTransfer() {
        appBar("Enviar dinero", GREEN);
        int y = TOP + 24;
        if (transferTo == null) {
            text("¿A quién se lo envías?", SX + 8, y, MUTED);
            int top = y + 12;
            int yy = beginScroll(top);
            for (ClientData.User u : contacts()) {
                boolean h = hover(SX, yy, SW, 24);
                rect(SX, yy, SW, 24, h ? 0xFFEFF1F4 : CARD);
                avatar(u.id, SX + 6, yy + 4, 16);
                text(fit(u.display, SW - 40), SX + 28, yy + 3, INK);
                text(fit("@" + u.nick, SW - 40), SX + 28, yy + 12, MUTED);
                UUID id = u.id;
                hit(SX, yy, SW, 24, () -> { transferTo = id; tap(); focus = inAmount; });
                yy += 25;
            }
            endScroll(top, yy);
            return;
        }
        avatar(transferTo, SX + SW / 2 - 16, y, 32);
        y += 36;
        center(fit(ClientData.display(transferTo), SW - 16), SX + SW / 2, y, INK);
        center("@" + ClientData.nick(transferTo), SX + SW / 2, y + 10, MUTED);
        hit(SX, y - 36, SW, 56, () -> transferTo = null);
        y += 26;
        text("Cantidad (Y)", SX + 10, y, MUTED);
        field(inAmount, SX + 8, y + 10, SW - 16);
        y += 32;
        text("Concepto", SX + 10, y, MUTED);
        field(inConcept, SX + 8, y + 10, SW - 16);
        y += 32;
        text("Tienes " + Money.fmt(ClientData.balance) + " Y", SX + 10, y, MUTED);
        y += 14;
        button("Enviar", SX + 8, y, SW - 16, 18, GREEN, 0xFFFFFFFF, () -> {
            long v;
            try { v = Long.parseLong(inAmount.get()); } catch (Exception e) { say("Escribe una cantidad"); return; }
            if (v <= 0) { say("Escribe una cantidad"); return; }
            if (v > ClientData.balance) { say("Saldo insuficiente"); return; }
            CompoundTag o = new CompoundTag();
            o.putString("to", transferTo.toString());
            o.putLong("v", v);
            o.putString("c", inConcept.get());
            Net.toServer("transfer", o);
            say("Enviado: " + Money.fmt(v) + " Y");
            goBack();
        });
    }

    // ---------- ajustes ----------

    private void drawSettings() {
        appBar("Ajustes", INK);
        int top = TOP + 19;
        int y = beginScroll(top) + 6;
        text("Fondo de pantalla", SX + 8, y, MUTED);
        y += 11;
        for (int i = 0; i < WALLS.length; i++) {
            int x = SX + 8 + (i % 3) * 51, yy = y + (i / 3) * 42;
            gradient(x, yy, 48, 30, WALLS[i][0], WALLS[i][1]);
            if (ClientData.wallpaper.equals(Integer.toString(i))) {
                rect(x, yy, 48, 1, BLUE); rect(x, yy + 29, 48, 1, BLUE); rect(x, yy, 1, 30, BLUE); rect(x + 47, yy, 1, 30, BLUE);
            }
            center(WALL_NAMES[i], x + 24, yy + 32, INK);
            final int idx = i;
            hit(x, yy, 48, 40, () -> { tap(); ClientData.wallpaper = Integer.toString(idx); ClientData.save(); });
        }
        y += 86;
        button("Usar una foto de la galería", SX + 8, y, SW - 16, 16, 0xFFE3E5EA, INK, () -> { pickForWallpaper = true; page = Page.PICK_PHOTO; back.push(Page.SETTINGS); scroll = 0; });
        y += 24;
        text("Privacidad", SX + 8, y, MUTED);
        y += 11;
        boolean hidden = ClientData.me != null && ClientData.hiddenTags.contains(ClientData.me);
        y = toggle("Ocultar mi nombre", "sobre la cabeza", hidden, y, () -> {
            CompoundTag o = new CompoundTag();
            o.putBoolean("on", !hidden);
            Net.toServer("hide", o);
        });
        y += 6;
        text("Avisos", SX + 8, y, MUTED);
        y += 11;
        y = toggle("YoguChat", "mensajes nuevos", ClientData.notifChat, y, () -> { ClientData.notifChat = !ClientData.notifChat; ClientData.save(); });
        y = toggle("Yogugram", "likes, comentarios, seguidores", ClientData.notifSocial, y, () -> { ClientData.notifSocial = !ClientData.notifSocial; ClientData.save(); });
        y = toggle("Banco", "dinero recibido", ClientData.notifBank, y, () -> { ClientData.notifBank = !ClientData.notifBank; ClientData.save(); });
        y += 8;
        center("Móvil Yogurines · v0.1", SX + SW / 2, y, MUTED);
        y += 14;
        endScroll(top, y);
    }

    private int toggle(String label, String sub, boolean on, int y, Runnable r) {
        rect(SX + 8, y, SW - 16, 26, CARD);
        text(fit(label, SW - 60), SX + 14, y + 4, INK);
        text(fit(sub, SW - 60), SX + 14, y + 14, MUTED);
        int tx = SX + SW - 36;
        round(tx, y + 7, 22, 12, 6, on ? 0xFF34C759 : 0xFFD1D5DB);
        round(on ? tx + 11 : tx + 1, y + 8, 10, 10, 5, 0xFFFFFFFF);
        hit(SX + 8, y, SW - 16, 26, () -> { tap(); r.run(); });
        return y + 28;
    }

    // ---------- entrada ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = vx(mouseX), y = vy(mouseY);
        if (page == Page.CROP && cropSrc != null) {
            int[] a = cropArea();
            if (in(x, y, a[0], a[1], a[2], a[3])) { dragging = true; dragX = x; dragY = y; return true; }
        }
        Input before = focus;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (in(x, y, h.x, h.y, h.w, h.h)) {
                h.r.run();
                return true;
            }
        }
        if (focus == before) focus = null;
        if (!in(x, y, 0, 0, W, H)) { onClose(); return true; }
        return true;
    }

    private boolean dragging;
    private int dragX, dragY;

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (page == Page.CROP && dragging && cropSrc != null) {
            int x = vx(mouseX), y = vy(mouseY);
            double k = cropArea()[4] / 1000.0;
            cropX += (int) Math.round((x - dragX) / k);
            cropY += (int) Math.round((y - dragY) / k);
            dragX = x;
            dragY = y;
            clampCrop();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void clampCrop() {
        int min = Math.max(16, Math.min(cropSrc.getWidth(), cropSrc.getHeight()) / 5);
        cropSize = Math.max(min, Math.min(cropSize, Math.min(cropSrc.getWidth(), cropSrc.getHeight())));
        cropX = Math.max(0, Math.min(cropX, cropSrc.getWidth() - cropSize));
        cropY = Math.max(0, Math.min(cropY, cropSrc.getHeight() - cropSize));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (page == Page.CROP && cropSrc != null) {
            int c = cropSize;
            cropSize = (int) Math.round(cropSize * (delta > 0 ? 0.92 : 1.08));
            cropX += (c - cropSize) / 2;
            cropY += (c - cropSize) / 2;
            clampCrop();
            return true;
        }
        if (page == Page.CHAT) scroll += (int) Math.round(delta * 20);
        else scroll -= (int) Math.round(delta * 20);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (focus != null && page != Page.CHAT && page != Page.NEW_POST) focus = null;
            else goBack();
            return true;
        }
        if (focus != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (focus.b.length() > 0) {
                    if (hasControlDown()) focus.b.setLength(0);
                    else focus.b.deleteCharAt(focus.b.length() - 1);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (focus == inMsg) sendMsg();
                else if (focus == inComment) sendComment();
                else if (focus == inCaption) publish();
                else if (focus == inName) focus = inBio;
                else focus = null;
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                if (focus == inName) focus = inBio;
                else if (focus == inAmount) focus = inConcept;
                return true;
            }
            if (isPaste(key)) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                for (char c : clip.toCharArray()) type(c);
                return true;
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (focus == null) return false;
        type(c);
        return true;
    }

    private void type(char c) {
        if (focus == null || focus.b.length() >= focus.max) return;
        if (focus.digits ? Character.isDigit(c) : SharedConstants.isAllowedChatCharacter(c)) focus.b.append(c);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
