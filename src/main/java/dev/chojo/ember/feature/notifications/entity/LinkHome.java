/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Where the links of a mail or a feed are read from: a station or an association, and which one.
 *
 * <p>A reader of several stations or several associations has to land in the one the notification
 * belongs to, so a link into that area carries its identity, and a link the address table does not
 * know falls back to that area's own start page rather than to another area's.
 *
 * @param kind station or association
 * @param uid  the station's or the association's identity, {@code null} where it is not known
 */
public record LinkHome(DigestGroup.Kind kind, @Nullable UUID uid) {

    /**
     * The links of a station's mail or feed.
     *
     * @param stationUid the station, {@code null} where it is not known
     * @return the home
     */
    public static LinkHome station(@Nullable UUID stationUid) {
        return new LinkHome(DigestGroup.Kind.STATION, stationUid);
    }

    /**
     * The links of an association's mail.
     *
     * @param clusterUid the association, {@code null} where it is not known
     * @return the home
     */
    public static LinkHome cluster(@Nullable UUID clusterUid) {
        return new LinkHome(DigestGroup.Kind.CLUSTER, clusterUid);
    }
}
