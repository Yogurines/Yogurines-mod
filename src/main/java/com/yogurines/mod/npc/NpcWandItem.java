package com.yogurines.mod.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;

/** Modificador de NPCs: crea NPCs (con o sin plantilla) y abre su editor. Solo para OP. */
public class NpcWandItem extends Item {
    public NpcWandItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(s);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (NpcServer.isAdmin(sp)) NpcServer.sendTemplates(sp, true);
            else sp.displayClientMessage(Component.literal("Solo los admins (OP) pueden usar el modificador").withStyle(ChatFormatting.RED), true);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer sp) {
            ItemStack s = ctx.getItemInHand();
            String tpl = s.hasTag() ? s.getTag().getString("tpl") : "";
            NpcServer.spawn(sp, ctx.getClickedPos().relative(ctx.getClickedFace()), tpl);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && !stack.getTag().getString("tpl").isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        String tpl = stack.hasTag() ? stack.getTag().getString("tpl") : "";
        if (!tpl.isEmpty()) tip.add(Component.translatable("tooltip.yogurines.npc_wand.tpl", tpl).withStyle(ChatFormatting.GREEN));
        tip.add(Component.translatable("tooltip.yogurines.npc_wand.1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.yogurines.npc_wand.2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.yogurines.npc_wand.3").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.yogurines.npc_wand.op").withStyle(ChatFormatting.DARK_GRAY));
    }
}
