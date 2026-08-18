/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

/**
 * What a member may do with a knowledge-base folder or file, from nothing to everything.
 *
 * <p>The order is the whole point: a check asks whether the level a member resolved to is at least
 * the level an action needs, so the levels must stay declared weakest first.
 */
public enum KbAccessLevel {
    /**
     * Explicitly denied. Carves an exception out of an inherited audience, and wins wherever it
     * appears along the path.
     */
    NONE,
    /**
     * See the item, open it, download it, comment on it.
     */
    READ,
    /**
     * Everything in {@link #READ}, plus editing the content, renaming, re-uploading, managing tags
     * and related files, and creating children in a folder.
     */
    WRITE,
    /**
     * Everything in {@link #WRITE}, plus deleting, moving, changing the grants themselves, and
     * changing public visibility.
     */
    MANAGE;

    /**
     * Tells whether this level is enough for an action that needs {@code required}.
     *
     * @param required the level the action needs
     * @return whether this level covers it
     */
    public boolean covers(KbAccessLevel required) {
        return compareTo(required) >= 0;
    }

    /**
     * The stronger of two levels.
     */
    public static KbAccessLevel max(KbAccessLevel first, KbAccessLevel second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.compareTo(second) >= 0 ? first : second;
    }
}
