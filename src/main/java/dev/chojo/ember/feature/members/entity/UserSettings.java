/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record UserSettings(int memberId, boolean emailEnabled) {
    public static RowMapping<UserSettings> map() {
        return row -> new UserSettings(row.getInt("member_id"), row.getBoolean("email_enabled"));
    }
}
