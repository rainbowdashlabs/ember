/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Defines a custom profile field for a station.
 *
 * @param id            the field identifier
 * @param stationId     the station this field belongs to
 * @param name          the display name of the field
 * @param fieldType     the type of the field (e.g. TEXT, NUMBER, DATE, BOOLEAN, ENUM, AGE)
 * @param config        the field configuration stored as JSON (options, required, readonly, etc.)
 * @param position      the display order position within its scope
 * @param scope         the visibility scope of the field (MEMBER, GUARDIAN, TEAM, GROUP)
 * @param keepOnArchive whether to retain this field's values when a member is archived
 */
public record ProfileField(
        int id,
        int stationId,
        String name,
        ProfileFieldType fieldType,
        String config,
        int position,
        ProfileFieldScope scope,
        boolean keepOnArchive) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ProfileField> map() {
        return row -> new ProfileField(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getEnum("field_type", ProfileFieldType.class),
                row.getString("config"),
                row.getInt("position"),
                row.getEnum("scope", ProfileFieldScope.class),
                row.getBoolean("keep_on_archive"));
    }
}
