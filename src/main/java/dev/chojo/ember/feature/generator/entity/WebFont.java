/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.time.Instant;

/**
 * The web version of an uploaded font style, which the template editor shows the style in instead of
 * the file documents print with. It is kept with the style, by the same owner, and goes with it.
 *
 * @param fileName   the name it was uploaded under
 * @param format     what it is
 * @param sizeBytes  its size
 * @param sha256     its SHA-256 as lowercase hex
 * @param uploadedAt when it was uploaded
 */
public record WebFont(String fileName, WebFontFormat format, long sizeBytes, String sha256, Instant uploadedAt) {}
