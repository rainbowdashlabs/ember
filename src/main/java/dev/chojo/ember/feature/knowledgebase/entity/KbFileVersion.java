/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record KbFileVersion(
        int id, int fileId, String patch, boolean isFull, int version, int createdBy, Instant createdAt) {

    public static RowMapping<KbFileVersion> map() {
        return row -> new KbFileVersion(
                row.getInt("id"),
                row.getInt("file_id"),
                row.getString("patch"),
                row.getBoolean("is_full"),
                row.getInt("version"),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
