package com.yogurines.mod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

/** Lo que el cliente sabe del servidor (última "foto" de datos) y los ajustes locales del móvil. */
public final class ClientData {
    public static class User {
        public UUID id; public String nick = "", display = "", bio = "", avatar = "";
        public boolean created, fake, online;
        public final Set<UUID> following = new HashSet<>();
    }

    public static class Comment { public UUID author; public String text; public long time; }

    public static class Post {
        public int id; public UUID author; public String img, caption; public long time;
        public final Set<UUID> likes = new HashSet<>();
        public final List<Comment> comments = new ArrayList<>();
    }

    public static class Msg { public UUID from; public String text, kind; public long time; }

    public static class Tx { public long time, amount; public String other, concept; }

    public static UUID me;
    public static long balance;
    public static boolean synced;
    public static final List<Tx> history = new ArrayList<>();
    public static final Map<UUID, User> users = new LinkedHashMap<>();
    public static final List<Post> posts = new ArrayList<>();
    public static final Map<UUID, List<Msg>> chats = new HashMap<>();
    public static final Set<UUID> hiddenTags = new HashSet<>();
    public static final Map<UUID, Integer> poses = new HashMap<>();
    public static String bankError = "";
    public static long bankErrorTime;
    public static int version;

    // ajustes locales
    public static String wallpaper = "0";
    public static boolean notifChat = true, notifSocial = true, notifBank = true;
    private static boolean loaded;

    public static void readSnap(CompoundTag s) {
        me = uuid(s.getString("me"));
        balance = s.getLong("bal");
        history.clear();
        ListTag h = s.getList("hist", Tag.TAG_COMPOUND);
        for (int i = 0; i < h.size(); i++) {
            CompoundTag t = h.getCompound(i);
            Tx x = new Tx();
            x.time = t.getLong("t"); x.amount = t.getLong("v"); x.other = t.getString("o"); x.concept = t.getString("c");
            history.add(x);
        }
        users.clear();
        ListTag us = s.getList("users", Tag.TAG_COMPOUND);
        for (int i = 0; i < us.size(); i++) {
            CompoundTag t = us.getCompound(i);
            User u = new User();
            u.id = uuid(t.getString("u"));
            if (u.id == null) continue;
            u.nick = t.getString("n"); u.display = t.getString("d"); u.bio = t.getString("b"); u.avatar = t.getString("a");
            u.created = t.getBoolean("c"); u.fake = t.getBoolean("fake"); u.online = t.getBoolean("on");
            ListTag f = t.getList("f", Tag.TAG_STRING);
            for (int k = 0; k < f.size(); k++) { UUID x = uuid(f.getString(k)); if (x != null) u.following.add(x); }
            users.put(u.id, u);
        }
        posts.clear();
        ListTag ps = s.getList("posts", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            CompoundTag t = ps.getCompound(i);
            Post p = new Post();
            p.id = t.getInt("id"); p.author = uuid(t.getString("a")); p.img = t.getString("img");
            p.caption = t.getString("cap"); p.time = t.getLong("t");
            ListTag l = t.getList("likes", Tag.TAG_STRING);
            for (int k = 0; k < l.size(); k++) { UUID x = uuid(l.getString(k)); if (x != null) p.likes.add(x); }
            ListTag c = t.getList("com", Tag.TAG_COMPOUND);
            for (int k = 0; k < c.size(); k++) {
                CompoundTag ct = c.getCompound(k);
                Comment cm = new Comment();
                cm.author = uuid(ct.getString("a")); cm.text = ct.getString("x"); cm.time = ct.getLong("t");
                p.comments.add(cm);
            }
            if (p.author != null) posts.add(p);
        }
        posts.sort((a, b) -> Long.compare(b.time, a.time));
        chats.clear();
        CompoundTag ch = s.getCompound("chats");
        for (String k : ch.getAllKeys()) {
            UUID other = uuid(k);
            if (other == null) continue;
            ListTag lt = ch.getList(k, Tag.TAG_COMPOUND);
            List<Msg> l = new ArrayList<>();
            for (int i = 0; i < lt.size(); i++) {
                CompoundTag t = lt.getCompound(i);
                Msg m = new Msg();
                m.from = uuid(t.getString("f")); m.text = t.getString("x"); m.time = t.getLong("t"); m.kind = t.getString("k");
                l.add(m);
            }
            chats.put(other, l);
        }
        synced = true;
        version++;
    }

    public static User user(UUID u) {
        return u == null ? null : users.get(u);
    }

    public static User self() {
        return user(me);
    }

    public static String display(UUID u) {
        User x = user(u);
        return x == null ? "?" : x.display;
    }

    public static String nick(UUID u) {
        User x = user(u);
        return x == null ? "?" : x.nick;
    }

    public static int followers(UUID u) {
        int n = 0;
        for (User x : users.values()) if (x.following.contains(u)) n++;
        return n;
    }

    public static UUID uuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public static void clear() {
        synced = false;
        users.clear(); posts.clear(); chats.clear(); history.clear(); poses.clear(); hiddenTags.clear();
        balance = 0;
    }

    // ---------- ajustes ----------

    private static File file() {
        File d = new File(Minecraft.getInstance().gameDirectory, "yogurines");
        d.mkdirs();
        return new File(d, "movil.properties");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Properties p = new Properties();
        try (FileReader r = new FileReader(file())) { p.load(r); } catch (Exception ignored) {}
        wallpaper = p.getProperty("fondo", "0");
        notifChat = !"false".equals(p.getProperty("avisos_chat"));
        notifSocial = !"false".equals(p.getProperty("avisos_yogugram"));
        notifBank = !"false".equals(p.getProperty("avisos_banco"));
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("fondo", wallpaper);
        p.setProperty("avisos_chat", Boolean.toString(notifChat));
        p.setProperty("avisos_yogugram", Boolean.toString(notifSocial));
        p.setProperty("avisos_banco", Boolean.toString(notifBank));
        try (FileWriter w = new FileWriter(file())) { p.store(w, "Ajustes del movil Yogurines"); } catch (Exception ignored) {}
    }

    private ClientData() {}
}
