package com.yogurines.mod.npc.client;

import com.yogurines.mod.item.Money;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.npc.NpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Editor de NPCs con pestañas y vista previa en directo. Solo lo abren los admins. */
public class NpcEditScreen extends Screen {
    private static final String[] TABS = {"look", "name", "dialog", "shop", "behaviour"};
    private static final int[] DYES = {0xB02E26, 0xF9801D, 0xFED83D, 0x80C71F, 0x3F9A2E, 0x169C9C, 0x3AB3DA, 0x3C44AA,
            0x8932B8, 0xC74EBD, 0xF38BAA, 0x835432, 0xF9FFFE, 0x9D9D97, 0x474F52, 0x1D1D21};
    /** Reposición en minutos (0 = nunca). */
    private static final int[] RESTOCK = {0, 1, 5, 10, 15, 30, 60, 120, 360, 720, 1440};
    private static final int SLOT = 26;

    private final NpcEntity npc;
    private final CompoundTag look;
    private final CompoundTag cfg;
    private final List<String> skinFiles;
    private ListTag equip;
    private boolean equipChanged, homeHere, confirmDelete;
    private int tab, node, offer, offerScroll;
    /** Hueco que se está eligiendo del inventario: "item" o "priceItem" (null = ninguno). */
    private String picking;
    private String tplName = "";
    private String status = "";
    private long statusTime;
    private ItemStack hoverStack = ItemStack.EMPTY;

    public NpcEditScreen(NpcEntity npc, CompoundTag look, CompoundTag cfg, ListTag equip, List<String> skinFiles) {
        super(Component.literal(T.t("edit.title")));
        this.npc = npc;
        this.look = look.copy();
        this.cfg = cfg.copy();
        this.equip = equip;
        this.skinFiles = skinFiles;
        this.tplName = look.getString("name");
        changed();
    }

    private CompoundTag beh() { return cfg.getCompound("beh"); }
    private CompoundTag dlg() { return cfg.getCompound("dlg"); }
    private ListTag shop() { ListTag l = cfg.getList("shop", Tag.TAG_COMPOUND); cfg.put("shop", l); return l; }
    private ListTag nodes() { ListTag l = dlg().getList("nodes", Tag.TAG_COMPOUND); dlg().put("nodes", l); return l; }

    private void changed() {
        npc.preview = look.copy();
    }

    // ---------- medidas ----------

    private int pw() { return Math.min(width - 12, 460); }
    private int ph() { return Math.min(height - 12, 264); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int cx() { return px() + 118; }
    private int cy() { return py() + 30; }
    private int cw() { return pw() - 126; }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int x = px(), y = py();
        int by = y + ph() - 22;
        if (picking != null) {
            addRenderableWidget(new Pill(x + pw() - 86, by, 78, 16, () -> T.t("edit.cancel"), b -> { picking = null; rebuild(); }, () -> false));
            return;
        }
        int tx = x + 118;
        for (int i = 0; i < TABS.length; i++) {
            final int k = i;
            String label = T.t("edit.tab." + TABS[i]);
            int w = font.width(label) + 12;
            addRenderableWidget(new Pill(tx, y + 6, w, 16, () -> label, b -> { tab = k; confirmDelete = false; rebuild(); }, () -> tab == k));
            tx += w + 2;
        }
        switch (tab) {
            case 0 -> buildLook();
            case 1 -> buildName();
            case 2 -> buildDialog();
            case 3 -> buildShop();
            default -> buildBehaviour();
        }
        addRenderableWidget(new Pill(x + pw() - 78, by, 70, 16, () -> T.t("edit.save"), b -> { save(); onClose(); }, () -> true));
        addRenderableWidget(new Pill(x + pw() - 152, by, 70, 16, () -> T.t("edit.cancel"), b -> onClose(), () -> false));
    }

    // ---------- filas ----------

    private int rowY(int r) { return cy() + 4 + r * 21; }

    private void label(GuiGraphics g, int r, String key) {
        g.drawString(font, T.t(key), cx(), rowY(r) + 4, 0xFFAAAAAA, false);
    }

    private int fx() { return cx() + 96; }
    private int fw() { return cw() - 96; }

    private EditBox box(int x, int y, int w, String value, int max, Consumer<String> onChange) {
        EditBox b = new EditBox(font, x, y, w, 16, Component.empty());
        b.setMaxLength(max);
        b.setValue(value);
        b.setResponder(onChange);
        addRenderableWidget(b);
        return b;
    }

    /** Selector que va pasando opciones; las etiquetas son claves de traducción (o texto tal cual si raw). */
    private void cycle(int r, String[] keys, String prefix, Supplier<String> get, Consumer<String> set) {
        cycleAt(fx(), rowY(r), fw(), keys, prefix, get, set);
    }

    private void cycleAt(int x, int y, int w, String[] keys, String prefix, Supplier<String> get, Consumer<String> set) {
        addRenderableWidget(new Pill(x, y, w, 16, () -> {
            String v = get.get();
            String k = keys[0];
            for (String key : keys) if (key.equals(v)) k = key;
            return "◀ " + (prefix == null ? k : T.t(prefix + k)) + " ▶";
        }, b -> {
            String v = get.get();
            int i = 0;
            for (int k = 0; k < keys.length; k++) if (keys[k].equals(v)) i = k;
            set.accept(keys[(i + (Screen.hasShiftDown() ? keys.length - 1 : 1)) % keys.length]);
            changed();
            rebuild();
        }, () -> false));
    }

