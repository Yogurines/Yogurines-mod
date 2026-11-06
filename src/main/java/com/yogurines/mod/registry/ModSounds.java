package com.yogurines.mod.registry;

import com.yogurines.mod.Yogurines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Yogurines.MOD_ID);

    // Sonidos ya elegidos por Aleix. El resto se añadirá cuando se elijan.
    public static final RegistryObject<SoundEvent> MESSAGE_RECEIVED = register("phone.message_received");
    public static final RegistryObject<SoundEvent> TRANSFER_SENT = register("bank.transfer_sent");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Yogurines.MOD_ID, name)));
    }

    private ModSounds() {}
}
