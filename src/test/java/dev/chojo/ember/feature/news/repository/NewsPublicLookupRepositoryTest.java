/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a news block may name and show is what the station's public blog shows: published entries of
 * that station, put on the blog and kept to nobody in particular. A draft, a restricted entry, one
 * left off the blog and one of another station are found by neither the lookup nor the search.
 */
class NewsPublicLookupRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Station otherStation;
    private static Account account;
    private static MemberGroup group;
    private static News older;
    private static News newer;
    private static News draft;
    private static News restricted;
    private static News offTheBlog;
    private static News elsewhere;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("News Lookup Station");
        otherStation = stationRepo.create("News Lookup Elsewhere");
        account = accountRepo.create("news-lookup@test.com", "News", "Lookup");
        stationMemberRepo.create(station.id(), account.id());
        group = memberGroupRepo.create(station.id(), "Vorstand");

        older = onTheBlog(station, "Neue Drehleiter", "Die Drehleiter ist da", Instant.parse("2026-01-10T10:00:00Z"));
        newer = onTheBlog(station, "Drehleiter im Einsatz", "Erster Einsatz", Instant.parse("2026-03-10T10:00:00Z"));
        draft = onTheBlog(station, "Drehleiter Entwurf", "Noch nicht fertig", null);
        restricted = onTheBlog(station, "Drehleiter intern", "Nur Vorstand", Instant.parse("2026-02-10T10:00:00Z"));
        restrictionRepo.setRestrictions(
                RestrictionType.NEWS,
                restricted.id(),
                new RestrictionSelection(List.of(), List.of(group.id()), List.of(), List.of(), null));
        offTheBlog = newsRepo.create(station.id(), "Drehleiter nicht im Blog", "Intern", "<p>Intern</p>", null);
        elsewhere =
                onTheBlog(otherStation, "Drehleiter anderswo", "Andere Wache", Instant.parse("2026-02-01T10:00:00Z"));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accountRepo.delete(account.id());
    }

    private static News onTheBlog(Station owner, String title, String body, Instant publishedAt) {
        var news = newsRepo.create(owner.id(), title, body, "<p>" + body + "</p>", null);
        newsRepo.updatePublicBlog(news.id(), true);
        Query.query("""
                        UPDATE news
                        SET published_at = :published_at
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("published_at", publishedAt, INSTANT_TIMESTAMP)
                        .bind("id", news.id()))
                .update();
        return news;
    }

    @Test
    void theLookupFindsAPublishedPublicEntryOfTheStation() {
        var found = newsRepo.findPublicByUid(station.id(), older.publicUid());

        assertEquals(older.id(), found.orElseThrow().id());
    }

    @Test
    void theLookupFindsNothingThePublicBlogDoesNotShow() {
        assertTrue(newsRepo.findPublicByUid(station.id(), draft.publicUid()).isEmpty(), "draft");
        assertTrue(
                newsRepo.findPublicByUid(station.id(), restricted.publicUid()).isEmpty(), "restricted");
        assertTrue(
                newsRepo.findPublicByUid(station.id(), offTheBlog.publicUid()).isEmpty(), "off the blog");
        assertTrue(newsRepo.findPublicByUid(station.id(), elsewhere.publicUid()).isEmpty(), "another station");
        assertTrue(newsRepo.findPublicByUid(station.id(), UUID.randomUUID()).isEmpty(), "no such entry");
    }

    @Test
    void theSearchMatchesTheTitleWhateverItsCaseNewestFirst() {
        var found = newsRepo.findPublicBlogEntries(station.id(), "DREHLEITER", 0, 10);

        assertEquals(
                List.of(newer.id(), older.id()), found.stream().map(News::id).toList());
    }

    @Test
    void theSearchLooksAtTheTitleOnly() {
        assertTrue(newsRepo.findPublicBlogEntries(station.id(), "Einsatz", 0, 10).stream()
                .anyMatch(news -> news.id() == newer.id()));
        assertTrue(newsRepo.findPublicBlogEntries(station.id(), "ist da", 0, 10).isEmpty());
    }

    @Test
    void theSearchIsLimitedAndPaged() {
        assertEquals(
                List.of(newer.id()),
                newsRepo.findPublicBlogEntries(station.id(), null, 0, 1).stream()
                        .map(News::id)
                        .toList());
        assertEquals(
                List.of(older.id()),
                newsRepo.findPublicBlogEntries(station.id(), null, 1, 1).stream()
                        .map(News::id)
                        .toList());
    }
}
