package com.yogurines.mod.registry;

import com.yogurines.mod.Yogurines;
import com.yogurines.mod.block.AtmBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Yogurines.MOD_ID);

    // Cajero automático: 1 bloque de ancho y 2 de alto.
    // Se rompe con cualquier pico (madera incluida); cuanto mejor el pico, más rápido.
    // Sonidos: metal vanilla + un toque suave de yunque al colocarlo (ver AtmBlock#setPlacedBy).
    public static final RegistryObject<Block> ATM = BLOCKS.register("atm", () -> new AtmBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 4)
                    .noOcclusion()));

    private ModBlocks() {}
}
