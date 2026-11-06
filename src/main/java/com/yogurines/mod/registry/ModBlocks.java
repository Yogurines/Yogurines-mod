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
    // Sonidos provisionales (metal vanilla) hasta que se elijan los definitivos.
    public static final RegistryObject<Block> ATM = BLOCKS.register("atm", () -> new AtmBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 4)
                    .noOcclusion()));

    private ModBlocks() {}
}
