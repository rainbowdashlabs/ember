/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * What a chosen column draws its values from.
 *
 * <p>The three are kept apart because each is read a different way and guarded a different way. A
 * builtin is a property of the membership itself and everybody who may see the person may see it. A
 * profile field is the station's own question about a person and passes the field scopes. An answer
 * belongs to one appointment and exists only where the table is drawn for one.
 */
public enum MemberTableColumnKind {
    /** A property of the membership: the name, the kind of member, the groups they are in. */
    BUILTIN,
    /** One of the station's own questions about a person, guarded by that field's scope. */
    PROFILE_FIELD,
    /** One of an appointment's own questions, answered when registering for it. */
    REGISTRATION_FIELD
}
