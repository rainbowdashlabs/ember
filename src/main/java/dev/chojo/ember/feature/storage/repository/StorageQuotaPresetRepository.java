/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.storage.entity.StorageQuotaPreset;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for storage quota presets.
 */
@Singleton
public class StorageQuotaPresetRepository {
    private static final String PRESET_COLUMNS = "id, name, total, kb, board, images, pages, per_file, per_image";

    public List<StorageQuotaPreset> findAll() {
        return query("SELECT %s FROM storage_quota_preset ORDER BY name;".formatted(PRESET_COLUMNS))
                .single(Call.of())
                .map(StorageQuotaPreset.map())
                .all();
    }

    public Optional<StorageQuotaPreset> findById(int id) {
        return query("SELECT %s FROM storage_quota_preset WHERE id = :id;".formatted(PRESET_COLUMNS))
                .single(Call.of().bind("id", id))
                .map(StorageQuotaPreset.map())
                .first();
    }

    public StorageQuotaPreset create(
            String name, long total, long kb, long board, long images, long pages, long perFile, long perImage) {
        return query("""
                INSERT INTO storage_quota_preset (name, total, kb, board, images, pages, per_file, per_image)
                VALUES (:name, :total, :kb, :board, :images, :pages, :per_file, :per_image)
                RETURNING %s;
                """.formatted(PRESET_COLUMNS))
                .single(Call.of()
                        .bind("name", name)
                        .bind("total", total)
                        .bind("kb", kb)
                        .bind("board", board)
                        .bind("images", images)
                        .bind("pages", pages)
                        .bind("per_file", perFile)
                        .bind("per_image", perImage))
                .map(StorageQuotaPreset.map())
                .first()
                .orElseThrow();
    }

    public StorageQuotaPreset update(
            int id,
            String name,
            long total,
            long kb,
            long board,
            long images,
            long pages,
            long perFile,
            long perImage) {
        return query("""
                UPDATE storage_quota_preset
                SET name = :name, total = :total, kb = :kb, board = :board,
                    images = :images, pages = :pages, per_file = :per_file, per_image = :per_image
                WHERE id = :id
                RETURNING %s;
                """.formatted(PRESET_COLUMNS))
                .single(Call.of()
                        .bind("id", id)
                        .bind("name", name)
                        .bind("total", total)
                        .bind("kb", kb)
                        .bind("board", board)
                        .bind("images", images)
                        .bind("pages", pages)
                        .bind("per_file", perFile)
                        .bind("per_image", perImage))
                .map(StorageQuotaPreset.map())
                .first()
                .orElseThrow();
    }

    public void delete(int id) {
        query("DELETE FROM storage_quota_preset WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete();
    }

    /**
     * Applies a preset's quota values to a station.
     * Copies the preset values into the station's storage quota columns.
     */
    public void applyToStation(int presetId, int stationId) {
        query("""
                UPDATE station
                SET storage_quota_bytes = p.total,
                    storage_quota_kb_bytes = p.kb,
                    storage_quota_board_bytes = p.board,
                    storage_quota_images_bytes = p.images,
                    storage_quota_pages_bytes = p.pages,
                    storage_per_file_bytes = p.per_file,
                    storage_per_image_bytes = p.per_image,
                    storage_preset_id = p.id
                FROM storage_quota_preset p
                WHERE station.id = :station_id AND p.id = :preset_id;
                """)
                .single(Call.of().bind("station_id", stationId).bind("preset_id", presetId))
                .update();
    }

    /**
     * Returns a map of station ID → preset name for all stations with an assigned preset.
     */
    public Map<Integer, String> findStationPresetNames() {
        var result = new HashMap<Integer, String>();
        query("""
                SELECT s.id AS station_id, p.name AS preset_name
                FROM station s
                JOIN storage_quota_preset p ON p.id = s.storage_preset_id;
                """)
                .single(Call.of())
                .map(row -> Map.entry(row.getInt("station_id"), row.getString("preset_name")))
                .all()
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    /**
     * Resets a station's quota overrides to NULL (use instance defaults).
     */
    public void resetStationQuotas(int stationId) {
        query("""
                UPDATE station
                SET storage_quota_bytes = NULL,
                    storage_quota_kb_bytes = NULL,
                    storage_quota_board_bytes = NULL,
                    storage_quota_images_bytes = NULL,
                    storage_quota_pages_bytes = NULL,
                    storage_per_file_bytes = NULL,
                    storage_per_image_bytes = NULL,
                    storage_preset_id = NULL
                WHERE id = :station_id;
                """).single(Call.of().bind("station_id", stationId)).update();
    }
}
