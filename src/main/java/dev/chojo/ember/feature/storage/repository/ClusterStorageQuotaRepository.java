/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.storage.entity.ClusterQuotaDefaults;
import dev.chojo.ember.feature.storage.entity.ClusterStationQuota;
import dev.chojo.ember.feature.storage.entity.ClusterStorageQuotaPreset;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The room a cluster hands out: its defaults, its tiers, and what each of its stations was granted.
 *
 * <p>Everything here is the cluster's own. The instance keeps its numbers on the station row and neither
 * writes the other's, which is what lets the pool add up what the cluster actually promised rather than
 * whatever was last written to a shared column.
 */
@Singleton
public class ClusterStorageQuotaRepository {

    private static final String DEFAULT_COLUMNS = """
            id, default_quota_bytes, default_quota_kb_bytes, default_quota_board_bytes, default_quota_images_bytes,
            default_quota_pages_bytes, default_per_file_bytes, default_per_image_bytes""";

    private static final String PRESET_COLUMNS =
            "id, cluster_id, name, total, kb, board, images, pages, per_file, per_image";

    private static final String GRANT_COLUMNS = """
            station_id, cluster_id, quota_bytes, quota_kb_bytes, quota_board_bytes, quota_images_bytes,
            quota_pages_bytes, per_file_bytes, per_image_bytes, preset_id""";

    // -- Defaults --

    /**
     * What the cluster gives a station it granted nothing of its own.
     *
     * @param clusterId the cluster
     * @return its defaults, all of them null when it has set none and when there is no such cluster
     */
    public ClusterQuotaDefaults findDefaults(int clusterId) {
        return query("SELECT %s FROM cluster WHERE id = :cluster_id;", DEFAULT_COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterQuotaDefaults.map())
                .first()
                .orElseGet(() -> ClusterQuotaDefaults.none(clusterId));
    }

