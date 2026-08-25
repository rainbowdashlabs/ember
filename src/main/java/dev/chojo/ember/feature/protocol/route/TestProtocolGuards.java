/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Ownership lookups for the test protocol routes.
 *
 * <p>A protocol permission is held in one station, and every endpoint below addresses a row by its
 * id, so without these the permission carries over to every other station's rows. Protocols and
 * runs carry their station; sections and items reach it through the protocol they hang under.
 *
 * <p>Everything answers 404 rather than 403, so an id belonging to another station is
 * indistinguishable from one that was never there.
 */
@Singleton
public class TestProtocolGuards {

    private final TestProtocolService service;

    @Inject
    public TestProtocolGuards(TestProtocolService service) {
        this.service = service;
    }

    /**
     * Loads the protocol the {@code id} path parameter names, in the caller's station.
     */
    public TestProtocol requireProtocol(Context ctx, int protocolId) {
        return RouteSupport.requireOwnedOrNotFound(ctx, protocolId, service::findProtocol, TestProtocol::stationId);
    }

    /**
     * Loads the run the given id names, in the caller's station.
     */
    public TestProtocolRun requireRun(Context ctx, int runId) {
        return RouteSupport.requireOwnedOrNotFound(ctx, runId, service::findRun, TestProtocolRun::stationId);
    }

    /**
     * Asserts the section belongs to a protocol of the caller's station.
     */
    public void requireSection(Context ctx, int sectionId) {
        requireStation(ctx, service.findSectionStation(sectionId).orElseThrow(NotFoundResponse::new));
    }

    /**
     * Asserts the item belongs to a protocol of the caller's station.
     */
    public void requireItem(Context ctx, int itemId) {
        requireStation(ctx, service.findItemStation(itemId).orElseThrow(NotFoundResponse::new));
    }

    private static void requireStation(Context ctx, int stationId) {
        RouteSupport.requireSameStation(UserSession.from(ctx), stationId);
    }
}
