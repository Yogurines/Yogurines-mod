package com.yogurines.mod.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Fotos y fotos de perfil guardadas como archivos en la carpeta del mundo (world/yogurines/images). */
public final class ImageStore {
    public static final int MAX_BYTES = 400_000;

    private static Path dir(MinecraftServer server) throws IOException {
        Path p = server.getWorldPath(LevelResource.ROOT).resolve("yogurines").resolve("images");
        Files.createDirectories(p);
        return p;
    }

    public static String save(MinecraftServer server, byte[] data) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try {
            Files.write(dir(server).resolve(id + ".img"), data);
            return id;
        } catch (IOException e) {
            return "";
        }
    }

    public static byte[] load(MinecraftServer server, String id) {
        if (id == null || !id.matches("[a-z0-9]{1,32}")) return null;
        try {
            Path p = dir(server).resolve(id + ".img");
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private ImageStore() {}
}
