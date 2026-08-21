/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

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
 * @param changedBy               the member who made the change
 * @param changedAt               the timestamp of the change
 * @param requiresAcknowledgement whether this change needs to be acknowledged by a manager
 * @param changedByName           the display name of the person who made the change
 * @param fieldName               the name of the changed profile field
 * @param acknowledgements        the list of acknowledgements for this change
 * @param memberName              the display name of the member whose field was changed
 */
public record ProfileFieldChange(
        int id,
        Integer fieldId,
        Integer clusterFieldId,
        int memberId,
        String oldValue,
        String newValue,
        int changedBy,
        Instant changedAt,
        boolean requiresAcknowledgement,
        String changedByName,
        String fieldName,
        List<ProfileFieldChangeAcknowledgement> acknowledgements,
        String memberName) {
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
                row.getInt("changed_by"),
                row.get("changed_at", INSTANT_TIMESTAMP),
                row.getBoolean("requires_acknowledgement"),
                row.getString("changed_by_name"),
                row.getString("field_name"),
                List.of(),
                null);
    }
}