    /**
     * The defaults of the cluster one station answers to.
     *
     * @param stationId the station
     * @return its cluster's defaults, empty when it answers to nobody
     */
    public Optional<ClusterQuotaDefaults> findDefaultsForStation(int stationId) {
        return query("""
                        SELECT %s FROM cluster c
                        JOIN station s ON s.cluster_id = c.id OR c.home_station_id = s.id
                        WHERE s.id = :station_id;""", SqlSupport.alias("c", DEFAULT_COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(ClusterQuotaDefaults.map())
                .first();
    }

    public boolean setDefaults(ClusterQuotaDefaults defaults) {
        return query("""
                UPDATE cluster
                SET default_quota_bytes        = :total,
                    default_quota_kb_bytes     = :kb,
                    default_quota_board_bytes  = :board,
                    default_quota_images_bytes = :images,
                    default_quota_pages_bytes  = :pages,
                    default_per_file_bytes     = :per_file,
                    default_per_image_bytes    = :per_image
                WHERE id = :cluster_id;""")
                .single(call().bind("cluster_id", defaults.clusterId())
                        .bind("total", defaults.quotaBytes())
                        .bind("kb", defaults.quotaKbBytes())
                        .bind("board", defaults.quotaBoardBytes())
                        .bind("images", defaults.quotaImagesBytes())
                        .bind("pages", defaults.quotaPagesBytes())
                        .bind("per_file", defaults.perFileBytes())
                        .bind("per_image", defaults.perImageBytes()))
                .update()
                .changed();
    }

    // -- Presets --

    public List<ClusterStorageQuotaPreset> findPresets(int clusterId) {
        return query("""
                SELECT %s FROM cluster_storage_quota_preset
                WHERE cluster_id = :cluster_id
                ORDER BY name;""", PRESET_COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterStorageQuotaPreset.map())
                .all();
    }

    public Optional<ClusterStorageQuotaPreset> findPreset(int presetId) {
        return SqlSupport.findById(
                "cluster_storage_quota_preset", PRESET_COLUMNS, presetId, ClusterStorageQuotaPreset.map());
    }

    public ClusterStorageQuotaPreset createPreset(
            int clusterId,
            String name,
            long total,
            long kb,
            long board,
            long images,
            long pages,
            long perFile,
            long perImage) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    cluster_storage_quota_preset(cluster_id, name, total, kb, board, images, pages, per_file,
                                                 per_image)
                VALUES
                    (:cluster_id, :name, :total, :kb, :board, :images, :pages, :per_file, :per_image)
                RETURNING %s;""",
                call().bind("cluster_id", clusterId)
                        .bind("name", name)
                        .bind("total", total)
                        .bind("kb", kb)
                        .bind("board", board)
                        .bind("images", images)
                        .bind("pages", pages)
                        .bind("per_file", perFile)
                        .bind("per_image", perImage),
                ClusterStorageQuotaPreset.map(),
                PRESET_COLUMNS);
    }

    public boolean updatePreset(
            int presetId,
            String name,
            long total,
            long kb,
            long board,
            long images,
            long pages,
            long perFile,
            long perImage) {
        return query("""
                UPDATE cluster_storage_quota_preset
                SET name      = :name,
                    total     = :total,
                    kb        = :kb,
                    board     = :board,
                    images    = :images,
                    pages     = :pages,
                    per_file  = :per_file,
                    per_image = :per_image
                WHERE id = :id;""")
                .single(call().bind("id", presetId)
                        .bind("name", name)
                        .bind("total", total)
                        .bind("kb", kb)
                        .bind("board", board)
                        .bind("images", images)
                        .bind("pages", pages)
                        .bind("per_file", perFile)
                        .bind("per_image", perImage))
                .update()
                .changed();
    }

    public boolean deletePreset(int presetId) {
        return SqlSupport.deleteById("cluster_storage_quota_preset", presetId);
    }

    // -- Grants --

    /**
     * What one station was granted.
     *
     * @param stationId the station
     * @return the grant, empty when the cluster has granted this station nothing of its own
     */
    public Optional<ClusterStationQuota> findGrant(int stationId) {
        return query("SELECT %s FROM cluster_station_quota WHERE station_id = :station_id;", GRANT_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(ClusterStationQuota.map())
                .first();
    }

    /**
     * Every grant one cluster has made, the home station's included.
     *
     * @param clusterId the cluster
     * @return one row per station it has granted something to
     */
    public List<ClusterStationQuota> findGrants(int clusterId) {
        return query("SELECT %s FROM cluster_station_quota WHERE cluster_id = :cluster_id;", GRANT_COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterStationQuota.map())
                .all();
    }

    /**
     * Every station a cluster has, with what it was granted.
     *
     * <p>The home station is in the list, because a cluster's own files are kept there and room for them is
     * promised out of the same pool as everybody else's. A station that has been granted nothing is still
     * listed, with nothing against its name.
     *
     * @param clusterId the cluster
     * @return one row per station, in name order
     */
    public List<GrantedStation> findStationsWithGrants(int clusterId) {
        return query("""
                SELECT s.id, s.uid, s.name, q.quota_bytes, q.preset_id
                FROM station s
                    LEFT JOIN cluster_station_quota q ON q.station_id = s.id
                WHERE s.cluster_id = :cluster_id
                   OR s.id = (SELECT home_station_id FROM cluster WHERE id = :cluster_id)
                ORDER BY s.name;""")
                .single(call().bind("cluster_id", clusterId))
                .map(row -> new GrantedStation(
                        row.getInt("id"),
                        row.get("uid", StandardValueConverter.UUID_STRING),
                        row.getString("name"),
                        row.getObject("quota_bytes", Long.class),
                        row.getObject("preset_id", Integer.class)))
                .all();
    }

    /**
     * Writes what a station was granted, replacing whatever it held before.
     *
     * @param grant the whole of the grant, nulls included, because a dimension left out is a dimension handed
     *              back to the cluster's defaults
     */
    public void setGrant(ClusterStationQuota grant) {
        query("""
                INSERT
                INTO
                    cluster_station_quota(station_id, cluster_id, quota_bytes, quota_kb_bytes, quota_board_bytes,
                                          quota_images_bytes, quota_pages_bytes, per_file_bytes, per_image_bytes,
                                          preset_id)
                VALUES
                    (:station_id, :cluster_id, :total, :kb, :board, :images, :pages, :per_file, :per_image,
                     :preset_id)
                ON CONFLICT (station_id) DO UPDATE
                    SET cluster_id         = EXCLUDED.cluster_id,
                        quota_bytes        = EXCLUDED.quota_bytes,
                        quota_kb_bytes     = EXCLUDED.quota_kb_bytes,
                        quota_board_bytes  = EXCLUDED.quota_board_bytes,
                        quota_images_bytes = EXCLUDED.quota_images_bytes,
                        quota_pages_bytes  = EXCLUDED.quota_pages_bytes,
                        per_file_bytes     = EXCLUDED.per_file_bytes,
                        per_image_bytes    = EXCLUDED.per_image_bytes,
                        preset_id          = EXCLUDED.preset_id;""")
                .single(call().bind("station_id", grant.stationId())
                        .bind("cluster_id", grant.clusterId())
                        .bind("total", grant.quotaBytes())
                        .bind("kb", grant.quotaKbBytes())
                        .bind("board", grant.quotaBoardBytes())
                        .bind("images", grant.quotaImagesBytes())
                        .bind("pages", grant.quotaPagesBytes())
                        .bind("per_file", grant.perFileBytes())
                        .bind("per_image", grant.perImageBytes())
                        .bind("preset_id", grant.presetId()))
                .update();
    }

    /**
     * Puts several stations on one tier at once, copying the tier's numbers into each grant.
     *
     * @param presetId   the tier
     * @param clusterId  the cluster it belongs to
     * @param stationIds the stations to put on it
     */
    public void applyPreset(int presetId, int clusterId, Collection<Integer> stationIds) {
        for (int stationId : stationIds) {
            query("""
                    INSERT
                    INTO
                        cluster_station_quota(station_id, cluster_id, quota_bytes, quota_kb_bytes,
                                              quota_board_bytes, quota_images_bytes, quota_pages_bytes,
                                              per_file_bytes, per_image_bytes, preset_id)
                    SELECT
                        :station_id, :cluster_id, p.total, p.kb, p.board, p.images, p.pages, p.per_file,
                        p.per_image, p.id
                    FROM cluster_storage_quota_preset p
                    WHERE p.id = :preset_id
                    ON CONFLICT (station_id) DO UPDATE
                        SET cluster_id         = EXCLUDED.cluster_id,
                            quota_bytes        = EXCLUDED.quota_bytes,
                            quota_kb_bytes     = EXCLUDED.quota_kb_bytes,
                            quota_board_bytes  = EXCLUDED.quota_board_bytes,
                            quota_images_bytes = EXCLUDED.quota_images_bytes,
                            quota_pages_bytes  = EXCLUDED.quota_pages_bytes,
                            per_file_bytes     = EXCLUDED.per_file_bytes,
                            per_image_bytes    = EXCLUDED.per_image_bytes,
                            preset_id          = EXCLUDED.preset_id;""")
                    .single(call().bind("station_id", stationId)
                            .bind("cluster_id", clusterId)
                            .bind("preset_id", presetId))
                    .update();
        }
    }

    /**
     * Takes the grant away, which is what a release does and what handing a station back to the cluster's
     * defaults does.
     *
     * @param stationId the station
     * @return whether there was anything to take away
     */
    public boolean deleteGrant(int stationId) {
        return query("DELETE FROM cluster_station_quota WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete()
                .changed();
    }

    /**
     * What the cluster has promised in total, which is what the pool is measured against.
     *
     * <p>A station whose grant names no total is not counted: nothing was promised out of the pool for it, and
     * what it may use follows from the cluster's defaults instead.
     *
     * @param clusterId      the cluster
     * @param excludeStation a station to leave out, for weighing a grant that is about to replace an old one,
     *                       or {@code 0} to count every one of them
     * @return the sum of the totals granted, in bytes
     */
    public long sumGrantedTotals(int clusterId, int excludeStation) {
        return query("""
                SELECT COALESCE(SUM(quota_bytes), 0) AS granted
                FROM cluster_station_quota
                WHERE cluster_id = :cluster_id
                  AND station_id <> :exclude;""")
                .single(call().bind("cluster_id", clusterId).bind("exclude", excludeStation))
                .map(row -> row.getLong("granted"))
                .first()
                .orElse(0L);
    }

    /**
     * One of a cluster's stations and what it was promised.
     *
     * @param quotaBytes the total granted, or {@code null} when the cluster granted this station nothing
     * @param presetId   the tier it was put on, or {@code null} when its numbers were set by hand
     */
    public record GrantedStation(int stationId, UUID uid, String name, Long quotaBytes, Integer presetId) {}
}
