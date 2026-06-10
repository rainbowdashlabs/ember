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
 * @param id                    the internal database identifier
 * @param uid                   the external UUID identifier (used in APIs and federation)
 * @param name                  the display name of the station
 * @param timezone              the IANA timezone identifier for the station
 * @param locale                the locale string used for formatting (e.g., "de-DE")
 * @param ownerMemberId         the member ID of the station owner, or {@code null} if no owner is set
 * @param discoveryVisibility   controls whether this station appears in federation discovery
 * @param discoveryDescription  optional description shown in discovery
 * @param discoveryShowKb       whether to show a link to the public knowledge base in discovery
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
        ThemeFeel defaultFeel,
        boolean allowUserFeel,
        PublicKbMode publicKbMode,
        String federationPrivateKey,
        DiscoveryVisibility discoveryVisibility,
        String discoveryDescription,
        boolean discoveryShowKb,
        boolean publicCalendarEnabled,
        Integer landingPageId,
        boolean publicPagesEnabled,
        String publicSlug) {
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
                row.getEnum("default_feel", ThemeFeel.class),
                row.getBoolean("allow_user_feel"),
                PublicKbMode.valueOf(row.getString("public_kb_mode")),
                row.getString("federation_private_key"),
                DiscoveryVisibility.valueOf(row.getString("discovery_visibility")),
                row.getString("discovery_description"),
                row.getBoolean("discovery_show_kb"),
                row.getBoolean("public_calendar_enabled"),
                row.getObject("landing_page_id") != null ? row.getInt("landing_page_id") : null,
                row.getBoolean("public_pages_enabled"),
                row.getString("public_slug"));
    }
}
