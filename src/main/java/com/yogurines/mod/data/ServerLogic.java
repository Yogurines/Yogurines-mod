package com.yogurines.mod.data;

import com.yogurines.mod.Tr;
import com.yogurines.mod.item.Money;
import com.yogurines.mod.item.MoneyItem;
import com.yogurines.mod.item.WalletItem;
import com.yogurines.mod.net.Net;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Lógica del servidor: recibe las acciones de los móviles, cajeros y carteras y las aplica. */
public final class ServerLogic {
    private static final Map<UUID, Map<String, byte[][]>> UPLOADS = new HashMap<>();
    private static final Map<UUID, Integer> CAMERA_MODE = new HashMap<>();
    private static final List<Object[]> TASKS = new ArrayList<>();
    private static MinecraftServer server;
    private static long tick;

    public static void setServer(MinecraftServer s) {
        server = s;
        TASKS.clear();
        UPLOADS.clear();
        CAMERA_MODE.clear();
    }

    public static MinecraftServer server() { return server; }

    public static void clearCamera(UUID u) {
        CAMERA_MODE.remove(u);
        UPLOADS.remove(u);
    }

    public static YoguData data() { return YoguData.get(server); }

    /** Ejecuta una tarea dentro de unos ticks (lo usan los perfiles falsos para "contestar"). */
    public static void later(int ticks, Runnable r) {
        TASKS.add(new Object[]{tick + ticks, r});
    }

    public static void serverTick() {
        tick++;
        if (TASKS.isEmpty()) return;
        List<Object[]> due = new ArrayList<>();
        Iterator<Object[]> it = TASKS.iterator();
        while (it.hasNext()) {
            Object[] t = it.next();
            if ((long) t[0] <= tick) { due.add(t); it.remove(); }
        }
        for (Object[] t : due) ((Runnable) t[1]).run();
    }

    public static String name(UUID u) {
        return data().names.getOrDefault(u, "?");
    }

    public static ServerPlayer online(UUID u) {
        return server == null ? null : server.getPlayerList().getPlayer(u);
    }

    // ---------- entrada de paquetes ----------

