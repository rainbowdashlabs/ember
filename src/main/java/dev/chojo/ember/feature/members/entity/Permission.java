/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StationPermission;

/**
 * A permission assignment linking a database permission ID to a {@link StationPermission} enum value.
 *
 * @param id         the database permission identifier
 * @param permission the application permission enum value
 */
public record Permission(int id, StationPermission permission) {
    public static RowMapping<Permission> map() {
        return row -> new Permission(row.getInt("id"), row.getEnum("name", StationPermission.class));
    }
}
