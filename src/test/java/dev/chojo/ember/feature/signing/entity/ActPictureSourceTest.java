/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A picture made for the act keeps how it was made, and one the signer said nothing about counts as drawn. */
class ActPictureSourceTest {

    @Test
    void aPictureMadeForTheActKeepsHowItWasMade() {
        assertEquals(ActPictureSource.DRAWN, ActPictureSource.madeAs(SignatureImageSource.DRAWN));
        assertEquals(ActPictureSource.TYPED, ActPictureSource.madeAs(SignatureImageSource.TYPED));
        assertEquals(ActPictureSource.UPLOADED, ActPictureSource.madeAs(SignatureImageSource.UPLOADED));
        assertEquals(ActPictureSource.DRAWN, ActPictureSource.madeAs(null));
    }
}
