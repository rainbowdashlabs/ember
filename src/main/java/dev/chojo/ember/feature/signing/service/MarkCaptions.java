/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The small print under a signature picture: who signed by their official name, and on which day and how,
 * in the document's language and the station's time zone.
 *
 * @param english whether the document is written in English rather than German
 * @param zone    the station's time zone, which decides the day
 */
record MarkCaptions(boolean english, ZoneId zone) {

    /**
     * @param language the language code, {@code en} for English and German for anything else
     * @param zone     the station's time zone
     * @return the captions in that language
     */
    static MarkCaptions of(String language, ZoneId zone) {
        return new MarkCaptions("en".equals(language), zone);
    }

    /**
     * @param name     the signer's official name
     * @param signedAt when they signed
     * @param page     the page the signature record starts on
     * @return the caption of a person's signing act, which points to its record
     */
    List<String> act(String name, Instant signedAt, int page) {
        String day = day(signedAt);
        return List.of(
                name,
                english
                        ? "Signed electronically on %s, record on page %d".formatted(day, page)
                        : "Elektronisch signiert am %s, Nachweis Seite %d".formatted(day, page));
    }

    /**
     * @param name     the issuer's official name
     * @param signedAt when the letter was signed
     * @return the caption of a letter signed for its issuer
     */
    List<String> issued(String name, Instant signedAt) {
        String day = day(signedAt);
        return List.of(
                name,
                english ? "Signed electronically on %s".formatted(day) : "Elektronisch signiert am %s".formatted(day));
    }

    private String day(Instant instant) {
        var format = english
                ? DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
                : DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);
        return format.withZone(zone).format(instant);
    }
}
