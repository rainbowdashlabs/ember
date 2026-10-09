/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

/**
 * What a signature leaves visible in its field: the signer's picture, and nothing printed beside it. Who
 * signed, when and how is in the evidence the document carries and in its record.
 *
 * <p>The array is handed over as it is, without a copy.
 *
 * @param fieldName the signature field it is drawn into
 * @param png       the signature picture, a transparent PNG, or null where the act left none and the field is
 *                  only taken out
 */
public record SignatureMark(String fieldName, byte @Nullable [] png) {}
