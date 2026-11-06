package com.yogurines.mod.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Datos del tooltip grande: el billete con su grabado. */
public record NoteTooltip(int value) implements TooltipComponent {}
