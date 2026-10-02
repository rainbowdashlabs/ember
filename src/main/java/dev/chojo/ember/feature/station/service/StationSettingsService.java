/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;

/**
 * The general settings a station manager changes on the station's own page: its name, clock and
 * language, its look, and what it shows the public. A setting the request leaves out stays as it is.
 */
@Singleton
public class StationSettingsService {
    private static final Logger log = LoggerFactory.getLogger(StationSettingsService.class);

    private final StationService stations;

    @Inject
    public StationSettingsService(StationService stations) {
        this.stations = stations;
    }

    /**
     * Applies the settings named in the request to the station.
     *
     * @param stationId the station being changed
     * @param request   the settings as the page sent them
     * @return the station as it now stands
     */
    public Station update(int stationId, UpdateStationRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw StationRefusal.STATION_NEEDS_A_NAME_ON_CHANGE.raise();
        }
        updateClock(stationId, request);
        updateLook(stationId, request);
        updatePublicReach(stationId, request);
        updatePublicSlug(stationId, request.publicSlug());
        return stations.update(stationId, request.name())
                .orElseThrow(StationRefusal.STATION_NOT_HERE_AFTER_CHANGE::raise);
    }

    private void updateClock(int stationId, UpdateStationRequest request) {
        if (isSet(request.timezone())) {
            try {
                ZoneId.of(request.timezone());
            } catch (ZoneRulesException e) {
                log.warn("Invalid timezone: {}", request.timezone(), e);
                throw StationRefusal.STATION_TIME_ZONE_NOT_KNOWN.raise();
            }
            stations.updateTimezone(stationId, request.timezone());
        }
        if (isSet(request.locale())) {
            stations.updateLocale(stationId, request.locale());
        }
    }

    private void updateLook(int stationId, UpdateStationRequest request) {
        if (request.defaultTheme() == null) return;
        stations.updateThemeSettings(
                stationId,
                request.defaultTheme(),
                orTrue(request.allowUserTheme()),
                request.customThemeColors(),
                request.defaultFeel() != null ? request.defaultFeel() : ThemeFeel.ROUNDED,
                orTrue(request.allowUserFeel()));
    }

    private void updatePublicReach(int stationId, UpdateStationRequest request) {
        if (request.publicKbMode() != null) {
            stations.updatePublicKbMode(stationId, PublicKbMode.valueOf(request.publicKbMode()));
        }
        if (request.discoveryVisibility() != null) {
            stations.updateDiscoverySettings(
                    stationId,
                    request.discoveryVisibility(),
                    request.discoveryDescription(),
                    Boolean.TRUE.equals(request.discoveryShowKb()));
        }
        if (request.publicCalendarEnabled() != null) {
            stations.updatePublicCalendarEnabled(stationId, request.publicCalendarEnabled());
        }
        if (request.publicPagesEnabled() != null) {
            stations.updatePublicPagesEnabled(stationId, request.publicPagesEnabled());
        }
        if (request.publicWaitlistEnabled() != null) {
            stations.updatePublicWaitlistEnabled(stationId, request.publicWaitlistEnabled());
        }
        if (request.publicBlogEnabled() != null) {
            stations.updatePublicBlogEnabled(stationId, request.publicBlogEnabled());
        }
        if (request.pdfHidesInstanceUrl() != null) {
            stations.updatePdfHidesInstanceUrl(stationId, request.pdfHidesInstanceUrl());
        }
        if (request.nicknamesEnabled() != null) {
            stations.updateNicknamesEnabled(stationId, request.nicknamesEnabled());
        }
    }

    /**
     * Gives the station the readable public address it asked for, or takes it away where the
     * request names an empty one.
     */
    private void updatePublicSlug(int stationId, String slug) {
        if (slug == null) return;
        try {
            stations.updatePublicSlug(stationId, slug.isBlank() ? null : slug);
        } catch (IllegalArgumentException e) {
            log.warn("Station {} could not be given the public address it asked for", stationId, e);
            throw StationRefusal.STATION_ADDRESS_NOT_USABLE.raise();
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean orTrue(Boolean value) {
        return value == null || value;
    }

    /**
     * Request body for updating station settings.
     *
     * @param name     the station name
     * @param timezone the IANA timezone identifier
     * @param locale   the locale string (e.g., "de-DE")
     */
    public record UpdateStationRequest(
            String name,
            String timezone,
            String locale,
            String defaultTheme,
            Boolean allowUserTheme,
            String customThemeColors,
            ThemeFeel defaultFeel,
            Boolean allowUserFeel,
            String publicKbMode,
            DiscoveryVisibility discoveryVisibility,
            String discoveryDescription,
            Boolean discoveryShowKb,
            Boolean publicCalendarEnabled,
            Boolean publicPagesEnabled,
            String publicSlug,
            Boolean publicWaitlistEnabled,
            Boolean publicBlogEnabled,
            Boolean pdfHidesInstanceUrl,
            Boolean nicknamesEnabled) {}
}
