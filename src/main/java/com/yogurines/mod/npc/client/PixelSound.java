package com.yogurines.mod.npc.client;

import com.yogurines.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** Sonidos cortos de interfaz para las pantallas de NPCs. */
final class PixelSound {
    static void play(SoundEvent e, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    static void tap() { play(ModSounds.TAP.get(), 1f, 0.5f); }

    static void tick() { play(ModSounds.NPC_LETTER.get(), 1.1f, 0.4f); }

    private PixelSound() {}
}
