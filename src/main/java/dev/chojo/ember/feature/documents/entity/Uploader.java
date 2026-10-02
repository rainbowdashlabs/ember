/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.entity;

import org.jspecify.annotations.Nullable;

/**
 * Who put a document in.
 *
 * <p>At most one of the two is set. Somebody at the station is named by their membership there. A
 * manager of the association has no membership at the document's station, and their membership at
 * another station would name a stranger on this one's paperwork, so they are named by their account.
 * A document that arrived by mail was put in by nobody.
 *
 * @param memberId  the uploader's membership at the document's station, or null
 * @param accountId the account of an association manager who filed it, or null
 */
public record Uploader(@Nullable Integer memberId, @Nullable Integer accountId) {

    /**
     * @param memberId the uploader's membership at the document's station
     * @return a member of the station as the uploader
     */
    public static Uploader member(int memberId) {
        return new Uploader(memberId, null);
    }

    /**
     * @param accountId the account of the association manager filing it
     * @return an association manager as the uploader
     */
    public static Uploader account(int accountId) {
        return new Uploader(null, accountId);
    }

    /**
     * @return nobody, for a document that arrived on its own
     */
    public static Uploader nobody() {
        return new Uploader(null, null);
    }
}