    private void toggle(int r, Supplier<Boolean> get, Consumer<Boolean> set) {
        addRenderableWidget(new Pill(fx(), rowY(r), 50, 16, () -> T.t(get.get() ? "edit.yes" : "edit.no"), b -> { set.accept(!get.get()); changed(); }, get::get));
    }

    /** Dos opciones una al lado de la otra; la elegida se ve encendida. */
    private void segmented(int x, int y, int w, String a, String b, Supplier<Boolean> isA, Consumer<Boolean> set) {
        int half = (w - 2) / 2;
        addRenderableWidget(new Pill(x, y, half, 16, () -> a, p -> { set.accept(true); rebuild(); }, isA::get));
        addRenderableWidget(new Pill(x + half + 2, y, w - half - 2, 16, () -> b, p -> { set.accept(false); rebuild(); }, () -> !isA.get()));
    }

    // ---------- pestaña Aspecto ----------

    private void buildLook() {
        cycle(0, new String[]{"default", "player", "url", "file"}, "edit.skin.",
                () -> look.getString("skinType"), v -> { look.putString("skinType", v); if (v.equals("file") && !skinFiles.isEmpty()) look.putString("skin", skinFiles.get(0)); });
        String st = look.getString("skinType");
        if (st.equals("player") || st.equals("url")) {
            box(fx(), rowY(1), fw(), look.getString("skin"), 200, v -> {
                look.putString("skin", v.trim());
                NpcSkins.forget(st, v.trim());
                changed();
            });
        } else if (st.equals("file")) {
            if (skinFiles.isEmpty()) {
                addRenderableWidget(new Pill(fx(), rowY(1), fw(), 16, () -> T.t("edit.skin.none"), b -> {}, () -> false));
            } else {
                cycle(1, skinFiles.toArray(new String[0]), null, () -> look.getString("skin"), v -> look.putString("skin", v));
            }
        }
        toggle(2, () -> look.getBoolean("slim"), v -> look.putBoolean("slim", v));
        cycle(3, new String[]{"0.6", "1.0", "1.4", "2.0"}, "edit.size.",
                () -> String.format(java.util.Locale.ROOT, "%.1f", look.getFloat("scale")), v -> look.putFloat("scale", Float.parseFloat(v)));
        cycle(4, new String[]{"stand", "sit", "cross", "behind", "wave", "think"}, "edit.pose.",
                () -> look.getString("pose"), v -> look.putString("pose", v));
        int half = (fw() - 4) / 2;
        addRenderableWidget(new Pill(fx(), rowY(5), half, 16, () -> T.t("edit.equip.copy"), b -> copyEquip(false), () -> false));
        addRenderableWidget(new Pill(fx() + half + 4, rowY(5), half, 16, () -> T.t("edit.equip.clear"), b -> copyEquip(true), () -> false));
    }

    private void copyEquip(boolean clear) {
        var p = Minecraft.getInstance().player;
        ListTag l = new ListTag();
        for (EquipmentSlot s : NpcEntity.SLOTS) {
            ItemStack it = clear || p == null ? ItemStack.EMPTY : p.getItemBySlot(s).copy();
            l.add(it.save(new CompoundTag()));
        }
        equip = l;
        equipChanged = true;
        status(T.t(clear ? "edit.equip.cleared" : "edit.equip.copied"));
    }

    // ---------- pestaña Nombre ----------

    private void buildName() {
        box(fx(), rowY(0), fw(), look.getString("name"), 40, v -> { look.putString("name", v); changed(); });
        toggle(1, () -> !look.contains("showName") || look.getBoolean("showName"), v -> look.putBoolean("showName", v));
        cycle(2, new String[]{"minecraft", "tech", "medieval", "cartel"}, "edit.font.",
                () -> look.getString("font"), v -> look.putString("font", v));
        cycle(3, new String[]{"vanilla", "plate", "holo", "ribbon", "banner"}, "edit.bg.",
                () -> look.getString("bg"), v -> look.putString("bg", v));
        if (look.getString("bg").equals("holo"))
            cycleAt(fx(), rowY(4), fw(), new String[]{"glitch", "projection", "interference"}, "edit.holo.",
                    () -> look.getString("holo"), v -> look.putString("holo", v));
        box(fx() + fw() - 60, rowY(5), 60, hex(look.getInt("c1")), 7, v -> { Integer c = parseHex(v); if (c != null) { look.putInt("c1", c); changed(); } });
        box(fx() + fw() - 60, rowY(6), 60, hex(look.contains("c2") ? look.getInt("c2") : 0xFFFFFF), 7, v -> { Integer c = parseHex(v); if (c != null) { look.putInt("c2", c); changed(); } });
        box(fx(), rowY(7), fw(), look.getString("title"), 32, v -> { look.putString("title", v); changed(); });
        cycle(8, new String[]{"none", "auto", "talk", "shop", "quest", "bank", "info"}, "edit.icon.",
                () -> look.getString("icon"), v -> look.putString("icon", v));
    }

    private static String hex(int c) { return String.format("#%06X", c & 0xFFFFFF); }

