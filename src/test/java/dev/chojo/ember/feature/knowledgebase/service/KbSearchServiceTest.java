/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KbSearchServiceTest extends RepositoryTestBase {
    private static KbSearchService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new KbSearchService(knowledgeBaseRepo, stationRepo);
        station = stationRepo.create("KbSearchStation");
        account = accountRepo.create("kb-search@test.com", "Kb", "SearchTester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static KbFile createFile(String name, String description) {
        return knowledgeBaseRepo.createFile(
                station.id(), null, name, description, KbFileType.MARKDOWN, "text/markdown", 0, null, member.id());
    }

    @Test
    void blankQueriesMatchNothing() {
        assertTrue(service.search(station.id(), "").isEmpty());
        assertTrue(service.search(station.id(), null).isEmpty());
        assertTrue(service.searchWithSnippets(station.id(), "  ").isEmpty());
        assertTrue(service.searchWithSnippets(station.id(), null).isEmpty());
    }

    /**
     * A reindexed file is found by a word from its body, not only by its name, and comes back with
     * an excerpt around the match.
     */
    @Test
    void reindexedBodiesBecomeSearchable() {
        var file = createFile("Hydrant Manual", "Water supply");
        service.reindex(file.id(), "The pressure regulator sits behind the coupling.");

        assertTrue(service.search(station.id(), "regulator").stream().anyMatch(f -> f.id() == file.id()));
        var snippets = service.searchWithSnippets(station.id(), "regulator");
        assertTrue(snippets.stream().anyMatch(r -> r.file().id() == file.id()));
        assertFalse(snippets.getFirst().snippet().isBlank());

        knowledgeBaseRepo.purgeFile(file.id());
    }

    /**
     * The index holds words, not markup: a body wrapped in HTML and markdown punctuation is still
     * matched by the words inside it.
     */
    @Test
    void markupIsStrippedBeforeIndexing() {
        var file = createFile("Markup Doc", "");
        service.reindex(file.id(), "<p>## **Zumischer** _kalibrieren_</p>");

        assertTrue(service.search(station.id(), "Zumischer").stream().anyMatch(f -> f.id() == file.id()));

        knowledgeBaseRepo.purgeFile(file.id());
    }

    /**
     * A file with nothing but a name still lands in the index under that name, and a file whose
     * name, description and body are all empty is skipped rather than indexed as blank.
     */
    @Test
    void filesAreIndexedByTheirNameWhenTheyHaveNoBody() {
        var named = createFile("Einsatzplan", "");
        service.reindex(named.id(), null);
        assertTrue(service.search(station.id(), "Einsatzplan").stream().anyMatch(f -> f.id() == named.id()));

        var blank = createFile(" ", " ");
        service.reindex(blank.id(), null);

        knowledgeBaseRepo.purgeFile(named.id());
        knowledgeBaseRepo.purgeFile(blank.id());
    }

    @Test
    void reindexingAnUnknownFileDoesNothing() {
        service.reindex(999999, "orphaned text");
    }

    /**
     * The text search configuration follows the station's locale, so German content is stemmed as
     * German; anything unrecognised falls back to verbatim indexing.
     */
    @Test
    void theStationLocaleDecidesTheTextSearchConfiguration() {
        String original = stationRepo.findById(station.id()).orElseThrow().locale();
        stationRepo.updateLocale(station.id(), "de-DE");
        assertEquals("german", service.textSearchConfig(station.id()));

        stationRepo.updateLocale(station.id(), "en");
        assertEquals("english", service.textSearchConfig(station.id()));

        stationRepo.updateLocale(station.id(), "xx");
        assertEquals("simple", service.textSearchConfig(station.id()));

        stationRepo.updateLocale(station.id(), "");
        assertEquals("simple", service.textSearchConfig(station.id()));

        assertEquals("simple", service.textSearchConfig(999999));

        stationRepo.updateLocale(station.id(), original);
    }
}
