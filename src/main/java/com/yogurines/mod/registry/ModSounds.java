package com.yogurines.mod.registry;

import com.yogurines.mod.Yogurines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Todos los sonidos elegidos por Aleix en las rondas de propuestas. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Yogurines.MOD_ID);

    public static final RegistryObject<SoundEvent> MESSAGE_RECEIVED = register("phone.message_received");
    public static final RegistryObject<SoundEvent> TRANSFER_SENT = register("bank.transfer_sent");
    public static final RegistryObject<SoundEvent> SHUTTER = register("phone.shutter");
    public static final RegistryObject<SoundEvent> LIKE = register("phone.like");
    public static final RegistryObject<SoundEvent> TAP = register("phone.tap");
    public static final RegistryObject<SoundEvent> PHONE_OPEN = register("phone.open");
    public static final RegistryObject<SoundEvent> ATM_KEY = register("atm.key");
    public static final RegistryObject<SoundEvent> ATM_OK = register("atm.ok");
    public static final RegistryObject<SoundEvent> ATM_BILL = register("atm.bill");
    public static final RegistryObject<SoundEvent> ATM_COIN = register("atm.coin");
    public static final RegistryObject<SoundEvent> ATM_BREAK = register("atm.break");
    public static final RegistryObject<SoundEvent> ATM_HIT = register("atm.hit");
    public static final RegistryObject<SoundEvent> ATM_STEP = register("atm.step");
    public static final RegistryObject<SoundEvent> WALLET_OPEN = register("wallet.open");
    public static final RegistryObject<SoundEvent> WALLET_CLOSE = register("wallet.close");

    public static final RegistryObject<SoundEvent> NPC_DIALOG_OPEN = register("npc.dialog_open");
    public static final RegistryObject<SoundEvent> NPC_LETTER = register("npc.letter");
    public static final RegistryObject<SoundEvent> SHOP_OPEN = register("shop.open");
    public static final RegistryObject<SoundEvent> SHOP_BUY = register("shop.buy");
    public static final RegistryObject<SoundEvent> SHOP_ERROR = register("shop.error");
    public static final RegistryObject<SoundEvent> SCRATCH = register("scratch.scratch");
    public static final RegistryObject<SoundEvent> SCRATCH_WIN = register("scratch.win");
    public static final RegistryObject<SoundEvent> SCRATCH_LOSE = register("scratch.lose");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Yogurines.MOD_ID, name)));
    }

    private ModSounds() {}
}
