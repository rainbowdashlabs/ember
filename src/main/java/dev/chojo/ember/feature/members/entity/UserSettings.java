/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * User-specific notification and communication settings.
 *
 * @param memberId     the station member identifier
 * @param emailEnabled whether email notifications are enabled for this member
 */
public record UserSettings(int memberId, boolean emailEnabled) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<UserSettings> map() {
        return row -> new UserSettings(row.getInt("member_id"), row.getBoolean("email_enabled"));
    }
}
