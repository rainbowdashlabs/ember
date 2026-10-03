/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.entity;

import org.jspecify.annotations.Nullable;

/**
 * What another instance sent with a picture to recognise it by, asked back with the next time so an
 * unchanged picture is not sent again.
 *
 * @param etag         the entity tag, or {@code null} where none was sent
 * @param lastModified the modification date exactly as it was sent, or {@code null} where none was sent
 */
public record PictureTags(@Nullable String etag, @Nullable String lastModified) {
    /** Nothing to recognise a picture by, which asks for it whatever it is. */
    public static final PictureTags NONE = new PictureTags(null, null);
}
