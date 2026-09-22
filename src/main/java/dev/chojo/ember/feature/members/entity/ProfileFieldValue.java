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

    /**
     * The answer as somebody wrote it, rather than as the database keeps it.
     *
     * <p>Answers are held as JSON, so a written answer comes back wearing its quotation marks and a
     * sheet printed straight from them would show every name in quotes. An explicit JSON null reads
     * as no answer.
     *
     * @return the unwrapped answer, empty for a JSON null, or {@code null} where nothing is stored
     */
    public String plainValue() {
        if (value == null) return null;
        var trimmed = value.strip();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"");
        }
        if ("null".equals(trimmed)) return "";
        return trimmed;
    }
}
