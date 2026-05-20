/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.Roles;

/**
 * A role assignment linking a database role ID to a {@link Roles} enum value.
 *
 * @param id   the database role identifier
 * @param role the application role enum value
 */
public record Role(int id, Roles role) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<Role> map() {
        return row -> new Role(row.getInt("id"), row.getEnum("name", Roles.class));
    }
}
