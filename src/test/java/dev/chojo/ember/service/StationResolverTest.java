/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.traffic.service.StationResolver;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StationResolverTest {

    private final StationResolver resolver = new StationResolver();

    @Test
    void explicitContextAttributeWinsOverSessions() {
        var ctx = mock(Context.class);
        when(ctx.attribute(StationResolver.ATTR_TRAFFIC_STATION_ID)).thenReturn(42);
        assertEquals(42, resolver.resolve(ctx).orElseThrow());
    }

    @Test
    void resolvesFromUserSessionWhenAttached() {
        var session = userSession(7);
        var ctx = mock(Context.class);
        when(ctx.attribute(StationResolver.ATTR_TRAFFIC_STATION_ID)).thenReturn(null);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(session);
        assertEquals(7, resolver.resolve(ctx).orElseThrow());
    }

    @Test
    void resolvesFromFederationSessionWhenNoUserSession() {
        var ctx = mock(Context.class);
        when(ctx.attribute(StationResolver.ATTR_TRAFFIC_STATION_ID)).thenReturn(null);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(null);
        when(ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION)).thenReturn(federationSession(9));
        assertEquals(9, resolver.resolve(ctx).orElseThrow());
    }

    @Test
    void userSessionWithoutStationIdFallsThroughToFederation() {
        var ctx = mock(Context.class);
        when(ctx.attribute(StationResolver.ATTR_TRAFFIC_STATION_ID)).thenReturn(null);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(userSession(null));
        when(ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION)).thenReturn(federationSession(11));
        assertEquals(11, resolver.resolve(ctx).orElseThrow());
    }

    @Test
    void noAttachmentsReturnEmpty() {
        var ctx = mock(Context.class);
        when(ctx.attribute(StationResolver.ATTR_TRAFFIC_STATION_ID)).thenReturn(null);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(null);
        when(ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION)).thenReturn(null);
        assertTrue(resolver.resolve(ctx).isEmpty());
    }

    private static UserSession userSession(Integer stationId) {
        return new UserSession(
                null, 0, stationId, stationId == null ? null : UUID.randomUUID(), null, Set.of(), Set.of(), null);
    }

    private static FederationSession federationSession(int stationId) {
        var partner = new FederationPartner(
                1,
                stationId,
                UUID.randomUUID(),
                null,
                "publicKey",
                "partnerKey",
                FederationPartner.FederationStatus.ACTIVE,
                "1.0",
                Instant.now(),
                Instant.now(),
                "https://example");
        return new FederationSession(partner, UUID.randomUUID());
    }
}
