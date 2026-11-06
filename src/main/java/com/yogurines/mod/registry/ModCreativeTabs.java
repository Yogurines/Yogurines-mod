package com.yogurines.mod.registry;

import com.yogurines.mod.Yogurines;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Yogurines.MOD_ID);

    public static final RegistryObject<CreativeModeTab> YOGURINES = TABS.register("yogurines", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.yogurines"))
            .icon(() -> new ItemStack(ModItems.PHONE.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.PHONE.get());
                output.accept(ModItems.ATM.get());
                ModItems.MONEY.forEach(item -> output.accept(item.get()));
            })
            .build());

    private ModCreativeTabs() {}
}
