/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.service;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import io.javalin.http.Context;
import jakarta.inject.Singleton;

/**
 * Maps a request context to the appropriate {@link AuthBucket}:
 * federation if a federation session or {@code /remote/} path is present; authenticated
 * if a real user session is attached; unauthenticated otherwise.
 */
@Singleton
public class AuthBucketClassifier {

    /**
     * Returns the bucket the given request should be charged against.
     */
    public AuthBucket classify(Context ctx) {
        FederationSession fed = ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION);
        if (fed != null || ctx.path().contains("/remote/")) {
            return AuthBucket.FEDERATION;
        }
        UserSession user = ctx.attribute(ApiServer.ATTR_SESSION);
        if (user != null && user.stationId() != null) {
            return AuthBucket.AUTHENTICATED;
        }
        return AuthBucket.UNAUTHENTICATED;
    }
}
