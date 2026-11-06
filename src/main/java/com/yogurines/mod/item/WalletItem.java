package com.yogurines.mod.item;

import com.yogurines.mod.client.ClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Cartera de cuero: guarda billetes y monedas dentro (NBT "c" = cantidad de cada valor). */
public class WalletItem extends Item {
    public WalletItem(Properties properties) {
        super(properties);
    }

    public static int[] counts(ItemStack stack) {
        int[] out = new int[Money.VALUES.length];
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("c")) {
            int[] c = tag.getIntArray("c");
            System.arraycopy(c, 0, out, 0, Math.min(c.length, out.length));
        }
        return out;
    }

    public static void setCounts(ItemStack stack, int[] counts) {
        stack.getOrCreateTag().putIntArray("c", counts);
    }

    public static long total(ItemStack stack) {
        int[] c = counts(stack);
        long t = 0;
        for (int i = 0; i < c.length; i++) t += (long) c[i] * Money.VALUES[i];
        return t;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openWallet(hand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.yogurines.wallet_total", Money.fmt(total(stack))).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.yogurines.wallet_hint").withStyle(ChatFormatting.GRAY));
    }
}
