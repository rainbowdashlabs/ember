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
    LOST_AND_FOUND("lost-and-found"),
    APP_LOGOS("app-logos"),
    QUIZ_QUESTIONS("quiz-questions"),
    KB_ICONS("kb-icons"),
    KB_IMAGES("kb-images");

    private final String directory;

    ImageCategory(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }
}