    public static void handle(ServerPlayer p, String type, CompoundTag t) {
        YoguData d = data();
        UUID me = p.getUUID();
        switch (type) {
            case "sync" -> sendSnap(p);
            case "img" -> {
                String id = t.getString("id");
                byte[] b = ImageStore.load(server, id);
                if (b == null && FakeProfiles.isResourceImage(id)) b = FakeProfiles.resourceImage(id);
                if (b != null) {
                    CompoundTag o = new CompoundTag();
                    o.putString("id", id);
                    o.putByteArray("b", b);
                    Net.toPlayer(p, "img", o);
                }
            }
            case "up" -> {
                String uid = t.getString("uid");
                int i = t.getInt("i"), n = t.getInt("n");
                if (n <= 0 || n > 40 || i < 0 || i >= n) return;
                Map<String, byte[][]> mine = UPLOADS.computeIfAbsent(me, k -> new HashMap<>());
                if (mine.size() > 4 && !mine.containsKey(uid)) mine.clear();
                byte[][] parts = mine.computeIfAbsent(uid, k -> new byte[n][]);
                if (parts.length != n) return;
                parts[i] = t.getByteArray("b");
            }
            case "profile" -> {
                YoguData.Profile pr = d.profile(me);
                pr.display = clean(t.getString("disp"), 24);
                if (pr.display.isEmpty()) pr.display = p.getGameProfile().getName();
                pr.bio = clean(t.getString("bio"), 60);
                pr.created = true;
                String uid = t.getString("avatar");
                if (!uid.isEmpty()) {
                    byte[] img = takeUpload(me, uid);
                    if (img != null) pr.avatar = ImageStore.save(server, img);
                }
                d.setDirty();
                broadcastSnap();
            }
            case "post" -> {
                byte[] img = takeUpload(me, t.getString("uid"));
                if (img == null) return;
                YoguData.Post post = new YoguData.Post();
                post.id = d.nextPostId++;
                post.author = me;
                post.img = ImageStore.save(server, img);
                post.caption = clean(t.getString("cap"), 140);
                post.time = System.currentTimeMillis();
                d.posts.add(post);
                d.setDirty();
                broadcastSnap();
                for (UUID f : followersOf(me)) notifyFrom(f, me, "social", "@" + name(me), Tr.of("ha publicado una foto nueva"));
                FakeProfiles.onPlayerPost(post);
            }
            case "like" -> {
                YoguData.Post post = d.post(t.getInt("id"));
                if (post == null) return;
                if (!post.likes.remove(me)) {
                    post.likes.add(me);
                    if (!post.author.equals(me)) notifyFrom(post.author, me, "social", "@" + name(me), Tr.of("le ha dado like a tu foto"));
                }
                d.setDirty();
                broadcastSnap();
            }
            case "comment" -> {
                YoguData.Post post = d.post(t.getInt("id"));
                String text = clean(t.getString("x"), 140);
                if (post == null || text.isEmpty()) return;
                YoguData.Comment c = new YoguData.Comment();
                c.author = me; c.text = text; c.time = System.currentTimeMillis();
                post.comments.add(c);
                d.setDirty();
                broadcastSnap();
                if (!post.author.equals(me)) notifyFrom(post.author, me, "social", "@" + name(me), Tr.of("ha comentado: \"%s\"", text));
            }
            case "follow" -> {
                UUID other = uuid(t.getString("u"));
                if (other == null || other.equals(me)) return;
                YoguData.Profile pr = d.profile(me);
                if (t.getBoolean("on")) {
                    if (pr.following.add(other)) notifyFrom(other, me, "social", "@" + name(me), Tr.of("ha empezado a seguirte"));
                } else pr.following.remove(other);
                d.setDirty();
                broadcastSnap();
            }
            case "msg" -> {
                UUID other = uuid(t.getString("to"));
                String text = clean(t.getString("x"), 200);
                if (other == null || text.isEmpty() || !d.names.containsKey(other)) return;
                if (d.profile(me).blocked.contains(other)) { err(p, Tr.of("Has bloqueado a esta persona")); return; }
                if (d.profile(other).blocked.contains(me)) { err(p, Tr.of("No puedes escribir a esta persona")); return; }
                d.addChat(me, other, text, "text");
                sendSnap(p);
                ServerPlayer op = online(other);
                if (op != null) sendSnap(op);
                notifyFrom(other, me, "chat", "@" + name(me), text);
                if (d.fakes.contains(other)) FakeProfiles.onMessage(other, me, text);
            }
            case "transfer" -> {
                UUID other = uuid(t.getString("to"));
                long amount = t.getLong("v");
                String concept = clean(t.getString("c"), 40);
                transfer(p, other, amount, concept);
            }
            case "hide" -> {
                if (t.getBoolean("on")) d.hiddenTags.add(me); else d.hiddenTags.remove(me);
                d.setDirty();
                sendHidden(null);
            }
            case "cam" -> {
                int mode = t.getInt("m");
                if (mode == 0) CAMERA_MODE.remove(me); else CAMERA_MODE.put(me, mode);
                CompoundTag o = new CompoundTag();
                o.putString("u", me.toString());
                o.putInt("m", mode);
                Net.toAll("pose", o);
            }
            case "mute", "block" -> {
                UUID other = uuid(t.getString("u"));
                if (other == null || other.equals(me)) return;
                Set<UUID> set = type.equals("mute") ? d.profile(me).muted : d.profile(me).blocked;
                if (t.getBoolean("on")) set.add(other); else set.remove(other);
                d.setDirty();
                sendSnap(p);
                ServerPlayer op = online(other);
                if (op != null) sendSnap(op);
            }
            case "bank_clear" -> {
                d.history.remove(me);
                d.setDirty();
                sendSnap(p);
            }
            case "atm_all" -> atmDepositAll(p);
            case "atm_take" -> atmWithdraw(p, t.getLong("v"));
            case "wal_store" -> walletStore(p, t.getBoolean("off") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            case "wal_take" -> walletTake(p, t.getBoolean("off") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, t.getInt("i"), t.getBoolean("all"));
            default -> {
                if (type.startsWith("npc_")) com.yogurines.mod.npc.NpcServer.handle(p, type, t);
                else if (type.startsWith("scr_")) com.yogurines.mod.scratch.ScratchServer.handle(p, type, t);
            }
        }
    }

    private static UUID uuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    private static String clean(String s, int max) {
        s = s == null ? "" : s.replaceAll("[\\p{Cntrl}§]", "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static byte[] takeUpload(UUID me, String uid) {
        Map<String, byte[][]> mine = UPLOADS.get(me);
        if (mine == null) return null;
        byte[][] parts = mine.remove(uid);
        if (parts == null) return null;
        int len = 0;
        for (byte[] b : parts) { if (b == null) return null; len += b.length; }
        if (len > ImageStore.MAX_BYTES) return null;
        byte[] out = new byte[len];
        int o = 0;
        for (byte[] b : parts) { System.arraycopy(b, 0, out, o, b.length); o += b.length; }
        return out;
    }

    public static List<UUID> followersOf(UUID u) {
        List<UUID> out = new ArrayList<>();
        data().profiles.forEach((k, pr) -> { if (pr.following.contains(u)) out.add(k); });
        return out;
    }

    // ---------- banco ----------

    public static void transfer(ServerPlayer p, UUID other, long amount, String concept) {
        YoguData d = data();
        UUID me = p.getUUID();
        if (other == null || other.equals(me) || !d.names.containsKey(other) || amount <= 0 || amount > d.bal(me)) {
            CompoundTag o = new CompoundTag();
            o.putString("r", amount > d.bal(me) ? Tr.of("Saldo insuficiente") : Tr.of("No se ha podido enviar"));
            Net.toPlayer(p, "bank_err", o);
            return;
        }
        d.addTx(me, -amount, other.toString(), concept);
        d.addTx(other, amount, me.toString(), concept);
        d.addChat(me, other, Money.fmt(amount) + " Y" + (concept.isEmpty() ? "" : " · " + concept), "transfer");
        sendSnap(p);
        ServerPlayer op = online(other);
        if (op != null) sendSnap(op);
        CompoundTag fx = new CompoundTag();
        fx.putString("k", "sent");
        Net.toPlayer(p, "fx", fx);
        notifyFrom(other, me, "bank", Tr.of("Banco Yogurín"), Tr.of("@%s te ha enviado %s Y", name(me), Money.fmt(amount)));
        if (d.fakes.contains(other)) FakeProfiles.onTransfer(other, me, amount);
    }

    /** Ingresa dinero en la cuenta de un jugador (cajero) y le manda los sonidos de la contadora. */
    public static void deposit(ServerPlayer p, long amount, int notes, int coins) {
        if (amount <= 0) return;
        YoguData d = data();
        d.addTx(p.getUUID(), amount, "", Tr.of("Ingreso en cajero"));
        sendSnap(p);
        CompoundTag fx = new CompoundTag();
        fx.putString("k", "deposit");
        fx.putInt("n", notes);
        fx.putInt("c", coins);
        fx.putLong("v", amount);
        Net.toPlayer(p, "fx", fx);
    }

    /** Clic derecho en el cajero con dinero en la mano: ingresa ese montón. */
    public static void depositHand(ServerPlayer p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (s.getItem() instanceof MoneyItem m) {
            int n = s.getCount();
            long v = (long) m.getValue() * n;
            s.shrink(n);
            deposit(p, v, m.getValue() > 1 ? n : 0, m.getValue() == 1 ? n : 0);
        } else if (s.getItem() instanceof WalletItem) {
            int[] c = WalletItem.counts(s);
            long v = WalletItem.total(s);
            int notes = 0;
            for (int i = 1; i < c.length; i++) notes += c[i];
            WalletItem.setCounts(s, new int[Money.VALUES.length]);
            deposit(p, v, notes, c[0]);
        }
    }

    private static void atmDepositAll(ServerPlayer p) {
        long v = 0;
        int notes = 0, coins = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof MoneyItem m) {
                v += (long) m.getValue() * s.getCount();
                if (m.getValue() == 1) coins += s.getCount(); else notes += s.getCount();
                inv.setItem(i, ItemStack.EMPTY);
            } else if (s.getItem() instanceof WalletItem) {
                int[] c = WalletItem.counts(s);
                v += WalletItem.total(s);
                coins += c[0];
                for (int k = 1; k < c.length; k++) notes += c[k];
                WalletItem.setCounts(s, new int[Money.VALUES.length]);
            }
        }
        if (v > 0) deposit(p, v, notes, coins);
    }

