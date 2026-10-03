/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

/**
 * The format a date placeholder prints in, as its key names it after the bar: a ready-made one by its
 * name ({@code {{member.birthDate|long}}}) or an own one in {@link DateToken}s
 * ({@code {{member.birthDate|T. MMMM JJJJ}}}).
 *
 * <p>An own format is made of tokens and the separators space, full stop, comma, colon, slash and
 * hyphen, at most {@value #MAX_LENGTH} characters and at least one token. Any other letter or sign is
 * refused rather than printed, so a typing error shows when the template is saved and not in a document.
 *
 * @param preset  the ready-made format, or null for an own one
 * @param written what the key names after the bar
 */
public record DateFormat(@Nullable DatePreset preset, String written) {
    /** The most characters an own format may have. */
    public static final int MAX_LENGTH = 40;

    private static final String SEPARATORS = " .,:/-";

    /**
     * A format turned into a pattern of {@link DateTimeFormatter}.
     *
     * @param pattern the pattern
     * @param clock   whether it prints a time of day
     */
    private record Compiled(String pattern, boolean clock) {}

    /**
     * @param written what a key names after the bar
     * @return the format, or empty where it is neither a ready-made one nor a valid own one
     */
    public static Optional<DateFormat> of(String written) {
        var preset = DatePreset.of(written);
        if (preset.isPresent()) return Optional.of(of(preset.get()));
        return compile(written).map(compiled -> new DateFormat(null, written));
    }

    /**
     * @param preset a ready-made format
     * @return the format
     */
    public static DateFormat of(DatePreset preset) {
        return new DateFormat(preset, preset.written());
    }

    /** @return whether it prints a time of day */
    public boolean readsClock() {
        return compiled(DocumentLanguage.DE).clock();
    }

    /**
     * @param value    a day, or a day with a time of day
     * @param language the language of the template, which writes the names of months and weekdays
     * @return the value as it prints, or empty where the format reads a time of day the value lacks
     */
    public Optional<String> format(TemporalAccessor value, DocumentLanguage language) {
        var compiled = compiled(language);
        if (compiled.clock() && !value.isSupported(ChronoField.HOUR_OF_DAY)) return Optional.empty();
        return Optional.of(DateTimeFormatter.ofPattern(compiled.pattern(), language.locale())
                .format(value));
    }

    private Compiled compiled(DocumentLanguage language) {
        String tokens = preset == null ? written : preset.pattern(language);
        return compile(tokens).orElseThrow();
    }

    private static Optional<Compiled> compile(String written) {
        if (written.isBlank() || written.length() > MAX_LENGTH) return Optional.empty();
        var pattern = new StringBuilder();
        boolean clock = false;
        boolean anyToken = false;
        int index = 0;
        while (index < written.length()) {
            char at = written.charAt(index);
            int end = index;
            while (end < written.length() && written.charAt(end) == at) end++;
            if (SEPARATORS.indexOf(at) >= 0) {
                pattern.append(written, index, end);
            } else {
                var token = DateToken.of(written.substring(index, end));
                if (token.isEmpty()) return Optional.empty();
                pattern.append(token.get().pattern());
                clock |= token.get().clock();
                anyToken = true;
            }
            index = end;
        }
        return anyToken ? Optional.of(new Compiled(pattern.toString(), clock)) : Optional.empty();
    }
}
