/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.BiPredicate;

public final class SlugGenerator {
    private SlugGenerator() {}

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) return "page";
        String normalized = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String ascii = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = ascii.replaceAll("[^a-z0-9]+", "-");
        return slug.replaceAll("^-+|-+$", "");
    }

    public static String uniqueSlug(String base, BiPredicate<String, Integer> existsExcluding, int excludeId) {
        String slug = toSlug(base);
        if (slug.isBlank()) slug = "page";
        if (!existsExcluding.test(slug, excludeId)) return slug;
        for (int i = 2; i < 1000; i++) {
            String candidate = slug + "-" + i;
            if (!existsExcluding.test(candidate, excludeId)) return candidate;
        }
        throw new IllegalStateException("Could not generate unique slug");
    }
}
