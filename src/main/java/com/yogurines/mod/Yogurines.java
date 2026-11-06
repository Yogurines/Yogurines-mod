package com.yogurines.mod;

import com.yogurines.mod.client.ClientSetup;
import com.yogurines.mod.net.Net;
import com.yogurines.mod.registry.ModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import com.yogurines.mod.registry.ModCreativeTabs;
import com.yogurines.mod.registry.ModItems;
import com.yogurines.mod.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Yogurines.MOD_ID)
public class Yogurines {
    public static final String MOD_ID = "yogurines";

    public Yogurines() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        Net.init();
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }
}
