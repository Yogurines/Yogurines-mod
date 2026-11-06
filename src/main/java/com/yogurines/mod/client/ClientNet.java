package com.yogurines.mod.client;

import com.yogurines.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvent;

import java.util.UUID;

/** Paquetes que llegan del servidor. */
public final class ClientNet {
    public static void handle(String type, CompoundTag t) {
        switch (type) {
            case "snap" -> ClientData.readSnap(t);
            case "img" -> Images.receive(t.getString("id"), t.getByteArray("b"));
            case "notify" -> {
                String app = t.getString("app");
                // si ya estás mirando ese chat no hace falta el aviso
                if (Minecraft.getInstance().screen instanceof PhoneScreen ps && ps.isShowing(app, t.getString("title"))) return;
                Toasts.push(app, t.getString("title"), t.getString("text"));
            }
            case "fx" -> fx(t);
            case "bank_err" -> {
                ClientData.bankError = t.getString("r");
                ClientData.bankErrorTime = System.currentTimeMillis();
            }
            case "hidden" -> {
                ClientData.hiddenTags.clear();
                ListTag l = t.getList("l", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) {
                    UUID u = ClientData.uuid(l.getString(i));
                    if (u != null) ClientData.hiddenTags.add(u);
                }
            }
            case "pose" -> {
                UUID u = ClientData.uuid(t.getString("u"));
                if (u == null) return;
                int m = t.getInt("m");
                if (m == 0) ClientData.poses.remove(u); else ClientData.poses.put(u, m);
            }
            default -> {
                if (type.startsWith("npc_")) com.yogurines.mod.npc.client.NpcClient.handle(type, t);
                else if (type.startsWith("scr_")) com.yogurines.mod.scratch.client.ScratchScreen.handle(type, t);
            }
        }
    }

    private static void fx(CompoundTag t) {
        switch (t.getString("k")) {
            case "deposit" -> {
                if (t.getInt("n") > 0) play(ModSounds.ATM_BILL.get(), 0);
                if (t.getInt("c") > 0) play(ModSounds.ATM_COIN.get(), 0);
                play(ModSounds.ATM_OK.get(), 0);
                AtmScreen.flash("Ingresado: " + com.yogurines.mod.item.Money.fmt(t.getLong("v")) + " Y");
            }
            case "withdraw" -> {
                if (t.getInt("n") > 0) play(ModSounds.ATM_BILL.get(), 0);
                if (t.getInt("c") > 0) play(ModSounds.ATM_COIN.get(), 0);
                play(ModSounds.ATM_OK.get(), 0);
                AtmScreen.flash("Retira tu dinero");
            }
            case "sent" -> play(ModSounds.TRANSFER_SENT.get(), 0);
            case "wallet_in", "bill" -> play(ModSounds.ATM_BILL.get(), 0);
            case "coin" -> play(ModSounds.ATM_COIN.get(), 0);
            case "npc_chat" -> play(ModSounds.NPC_DIALOG_OPEN.get(), 0);
            default -> {}
        }
    }

    public static void play(SoundEvent s, int unused) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, 1f, 0.9f));
    }

    private ClientNet() {}
}
