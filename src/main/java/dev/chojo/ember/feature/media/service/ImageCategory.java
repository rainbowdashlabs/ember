/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

/**
 * Categories for image storage, each mapped to a subdirectory on disk.
 */
public enum ImageCategory {
    AVATARS("avatars"),
    LOST_AND_FOUND("lost-and-found");

    private final String directory;

    ImageCategory(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }
}
