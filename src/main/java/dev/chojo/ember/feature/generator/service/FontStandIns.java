/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The built-in fonts suggested in place of common fonts nobody may ship, and how closely each matches.
 *
 * <p>A font of the same measure sets every letter as wide as the original, so a document keeps its lines:
 * Liberation for Arial, Helvetica, Times New Roman and Courier New, Carlito for Calibri, Caladea for
 * Cambria. A look-alike only resembles the original and sets lines a little differently: Open Sans for
 * humanist sans serifs such as Frutiger, Myriad and Segoe UI, and the family of the same measure for other
 * cuts of the same font, such as Carlito for Calibri Light. Either is only ever a suggestion; the person
 * importing confirms it.
 *
 * <p>Fonts of symbols (Symbol, Wingdings, the font Word draws check boxes in) stand for no text, and the
 * machine's own fonts draw them.
 */
final class FontStandIns {
    private static final Map<String, String> SAME_MEASURE = Map.of(
            "arial", "Liberation Sans",
            "helvetica", "Liberation Sans",
            "times new roman", "Liberation Serif",
            "times", "Liberation Serif",
            "courier new", "Liberation Mono",
            "courier", "Liberation Mono",
            "calibri", "Carlito",
            "cambria", "Caladea");

    /** The look-alikes by the beginnings of the names of the fonts they resemble, in lower case. */
    private static final Map<String, String> LOOKALIKES = lookalikes();

    private static final Set<String> SYMBOLS =
            Set.of("symbol", "wingdings", "webdings", "ms gothic", "ms mincho", "segoe ui symbol");

    /** A weight in a family's name that makes it a light cut. */
    private static final Pattern LIGHT = Pattern.compile("\\b(light|thin|35|45)\\b");

    private FontStandIns() {}

    private static Map<String, String> lookalikes() {
        var lookalikes = new LinkedHashMap<String, String>();
        for (String humanist : new String[] {"frutiger", "myriad", "segoe ui", "verdana", "tahoma"}) {
            lookalikes.put(humanist, "Open Sans");
        }
        lookalikes.put("calibri", "Carlito");
        lookalikes.put("cambria", "Caladea");
        lookalikes.put("arial", "Liberation Sans");
        lookalikes.put("helvetica", "Liberation Sans");
        lookalikes.put("times", "Liberation Serif");
        lookalikes.put("courier", "Liberation Mono");
        return Map.copyOf(lookalikes);
    }

    /**
     * A built-in font suggested in place of another.
     *
     * @param family      the built-in family
     * @param sameMeasure whether it sets every letter as wide as the original
     */
    record StandIn(String family, boolean sameMeasure) {}

    /**
     * @param family a family a document names
     * @return the built-in family suggested in its place, the one of the same measure where there is one
     */
    static Optional<StandIn> of(String family) {
        String name = lower(family);
        var exact = SAME_MEASURE.get(name);
        if (exact != null) return Optional.of(new StandIn(exact, true));
        return LOOKALIKES.entrySet().stream()
                .filter(entry -> name.startsWith(entry.getKey()))
                .map(entry -> new StandIn(entry.getValue(), false))
                .findFirst();
    }

    /**
     * @param family a family a document names
     * @return whether it holds symbols rather than letters
     */
    static boolean symbols(String family) {
        return SYMBOLS.contains(lower(family));
    }

    /**
     * @param family a family a document names
     * @return whether its name says it is a light cut, such as Frutiger 45 Light
     */
    static boolean light(String family) {
        return LIGHT.matcher(lower(family)).find();
    }

    private static String lower(String family) {
        return family.strip().toLowerCase(Locale.ROOT);
    }
}
