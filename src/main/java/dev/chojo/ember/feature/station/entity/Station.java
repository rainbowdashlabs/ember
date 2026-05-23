/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;

import java.util.UUID;

/**
 * Represents a station (organization unit) in the system.
 *
 * @param id            the internal database identifier
 * @param uid           the external UUID identifier (used in APIs and federation)
 * @param name          the display name of the station
 * @param timezone      the IANA timezone identifier for the station
 * @param locale        the locale string used for formatting (e.g., "de-DE")
 * @param ownerMemberId the member ID of the station owner, or {@code null} if no owner is set
 */
public record Station(
        int id,
        UUID uid,
        String name,
        String timezone,
        String locale,
        Integer ownerMemberId,
        String defaultTheme,
        boolean allowUserTheme,
        String customThemeColors,
        PublicKbMode publicKbMode) {
    public static RowMapping<Station> map() {
        return row -> new Station(
                row.getInt("id"),
                row.get("uid", StandardValueConverter.UUID_STRING),
                row.getString("name"),
                row.getString("timezone"),
                row.getString("locale"),
                row.getObject("owner_member_id") != null ? row.getInt("owner_member_id") : null,
                row.getString("default_theme"),
                row.getBoolean("allow_user_theme"),
                row.getString("custom_theme_colors"),
                PublicKbMode.valueOf(row.getString("public_kb_mode")));
    }
}
