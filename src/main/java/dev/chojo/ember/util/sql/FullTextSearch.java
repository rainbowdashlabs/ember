/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PostgreSQL full-text fragments. The text search configuration is the one part that cannot be bound, so every
 * fragment passes it through the allow-list of {@link #config(String)}; search text only ever travels as a bind.
 */
public final class FullTextSearch {

    /** Stems nothing and exists in every installation, so it is coarse but never wrong. */
    public static final String DEFAULT_CONFIG = "simple";

    private static final Set<String> ALLOWED_CONFIGS =
            Set.of("simple", "german", "english", "french", "spanish", "italian", "dutch", "portuguese", "russian");

    private static final Map<String, String> LOCALE_TO_CONFIG = Map.of(
            "de", "german",
            "en", "english",
            "fr", "french",
            "es", "spanish",
            "it", "italian",
            "nl", "dutch",
            "pt", "portuguese",
            "ru", "russian");

    private FullTextSearch() {}

    /** The configuration for a locale such as {@code de-DE}, {@link #DEFAULT_CONFIG} for anything unknown. */
    public static String forLocale(String locale) {
        if (locale == null || locale.isBlank()) return DEFAULT_CONFIG;
        int separator = locale.indexOf('-');
        String language = separator > 0 ? locale.substring(0, separator) : locale;
        return LOCALE_TO_CONFIG.getOrDefault(language.toLowerCase(Locale.ROOT), DEFAULT_CONFIG);
    }

    /** The requested configuration if allow-listed, else {@link #DEFAULT_CONFIG}; never {@code null}. */
    public static String config(String requested) {
        if (requested == null) return DEFAULT_CONFIG;
        return ALLOWED_CONFIGS.contains(requested) ? requested : DEFAULT_CONFIG;
    }

    /** {@code to_tsvector} over the text in the named bind. */
    public static String vector(String requestedConfig, String textBind) {
        return "to_tsvector('%s', :%s)".formatted(config(requestedConfig), textBind);
    }

    /** {@code to_tsquery} over the named bind, which holds {@link #prefixTerms(String)}. */
    public static String prefixQuery(String requestedConfig, String queryBind) {
        return "to_tsquery('%s', :%s)".formatted(config(requestedConfig), queryBind);
    }

    /**
     * {@code ts_headline} highlighting the query's matches in the text.
     *
     * @param options the {@code ts_headline} option string, for example {@code MaxWords=30}
     */
    public static String headline(String requestedConfig, String textExpression, String tsQuery, String options) {
        return "ts_headline('%s', %s, %s, '%s')".formatted(config(requestedConfig), textExpression, tsQuery, options);
    }

    /**
     * A user query as prefix terms joined with {@code &}, so {@code Notr} matches {@code Notruf}; empty when
     * no word is left. Words are filtered after stripping, since a bare {@code :*} makes the whole query invalid.
     */
    public static String prefixTerms(String query) {
        return Arrays.stream(query.trim().split("\\s+"))
                .map(word -> word.replaceAll("[^\\w\\p{L}]", ""))
                .filter(word -> !word.isEmpty())
                .map(word -> word + ":*")
                .collect(Collectors.joining(" & "));
    }

    /** The text expression without HTML tags and markdown punctuation, so snippets carry no markup. */
    public static String stripMarkup(String textExpression) {
        return "regexp_replace(regexp_replace(%s, '<[^>]+>', ' ', 'g'), '[#*_~`>\\[\\]()!|]', '', 'g')"
                .formatted(textExpression);
    }
}
