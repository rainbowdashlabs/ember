/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for station CRUD operations, logo management, and module settings.
 */
@Singleton
public class StationRepository {

    private static final String STATION_COLUMNS =
            "id, uid, name, timezone, locale, owner_member_id, default_theme, allow_user_theme, custom_theme_colors, default_feel, allow_user_feel, public_kb_mode, federation_private_key, discovery_visibility, discovery_description, discovery_show_kb, public_calendar_enabled";

    /**
     * Finds a station by its ID.
     *
     * @param id the station ID
     * @return the station, or empty if not found
     */
    public Optional<Station> findById(int id) {
        return Query.query("SELECT " + STATION_COLUMNS + " FROM station WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(Station.map())
                .first();
    }

    /**
     * Finds a station by its external UUID.
     *
     * @param uid the station UUID
     * @return the station, or empty if not found
     */
    public Optional<Station> findByUid(UUID uid) {
        return Query.query("SELECT " + STATION_COLUMNS + " FROM station WHERE uid = :uid::uuid;")
                .single(Call.of().bind("uid", uid, StandardValueConverter.UUID_STRING))
                .map(Station.map())
                .first();
    }

    /**
     * Retrieves all stations.
     *
     * @return a list of all stations
     */
    public List<Station> findAll() {
        return Query.query("SELECT " + STATION_COLUMNS + " FROM station ORDER BY id;")
                .single()
                .map(Station.map())
                .all();
    }

    /**
     * Creates a new station with the given name.
     *
     * @param name the station name
     * @return the created station
     */
    public Station create(String name) {
        return Query.query("INSERT INTO station(name) VALUES(:name) RETURNING " + STATION_COLUMNS + ";")
                .single(Call.of().bind("name", name))
                .map(Station.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates the name of a station.
     *
     * @param id   the station ID
     * @param name the new name
     * @return {@code true} if a row was updated
     */
    public boolean update(int id, String name) {
        return Query.query("UPDATE station SET name = :name WHERE id = :id;")
                .single(Call.of().bind("name", name).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Updates the timezone of a station.
     *
     * @param id       the station ID
     * @param timezone the IANA timezone identifier
     * @return {@code true} if a row was updated
     */
    public boolean updateTimezone(int id, String timezone) {
        return Query.query("UPDATE station SET timezone = :timezone WHERE id = :id;")
                .single(Call.of().bind("timezone", timezone).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Updates the locale of a station.
     *
     * @param id     the station ID
     * @param locale the locale string (e.g., "de-DE")
     * @return {@code true} if a row was updated
     */
    public boolean updateLocale(int id, String locale) {
        return Query.query("UPDATE station SET locale = :locale WHERE id = :id;")
                .single(Call.of().bind("locale", locale).bind("id", id))
                .update()
                .changed();
    }

    public boolean updatePublicKbMode(int id, PublicKbMode mode) {
        return Query.query("UPDATE station SET public_kb_mode = :mode WHERE id = :id;")
                .single(Call.of().bind("mode", mode.name()).bind("id", id))
                .update()
                .changed();
    }

    public boolean updatePublicCalendarEnabled(int id, boolean enabled) {
        return Query.query("UPDATE station SET public_calendar_enabled = :enabled WHERE id = :id;")
                .single(Call.of().bind("enabled", enabled).bind("id", id))
                .update()
                .changed();
    }

    public boolean updateFederationPrivateKey(int id, String privateKey) {
        return Query.query("UPDATE station SET federation_private_key = :key WHERE id = :id;")
                .single(Call.of().bind("key", privateKey).bind("id", id))
                .update()
                .changed();
    }

    public void updateThemeSettings(
            int id,
            String defaultTheme,
            boolean allowUserTheme,
            String customThemeColors,
            ThemeFeel defaultFeel,
            boolean allowUserFeel) {
        Query.query("""
                        UPDATE station SET default_theme = :default_theme, allow_user_theme = :allow_user_theme,
                        custom_theme_colors = :custom_theme_colors::jsonb, default_feel = :default_feel,
                        allow_user_feel = :allow_user_feel WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("default_theme", defaultTheme)
                        .bind("allow_user_theme", allowUserTheme)
                        .bind("custom_theme_colors", customThemeColors)
                        .bind("default_feel", defaultFeel)
                        .bind("allow_user_feel", allowUserFeel))
                .update();
    }

    /**
     * Sets the owner of a station.
     *
     * @param stationId     the station ID
     * @param ownerMemberId the member ID of the new owner, or {@code null} to clear ownership
     * @return {@code true} if a row was updated
     */
    public boolean setOwner(int stationId, Integer ownerMemberId) {
        return Query.query("UPDATE station SET owner_member_id = :owner WHERE id = :id;")
                .single(Call.of().bind("owner", ownerMemberId).bind("id", stationId))
                .update()
                .changed();
    }

    /**
     * Deletes a station by its ID.
     *
     * @param id the station ID
     * @return {@code true} if a row was deleted
     */
    public boolean delete(int id) {
        return Query.query("DELETE FROM station WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Retrieves the logo for a station.
     *
     * @param id the station ID
     * @return the logo data and content type, or empty if no logo is set
     */
    public Optional<StationLogo> findLogo(int id) {
        return Query.query("SELECT logo, logo_content_type FROM station WHERE id = :id AND logo IS NOT NULL;")
                .single(Call.of().bind("id", id))
                .map(row -> new StationLogo(row.getBytes("logo"), row.getString("logo_content_type")))
                .first();
    }

    /**
     * Updates the logo of a station.
     *
     * @param id          the station ID
     * @param logo        the logo image data
     * @param contentType the MIME content type of the logo
     * @return {@code true} if a row was updated
     */
    public boolean updateLogo(int id, byte[] logo, String contentType) {
        return Query.query("UPDATE station SET logo = :logo, logo_content_type = :content_type WHERE id = :id;")
                .single(Call.of()
                        .bind("logo", logo)
                        .bind("content_type", contentType)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Removes the logo from a station.
     *
     * @param id the station ID
     * @return {@code true} if a row was updated
     */
    public boolean deleteLogo(int id) {
        return Query.query("UPDATE station SET logo = NULL, logo_content_type = NULL WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    /**
     * Retrieves the set of disabled module names for a station.
     *
     * @param stationId the station ID
     * @return an immutable set of disabled module names
     */
    public Set<StationModule> findDisabledModules(int stationId) {
        return Set.copyOf(Query.query("SELECT module FROM station_disabled_module WHERE station_id = :station_id;")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> row.getEnum("module", StationModule.class))
                .all());
    }

    // -- Module settings --

    /**
     * Replaces all disabled modules for a station with the given set.
     */
    public void setDisabledModules(int stationId, Set<StationModule> modules) {
        Query.query("DELETE FROM station_disabled_module WHERE station_id = :station_id;")
                .single(Call.of().bind("station_id", stationId))
                .delete();
        for (StationModule module : modules) {
            Query.query(
                            "INSERT INTO station_disabled_module(station_id, module) VALUES(:station_id, :module) ON CONFLICT DO NOTHING;")
                    .single(Call.of().bind("station_id", stationId).bind("module", module))
                    .insert();
        }
    }

    /**
     * Updates the UUID of a station (used during import to preserve the original UUID).
     */
    public void updateUid(int id, UUID uid) {
        Query.query("UPDATE station SET uid = :uid::uuid WHERE id = :id;")
                .single(Call.of()
                        .bind("uid", uid, StandardValueConverter.UUID_STRING)
                        .bind("id", id))
                .update();
    }

    /**
     * Updates the discovery settings for a station.
     */
    public boolean updateDiscoverySettings(int id, DiscoveryVisibility visibility, String description, boolean showKb) {
        return Query.query("""
                        UPDATE station SET discovery_visibility = :visibility, discovery_description = :description,
                        discovery_show_kb = :show_kb WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("visibility", visibility)
                        .bind("description", description)
                        .bind("show_kb", showKb))
                .update()
                .changed();
    }

    /**
     * Finds all stations visible to the given visibility levels, excluding the given station.
     */
    public List<Station> findWithPublicContent(int excludeStationId) {
        return Query.query(
                        "SELECT " + STATION_COLUMNS
                                + " FROM station WHERE id != :exclude_id AND (public_calendar_enabled = TRUE OR public_kb_mode != 'OFF') ORDER BY name;")
                .single(Call.of().bind("exclude_id", excludeStationId))
                .map(Station.map())
                .all();
    }

    public List<Station> findDiscoverable(int excludeStationId, DiscoveryVisibility visA, DiscoveryVisibility visB) {
        return Query.query(
                        "SELECT %s FROM station WHERE id != :exclude_id AND discovery_visibility IN (:vis_a, :vis_b) ORDER BY name;", STATION_COLUMNS)
                .single(Call.of()
                        .bind("exclude_id", excludeStationId)
                        .bind("vis_a", visA)
                        .bind("vis_b", visB))
                .map(Station.map())
                .all();
    }

    /**
     * Holds the binary data and content type of a station logo.
     *
     * @param data        the raw image bytes
     * @param contentType the MIME content type
     */
    public record StationLogo(byte[] data, String contentType) {}
}
