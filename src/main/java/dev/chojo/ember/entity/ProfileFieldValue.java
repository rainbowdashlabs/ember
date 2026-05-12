/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

public record ProfileFieldValue(int memberId, int fieldId, String value) {
    public static de.chojo.sadu.mapper.rowmapper.RowMapping<ProfileFieldValue> map() {
        return row -> new ProfileFieldValue(row.getInt("member_id"), row.getInt("field_id"), row.getString("value"));
    }
}
