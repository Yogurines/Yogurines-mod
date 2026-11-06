package com.yogurines.mod.npc.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.yogurines.mod.Yogurines;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.npc.NpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Skins de los NPCs: por nombre de jugador, por enlace o por archivo del servidor. */
public final class NpcSkins {
    private static final class Entry {
        volatile ResourceLocation rl;
        volatile boolean slim;
        boolean failed;
    }

    private static final Map<String, Entry> CACHE = new HashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static int counter;

    public static ResourceLocation texture(NpcEntity.Look l) {
        Entry e = entry(l);
        if (e != null && e.rl != null) return e.rl;
        return DefaultPlayerSkin.getDefaultSkin(UUID.nameUUIDFromBytes(l.name.getBytes()));
    }

    /** Si la skin de un jugador es de brazos finos (cuando el NPC no lo fuerza). */
    public static boolean slim(NpcEntity.Look l) {
        if (l.slim) return true;
        Entry e = entry(l);
        return e != null && e.rl != null && "player".equals(l.skinType) && e.slim;
    }

    private static Entry entry(NpcEntity.Look l) {
        if (l.skin.isEmpty() || "default".equals(l.skinType)) return null;
        String key = l.skinType + ":" + l.skin;
        Entry e = CACHE.get(key);
        if (e != null) return e;
        e = new Entry();
        CACHE.put(key, e);
        switch (l.skinType) {
            case "player" -> loadPlayer(l.skin, e);
            case "url" -> loadUrl(l.skin, e);
            case "file" -> {
                CompoundTag t = new CompoundTag();
                t.putString("name", l.skin);
                Net.toServer("npc_skinfile", t);
            }
            default -> {}
        }
        return e;
    }

    private static void loadPlayer(String name, Entry e) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) { e.failed = true; return; }
        try {
            SkullBlockEntity.updateGameprofile(new GameProfile(null, name), filled -> {
                if (filled == null) return;
                Minecraft.getInstance().getSkinManager().registerSkins(filled, (type, location, texture) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        e.slim = "slim".equals(texture.getMetadata("model"));
                        e.rl = location;
                    }
                }, false);
            });
        } catch (Exception ex) {
            e.failed = true;
        }
    }

    private static void loadUrl(String url, Entry e) {
        String low = url.toLowerCase(Locale.ROOT);
        if (!low.startsWith("https://") && !low.startsWith("http://")) { e.failed = true; return; }
        Thread t = new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", "Yogurines-Mod").GET().build();
                HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200 || res.body().length > 512 * 1024) { e.failed = true; return; }
                byte[] b = res.body();
                Minecraft.getInstance().execute(() -> register(b, e));
            } catch (Exception ex) {
                e.failed = true;
            }
        }, "Yogurines skin");
        t.setDaemon(true);
        t.start();
    }

    public static void receiveFile(String name, byte[] b) {
        Entry e = CACHE.computeIfAbsent("file:" + name, k -> new Entry());
        register(b, e);
    }

    private static void register(byte[] b, Entry e) {
        try (InputStream in = new ByteArrayInputStream(b)) {
            NativeImage img = NativeImage.read(in);
            if (img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) { img.close(); e.failed = true; return; }
            if (img.getHeight() == 32) img = legacy(img);
            ResourceLocation rl = new ResourceLocation(Yogurines.MOD_ID, "npc_skin/" + (counter++));
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
            e.rl = rl;
        } catch (Exception ex) {
            e.failed = true;
        }
    }

    /** Skins antiguas de 64x32: se copian brazo y pierna al lado izquierdo, como hace el juego. */
    private static NativeImage legacy(NativeImage old) {
        NativeImage n = new NativeImage(64, 64, true);
        n.copyFrom(old);
        old.close();
        n.fillRect(0, 32, 64, 32, 0);
        n.copyRect(4, 16, 16, 32, 4, 4, true, false);
        n.copyRect(8, 16, 16, 32, 4, 4, true, false);
        n.copyRect(0, 20, 24, 32, 4, 12, true, false);
        n.copyRect(4, 20, 16, 32, 4, 12, true, false);
        n.copyRect(8, 20, 8, 32, 4, 12, true, false);
        n.copyRect(12, 20, 16, 32, 4, 12, true, false);
        n.copyRect(44, 16, -8, 32, 4, 4, true, false);
        n.copyRect(48, 16, -8, 32, 4, 4, true, false);
        n.copyRect(40, 20, 0, 32, 4, 12, true, false);
        n.copyRect(44, 20, -8, 32, 4, 12, true, false);
        n.copyRect(48, 20, -16, 32, 4, 12, true, false);
        n.copyRect(52, 20, -8, 32, 4, 12, true, false);
        return n;
    }

    /** Se llama al cambiar el texto de la skin en el editor para volver a intentarlo. */
    public static void forget(String type, String skin) {
        Entry e = CACHE.get(type + ":" + skin);
        if (e != null && e.failed) CACHE.remove(type + ":" + skin);
    }

    private NpcSkins() {}
}
