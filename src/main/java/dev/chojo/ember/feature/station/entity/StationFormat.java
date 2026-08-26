/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * How a station's stored timezone and locale turn into the values an export or a report formats with.
 *
 * <p>Both fields are free text a station wrote, so both can be unusable, and every caller answers that the
 * same way: fall back and say so. Kept in one place because a station whose timezone stops parsing should
 * not print times one way in the attendance report and another in the inventory list.
 */
public final class StationFormat {
    private static final Logger log = LoggerFactory.getLogger(StationFormat.class);

    private StationFormat() {}

    /**
     * The zone a station's times are printed in, UTC when it has none or holds one that will not parse.
     *
     * @param station the station, or {@code null} when it could not be loaded
     * @return the zone to format with
     */
    public static ZoneId timezoneOf(Station station) {
        if (station == null || station.timezone() == null) return ZoneOffset.UTC;
        try {
            return ZoneId.of(station.timezone());
        } catch (Exception e) {
            log.warn("Station {} holds the unusable timezone '{}', UTC is used", station.id(), station.timezone(), e);
            return ZoneOffset.UTC;
        }
    }

    /**
     * The language a station's documents are built in, which is German or English and nothing else,
     * because those are the two every template ships in.
     *
     * @param station the station, or {@code null} when it could not be loaded
     * @return {@code de} or {@code en}
     */
    public static String languageOf(Station station) {
        if (station != null && station.locale() != null && station.locale().startsWith("de")) return "de";
        return "en";
    }

    /**
     * The locale a station's numbers and dates are formatted with, German when it has none or holds
     * one that will not parse.
     *
     * @param station the station, or {@code null} when it could not be loaded
     * @return the locale to format with
     */
    public static Locale localeOf(Station station) {
        if (station == null || station.locale() == null) return Locale.GERMAN;
        try {
            return Locale.forLanguageTag(station.locale());
        } catch (Exception e) {
            log.warn("Station {} holds the unusable locale '{}', German is used", station.id(), station.locale(), e);
            return Locale.GERMAN;
        }
    }
}
