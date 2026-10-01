/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.service.NotificationInbox;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.system.service.RequirementsService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What waits for an account across every station it is a current member of: per station the
 * unread notifications and open requirements, and the newest notifications of all of them in one
 * list. Former memberships and stations that are gone are left out.
 */
@Singleton
public class CrossStationDashboardService {
    private static final int RECENT_NOTIFICATIONS = 20;

    private final StationMemberService memberService;
    private final StationService stationService;
    private final NotificationInbox inbox;
    private final RequirementsService requirementsService;
    private final AccessManager accessManager;

    @Inject
    public CrossStationDashboardService(
            StationMemberService memberService,
            StationService stationService,
            NotificationInbox inbox,
            RequirementsService requirementsService,
            AccessManager accessManager) {
        this.memberService = memberService;
        this.stationService = stationService;
        this.inbox = inbox;
        this.requirementsService = requirementsService;
        this.accessManager = accessManager;
    }

    public CrossStationDashboard dashboard(int accountId) {
        var summaries = new ArrayList<CrossStationSummary>();
        var notifications = new ArrayList<CrossStationNotification>();
        for (StationMember member : memberService.findBelongingByAccount(accountId)) {
            if (member.former()) continue;
            stationService.findById(member.stationId()).ifPresent(station -> {
                summaries.add(summaryOf(member, station));
                inbox.unread(Recipient.stationMember(member.id())).stream()
                        .map(notification -> CrossStationNotification.of(station, notification))
                        .forEach(notifications::add);
            });
        }
        var recent = notifications.stream()
                .sorted(Comparator.comparing(CrossStationNotification::createdAt)
                        .reversed())
                .limit(RECENT_NOTIFICATIONS)
                .toList();
        return new CrossStationDashboard(summaries, recent);
    }

    private CrossStationSummary summaryOf(StationMember member, Station station) {
        var roleNames = accessManager.resolveExpandedMemberPermissions(member).stream()
                .map(Enum::name)
                .toList();
        return new CrossStationSummary(
                station.uid(),
                station.name(),
                inbox.countUnread(Recipient.stationMember(member.id())),
                requirementsService.countPending(member.id(), member.stationId(), roleNames));
    }

    public record CrossStationDashboard(
            List<CrossStationSummary> stations, List<CrossStationNotification> recentNotifications) {}

    public record CrossStationSummary(UUID stationId, String stationName, int notifications, int requirements) {}

    public record CrossStationNotification(
            UUID stationId,
            String stationName,
            int id,
            NotificationType type,
            String localeKey,
            Map<String, String> params,
            @Nullable CrossStationNotificationLink link,
            Instant createdAt) {

        static CrossStationNotification of(Station station, Notification notification) {
            var link = notification.data().link();
            return new CrossStationNotification(
                    station.uid(),
                    station.name(),
                    notification.id(),
                    notification.type(),
                    notification.type().localeKey(),
                    notification.data().paramsAsMap(),
                    link == null ? null : new CrossStationNotificationLink(link.route(), link.routeParams()),
                    notification.createdAt());
        }
    }

    public record CrossStationNotificationLink(
            String route, @Nullable Map<String, Object> routeParams) {}
}
