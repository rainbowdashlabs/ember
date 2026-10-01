/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.FederationSession;
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
import io.javalin.http.Handler;
import io.javalin.security.RouteRole;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The before-matched handler that enforces authentication and role-based authorization.
 * Resolves the session from the session cookie, stores it as a context attribute, and checks that
 * the user has at least one of the required route roles.
 *
 * <p>{@link SessionGate} does the session's part: it refuses a change that does not carry the
 * token proving it came from this site's pages, renews a session that has used up half of its
 * lifetime, and clears a cookie naming no live session on the way to the 401.
 *
 * <p>A request may name a cluster as well as a station, since one person can wear both hats at
 * once. A station or cluster header naming something this instance cannot find is answered as a
 * bad request, not as an unauthorized one. Only the session says whether the sign-in still stands,
 * and every client reads a 401 as the sign-in being over: answering a stale header that way threw
 * away a perfectly good session and put the reader back on the login screen, which is the one
 * thing a wrong header must not be able to do.
 *
 * <p>A route with no roles is public. It still takes the federation session of a signed partner
 * request and the session cookie when there is one, but refuses nobody.
 */
@Singleton
public class AccessGate implements Handler {
    private static final Logger log = LoggerFactory.getLogger(AccessGate.class);

    private final AccessManager accessManager;
    private final SessionGate sessionGate;
    private final StepUpGuard stepUpGuard;
    private final StationRepository stationRepository;
    private final ClusterRepository clusterRepository;
    private final Demo demoConfig;
    private final DemoService demoService;

    @Inject
    public AccessGate(
            AccessManager accessManager,
            SessionGate sessionGate,
            StepUpGuard stepUpGuard,
            StationRepository stationRepository,
            ClusterRepository clusterRepository,
            Demo demoConfig,
            DemoService demoService) {
        this.accessManager = accessManager;
        this.sessionGate = sessionGate;
        this.stepUpGuard = stepUpGuard;
        this.stationRepository = stationRepository;
        this.clusterRepository = clusterRepository;
        this.demoConfig = demoConfig;
        this.demoService = demoService;
    }

    /**
     * Admits or refuses a request on the roles its route declares.
     *
     * <p>Activity of an admitted session is recorded for the idle reset of a public demo.
     *
     * @param ctx the request
     */
    @Override
    public void handle(Context ctx) {
        Set<RouteRole> routeRoles = ctx.routeRoles();

        if (routeRoles.isEmpty()) {
            attachPublicSessions(ctx);
            return;
        }

        String token = SessionCookies.token(ctx).orElse(null);
        if (token == null) {
            throw GeneralRefusal.ROUTE_NEEDS_SIGN_IN.raise();
        }

        Station station = requestedStation(ctx);
        Cluster cluster = requestedCluster(ctx);

        Optional<UserSession> sessionOpt = sessionGate.admit(ctx, token, station, cluster);
        if (sessionOpt.isEmpty()) {
            throw GeneralRefusal.SIGN_IN_SESSION_NOT_VALID.raise();
        }

        UserSession session = sessionOpt.get();
        ctx.attribute(ApiServer.ATTR_SESSION, session);

        if (demoConfig.enabled()) {
            demoService.recordActivity();
        }

        if (routeRoles.size() == 1 && routeRoles.contains(StationPermission.LOGIN)) {
            return;
        }

        checkRoles(ctx, routeRoles, session);
    }

