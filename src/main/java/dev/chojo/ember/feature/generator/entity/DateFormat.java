/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
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
 * The editor asks the same rules about an own format while it is typed ({@link #check}).
 *
 * @param preset  the ready-made format, or null for an own one
 * @param written what the key names after the bar
 */
public record DateFormat(@Nullable DatePreset preset, String written) {
    /** The most characters an own format may have. */
    public static final int MAX_LENGTH = 40;

    /** The day the editor shows every date format for: a Saturday in October, at half past six in the evening. */
    public static final LocalDateTime EXAMPLE_DAY = LocalDateTime.of(2026, 10, 3, 18, 30);

    private static final String SEPARATORS = " .,:/-";

    /** An own format as it was read: turned into a pattern, or refused. */
    private sealed interface Reading permits Compiled, Refused {}

    /**
     * A format turned into a pattern of {@link DateTimeFormatter}.
     *
     * @param pattern the pattern
     * @param clock   whether it prints a time of day
     */
    private record Compiled(String pattern, boolean clock) implements Reading {
        DateTimeFormatter formatter(DocumentLanguage language) {
            return DateTimeFormatter.ofPattern(pattern, language.locale());
        }
    }

    /**
     * A format that cannot be printed.
     *
     * @param problem why
     * @param detail  what the problem names, or null
     */
    private record Refused(
            DateFormatProblem problem, @Nullable String detail) implements Reading {}

    /**
     * @param written what a key names after the bar
     * @return the format, or empty where it is neither a ready-made one nor a valid own one
     */
    public static Optional<DateFormat> of(String written) {
        var preset = DatePreset.of(written);
        if (preset.isPresent()) return Optional.of(of(preset.get()));
        return compile(written) instanceof Compiled ? Optional.of(new DateFormat(null, written)) : Optional.empty();
    }

    /**
     * @param preset a ready-made format
     * @return the format
     */
    public static DateFormat of(DatePreset preset) {
        return new DateFormat(preset, preset.written());
    }

    /**
     * Reads an own format as the editor offers it while it is typed.
     *
     * @param written  the format as written
     * @param language the language of the template, which writes the names of months and weekdays
     * @param clock    whether the date it is for has a time of day
     * @return the example day in the format, or why it cannot be printed
     */
    public static DateFormatCheck check(String written, DocumentLanguage language, boolean clock) {
        return switch (compile(written)) {
            case Refused refused -> DateFormatCheck.refused(refused.problem(), refused.detail());
            case Compiled compiled
            when compiled.clock() && !clock -> DateFormatCheck.refused(DateFormatProblem.CLOCK, null);
            case Compiled compiled ->
                DateFormatCheck.printed(compiled.formatter(language).format(EXAMPLE_DAY));
        };
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
        return Optional.of(compiled.formatter(language).format(value));
    }

    private Compiled compiled(DocumentLanguage language) {
        String tokens = preset == null ? written : preset.pattern(language);
        if (compile(tokens) instanceof Compiled compiled) return compiled;
        throw new IllegalStateException("A date format that cannot be printed was taken: " + tokens);
    }

    private static Reading compile(String written) {
        if (written.isBlank()) return new Refused(DateFormatProblem.EMPTY, null);
        if (written.length() > MAX_LENGTH) return new Refused(DateFormatProblem.TOO_LONG, String.valueOf(MAX_LENGTH));
        var pattern = new StringBuilder();
        boolean clock = false;
        boolean anyToken = false;
        int index = 0;
        while (index < written.length()) {
            char at = written.charAt(index);
            int end = index;
            while (end < written.length() && written.charAt(end) == at) end++;
            String run = written.substring(index, end);
            if (SEPARATORS.indexOf(at) >= 0) {
                pattern.append(run);
            } else {
                var token = DateToken.of(run);
                if (token.isEmpty()) return new Refused(DateFormatProblem.UNKNOWN, run);
                pattern.append(token.get().pattern());
                clock |= token.get().clock();
                anyToken = true;
            }
            index = end;
        }
        return anyToken ? new Compiled(pattern.toString(), clock) : new Refused(DateFormatProblem.NO_TOKEN, null);
    }
}
