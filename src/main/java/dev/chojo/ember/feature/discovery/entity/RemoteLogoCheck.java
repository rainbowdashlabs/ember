/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * A cached station card whose logo is due to be asked for again, and what is known of the copy kept here.
 *
 * @param stationUid the station's identifier as the other instance sent it
 * @param logoUrl    where the other instance says the logo is, or {@code null} where it named nothing
 * @param stored     whether a copy of the logo is kept here
 * @param tags       what the other instance sent with that copy
 */
public record RemoteLogoCheck(String stationUid, @Nullable String logoUrl, boolean stored, PictureTags tags) {

    public static RowMapping<RemoteLogoCheck> map() {
        return row -> new RemoteLogoCheck(
                row.getString("station_uid"),
                row.getString("logo_url"),
                row.getBoolean("logo_stored"),
                new PictureTags(row.getString("logo_etag"), row.getString("logo_last_modified")));
    }
}
