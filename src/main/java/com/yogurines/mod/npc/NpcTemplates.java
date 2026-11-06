package com.yogurines.mod.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Plantillas de NPC guardadas con nombre (se comparten en todo el servidor). */
public class NpcTemplates extends SavedData {
    private final Map<String, CompoundTag> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public static NpcTemplates get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NpcTemplates::load, NpcTemplates::new, "yogurines_npc_templates");
    }

    public CompoundTag get(String name) {
        CompoundTag t = map.get(name);
        return t == null ? null : t.copy();
    }

    public void put(String name, CompoundTag t) {
        map.put(name, t.copy());
        setDirty();
    }

    public void remove(String name) {
        if (map.remove(name) != null) setDirty();
    }

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        map.forEach(all::put);
        tag.put("templates", all);
        return tag;
    }

    public static NpcTemplates load(CompoundTag tag) {
        NpcTemplates t = new NpcTemplates();
        CompoundTag all = tag.getCompound("templates");
        for (String k : all.getAllKeys()) t.map.put(k, all.getCompound(k));
        return t;
    }
}
