/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a recorded change to a profile field value.
 *
 * @param id                      the change record identifier
 * @param fieldId                 the profile field that was changed
 * @param memberId                the member whose field was changed
 * @param oldValue                the previous JSON value
 * @param newValue                the new JSON value
 * @param changedBy               the member who made the change, or {@code null} where they had no
 *                                membership at the member's station (an association manager), whose
 *                                account then names them
 * @param changedAt               the timestamp of the change
 * @param requiresAcknowledgement whether this change needs to be acknowledged by a manager
 * @param changedByName           the display name of the person who made the change
 * @param fieldName               the name of the changed profile field
 * @param fieldType               the type of the changed profile field, which says how its values read
 * @param acknowledgements        the list of acknowledgements for this change
 * @param memberName              the display name of the member whose field was changed
 */
public record ProfileFieldChange(
        int id,
        @Nullable Integer fieldId,
        @Nullable Integer clusterFieldId,
        int memberId,
        @Nullable String oldValue,
        @Nullable String newValue,
        @Nullable Integer changedBy,
        Instant changedAt,
        boolean requiresAcknowledgement,
        String changedByName,
        @Nullable String fieldName,
        @Nullable String fieldType,
        List<ProfileFieldChangeAcknowledgement> acknowledgements,
        @Nullable String memberName) {
    /**
     * Whether the field that changed was asked for by the station's cluster rather than by the station.
     *
     * <p>Exactly one of the two ids is set on every row, so this is the whole answer.
     *
     * @return {@code true} when a cluster's field changed
     */
    public boolean clusterDefined() {
        return clusterFieldId != null;
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ProfileFieldChange> map() {
        return row -> new ProfileFieldChange(
                row.getInt("id"),
                row.getObject("field_id", Integer.class),
                row.getObject("cluster_field_id", Integer.class),
                row.getInt("member_id"),
                row.getString("old_value"),
                row.getString("new_value"),
                row.getObject("changed_by", Integer.class),
                row.get("changed_at", INSTANT_TIMESTAMP),
                row.getBoolean("requires_acknowledgement"),
                row.getString("changed_by_name"),
                row.getString("field_name"),
                row.getString("field_type"),
                List.of(),
                null);
    }
}
