/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.service.SessionInfoService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.system.service.RequirementsService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read routes describing the current session: the aggregated session info, the stations the
 * account belongs to, and the cross-station dashboard.
 */
@Singleton
public class SessionRoutes implements Routes {
    private final SessionInfoService sessionInfoService;
    private final StationMemberService memberService;
    private final StationService stationService;
    private final NotificationService notificationService;
    private final RequirementsService requirementsService;
    private final AccessManager accessManager;

    @Inject
    public SessionRoutes(
            SessionInfoService sessionInfoService,
            StationMemberService memberService,
            StationService stationService,
            NotificationService notificationService,
            RequirementsService requirementsService,
            AccessManager accessManager) {
        this.sessionInfoService = sessionInfoService;
        this.memberService = memberService;
        this.stationService = stationService;
        this.notificationService = notificationService;
        this.requirementsService = requirementsService;
        this.accessManager = accessManager;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/session", this::getSessionInfo, StationPermission.LOGIN);
        routes.get(prefix + "/session/stations", this::getStations, StationPermission.LOGIN);
        routes.get(
                prefix + "/session/cross-station-dashboard", this::getCrossStationDashboard, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/session",
            methods = HttpMethod.GET,
            summary = "Get current session info",
            description = "Returns account info, roles for the current station, managed members, and groups.",
            tags = {"Session"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = SessionInfoService.SessionInfo.class)))
    private void getSessionInfo(Context ctx) {
        ctx.json(sessionInfoService.describe(UserSession.from(ctx)));
    }

    @OpenApi(
            path = "/api/v1/session/stations",
            methods = HttpMethod.GET,
            summary = "List stations the current user is a member of",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationMembership[].class)))
    private void getStations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        List<StationMember> memberships = memberService.findBelongingByAccount(session.accountId());
        List<StationMembership> result = memberships.stream()
                .map(m -> {
                    var station = stationService.findById(m.stationId()).orElse(null);
                    String stationName = station != null ? station.name() : null;
                    UUID stationUid = station != null ? station.uid() : null;
                    return new StationMembership(m.id(), stationUid, stationName);
                })
                .toList();
        ctx.json(result);
    }

    private void getCrossStationDashboard(Context ctx) {
        UserSession session = UserSession.from(ctx);
        List<StationMember> memberships = memberService.findBelongingByAccount(session.accountId());

        var stationSummaries = new ArrayList<CrossStationSummary>();
        var allNotifications = new ArrayList<CrossStationNotification>();

        for (StationMember member : memberships) {
            if (member.former()) continue;
            var station = stationService.findById(member.stationId()).orElse(null);
            if (station == null) continue;

            int notificationCount = notificationService.countUnacknowledged(member.id());

            var permissions = accessManager.resolveExpandedMemberPermissions(member);
            var roleNames = permissions.stream().map(Enum::name).toList();
            int requirementCount = requirementsService.countPending(member.id(), member.stationId(), roleNames);

            stationSummaries.add(
                    new CrossStationSummary(station.uid(), station.name(), notificationCount, requirementCount));

            for (Notification n : notificationService.findUnacknowledged(member.id())) {
                allNotifications.add(new CrossStationNotification(
                        station.uid(),
                        station.name(),
                        n.id(),
                        n.type().name(),
                        n.type().localeKey(),
                        n.data().paramsAsMap(),
                        n.data().link() != null
                                ? new CrossStationNotificationLink(
                                        n.data().link().route(), n.data().link().routeParams())
                                : null,
                        n.createdAt()));
            }
        }

        allNotifications.sort(
                Comparator.comparing(CrossStationNotification::createdAt).reversed());
        var limited = allNotifications.size() > 20 ? allNotifications.subList(0, 20) : allNotifications;

        ctx.json(new CrossStationDashboard(stationSummaries, limited));
    }

    public record CrossStationDashboard(
            List<CrossStationSummary> stations, List<CrossStationNotification> recentNotifications) {}

    public record CrossStationSummary(UUID stationId, String stationName, int notifications, int requirements) {}

    public record CrossStationNotification(
            UUID stationId,
            String stationName,
            int id,
            String type,
            String localeKey,
            Map<String, String> params,
            CrossStationNotificationLink link,
            Instant createdAt) {}

    public record CrossStationNotificationLink(String route, Map<String, Object> routeParams) {}

    /**
     * A station membership entry listing which stations the user belongs to.
     *
     * @param memberId    the member identifier
     * @param stationId   the station identifier
     * @param stationName the station name
     */
    public record StationMembership(int memberId, UUID stationId, String stationName) {}
}
