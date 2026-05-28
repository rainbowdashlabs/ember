/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import io.javalin.http.Context;

import java.util.UUID;

/**
 * Represents an authenticated federation partner session, resolved from signed request headers.
 * Stored as a context attribute on requests to {@code /remote/} endpoints.
 *
 * @param partner           the verified federation partner record
 * @param partnerStationUid the UUID of the remote station that sent the request
 */
public record FederationSession(FederationPartner partner, UUID partnerStationUid) {

    public static final String ATTR_FEDERATION_SESSION = "federationSession";

    /**
     * Extracts the federation session from a Javalin request context.
     *
     * @param ctx the Javalin context
     * @return the federation session stored as a context attribute
     */
    public static FederationSession from(Context ctx) {
        return ctx.attribute(ATTR_FEDERATION_SESSION);
    }

    public int partnerId() {
        return partner.id();
    }

    public int stationId() {
        return partner.stationId();
    }
}
