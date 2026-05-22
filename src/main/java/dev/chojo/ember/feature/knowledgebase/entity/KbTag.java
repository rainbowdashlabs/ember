/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import de.chojo.sadu.mapper.annotation.MappingProvider;
import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record KbTag(int id, int stationId, String name) {
    @MappingProvider("")
    public static RowMapping<KbTag> map() {
        return row -> new KbTag(row.getInt("id"), row.getInt("station_id"), row.getString("name"));
    }
}
