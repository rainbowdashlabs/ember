/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import jakarta.inject.Singleton;

/**
 * The links that stand in for a password.
 *
 * <p>A form sent to somebody, and a page reached only by its link, are both opened by whoever holds
 * the address and by nobody else. The address is therefore the whole of the protection, and it has
 * to be long enough that guessing one is not a thing anybody can do: 32 random bytes, written in the
 * Base64 alphabet that survives being a path segment. Both features mint through this one, so they
 * never disagree about how long a link is.
 */
@Singleton
public class ShareTokens {
    private static final int BYTES = 32;

    /**
     * @return a fresh link, in URL-safe Base64 without padding
     */
    public String mint() {
        return RandomTokens.urlSafe(BYTES);
    }
}
