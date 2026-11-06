package com.yogurines.mod.data;

import com.yogurines.mod.Tr;
import com.yogurines.mod.item.Money;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Tres perfiles de prueba para que el móvil no esté vacío: publican fotos, dan likes,
 * contestan mensajes y agradecen las transferencias.
 */
public final class FakeProfiles {
    public record Fake(UUID id, String nick, String display, String bio, String avatar, String[] posts, String[] captions, String[] replies) {}

    private static UUID uid(String nick) {
        return UUID.nameUUIDFromBytes(("yogurines-fake:" + nick).getBytes(StandardCharsets.UTF_8));
    }

    public static final Fake LUNA = new Fake(uid("LunaPixel"), "LunaPixel", "Luna", Tr.of("Constructora de casitas bonitas ☾"),
            "luna", new String[]{"post_luna1", "post_luna2"},
            new String[]{Tr.of("Mi casita nueva junto al lago ⌂"), Tr.of("Atardecer desde la montaña ✦")},
            new String[]{Tr.of("¡Holaa! ¿Qué tal todo? ☺"), Tr.of("Jajaja me encanta"), Tr.of("¿Quedamos luego en el spawn?"), Tr.of("Ahora mismo estoy construyendo, luego te escribo ⚒"), Tr.of("¡Qué guay!")});
    public static final Fake CREEPER = new Fake(uid("CreeperKing99"), "CreeperKing99", "Creeper King", Tr.of("Sssss... ✸ Rey de las explosiones"),
            "creeper", new String[]{"post_creeper1", "post_creeper2"},
            new String[]{Tr.of("Nueva granja de pólvora ✸"), Tr.of("Encontré diamantes en la cueva ◆")},
            new String[]{Tr.of("Sssss... ✸"), Tr.of("¿Te vienes a minar?"), Tr.of("No toques mi TNT"), Tr.of("jajaja buena esa"), Tr.of("Estoy en la mina, cambio y corto")});
    public static final Fake FAN = new Fake(uid("YoguFan"), "YoguFan", "Yogurín Fan", Tr.of("Fan nº1 de Yotu, Samu y Metta ♥"),
            "yogufan", new String[]{"post_yogufan1"},
            new String[]{Tr.of("¡Mi colección de billetes Yogurines! $")},
            new String[]{Tr.of("¡¡Holaa!! ♥"), Tr.of("¿Has visto el billete de 500? Es una pasada"), Tr.of("Yo ahorro todo en el banco Yogurín $"), Tr.of("¡Me encanta este server!"), Tr.of("Te sigo ☺")});

    public static final List<Fake> ALL = List.of(LUNA, CREEPER, FAN);

    private static final Random RNG = new Random();

    public static Fake byId(UUID u) {
        for (Fake f : ALL) if (f.id().equals(u)) return f;
        return null;
    }

    public static boolean isResourceImage(String id) {
        return id != null && id.startsWith("fake_") && id.substring(5).matches("[a-z0-9_]{1,32}");
    }

