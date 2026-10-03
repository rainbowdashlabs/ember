/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.PublicStationInfoService;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which parts of its public page a published card names, each switched on and off on its own, and
 * whether the station takes federation requests.
 */
class DiscoveryStationCardOffersTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static DiscoveryStationProjectionService service;
    private static WaitingListService waitingLists;
    private static NewsService news;

    @TempDir
    static Path configDir;

    private Station station;

    @BeforeAll
    static void setup() {
        waitingLists = mock(WaitingListService.class);
        news = mock(NewsService.class);
        var publicInfo = new PublicStationInfoService(
                stationRepo,
                mock(StationLogoService.class),
                mock(PageService.class),
                waitingLists,
                news,
                mock(FormService.class));
        service = new DiscoveryStationProjectionService(
                stationRepo,
                clusterRepo,
                stationMemberRepo,
                new Conf(configDir),
                mock(StationLogoService.class),
                publicInfo);
    }

    @BeforeEach
    void createStation() {
        station = stationRepo.create("Wache Angebot " + NAMES.incrementAndGet());
        stationRepo.updateDiscoverySettings(station.id(), DiscoveryVisibility.PUBLIC, "Hier", true);
    }

    @AfterEach
    void deleteStation() {
        stationRepo.delete(station.id());
    }

    private DiscoveryStationCard card() {
        return service.publicCards().stream()
                .filter(c -> c.stationUid().equals(station.uid().toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(station.name() + " is on no card"));
    }

    @Test
    void aStationWithNothingPublicNamesNoPartAndNoPage() {
        var card = card();

        assertFalse(card.hasPublicWiki());
        assertFalse(card.hasPublicCalendar());
        assertFalse(card.hasPublicBlog());
        assertFalse(card.waitingListOpen());
        assertNull(card.contactUrl());
        assertTrue(card.acceptsFederation());
    }

    @Test
    void aPublicWikiIsNamedOnlyWhereTheStationShowsItInDiscovery() {
        stationRepo.updatePublicKbMode(station.id(), PublicKbMode.ALLOW_ALL);

        assertTrue(card().hasPublicWiki());

        stationRepo.updateDiscoverySettings(station.id(), DiscoveryVisibility.PUBLIC, "Hier", false);
        var hidden = card();
        assertFalse(hidden.hasPublicWiki());
        assertNotNull(hidden.contactUrl());

        stationRepo.updateDiscoverySettings(station.id(), DiscoveryVisibility.PUBLIC, "Hier", true);
        stationRepo.updatePublicKbMode(station.id(), PublicKbMode.OFF);
        assertFalse(card().hasPublicWiki());
    }

    @Test
    void aPublicCalendarIsNamedWhileItIsSwitchedOn() {
        stationRepo.updatePublicCalendarEnabled(station.id(), true);

        var card = card();
        assertTrue(card.hasPublicCalendar());
        assertFalse(card.hasPublicWiki());
        assertNotNull(card.contactUrl());

        stationRepo.updatePublicCalendarEnabled(station.id(), false);
        assertFalse(card().hasPublicCalendar());
    }

    @Test
    void aBlogIsNamedOnlyWithPublicEntries() {
        stationRepo.updatePublicBlogEnabled(station.id(), true);

        assertFalse(card().hasPublicBlog());

        when(news.withPublicBlogEntries(argThat(ids -> ids.contains(station.id()))))
                .thenReturn(Set.of(station.id()));
        assertTrue(card().hasPublicBlog());

        stationRepo.updatePublicBlogEnabled(station.id(), false);
        assertFalse(card().hasPublicBlog());
    }

    @Test
    void theWaitingListIsOpenOnlyWithAPublicListAndTheSwitchOn() {
        stationRepo.updatePublicWaitlistEnabled(station.id(), true);

        assertFalse(card().waitingListOpen());

        when(waitingLists.withPublicWaitlists(argThat(ids -> ids.contains(station.id()))))
                .thenReturn(Set.of(station.id()));
        var open = card();
        assertTrue(open.waitingListOpen());
        assertNotNull(open.contactUrl());

        stationRepo.updatePublicWaitlistEnabled(station.id(), false);
        assertFalse(card().waitingListOpen());
    }

    @Test
    void aCardStoredBeforeThePublicOffersReadsEachOfThemAsNo() {
        var stored = """
                {"stationUid":"uid-alt","name":"Wache","slogan":null,"logoUrl":null,"country":"DE",
                 "region":null,"city":"Musterstadt","contactUrl":null,"tags":[],"memberCount":"<10",
                 "publishedAt":"2026-01-01T00:00:00Z","addressLine":"Hauptstraße 1","latitude":null,
                 "longitude":null,"clusterUid":null,"clusterName":null,"publicSlug":"wache"}""";

        var card = DiscoveryStationCard.parse(stored);

        assertEquals("Hauptstraße 1", card.addressLine());
        assertFalse(card.hasPublicWiki());
        assertFalse(card.hasPublicCalendar());
        assertFalse(card.hasPublicBlog());
        assertFalse(card.waitingListOpen());
        assertFalse(card.acceptsFederation());
    }
}
