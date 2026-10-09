/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.util.Sha256;

/**
 * The signature picture a signing act left in its field, as the evidence keeps it.
 *
 * <p>The array is handed over as it is, without a copy.
 *
 * @param png    the picture, a transparent PNG
 * @param source how it came to the act
 */
public record ActPicture(byte[] png, ActPictureSource source) {

    /** @return SHA-256 of the picture, lower-case hexadecimal */
    public String sha256() {
        return Sha256.hex(png);
    }
}
