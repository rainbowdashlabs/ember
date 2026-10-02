/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import java.io.IOException;

/**
 * An outbound request was not sent because its destination is not one this instance may reach.
 *
 * <p>An {@link IOException}, so callers that already treat an unreachable host as a failed call
 * treat a refused one the same way without a second catch.
 */
public class RefusedDestinationException extends IOException {
    private final Reason reason;

    /**
     * @param reason  why the destination was refused
     * @param message the details, safe to log
     */
    public RefusedDestinationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Why the destination was refused, for callers that tell an operator what to fix.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }

    /** Why a destination may not be reached. */
    public enum Reason {
        /** The address is not an HTTPS URL with a host, or carries credentials. */
        MALFORMED,
        /** The host name does not resolve. */
        UNRESOLVABLE,
        /** The host resolves to a loopback, private, link-local or otherwise reserved address. */
        NOT_PUBLIC
    }
}
