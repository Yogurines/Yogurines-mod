package com.yogurines.mod.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * NPC humanoide personalizable. El aspecto y el nombre ("look") se sincronizan con los clientes;
 * la conducta, los diálogos y la tienda ("cfg") solo los guarda el servidor.
 */
public class NpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<CompoundTag> LOOK = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** beh = conducta, dlg = diálogos, shop = ofertas, talked = jugadores que ya han hablado. */
    private CompoundTag cfg = defaultCfg();
    @Nullable private BlockPos home;
    /** Solo en el cliente: aspecto que se está editando (vista previa del editor). */
    @Nullable public CompoundTag preview;
    private CompoundTag cachedTag;
    private Look cachedLook;

    public NpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 16);
    }

    // ---------- datos ----------

    public static CompoundTag defaultLook() {
        CompoundTag t = new CompoundTag();
        t.putString("skinType", "default");
        t.putString("skin", "");
        t.putBoolean("slim", false);
        t.putFloat("scale", 1f);
        t.putString("pose", "stand");
        t.putString("name", "NPC");
        t.putBoolean("showName", true);
        t.putString("font", "minecraft");
        t.putString("bg", "vanilla");
        t.putInt("c1", 0x3F9A2E);
        t.putInt("c2", 0xFFFFFF);
        t.putString("title", "");
        t.putString("holo", "glitch");
        t.putString("icon", "auto");
        return t;
    }

    public static CompoundTag defaultCfg() {
        CompoundTag c = new CompoundTag();
        CompoundTag beh = new CompoundTag();
        beh.putBoolean("look", true);
        beh.putString("move", "still");
        beh.putInt("radius", 6);
        beh.putBoolean("invuln", true);
        beh.putBoolean("push", false);
        c.put("beh", beh);
        CompoundTag dlg = new CompoundTag();
        dlg.putString("mode", "none");
        dlg.put("greet", new ListTag());
        dlg.putString("first", "");
        dlg.put("nodes", new ListTag());
        c.put("dlg", dlg);
        c.put("shop", new ListTag());
        c.put("talked", new ListTag());
        return c;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LOOK, defaultLook());
    }

    public CompoundTag lookTag() {
        return preview != null ? preview : entityData.get(LOOK);
    }

    /** Aspecto ya interpretado (con caché para no leer el NBT en cada fotograma). */
    public Look look() {
        CompoundTag t = lookTag();
        if (t != cachedTag) { cachedTag = t; cachedLook = new Look(t); }
        return cachedLook;
    }

    public void setLook(CompoundTag t) {
        entityData.set(LOOK, t.copy());
        refreshDimensions();
    }

    public CompoundTag cfg() { return cfg; }
    public CompoundTag beh() { return cfg.getCompound("beh"); }
    public CompoundTag dlg() { return cfg.getCompound("dlg"); }
    public ListTag shop() { return cfg.getList("shop", Tag.TAG_COMPOUND); }

    public void setCfg(CompoundTag c) {
        CompoundTag merged = defaultCfg();
        merged.merge(c);
        this.cfg = merged;
        applyBehaviour();
    }

    @Nullable public BlockPos home() { return home; }

    public void setHome(BlockPos p) {
        this.home = p;
        applyBehaviour();
    }

    private void applyBehaviour() {
        CompoundTag b = beh();
        if (home != null && "wander".equals(b.getString("move"))) restrictTo(home, Math.max(1, Math.min(48, b.getInt("radius"))));
        else clearRestriction();
        if ("still".equals(b.getString("move"))) getNavigation().stop();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LOOK.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(look().scale);
    }

    // ---------- conducta ----------

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new Goal() {
            { setFlags(java.util.EnumSet.of(Flag.MOVE)); }
            @Override public boolean canUse() {
                return home != null && "home".equals(beh().getString("move")) && distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 2.5;
            }
            @Override public boolean canContinueToUse() { return canUse() && !getNavigation().isDone(); }
            @Override public void start() { getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.7); }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6) {
            @Override public boolean canUse() { return "wander".equals(beh().getString("move")) && super.canUse(); }
            @Override public boolean canContinueToUse() { return "wander".equals(beh().getString("move")) && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f, 1f) {
            @Override public boolean canUse() { return beh().getBoolean("look") && super.canUse(); }
            @Override public boolean canContinueToUse() { return beh().getBoolean("look") && super.canContinueToUse(); }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override public boolean canUse() { return !"still".equals(beh().getString("move")) && super.canUse(); }
        });
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (beh().getBoolean("invuln") && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return beh().getBoolean("push");
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (beh().getBoolean("push")) super.knockback(strength, x, z);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            if (player.getMainHandItem().getItem() instanceof NpcWandItem) NpcServer.openEditor(sp, this);
            else NpcServer.talk(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ---------- guardado ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("YLook", entityData.get(LOOK).copy());
        tag.put("YCfg", cfg.copy());
        if (home != null) {
            tag.putInt("YHomeX", home.getX());
            tag.putInt("YHomeY", home.getY());
            tag.putInt("YHomeZ", home.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("YLook")) {
            CompoundTag l = defaultLook();
            l.merge(tag.getCompound("YLook"));
            setLook(l);
        }
        if (tag.contains("YCfg")) setCfg(tag.getCompound("YCfg"));
        if (tag.contains("YHomeX")) setHome(new BlockPos(tag.getInt("YHomeX"), tag.getInt("YHomeY"), tag.getInt("YHomeZ")));
    }

    /** Todo lo necesario para una plantilla: aspecto, configuración y equipo. */
    public CompoundTag templateTag() {
        CompoundTag t = new CompoundTag();
        t.put("look", entityData.get(LOOK).copy());
        CompoundTag c = cfg.copy();
        c.put("talked", new ListTag());
        ListTag shop = c.getList("shop", Tag.TAG_COMPOUND);
        for (int i = 0; i < shop.size(); i++) {
            CompoundTag o = shop.getCompound(i);
            o.remove("used");
            o.putInt("stock", o.getInt("max"));
        }
        t.put("cfg", c);
        t.put("equip", equipTag());
        return t;
    }

    public void applyTemplate(CompoundTag t) {
        CompoundTag l = defaultLook();
        l.merge(t.getCompound("look"));
        setLook(l);
        setCfg(t.getCompound("cfg"));
        if (t.contains("equip")) setEquip(t.getList("equip", Tag.TAG_COMPOUND));
    }

    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public ListTag equipTag() {
        ListTag l = new ListTag();
        for (EquipmentSlot s : SLOTS) l.add(getItemBySlot(s).save(new CompoundTag()));
        return l;
    }

    public void setEquip(ListTag l) {
        for (int i = 0; i < SLOTS.length; i++) setItemSlot(SLOTS[i], i < l.size() ? ItemStack.of(l.getCompound(i)) : ItemStack.EMPTY);
    }

    /** Aspecto interpretado. */
    public static final class Look {
        public final String skinType, skin, pose, name, font, bg, title, holo, icon;
        public final boolean slim, showName, hasShop, hasDialog;
        public final float scale;
        public final int c1, c2;

        public Look(CompoundTag t) {
            skinType = t.getString("skinType");
            skin = t.getString("skin");
            slim = t.getBoolean("slim");
            float sc = t.contains("scale") ? t.getFloat("scale") : 1f;
            scale = Math.max(0.3f, Math.min(3f, sc));
            pose = t.getString("pose");
            name = t.getString("name");
            showName = !t.contains("showName") || t.getBoolean("showName");
            font = t.getString("font");
            bg = t.getString("bg");
            c1 = t.getInt("c1");
            c2 = t.contains("c2") ? t.getInt("c2") : 0xFFFFFF;
            title = t.getString("title");
            holo = t.getString("holo");
            icon = t.getString("icon");
            hasShop = t.getBoolean("hs");
            hasDialog = t.getBoolean("hd");
        }
    }
}
