package com.yogurines.mod.net;

import com.yogurines.mod.Yogurines;
import com.yogurines.mod.client.ClientNet;
import com.yogurines.mod.data.ServerLogic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Red del mod: un único tipo de paquete en cada sentido, con un nombre de acción y un CompoundTag.
 * Así añadir funciones nuevas no obliga a crear clases de paquete nuevas.
 */
public final class Net {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Yogurines.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void init() {
        CHANNEL.messageBuilder(ToServer.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Msg::write).decoder(ToServer::new).consumerMainThread(ToServer::handle).add();
        CHANNEL.messageBuilder(ToClient.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Msg::write).decoder(ToClient::new).consumerMainThread(ToClient::handle).add();
    }

    public static void toServer(String type, CompoundTag tag) {
        CHANNEL.sendToServer(new ToServer(type, tag));
    }

    public static void toPlayer(ServerPlayer p, String type, CompoundTag tag) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ToClient(type, tag));
    }

    public static void toAll(String type, CompoundTag tag) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new ToClient(type, tag));
    }

    public static class Msg {
        public final String type;
        public final CompoundTag tag;

        Msg(String type, CompoundTag tag) {
            this.type = type;
            this.tag = tag == null ? new CompoundTag() : tag;
        }

        Msg(FriendlyByteBuf buf) {
            this.type = buf.readUtf(64);
            CompoundTag t = buf.readNbt();
            this.tag = t == null ? new CompoundTag() : t;
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(type, 64);
            buf.writeNbt(tag);
        }
    }

    public static class ToServer extends Msg {
        ToServer(String type, CompoundTag tag) { super(type, tag); }
        ToServer(FriendlyByteBuf buf) { super(buf); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) ServerLogic.handle(sender, type, tag);
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ToClient extends Msg {
        ToClient(String type, CompoundTag tag) { super(type, tag); }
        ToClient(FriendlyByteBuf buf) { super(buf); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.handle(type, tag));
            ctx.get().setPacketHandled(true);
        }
    }

    private Net() {}
}
