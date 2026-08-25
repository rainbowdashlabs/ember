/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.Optional;
import java.util.UUID;
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
     * Reads a UUID path parameter. Answers {@code 404} when the value is not a valid UUID -
     * a malformed identifier can never address an existing resource, and this avoids
     * leaking whether the parameter format alone was the problem.
     */
    public static UUID pathUuid(Context ctx, String name) {
        try {
            return UUID.fromString(ctx.pathParam(name));
        } catch (IllegalArgumentException e) {
            throw new NotFoundResponse();
        }
    }

    /**
     * Shortcut for resolving the caller's session, so handlers read as
     * {@code session(ctx).stationId()} instead of repeating the static factory call.
     */
    public static UserSession session(Context ctx) {
        return UserSession.from(ctx);
    }

    /**
     * Confirms an already-loaded entity's owning station matches the caller's, answering
     * {@code 404} on mismatch so cross-station resource existence is not revealed. For
     * helpers that hold the entity and session directly, where
     * {@link #requireOwnedOrNotFound} does not fit.
     */
    public static void requireSameStation(UserSession session, int entityStationId) {
        if (session.stationId() == null || entityStationId != session.stationId()) {
            throw new NotFoundResponse();
        }
    }

    /**
     * Loads an entity by id and confirms it belongs to the caller's station, returning it.
     *
     * <p>A missing entity and one belonging to another station answer the same {@code 404}. The
     * variant that answered {@code 403} on a mismatch is gone: it told a caller that the id exists
     * somewhere on the instance, which is the one thing an ownership check is meant not to say, and
     * having both meant the weaker one was picked by whoever wrote the handler next.
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
