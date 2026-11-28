/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListRateLimiter;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListService;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recording how far a reader looked at a waiting list, over HTTP: whoever may read the list records
 * it for themselves, and a list of another station stays out of reach.
 */
class WaitingListSeenRoutesTest {
    private static final String SEEN = PREFIX + "/waiting-lists/5/seen";

    private WaitingListService lists;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        lists = mock(WaitingListService.class);
        var own = mock(WaitingList.class);
        when(own.stationId()).thenReturn(3);
        when(lists.findById(5)).thenReturn(Optional.of(own));
        var foreign = mock(WaitingList.class);
        when(foreign.stationId()).thenReturn(4);
        when(lists.findById(6)).thenReturn(Optional.of(foreign));

        harness = RouteHarness.serving(new WaitingListRoutes(
                lists,
                mock(PublicWaitingListService.class),
                mock(ConsentService.class),
                mock(PublicWaitingListRateLimiter.class),
                mock(EventCrudService.class),
                mock(EventRestrictionService.class),
                mock(GuardianPolicy.class)));
    }

    @Test
    void aReaderRecordsHowFarTheyLookedForThemselves() {
        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.member(3, StationPermission.WAITLIST_READ));
            assertEquals(
                    204, client.post(SEEN, body("{\"upToEntryId\": 9}"), reader).code());
        });

        verify(lists).markSeen(5, TestSessions.MEMBER_ID, 9);
    }

    @Test
    void somebodyWhoMayNotReadTheListRecordsNothing() {
        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(3, StationPermission.LOGIN));
            assertEquals(
                    403, client.post(SEEN, body("{\"upToEntryId\": 9}"), member).code());
        });

        verify(lists, never()).markSeen(anyInt(), anyInt(), anyInt());
    }

    @Test
    void aListOfAnotherStationStaysOutOfReach() {
        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.member(3, StationPermission.WAITLIST_READ));
            assertEquals(
                    404,
                    client.post(PREFIX + "/waiting-lists/6/seen", body("{\"upToEntryId\": 9}"), reader)
                            .code());
        });

        verify(lists, never()).markSeen(anyInt(), anyInt(), anyInt());
    }
}
