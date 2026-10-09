/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.service.DemoService;
import io.javalin.http.Context;
import io.javalin.security.RouteRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The gate takes along what a public request carries, refuses a request without a live session, a
 * header naming nothing and a session lacking the route's permission, and asks for a fresh second
 * factor where the route names a step-up.
 */
class AccessGateTest {
    private static final String TOKEN = "live";
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID CLUSTER_UID = UUID.fromString("00000000-0000-0000-0001-000000000003");

    private final AccessManager accessManager = mock(AccessManager.class);
    private final SessionGate sessionGate = mock(SessionGate.class);
    private final StepUpGuard stepUpGuard = mock(StepUpGuard.class);
    private final StationRepository stations = mock(StationRepository.class);
    private final ClusterRepository clusters = mock(ClusterRepository.class);
    private final Demo demo = mock(Demo.class);
    private final DemoService demoService = mock(DemoService.class);
    private final Context ctx = mock(Context.class);
    private AccessGate gate;

    @BeforeEach
    void setUp() {
        gate = new AccessGate(accessManager, sessionGate, stepUpGuard, stations, clusters, demo, demoService);
    }

    private void route(RouteRole... roles) {
        when(ctx.routeRoles()).thenReturn(Set.of(roles));
    }

    private void signedInAs(UserSession session) {
        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn(TOKEN);
        when(sessionGate.admit(eq(ctx), eq(TOKEN), any(), any())).thenReturn(Optional.of(session));
    }

