/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.entity;

/**
 * The bytes of one stored picture or library file, and the type they are served as.
 *
 * <p>The type is ready to be sent as the {@code Content-Type} header as it is: a WebP size always
 * says WebP, and a file whose sidecar recorded nothing is named by its extension.
 *
 * @param data        the bytes
 * @param contentType the type to serve them as
 */
public record MediaContent(byte[] data, String contentType) {}
