package com.yogurines.mod.npc.client;

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
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Editor de NPCs con pestañas y vista previa en directo. Solo lo abren los admins. */
public class NpcEditScreen extends Screen {
    private static final String[] TABS = {"Aspecto", "Nombre", "Diálogos", "Tienda", "Conducta"};
    private static final int[] DYES = {0xB02E26, 0xF9801D, 0xFED83D, 0x80C71F, 0x3F9A2E, 0x169C9C, 0x3AB3DA, 0x3C44AA,
            0x8932B8, 0xC74EBD, 0xF38BAA, 0x835432, 0xF9FFFE, 0x9D9D97, 0x474F52, 0x1D1D21};

    private final NpcEntity npc;
    private final CompoundTag look;
    private final CompoundTag cfg;
    private final List<String> skinFiles;
    private ListTag equip;
    private boolean equipChanged, homeHere, confirmDelete;
    private int tab, node, offer;
    private String tplName = "";
    private String status = "";
    private long statusTime;

    public NpcEditScreen(NpcEntity npc, CompoundTag look, CompoundTag cfg, ListTag equip, List<String> skinFiles) {
        super(Component.literal("Editor de NPC"));
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

    private int pw() { return Math.min(width - 12, 440); }
    private int ph() { return Math.min(height - 12, 260); }
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
        int tx = x + 118;
        for (int i = 0; i < TABS.length; i++) {
            final int k = i;
            int w = font.width(TABS[i]) + 12;
            addRenderableWidget(new Pill(tx, y + 6, w, 16, () -> TABS[k], b -> { tab = k; confirmDelete = false; rebuild(); }, () -> tab == k));
            tx += w + 2;
        }
        switch (tab) {
            case 0 -> buildLook();
            case 1 -> buildName();
            case 2 -> buildDialog();
            case 3 -> buildShop();
            default -> buildBehaviour();
        }
        int by = y + ph() - 22;
        addRenderableWidget(new Pill(x + pw() - 74, by, 66, 16, () -> "GUARDAR", b -> { save(); onClose(); }, () -> true));
        addRenderableWidget(new Pill(x + pw() - 146, by, 66, 16, () -> "CANCELAR", b -> onClose(), () -> false));
    }

    // ---------- filas ----------

    private int rowY(int r) { return cy() + 4 + r * 21; }

    private void label(GuiGraphics g, int r, String text) {
        g.drawString(font, text, cx(), rowY(r) + 4, 0xFFAAAAAA, false);
    }

    private int fx() { return cx() + 92; }
    private int fw() { return cw() - 92; }

    private EditBox box(int x, int y, int w, String value, int max, Consumer<String> onChange) {
        EditBox b = new EditBox(font, x, y, w, 16, Component.empty());
        b.setMaxLength(max);
        b.setValue(value);
        b.setResponder(onChange);
        addRenderableWidget(b);
        return b;
    }

    private void cycle(int r, String[] keys, String[] labels, Supplier<String> get, Consumer<String> set) {
        cycleAt(fx(), rowY(r), fw(), keys, labels, get, set);
    }

