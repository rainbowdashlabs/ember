/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Something one member marked in the wiki, as it is drawn: the entry's name and kind are this
 * station's live values for its own entries, and the values kept when last seen for a partner's.
 *
 * @param id                the favourite
 * @param memberId          whose favourite it is
 * @param target            what it points at
 * @param entryId           the file or folder id, on this station or on the partner
 * @param partnerStationUid the partner serving the entry, {@code null} for this station's entries
 * @param title             the entry's name
 * @param fileType          the file's kind, {@code null} for a folder
 * @param stationName       the partner's name, {@code null} for this station's entries
 * @param createdAt         when it was marked
 */
public record KbFavourite(
        int id,
        int memberId,
        KbFavouriteTarget target,
        int entryId,
        UUID partnerStationUid,
        String title,
        String fileType,
        String stationName,
        Instant createdAt) {

    /**
     * Maps a row of the favourites query, which resolves this station's entries against their
     * tables and passes a partner's kept values through.
     */
    public static RowMapping<KbFavourite> map() {
        return row -> {
            String partner = row.getString("partner_station_uid");
            return new KbFavourite(
                    row.getInt("id"),
                    row.getInt("member_id"),
                    row.getEnum("target", KbFavouriteTarget.class),
                    row.getInt("entry_id"),
                    partner == null ? null : UUID.fromString(partner),
                    row.getString("title"),
                    row.getString("file_type"),
                    row.getString("station_name"),
                    row.get("created_at", INSTANT_TIMESTAMP));
        };
    }
}
