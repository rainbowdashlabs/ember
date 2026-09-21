/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

/**
 * What a favourite points at, which decides where it is looked up and how it is opened.
 *
 * <p>This station's entries are rows here and are followed live; a partner's live on the partner and
 * are kept as they were last seen.
 */
public enum KbFavouriteTarget {
    /** A file of this station. */
    FILE,
    /** A folder of this station. */
    FOLDER,
    /** A file a partner station shares with this one. */
    PARTNER_FILE,
    /** A folder a partner station shares with this one. */
    PARTNER_FOLDER;

    /** Whether the entry lives on a partner station rather than this one. */
    public boolean isPartner() {
        return this == PARTNER_FILE || this == PARTNER_FOLDER;
    }
}
