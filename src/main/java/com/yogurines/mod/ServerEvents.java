package com.yogurines.mod;

import com.yogurines.mod.data.FakeProfiles;
import com.yogurines.mod.data.ServerLogic;
import com.yogurines.mod.net.Net;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Eventos del servidor: arranque, ticks y entrada/salida de jugadores. */
public final class ServerEvents {
    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        ServerLogic.setServer(e.getServer());
        FakeProfiles.seed();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        ServerLogic.setServer(null);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ServerLogic.server() != null) ServerLogic.serverTick();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || ServerLogic.server() == null) return;
        var d = ServerLogic.data();
        d.names.put(p.getUUID(), p.getGameProfile().getName());
        d.setDirty();
        ServerLogic.broadcastSnap();
        ServerLogic.sendHidden(p);
        ServerLogic.sendPoses(p);
        FakeProfiles.onLogin(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || ServerLogic.server() == null) return;
        ServerLogic.clearCamera(p.getUUID());
        com.yogurines.mod.scratch.ScratchServer.logout(p);
        CompoundTag o = new CompoundTag();
        o.putString("u", p.getUUID().toString());
        o.putInt("m", 0);
        Net.toAll("pose", o);
        ServerLogic.later(1, ServerLogic::broadcastSnap);
    }

    private ServerEvents() {}
}
