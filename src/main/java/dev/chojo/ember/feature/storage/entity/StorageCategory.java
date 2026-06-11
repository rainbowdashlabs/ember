/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

/**
 * Categories of file storage, each tracked independently for quota purposes.
 */
public enum StorageCategory {
    KB_FILES,
    BOARD_ATTACHMENTS,
    PAGE_IMAGES,
    AVATARS,
    IMAGES;

    /**
     * Whether this category counts toward quota enforcement.
     * {@link #AVATARS} are tracked but never enforced.
     */
    public boolean enforcesQuota() {
        return this != AVATARS;
    }
}