    private static Integer parseHex(String v) {
        v = v.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (!v.matches("[0-9a-fA-F]{6}")) return null;
        return Integer.parseInt(v, 16);
    }

    // ---------- pestaña Diálogos ----------

    private void buildDialog() {
        cycle(0, new String[]{"none", "chat", "tree"}, "edit.dlg.mode.",
                () -> dlg().getString("mode"), v -> dlg().putString("mode", v));
        box(fx(), rowY(1), fw(), dlg().getString("first"), 200, v -> dlg().putString("first", v));
        String mode = dlg().getString("mode");
        if (mode.equals("chat")) {
            ListTag greet = dlg().getList("greet", Tag.TAG_STRING);
            for (int i = 0; i < 5; i++) {
                final int k = i;
                box(fx(), rowY(2 + i), fw(), i < greet.size() ? greet.getString(i) : "", 160, v -> setGreet(k, v));
            }
        } else if (mode.equals("tree")) {
            ListTag nodes = nodes();
            if (nodes.isEmpty()) nodes.add(newNode(T.t("edit.dlg.hello")));
            node = Math.max(0, Math.min(node, nodes.size() - 1));
            int bx = fx();
            for (int i = 0; i < nodes.size() && i < 12; i++) {
                final int k = i;
                addRenderableWidget(new Pill(bx, rowY(2), 16, 16, () -> String.valueOf(k + 1), b -> { node = k; rebuild(); }, () -> node == k));
                bx += 18;
            }
            if (nodes.size() < 12) addRenderableWidget(new Pill(bx, rowY(2), 16, 16, () -> "+", b -> { nodes().add(newNode("")); node = nodes().size() - 1; rebuild(); }, () -> false));
            if (nodes.size() > 1) addRenderableWidget(new Pill(bx + 18, rowY(2), 16, 16, () -> "−", b -> { nodes().remove(node); node = Math.max(0, node - 1); rebuild(); }, () -> false));
            CompoundTag n = nodes.getCompound(node);
            box(cx(), rowY(3), cw(), n.getString("text"), 300, v -> n.putString("text", v));
            ListTag opts = n.getList("opts", Tag.TAG_COMPOUND);
            n.put("opts", opts);
            for (int i = 0; i < opts.size() && i < 4; i++) {
                final CompoundTag o = opts.getCompound(i);
                final int k = i;
                int y = rowY(4 + i);
                int lw = cw() - 196;
                box(cx(), y, lw, o.getString("label"), 60, v -> o.putString("label", v));
                cycleAt(cx() + lw + 2, y, 84, new String[]{"goto", "shop", "close", "give", "pay"}, "edit.dlg.act.",
                        () -> o.getString("act"), v -> o.putString("act", v));
                int px = cx() + lw + 88;
                String act = o.getString("act");
                if (act.equals("goto") || act.equals("pay") || act.equals("give")) {
                    String[] keys = new String[nodes.size() + 1];
                    keys[0] = "-1";
                    for (int j = 0; j < nodes.size(); j++) keys[j + 1] = String.valueOf(j);
                    final String end = T.t("edit.dlg.end");
                    addRenderableWidget(new Pill(px, y, 44, 16, () -> {
                        int tg = o.contains("target") ? o.getInt("target") : -1;
                        return tg < 0 ? end : "→" + (tg + 1);
                    }, b -> {
                        int tg = o.contains("target") ? o.getInt("target") : -1;
                        int next = tg + 1 >= nodes().size() ? -1 : tg + 1;
                        o.putInt("target", next);
                    }, () -> false));
                    px += 46;
                }
                if (act.equals("pay")) {
                    box(px, y, 40, String.valueOf(o.getLong("price")), 7, v -> o.putLong("price", parseLong(v)));
                } else if (act.equals("give")) {
                    addRenderableWidget(new Pill(px, y, 40, 16, () -> (o.contains("item") && !ItemStack.of(o.getCompound("item")).isEmpty() ? "✔ " : "") + T.t("edit.dlg.hand"), b -> {
                        var p = Minecraft.getInstance().player;
                        if (p != null) o.put("item", p.getMainHandItem().copy().save(new CompoundTag()));
                        status(T.t("edit.dlg.handSaved"));
                    }, () -> false));
                }
                addRenderableWidget(new Pill(cx() + cw() - 16, y, 16, 16, () -> "✕", b -> { opts.remove(k); rebuild(); }, () -> false));
            }
            if (opts.size() < 4) addRenderableWidget(new Pill(cx(), rowY(4 + opts.size()), 110, 16, () -> T.t("edit.dlg.addReply"), b -> {
                CompoundTag o = new CompoundTag();
                o.putString("label", T.t("edit.dlg.bye"));
                o.putString("act", "close");
                o.putInt("target", -1);
                opts.add(o);
                rebuild();
            }, () -> false));
        }
    }

    private static CompoundTag newNode(String text) {
        CompoundTag n = new CompoundTag();
        n.putString("text", text);
        n.put("opts", new ListTag());
        return n;
    }

    private void setGreet(int i, String v) {
        ListTag greet = dlg().getList("greet", Tag.TAG_STRING);
        String[] arr = new String[5];
        for (int k = 0; k < 5; k++) arr[k] = k < greet.size() ? greet.getString(k) : "";
        arr[i] = v;
        ListTag out = new ListTag();
        for (String s : arr) if (!s.isBlank()) out.add(StringTag.valueOf(s.trim()));
        dlg().put("greet", out);
    }

