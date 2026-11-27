/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.api.refusal.FeedRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.feed.entity.StationFeedLevel;
import dev.chojo.ember.feature.feed.repository.PersonalFeedFilterRepository;
import dev.chojo.ember.feature.feed.repository.StationCalendarFeedRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calendar feeds of a station and the category filter of the personal feed, against the database.
 */
class StationCalendarFeedServiceTest extends RepositoryTestBase {
    private static StationCalendarFeedService service;
    private static PersonalFeedFilterService personalFilter;
    private static Station station;
    private static Station otherStation;
    private static Account account;
    private static StationMember member;
    private static int youth;
    private static int exercise;
    private static int foreign;

    @BeforeAll
    static void setup() {
        var categoryFilter = new FeedCategoryFilter(new EventCategoryService(eventCategoryRepo));
        service = new StationCalendarFeedService(new StationCalendarFeedRepository(), categoryFilter);
        personalFilter = new PersonalFeedFilterService(new PersonalFeedFilterRepository(), categoryFilter);
        station = stationRepo.create("CalendarFeedStation");
        otherStation = stationRepo.create("CalendarFeedOtherStation");
        account = accountRepo.create("calendar-feed@test.com", "Calendar", "Feed");
        member = stationMemberRepo.create(station.id(), account.id());
        youth = eventCategoryRepo.create(station.id(), "Jugend", 0, null).id();
        exercise = eventCategoryRepo.create(station.id(), "Übung", 1, null).id();
        foreign = eventCategoryRepo.create(otherStation.id(), "Fremd", 0, null).id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aCreatedFeedCarriesItsNameLevelAndHiddenCategories() {
        var feed = service.create(
                station.id(), "  Leinwand  ", StationFeedLevel.MEMBERS, List.of(exercise, youth, youth), member.id());

        assertEquals("Leinwand", feed.name());
        assertEquals(StationFeedLevel.MEMBERS, feed.level());
        assertEquals(List.of(Math.min(youth, exercise), Math.max(youth, exercise)), feed.hiddenCategoryIds());
        assertEquals(member.id(), feed.createdBy());
        assertNull(feed.polledAt());
        assertTrue(service.findByToken(feed.token()).isPresent());
        assertTrue(service.list(station.id()).stream().anyMatch(listed -> listed.id() == feed.id()));
    }

    @Test
    void updatingChangesNameLevelAndFilterButKeepsTheAddress() {
        var feed = service.create(station.id(), "Führung", StationFeedLevel.ALL, List.of(youth), member.id());

        var changed = service.update(feed.id(), station.id(), "Führungsstab", StationFeedLevel.PUBLIC, List.of());

        assertEquals("Führungsstab", changed.name());
        assertEquals(StationFeedLevel.PUBLIC, changed.level());
        assertEquals(List.of(), changed.hiddenCategoryIds());
        assertEquals(feed.token(), changed.token());
    }

    @Test
    void renewingTheAddressEndsTheOldOne() {
        var feed = service.create(station.id(), "Erneuert", StationFeedLevel.PUBLIC, List.of(), member.id());

        var renewed = service.regenerate(feed.id(), station.id());

        assertNotEquals(feed.token(), renewed.token());
        assertTrue(service.findByToken(feed.token()).isEmpty());
        assertTrue(service.findByToken(renewed.token()).isPresent());
    }

    @Test
    void aFeedOfAnotherStationIsNotHere() {
        var feed = service.create(station.id(), "Fremdzugriff", StationFeedLevel.PUBLIC, List.of(), member.id());

        assertRefused(
                FeedRefusal.FEED_STATION_FEED_NOT_HERE,
                () -> service.update(feed.id(), otherStation.id(), "x", StationFeedLevel.ALL, List.of()));
        assertRefused(FeedRefusal.FEED_STATION_FEED_NOT_HERE, () -> service.regenerate(feed.id(), otherStation.id()));
        assertRefused(FeedRefusal.FEED_STATION_FEED_NOT_HERE, () -> service.delete(feed.id(), otherStation.id()));
    }

    @Test
    void aFetchIsRecorded() {
        var feed = service.create(station.id(), "Abgerufen", StationFeedLevel.PUBLIC, List.of(), member.id());

        service.recordPoll(feed.id());

        assertNotNull(service.findByToken(feed.token()).orElseThrow().polledAt());
    }

    @Test
    void aForeignFeedIsNotHereEvenWithAForeignCategory() {
        var feed = service.create(station.id(), "Doppelt fremd", StationFeedLevel.PUBLIC, List.of(), member.id());

        assertRefused(
                FeedRefusal.FEED_STATION_FEED_NOT_HERE,
                () -> service.update(feed.id(), otherStation.id(), "x", StationFeedLevel.ALL, List.of(youth)));
    }

    @Test
    void deletingEndsTheAddress() {
        var feed = service.create(station.id(), "Weg", StationFeedLevel.PUBLIC, List.of(), member.id());

        service.delete(feed.id(), station.id());

        assertTrue(service.findByToken(feed.token()).isEmpty());
        assertRefused(FeedRefusal.FEED_STATION_FEED_NOT_HERE, () -> service.delete(feed.id(), station.id()));
    }

    @Test
    void nameAndLevelAreRequired() {
        assertRefused(
                FeedRefusal.FEED_STATION_FEED_NEEDS_A_NAME,
                () -> service.create(station.id(), "   ", StationFeedLevel.PUBLIC, List.of(), member.id()));
        assertRefused(
                FeedRefusal.FEED_STATION_FEED_NAME_TOO_LONG,
                () -> service.create(station.id(), "x".repeat(101), StationFeedLevel.PUBLIC, List.of(), member.id()));
        assertRefused(
                FeedRefusal.FEED_STATION_FEED_NEEDS_A_LEVEL,
                () -> service.create(station.id(), "Ohne Stufe", null, List.of(), member.id()));
    }

    @Test
    void aCategoryOfAnotherStationIsRefused() {
        assertRefused(
                FeedRefusal.FEED_CATEGORY_NOT_HERE,
                () -> service.create(station.id(), "Fremd", StationFeedLevel.ALL, List.of(foreign), member.id()));
        assertRefused(
                FeedRefusal.FEED_CATEGORY_NOT_HERE,
                () -> personalFilter.setHiddenCategories(member.id(), station.id(), List.of(youth, foreign)));
    }

    @Test
    void aDeletedCategoryLeavesEveryFilter() {
        int temporary =
                eventCategoryRepo.create(station.id(), "Kurzzeitig", 5, null).id();
        var feed = service.create(station.id(), "Mit Kategorie", StationFeedLevel.ALL, List.of(temporary), member.id());
        personalFilter.setHiddenCategories(member.id(), station.id(), List.of(temporary, youth));

        eventCategoryRepo.delete(temporary);

        assertEquals(
                List.of(),
                service.list(station.id()).stream()
                        .filter(listed -> listed.id() == feed.id())
                        .findFirst()
                        .orElseThrow()
                        .hiddenCategoryIds());
        assertEquals(List.of(youth), personalFilter.hiddenCategories(member.id()));
        personalFilter.setHiddenCategories(member.id(), station.id(), List.of());
    }

    @Test
    void thePersonalFilterIsReplacedAsAWhole() {
        personalFilter.setHiddenCategories(member.id(), station.id(), List.of(youth));
        var now = personalFilter.setHiddenCategories(member.id(), station.id(), List.of(exercise));

        assertEquals(List.of(exercise), now);
        assertEquals(List.of(exercise), personalFilter.hiddenCategories(member.id()));
        personalFilter.setHiddenCategories(member.id(), station.id(), List.of());
        assertEquals(List.of(), personalFilter.hiddenCategories(member.id()));
    }
}
