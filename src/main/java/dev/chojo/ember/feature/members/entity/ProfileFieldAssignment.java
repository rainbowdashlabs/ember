/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * Who a profile field is asked of, and how it is put to them.
 *
 * <p>The definition beside this says what the question is. Everything here is about one audience, so
 * the same question can stand on two forms without being two questions, first on one and last on the
 * other.
 *
 * @param id               the assignment identifier
 * @param fieldId          the definition being assigned
 * @param targetKind       whether this names a role or a group
 * @param role             the kind of member asked, null where this names a group
 * @param groupId          the group asked, null where this names a role
 * @param position         where the field sits on this audience's form. Not an override: where a
 *                         question sits is the form's own business and every form orders itself.
 * @param widthOverride    how much of a row it takes for this audience, where that differs from the
 *                         definition. Null means the definition decides.
 * @param readonlyOverride whether only the member management may write the answer for this audience,
 *                         where that differs from the definition. Null means the definition decides.
 * @param requiredOverride whether this audience must answer, where that differs from the definition.
 *                         Null means the definition decides, which is the ordinary case.
 */
public record ProfileFieldAssignment(
        int id,
        int fieldId,
        ProfileFieldTarget targetKind,
        @Nullable ProfileFieldScope role,
        @Nullable Integer groupId,
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
     * Whether this audience must answer, given what the definition says.
     *
     * @param definitionRequired what the field asks of everybody
     * @return what it asks of this audience
     */
    public boolean required(boolean definitionRequired) {
        return requiredOverride == null ? definitionRequired : requiredOverride;
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
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ProfileFieldAssignment> map() {
        return row -> new ProfileFieldAssignment(
                row.getInt("id"),
                row.getInt("field_id"),
                row.getEnum("target_kind", ProfileFieldTarget.class),
                row.getString("role") == null ? null : row.getEnum("role", ProfileFieldScope.class),
                row.getObject("group_id") == null ? null : row.getInt("group_id"),
                row.getInt("position"),
                row.getString("width_override"),
                row.getObject("readonly_override") == null ? null : row.getBoolean("readonly_override"),
                row.getObject("required_override") == null ? null : row.getBoolean("required_override"));
    }
}
