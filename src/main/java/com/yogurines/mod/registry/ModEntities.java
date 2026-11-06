package com.yogurines.mod.registry;

import com.yogurines.mod.Yogurines;
import com.yogurines.mod.npc.NpcEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Yogurines.MOD_ID);

    public static final RegistryObject<EntityType<NpcEntity>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10).build("npc"));

    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(NPC.get(), NpcEntity.createAttributes().build());
    }

    private ModEntities() {}
}
