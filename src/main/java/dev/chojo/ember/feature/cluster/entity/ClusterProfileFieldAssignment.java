/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import org.jspecify.annotations.Nullable;

/**
 * Which kind of member a cluster's question is asked of, and how it is put to them.
 *
 * <p>The same split a station's fields have. A cluster names no group of members, because a group
 * belongs to one station and a cluster spans several, so the only target here is a role. Which
 * stations the question reaches is on the definition instead.
 *
 * @param id               the assignment identifier
 * @param fieldId          the definition being assigned
 * @param role             the kind of member asked
 * @param position         where the field sits on that audience's form
 * @param widthOverride    how much of a row it takes here, null to follow the definition
 * @param readonlyOverride whether only the member management may write the answer here, null to
 *                         follow the definition
 * @param requiredOverride whether that audience must answer, where that differs from the definition
 */
public record ClusterProfileFieldAssignment(
        int id,
        int fieldId,
        ProfileFieldScope role,
        int position,
        @Nullable String widthOverride,
        @Nullable Boolean readonlyOverride,
        @Nullable Boolean requiredOverride) {

    /**
     * Whether only the member management may write the answer here, given what the definition says.
     *
     * @param definitionReadonly what the field says of everybody asked it
     * @return what holds for this audience
     */
    public boolean readonly(boolean definitionReadonly) {
        return readonlyOverride == null ? definitionReadonly : readonlyOverride;
    }

    /**
     * How much of a row this audience gives the question, given what the definition says.
     *
     * @param definitionWidth the width the question carries
     * @return the width this audience draws it at
     */
    public @Nullable String width(@Nullable String definitionWidth) {
        return widthOverride == null ? definitionWidth : widthOverride;
    }

    /**
     * Whether this audience must answer, given what the definition says.
     *
     * @param definitionRequired what the field asks of everybody
     * @return what it asks of this audience
     */
    public boolean required(boolean definitionRequired) {
        return requiredOverride == null ? definitionRequired : requiredOverride;
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ClusterProfileFieldAssignment> map() {
        return row -> new ClusterProfileFieldAssignment(
                row.getInt("id"),
                row.getInt("field_id"),
                row.getEnum("role", ProfileFieldScope.class),
                row.getInt("position"),
                row.getString("width_override"),
                row.getObject("readonly_override") == null ? null : row.getBoolean("readonly_override"),
                row.getObject("required_override") == null ? null : row.getBoolean("required_override"));
    }
}