    /**
     * Takes along what a request to a public route carries: the federation session of a signed
     * partner request, and the session cookie.
     */
    private void attachPublicSessions(Context ctx) {
        if (ctx.header("X-Federation-Station-Id") != null) {
            accessManager
                    .resolveFederationSession(ctx)
                    .ifPresent(s -> ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION, s));
        }
        SessionCookies.token(ctx).ifPresent(publicToken -> attachPublicSession(ctx, publicToken));
    }

    /**
     * The station the {@code X-Station-Id} header names, or null when the request names none.
     *
     * @throws RefusalResponse when the header is no id or names no station here
     */
    private @Nullable Station requestedStation(Context ctx) {
        String stationIdHeader = ctx.header("X-Station-Id");
        if (stationIdHeader == null || stationIdHeader.isBlank()) return null;
        try {
            var uid = UUID.fromString(stationIdHeader);
            Station station = stationRepository.findByUid(uid).orElse(null);
            if (station == null) {
                throw GeneralRefusal.REQUESTED_STATION_NOT_HERE.raise();
            }
            return station;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid X-Station-Id header value", e);
            throw GeneralRefusal.REQUESTED_STATION_NOT_AN_IDENTITY.raise();
        }
    }

    /**
     * The cluster the {@code X-Cluster-Id} header names, or null when the request names none.
     *
     * @throws RefusalResponse when the header is no id or names no cluster here
     */
    private @Nullable Cluster requestedCluster(Context ctx) {
        String clusterIdHeader = ctx.header("X-Cluster-Id");
        if (clusterIdHeader == null || clusterIdHeader.isBlank()) return null;
        try {
            return clusterRepository
                    .findByUid(UUID.fromString(clusterIdHeader))
                    .orElseThrow(GeneralRefusal.REQUESTED_CLUSTER_NOT_HERE::raise);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid X-Cluster-Id header value", e);
            throw GeneralRefusal.REQUESTED_CLUSTER_NOT_AN_IDENTITY.raise();
        }
    }

    /**
     * Refuses a session that holds none of the permissions the route asks for, and asks for a fresh
     * second factor where the route names a {@link StepUpCategory}.
     *
     * <p>Permissions are already expanded on the session. A step-up category stands beside the
     * permissions: they still gate access, and the category additionally requires a recent
     * verification. The refusal names the set of permissions the route asked about, cluster ones
     * where the route wants a cluster permission and station ones otherwise, because telling
     * somebody their station permissions when the route wanted a cluster one explains nothing.
     */
    private void checkRoles(Context ctx, Set<RouteRole> routeRoles, UserSession session) {
        boolean permissionRequired = false;
        boolean permissionGranted = false;
        StepUpCategory stepUpCategory = null;
        for (RouteRole required : routeRoles) {
            if (required instanceof StepUpCategory sc) {
                stepUpCategory = sc;
                continue;
            }
            permissionRequired = true;
            if (required instanceof StationPermission sp && session.hasPermission(sp)) {
                permissionGranted = true;
            } else if (required instanceof InstancePermission ip && session.hasInstancePermission(ip)) {
                permissionGranted = true;
            } else if (required instanceof ClusterPermission cp && session.hasClusterPermission(cp)) {
                permissionGranted = true;
            }
        }

        if (permissionRequired && !permissionGranted) {
            Set<? extends RouteRole> held = routeRoles.stream().anyMatch(r -> r instanceof ClusterPermission)
                    ? session.clusterPermissions()
                    : session.permissions();
            ctx.header("X-Required-Permissions", routeRoles.toString());
            ctx.header("X-User-Permissions", held.toString());
            throw GeneralRefusal.ROUTE_PERMISSION_MISSING.raise();
        }

        if (stepUpCategory != null) {
            stepUpGuard.require(session, stepUpCategory);
        }
    }

    /**
     * Attaches the session a request to a public route carries, when it carries a live one. Best
     * effort: a public route works without it, so a station header that names nothing is ignored
     * rather than refused.
     */
    private void attachPublicSession(Context ctx, String token) {
        Station station = null;
        String stationId = ctx.header("X-Station-Id");
        if (stationId != null && !stationId.isBlank()) {
            try {
                station =
                        stationRepository.findByUid(UUID.fromString(stationId)).orElse(null);
            } catch (IllegalArgumentException ignored) {
            }
        }
        sessionGate.attach(ctx, token, station);
    }
}
