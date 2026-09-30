/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The settings of a cell arrive as an object and are bound once the content type says which record
 * they are. Reading them from the tree rather than from JSON text is what keeps the request and the
 * cell in step.
 */
class CellConfigTest {

    private static JsonNode node(String json) {
        return CellConfig.MAPPER.readTree(json);
    }

    @Test
    void bindsAnObjectAgainstTheTypeBesideIt() {
        var config = CellConfig.parse(CellContentType.VIDEO, node("{\"autoplay\":true,\"loop\":false}"));

        var video = assertInstanceOf(CellConfig.VideoConfig.class, config);
        assertEquals(true, video.autoplay());
        assertEquals(false, video.loop());
    }

    @Test
    void answersTheEmptySettingsOfTheTypeWhenNoneAreNamed() {
        assertEquals(CellContentType.VIDEO.emptyConfig(), CellConfig.parse(CellContentType.VIDEO, (JsonNode) null));
        assertEquals(CellContentType.VIDEO.emptyConfig(), CellConfig.parse(CellContentType.VIDEO, node("null")));
        assertEquals(CellContentType.VIDEO.emptyConfig(), CellConfig.parse(CellContentType.VIDEO, node("{}")));
    }

    /** A tree that does not fit the type is the cell's own settings gone, not the request refused. */
    @Test
    void fallsBackToTheEmptySettingsWhenTheTreeDoesNotFit() {
        assertEquals(
                CellContentType.VIDEO.emptyConfig(),
                CellConfig.parse(CellContentType.VIDEO, node("{\"autoplay\":\"not a flag\"}")));
    }

    /**
     * A news block names its entry only by a well formed id, and a block stored before it named one
     * is still read with the text it was written with.
     */
    @Test
    void aNewsBlockNamesItsEntryOnlyByAWellFormedId() {
        var named = assertInstanceOf(
                CellConfig.NewsTeaserConfig.class,
                CellConfig.parse(
                        CellContentType.NEWS_TEASER, node("{\"newsUid\":\"3F2B8C4E-1A6D-4E7F-9B0C-5D8E2F1A7C33\"}")));
        assertEquals("3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c33", named.newsUid());

        var malformed = assertInstanceOf(
                CellConfig.NewsTeaserConfig.class,
                CellConfig.parse(CellContentType.NEWS_TEASER, node("{\"newsUid\":\"javascript:alert(1)\"}")));
        assertEquals(null, malformed.newsUid());

        var older = assertInstanceOf(
                CellConfig.NewsTeaserConfig.class,
                CellConfig.parse(
                        CellContentType.NEWS_TEASER,
                        node("{\"title\":\"Neue Drehleiter\",\"summary\":\"Eingeweiht\",\"url\":\"#\"}")));
        assertEquals(null, older.newsUid());
        assertEquals("Neue Drehleiter", older.title());
        assertEquals("Eingeweiht", older.summary());
    }

    /** The event blocks keep what their editor writes, so a saved block still names its event. */
    @Test
    void theEventBlocksKeepTheEventTheyName() {
        var featured = assertInstanceOf(
                CellConfig.FeaturedEventConfig.class,
                CellConfig.parse(
                        CellContentType.FEATURED_EVENT,
                        node("{\"eventUid\":\"7d7c1d5e-4e3a-4f9b-9d0e-2a4f6c1b8e11\",\"date\":\"2027-07-01\","
                                + "\"descriptionOverride\":\"Komm vorbei\"}")));
        assertEquals("7d7c1d5e-4e3a-4f9b-9d0e-2a4f6c1b8e11", featured.eventUid());
        assertEquals("2027-07-01", featured.date());
        assertEquals("Komm vorbei", featured.descriptionOverride());

        var upcoming = assertInstanceOf(
                CellConfig.UpcomingEventsConfig.class,
                CellConfig.parse(
                        CellContentType.UPCOMING_EVENTS,
                        node("{\"categoryIds\":[3,4],\"limit\":8,\"includeFederated\":false}")));
        assertEquals(java.util.List.of(3, 4), upcoming.categoryIds());
        assertEquals(8, upcoming.limit());
        assertEquals(false, upcoming.includeFederated());

        var recap = assertInstanceOf(
                CellConfig.PastEventRecapConfig.class,
                CellConfig.parse(
                        CellContentType.PAST_EVENT_RECAP,
                        node("{\"eventUid\":\"e-2\",\"recapDescription\":\"Schön war's\"}")));
        assertEquals("e-2", recap.eventUid());
        assertEquals("Schön war's", recap.recapDescription());
    }

    /** What an author writes into an event block names an event only when it is well formed. */
    @Test
    void anEventBlockDropsWhatIsNotAnEventOrADay() {
        var malformed = assertInstanceOf(
                CellConfig.FeaturedEventConfig.class,
                CellConfig.parse(
                        CellContentType.FEATURED_EVENT,
                        node("{\"eventUid\":\"javascript:alert(1)\",\"date\":\"../../x\"}")));
        assertEquals(null, malformed.eventUid());
        assertEquals("../../x", malformed.date(), "without an event the date is the old hand-written text");

        var badDay = assertInstanceOf(
                CellConfig.FeaturedEventConfig.class,
                CellConfig.parse(
                        CellContentType.FEATURED_EVENT,
                        node("{\"eventUid\":\"7d7c1d5e-4e3a-4f9b-9d0e-2a4f6c1b8e11\",\"date\":\"../../x\"}")));
        assertEquals(null, badDay.date());
    }

    /** Reading a cell back out of the database still starts from the text the column holds. */
    @Test
    void stillReadsTheTextTheColumnHolds() {
        var config = CellConfig.parse(CellContentType.VIDEO, "{\"autoplay\":true}");

        assertEquals(
                true, assertInstanceOf(CellConfig.VideoConfig.class, config).autoplay());
    }
}