    private static void atmWithdraw(ServerPlayer p, long amount) {
        YoguData d = data();
        if (amount <= 0 || amount > d.bal(p.getUUID()) || amount > 100_000) {
            CompoundTag o = new CompoundTag();
            o.putString("r", amount > d.bal(p.getUUID()) ? Tr.of("Saldo insuficiente") : Tr.of("Cantidad no válida"));
            Net.toPlayer(p, "bank_err", o);
            return;
        }
        d.addTx(p.getUUID(), -amount, "", Tr.of("Retirada en cajero"));
        Money.give(p, amount);
        sendSnap(p);
        int[] parts = Money.breakdown(amount);
        int notes = 0;
        for (int i = 1; i < parts.length; i++) notes += parts[i];
        CompoundTag fx = new CompoundTag();
        fx.putString("k", "withdraw");
        fx.putInt("n", notes);
        fx.putInt("c", parts[0]);
        Net.toPlayer(p, "fx", fx);
    }

    // ---------- cartera ----------

    private static void walletStore(ServerPlayer p, InteractionHand hand) {
        ItemStack w = p.getItemInHand(hand);
        if (!(w.getItem() instanceof WalletItem)) return;
        int[] c = WalletItem.counts(w);
        var inv = p.getInventory();
        int moved = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof MoneyItem m) {
                int idx = Money.indexOf(m.getValue());
                c[idx] += s.getCount();
                moved += s.getCount();
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        WalletItem.setCounts(w, c);
        if (moved > 0) {
            CompoundTag fx = new CompoundTag();
            fx.putString("k", "wallet_in");
            Net.toPlayer(p, "fx", fx);
        }
    }

