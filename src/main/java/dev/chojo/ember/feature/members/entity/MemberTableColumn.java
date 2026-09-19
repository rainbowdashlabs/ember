/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import jakarta.annotation.Nullable;

/**
 * One column a station asked for, named by what it points at rather than by what it says.
 *
 * <p>No label is kept. A field renamed after a selection was saved would otherwise go on being
 * printed under its old name, and how a date is shown is the field's own business: a birth date
 * carrying an age mode prints an age, and the column only has to ask for the field. The label is
 * worked out wherever the table is drawn.
 *
 * @param kind    where the value comes from
 * @param key     which builtin, for {@link MemberTableColumnKind#BUILTIN}, and null otherwise
 * @param fieldId which question, for the two field kinds, and null for a builtin
 */
public record MemberTableColumn(
        MemberTableColumnKind kind,
        @Nullable String key,
        @Nullable Integer fieldId) {

    /** A property of the membership, named by one of the keys {@code MemberTableService} answers to. */
    public static MemberTableColumn builtin(String key) {
        return new MemberTableColumn(MemberTableColumnKind.BUILTIN, key, null);
    }

    /** One of the station's questions about a person. */
    public static MemberTableColumn profileField(int fieldId) {
        return new MemberTableColumn(MemberTableColumnKind.PROFILE_FIELD, null, fieldId);
    }

    /** One of an appointment's questions, answered when registering. */
    public static MemberTableColumn registrationField(int fieldId) {
        return new MemberTableColumn(MemberTableColumnKind.REGISTRATION_FIELD, null, fieldId);
    }

    /** Whether this column names something real, which a selection read back from a client may not. */
    public boolean isWellFormed() {
        return switch (kind) {
            case BUILTIN -> key != null && !key.isBlank();
            case PROFILE_FIELD, REGISTRATION_FIELD -> fieldId != null;
        };
    }
}
