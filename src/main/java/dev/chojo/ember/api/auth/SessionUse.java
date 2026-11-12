/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import io.javalin.security.RouteRole;

/**
 * How a route uses the session beside the permissions it asks for.
 *
 * <p>Every admitted request keeps its session alive: it records the use and renews a session that
 * has used up half its lifetime. A request a page holds open on its own, without anybody doing
 * anything, must not, or an open tab would keep a session alive for as long as it stays open.
 */
public enum SessionUse implements RouteRole {
    /** The route reads with the session and leaves its lifetime alone. */
    PASSIVE
}
