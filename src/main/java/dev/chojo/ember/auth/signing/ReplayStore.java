/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

import java.time.Instant;

/**
 * Remembers the nonces of signed requests already taken, so a captured request cannot be sent again.
 *
 * <p>A nonce only has to be remembered for as long as its request would still be accepted, which is
 * the timestamp window of its protocol; the caller says how long that is. Two strategies stand
 * behind this: {@link MemoryReplayStore} for traffic that only has to survive while the process
 * runs, and {@link DatabaseReplayStore} for traffic whose nonces should outlive a restart.
 */
public interface ReplayStore {

    /**
     * Records a nonce and says whether this is the first time it was seen.
     *
     * <p>Recording is atomic, so two copies of the same request arriving together cannot both pass.
     *
     * @param scope     whose nonces these are, so two senders can use the same nonce independently
     * @param nonce     the nonce the request carries
     * @param expiresAt when the request would no longer be accepted anyway, and the nonce can be
     *                  forgotten
     * @return true on the first sighting; false for a replay, and for a nonce that could not be
     *         remembered, both of which are to be refused
     */
    boolean firstSighting(String scope, String nonce, Instant expiresAt);
}
