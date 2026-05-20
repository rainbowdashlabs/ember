/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Stores a member's value for a specific profile field.
 *
 * @param memberId the station member identifier
 * @param fieldId  the profile field identifier
 * @param value    the field value stored as JSON
 */
public record ProfileFieldValue(int memberId, int fieldId, String value) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ProfileFieldValue> map() {
        return row -> new ProfileFieldValue(row.getInt("member_id"), row.getInt("field_id"), row.getString("value"));
    }
}
