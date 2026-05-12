/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.router.JavalinDefaultRoutingApi;

public interface Routes {
    void register(JavalinDefaultRoutingApi routes, String prefix);
}
