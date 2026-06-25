/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.page.entity.PageFileFolder;
import dev.chojo.ember.feature.page.entity.PageFileTag;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class PageFileMetaRepository {

    // --- Folders ---

    public PageFileFolder createFolder(int stationId, Integer parentId, String name, int sortOrder) {
        return query("""
                INSERT INTO page_file_folder(station_id, parent_id, name, sort_order)
                VALUES (:station_id, :parent_id, :name, :sort_order)
                RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("parent_id", parentId)
                        .bind("name", name)
                        .bind("sort_order", sortOrder))
                .map(PageFileFolder.map())
                .first()
                .orElseThrow();
    }

    public Optional<PageFileFolder> findFolder(int folderId) {
        return query("SELECT * FROM page_file_folder WHERE id = :id;")
                .single(call().bind("id", folderId))
                .map(PageFileFolder.map())
                .first();
    }

    public List<PageFileFolder> findFoldersByStation(int stationId) {
        return query("""
                SELECT * FROM page_file_folder
                WHERE station_id = :station_id
                ORDER BY parent_id NULLS FIRST, sort_order, name;""")
                .single(call().bind("station_id", stationId))
                .map(PageFileFolder.map())
                .all();
    }

    public boolean updateFolder(int folderId, Integer parentId, String name, int sortOrder) {
        return query("""
                UPDATE page_file_folder
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
        return query("DELETE FROM page_file_folder WHERE id = :id;")
                .single(call().bind("id", folderId))
                .delete()
                .changed();
    }

    public boolean moveFileToFolder(int fileId, Integer folderId) {
        return query("UPDATE page_file SET folder_id = :folder_id WHERE id = :id;")
                .single(call().bind("id", fileId).bind("folder_id", folderId))
                .update()
                .changed();
    }

    // --- Tags ---

    public PageFileTag createTag(int stationId, String name, String color) {
        return query("""
                INSERT INTO page_file_tag(station_id, name, color)
                VALUES (:station_id, :name, :color)
                RETURNING *;""")
                .single(call().bind("station_id", stationId).bind("name", name).bind("color", color))
                .map(PageFileTag.map())
                .first()
                .orElseThrow();
    }

    public Optional<PageFileTag> findTag(int tagId) {
        return query("SELECT * FROM page_file_tag WHERE id = :id;")
                .single(call().bind("id", tagId))
                .map(PageFileTag.map())
                .first();
    }

    public List<PageFileTag> findTagsByStation(int stationId) {
        return query("SELECT * FROM page_file_tag WHERE station_id = :station_id ORDER BY name;")
                .single(call().bind("station_id", stationId))
                .map(PageFileTag.map())
                .all();
    }

    public boolean updateTag(int tagId, String name, String color) {
        return query("UPDATE page_file_tag SET name = :name, color = :color WHERE id = :id;")
                .single(call().bind("id", tagId).bind("name", name).bind("color", color))
                .update()
                .changed();
    }

    public boolean deleteTag(int tagId) {
        return query("DELETE FROM page_file_tag WHERE id = :id;")
                .single(call().bind("id", tagId))
                .delete()
                .changed();
    }

    public void assignTag(int fileId, int tagId) {
        query("""
                INSERT INTO page_file_tag_assignment(file_id, tag_id)
                VALUES (:file_id, :tag_id)
                ON CONFLICT DO NOTHING;""").single(call().bind("file_id", fileId).bind("tag_id", tagId)).insert();
    }

    public boolean unassignTag(int fileId, int tagId) {
        return query("DELETE FROM page_file_tag_assignment WHERE file_id = :file_id AND tag_id = :tag_id;")
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
                FROM page_file_tag_assignment
                WHERE file_id = ANY(:file_ids);""")
                .single(call().bind("file_ids", fileIds, PostgreSqlTypes.INTEGER))
                .map(row -> {
                    out.computeIfAbsent(row.getInt("file_id"), k -> new HashSet<>())
                            .add(row.getInt("tag_id"));
                    return null;
                })
                .all();
        return out;
    }
}
