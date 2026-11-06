package com.yogurines.mod.registry;

import com.yogurines.mod.Yogurines;
import com.yogurines.mod.block.AtmBlock;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Yogurines.MOD_ID);

    /** Sonidos del cajero: "Máquina que se apaga" para romper/golpear/pisar y metal vanilla al colocar (+ yunque en AtmBlock). */
    public static final ForgeSoundType ATM_SOUNDS = new ForgeSoundType(1.0F, 1.0F,
            ModSounds.ATM_BREAK, ModSounds.ATM_STEP, () -> SoundEvents.METAL_PLACE, ModSounds.ATM_HIT, () -> SoundEvents.METAL_FALL);

    // Cajero automático: 1 bloque de ancho y 2 de alto. Se rompe con cualquier pico; cuanto mejor, más rápido.
    public static final RegistryObject<Block> ATM = BLOCKS.register("atm", () -> new AtmBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(ATM_SOUNDS)
                    .lightLevel(state -> 4)
                    .noOcclusion()));

    private ModBlocks() {}
}
