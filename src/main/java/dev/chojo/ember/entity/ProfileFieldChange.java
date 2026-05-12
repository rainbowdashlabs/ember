/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record ProfileFieldChange(
        int id,
        int fieldId,
        int memberId,
        String oldValue,
        String newValue,
        int changedBy,
        Instant changedAt,
        boolean requiresAcknowledgement,
        String changedByName,
        String fieldName,
        List<ProfileFieldChangeAcknowledgement> acknowledgements) {
    public static RowMapping<ProfileFieldChange> map() {
        return row -> new ProfileFieldChange(
                row.getInt("id"),
                row.getInt("field_id"),
                row.getInt("member_id"),
                row.getString("old_value"),
                row.getString("new_value"),
                row.getInt("changed_by"),
                row.get("changed_at", INSTANT_TIMESTAMP),
                row.getBoolean("requires_acknowledgement"),
                row.getString("changed_by_name"),
                row.getString("field_name"),
                List.of());
    }
}