    private void cycleAt(int x, int y, int w, String[] keys, String[] labels, Supplier<String> get, Consumer<String> set) {
        addRenderableWidget(new Pill(x, y, w, 16, () -> {
            String v = get.get();
            for (int i = 0; i < keys.length; i++) if (keys[i].equals(v)) return "◀ " + labels[i] + " ▶";
            return "◀ " + labels[0] + " ▶";
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
        addRenderableWidget(new Pill(fx(), rowY(r), 46, 16, () -> get.get() ? "Sí" : "No", b -> { set.accept(!get.get()); changed(); }, get::get));
    }

    // ---------- pestaña Aspecto ----------

    private void buildLook() {
        cycle(0, new String[]{"default", "player", "url", "file"}, new String[]{"Por defecto", "Nombre de jugador", "Enlace (URL)", "Archivo del servidor"},
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
                addRenderableWidget(new Pill(fx(), rowY(1), fw(), 16, () -> "No hay skins en config/yogurines/skins", b -> {}, () -> false));
            } else {
                String[] arr = skinFiles.toArray(new String[0]);
                cycle(1, arr, arr, () -> look.getString("skin"), v -> look.putString("skin", v));
            }
        }
        toggle(2, () -> look.getBoolean("slim"), v -> look.putBoolean("slim", v));
        cycle(3, new String[]{"0.6", "1.0", "1.4", "2.0"}, new String[]{"Pequeño", "Normal", "Grande", "Gigante"},
                () -> String.format(java.util.Locale.ROOT, "%.1f", look.getFloat("scale")), v -> look.putFloat("scale", Float.parseFloat(v)));
        cycle(4, new String[]{"stand", "sit", "cross", "behind", "wave", "think"},
                new String[]{"De pie", "Sentado", "Brazos cruzados", "Manos atrás", "Saludando", "Pensativo"},
                () -> look.getString("pose"), v -> look.putString("pose", v));
        int half = (fw() - 4) / 2;
        addRenderableWidget(new Pill(fx(), rowY(5), half, 16, () -> "Copiar mi equipo", b -> copyEquip(false), () -> false));
        addRenderableWidget(new Pill(fx() + half + 4, rowY(5), half, 16, () -> "Quitar equipo", b -> copyEquip(true), () -> false));
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
        status(clear ? "Equipo quitado (al guardar)" : "Equipo copiado (al guardar)");
    }

    // ---------- pestaña Nombre ----------

    private void buildName() {
        box(fx(), rowY(0), fw(), look.getString("name"), 40, v -> { look.putString("name", v); changed(); });
        toggle(1, () -> !look.contains("showName") || look.getBoolean("showName"), v -> look.putBoolean("showName", v));
        cycle(2, new String[]{"minecraft", "tech", "medieval", "cartel"}, new String[]{"Minecraft", "Tech", "Medieval", "Cartel"},
                () -> look.getString("font"), v -> look.putString("font", v));
        cycle(3, new String[]{"vanilla", "plate", "holo", "ribbon", "banner"}, new String[]{"Vanilla", "Placa", "Holograma", "Cinta", "Banderín"},
                () -> look.getString("bg"), v -> look.putString("bg", v));
        if (look.getString("bg").equals("holo"))
            cycleAt(fx() + 0, rowY(4), fw(), new String[]{"glitch", "projection", "interference"}, new String[]{"Glitch de color", "Proyección", "Interferencia"},
                    () -> look.getString("holo"), v -> look.putString("holo", v));
        box(fx() + fw() - 60, rowY(5), 60, hex(look.getInt("c1")), 7, v -> { Integer c = parseHex(v); if (c != null) { look.putInt("c1", c); changed(); } });
        box(fx() + fw() - 60, rowY(6), 60, hex(look.contains("c2") ? look.getInt("c2") : 0xFFFFFF), 7, v -> { Integer c = parseHex(v); if (c != null) { look.putInt("c2", c); changed(); } });
        box(fx(), rowY(7), fw(), look.getString("title"), 32, v -> { look.putString("title", v); changed(); });
        cycle(8, new String[]{"none", "auto", "talk", "shop", "quest", "bank", "info"},
                new String[]{"Ninguno", "Automático", "Habla", "Tienda", "Misión", "Banco", "Info"},
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
        cycle(0, new String[]{"none", "chat", "tree"}, new String[]{"Sin diálogo", "Saludo en el chat", "Conversación (caja RPG)"},
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
            if (nodes.isEmpty()) { nodes.add(newNode("¡Hola! ¿En qué te puedo ayudar?")); }
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
                cycleAt(cx() + lw + 2, y, 84, new String[]{"goto", "shop", "close", "give", "pay"}, new String[]{"Ir a", "Tienda", "Cerrar", "Dar objeto", "Cobrar"},
                        () -> o.getString("act"), v -> o.putString("act", v));
                int px = cx() + lw + 88;
                String act = o.getString("act");
                if (act.equals("goto") || act.equals("pay") || act.equals("give")) {
                    String[] keys = new String[nodes.size() + 1];
                    String[] labels = new String[nodes.size() + 1];
                    keys[0] = "-1"; labels[0] = "fin";
                    for (int j = 0; j < nodes.size(); j++) { keys[j + 1] = String.valueOf(j); labels[j + 1] = "→" + (j + 1); }
                    cycleAt(px, y, 44, keys, labels, () -> String.valueOf(o.contains("target") ? o.getInt("target") : -1), v -> o.putInt("target", Integer.parseInt(v)));
                    px += 46;
                }
                if (act.equals("pay")) {
                    box(px, y, 40, String.valueOf(o.getLong("price")), 7, v -> o.putLong("price", parseLong(v)));
                } else if (act.equals("give")) {
                    addRenderableWidget(new Pill(px, y, 40, 16, () -> o.contains("item") && !ItemStack.of(o.getCompound("item")).isEmpty() ? "✔ mano" : "mano", b -> {
                        var p = Minecraft.getInstance().player;
                        if (p != null) o.put("item", p.getMainHandItem().copy().save(new CompoundTag()));
                        status("Objeto de tu mano guardado");
                    }, () -> false));
                }
                addRenderableWidget(new Pill(cx() + cw() - 16, y, 16, 16, () -> "✕", b -> { opts.remove(k); rebuild(); }, () -> false));
            }
            if (opts.size() < 4) addRenderableWidget(new Pill(cx(), rowY(4 + opts.size()), 110, 16, () -> "+ Respuesta", b -> {
                CompoundTag o = new CompoundTag();
                o.putString("label", "Adiós");
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

    private void buildShop() {
        ListTag shop = shop();
        offer = Math.max(0, Math.min(offer, shop.size() - 1));
        int lx = cx(), lw = 96;
        addRenderableWidget(new Pill(lx, rowY(0), 46, 16, () -> "+ Oferta", b -> {
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
            rebuild();
        }, () -> false));
        if (!shop.isEmpty()) addRenderableWidget(new Pill(lx + 50, rowY(0), 46, 16, () -> "Borrar", b -> { shop().remove(offer); offer = Math.max(0, offer - 1); rebuild(); }, () -> false));
        if (shop.isEmpty()) return;
        CompoundTag o = shop.getCompound(offer);
        int fx = cx() + lw + 6, fw = cw() - lw - 6;
        int y = rowY(0);
        cycleAt(fx, y, fw, new String[]{"sell", "buy"}, new String[]{"El NPC vende", "El NPC compra"}, () -> o.getString("mode"), v -> o.putString("mode", v));
        y += 21;
        addRenderableWidget(new Pill(fx, y, fw, 16, () -> {
            ItemStack it = ItemStack.of(o.getCompound("item"));
            return it.isEmpty() ? "Objeto: usar mi mano" : "Objeto: " + it.getCount() + "× " + it.getHoverName().getString();
        }, b -> {
            var p = Minecraft.getInstance().player;
            if (p != null && !p.getMainHandItem().isEmpty()) o.put("item", p.getMainHandItem().copy().save(new CompoundTag()));
        }, () -> false));
        y += 21;
        cycleAt(fx, y, fw, new String[]{"y", "item"}, new String[]{"Precio en yogurines", "Precio en objetos"},
                () -> o.getBoolean("payY") ? "y" : "item", v -> o.putBoolean("payY", v.equals("y")));
        y += 21;
        if (o.getBoolean("payY")) {
            box(fx + fw - 80, y, 80, String.valueOf(o.getLong("price")), 9, v -> o.putLong("price", parseLong(v)));
        } else {
            addRenderableWidget(new Pill(fx, y, fw, 16, () -> {
                ItemStack it = ItemStack.of(o.getCompound("priceItem"));
                return it.isEmpty() ? "Precio: usar mi otra mano" : "Precio: " + it.getCount() + "× " + it.getHoverName().getString();
            }, b -> {
                var p = Minecraft.getInstance().player;
                if (p != null && !p.getOffhandItem().isEmpty()) o.put("priceItem", p.getOffhandItem().copy().save(new CompoundTag()));
            }, () -> false));
        }
        y += 21;
        boolean unlimited = o.getInt("max") < 0;
        addRenderableWidget(new Pill(fx, y, 70, 16, () -> o.getInt("max") < 0 ? "Stock: ∞" : "Stock: límite", b -> {
            if (o.getInt("max") < 0) { o.putInt("max", 16); o.putInt("stock", 16); } else { o.putInt("max", -1); o.putInt("stock", -1); }
            rebuild();
        }, () -> o.getInt("max") >= 0));
        if (!unlimited) {
            box(fx + 74, y, 36, String.valueOf(o.getInt("max")), 5, v -> { int m = (int) parseLong(v); o.putInt("max", m); o.putInt("stock", m); });
        }
        y += 21;
        box(fx + fw - 50, y, 50, String.valueOf(o.getInt("restock")), 6, v -> o.putInt("restock", (int) parseLong(v)));
        y += 21;
        box(fx + fw - 50, y, 50, String.valueOf(o.getInt("limit")), 5, v -> o.putInt("limit", (int) parseLong(v)));
    }

    private static long parseLong(String v) {
        try { return Math.max(0, Long.parseLong(v.trim())); } catch (Exception e) { return 0; }
    }

    // ---------- pestaña Conducta ----------

    private void buildBehaviour() {
        toggle(0, () -> beh().getBoolean("look"), v -> beh().putBoolean("look", v));
        cycle(1, new String[]{"still", "wander", "home"}, new String[]{"Quieto", "Pasea por la zona", "Vuelve a su sitio"},
                () -> beh().getString("move"), v -> beh().putString("move", v));
        box(fx(), rowY(2), 40, String.valueOf(beh().getInt("radius")), 2, v -> beh().putInt("radius", (int) Math.max(1, Math.min(48, parseLong(v)))));
        toggle(3, () -> beh().getBoolean("invuln"), v -> beh().putBoolean("invuln", v));
        toggle(4, () -> beh().getBoolean("push"), v -> beh().putBoolean("push", v));
        addRenderableWidget(new Pill(fx(), rowY(5), fw(), 16, () -> homeHere ? "✔ Su sitio será donde está ahora" : "Poner su sitio donde está ahora", b -> homeHere = !homeHere, () -> homeHere));
        box(fx(), rowY(7), fw() - 70, tplName, 32, v -> tplName = v);
        addRenderableWidget(new Pill(fx() + fw() - 66, rowY(7), 66, 16, () -> "Guardar", b -> {
            if (tplName.isBlank()) { status("Ponle un nombre a la plantilla"); return; }
            save();
            CompoundTag t = new CompoundTag();
            t.putInt("id", npc.getId());
            t.putString("name", tplName.trim());
            Net.toServer("npc_tpl_save", t);
            status("Plantilla guardada: " + tplName.trim());
        }, () -> false));
        addRenderableWidget(new Pill(fx(), rowY(8), fw(), 16, () -> confirmDelete ? "¿Seguro? Pulsa otra vez para eliminar" : "Eliminar NPC", b -> {
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
        int x = px(), y = py(), w = pw(), h = ph();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xF51A1C21);
        g.fill(x + 112, y + 24, x + w - 4, y + h - 28, 0xFF33373F);
        g.fill(x + 112, y + 24, x + w - 4, y + 25, 0xFF7FD36A);
        // vista previa
        g.fill(x + 4, y + 6, x + 108, y + h - 28, 0xFF23262C);
        g.drawString(font, Nameplate.styled("VISTA PREVIA", "tech"), x + 10, y + 10, 0xFF7FD36A, false);
        int ex = x + 56, ey = y + h - 44;
        float sc = 48f / Math.max(0.6f, npc.look().scale);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, ex, ey, (int) sc, ex - mx, ey - 60 - my, npc);
        // etiquetas de cada pestaña
        switch (tab) {
            case 0 -> {
                label(g, 0, "Skin");
                String st = look.getString("skinType");
                label(g, 1, st.equals("player") ? "Jugador" : st.equals("url") ? "Enlace" : st.equals("file") ? "Archivo" : "");
                label(g, 2, "Brazos finos");
                label(g, 3, "Tamaño");
                label(g, 4, "Pose");
                label(g, 5, "Equipo");
                if (st.equals("file")) g.drawString(font, "Pon los .png en config/yogurines/skins del servidor", cx(), rowY(6) + 4, 0xFF777777, false);
            }
            case 1 -> {
                label(g, 0, "Nombre");
                label(g, 1, "Mostrar nombre");
                label(g, 2, "Tipografía");
                label(g, 3, "Fondo");
                if (look.getString("bg").equals("holo")) label(g, 4, "Efecto");
                label(g, 5, "Color fondo");
                label(g, 6, "Color texto");
                label(g, 7, "Título");
                label(g, 8, "Icono");
                swatches(g, 5, look.getInt("c1"));
                swatches(g, 6, look.contains("c2") ? look.getInt("c2") : 0xFFFFFF);
            }
            case 2 -> {
                label(g, 0, "Modo");
                label(g, 1, "Primera vez");
                String mode = dlg().getString("mode");
                if (mode.equals("chat")) for (int i = 0; i < 5; i++) label(g, 2 + i, "Frase " + (i + 1));
                if (mode.equals("tree")) label(g, 2, "Pantallas");
                if (mode.equals("none")) g.drawString(font, "Sin diálogo: al hacer clic abre la tienda (si tiene).", cx(), rowY(2) + 4, 0xFF777777, false);
            }
            case 3 -> {
                ListTag shop = shop();
                int lx = cx();
                for (int i = 0; i < shop.size() && i < 9; i++) {
                    CompoundTag o = shop.getCompound(i);
                    int ry = rowY(1) + i * 19;
                    boolean on = i == offer;
                    g.fill(lx, ry, lx + 96, ry + 18, on ? 0xFF2F5A28 : 0xFF262A31);
                    ItemStack it = ItemStack.of(o.getCompound("item"));
                    g.renderItem(it, lx + 1, ry + 1);
                    String tag = "buy".equals(o.getString("mode")) ? "Compra" : "Vende";
                    String price = o.getBoolean("payY") ? "Y" + o.getLong("price") : "objeto";
                    g.drawString(font, tag + " " + price, lx + 19, ry + 5, on ? 0xFFB8FFA8 : 0xFFDDDDDD, false);
                }
                if (shop.isEmpty()) g.drawString(font, "Pon el objeto en tu mano y pulsa + Oferta", lx, rowY(1) + 4, 0xFF777777, false);
                else {
                    int fx = cx() + 102;
                    if (shop.getCompound(offer).getBoolean("payY")) g.drawString(font, "Precio (Y)", fx, rowY(3) + 4, 0xFFAAAAAA, false);
                    g.drawString(font, "Reponer cada (min, 0 = nunca)", fx, rowY(5) + 4, 0xFFAAAAAA, false);
                    g.drawString(font, "Límite por jugador (0 = no)", fx, rowY(6) + 4, 0xFFAAAAAA, false);
                }
            }
            default -> {
                label(g, 0, "Mirar al jugador");
                label(g, 1, "Movimiento");
                label(g, 2, "Radio (bloques)");
                label(g, 3, "Invulnerable");
                label(g, 4, "Se puede empujar");
                label(g, 5, "Su sitio");
                g.drawString(font, Nameplate.styled("PLANTILLA", "tech"), cx(), rowY(6) + 6, 0xFF7FD36A, false);
                label(g, 7, "Nombre");
            }
        }
        if (!status.isEmpty() && System.currentTimeMillis() - statusTime < 2500)
            g.drawString(font, status, x + 10, y + h - 18, 0xFF9BE37F, false);
        super.render(g, mx, my, pt);
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
            for (int i = 0; i < shop.size() && i < 9; i++) {
                int ry = rowY(1) + i * 19;
                if (mx >= cx() && mx < cx() + 96 && my >= ry && my < ry + 18) {
                    offer = i;
                    rebuild();
                    PixelSound.tap();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
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
            g.drawCenteredString(font, s, getX() + width / 2, getY() + (height - 8) / 2, on ? 0xFFFFFFFF : 0xFFE0E0E0);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
