/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.event.events.NewsCreated;
import dev.chojo.ember.feature.cluster.service.ClusterAutoShareService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * What a cluster writes goes to its stations, and what a station writes stays where it was written.
 *
 * <p>A cluster's news list and calendar are a station's, kept on the station it owns and filled in with the
 * ordinary station screens. Those screens know nothing about clusters, so the step that makes the entry the
 * cluster's rather than one station's happens here. The distinction being checked is exactly that: the same
 * call, made at two different stations, means two different things.
 */
class ClusterShareHandlerTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static NewsFederationService newsFederation;
    private static EventFederationService eventFederation;
    private static ClusterNewsShareHandler newsHandler;
    private static ClusterEventShareHandler eventHandler;

    @BeforeAll
    static void setup() {
        var autoShare = new ClusterAutoShareService(clusterRepo, new FederationRepository());
        newsFederation = mock(NewsFederationService.class);
        eventFederation = mock(EventFederationService.class);
        newsHandler = new ClusterNewsShareHandler(autoShare, () -> newsFederation);
        eventHandler = new ClusterEventShareHandler(autoShare, () -> eventFederation);
    }

    private int clusterHomeStation() {
        return clusterService
                .create("Kreisverband Verteilung " + NAMES.incrementAndGet(), null)
                .homeStationId();
    }

    private int ordinaryStation() {
        return stationRepo.create("Wache Verteilung " + NAMES.incrementAndGet()).id();
    }

    @Test
    void newsWrittenForAClusterGoesToItsStations() {
        reset(newsFederation);

        newsHandler.handle(new NewsCreated(clusterHomeStation(), 4711, "Rundschreiben", "Wer auch immer", null));

        verify(newsFederation)
                .setShare(eq(4711), eq(ShareScope.ALL_PARTNERS), eq(NewsVisibilityRole.MEMBER), eq(List.of()));
    }

    @Test
    void newsWrittenAtAStationStaysThere() {
        reset(newsFederation);

        newsHandler.handle(new NewsCreated(ordinaryStation(), 4712, "Aushang", "Wer auch immer", null));

        verify(newsFederation, never()).setShare(anyInt(), any(), any(), any());
    }

    @Test
    void anAppointmentMadeForAClusterGoesToItsStations() {
        reset(eventFederation);

        eventHandler.handle(new EventCreated(clusterHomeStation(), eventWithId(815)));

        verify(eventFederation).setShare(eq(815), eq(ShareScope.ALL_PARTNERS), eq(List.of()));
    }

    @Test
    void anAppointmentMadeAtAStationStaysThere() {
        reset(eventFederation);

        eventHandler.handle(new EventCreated(ordinaryStation(), eventWithId(816)));

        verify(eventFederation, never()).setShare(anyInt(), any(), any());
    }

    private StationEvent eventWithId(int id) {
        return new StationEvent(
                id,
                0,
                "Übung",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                null,
                null,
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.OR,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null);
    }
}
