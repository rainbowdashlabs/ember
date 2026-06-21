/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import io.javalin.security.RouteRole;

/**
 * Sensitivity categories that require a fresh second-factor verification before a route runs.
 * A route marked with one of these as a {@link RouteRole} is only invoked if the session's
 * last 2FA verification is within the configured freshness window.
 */
public enum StepUpCategory implements RouteRole {
    /**
     * Self-service changes to credentials, factors, and active sessions.
     * Examples: password change, email change, 2FA factor add/remove, backup-code regenerate,
     * session revoke-all, trusted-device revoke.
     */
    ACCOUNT_SECURITY,

    /**
     * Federation pairing, key rotation, and cross-instance sharing changes.
     */
    FEDERATION,

    /**
     * Instance-wide configuration: settings, module toggles, mail, storage, legal docs,
     * federation discovery toggle.
     */
    INSTANCE_CONFIG,

    /**
     * Granting or revoking station / instance permissions or member-type changes.
     */
    ROLE_CHANGE
}
