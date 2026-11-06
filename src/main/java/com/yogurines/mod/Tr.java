package com.yogurines.mod;

import java.text.Normalizer;

/**
 * Textos traducibles. En el código se escribe el texto en español; la clave de idioma sale de él
 * (yogurines.t.<texto_sin_tildes>). El servidor manda "#tr:clave␟arg␟arg" y cada cliente lo pone
 * en su idioma (es_es o en_us).
 */
public final class Tr {
    public static final String PREFIX = "#tr:";
    public static final char SEP = '\u001f';

    public static String slug(String es) {
        String n = Normalizer.normalize(es, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        n = n.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (n.length() > 48) n = n.substring(0, 48).replaceAll("_+$", "");
        return n.isEmpty() ? "x" : n;
    }

    public static String key(String es) {
        return "yogurines.t." + slug(es);
    }

    /** Texto para mandar desde el servidor: se traduce en cada cliente. */
    public static String of(String es, Object... args) {
        StringBuilder b = new StringBuilder(PREFIX).append(slug(es));
        for (Object a : args) b.append(SEP).append(a);
        return b.toString();
    }

    private Tr() {}
}
