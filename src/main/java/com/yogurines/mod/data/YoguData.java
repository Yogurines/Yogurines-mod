package com.yogurines.mod.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Todo lo que el servidor guarda del mod: banco, perfiles, publicaciones, chats y ajustes. */
public class YoguData extends SavedData {
    public static final String NAME = "yogurines";

    public static class Profile {
        public String display = "";
        public String bio = "";
        public String avatar = "";
        public boolean created;
        public final Set<UUID> following = new LinkedHashSet<>();
        public final Set<UUID> muted = new HashSet<>();
        public final Set<UUID> blocked = new HashSet<>();
    }

    public static class Comment {
        public UUID author; public String text; public long time;
    }

    public static class Post {
        public int id; public UUID author; public String img = ""; public String caption = ""; public long time;
        public final Set<UUID> likes = new LinkedHashSet<>();
        public final List<Comment> comments = new ArrayList<>();
    }

    public static class ChatMsg {
        public UUID from; public String text; public long time; public String kind = "text";
    }

    public static class Tx {
        public long time; public long amount; public String other = ""; public String concept = "";
    }

    public final Map<UUID, Long> balance = new HashMap<>();
    public final Map<UUID, String> names = new LinkedHashMap<>();
    public final Set<UUID> fakes = new LinkedHashSet<>();
    public final Map<UUID, Profile> profiles = new HashMap<>();
    public final List<Post> posts = new ArrayList<>();
    public final Map<String, List<ChatMsg>> chats = new HashMap<>();
    public final Map<UUID, List<Tx>> history = new HashMap<>();
    public final Set<UUID> hiddenTags = new HashSet<>();
    public final Set<UUID> welcomed = new HashSet<>();
    public boolean fakesSeeded;
    public int nextPostId = 1;

