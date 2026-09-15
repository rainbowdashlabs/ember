/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;

/**
 * A cluster's question together with how it is put to one audience.
 *
 * @param field      what is asked
 * @param assignment how it is put to this audience
 */
public record AssignedClusterProfileField(ClusterProfileField field, ClusterProfileFieldAssignment assignment) {

    /** The columns this reads, with the two identifiers told apart. */
    public static final String COLUMNS = """
            cpf.id, cpf.cluster_id, cpf.name, cpf.field_type, cpf.config, cpf.required, cpf.readonly,
            cpf.width, cpf.station_readonly, cpf.keep_on_archive, cpf.station_group_id,
            a.id AS assignment_id, a.field_id, a.role, a.position, a.width_override,
            a.readonly_override, a.required_override""";

    /** How much of a row this audience gives it, which the assignment may decide against the definition. */
    public String width() {
        return assignment.width(field.width());
    }

    /** Whether only the member management writes it here, which the assignment may decide too. */
    public boolean readonly() {
        return assignment.readonly(field.readonly());
    }

    /** Whether this audience must answer, which the assignment may decide against the definition. */
    public boolean required() {
        return assignment.required(field.required());
    }

    /**
     * Creates a row mapping for a join of a cluster field onto one of its assignments.
     *
     * <p>Written out rather than composed from the two mappers beside it: both read a column called
     * {@code id}, and in this join only one of them can have it.
     */
    public static RowMapping<AssignedClusterProfileField> map() {
        return row -> new AssignedClusterProfileField(
                new ClusterProfileField(
                        row.getInt("id"),
                        row.getInt("cluster_id"),
                        row.getString("name"),
                        row.getEnum("field_type", ProfileFieldType.class),
                        ProfileFieldConfig.parse(row.getString("config")),
                        row.getBoolean("required"),
                        row.getBoolean("readonly"),
                        row.getString("width"),
                        row.getBoolean("station_readonly"),
                        row.getBoolean("keep_on_archive"),
                        row.getObject("station_group_id") == null ? null : row.getInt("station_group_id")),
                new ClusterProfileFieldAssignment(
                        row.getInt("assignment_id"),
                        row.getInt("field_id"),
                        row.getEnum("role", ProfileFieldScope.class),
                        row.getInt("position"),
                        row.getString("width_override"),
                        row.getObject("readonly_override") == null ? null : row.getBoolean("readonly_override"),
                        row.getObject("required_override") == null ? null : row.getBoolean("required_override")));
    }
}
