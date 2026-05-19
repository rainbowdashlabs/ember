/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.Roles;

public record Role(int id, Roles role) {
    public static RowMapping<Role> map() {
        return row -> new Role(row.getInt("id"), row.getEnum("name", Roles.class));
    }
}
