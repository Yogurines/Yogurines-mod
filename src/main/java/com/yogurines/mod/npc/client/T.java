package com.yogurines.mod.npc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

/** Textos traducidos de las pantallas (es_* = español, el resto = inglés). */
public final class T {
    public static String t(String key, Object... args) {
        return I18n.get("yogurines." + key, args);
    }

    /** true si el juego está en cualquier español (es_es, es_mx, es_ar...). */
    public static boolean spanish() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.startsWith("es");
    }

    private T() {}
}