    private static void walletTake(ServerPlayer p, InteractionHand hand, int idx, boolean all) {
        ItemStack w = p.getItemInHand(hand);
        if (!(w.getItem() instanceof WalletItem) || idx < 0 || idx >= Money.VALUES.length) return;
        int[] c = WalletItem.counts(w);
        int n = all ? c[idx] : Math.min(1, c[idx]);
        if (n <= 0) return;
        c[idx] -= n;
        WalletItem.setCounts(w, c);
        while (n > 0) {
            int k = Math.min(64, n);
            ItemStack s = new ItemStack(Money.item(idx), k);
            if (!p.getInventory().add(s)) p.drop(s, false);
            n -= k;
        }
        CompoundTag fx = new CompoundTag();
        fx.putString("k", idx == 0 ? "coin" : "bill");
        Net.toPlayer(p, "fx", fx);
    }

    // ---------- avisos y sincronización ----------

    /** Aviso de algo que ha hecho otra persona: no llega si la tienes silenciada o bloqueada. */
    public static void notifyFrom(UUID to, UUID from, String app, String title, String text) {
        YoguData.Profile pr = data().profiles.get(to);
        if (pr != null && (pr.muted.contains(from) || pr.blocked.contains(from))) return;
        notify(to, app, title, text);
    }

