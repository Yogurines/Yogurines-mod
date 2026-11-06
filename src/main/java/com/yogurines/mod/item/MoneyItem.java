package com.yogurines.mod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Moneda o billete de yogurines con un valor fijo. */
public class MoneyItem extends Item {
    private final int value;

    public MoneyItem(int value, Properties properties) {
        super(properties);
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.yogurines.value", value).withStyle(ChatFormatting.GRAY));
        if (stack.getCount() > 1) {
            tooltip.add(Component.translatable("tooltip.yogurines.stack_value", value * stack.getCount()).withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