    // ---------- pestaña Tienda ----------

    private static final int LIST_W = 112, LIST_ROWS = 8;

    private int shopX() { return cx() + LIST_W + 8; }
    private int shopW() { return cw() - LIST_W - 8; }
    /** Posición de los dos huecos del trueque: [lo que paga el jugador] → [lo que recibe]. */
    private int slotAX() { return shopX() + 14; }
    private int slotBX() { return shopX() + shopW() - 14 - SLOT; }
    private int slotY() { return rowY(1) + 12; }

    private CompoundTag cur() {
        ListTag s = shop();
        return s.isEmpty() ? null : s.getCompound(Math.max(0, Math.min(offer, s.size() - 1)));
    }

    private static boolean sells(CompoundTag o) { return !"buy".equals(o.getString("mode")); }

    private void buildShop() {
        ListTag shop = shop();
        offer = Math.max(0, Math.min(offer, shop.size() - 1));
        offerScroll = Math.max(0, Math.min(offerScroll, Math.max(0, shop.size() - LIST_ROWS)));
        int lx = cx();
        addRenderableWidget(new Pill(lx, rowY(0), LIST_W, 16, () -> T.t("edit.shop.add"), b -> {
            if (shop().size() >= 40) return;
            CompoundTag o = new CompoundTag();
            o.putString("mode", "sell");
            var p = Minecraft.getInstance().player;
            ItemStack it = p == null ? ItemStack.EMPTY : p.getMainHandItem().copy();
            o.put("item", it.save(new CompoundTag()));
            o.putBoolean("payY", true);
            o.putLong("price", 10);
            o.put("priceItem", ItemStack.EMPTY.save(new CompoundTag()));
            o.putInt("max", -1);
            o.putInt("stock", -1);
            o.putInt("restock", 0);
            o.putInt("limit", 0);
            shop().add(o);
            offer = shop().size() - 1;
            offerScroll = Math.max(0, offer - LIST_ROWS + 1);
            if (it.isEmpty()) { picking = "item"; }
            rebuild();
        }, () -> false));
        CompoundTag o = cur();
        if (o == null) return;
        int sx = shopX(), sw = shopW();
        // quién vende
        segmented(sx, rowY(0), sw, T.t("edit.shop.npcSells"), T.t("edit.shop.npcBuys"), () -> sells(o), v -> o.putString("mode", v ? "sell" : "buy"));
        // los dos huecos se pulsan en mouseClicked; aquí van los controles de cantidad
        boolean sell = sells(o);
        String payKey = sell ? "price" : "item";
        String getKey = sell ? "item" : "price";
        stepperFor(o, payKey, slotAX());
        stepperFor(o, getKey, slotBX());
        // tipo de precio
        int ty = slotY() + SLOT + 22;
        segmented(sx, ty, sw, T.t("edit.shop.priceY"), T.t("edit.shop.priceItem"), () -> o.getBoolean("payY"), v -> {
            o.putBoolean("payY", v);
            if (!v && ItemStack.of(o.getCompound("priceItem")).isEmpty()) picking = "priceItem";
        });
        // stock
        int y = ty + 21;
        int lw = 70;
        int half = (sw - lw - 2) / 2;
        addRenderableWidget(new Pill(sx + lw, y, half, 16, () -> "∞", b -> { o.putInt("max", -1); o.putInt("stock", -1); rebuild(); }, () -> o.getInt("max") < 0));
        if (o.getInt("max") < 0) {
            addRenderableWidget(new Pill(sx + lw + half + 2, y, sw - lw - half - 2, 16, () -> T.t("edit.shop.limited"), b -> { o.putInt("max", 16); o.putInt("stock", 16); rebuild(); }, () -> false));
        } else {
            stepper(sx + lw + half + 2, y, sw - lw - half - 2, () -> String.valueOf(o.getInt("max")), d -> {
                int m = Math.max(1, Math.min(9999, o.getInt("max") + d));
                o.putInt("max", m);
                o.putInt("stock", m);
            });
        }
        y += 21;
        addRenderableWidget(new Pill(sx + lw, y, sw - lw, 16, () -> "◀ " + restockLabel(o.getInt("restock")) + " ▶", b -> {
            int cur = o.getInt("restock"), i = 0;
            for (int k = 0; k < RESTOCK.length; k++) if (RESTOCK[k] <= cur) i = k;
            o.putInt("restock", RESTOCK[(i + (Screen.hasShiftDown() ? RESTOCK.length - 1 : 1)) % RESTOCK.length]);
        }, () -> false));
        y += 21;
        stepper(sx + lw, y, sw - lw, () -> o.getInt("limit") <= 0 ? T.t("edit.shop.noLimit") : String.valueOf(o.getInt("limit")),
                d -> o.putInt("limit", Math.max(0, Math.min(9999, o.getInt("limit") + d))));
        // borrar / duplicar
        int ey = rowY(1) + LIST_ROWS * 19 + 2;
        int bw = (LIST_W - 2) / 2;
        addRenderableWidget(new Pill(lx, ey, bw, 16, () -> T.t("edit.shop.dup"), b -> {
            if (shop().size() >= 40) return;
            shop().add(offer + 1, o.copy());
            offer++;
            rebuild();
        }, () -> false));
        addRenderableWidget(new Pill(lx + bw + 2, ey, LIST_W - bw - 2, 16, () -> T.t("edit.shop.del"), b -> {
            shop().remove(offer);
            offer = Math.max(0, offer - 1);
            rebuild();
        }, () -> false));
    }