    @Test
    void aPublicRouteTakesAlongTheSessionAndThePartnerItCarries() {
        route();
        Station station = mock(Station.class);
        FederationSession partner = mock(FederationSession.class);
        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn(TOKEN);
        when(ctx.header("X-Station-Id")).thenReturn(STATION_UID.toString());
        when(ctx.header("X-Federation-Station-Id")).thenReturn("partner");
        when(stations.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(accessManager.resolveFederationSession(ctx)).thenReturn(Optional.of(partner));

        gate.handle(ctx);

        verify(sessionGate).attach(ctx, TOKEN, station);
        verify(ctx).attribute(FederationSession.ATTR_FEDERATION_SESSION, partner);
    }

    @Test
    void aPublicRouteIgnoresAStationHeaderThatIsNoId() {
        route();
        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn(TOKEN);
        when(ctx.header("X-Station-Id")).thenReturn("not-an-id");

        gate.handle(ctx);

        verify(sessionGate).attach(eq(ctx), eq(TOKEN), isNull());
    }

    @Test
    void aPublicRouteWithoutACookieAttachesNothing() {
        route();

        gate.handle(ctx);

        verify(sessionGate, never()).attach(any(), anyString(), any());
    }

    @Test
    void aRouteWithRolesRefusesARequestThatIsNotSignedIn() {
        route(StationPermission.LOGIN);

        var refused = assertThrows(RefusalResponse.class, () -> gate.handle(ctx));

        assertEquals(GeneralRefusal.ROUTE_NEEDS_SIGN_IN, refused.refusal());
        verify(sessionGate).forgetHalfSession(ctx);
    }

    @Test
    void aSessionThatIsNoLongerLiveIsRefused() {
        route(StationPermission.LOGIN);
        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn(TOKEN);
        when(sessionGate.admit(ctx, TOKEN, null, null)).thenReturn(Optional.empty());

        var refused = assertThrows(RefusalResponse.class, () -> gate.handle(ctx));

        assertEquals(GeneralRefusal.SIGN_IN_SESSION_NOT_VALID, refused.refusal());
    }

    @Test
    void aStationHeaderNamingNothingIsABadRequestNotASignOut() {
        route(StationPermission.LOGIN);
        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn(TOKEN);
        when(ctx.header("X-Station-Id")).thenReturn(STATION_UID.toString());
        when(stations.findByUid(STATION_UID)).thenReturn(Optional.empty());

        var refused = assertThrows(RefusalResponse.class, () -> gate.handle(ctx));

        assertEquals(GeneralRefusal.REQUESTED_STATION_NOT_HERE, refused.refusal());
    }

    @Test
    void aStationHeaderThatIsNoIdIsABadRequest() {
        route(StationPermission.LOGIN);
        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn(TOKEN);
        when(ctx.header("X-Station-Id")).thenReturn("not-an-id");

        var refused = assertThrows(RefusalResponse.class, () -> gate.handle(ctx));

        assertEquals(GeneralRefusal.REQUESTED_STATION_NOT_AN_IDENTITY, refused.refusal());
    }

    @Test
    void aClusterHeaderNamingNothingIsABadRequest() {
        route(StationPermission.LOGIN);
        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn(TOKEN);
        when(ctx.header("X-Cluster-Id")).thenReturn(CLUSTER_UID.toString());
        when(clusters.findByUid(CLUSTER_UID)).thenReturn(Optional.empty());

        var refused = assertThrows(RefusalResponse.class, () -> gate.handle(ctx));

        assertEquals(GeneralRefusal.REQUESTED_CLUSTER_NOT_HERE, refused.refusal());
    }

    @Test
    void aClusterHeaderThatIsNoIdIsABadRequest() {
        route(StationPermission.LOGIN);
        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn(TOKEN);
        when(ctx.header("X-Cluster-Id")).thenReturn("not-an-id");

        var refused = assertThrows(RefusalResponse.class, () -> gate.handle(ctx));

        assertEquals(GeneralRefusal.REQUESTED_CLUSTER_NOT_AN_IDENTITY, refused.refusal());
    }

    @Test
    void theStationAndClusterTheHeadersNameReachTheSessionGate() {
        route(StationPermission.LOGIN);
        Station station = mock(Station.class);
        Cluster cluster = mock(Cluster.class);
        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn(TOKEN);
        when(ctx.header("X-Station-Id")).thenReturn(STATION_UID.toString());
        when(ctx.header("X-Cluster-Id")).thenReturn(CLUSTER_UID.toString());
        when(stations.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(clusters.findByUid(CLUSTER_UID)).thenReturn(Optional.of(cluster));
        UserSession session = TestSessions.member(5);
        when(sessionGate.admit(ctx, TOKEN, station, cluster)).thenReturn(Optional.of(session));

        gate.handle(ctx);

        verify(ctx).attribute(ApiServer.ATTR_SESSION, session);
    }

    @Test
    void aSignedInSessionIsEnoughForALoginRoute() {
        route(StationPermission.LOGIN);
        UserSession session = TestSessions.member(5);
        signedInAs(session);

        gate.handle(ctx);

        verify(ctx).attribute(ApiServer.ATTR_SESSION, session);
        verify(demoService, never()).recordActivity();
    }

    @Test
    void aPublicDemoRecordsActivity() {
        route(StationPermission.LOGIN);
        when(demo.enabled()).thenReturn(true);
        signedInAs(TestSessions.member(5));

        gate.handle(ctx);

        verify(demoService).recordActivity();
    }

    @Test
    void anyOneOfThePermissionsTheRouteNamesAdmits() {
        route(StationPermission.ATTENDANCE_EDIT, InstancePermission.ADMINISTRATOR);
        signedInAs(TestSessions.administrator());

        gate.handle(ctx);
    }

    @Test
    void aMissingStationPermissionIsRefusedNamingTheStationPermissionsHeld() {
        route(StationPermission.ATTENDANCE_EDIT);
        UserSession session = TestSessions.member(5, StationPermission.LOGIN);
        signedInAs(session);

        var refused = assertThrows(RefusalResponse.class, () -> gate.handle(ctx));

        assertEquals(GeneralRefusal.ROUTE_PERMISSION_MISSING, refused.refusal());
        verify(ctx).header("X-User-Permissions", session.permissions().toString());
    }

    @Test
    void aMissingClusterPermissionIsRefusedNamingTheClusterPermissionsHeld() {
        ClusterPermission wanted = ClusterPermission.values()[0];
        route(wanted);
        UserSession session = TestSessions.clusterMember(3);
        signedInAs(session);

        assertThrows(RefusalResponse.class, () -> gate.handle(ctx));

        verify(ctx).header("X-Required-Permissions", Set.of(wanted).toString());
        verify(ctx).header("X-User-Permissions", session.clusterPermissions().toString());
    }

    @Test
    void aStepUpRouteAsksForAFreshSecondFactorOnceThePermissionHolds() {
        route(InstancePermission.ADMINISTRATOR, StepUpCategory.INSTANCE_CONFIG);
        UserSession session = TestSessions.administrator();
        signedInAs(session);

        gate.handle(ctx);

        verify(stepUpGuard).require(session, StepUpCategory.INSTANCE_CONFIG);
    }

    @Test
    void aStepUpIsNotAskedOfSomebodyLackingThePermission() {
        route(InstancePermission.ADMINISTRATOR, StepUpCategory.INSTANCE_CONFIG);
        signedInAs(TestSessions.member(5, StationPermission.LOGIN));

        assertThrows(RefusalResponse.class, () -> gate.handle(ctx));

        verify(stepUpGuard, never()).require(any(), any());
    }

    @Test
    void aRouteNamingOnlyAStepUpAsksForItOfAnySignedInSession() {
        route(StepUpCategory.ACCOUNT_SECURITY);
        UserSession session = TestSessions.member(5);
        signedInAs(session);

        gate.handle(ctx);

        verify(stepUpGuard).require(session, StepUpCategory.ACCOUNT_SECURITY);
    }
}
