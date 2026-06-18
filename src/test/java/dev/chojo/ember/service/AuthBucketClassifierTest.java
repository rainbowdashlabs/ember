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
import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.service.AuthBucketClassifier;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthBucketClassifierTest {

    private final AuthBucketClassifier classifier = new AuthBucketClassifier();

    @Test
    void federationSessionMapsToFederation() {
        var ctx = mock(Context.class);
        when(ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION)).thenReturn(federationSession());
        when(ctx.path()).thenReturn("/api/v1/anything");
        assertEquals(AuthBucket.FEDERATION, classifier.classify(ctx));
    }

    @Test
    void remotePathMapsToFederationEvenWithoutSession() {
        var ctx = mock(Context.class);
        when(ctx.path()).thenReturn("/api/v1/remote/sync");
        assertEquals(AuthBucket.FEDERATION, classifier.classify(ctx));
    }

    @Test
    void userSessionWithStationMapsToAuthenticated() {
        var ctx = mock(Context.class);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(userSession(3));
        when(ctx.path()).thenReturn("/api/v1/events");
        assertEquals(AuthBucket.AUTHENTICATED, classifier.classify(ctx));
    }

    @Test
    void userSessionWithoutStationMapsToUnauthenticated() {
        var ctx = mock(Context.class);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(userSession(null));
        when(ctx.path()).thenReturn("/api/v1/account");
        assertEquals(AuthBucket.UNAUTHENTICATED, classifier.classify(ctx));
    }

    @Test
    void noSessionsAndPublicPathMapsToUnauthenticated() {
        var ctx = mock(Context.class);
        when(ctx.path()).thenReturn("/api/v1/public/feed/x");
        assertEquals(AuthBucket.UNAUTHENTICATED, classifier.classify(ctx));
    }

    private static UserSession userSession(Integer stationId) {
        return new UserSession(null, stationId, stationId == null ? null : UUID.randomUUID(), null, Set.of(), Set.of());
    }

    private static FederationSession federationSession() {
        var partner = new FederationPartner(
                1,
                1,
                UUID.randomUUID(),
                null,
                "pk",
                "ppk",
                FederationPartner.FederationStatus.ACTIVE,
                "1.0",
                Instant.now(),
                Instant.now(),
                "https://example");
        return new FederationSession(partner, UUID.randomUUID());
    }
}
