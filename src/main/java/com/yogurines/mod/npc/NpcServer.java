package com.yogurines.mod.npc;

import com.yogurines.mod.data.ServerLogic;
import com.yogurines.mod.data.YoguData;
import com.yogurines.mod.item.Money;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Lógica de servidor de los NPCs: editor, plantillas, diálogos y tienda. */
public final class NpcServer {
    private static final Random RNG = new Random();

    public static boolean isAdmin(ServerPlayer p) {
        return p.hasPermissions(2);
    }

    private static void deny(ServerPlayer p) {
        p.displayClientMessage(Component.translatable("yogurines.npc.onlyOp").withStyle(ChatFormatting.RED), true);
    }

    public static Path skinDir() {
        return FMLPaths.CONFIGDIR.get().resolve("yogurines").resolve("skins");
    }

    private static List<String> skinFiles() {
        List<String> out = new ArrayList<>();
        try {
            Path d = skinDir();
            Files.createDirectories(d);
            try (var s = Files.list(d)) {
                s.map(x -> x.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                        .map(n -> n.substring(0, n.length() - 4)).sorted().limit(200).forEach(out::add);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static NpcEntity find(ServerPlayer p, int id, double maxDist) {
        Entity e = p.level().getEntity(id);
        if (e instanceof NpcEntity n && n.distanceTo(p) <= maxDist) return n;
        return null;
    }

    // ---------- entrada de paquetes ----------

    public static void handle(ServerPlayer p, String type, CompoundTag t) {
        switch (type) {
            case "npc_save" -> save(p, t);
            case "npc_delete" -> {
                if (!isAdmin(p)) { deny(p); return; }
                NpcEntity n = find(p, t.getInt("id"), 24);
                if (n != null) n.discard();
            }
            case "npc_tpl_save" -> {
                if (!isAdmin(p)) { deny(p); return; }
                NpcEntity n = find(p, t.getInt("id"), 24);
                String name = clean(t.getString("name"), 32);
                if (n == null || name.isEmpty()) return;
                NpcTemplates.get(p.server).put(name, n.templateTag());
                p.displayClientMessage(Component.translatable("yogurines.npc.tplSaved", name).withStyle(ChatFormatting.GREEN), true);
                sendTemplates(p, false);
            }
            case "npc_tpl_del" -> {
                if (!isAdmin(p)) { deny(p); return; }
                NpcTemplates.get(p.server).remove(t.getString("name"));
                sendTemplates(p, true);
            }
            case "npc_tpl_pick" -> {
                if (!isAdmin(p)) { deny(p); return; }
                ItemStack s = p.getMainHandItem();
                if (!(s.getItem() instanceof NpcWandItem)) return;
                String name = t.getString("name");
                if (name.isEmpty()) s.removeTagKey("tpl"); else s.getOrCreateTag().putString("tpl", name);
                p.displayClientMessage((name.isEmpty() ? Component.translatable("yogurines.npc.wandBlank") : Component.translatable("yogurines.npc.wandTpl", name)).withStyle(ChatFormatting.GREEN), true);
            }
            case "npc_skinfile" -> sendSkinFile(p, t.getString("name"));
            case "npc_shop_open" -> {
                NpcEntity n = find(p, t.getInt("id"), 10);
                if (n != null) openShop(p, n);
            }
            case "npc_buy" -> {
                NpcEntity n = find(p, t.getInt("id"), 10);
                if (n != null) buy(p, n, t.getInt("i"), t.getBoolean("bank"));
            }
            case "npc_act" -> {
                NpcEntity n = find(p, t.getInt("id"), 10);
                if (n != null) action(p, n, t.getInt("node"), t.getInt("opt"));
            }
            default -> {}
        }
    }

    private static String clean(String s, int max) {
        s = s.replace('\n', ' ').replace('§', ' ').trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ---------- crear y editar ----------

    public static void spawn(ServerPlayer p, BlockPos pos, String tpl) {
        if (!isAdmin(p)) { deny(p); return; }
        ServerLevel level = p.serverLevel();
        NpcEntity n = ModEntities.NPC.get().create(level);
        if (n == null) return;
        float yaw = p.getYRot() + 180f;
        n.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        n.setYHeadRot(yaw);
        n.setYBodyRot(yaw);
        CompoundTag t = tpl.isEmpty() ? null : NpcTemplates.get(p.server).get(tpl);
        if (t != null) n.applyTemplate(t);
        else {
            CompoundTag look = NpcEntity.defaultLook();
            look.putString("name", "NPC " + (1 + RNG.nextInt(99)));
            n.setLook(look);
        }
        n.setHome(pos);
        n.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWN_EGG, null, null);
        level.addFreshEntity(n);
        if (t == null) openEditor(p, n);
    }

    public static void openEditor(ServerPlayer p, NpcEntity n) {
        if (!isAdmin(p)) { deny(p); talk(p, n); return; }
        CompoundTag o = new CompoundTag();
        o.putInt("id", n.getId());
        o.put("look", n.lookTag().copy());
        o.put("cfg", n.cfg().copy());
        o.put("equip", n.equipTag());
        ListTag skins = new ListTag();
        for (String s : skinFiles()) skins.add(StringTag.valueOf(s));
        o.put("skins", skins);
        Net.toPlayer(p, "npc_edit", o);
    }

    private static void save(ServerPlayer p, CompoundTag t) {
        if (!isAdmin(p)) { deny(p); return; }
        NpcEntity n = find(p, t.getInt("id"), 24);
        if (n == null) return;
        CompoundTag look = NpcEntity.defaultLook();
        look.merge(t.getCompound("look"));
        look.putString("name", clean(look.getString("name"), 40));
        look.putString("title", clean(look.getString("title"), 32));
        look.putString("skin", clean(look.getString("skin"), 200));
        CompoundTag cfg = t.getCompound("cfg").copy();
        cfg.put("talked", n.cfg().getList("talked", Tag.TAG_STRING));
        // conservar contadores por jugador de las ofertas que no cambian de objeto
        ListTag old = n.shop();
        ListTag shop = cfg.getList("shop", Tag.TAG_COMPOUND);
        while (shop.size() > 40) shop.remove(shop.size() - 1);
        for (int i = 0; i < shop.size(); i++) {
            CompoundTag o = shop.getCompound(i);
            if (i < old.size() && old.getCompound(i).getCompound("item").equals(o.getCompound("item")) && old.getCompound(i).contains("used"))
                o.put("used", old.getCompound(i).getCompound("used"));
            if (!o.contains("last")) o.putLong("last", p.level().getGameTime());
        }
        CompoundTag dlg = cfg.getCompound("dlg");
        String mode = dlg.getString("mode");
        boolean hasDialog = mode.equals("chat") ? !dlg.getList("greet", Tag.TAG_STRING).isEmpty()
                : mode.equals("tree") && !dlg.getList("nodes", Tag.TAG_COMPOUND).isEmpty();
        look.putBoolean("hs", !shop.isEmpty());
        look.putBoolean("hd", hasDialog);
        n.setLook(look);
        n.setCfg(cfg);
        if (t.contains("equip")) n.setEquip(t.getList("equip", Tag.TAG_COMPOUND));
        if (t.getBoolean("homeHere")) n.setHome(n.blockPosition());
        else if (n.home() == null) n.setHome(n.blockPosition());
        p.displayClientMessage(Component.translatable("yogurines.npc.saved").withStyle(ChatFormatting.GREEN), true);
    }

    public static void sendTemplates(ServerPlayer p, boolean open) {
        CompoundTag o = new CompoundTag();
        ListTag l = new ListTag();
        for (String s : NpcTemplates.get(p.server).names()) l.add(StringTag.valueOf(s));
        o.put("l", l);
        o.putBoolean("open", open);
        ItemStack s = p.getMainHandItem();
        o.putString("cur", s.hasTag() ? s.getTag().getString("tpl") : "");
        Net.toPlayer(p, "npc_tpls", o);
    }

    private static void sendSkinFile(ServerPlayer p, String name) {
        if (!name.matches("[A-Za-z0-9_\\- ]{1,48}")) return;
        try {
            Path f = skinDir().resolve(name + ".png");
            if (!Files.isRegularFile(f) || Files.size(f) > 256 * 1024) return;
            CompoundTag o = new CompoundTag();
            o.putString("name", name);
            o.putByteArray("b", Files.readAllBytes(f));
            Net.toPlayer(p, "npc_skinfile", o);
        } catch (Exception ignored) {}
    }

    // ---------- hablar ----------

    public static void talk(ServerPlayer p, NpcEntity n) {
        CompoundTag dlg = n.dlg();
        String mode = dlg.getString("mode");
        boolean hasShop = !n.shop().isEmpty();
        NpcEntity.Look look = n.look();
        ListTag talked = n.cfg().getList("talked", Tag.TAG_STRING);
        boolean first = !talked.contains(StringTag.valueOf(p.getStringUUID()));
        if (first) {
            talked.add(StringTag.valueOf(p.getStringUUID()));
            while (talked.size() > 500) talked.remove(0);
            n.cfg().put("talked", talked);
        }
        n.getLookControl().setLookAt(p, 30f, 30f);
        if (mode.equals("chat")) {
            ListTag greet = dlg.getList("greet", Tag.TAG_STRING);
            if (!greet.isEmpty()) {
                String line = first && !dlg.getString("first").isEmpty() ? dlg.getString("first") : greet.getString(RNG.nextInt(greet.size()));
                p.sendSystemMessage(Component.literal(look.name.isEmpty() ? "NPC" : look.name)
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(brighten(look.c1))).withBold(true))
                        .append(Component.literal(": " + line).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(false))));
                CompoundTag fx = new CompoundTag();
                fx.putString("k", "npc_chat");
                Net.toPlayer(p, "fx", fx);
            }
            if (hasShop) openShop(p, n);
            return;
        }
        if (mode.equals("tree") && !dlg.getList("nodes", Tag.TAG_COMPOUND).isEmpty()) {
            CompoundTag o = new CompoundTag();
            o.putInt("id", n.getId());
            o.put("nodes", dlg.getList("nodes", Tag.TAG_COMPOUND).copy());
            o.putString("first", first ? dlg.getString("first") : "");
            o.putBoolean("shop", hasShop);
            Net.toPlayer(p, "npc_dialog", o);
            return;
        }
        if (hasShop) openShop(p, n);
    }

    private static int brighten(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int max = Math.max(r, Math.max(g, b));
        if (max < 120) { r = r + (255 - r) / 2; g = g + (255 - g) / 2; b = b + (255 - b) / 2; }
        return (r << 16) | (g << 8) | b;
    }

    /** Acción de una respuesta del diálogo que necesita al servidor (dar objeto o cobrar). */
    private static void action(ServerPlayer p, NpcEntity n, int node, int opt) {
        ListTag nodes = n.dlg().getList("nodes", Tag.TAG_COMPOUND);
        if (node < 0 || node >= nodes.size()) return;
        ListTag opts = nodes.getCompound(node).getList("opts", Tag.TAG_COMPOUND);
        if (opt < 0 || opt >= opts.size()) return;
        CompoundTag o = opts.getCompound(opt);
        CompoundTag res = new CompoundTag();
        res.putInt("goto", o.getInt("target"));
        switch (o.getString("act")) {
            case "give" -> {
                ItemStack s = ItemStack.of(o.getCompound("item"));
                if (!s.isEmpty()) Cash.giveItem(p, s);
                res.putBoolean("ok", true);
            }
            case "pay" -> {
                long v = Math.max(0, o.getLong("price"));
                YoguData d = ServerLogic.data();
                boolean ok;
                if (Cash.count(p) >= v) ok = Cash.take(p, v);
                else if (d.bal(p.getUUID()) >= v) {
                    d.addTx(p.getUUID(), -v, "", "Pago a " + n.look().name);
                    ServerLogic.sendSnap(p);
                    ok = true;
                } else ok = false;
                res.putBoolean("ok", ok);
                if (!ok) { res.putString("msg", "dialog.noMoney"); res.putString("arg", Money.fmt(v)); }
            }
            default -> res.putBoolean("ok", true);
        }
        Net.toPlayer(p, "npc_act_res", res);
    }

    // ---------- tienda ----------

    private static void restock(CompoundTag o, long now) {
        int mins = o.getInt("restock");
        if (mins <= 0) return;
        if (now - o.getLong("last") >= mins * 1200L) {
            if (o.getInt("max") >= 0) o.putInt("stock", o.getInt("max"));
            o.remove("used");
            o.putLong("last", now);
        }
    }

    public static void openShop(ServerPlayer p, NpcEntity n) {
        sendShop(p, n, true, "", false);
    }

    private static void sendShop(ServerPlayer p, NpcEntity n, boolean open, String msg, boolean ok) {
        long now = p.level().getGameTime();
        ListTag shop = n.shop();
        ListTag out = new ListTag();
        for (int i = 0; i < shop.size(); i++) {
            CompoundTag o = shop.getCompound(i);
            restock(o, now);
            CompoundTag c = new CompoundTag();
            c.putString("mode", o.getString("mode"));
            c.put("item", o.getCompound("item"));
            c.putBoolean("payY", o.getBoolean("payY"));
            c.putLong("price", o.getLong("price"));
            c.put("priceItem", o.getCompound("priceItem"));
            c.putInt("max", o.getInt("max"));
            c.putInt("stock", o.getInt("stock"));
            int limit = o.getInt("limit");
            c.putInt("left", limit <= 0 ? -1 : Math.max(0, limit - o.getCompound("used").getInt(p.getStringUUID())));
            out.add(c);
        }
        CompoundTag t = new CompoundTag();
        t.putInt("id", n.getId());
        t.putString("name", n.look().name);
        t.put("offers", out);
        t.putLong("cash", Cash.count(p));
        t.putLong("bank", ServerLogic.data().bal(p.getUUID()));
        t.putBoolean("open", open);
        t.putString("msg", msg);
        t.putBoolean("ok", ok);
        Net.toPlayer(p, "npc_shop", t);
    }

    private static void buy(ServerPlayer p, NpcEntity n, int i, boolean bank) {
        ListTag shop = n.shop();
        if (i < 0 || i >= shop.size()) return;
        CompoundTag o = shop.getCompound(i);
        long now = p.level().getGameTime();
        restock(o, now);
        ItemStack item = ItemStack.of(o.getCompound("item"));
        if (item.isEmpty()) { sendShop(p, n, false, "shop.err.empty", false); return; }
        int max = o.getInt("max"), stock = o.getInt("stock");
        if (max >= 0 && stock <= 0) { sendShop(p, n, false, "shop.err.soldOut", false); return; }
        int limit = o.getInt("limit");
        CompoundTag used = o.getCompound("used");
        String me = p.getStringUUID();
        if (limit > 0 && used.getInt(me) >= limit) { sendShop(p, n, false, "shop.err.limit", false); return; }
        boolean payY = o.getBoolean("payY");
        long price = Math.max(0, o.getLong("price"));
        ItemStack priceItem = ItemStack.of(o.getCompound("priceItem"));
        YoguData d = ServerLogic.data();
        boolean sell = !"buy".equals(o.getString("mode"));
        String npcName = n.look().name;
        if (sell) {
            // el NPC vende: el jugador paga
            if (payY) {
                if (bank) {
                    if (d.bal(p.getUUID()) < price) { sendShop(p, n, false, "shop.err.bank", false); return; }
                    d.addTx(p.getUUID(), -price, "", "Compra a " + npcName);
                    ServerLogic.sendSnap(p);
                } else if (!Cash.take(p, price)) { sendShop(p, n, false, "shop.err.cash", false); return; }
            } else {
                if (priceItem.isEmpty() || Cash.countItem(p, priceItem) < priceItem.getCount()) { sendShop(p, n, false, "shop.err.price", false); return; }
                Cash.takeItem(p, priceItem, priceItem.getCount());
            }
            Cash.giveItem(p, item);
        } else {
            // el NPC compra: el jugador entrega el objeto
            if (Cash.countItem(p, item) < item.getCount()) { sendShop(p, n, false, "shop.err.noItem", false); return; }
            Cash.takeItem(p, item, item.getCount());
            if (payY) {
                if (bank) {
                    d.addTx(p.getUUID(), price, "", "Venta a " + npcName);
                    ServerLogic.sendSnap(p);
                } else Money.give(p, price);
            } else if (!priceItem.isEmpty()) Cash.giveItem(p, priceItem);
        }
        if (max >= 0) o.putInt("stock", stock - 1);
        if (limit > 0) { used.putInt(me, used.getInt(me) + 1); o.put("used", used); }
        p.containerMenu.broadcastChanges();
        sendShop(p, n, false, sell ? "shop.ok.bought" : "shop.ok.sold", true);
    }

    private NpcServer() {}
}
