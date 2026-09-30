/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.service.CrossStationDashboardService;
import dev.chojo.ember.feature.account.service.SessionInfoService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
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
    private final CrossStationDashboardService dashboardService;

    @Inject
    public SessionRoutes(
            SessionInfoService sessionInfoService,
            StationMemberService memberService,
            StationService stationService,
            CrossStationDashboardService dashboardService) {
        this.sessionInfoService = sessionInfoService;
        this.memberService = memberService;
        this.stationService = stationService;
        this.dashboardService = dashboardService;
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

    /**
     * A membership whose station is gone is left out rather than listed without one. The caller
     * picks an entry and sends its identifier back as the station it is acting for, and an entry
     * with nothing to send names no station the server can find, which is answered as a bad
     * request on every call the reader makes afterwards.
     */
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
                .flatMap(m -> stationService.findById(m.stationId()).stream()
                        .map(station -> new StationMembership(m.id(), station.uid(), station.name())))
                .toList();
        ctx.json(result);
    }

    private void getCrossStationDashboard(Context ctx) {
        ctx.json(dashboardService.dashboard(UserSession.from(ctx).accountId()));
    }

    /**
     * A station membership entry listing which stations the user belongs to.
     *
     * @param memberId    the member identifier
     * @param stationId   the station identifier
     * @param stationName the station name
     */
    public record StationMembership(int memberId, UUID stationId, String stationName) {}
}
