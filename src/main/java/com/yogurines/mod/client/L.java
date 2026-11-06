package com.yogurines.mod.client;

import com.yogurines.mod.Tr;
import net.minecraft.client.resources.language.I18n;

/** Traducción en el cliente (español o inglés según el idioma del juego). */
public final class L {
    public static String t(String es, Object... args) {
        String k = Tr.key(es);
        if (I18n.exists(k)) return I18n.get(k, args);
        return args.length == 0 ? es : String.format(es, args);
    }

    /** Traduce un texto que viene del servidor si viene marcado; si no, lo deja tal cual. */
    public static String tr(String s) {
        if (s == null || !s.startsWith(Tr.PREFIX)) return s;
        String[] parts = s.substring(Tr.PREFIX.length()).split(String.valueOf(Tr.SEP), -1);
        String k = "yogurines.t." + parts[0];
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) args[i - 1] = tr(parts[i]);
        return I18n.exists(k) ? I18n.get(k, args) : parts[0];
    }

    private L() {}
}