    public static YoguData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(YoguData::load, YoguData::new, NAME);
    }

    public Profile profile(UUID u) {
        return profiles.computeIfAbsent(u, k -> new Profile());
    }

    public long bal(UUID u) {
        return balance.getOrDefault(u, 0L);
    }

    public void addTx(UUID u, long amount, String other, String concept) {
        Tx t = new Tx();
        t.time = System.currentTimeMillis(); t.amount = amount; t.other = other; t.concept = concept;
        List<Tx> l = history.computeIfAbsent(u, k -> new ArrayList<>());
        l.add(t);
        while (l.size() > 60) l.remove(0);
        balance.put(u, bal(u) + amount);
        setDirty();
    }

    public static String chatKey(UUID a, UUID b) {
        String x = a.toString(), y = b.toString();
        return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
    }

    public ChatMsg addChat(UUID from, UUID to, String text, String kind) {
        ChatMsg m = new ChatMsg();
        m.from = from; m.text = text; m.time = System.currentTimeMillis(); m.kind = kind;
        List<ChatMsg> l = chats.computeIfAbsent(chatKey(from, to), k -> new ArrayList<>());
        l.add(m);
        while (l.size() > 200) l.remove(0);
        setDirty();
        return m;
    }

    public Post post(int id) {
        for (Post p : posts) if (p.id == id) return p;
        return null;
    }

    // ---------- guardado ----------

    private static ListTag uuids(Collection<UUID> set) {
        ListTag l = new ListTag();
        for (UUID u : set) l.add(StringTag.valueOf(u.toString()));
        return l;
    }

    private static void readUuids(ListTag l, Collection<UUID> into) {
        for (int i = 0; i < l.size(); i++) {
            try { into.add(UUID.fromString(l.getString(i))); } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag bal = new CompoundTag();
        balance.forEach((u, v) -> bal.putLong(u.toString(), v));
        tag.put("bal", bal);
        CompoundTag nm = new CompoundTag();
        names.forEach((u, v) -> nm.putString(u.toString(), v));
        tag.put("names", nm);
        tag.put("fakes", uuids(fakes));
        tag.put("hidden", uuids(hiddenTags));
        tag.put("welcomed", uuids(welcomed));
        tag.putBoolean("seeded", fakesSeeded);
        tag.putInt("nextPost", nextPostId);
        CompoundTag pr = new CompoundTag();
        profiles.forEach((u, p) -> {
            CompoundTag t = new CompoundTag();
            t.putString("d", p.display); t.putString("b", p.bio); t.putString("a", p.avatar); t.putBoolean("c", p.created);
            t.put("f", uuids(p.following));
            t.put("mu", uuids(p.muted));
            t.put("bl", uuids(p.blocked));
            pr.put(u.toString(), t);
        });
        tag.put("profiles", pr);
        ListTag ps = new ListTag();
        for (Post p : posts) ps.add(writePost(p));
        tag.put("posts", ps);
        CompoundTag ch = new CompoundTag();
        chats.forEach((k, l) -> {
            ListTag lt = new ListTag();
            for (ChatMsg m : l) lt.add(writeChat(m));
            ch.put(k, lt);
        });
        tag.put("chats", ch);
        CompoundTag hi = new CompoundTag();
        history.forEach((u, l) -> {
            ListTag lt = new ListTag();
            for (Tx t : l) lt.add(writeTx(t));
            hi.put(u.toString(), lt);
        });
        tag.put("history", hi);
        return tag;
    }

    public static CompoundTag writePost(Post p) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", p.id); t.putString("a", p.author.toString()); t.putString("img", p.img);
        t.putString("cap", p.caption); t.putLong("t", p.time);
        t.put("likes", uuids(p.likes));
        ListTag cl = new ListTag();
        for (Comment c : p.comments) {
            CompoundTag ct = new CompoundTag();
            ct.putString("a", c.author.toString()); ct.putString("x", c.text); ct.putLong("t", c.time);
            cl.add(ct);
        }
        t.put("com", cl);
        return t;
    }

    public static CompoundTag writeChat(ChatMsg m) {
        CompoundTag t = new CompoundTag();
        t.putString("f", m.from.toString()); t.putString("x", m.text); t.putLong("t", m.time); t.putString("k", m.kind);
        return t;
    }

    public static CompoundTag writeTx(Tx x) {
        CompoundTag t = new CompoundTag();
        t.putLong("t", x.time); t.putLong("v", x.amount); t.putString("o", x.other); t.putString("c", x.concept);
        return t;
    }

    public static YoguData load(CompoundTag tag) {
        YoguData d = new YoguData();
        CompoundTag bal = tag.getCompound("bal");
        for (String k : bal.getAllKeys()) d.balance.put(UUID.fromString(k), bal.getLong(k));
        CompoundTag nm = tag.getCompound("names");
        for (String k : nm.getAllKeys()) d.names.put(UUID.fromString(k), nm.getString(k));
        readUuids(tag.getList("fakes", Tag.TAG_STRING), d.fakes);
        readUuids(tag.getList("hidden", Tag.TAG_STRING), d.hiddenTags);
        readUuids(tag.getList("welcomed", Tag.TAG_STRING), d.welcomed);
        d.fakesSeeded = tag.getBoolean("seeded");
        d.nextPostId = Math.max(1, tag.getInt("nextPost"));
        CompoundTag pr = tag.getCompound("profiles");
        for (String k : pr.getAllKeys()) {
            CompoundTag t = pr.getCompound(k);
            Profile p = new Profile();
            p.display = t.getString("d"); p.bio = t.getString("b"); p.avatar = t.getString("a"); p.created = t.getBoolean("c");
            readUuids(t.getList("f", Tag.TAG_STRING), p.following);
            readUuids(t.getList("mu", Tag.TAG_STRING), p.muted);
            readUuids(t.getList("bl", Tag.TAG_STRING), p.blocked);
            d.profiles.put(UUID.fromString(k), p);
        }
        ListTag ps = tag.getList("posts", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) d.posts.add(readPost(ps.getCompound(i)));
        CompoundTag ch = tag.getCompound("chats");
        for (String k : ch.getAllKeys()) {
            ListTag lt = ch.getList(k, Tag.TAG_COMPOUND);
            List<ChatMsg> l = new ArrayList<>();
            for (int i = 0; i < lt.size(); i++) l.add(readChat(lt.getCompound(i)));
            d.chats.put(k, l);
        }
        CompoundTag hi = tag.getCompound("history");
        for (String k : hi.getAllKeys()) {
            ListTag lt = hi.getList(k, Tag.TAG_COMPOUND);
            List<Tx> l = new ArrayList<>();
            for (int i = 0; i < lt.size(); i++) {
                CompoundTag t = lt.getCompound(i);
                Tx x = new Tx();
                x.time = t.getLong("t"); x.amount = t.getLong("v"); x.other = t.getString("o"); x.concept = t.getString("c");
                l.add(x);
            }
            d.history.put(UUID.fromString(k), l);
        }
        return d;
    }

    public static Post readPost(CompoundTag t) {
        Post p = new Post();
        p.id = t.getInt("id"); p.author = UUID.fromString(t.getString("a")); p.img = t.getString("img");
        p.caption = t.getString("cap"); p.time = t.getLong("t");
        readUuids(t.getList("likes", Tag.TAG_STRING), p.likes);
        ListTag cl = t.getList("com", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) {
            CompoundTag ct = cl.getCompound(i);
            Comment c = new Comment();
            c.author = UUID.fromString(ct.getString("a")); c.text = ct.getString("x"); c.time = ct.getLong("t");
            p.comments.add(c);
        }
        return p;
    }

    public static ChatMsg readChat(CompoundTag t) {
        ChatMsg m = new ChatMsg();
        m.from = UUID.fromString(t.getString("f")); m.text = t.getString("x"); m.time = t.getLong("t"); m.kind = t.getString("k");
        return m;
    }
}
