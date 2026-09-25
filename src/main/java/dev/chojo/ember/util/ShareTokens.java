/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * The links that stand in for a password.
 *
 * <p>A form sent to somebody, and a page reached only by its link, are both opened by whoever holds
 * the address and by nobody else. The address is therefore the whole of the protection, and it has
 * to be long enough that guessing one is not a thing anybody can do: 32 bytes from the system's
 * cryptographic source, written in the Base64 alphabet that survives being a path segment.
 *
 * <p>The feed tokens do the same thing in their own service and predate this. They are not moved
 * here, because a feed token is created and looked up in one place while these are minted by two
 * features, and a shared mint is what keeps those two from disagreeing about how long a link is.
 */
@Singleton
public class ShareTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    /**
     * @return a fresh link, in URL-safe Base64 without padding
     */
    public String mint() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
