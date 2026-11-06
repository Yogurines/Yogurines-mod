package com.yogurines.mod.client;

import com.yogurines.mod.Yogurines;
import com.yogurines.mod.item.NoteTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;

/** El billete grande con su grabado que sale al pasar el ratón por encima. */
public class NoteTooltipComponent implements ClientTooltipComponent {
    private final ResourceLocation tex;

    public NoteTooltipComponent(NoteTooltip t) {
        this.tex = texture(t.value());
    }

    public static ResourceLocation texture(int value) {
        return new ResourceLocation(Yogurines.MOD_ID, "textures/gui/note_" + value + ".png");
    }

    @Override
    public int getHeight() {
        return 68;
    }

    @Override
    public int getWidth(Font font) {
        return 128;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        g.blit(tex, x, y + 2, 128, 64, 0, 0, 256, 128, 256, 128);
    }
}
