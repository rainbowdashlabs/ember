/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a station (organization unit) in the system.
 *
 * @param id            the unique station identifier
 * @param name          the display name of the station
 * @param timezone      the IANA timezone identifier for the station
 * @param locale        the locale string used for formatting (e.g., "de-DE")
 * @param ownerMemberId the member ID of the station owner, or {@code null} if no owner is set
 */
public record Station(
        int id,
        String name,
        String timezone,
        String locale,
        Integer ownerMemberId,
        String defaultTheme,
        boolean allowUserTheme,
        String customThemeColors) {
    public static RowMapping<Station> map() {
        return row -> new Station(
                row.getInt("id"),
                row.getString("name"),
                row.getString("timezone"),
                row.getString("locale"),
                row.getObject("owner_member_id") != null ? row.getInt("owner_member_id") : null,
                row.getString("default_theme"),
                row.getBoolean("allow_user_theme"),
                row.getString("custom_theme_colors"));
    }
}
