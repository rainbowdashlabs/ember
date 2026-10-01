/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import dev.chojo.ember.util.DocumentWord;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An answer as an export prints it.
 *
 * <p>Every export wrote its own: one printed yes as a fixed German word, two printed the stored text
 * raw, so a member field showed numbers and a date showed the way the database writes it. Here a date
 * reads as a day, a time as hours and minutes, yes and no in the export's language, and members by
 * name.
 */
public final class QuestionText {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private QuestionText() {}

    /**
     * The answer as an export prints it.
     *
     * <p>Whatever does not read as its kind is printed as it was stored, because an export that
     * leaves out an odd value hides it from the one person who could put it right.
     *
     * @param type     the field type
     * @param stored   the answer as the feature holds it, in any of its stored shapes
     * @param names    the names of the members it may name, by member id; a member missing here is
     *                 printed by number
     * @param language the export's language, {@code de} or {@code en}
     * @return the text to print, empty where nothing was answered or the type holds no value
     */
    public static String format(FieldType type, @Nullable String stored, Map<Integer, String> names, String language) {
        String value = QuestionValues.read(stored);
        var kind = type.kind();
        if (value.isEmpty() || kind.isEmpty()) return "";
        return switch (kind.get()) {
            case BOOLEAN -> yesOrNo(value, language);
            case DATE -> day(value);
            case TIME -> clock(value);
            case MEMBER, MEMBER_LIST -> members(value, names);
            default -> value;
        };
    }

    private static String yesOrNo(String value, String language) {
        if (value.equalsIgnoreCase("true") || value.equals("1")) return DocumentWord.YES.in(language);
        if (value.equalsIgnoreCase("false") || value.equals("0")) return DocumentWord.NO.in(language);
        return value;
    }

    private static String day(String value) {
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value)
                    .format(DAY);
        } catch (DateTimeParseException notADay) {
            return value;
        }
    }

    private static String clock(String value) {
        try {
            return LocalTime.parse(value).format(CLOCK);
        } catch (DateTimeParseException notATime) {
            return value;
        }
    }

    private static String members(String value, Map<Integer, String> names) {
        var ids = QuestionValues.memberIds(value);
        if (ids.isEmpty()) return value;
        return ids.stream()
                .map(id -> names.getOrDefault(id, String.valueOf(id)))
                .collect(Collectors.joining(", "));
    }
}