    public static void err(ServerPlayer p, String text) {
        CompoundTag o = new CompoundTag();
        o.putString("r", text);
        Net.toPlayer(p, "err", o);
    }

    public static void notify(UUID to, String app, String title, String text) {
        ServerPlayer p = online(to);
        if (p == null) return;
        CompoundTag o = new CompoundTag();
        o.putString("app", app);
        o.putString("title", title);
        o.putString("text", text);
        Net.toPlayer(p, "notify", o);
    }

    public static void sendHidden(ServerPlayer only) {
        CompoundTag o = new CompoundTag();
        ListTag l = new ListTag();
        for (UUID u : data().hiddenTags) l.add(StringTag.valueOf(u.toString()));
        o.put("l", l);
        if (only != null) Net.toPlayer(only, "hidden", o); else Net.toAll("hidden", o);
    }

    public static void sendPoses(ServerPlayer to) {
        CAMERA_MODE.forEach((u, m) -> {
            CompoundTag o = new CompoundTag();
            o.putString("u", u.toString());
            o.putInt("m", m);
            Net.toPlayer(to, "pose", o);
        });
    }

    public static void broadcastSnap() {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendSnap(p);
    }

    public static void sendSnap(ServerPlayer p) {
        YoguData d = data();
        UUID me = p.getUUID();
        CompoundTag s = new CompoundTag();
        s.putString("me", me.toString());
        s.putLong("bal", d.bal(me));
        ListTag hist = new ListTag();
        for (YoguData.Tx x : d.history.getOrDefault(me, List.of())) hist.add(YoguData.writeTx(x));
        s.put("hist", hist);
        ListTag users = new ListTag();
        for (Map.Entry<UUID, String> e : d.names.entrySet()) {
            UUID u = e.getKey();
            YoguData.Profile pr = d.profiles.get(u);
            CompoundTag t = new CompoundTag();
            t.putString("u", u.toString());
            t.putString("n", e.getValue());
            t.putString("d", pr != null && !pr.display.isEmpty() ? pr.display : e.getValue());
            t.putString("b", pr != null ? pr.bio : "");
            t.putString("a", pr != null ? pr.avatar : "");
            t.putBoolean("c", pr != null && pr.created);
            t.putBoolean("fake", d.fakes.contains(u));
            YoguData.Profile mine0 = d.profiles.get(me);
            t.putBoolean("mu", mine0 != null && mine0.muted.contains(u));
            t.putBoolean("bl", mine0 != null && mine0.blocked.contains(u));
            t.putBoolean("bm", pr != null && pr.blocked.contains(me));
            t.putBoolean("on", d.fakes.contains(u) || online(u) != null);
            ListTag f = new ListTag();
            if (pr != null) for (UUID x : pr.following) f.add(StringTag.valueOf(x.toString()));
            t.put("f", f);
            users.add(t);
        }
        s.put("users", users);
        ListTag posts = new ListTag();
        int from = Math.max(0, d.posts.size() - 80);
        for (int i = from; i < d.posts.size(); i++) posts.add(YoguData.writePost(d.posts.get(i)));
        s.put("posts", posts);
        CompoundTag chats = new CompoundTag();
        String mine = me.toString();
        d.chats.forEach((k, l) -> {
            if (!k.contains(mine)) return;
            String other = k.startsWith(mine) ? k.substring(mine.length() + 1) : k.substring(0, k.indexOf('|'));
            ListTag lt = new ListTag();
            int f2 = Math.max(0, l.size() - 80);
            for (int i = f2; i < l.size(); i++) lt.add(YoguData.writeChat(l.get(i)));
            chats.put(other, lt);
        });
        s.put("chats", chats);
        Net.toPlayer(p, "snap", s);
    }

    private ServerLogic() {}
}
