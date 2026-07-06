/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;

import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Shared helpers for route handlers, factoring out the boilerplate that otherwise
 * repeats across nearly every endpoint: reading an integer path parameter and
 * loading a station-scoped entity while enforcing that it belongs to the caller's
 * station.
 */
public final class RouteSupport {
    private RouteSupport() {}

    /**
     * Reads an integer path parameter, throwing the framework's validation error when
     * it is missing or not an integer.
     */
    public static int pathInt(Context ctx, String name) {
        return ctx.pathParamAsClass(name, Integer.class).get();
    }

    /**
     * Loads an entity by id and confirms it belongs to the caller's station, returning
     * it. Answers {@code 404} when the entity is absent and {@code 403} when it belongs
     * to another station — the predominant ownership pattern across the API.
     *
     * @param finder    the repository/service lookup for the entity type
     * @param stationOf extracts the owning station id from the entity
     */
    public static <T> T requireOwned(Context ctx, int id, IntFunction<Optional<T>> finder, ToIntFunction<T> stationOf) {
        T entity = finder.apply(id).orElseThrow(NotFoundResponse::new);
        if (stationOf.applyAsInt(entity) != UserSession.from(ctx).stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        return entity;
    }

    /**
     * Variant of {@link #requireOwned} that answers {@code 404} rather than {@code 403}
     * on a station mismatch, so the existence of another station's resource is not
     * revealed. Matches handlers that deliberately hide cross-station resources.
     *
     * @param finder    the repository/service lookup for the entity type
     * @param stationOf extracts the owning station id from the entity
     */
    public static <T> T requireOwnedOrNotFound(
            Context ctx, int id, IntFunction<Optional<T>> finder, ToIntFunction<T> stationOf) {
        T entity = finder.apply(id).orElseThrow(NotFoundResponse::new);
        if (stationOf.applyAsInt(entity) != UserSession.from(ctx).stationId()) {
            throw new NotFoundResponse();
        }
        return entity;
    }
}
