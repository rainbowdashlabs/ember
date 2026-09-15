/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A question together with how it is put to one audience.
 *
 * <p>Read as a pair because a form needs both halves at once: the definition says what is asked, the
 * assignment says where it sits, how wide it is drawn, and whether this audience may write it.
 *
 * @param field      what is asked
 * @param assignment how it is put to this audience
 */
public record AssignedProfileField(ProfileField field, ProfileFieldAssignment assignment) {

    /** The columns this reads, with the two identifiers told apart. */
    public static final String COLUMNS = """
            f.id, f.station_id, f.name, f.field_type, f.config, f.required, f.readonly, f.width,
            f.keep_on_archive,
            a.id AS assignment_id, a.field_id, a.target_kind, a.role, a.group_id,
            a.position, a.width_override, a.readonly_override, a.required_override""";

    /** Whether this audience must answer, which the assignment may decide against the definition. */
    public boolean required() {
        return assignment.required(field.required());
    }

    /** Whether only the member management writes it here, which the assignment may decide too. */
    public boolean readonly() {
        return assignment.readonly(field.readonly());
    }

    /** How much of a row this audience gives it, which the assignment may decide against the definition. */
    public String width() {
        return assignment.width(field.width());
    }

    /**
     * Creates a row mapping for a join of a field onto one of its assignments.
     *
     * <p>Written out rather than composed from the two mappers beside it: both of those read a column
     * called {@code id}, and in this join only one of them can have it.
     */
    public static RowMapping<AssignedProfileField> map() {
        return row -> new AssignedProfileField(
                new ProfileField(
                        row.getInt("id"),
                        row.getInt("station_id"),
                        row.getString("name"),
                        row.getEnum("field_type", ProfileFieldType.class),
                        ProfileFieldConfig.parse(row.getString("config")),
                        row.getBoolean("required"),
                        row.getBoolean("readonly"),
                        row.getString("width"),
                        row.getBoolean("keep_on_archive")),
                new ProfileFieldAssignment(
                        row.getInt("assignment_id"),
                        row.getInt("field_id"),
                        row.getEnum("target_kind", ProfileFieldTarget.class),
                        row.getString("role") == null ? null : row.getEnum("role", ProfileFieldScope.class),
                        row.getObject("group_id") == null ? null : row.getInt("group_id"),
                        row.getInt("position"),
                        row.getString("width_override"),
                        row.getObject("readonly_override") == null ? null : row.getBoolean("readonly_override"),
                        row.getObject("required_override") == null ? null : row.getBoolean("required_override")));
    }
}
