/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.service.NotificationInbox;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.system.service.RequirementsService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dashboard sums up every current membership, leaves out former ones and gone stations, and
 * lists only the newest notifications across all of them.
 */
class CrossStationDashboardServiceTest {
    private static final int ACCOUNT = 3;
    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

    private final StationMemberService members = mock(StationMemberService.class);
    private final StationService stations = mock(StationService.class);
    private static final Recipient MEMBER = Recipient.stationMember(21);

    private final NotificationInbox notifications = mock(NotificationInbox.class);
    private final RequirementsService requirements = mock(RequirementsService.class);
    private final AccessManager access = mock(AccessManager.class);
    private final CrossStationDashboardService service =
            new CrossStationDashboardService(members, stations, notifications, requirements, access);

    @Test
    void currentMembershipsOfExistingStationsAreSummedUp() {
        var current = member(21, 1, false);
        var former = member(22, 2, true);
        var orphaned = member(23, 3, false);
        when(members.findBelongingByAccount(ACCOUNT)).thenReturn(List.of(current, former, orphaned));
        var station = station("Nord");
        when(stations.findById(1)).thenReturn(Optional.of(station));
        when(stations.findById(3)).thenReturn(Optional.empty());
        when(access.resolveExpandedMemberPermissions(current)).thenReturn(Set.of(StationPermission.LOGIN));
        when(requirements.countPending(21, 1, List.of("LOGIN"))).thenReturn(2);
        when(notifications.countUnread(MEMBER)).thenReturn(1);
        var unread = notification(5, NOW, new NotificationLink("news", Map.of("id", 9), Map.of()));
        when(notifications.unread(MEMBER)).thenReturn(List.of(unread));

        var dashboard = service.dashboard(ACCOUNT);

        assertEquals(1, dashboard.stations().size());
        var summary = dashboard.stations().getFirst();
        assertEquals(station.uid(), summary.stationId());
        assertEquals(1, summary.notifications());
        assertEquals(2, summary.requirements());
        var shown = dashboard.recentNotifications().getFirst();
        assertEquals("Nord", shown.stationName());
        assertEquals(NotificationType.NEW_NEWS.localeKey(), shown.localeKey());
        assertEquals("news", shown.link().route());
    }

    @Test
    void onlyTheTwentyNewestNotificationsAreListedNewestFirst() {
        var current = member(21, 1, false);
        when(members.findBelongingByAccount(ACCOUNT)).thenReturn(List.of(current));
        var station = station("Nord");
        when(stations.findById(1)).thenReturn(Optional.of(station));
        when(access.resolveExpandedMemberPermissions(current)).thenReturn(Set.of());
        var unread = IntStream.range(0, 25)
                .mapToObj(i -> notification(i, NOW.plusSeconds(i), null))
                .toList();
        when(notifications.unread(MEMBER)).thenReturn(unread);

        var recent = service.dashboard(ACCOUNT).recentNotifications();

        assertEquals(20, recent.size());
        assertEquals(24, recent.getFirst().id());
        assertEquals(5, recent.getLast().id());
        assertNull(recent.getFirst().link());
    }

    private static StationMember member(int id, int stationId, boolean former) {
        var member = mock(StationMember.class);
        when(member.id()).thenReturn(id);
        when(member.stationId()).thenReturn(stationId);
        when(member.former()).thenReturn(former);
        return member;
    }

    private static Station station(String name) {
        var station = mock(Station.class);
        when(station.uid()).thenReturn(UUID.randomUUID());
        when(station.name()).thenReturn(name);
        return station;
    }

    private static Notification notification(int id, Instant createdAt, NotificationLink link) {
        var data = mock(NotificationData.class);
        when(data.paramsAsMap()).thenReturn(Map.of());
        when(data.link()).thenReturn(link);
        return new Notification(id, 21, null, NotificationType.NEW_NEWS, data, createdAt, null);
    }
}