    private static String restockLabel(int mins) {
        if (mins <= 0) return T.t("edit.shop.never");
        if (mins < 60) return T.t("edit.shop.everyMin", mins);
        if (mins < 1440) return T.t("edit.shop.everyHour", mins / 60);
        return T.t("edit.shop.everyDay", mins / 1440);
    }

    /** Controles de cantidad debajo de un hueco: objetos de 1 a 64, o yogurines con caja de texto. */
    private void stepperFor(CompoundTag o, String what, int slotX) {
        int y = slotY() + SLOT + 3;
        int w = 76, x = slotX + SLOT / 2 - w / 2;
        if (what.equals("price") && o.getBoolean("payY")) {
            addRenderableWidget(new Pill(x, y, 14, 16, () -> "−", b -> { o.putLong("price", Math.max(0, o.getLong("price") - step())); rebuild(); }, () -> false));
            box(x + 15, y, w - 30, String.valueOf(o.getLong("price")), 9, v -> o.putLong("price", parseLong(v)));
            addRenderableWidget(new Pill(x + w - 14, y, 14, 16, () -> "+", b -> { o.putLong("price", Math.min(999_999_999L, o.getLong("price") + step())); rebuild(); }, () -> false));
            return;
        }
        String key = what.equals("price") ? "priceItem" : "item";
        stepper(x, y, w, () -> {
            ItemStack it = ItemStack.of(o.getCompound(key));
            return it.isEmpty() ? "—" : "×" + it.getCount();
        }, d -> {
            ItemStack it = ItemStack.of(o.getCompound(key));
            if (it.isEmpty()) return;
            it.setCount(Math.max(1, Math.min(it.getMaxStackSize(), it.getCount() + d)));
            o.put(key, it.save(new CompoundTag()));
        });
    }

    private static int step() {
        return Screen.hasControlDown() ? 100 : Screen.hasShiftDown() ? 10 : 1;
    }

    private void stepper(int x, int y, int w, Supplier<String> value, Consumer<Integer> change) {
        addRenderableWidget(new Pill(x, y, 14, 16, () -> "−", b -> change.accept(-step()), () -> false));
        addRenderableWidget(new Pill(x + 15, y, w - 30, 16, value, b -> {}, () -> false));
        addRenderableWidget(new Pill(x + w - 14, y, 14, 16, () -> "+", b -> change.accept(step()), () -> false));
    }

    private static long parseLong(String v) {
        try { return Math.max(0, Long.parseLong(v.trim())); } catch (Exception e) { return 0; }
    }

    // ---------- pestaña Conducta ----------

    private void buildBehaviour() {
        toggle(0, () -> beh().getBoolean("look"), v -> beh().putBoolean("look", v));
        cycle(1, new String[]{"still", "wander", "home"}, "edit.move.",
                () -> beh().getString("move"), v -> beh().putString("move", v));
        box(fx(), rowY(2), 40, String.valueOf(beh().getInt("radius")), 2, v -> beh().putInt("radius", (int) Math.max(1, Math.min(48, parseLong(v)))));
        toggle(3, () -> beh().getBoolean("invuln"), v -> beh().putBoolean("invuln", v));
        toggle(4, () -> beh().getBoolean("push"), v -> beh().putBoolean("push", v));
        addRenderableWidget(new Pill(fx(), rowY(5), fw(), 16, () -> T.t(homeHere ? "edit.home.set" : "edit.home.here"), b -> homeHere = !homeHere, () -> homeHere));
        box(fx(), rowY(7), fw() - 70, tplName, 32, v -> tplName = v);
        addRenderableWidget(new Pill(fx() + fw() - 66, rowY(7), 66, 16, () -> T.t("edit.tpl.save"), b -> {
            if (tplName.isBlank()) { status(T.t("edit.tpl.needName")); return; }
            save();
            CompoundTag t = new CompoundTag();
            t.putInt("id", npc.getId());
            t.putString("name", tplName.trim());
            Net.toServer("npc_tpl_save", t);
            status(T.t("edit.tpl.saved", tplName.trim()));
        }, () -> false));
        addRenderableWidget(new Pill(fx(), rowY(8), fw(), 16, () -> T.t(confirmDelete ? "edit.delete.sure" : "edit.delete"), b -> {
            if (!confirmDelete) { confirmDelete = true; return; }
            CompoundTag t = new CompoundTag();
            t.putInt("id", npc.getId());
            Net.toServer("npc_delete", t);
            onClose();
        }, () -> confirmDelete));
    }

    // ---------- guardar ----------

