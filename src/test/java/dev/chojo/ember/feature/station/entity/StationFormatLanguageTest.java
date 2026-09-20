/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which language a station's documents come out in.
 *
 * <p>Every export that renders a template asks this, so the answer for a station that has said nothing
 * decides what most installations receive. It is German: that is the language of the interface the
 * button was pressed in, and English is something a station asks for rather than something it arrives
 * at by saying nothing.
 */
class StationFormatLanguageTest {

    @Test
    void englishIsAskedForByName() {
        assertEquals("en", StationFormat.languageOf(withLocale("en-GB")));
        assertEquals("en", StationFormat.languageOf(withLocale("en")));
    }

    @Test
    void germanIsAskedForByName() {
        assertEquals("de", StationFormat.languageOf(withLocale("de-DE")));
        assertEquals("de", StationFormat.languageOf(withLocale("de-AT")));
    }

    @Test
    void sayingNothingMeansGerman() {
        assertEquals("de", StationFormat.languageOf(withLocale(null)));
        assertEquals("de", StationFormat.languageOf(null));
    }

    /** Only two languages are shipped, so a third falls to the one everything else defaults to. */
    @Test
    void aLanguageWithNoTemplatesFallsToGerman() {
        assertEquals("de", StationFormat.languageOf(withLocale("fr-FR")));
    }

    private static Station withLocale(String locale) {
        return new Station(
                1,
                null,
                "Test",
                "Europe/Berlin",
                locale,
                null,
                null,
                false,
                null,
                ThemeFeel.ROUNDED,
                false,
                PublicKbMode.OFF,
                null,
                DiscoveryVisibility.NONE,
                null,
                false,
                false,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                StationKind.REGULAR,
                null,
                false,
                false);
    }
}
