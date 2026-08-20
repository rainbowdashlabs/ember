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
 * Builds the PostgreSQL full-text search fragments a repository splices into its statements.
 * <p>
 * The text search configuration is the only part of a full-text expression that cannot be bound
 * as a parameter, so every fragment built here runs the requested configuration through
 * {@link #config(String)} first: anything outside the allow-list collapses to
 * {@link #DEFAULT_CONFIG}. User-supplied search text never reaches the statement - it is passed
 * as a bind name and the caller binds the value produced by {@link #prefixTerms(String)}.
 */
public final class FullTextSearch {

    /**
     * Configuration used for unknown or unsupported locales. {@code simple} performs no
     * stemming and exists in every PostgreSQL installation.
     */
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

    /**
     * The configuration that stems the language a station writes in. Everything unknown falls back
     * to {@link #DEFAULT_CONFIG}, which stems nothing and is therefore never wrong, only coarse.
     *
     * @param locale a locale such as {@code de-DE}, may be null
     */
    public static String forLocale(String locale) {
        if (locale == null || locale.isBlank()) return DEFAULT_CONFIG;
        int separator = locale.indexOf('-');
        String language = separator > 0 ? locale.substring(0, separator) : locale;
        return LOCALE_TO_CONFIG.getOrDefault(language.toLowerCase(Locale.ROOT), DEFAULT_CONFIG);
    }

    /**
     * Maps a requested text search configuration onto the allow-list, falling back to
     * {@link #DEFAULT_CONFIG} for anything unknown or absent. This is the only guard between a
     * caller-chosen configuration name and the SQL text, so every fragment builder applies it and
     * no input - including {@code null} - may leave the method without an allow-listed answer.
     *
     * @param requested the configuration name to validate, may be {@code null}
     * @return an allow-listed configuration name, never {@code null}
     */
    public static String config(String requested) {
        if (requested == null) return DEFAULT_CONFIG;
        return ALLOWED_CONFIGS.contains(requested) ? requested : DEFAULT_CONFIG;
    }

    /**
     * Builds a {@code to_tsvector} expression reading its text from a named bind parameter.
     *
     * @param requestedConfig the requested text search configuration
     * @param textBind        the name of the bind parameter holding the indexed text
     * @return the {@code to_tsvector} fragment
     */
    public static String vector(String requestedConfig, String textBind) {
        return "to_tsvector('%s', :%s)".formatted(config(requestedConfig), textBind);
    }

    /**
     * Builds a {@code to_tsquery} expression reading its terms from a named bind parameter. The
     * bound value is expected to come from {@link #prefixTerms(String)}.
     *
     * @param requestedConfig the requested text search configuration
     * @param queryBind       the name of the bind parameter holding the prepared terms
     * @return the {@code to_tsquery} fragment
     */
    public static String prefixQuery(String requestedConfig, String queryBind) {
        return "to_tsquery('%s', :%s)".formatted(config(requestedConfig), queryBind);
    }

    /**
     * Builds a {@code ts_headline} expression that highlights the matched terms in a snippet.
     *
     * @param requestedConfig the requested text search configuration
     * @param textExpression  the SQL expression producing the text to excerpt
     * @param tsQuery         the {@code to_tsquery} fragment to highlight against
     * @param options         the {@code ts_headline} option string, for example {@code MaxWords=30}
     * @return the {@code ts_headline} fragment
     */
    public static String headline(String requestedConfig, String textExpression, String tsQuery, String options) {
        return "ts_headline('%s', %s, %s, '%s')".formatted(config(requestedConfig), textExpression, tsQuery, options);
    }

    /**
     * Turns a user search string into {@code to_tsquery} terms with prefix matching, so
     * {@code "Notr"} still matches {@code "Notruf"}. Words are stripped of everything that is not
     * a letter, digit or underscore, get {@code :*} appended and are combined with {@code &}.
     * The result is a bind value, never spliced into SQL.
     *
     * <p>Words are filtered <em>after</em> stripping, not before. A token made entirely of
     * punctuation strips to nothing, and appending {@code :*} to it would produce a bare
     * {@code :*} - which is not valid tsquery syntax and makes {@code to_tsquery} reject the whole
     * query. A query with no usable words yields an empty string.
     *
     * @param query the raw user query
     * @return the prepared tsquery terms
     */
    public static String prefixTerms(String query) {
        return Arrays.stream(query.trim().split("\\s+"))
                .map(word -> word.replaceAll("[^\\w\\p{L}]", ""))
                .filter(word -> !word.isEmpty())
                .map(word -> word + ":*")
                .collect(Collectors.joining(" & "));
    }

    /**
     * Wraps a text expression so HTML tags and markdown punctuation are removed before the text is
     * excerpted, keeping generated snippets free of markup noise.
     *
     * @param textExpression the SQL expression producing the raw text
     * @return the stripping fragment
     */
    public static String stripMarkup(String textExpression) {
        return "regexp_replace(regexp_replace(%s, '<[^>]+>', ' ', 'g'), '[#*_~`>\\[\\]()!|]', '', 'g')"
                .formatted(textExpression);
    }
}
