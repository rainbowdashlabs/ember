/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A word a station sorts its documents by. Written as it is needed rather than set up in advance.
 */
public record MemberDocumentTag(int id, int stationId, String name) {
    public static RowMapping<MemberDocumentTag> map() {
        return row -> new MemberDocumentTag(row.getInt("id"), row.getInt("station_id"), row.getString("name"));
    }
}
