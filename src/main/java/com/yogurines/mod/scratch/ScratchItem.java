package com.yogurines.mod.scratch;

import com.yogurines.mod.item.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Rasca y gana. tier 0 = Bronce (Y1), 1 = Plata (Y10), 2 = Oro (Y100). */
public class ScratchItem extends Item {
    public final int tier;

    public ScratchItem(int tier, Properties p) {
        super(p);
        this.tier = tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ScratchServer.open(sp, tier);
            if (!player.getAbilities().instabuild) s.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.yogurines.scratch.price", Money.fmt(ScratchServer.PRICE[tier])).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("tooltip.yogurines.scratch.max", Money.fmt(ScratchServer.MAX[tier])).withStyle(ChatFormatting.YELLOW));
        tip.add(Component.translatable("tooltip.yogurines.scratch.hint").withStyle(ChatFormatting.GRAY));
    }
}
