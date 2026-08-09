/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for station CRUD operations, logo management, and module settings.
 */
@Singleton
public class StationRepository {

    private static final String STATION_COLUMNS =
            "id, uid, name, timezone, locale, owner_member_id, default_theme, allow_user_theme, custom_theme_colors, default_feel, allow_user_feel, public_kb_mode, federation_private_key, discovery_visibility, discovery_description, discovery_show_kb, public_calendar_enabled, landing_page_id, public_pages_enabled, public_slug, public_waitlist_enabled, public_blog_enabled, address_line, postal_code, city, country, latitude, longitude, setup_completed_at";

    private final Cache<Integer, UUID> uidCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(1_000)
            .build();
    private final Cache<UUID, Integer> idCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(1_000)
            .build();
    private final Cache<Integer, Boolean> readOnlyCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(1_000)
            .build();

    /**
     * Resolves an internal station ID to its external UUID. Cached.
     */
    public UUID resolveUid(int stationId) {
        return uidCache.get(stationId, id -> query("SELECT uid FROM station WHERE id = :id;")
                .single(call().bind("id", id))
                .map(row -> row.get("uid", StandardValueConverter.UUID_STRING))
                .first()
                .orElse(null));
    }

    /**
     * Resolves an external UUID to its internal station ID. Cached.
     */
    public Optional<Integer> resolveId(UUID uid) {
        var cached = idCache.getIfPresent(uid);
        if (cached != null) return Optional.of(cached);
        return query("SELECT id FROM station WHERE uid = :uid::UUID;")
                .single(call().bind("uid", uid, StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .map(id -> {
                    idCache.put(uid, id);
                    uidCache.put(id, uid);
                    return id;
                });
    }

    /**
     * Invalidates the UID cache for a station (on create/delete).
     */
    public void invalidateUidCache(int stationId) {
        var uid = uidCache.getIfPresent(stationId);
        uidCache.invalidate(stationId);
        if (uid != null) idCache.invalidate(uid);
    }

    /**
     * Finds a station by its ID.
     *
     * @param id the station ID
     * @return the station, or empty if not found
     */
    public Optional<Station> findById(int id) {
        return SqlSupport.findById("station", STATION_COLUMNS, id, Station.map());
    }

    /**
     * Finds a station by its external UUID.
     *
     * @param uid the station UUID
     * @return the station, or empty if not found
     */
    public Optional<Station> findByUid(UUID uid) {
        return query("SELECT %s FROM station WHERE uid = :uid::UUID;", STATION_COLUMNS)
                .single(call().bind("uid", uid, StandardValueConverter.UUID_STRING))
                .map(Station.map())
                .first();
    }

    /**
     * Finds a station by its public slug.
     */
    public Optional<Station> findBySlug(String slug) {
        return query("SELECT %s FROM station WHERE public_slug = :slug;", STATION_COLUMNS)
                .single(call().bind("slug", slug))
                .map(Station.map())
                .first();
    }

    /**
     * Updates the public slug for a station.
     */
    public void updatePublicSlug(int id, String slug) {
        query("UPDATE station SET public_slug = :slug WHERE id = :id;")
                .single(call().bind("slug", slug).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Retrieves all stations.
     *
     * @return a list of all stations
     */
    public List<Station> findAll() {
        return query("SELECT %s FROM station ORDER BY id;", STATION_COLUMNS)
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
        return SqlSupport.insertReturning(
                "INSERT INTO station(name) VALUES(:name) RETURNING %s;",
                call().bind("name", name), Station.map(), STATION_COLUMNS);
    }

    public Station create(String name, UUID uid) {
        return SqlSupport.insertReturning(
                "INSERT INTO station(name, uid) VALUES(:name, :uid::UUID) RETURNING %s;",
                call().bind("name", name).bind("uid", uid, StandardValueConverter.UUID_STRING),
                Station.map(),
                STATION_COLUMNS);
    }

    /**
     * Updates the name of a station.
     *
     * @param id   the station ID
     * @param name the new name
     * @return {@code true} if a row was updated
     */
    public boolean update(int id, String name) {
        return query("UPDATE station SET name = :name WHERE id = :id;")
                .single(call().bind("name", name).bind("id", id))
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
        return query("UPDATE station SET timezone = :timezone WHERE id = :id;")
                .single(call().bind("timezone", timezone).bind("id", id))
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
        return query("UPDATE station SET locale = :locale WHERE id = :id;")
                .single(call().bind("locale", locale).bind("id", id))
                .update()
                .changed();
    }

    public boolean updatePublicKbMode(int id, PublicKbMode mode) {
        return query("UPDATE station SET public_kb_mode = :mode WHERE id = :id;")
                .single(call().bind("mode", mode).bind("id", id))
                .update()
                .changed();
    }

    public boolean updatePublicCalendarEnabled(int id, boolean enabled) {
        return query("UPDATE station SET public_calendar_enabled = :enabled WHERE id = :id;")
                .single(call().bind("enabled", enabled).bind("id", id))
                .update()
                .changed();
    }

    public void updatePublicPagesEnabled(int id, boolean enabled) {
        query("UPDATE station SET public_pages_enabled = :enabled WHERE id = :id;")
                .single(call().bind("enabled", enabled).bind("id", id))
                .update()
                .changed();
    }

    public void updatePublicWaitlistEnabled(int id, boolean enabled) {
        query("UPDATE station SET public_waitlist_enabled = :enabled WHERE id = :id;")
                .single(call().bind("enabled", enabled).bind("id", id))
                .update()
                .changed();
    }

    public void updatePublicBlogEnabled(int id, boolean enabled) {
        query("UPDATE station SET public_blog_enabled = :enabled WHERE id = :id;")
                .single(call().bind("enabled", enabled).bind("id", id))
                .update()
                .changed();
    }

    public boolean updateFederationPrivateKey(int id, String privateKey) {
        return query("UPDATE station SET federation_private_key = :key WHERE id = :id;")
                .single(call().bind("key", privateKey).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Writes the opt-in geolocation block. All fields nullable; clearing them removes the
     * station from the discovery map and lending-distance results.
     */
    public void updateLocation(
            int id,
            String addressLine,
            String postalCode,
            String city,
            String country,
            BigDecimal latitude,
            BigDecimal longitude) {
        query("""
                UPDATE station
                SET address_line = :address_line,
                    postal_code  = :postal_code,
                    city         = :city,
                    country      = :country,
                    latitude     = :latitude,
                    longitude    = :longitude
                WHERE id = :id;""")
                .single(call().bind("address_line", addressLine)
                        .bind("postal_code", postalCode)
                        .bind("city", city)
                        .bind("country", country)
                        .bind("latitude", latitude)
                        .bind("longitude", longitude)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Returns station id + distance pairs for stations within the given radius of the
     * origin, sorted ascending. Honors the partial coordinate index via a bounding-box
     * pre-filter; calls {@code haversine_km} for the precise distance.
     */
    public List<StationDistance> findStationsWithinRadius(BigDecimal originLat, BigDecimal originLon, double radiusKm) {
        return query("""
                WITH bbox AS (
                    SELECT :lat::NUMERIC AS lat0,
                           :lon::NUMERIC AS lon0,
                           :lat::NUMERIC - (:radius_km::NUMERIC / 111.0) AS min_lat,
                           :lat::NUMERIC + (:radius_km::NUMERIC / 111.0) AS max_lat,
                           :lon::NUMERIC - (:radius_km::NUMERIC / (111.0 * cos(radians(:lat::NUMERIC)))) AS min_lon,
                           :lon::NUMERIC + (:radius_km::NUMERIC / (111.0 * cos(radians(:lat::NUMERIC)))) AS max_lon
                ), candidates AS (
                    SELECT s.id,
                           haversine_km(bbox.lat0, bbox.lon0, s.latitude, s.longitude) AS distance_km
                    FROM station s, bbox
                    WHERE s.latitude  IS NOT NULL
                      AND s.longitude IS NOT NULL
                      AND s.latitude  BETWEEN bbox.min_lat AND bbox.max_lat
                      AND s.longitude BETWEEN bbox.min_lon AND bbox.max_lon
                )
                SELECT id, distance_km
                FROM candidates
                WHERE distance_km <= :radius_km::NUMERIC
                ORDER BY distance_km;""")
                .single(call().bind("lat", originLat).bind("lon", originLon).bind("radius_km", radiusKm))
                .map(row -> new StationDistance(row.getInt("id"), row.getDouble("distance_km")))
                .all();
    }

    public void updateThemeSettings(
            int id,
            String defaultTheme,
            boolean allowUserTheme,
            String customThemeColors,
            ThemeFeel defaultFeel,
            boolean allowUserFeel) {
        query("""
                UPDATE station
                SET
                    default_theme       = :default_theme,
                    allow_user_theme    = :allow_user_theme,
                    custom_theme_colors = :custom_theme_colors::JSONB,
                    default_feel        = :default_feel,
                    allow_user_feel     = :allow_user_feel
                WHERE id = :id;""")
                .single(call().bind("id", id)
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
        return query("UPDATE station SET owner_member_id = :owner WHERE id = :id;")
                .single(call().bind("owner", ownerMemberId).bind("id", stationId))
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
        boolean removed = SqlSupport.deleteById("station", id);
        if (removed) {
            sweepOrphanedAccounts();
        }
        return removed;
    }

    /**
     * Deletes accounts that are no longer connected to any station member and are not instance
     * authorities. Runs after a station is removed so that accounts which only existed because of
     * that station (typically blank-email applicants carried in through the waitlist or a
     * cross-instance transfer) are not left as ghost data. Administrators are always preserved
     * because they need to be able to administer the instance with no station of their own.
     *
     * <p>Also called once at startup so that accounts left behind by a failed transfer that
     * happened on an older build (where the on-delete sweep did not yet exist) get cleaned
     * up the next time the backend boots.
     */
    public void sweepOrphanedAccounts() {
        query("""
                DELETE FROM account
                 WHERE instance_user_type = 'USER'
                   AND id NOT IN (SELECT account_id FROM station_member WHERE account_id IS NOT NULL);""").single().delete();
    }

    /**
     * Returns the setup wizard completion timestamp for a station, or empty while the wizard has
     * not been finished yet.
     *
     * @param stationId the station ID
     * @return the timestamp at which an administrator marked setup complete, or empty if still pending
     */
    public Optional<Instant> findSetupCompletedAt(int stationId) {
        return query("SELECT setup_completed_at FROM station WHERE id = :id;")
                .single(call().bind("id", stationId))
                .map(row -> row.get("setup_completed_at", INSTANT_TIMESTAMP))
                .first();
    }

    /**
     * Marks the station setup wizard as complete by stamping {@code setup_completed_at = now()}.
     * Only updates rows where the column is still {@code NULL}, so calling twice does not overwrite
     * the first completion timestamp.
     *
     * @param stationId the station ID
     * @return {@code true} if a row was updated by this call, {@code false} if setup was already
     *         marked complete previously (or no station matched)
     */
    public boolean markSetupComplete(int stationId) {
        return query("""
                UPDATE station
                SET setup_completed_at = now()
                WHERE id = :id
                  AND setup_completed_at IS NULL;
                """).single(call().bind("id", stationId)).update().changed();
    }

    /**
     * Retrieves the logo for a station.
     *
     * @param id the station ID
     * @return the logo data and content type, or empty if no logo is set
     */
    public Optional<StationLogo> findLogo(int id) {
        return query("SELECT logo, logo_content_type FROM station WHERE id = :id AND logo IS NOT NULL;")
                .single(call().bind("id", id))
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
        return query("UPDATE station SET logo = :logo, logo_content_type = :content_type WHERE id = :id;")
                .single(call().bind("logo", logo)
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
        return query("UPDATE station SET logo = NULL, logo_content_type = NULL WHERE id = :id;")
                .single(call().bind("id", id))
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
        return Set.copyOf(query("SELECT module FROM station_disabled_module WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getEnum("module", StationModule.class))
                .all());
    }

    /**
     * Replaces all disabled modules for a station with the given set.
     */
    public void setDisabledModules(int stationId, Set<StationModule> modules) {
        query("DELETE FROM station_disabled_module WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete();
        for (StationModule module : modules) {
            query("""
                    INSERT
                    INTO
                        station_disabled_module(station_id, module)
                    VALUES
                        (:station_id, :module)
                    ON CONFLICT DO NOTHING;""")
                    .single(call().bind("station_id", stationId).bind("module", module))
                    .insert();
        }
    }

    /**
     * Updates the UUID of a station (used during import to preserve the original UUID).
     */
    public void updateUid(int id, UUID uid) {
        query("UPDATE station SET uid = :uid::UUID WHERE id = :id;")
                .single(call().bind("uid", uid, StandardValueConverter.UUID_STRING)
                        .bind("id", id))
                .update();
    }

    /**
     * Updates the discovery settings for a station.
     */
    public boolean updateDiscoverySettings(int id, DiscoveryVisibility visibility, String description, boolean showKb) {
        return query("""
                UPDATE station
                SET
                    discovery_visibility  = :visibility,
                    discovery_description = :description,
                    discovery_show_kb     = :show_kb
                WHERE id = :id;""")
                .single(call().bind("id", id)
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
        return query("""
                SELECT
                    %s
                FROM
                    station
                WHERE id != :exclude_id
                  AND ( public_calendar_enabled OR public_kb_mode != 'OFF' OR public_pages_enabled )
                ORDER BY name;""", STATION_COLUMNS)
                .single(call().bind("exclude_id", excludeStationId))
                .map(Station.map())
                .all();
    }

    public List<Station> findDiscoverable(int excludeStationId, DiscoveryVisibility visA, DiscoveryVisibility visB) {
        return query("""
                SELECT
                    %s
                FROM
                    station
                WHERE id != :exclude_id
                  AND discovery_visibility IN (:vis_a, :vis_b)
                ORDER BY name;""", STATION_COLUMNS)
                .single(call().bind("exclude_id", excludeStationId)
                        .bind("vis_a", visA)
                        .bind("vis_b", visB))
                .map(Station.map())
                .all();
    }

    /**
     * Returns {@code true} when {@code station.read_only_for_transfer} is set, meaning the
     * source operator has created a transfer token for this station and no fresh uploads must
     * land while the destination instance is pulling the data over. Cached for 30 seconds so
     * the per-upload check doesn't hit the database on every call; {@link #markReadOnlyForTransfer}
     * and {@link #clearReadOnlyForTransfer} actively invalidate so the flip is immediate.
     */
    public boolean isReadOnlyForTransfer(int stationId) {
        Boolean cached = readOnlyCache.getIfPresent(stationId);
        if (cached != null) return cached;
        boolean value = query("SELECT read_only_for_transfer FROM station WHERE id = :id;")
                .single(call().bind("id", stationId))
                .map(row -> row.getBoolean("read_only_for_transfer"))
                .first()
                .orElse(false);
        readOnlyCache.put(stationId, value);
        return value;
    }

    /**
     * Flips the flag on; invalidates the cache so the next read sees the change immediately.
     */
    public void markReadOnlyForTransfer(int stationId) {
        query("UPDATE station SET read_only_for_transfer = TRUE WHERE id = :id;")
                .single(call().bind("id", stationId))
                .update();
        readOnlyCache.invalidate(stationId);
    }

    /**
     * Clears the flag and invalidates the cache.
     */
    public void clearReadOnlyForTransfer(int stationId) {
        query("UPDATE station SET read_only_for_transfer = FALSE WHERE id = :id;")
                .single(call().bind("id", stationId))
                .update();
        readOnlyCache.invalidate(stationId);
    }

    /**
     * @param stationId  internal station id
     * @param distanceKm great-circle distance in km from the origin point
     */
    public record StationDistance(int stationId, double distanceKm) {}

    /**
     * Holds the binary data and content type of a station logo.
     *
     * @param data        the raw image bytes
     * @param contentType the MIME content type
     */
    public record StationLogo(byte[] data, String contentType) {}
}
