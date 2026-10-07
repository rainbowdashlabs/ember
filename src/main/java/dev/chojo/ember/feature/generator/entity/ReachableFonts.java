/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Every font family a template can print with, the nearest owner's where a name is found at several,
 * and a built-in family only where no owner uploaded one of its name.
 *
 * <p>Family names are compared without case, so "Roboto" at a station hides "roboto" at the instance.
 */
public final class ReachableFonts {
    private final Map<String, FontFamily> families;

    private ReachableFonts(Map<String, FontFamily> families) {
        this.families = families;
    }

    /** No fonts at all, where every text prints in the default font. */
    public static ReachableFonts none() {
        return new ReachableFonts(Map.of());
    }

    /**
     * Groups font files into families, the nearest owner winning a name.
     *
     * @param fonts the files of every owner reached
     * @return the families
     */
    public static ReachableFonts of(Collection<DocumentFont> fonts) {
        return of(fonts, List.of());
    }

    /**
     * Groups font files into families, the nearest owner winning a name, and adds the built-in
     * families whose names no owner took.
     *
     * @param fonts   the files of every owner reached
     * @param builtIn the families every installation has
     * @return the families
     */
    public static ReachableFonts of(Collection<DocumentFont> fonts, Collection<FontFamily> builtIn) {
        var byOwner = new EnumMap<FontOrigin, Map<String, List<DocumentFont>>>(FontOrigin.class);
        for (var font : fonts) {
            byOwner.computeIfAbsent(font.origin(), origin -> new LinkedHashMap<>())
                    .computeIfAbsent(keyOf(font.family()), key -> new ArrayList<>())
                    .add(font);
        }
        var families = new LinkedHashMap<String, FontFamily>();
        for (var owner : byOwner.entrySet()) {
            for (var family : owner.getValue().entrySet()) {
                if (families.containsKey(family.getKey())) continue;
                var files = new EnumMap<FontStyle, FontFace>(FontStyle.class);
                family.getValue().forEach(file -> files.put(file.style(), file));
                String name = family.getValue().getFirst().family();
                families.put(family.getKey(), new FontFamily(name, owner.getKey(), files));
            }
        }
        builtIn.forEach(family -> families.putIfAbsent(keyOf(family.name()), family));
        return new ReachableFonts(families);
    }

    /**
     * @param family a family name, in any case, or null for the default font
     * @return the family, or empty where it is null or not reached
     */
    public Optional<FontFamily> find(@Nullable String family) {
        if (family == null || family.isBlank()) return Optional.empty();
        return Optional.ofNullable(families.get(keyOf(family)));
    }

    /** @return every family, sorted by name */
    public List<FontFamily> families() {
        return families.values().stream()
                .sorted(Comparator.comparing(family -> keyOf(family.name())))
                .toList();
    }

    private static String keyOf(String family) {
        return family.strip().toLowerCase(Locale.ROOT);
    }
}
