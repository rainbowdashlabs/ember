/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.router.JavalinDefaultRoutingApi;

/**
 * Interface for route groups that register their HTTP endpoints with the Javalin router.
 * Each feature module implements this interface and is bound via Guice multibinding.
 */
public interface Routes {
    /**
     * Registers all HTTP endpoints for this route group.
     *
     * @param routes the Javalin routing API to register handlers on
     * @param prefix the API version prefix (e.g. {@code /api/v1})
     */
    void register(JavalinDefaultRoutingApi routes, String prefix);
}
