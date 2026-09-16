/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a station (organization unit) in the system.
 *
 * @param id                   the internal database identifier
 * @param uid                  the external UUID identifier (used in APIs and federation)
 * @param name                 the display name of the station
 * @param timezone             the IANA timezone identifier for the station
 * @param locale               the locale string used for formatting (e.g., "de-DE")
 * @param ownerMemberId        the member ID of the station owner, or {@code null} if no owner is set
 * @param discoveryVisibility  controls whether this station appears in federation discovery
 * @param discoveryDescription optional description shown in discovery
 * @param discoveryShowKb      whether to show a link to the public knowledge base in discovery
 * @param setupCompletedAt     timestamp at which an administrator first marked the station setup wizard
 *                             complete, or {@code null} while the wizard still runs for new administrators
 * @param stationKind          whether this is a station somebody joins or the shell a cluster owns
 * @param clusterId            the cluster this station belongs to, or {@code null} when it answers to nobody
 * @param lossNoteRequired     whether a member marking their own gear lost has to write a note about it
 * @param pdfHidesInstanceUrl  whether exported PDFs leave the address of this installation off the
 *                             foot of the page. An export may differ from it for one document
 *                             without changing what the station settled on
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
        String publicSlug,
        boolean publicWaitlistEnabled,
        boolean publicBlogEnabled,
        String addressLine,
        String postalCode,
        String city,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant setupCompletedAt,
        StationKind stationKind,
        Integer clusterId,
        boolean lossNoteRequired,
        boolean pdfHidesInstanceUrl,
        boolean nicknamesEnabled) {

    /**
     * A station as it is read where the question of nicknames does not arise.
     *
     * <p>Reading them is the ordinary case, so the places that build a station row by hand do not
     * have to say so.
     */
    public Station(
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
            String publicSlug,
            boolean publicWaitlistEnabled,
            boolean publicBlogEnabled,
            String addressLine,
            String postalCode,
            String city,
            String country,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant setupCompletedAt,
            StationKind stationKind,
            Integer clusterId,
            boolean lossNoteRequired,
            boolean pdfHidesInstanceUrl) {
        this(
                id,
                uid,
                name,
                timezone,
                locale,
                ownerMemberId,
                defaultTheme,
                allowUserTheme,
                customThemeColors,
                defaultFeel,
                allowUserFeel,
                publicKbMode,
                federationPrivateKey,
                discoveryVisibility,
                discoveryDescription,
                discoveryShowKb,
                publicCalendarEnabled,
                landingPageId,
                publicPagesEnabled,
                publicSlug,
                publicWaitlistEnabled,
                publicBlogEnabled,
                addressLine,
                postalCode,
                city,
                country,
                latitude,
                longitude,
                setupCompletedAt,
                stationKind,
                clusterId,
                lossNoteRequired,
                pdfHidesInstanceUrl,
                true);
    }

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
                row.getEnum("public_kb_mode", PublicKbMode.class),
                row.getString("federation_private_key"),
                row.getEnum("discovery_visibility", DiscoveryVisibility.class),
                row.getString("discovery_description"),
                row.getBoolean("discovery_show_kb"),
                row.getBoolean("public_calendar_enabled"),
                row.getObject("landing_page_id") != null ? row.getInt("landing_page_id") : null,
                row.getBoolean("public_pages_enabled"),
                row.getString("public_slug"),
                row.getBoolean("public_waitlist_enabled"),
                row.getBoolean("public_blog_enabled"),
                row.getString("address_line"),
                row.getString("postal_code"),
                row.getString("city"),
                row.getString("country"),
                row.getBigDecimal("latitude"),
                row.getBigDecimal("longitude"),
                row.get("setup_completed_at", INSTANT_TIMESTAMP),
                row.getEnum("station_kind", StationKind.class),
                row.getObject("cluster_id", Integer.class),
                row.getBoolean("loss_note_required"),
                row.getBoolean("pdf_hides_instance_url"),
                row.getBoolean("nicknames_enabled"));
    }
}
