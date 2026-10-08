/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What a signature leaves visible in its field: the signer's picture with a short caption under it, so a
 * printed copy still shows who signed and when.
 *
 * <p>The array is handed over as it is, without a copy.
 *
 * @param fieldName the signature field it is drawn into
 * @param png       the signature picture, a transparent PNG, or null where the act left none and only the
 *                  caption is drawn
 * @param caption   the lines under the picture, such as the official name and when and how it was signed
 */
public record SignatureMark(String fieldName, byte @Nullable [] png, List<String> caption) {

    public SignatureMark {
        caption = List.copyOf(caption);
    }
}
