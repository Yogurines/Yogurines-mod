package com.yogurines.mod.npc.client;

import com.yogurines.mod.npc.NpcEntity;
import com.yogurines.mod.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.util.ArrayList;
import java.util.List;

/** Paquetes de NPCs que llegan al cliente. */
public final class NpcClient {
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.NPC.get(), NpcRenderer::new);
    }

    static NpcEntity entity(int id) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        Entity e = level.getEntity(id);
        return e instanceof NpcEntity n ? n : null;
    }

    public static void handle(String type, CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        switch (type) {
            case "npc_edit" -> {
                NpcEntity n = entity(t.getInt("id"));
                if (n == null) return;
                List<String> skins = new ArrayList<>();
                ListTag l = t.getList("skins", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) skins.add(l.getString(i));
                mc.setScreen(new NpcEditScreen(n, t.getCompound("look"), t.getCompound("cfg"), t.getList("equip", Tag.TAG_COMPOUND), skins));
            }
            case "npc_tpls" -> {
                List<String> names = new ArrayList<>();
                ListTag l = t.getList("l", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) names.add(l.getString(i));
                if (mc.screen instanceof NpcTemplateScreen ts) ts.update(names, t.getString("cur"));
                else if (t.getBoolean("open")) mc.setScreen(new NpcTemplateScreen(names, t.getString("cur")));
            }
            case "npc_skinfile" -> NpcSkins.receiveFile(t.getString("name"), t.getByteArray("b"));
            case "npc_dialog" -> {
                NpcEntity n = entity(t.getInt("id"));
                if (n == null) return;
                mc.setScreen(new NpcDialogScreen(n, t.getList("nodes", Tag.TAG_COMPOUND), t.getString("first"), t.getBoolean("shop")));
            }
            case "npc_act_res" -> {
                if (mc.screen instanceof NpcDialogScreen d) d.actionResult(t.getBoolean("ok"), t.getInt("goto"), t.getString("msg"));
            }
            case "npc_shop" -> {
                NpcEntity n = entity(t.getInt("id"));
                if (mc.screen instanceof NpcShopScreen s && s.npcId() == t.getInt("id")) s.update(t);
                else if (t.getBoolean("open") && n != null) mc.setScreen(new NpcShopScreen(n, t));
            }
            default -> {}
        }
    }

    private NpcClient() {}
}
