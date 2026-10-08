/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.util.Sha256;

/**
 * A signature picture ready to be drawn into a document: a PNG with a transparent background, the ink in
 * one dark colour, cut to the signature with a small margin.
 *
 * <p>The array is handed over as it is, without a copy.
 *
 * @param png    the picture
 * @param width  its width in pixels
 * @param height its height in pixels
 */
public record SignaturePicture(byte[] png, int width, int height) {

    /** @return SHA-256 of the picture, lower-case hexadecimal */
    public String sha256() {
        return Sha256.hex(png);
    }
}