    private void save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", npc.getId());
        t.put("look", look.copy());
        t.put("cfg", cfg.copy());
        if (equipChanged) t.put("equip", equip);
        t.putBoolean("homeHere", homeHere);
        Net.toServer("npc_save", t);
        equipChanged = false;
        homeHere = false;
    }

    private void status(String s) {
        status = s;
        statusTime = System.currentTimeMillis();
    }

    @Override
    public void onClose() {
        npc.preview = null;
        super.onClose();
    }

    @Override
    public void removed() {
        npc.preview = null;
        super.removed();
    }

    // ---------- dibujo ----------

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hoverStack = ItemStack.EMPTY;
        int x = px(), y = py(), w = pw(), h = ph();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xF51A1C21);
        g.fill(x + 112, y + 24, x + w - 4, y + h - 28, 0xFF33373F);
        g.fill(x + 112, y + 24, x + w - 4, y + 25, 0xFF7FD36A);
        // vista previa
        g.fill(x + 4, y + 6, x + 108, y + h - 28, 0xFF23262C);
        g.drawString(font, Nameplate.styled(T.t("edit.preview").toUpperCase(), "tech"), x + 10, y + 10, 0xFF7FD36A, false);
        int ex = x + 56, ey = y + h - 44;
        float sc = 48f / Math.max(0.6f, npc.look().scale);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, ex, ey, (int) sc, ex - mx, ey - 60 - my, npc);
        if (picking != null) {
            super.render(g, mx, my, pt);
            renderPicker(g, mx, my);
            return;
        }
        switch (tab) {
            case 0 -> {
                label(g, 0, "edit.l.skin");
                String st = look.getString("skinType");
                if (!st.equals("default")) label(g, 1, "edit.l.skin." + st);
                label(g, 2, "edit.l.slim");
                label(g, 3, "edit.l.size");
                label(g, 4, "edit.l.pose");
                label(g, 5, "edit.l.equip");
                if (st.equals("file")) g.drawString(font, T.t("edit.skin.hint"), cx(), rowY(6) + 4, 0xFF777777, false);
            }
            case 1 -> {
                label(g, 0, "edit.l.name");
                label(g, 1, "edit.l.showName");
                label(g, 2, "edit.l.font");
                label(g, 3, "edit.l.bg");
                if (look.getString("bg").equals("holo")) label(g, 4, "edit.l.effect");
                label(g, 5, "edit.l.c1");
                label(g, 6, "edit.l.c2");
                label(g, 7, "edit.l.title");
                label(g, 8, "edit.l.icon");
                swatches(g, 5, look.getInt("c1"));
                swatches(g, 6, look.contains("c2") ? look.getInt("c2") : 0xFFFFFF);
            }
            case 2 -> {
                label(g, 0, "edit.l.mode");
                label(g, 1, "edit.l.first");
                String mode = dlg().getString("mode");
                if (mode.equals("chat")) for (int i = 0; i < 5; i++) g.drawString(font, T.t("edit.l.phrase", i + 1), cx(), rowY(2 + i) + 4, 0xFFAAAAAA, false);
                if (mode.equals("tree")) label(g, 2, "edit.l.screens");
                if (mode.equals("none")) g.drawString(font, T.t("edit.dlg.noneHint"), cx(), rowY(2) + 4, 0xFF777777, false);
            }
            case 3 -> drawShop(g, mx, my);
            default -> {
                label(g, 0, "edit.l.look");
                label(g, 1, "edit.l.move");
                label(g, 2, "edit.l.radius");
                label(g, 3, "edit.l.invuln");
                label(g, 4, "edit.l.push");
                label(g, 5, "edit.l.home");
                g.drawString(font, Nameplate.styled(T.t("edit.l.template").toUpperCase(), "tech"), cx(), rowY(6) + 6, 0xFF7FD36A, false);
                label(g, 7, "edit.l.name");
            }
        }
        if (!status.isEmpty() && System.currentTimeMillis() - statusTime < 2500)
            g.drawString(font, status, x + 10, y + h - 18, 0xFF9BE37F, false);
        super.render(g, mx, my, pt);
        if (!hoverStack.isEmpty()) g.renderTooltip(font, hoverStack, mx, my);
    }

    private void drawShop(GuiGraphics g, int mx, int my) {
        ListTag shop = shop();
        int lx = cx();
        // lista de ofertas
        g.fill(lx, rowY(1) - 2, lx + LIST_W, rowY(1) + LIST_ROWS * 19, 0xFF23262C);
        for (int r = 0; r < LIST_ROWS && r + offerScroll < shop.size(); r++) {
            int i = r + offerScroll;
            CompoundTag o = shop.getCompound(i);
            int ry = rowY(1) + r * 19;
            boolean on = i == offer;
            boolean hover = mx >= lx && mx < lx + LIST_W && my >= ry && my < ry + 18;
            g.fill(lx, ry, lx + LIST_W, ry + 18, on ? 0xFF2F5A28 : hover ? 0xFF33373F : 0xFF262A31);
            ItemStack it = ItemStack.of(o.getCompound("item"));
            g.renderItem(it, lx + 1, ry + 1);
            g.renderItemDecorations(font, it, lx + 1, ry + 1);
            String tag = T.t(sells(o) ? "edit.shop.tagSells" : "edit.shop.tagBuys");
            String price;
            if (o.getBoolean("payY")) price = "Y" + Money.fmt(o.getLong("price"));
            else {
                ItemStack pi = ItemStack.of(o.getCompound("priceItem"));
                price = pi.isEmpty() ? "?" : pi.getCount() + "× " + pi.getHoverName().getString();
            }
            String line = tag + " · " + price;
            if (font.width(line) > LIST_W - 22) line = font.plainSubstrByWidth(line, LIST_W - 28) + "…";
            g.drawString(font, line, lx + 19, ry + 5, on ? 0xFFB8FFA8 : 0xFFDDDDDD, false);
        }
        if (shop.size() > LIST_ROWS) {
            int barH = LIST_ROWS * 19;
            int th = Math.max(8, barH * LIST_ROWS / shop.size());
            int ty = rowY(1) + (barH - th) * offerScroll / Math.max(1, shop.size() - LIST_ROWS);
            g.fill(lx + LIST_W - 2, ty, lx + LIST_W, ty + th, 0xFF7FD36A);
        }
        CompoundTag o = cur();
        if (o == null) {
            g.drawString(font, T.t("edit.shop.empty1"), shopX(), rowY(1) + 4, 0xFF777777, false);
            g.drawString(font, T.t("edit.shop.empty2"), shopX(), rowY(1) + 16, 0xFF777777, false);
            return;
        }
        boolean sell = sells(o);
        // trueque visual
        int sy = slotY();
        drawSlot(g, mx, my, slotAX(), sy, o, sell ? "price" : "item", T.t("edit.shop.pays"));
        drawSlot(g, mx, my, slotBX(), sy, o, sell ? "item" : "price", T.t("edit.shop.gets"));
        int ax0 = slotAX() + SLOT + 6, ax1 = slotBX() - 6, ay = sy + SLOT / 2;
        for (int ax = ax0; ax < ax1 - 4; ax += 4) g.fill(ax, ay, ax + 2, ay + 1, 0xFF7FD36A);
        g.drawString(font, "▶", ax1 - 6, ay - 4, 0xFF7FD36A, false);
        String who = T.t("edit.shop.player");
        g.drawString(font, who, (ax0 + ax1) / 2 - font.width(who) / 2, ay - 12, 0xFF777777, false);
        int ty = slotY() + SLOT + 22;
        int y = ty + 21;
        g.drawString(font, T.t("edit.shop.stock"), shopX(), y + 4, 0xFFAAAAAA, false);
        g.drawString(font, T.t("edit.shop.restock"), shopX(), y + 25, 0xFFAAAAAA, false);
        g.drawString(font, T.t("edit.shop.limit"), shopX(), y + 46, 0xFFAAAAAA, false);
    }

    private void drawSlot(GuiGraphics g, int mx, int my, int x, int y, CompoundTag o, String what, String caption) {
        boolean hover = mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
        g.drawString(font, caption, x + SLOT / 2 - font.width(caption) / 2, y - 11, 0xFFAAAAAA, false);
        g.fill(x - 1, y - 1, x + SLOT + 1, y + SLOT + 1, hover ? 0xFF7FD36A : 0xFF000000);
        g.fill(x, y, x + SLOT, y + SLOT, 0xFF15171B);
        if (what.equals("price") && o.getBoolean("payY")) {
            String v = "Y";
            g.drawString(font, Nameplate.styled(v, "cartel"), x + SLOT / 2 - font.width(Nameplate.styled(v, "cartel")) / 2, y + 9, 0xFFFFD84A, false);
            return;
        }
        ItemStack it = ItemStack.of(o.getCompound(what.equals("price") ? "priceItem" : "item"));
        if (it.isEmpty()) {
            g.drawString(font, "+", x + SLOT / 2 - 2, y + SLOT / 2 - 4, 0xFF7FD36A, false);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x + 1, y + 1, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.renderItem(it, 0, 0);
        g.pose().popPose();
        if (hover) hoverStack = it;
    }

    /** Ventana para elegir un objeto de tu inventario. */
    private int pickX() { return cx() + (cw() - 9 * 20) / 2; }
    private int pickY() { return cy() + 30; }

    private void renderPicker(GuiGraphics g, int mx, int my) {
        int x = pickX() - 8, y = pickY() - 24, w = 9 * 20 + 16, h = 5 * 20 + 40;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF7FD36A);
        g.fill(x, y, x + w, y + h, 0xFF1A1C21);
        g.drawString(font, T.t("edit.pick.title"), x + 8, y + 8, 0xFFFFFFFF, false);
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        Inventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            int slot = i < 27 ? i + 9 : i - 27; // primero la mochila, abajo la barra rápida
            int sx = pickX() + (i % 9) * 20, sy = pickY() + (i / 9) * 20 + (i >= 27 ? 4 : 0);
            ItemStack it = inv.items.get(slot);
            boolean hover = mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18;
            g.fill(sx, sy, sx + 18, sy + 18, hover && !it.isEmpty() ? 0xFF4CB43A : 0xFF2A2E35);
            g.renderItem(it, sx + 1, sy + 1);
            g.renderItemDecorations(font, it, sx + 1, sy + 1);
            if (hover && !it.isEmpty()) hoverStack = it;
        }
        g.drawString(font, T.t("edit.pick.hint"), x + 8, pickY() + 4 * 20 + 10, 0xFF777777, false);
        if (!hoverStack.isEmpty()) g.renderTooltip(font, hoverStack, mx, my);
    }

    private void swatches(GuiGraphics g, int r, int current) {
        int sx = fx(), sy = rowY(r) + 1;
        for (int i = 0; i < DYES.length; i++) {
            int x = sx + (i % 8) * 11, y = sy + (i / 8) * 8;
            g.fill(x, y, x + 10, y + 7, 0xFF000000);
            g.fill(x + 1, y + 1, x + 9, y + 6, 0xFF000000 | DYES[i]);
            if ((current & 0xFFFFFF) == DYES[i]) g.fill(x, y + 7, x + 10, y + 8, 0xFFFFD84A);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picking != null) {
            var p = Minecraft.getInstance().player;
            CompoundTag o = cur();
            if (p != null && o != null) {
                for (int i = 0; i < 36; i++) {
                    int slot = i < 27 ? i + 9 : i - 27;
                    int sx = pickX() + (i % 9) * 20, sy = pickY() + (i / 9) * 20 + (i >= 27 ? 4 : 0);
                    if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) {
                        ItemStack it = p.getInventory().items.get(slot);
                        if (it.isEmpty()) return true;
                        ItemStack c = it.copy();
                        if (button == 1) c.setCount(1); // clic derecho: solo uno
                        o.put(picking, c.save(new CompoundTag()));
                        PixelSound.tap();
                        picking = null;
                        rebuild();
                        return true;
                    }
                }
            }
            return super.mouseClicked(mx, my, button);
        }
        if (tab == 1) {
            for (int r = 5; r <= 6; r++) {
                int sx = fx(), sy = rowY(r) + 1;
                for (int i = 0; i < DYES.length; i++) {
                    int x = sx + (i % 8) * 11, y = sy + (i / 8) * 8;
                    if (mx >= x && mx < x + 10 && my >= y && my < y + 8) {
                        look.putInt(r == 5 ? "c1" : "c2", DYES[i]);
                        changed();
                        rebuild();
                        PixelSound.tap();
                        return true;
                    }
                }
            }
        }
        if (tab == 3) {
            ListTag shop = shop();
            for (int r = 0; r < LIST_ROWS && r + offerScroll < shop.size(); r++) {
                int ry = rowY(1) + r * 19;
                if (mx >= cx() && mx < cx() + LIST_W && my >= ry && my < ry + 18) {
                    offer = r + offerScroll;
                    rebuild();
                    PixelSound.tap();
                    return true;
                }
            }
            CompoundTag o = cur();
            if (o != null) {
                boolean sell = sells(o);
                for (int k = 0; k < 2; k++) {
                    int sx = k == 0 ? slotAX() : slotBX();
                    if (mx >= sx && mx < sx + SLOT && my >= slotY() && my < slotY() + SLOT) {
                        String what = (k == 0) == sell ? "price" : "item";
                        if (what.equals("price") && o.getBoolean("payY")) return true;
                        picking = what.equals("price") ? "priceItem" : "item";
                        PixelSound.tap();
                        rebuild();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 3 && picking == null) {
            if (mx >= cx() && mx < cx() + LIST_W) {
                offerScroll -= (int) Math.signum(delta);
                rebuild();
                return true;
            }
            // rueda sobre un hueco = cambiar cantidad
            CompoundTag o = cur();
            if (o != null) {
                boolean sell = sells(o);
                for (int k = 0; k < 2; k++) {
                    int sx = k == 0 ? slotAX() : slotBX();
                    if (mx >= sx && mx < sx + SLOT && my >= slotY() && my < slotY() + SLOT) {
                        String what = (k == 0) == sell ? "price" : "item";
                        int d = (int) Math.signum(delta) * step();
                        if (what.equals("price") && o.getBoolean("payY")) o.putLong("price", Math.max(0, o.getLong("price") + d));
                        else {
                            String key = what.equals("price") ? "priceItem" : "item";
                            ItemStack it = ItemStack.of(o.getCompound(key));
                            if (!it.isEmpty()) {
                                it.setCount(Math.max(1, Math.min(it.getMaxStackSize(), it.getCount() + d)));
                                o.put(key, it.save(new CompoundTag()));
                            }
                        }
                        PixelSound.tick();
                        rebuild();
                        return true;
                    }
                }
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (picking != null && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { picking = null; rebuild(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Botón plano con el estilo del mod (oscuro con acento verde). */
    private class Pill extends AbstractButton {
        private final Supplier<String> text;
        private final Consumer<Pill> action;
        private final Supplier<Boolean> active;

        Pill(int x, int y, int w, int h, Supplier<String> text, Consumer<Pill> action, Supplier<Boolean> active) {
            super(x, y, w, h, Component.empty());
            this.text = text;
            this.action = action;
            this.active = active;
        }

        @Override
        public void onPress() {
            PixelSound.tap();
            action.accept(this);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean on = active.get();
            boolean hover = isHoveredOrFocused();
            int bg = on ? 0xFF2F7D25 : (hover ? 0xFF3A3F48 : 0xFF262A31);
            g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, bg);
            if (on) g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 2, 0xFF7FD36A);
            String s = text.get();
            if (font.width(s) > width - 6) s = font.plainSubstrByWidth(s, width - 10) + "…";
            g.drawString(font, s, getX() + width / 2 - font.width(s) / 2, getY() + (height - 8) / 2, on ? 0xFFFFFFFF : 0xFFE0E0E0, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