    public static byte[] resourceImage(String id) {
        if (!isResourceImage(id)) return null;
        try (InputStream in = FakeProfiles.class.getResourceAsStream("/data/yogurines/fake/" + id.substring(5) + ".png")) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    /** Se llama al arrancar el servidor: crea los perfiles y sus fotos la primera vez. */
    public static void seed() {
        YoguData d = ServerLogic.data();
        for (Fake f : ALL) {
            d.names.put(f.id(), f.nick());
            d.fakes.add(f.id());
            YoguData.Profile p = d.profile(f.id());
            p.display = f.display();
            p.bio = f.bio();
            p.avatar = "fake_" + f.avatar();
            p.created = true;
        }
        if (!d.fakesSeeded) {
            d.fakesSeeded = true;
            long now = System.currentTimeMillis();
            long[] ago = {26, 20, 9, 5, 2};
            int k = 0;
            // Se intercalan para que el feed parezca natural.
            Object[][] order = {{LUNA, 0}, {CREEPER, 0}, {FAN, 0}, {LUNA, 1}, {CREEPER, 1}};
            for (Object[] o : order) {
                Fake f = (Fake) o[0];
                int i = (int) o[1];
                YoguData.Post post = new YoguData.Post();
                post.id = d.nextPostId++;
                post.author = f.id();
                post.img = "fake_" + f.posts()[i];
                post.caption = f.captions()[i];
                post.time = now - ago[k++] * 3_600_000L;
                for (Fake other : ALL) if (other != f) post.likes.add(other.id());
                YoguData.Comment c = new YoguData.Comment();
                Fake by = ALL.get((ALL.indexOf(f) + 1) % ALL.size());
                c.author = by.id();
                c.text = pick(new String[]{Tr.of("¡Qué pasada! ♥"), Tr.of("Brutal ✦"), Tr.of("Me encanta"), Tr.of("Quiero ir")});
                c.time = post.time + 600_000L;
                post.comments.add(c);
                d.posts.add(post);
            }
            for (Fake f : ALL) {
                for (Fake other : ALL) if (other != f) d.profile(f.id()).following.add(other.id());
                if (d.bal(f.id()) <= 0) d.addTx(f.id(), 1000, "", Tr.of("Saldo inicial"));
            }
            d.addChat(LUNA.id(), CREEPER.id(), Tr.of("¿Me devuelves mi pico de diamante?"), "text");
            d.addChat(CREEPER.id(), LUNA.id(), Tr.of("Sssss... no sé de qué me hablas ✸"), "text");
        }
        d.setDirty();
    }

    /** Primera vez que un jugador entra: Luna le saluda y le regala 50 Y, Creeper King le sigue. */
    public static void onLogin(ServerPlayer p) {
        YoguData d = ServerLogic.data();
        UUID me = p.getUUID();
        if (!d.welcomed.add(me)) return;
        d.setDirty();
        ServerLogic.later(200, () -> {
            say(LUNA, me, Tr.of("¡Hola %s! Bienvenid@ al server. Te dejo 50 Y para que empieces", p.getGameProfile().getName()));
            gift(LUNA, me, 50, Tr.of("Regalo de bienvenida"));
        });
        ServerLogic.later(500, () -> {
            YoguData dd = ServerLogic.data();
            if (dd.profile(CREEPER.id()).following.add(me)) {
                dd.setDirty();
                ServerLogic.broadcastSnap();
                ServerLogic.notifyFrom(me, CREEPER.id(), "social", "@" + CREEPER.nick(), Tr.of("ha empezado a seguirte"));
            }
        });
    }

    public static void onPlayerPost(YoguData.Post post) {
        int delay = 100;
        for (Fake f : ALL) {
            if (RNG.nextInt(3) == 0 && f != LUNA) continue;
            ServerLogic.later(delay, () -> {
                YoguData d = ServerLogic.data();
                YoguData.Post p = d.post(post.id);
                if (p == null || !p.likes.add(f.id())) return;
                d.setDirty();
                ServerLogic.broadcastSnap();
                ServerLogic.notifyFrom(p.author, f.id(), "social", "@" + f.nick(), Tr.of("le ha dado like a tu foto"));
            });
            delay += 80 + RNG.nextInt(160);
        }
        Fake c = ALL.get(RNG.nextInt(ALL.size()));
        ServerLogic.later(delay + 100, () -> {
            YoguData d = ServerLogic.data();
            YoguData.Post p = d.post(post.id);
            if (p == null) return;
            YoguData.Comment cm = new YoguData.Comment();
            cm.author = c.id();
            cm.text = pick(new String[]{Tr.of("¡Qué buena foto!"), Tr.of("Brutal ✦"), Tr.of("Me flipa ♥"), Tr.of("Top"), Tr.of("¿Dónde es eso?")});
            cm.time = System.currentTimeMillis();
            p.comments.add(cm);
            d.setDirty();
            ServerLogic.broadcastSnap();
            ServerLogic.notifyFrom(p.author, c.id(), "social", "@" + c.nick(), Tr.of("ha comentado: \"%s\"", cm.text));
        });
    }

    public static void onMessage(UUID fake, UUID from, String text) {
        Fake f = byId(fake);
        if (f == null) return;
        String t = text.toLowerCase(Locale.ROOT);
        String reply;
        if (t.contains("hola") || t.contains("buenas") || t.contains("hey") || t.contains("hello") || t.startsWith("hi")) reply = Tr.of("¡Hola! ☺ ¿Qué tal?");
        else if (t.contains("dinero") || t.contains("yogur") || t.contains("pasta") || t.contains("money")) reply = Tr.of("Yo guardo todo en el banco, que en el bolsillo se pierde $");
        else if (t.endsWith("?")) reply = pick(new String[]{Tr.of("Mmm... ¡creo que sí!"), Tr.of("Ni idea jajaja"), Tr.of("¡Claro!")});
        else reply = pick(f.replies());
        ServerLogic.later(40 + RNG.nextInt(60), () -> say(f, from, reply));
    }

    public static void onTransfer(UUID fake, UUID from, long amount) {
        Fake f = byId(fake);
        if (f == null) return;
        ServerLogic.later(60, () -> say(f, from, Tr.of("¡Gracias por los %s Y! ♥", Money.fmt(amount))));
    }

    private static void say(Fake f, UUID to, String text) {
        YoguData d = ServerLogic.data();
        d.addChat(f.id(), to, text, "text");
        ServerPlayer p = ServerLogic.online(to);
        if (p != null) ServerLogic.sendSnap(p);
        ServerLogic.notifyFrom(to, f.id(), "chat", "@" + f.nick(), text);
    }

    private static void gift(Fake f, UUID to, long amount, String concept) {
        YoguData d = ServerLogic.data();
        d.addTx(f.id(), -amount, to.toString(), concept);
        d.addTx(to, amount, f.id().toString(), concept);
        d.addChat(f.id(), to, Tr.of("%s Y · %s", Money.fmt(amount), concept), "transfer");
        ServerPlayer p = ServerLogic.online(to);
        if (p != null) ServerLogic.sendSnap(p);
        ServerLogic.notifyFrom(to, f.id(), "bank", Tr.of("Banco Yogurín"), Tr.of("@%s te ha enviado %s Y", f.nick(), Money.fmt(amount)));
        if (d.bal(f.id()) < 200) d.addTx(f.id(), 1000, "", Tr.of("Saldo inicial"));
    }

    private static String pick(String[] a) {
        return a[RNG.nextInt(a.length)];
    }

    private FakeProfiles() {}
}
