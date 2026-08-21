/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.media.entity.StationFileFolder;
import dev.chojo.ember.feature.media.entity.StationFileTag;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The folders and tags a station organises its media library with.
 */
@Singleton
public class MediaMetaRepository {

    private static final String FOLDER_COLUMNS = "id, station_id, parent_id, name, sort_order, created_at";
    private static final String TAG_COLUMNS = "id, station_id, name, color";

    // --- Folders ---

    public StationFileFolder createFolder(int stationId, Integer parentId, String name, int sortOrder) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO station_file_folder(station_id, parent_id, name, sort_order)
                VALUES (:station_id, :parent_id, :name, :sort_order)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("parent_id", parentId)
                        .bind("name", name)
                        .bind("sort_order", sortOrder),
                StationFileFolder.map(),
                FOLDER_COLUMNS);
    }

    public Optional<StationFileFolder> findFolder(int folderId) {
        return SqlSupport.findById("station_file_folder", FOLDER_COLUMNS, folderId, StationFileFolder.map());
    }

    public List<StationFileFolder> findFoldersByStation(int stationId) {
        return query("""
                SELECT %s FROM station_file_folder
                WHERE station_id = :station_id
                ORDER BY parent_id NULLS FIRST, sort_order, name;""", FOLDER_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationFileFolder.map())
                .all();
    }

    public boolean updateFolder(int folderId, Integer parentId, String name, int sortOrder) {
        return query("""
                UPDATE station_file_folder
                SET parent_id = :parent_id, name = :name, sort_order = :sort_order
                WHERE id = :id;""")
                .single(call().bind("id", folderId)
                        .bind("parent_id", parentId)
                        .bind("name", name)
                        .bind("sort_order", sortOrder))
                .update()
                .changed();
    }

    public boolean deleteFolder(int folderId) {
        return SqlSupport.deleteById("station_file_folder", folderId);
    }

    public boolean moveFileToFolder(int fileId, Integer folderId) {
        return query("UPDATE station_file SET folder_id = :folder_id WHERE id = :id;")
                .single(call().bind("id", fileId).bind("folder_id", folderId))
                .update()
                .changed();
    }

    // --- Tags ---

    public StationFileTag createTag(int stationId, String name, String color) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO station_file_tag(station_id, name, color)
                VALUES (:station_id, :name, :color)
                RETURNING %s;""",
                call().bind("station_id", stationId).bind("name", name).bind("color", color),
                StationFileTag.map(),
                TAG_COLUMNS);
    }

    public Optional<StationFileTag> findTag(int tagId) {
        return SqlSupport.findById("station_file_tag", TAG_COLUMNS, tagId, StationFileTag.map());
    }

    public List<StationFileTag> findTagsByStation(int stationId) {
        return query("SELECT %s FROM station_file_tag WHERE station_id = :station_id ORDER BY name;", TAG_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationFileTag.map())
                .all();
    }

    public boolean updateTag(int tagId, String name, String color) {
        return query("UPDATE station_file_tag SET name = :name, color = :color WHERE id = :id;")
                .single(call().bind("id", tagId).bind("name", name).bind("color", color))
                .update()
                .changed();
    }

    public boolean deleteTag(int tagId) {
        return SqlSupport.deleteById("station_file_tag", tagId);
    }

    public void assignTag(int fileId, int tagId) {
        query("""
                INSERT INTO station_file_tag_assignment(file_id, tag_id)
                VALUES (:file_id, :tag_id)
                ON CONFLICT DO NOTHING;""").single(call().bind("file_id", fileId).bind("tag_id", tagId)).insert();
    }

    public boolean unassignTag(int fileId, int tagId) {
        return query("DELETE FROM station_file_tag_assignment WHERE file_id = :file_id AND tag_id = :tag_id;")
                .single(call().bind("file_id", fileId).bind("tag_id", tagId))
                .delete()
                .changed();
    }

    /**
     * Returns the set of tag ids for each file id in the provided collection.
     */
    public Map<Integer, Set<Integer>> findTagAssignments(List<Integer> fileIds) {
        Map<Integer, Set<Integer>> out = new HashMap<>();
        if (fileIds == null || fileIds.isEmpty()) return out;
        query("""
                SELECT file_id, tag_id
                FROM station_file_tag_assignment
                WHERE file_id = ANY(:file_ids);""")
                .single(call().bind("file_ids", fileIds, PostgreSqlTypes.INTEGER))
                .map(row -> {
                    out.computeIfAbsent(row.getInt("file_id"), _ -> new HashSet<>())
                            .add(row.getInt("tag_id"));
                    return null;
                })
                .all();
        return out;
    }
}
