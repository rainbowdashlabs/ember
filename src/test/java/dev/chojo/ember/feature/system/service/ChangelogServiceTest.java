/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.conf.file.elements.Updates;
import dev.chojo.ember.feature.system.service.ChangelogService.ChangelogEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The changelog as the instance carries it.
 *
 * <p>Read against the files that ship rather than against a fixture, because what this has to be
 * right about is the real thing: the shape somebody actually writes the entries in, and the version
 * this build says it is.
 */
class ChangelogServiceTest {

    private final ChangelogService service = new ChangelogService(new Updates());

    @Test
    void theShippedChangelogIsReadAndSplitIntoItsVersions() {
        var versions = service.all("de");

        assertFalse(versions.isEmpty(), "the changelog travels in the jar");
        assertTrue(
                versions.stream().allMatch(entry -> entry.version().matches("\\d+(\\.\\d+)*")),
                "a version is the numbers alone");
        assertTrue(
                versions.stream().noneMatch(entry -> entry.body().isBlank()),
                "no version is listed with nothing in it");
    }

    /** Newest first, and by the numbers: 26.9.0 is older than 26.17.0 rather than later in the alphabet. */
    @Test
    void theVersionsRunNewestFirst() {
        var versions = service.all("de");

        for (int i = 1; i < versions.size(); i++) {
            String newer = versions.get(i - 1).version();
            String older = versions.get(i).version();
            assertTrue(ChangelogService.compareVersions(newer, older) > 0, newer + " should stand above " + older);
        }
    }

    @Test
    void aVersionIsReadOutOfTheFileOnItsOwn() {
        var newest = service.all("de").getFirst();

        var alone = service.forVersion("de", newest.version()).orElseThrow();

        assertEquals(newest.version(), alone.version());
        assertEquals(newest.body(), alone.body());
    }

    /**
     * A version the file does not name is absent rather than empty. A development build has no
     * section of its own, and an announcement with nothing in it says less than nothing.
     */
    @Test
    void aVersionTheChangelogDoesNotNameIsAbsent() {
        assertTrue(service.forVersion("de", "1.0.0").isEmpty());
        assertTrue(service.forVersion("de", "").isEmpty());
        assertTrue(service.forVersion("de", null).isEmpty());
    }

    /**
     * The German file begins where it was introduced, so what it has not reached is still readable
     * in English rather than missing from the list.
     */
    @Test
    void aVersionWithNoGermanTextFallsBackToTheEnglishOne() {
        var german = service.all("de");
        var english = service.all("en");

        assertEquals(english.size(), german.size(), "every version is listed in both, translated or not");
    }

    /** What the instance reports about itself carries more than the version a changelog is keyed by. */
    @Test
    void aVersionIsReadOutOfWhatTheInstanceReports() {
        assertEquals("26.17.0", ChangelogService.normalise("26.17.0"));
        assertEquals("26.17.0", ChangelogService.normalise("v26.17.0"));
        assertEquals("26.17.0", ChangelogService.normalise("26.17.0 main-1a2b3c4 @ 2026-09-15 10:00"));
        assertEquals("26.17.0", ChangelogService.normalise("26.17.0 snapshot"));
        assertEquals("", ChangelogService.normalise("not a version"));
        assertEquals("", ChangelogService.normalise(null));
    }

    @Test
    void versionsAreComparedByTheirNumbers() {
        assertTrue(ChangelogService.compareVersions("26.17.0", "26.9.0") > 0, "17 is after 9");
        assertTrue(ChangelogService.compareVersions("26.16.1", "26.17.0") < 0);
        assertEquals(0, ChangelogService.compareVersions("26.17.0", "v26.17.0"));
        assertTrue(ChangelogService.compareVersions("26.17", "26.17.0") == 0, "a missing segment is a zero");
    }

    private static ChangelogService withTags(String releasesJson) {
        return new ChangelogService(new Updates(), releasesJson);
    }

    private static ChangelogEntry versionOf(ChangelogService service, String version) {
        return service.forVersion("de", version).orElseThrow();
    }

    @Test
    void aTaggedVersionSaysWhenItWasReleasedAndWhereItsChangesAre() {
        var service = withTags("""
                {
                  "26.17.0": {"tag": "v26.17.0", "releasedAt": "2026-09-15T12:40:08Z"},
                  "26.16.0": {"tag": "v26.16.0", "releasedAt": "2026-09-14T15:03:30Z"}
                }""");

        var entry = versionOf(service, "26.17.0");

        assertEquals(Instant.parse("2026-09-15T12:40:08Z"), entry.releasedAt());
        assertEquals("https://github.com/rainbowdashlabs/ember/compare/v26.16.0...v26.17.0", entry.compareUrl());
    }

    /**
     * A release can be cut without an entry of its own, and a comparison that skipped it would claim
     * its changes for the entry above it.
     */
    @Test
    void theComparisonRunsAgainstTheReleaseBeforeItRatherThanTheEntryAboveIt() {
        var service = withTags("""
                {
                  "26.17.0": {"tag": "v26.17.0", "releasedAt": "2026-09-15T12:40:08Z"},
                  "26.16.9": {"tag": "v26.16.9", "releasedAt": "2026-09-14T20:00:00Z"},
                  "26.16.0": {"tag": "v26.16.0", "releasedAt": "2026-09-14T15:03:30Z"}
                }""");

        var entry = versionOf(service, "26.17.0");

        assertEquals(
                "https://github.com/rainbowdashlabs/ember/compare/v26.16.9...v26.17.0",
                entry.compareUrl(),
                "the tag before it, entry or no entry");
    }

    /** Nothing older to compare against, so the oldest release is offered no comparison. */
    @Test
    void theOldestReleaseIsOfferedNoComparison() {
        var service = withTags("""
                {"26.16.0": {"tag": "v26.16.0", "releasedAt": "2026-09-14T15:03:30Z"}}""");

        var entry = versionOf(service, "26.16.0");

        assertNotNull(entry.releasedAt());
        assertNull(entry.compareUrl());
    }

    /** A checkout without its history knows no tags, and the entries are readable without them. */
    @Test
    void aBuildThatFoundNoTagsListsItsVersionsWithoutDates() {
        var service = withTags(null);

        var versions = service.all("de");

        assertFalse(versions.isEmpty(), "the entries are still there");
        assertTrue(
                versions.stream().allMatch(entry -> entry.releasedAt() == null && entry.compareUrl() == null),
                "nothing is claimed about when they were released");
    }

    /** A version named in the changelog that was never tagged carries neither. */
    @Test
    void aVersionWithNoTagCarriesNeitherDateNorLink() {
        var service = withTags("""
                {"26.17.0": {"tag": "v26.17.0", "releasedAt": "2026-09-15T12:40:08Z"}}""");

        var entry = versionOf(service, "26.16.0");

        assertNull(entry.releasedAt());
        assertNull(entry.compareUrl());
    }
}
