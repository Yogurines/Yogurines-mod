package com.yogurines.mod.registry;

import com.yogurines.mod.Yogurines;
import com.yogurines.mod.item.MoneyItem;
import com.yogurines.mod.item.PhoneItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Yogurines.MOD_ID);

    public static final RegistryObject<Item> PHONE = ITEMS.register("phone",
            () -> new PhoneItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ATM = ITEMS.register("atm",
            () -> new BlockItem(ModBlocks.ATM.get(), new Item.Properties()));

    public static final RegistryObject<Item> COIN_1 = money("coin_1", 1, Rarity.COMMON);
    public static final RegistryObject<Item> NOTE_5 = money("note_5", 5, Rarity.COMMON);
    public static final RegistryObject<Item> NOTE_10 = money("note_10", 10, Rarity.COMMON);
    public static final RegistryObject<Item> NOTE_20 = money("note_20", 20, Rarity.COMMON);
    public static final RegistryObject<Item> NOTE_50 = money("note_50", 50, Rarity.UNCOMMON);
    public static final RegistryObject<Item> NOTE_100 = money("note_100", 100, Rarity.UNCOMMON);
    public static final RegistryObject<Item> NOTE_200 = money("note_200", 200, Rarity.RARE);
    public static final RegistryObject<Item> NOTE_500 = money("note_500", 500, Rarity.EPIC);

    /** Moneda y billetes de menor a mayor: así salen en la pestaña creativa. */
    public static final List<RegistryObject<Item>> MONEY = List.of(
            COIN_1, NOTE_5, NOTE_10, NOTE_20, NOTE_50, NOTE_100, NOTE_200, NOTE_500);

    private static RegistryObject<Item> money(String name, int value, Rarity rarity) {
        return ITEMS.register(name, () -> new MoneyItem(value, new Item.Properties().stacksTo(64).rarity(rarity)));
    }

    private ModItems() {}
}
