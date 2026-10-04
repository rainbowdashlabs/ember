/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.jspecify.annotations.Nullable;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Reads the day of a stored date answer, such as a birth date. An answer is stored as an ISO date, and
 * one stored with a time of day after it still names its day in the first ten characters.
 */
public final class StoredDay {

    private StoredDay() {}

    /**
     * @param stored the stored answer, or null
     * @return the day it names, or empty where it is blank or names none
     */
    public static Optional<LocalDate> of(@Nullable String stored) {
        if (stored == null || stored.isBlank()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(stored.length() > 10 ? stored.substring(0, 10) : stored));
        } catch (DateTimeException notADay) {
            return Optional.empty();
        }
    }
}
