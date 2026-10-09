/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Whether this installation holds exactly the checked file among its sealed documents, compared byte for
 * byte by SHA-256. Says nothing about which station, which document, its title or the people it concerns.
 *
 * @param held      whether a sealed document here has a version with exactly these bytes
 * @param sealedAt  when that version was filed, the first one when several match; null when none is held
 * @param sealLevel the level its seal reached when it was filed; null when none is held
 */
public record HeldCopy(
        boolean held, @Nullable Instant sealedAt, @Nullable SealLevel sealLevel) {

    /** @return the answer for a file this installation does not hold */
    public static HeldCopy notHeld() {
        return new HeldCopy(false, null, null);
    }
}
