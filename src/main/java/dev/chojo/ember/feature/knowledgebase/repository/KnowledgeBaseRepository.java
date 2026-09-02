/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.repository;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.knowledgebase.entity.ConversionStatus;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessGrant;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileVersion;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.KbSearchResult;
import dev.chojo.ember.feature.knowledgebase.entity.KbTag;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.util.sql.FullTextSearch;
import dev.chojo.ember.util.sql.SqlSupport;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class KnowledgeBaseRepository {

    /**
     * Returns {@code true} if the station has at least one knowledge-base file or folder. Used by
     * the setup wizard's status endpoint to mark the optional "knowledge base seed" step complete.
     */
    public boolean existsForStation(int stationId) {
        return SqlSupport.exists("""
                SELECT 1
                FROM (
                    SELECT 1 FROM kb_folder WHERE station_id = :station_id
                    UNION ALL
                    SELECT 1 FROM kb_file WHERE station_id = :station_id
                ) any_kb_entry
                LIMIT 1;""", call().bind("station_id", stationId));
    }

    private static final String FOLDER_COLUMNS_BARE =
            "id, station_id, parent_id, name, description, icon_url, position, created_by, created_at, updated_at, restriction_mode";
    private static final String FOLDER_COLUMNS = SqlSupport.alias("fo", FOLDER_COLUMNS_BARE);
    private static final String FOLDER_RESTRICTED = RestrictionSql.restrictedFlag(RestrictionType.KB_FOLDER, "fo.id");
    private static final String FILE_COLUMNS_BARE =
            "id, station_id, folder_id, name, description, file_type, mime_type, file_size, icon_url, youtube_url, link_url, position, created_by, created_at, updated_at, source_file_id, source_station_id, restriction_mode, conversion_status, content_mode, container_id";
    private static final String FILE_COLUMNS = SqlSupport.alias("f", FILE_COLUMNS_BARE);
    private static final String FILE_RESTRICTED = RestrictionSql.restrictedFlag(RestrictionType.KB_FILE, "f.id");
    private static final String FILE_VERSION_COLUMNS = "id, file_id, patch, is_full, version, created_by, created_at";
    private static final String RESTRICTION_COLUMNS =
            "id, folder_id, file_id, user_type, group_id, tag_id, member_id, level";
    private static final String TAG_COLUMNS = "id, station_id, name";
    private static final String TAG_COLUMNS_ALIASED = SqlSupport.alias("t", TAG_COLUMNS);
    private static final String SNIPPET_SOURCE =
            "COALESCE(fc.text_content, f.name || ' ' || COALESCE(f.description, ''))";
    private static final String SNIPPET_OPTIONS = "MaxWords=30, MinWords=10, StartSel=<mark>, StopSel=</mark>";

    /**
     * Folders directly under {@code parentId}, or the root folders when it is {@code null}.
     *
     * <p>A null parent means "match {@code IS NULL}" here, not "no filter", so the predicate is
     * chosen rather than left out - {@link WhereBuilder} drops null-valued predicates, which would
     * widen this to every folder in the station.
     */
    public List<KbFolder> findFolders(int stationId, Integer parentId) {
        var where = parentId == null
                ? WhereBuilder.create().add("AND fo.parent_id IS NULL")
                : WhereBuilder.create().add("AND fo.parent_id = :parent_id", "parent_id", parentId);
        return query("""
                SELECT
                    %s, %s
                FROM
                    kb_folder fo
                WHERE fo.station_id = :station_id
                  %s
                ORDER BY fo.position, fo.name;""", FOLDER_COLUMNS, FOLDER_RESTRICTED, where.fragment())
                .single(where.apply(call().bind("station_id", stationId)))
                .map(KbFolder.map())
                .all();
    }

    // -- Files --

    public Optional<KbFolder> findFolderById(int id) {
        return query("SELECT %s, %s FROM kb_folder fo WHERE fo.id = :id;", FOLDER_COLUMNS, FOLDER_RESTRICTED)
                .single(call().bind("id", id))
                .map(KbFolder.map())
                .first();
    }

    public KbFolder createFolder(int stationId, Integer parentId, String name, String description, int createdBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO kb_folder AS fo(station_id, parent_id, name, description, created_by)
                VALUES (:station_id, :parent_id, :name, :description, :created_by)
                RETURNING %s, %s;""",
                call().bind("station_id", stationId)
                        .bind("parent_id", parentId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("created_by", createdBy),
                KbFolder.map(),
                FOLDER_COLUMNS,
                FOLDER_RESTRICTED);
    }

    public boolean updateFolder(int id, String name, String description, String iconUrl, int position) {
        return query("""
                UPDATE kb_folder
                        SET
                            name        = :name,
                            description = :description,
                            icon_url    = :icon_url,
                            position    = :position,
                            updated_at  = now()
                        WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("icon_url", iconUrl)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean deleteFolder(int id) {
        return SqlSupport.deleteById("kb_folder", id);
    }

    /**
     * Files directly inside {@code folderId}, or the ones at the root when it is {@code null}.
     *
     * <p>Same null-means-{@code IS NULL} handling as {@link #findFolders(int, Integer)}.
     */
    public List<KbFile> findFiles(int stationId, Integer folderId) {
        var where = folderId == null
                ? WhereBuilder.create().add("AND f.folder_id IS NULL")
                : WhereBuilder.create().add("AND f.folder_id = :folder_id", "folder_id", folderId);
        return query("""
                SELECT
                    %s, %s
                FROM
                    kb_file f
                WHERE f.station_id = :station_id
                  %s
                ORDER BY f.position, f.name;""", FILE_COLUMNS, FILE_RESTRICTED, where.fragment())
                .single(where.apply(call().bind("station_id", stationId)))
                .map(KbFile.map())
                .all();
    }

    public Optional<KbFile> findFileById(int id) {
        return query("SELECT %s, %s FROM kb_file f WHERE f.id = :id;", FILE_COLUMNS, FILE_RESTRICTED)
                .single(call().bind("id", id))
                .map(KbFile.map())
                .first();
    }

    public KbFile createFile(
            int stationId,
            Integer folderId,
            String name,
            String description,
            KbFileType fileType,
            String mimeType,
            long fileSize,
            String youtubeUrl,
            int createdBy) {
        return createFile(
                stationId, folderId, name, description, fileType, mimeType, fileSize, youtubeUrl, null, createdBy);
    }

    public KbFile createFile(
            int stationId,
            Integer folderId,
            String name,
            String description,
            KbFileType fileType,
            String mimeType,
            long fileSize,
            String youtubeUrl,
            String linkUrl,
            int createdBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO kb_file AS f(station_id, folder_id, name, description, file_type, mime_type, file_size, youtube_url, link_url, created_by)
                VALUES (:station_id, :folder_id, :name, :description, :file_type, :mime_type, :file_size, :youtube_url, :link_url, :created_by)
                RETURNING %s, %s;""",
                call().bind("station_id", stationId)
                        .bind("folder_id", folderId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("file_type", fileType.name())
                        .bind("mime_type", mimeType)
                        .bind("file_size", fileSize)
                        .bind("youtube_url", youtubeUrl)
                        .bind("link_url", linkUrl)
                        .bind("created_by", createdBy),
                KbFile.map(),
                FILE_COLUMNS,
                FILE_RESTRICTED);
    }

    // -- File Content --

    public boolean updateFile(int id, String name, String description, String iconUrl, int position) {
        return query("""
                UPDATE kb_file
                SET
                    name        = :name,
                    description = :description,
                    icon_url    = :icon_url,
                    position    = :position,
                    updated_at  = now()
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("icon_url", iconUrl)
                        .bind("position", position))
                .update()
                .changed();
    }

    public void updateConversionStatus(int fileId, ConversionStatus status) {
        query("UPDATE kb_file SET conversion_status = :status, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", fileId).bind("status", status))
                .update()
                .changed();
    }

    // -- Version History (Markdown) --

    public boolean setSourceReference(int fileId, int sourceFileId, int sourceStationId) {
        return query(
                        "UPDATE kb_file SET source_file_id = :source_file_id, source_station_id = :source_station_id WHERE id = :id;")
                .single(call().bind("id", fileId)
                        .bind("source_file_id", sourceFileId)
                        .bind("source_station_id", sourceStationId))
                .update()
                .changed();
    }

    public boolean deleteFile(int id) {
        return SqlSupport.deleteById("kb_file", id);
    }

    /**
     * Turns a plain article into one built from blocks. The existing text travels into the
     * container as a single markdown block, so nothing is parsed and nothing is lost.
     */
    public boolean setRichMode(int fileId, int containerId) {
        return query("""
                UPDATE kb_file SET content_mode = 'RICH', container_id = :container_id
                WHERE id = :id AND content_mode = 'SIMPLE' AND file_type = 'MARKDOWN';""")
                .single(call().bind("id", fileId).bind("container_id", containerId))
                .update()
                .changed();
    }

    public void storeTextContent(int fileId, String textContent) {
        query("""
                INSERT INTO kb_file_content(file_id, text_content) VALUES (:file_id, :text_content)
                ON CONFLICT (file_id) DO UPDATE SET text_content = :text_content;""")
                .single(call().bind("file_id", fileId).bind("text_content", textContent))
                .insert();
    }

    public Optional<String> readTextContent(int fileId) {
        return query("SELECT text_content FROM kb_file_content WHERE file_id = :file_id;")
                .single(call().bind("file_id", fileId))
                .map(row -> row.getString("text_content"))
                .first();
    }

    // -- Search Index --

    public List<KbFileVersion> findVersions(int fileId) {
        return query(
                        "SELECT %s FROM kb_file_version WHERE file_id = :file_id ORDER BY version DESC;",
                        FILE_VERSION_COLUMNS)
                .single(call().bind("file_id", fileId))
                .map(KbFileVersion.map())
                .all();
    }

    public Optional<KbFileVersion> findVersion(int fileId, int version) {
        return query(
                        "SELECT %s FROM kb_file_version WHERE file_id = :file_id AND version = :version;",
                        FILE_VERSION_COLUMNS)
                .single(call().bind("file_id", fileId).bind("version", version))
                .map(KbFileVersion.map())
                .first();
    }

    public int getNextVersion(int fileId) {
        return query("SELECT coalesce(max(version), 0) + 1 AS next FROM kb_file_version WHERE file_id = :file_id;")
                .single(call().bind("file_id", fileId))
                .map(row -> row.getInt("next"))
                .first()
                .orElse(1);
    }

    public KbFileVersion createVersion(int fileId, String patch, boolean isFull, int version, int createdBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO kb_file_version(file_id, patch, is_full, version, created_by)
                VALUES (:file_id, :patch, :is_full, :version, :created_by)
                RETURNING %s;""",
                call().bind("file_id", fileId)
                        .bind("patch", patch)
                        .bind("is_full", isFull)
                        .bind("version", version)
                        .bind("created_by", createdBy),
                KbFileVersion.map(),
                FILE_VERSION_COLUMNS);
    }

    public void updateSearchIndex(int fileId, String plainText, String tsConfig) {
        query("""
                INSERT
                INTO
                    kb_search_index(file_id, search_text)
                VALUES
                    (:file_id, %s)
                ON CONFLICT (file_id) DO UPDATE SET
                    search_text = excluded.search_text;""", FullTextSearch.vector(tsConfig, "text"))
                .single(call().bind("file_id", fileId).bind("text", plainText))
                .insert();
    }

    public List<KbFile> search(int stationId, String query, String tsConfig) {
        String tsq = FullTextSearch.prefixQuery(tsConfig, "tsquery");
        return query("""
                SELECT
                    %s, %s
                FROM
                    kb_file f
                        JOIN kb_search_index si
                        ON si.file_id = f.id
                WHERE f.station_id = :station_id
                  AND si.search_text @@ %s
                ORDER BY ts_rank(si.search_text, %s) DESC
                LIMIT 50;""", FILE_COLUMNS, FILE_RESTRICTED, tsq, tsq)
                .single(call().bind("station_id", stationId).bind("tsquery", FullTextSearch.prefixTerms(query)))
                .map(KbFile.map())
                .all();
    }

    public List<KbSearchResult> searchWithSnippets(int stationId, String query, String tsConfig) {
        String tsq = FullTextSearch.prefixQuery(tsConfig, "tsquery");
        String snippet =
                FullTextSearch.headline(tsConfig, FullTextSearch.stripMarkup(SNIPPET_SOURCE), tsq, SNIPPET_OPTIONS);
        return query("""
                SELECT
                    %s, %s,
                    %s AS snippet
                FROM
                    kb_file f
                        JOIN kb_search_index si
                        ON si.file_id = f.id
                        LEFT JOIN kb_file_content fc
                        ON fc.file_id = f.id
                WHERE f.station_id = :station_id
                  AND si.search_text @@ %s
                ORDER BY ts_rank(si.search_text, %s) DESC
                LIMIT 50;""", FILE_COLUMNS, FILE_RESTRICTED, snippet, tsq, tsq)
                .single(call().bind("station_id", stationId).bind("tsquery", FullTextSearch.prefixTerms(query)))
                .map(row -> new KbSearchResult(KbFile.map().map(row), row.getString("snippet")))
                .all();
    }

    // -- Access Grants --

    public List<KbAccessGrant> findRestrictions(Integer folderId, Integer fileId) {
        if (folderId != null) {
            return query("SELECT %s FROM kb_access_grant WHERE folder_id = :folder_id;", RESTRICTION_COLUMNS)
                    .single(call().bind("folder_id", folderId))
                    .map(KbAccessGrant.map())
                    .all();
        }
        return query("SELECT %s FROM kb_access_grant WHERE file_id = :file_id;", RESTRICTION_COLUMNS)
                .single(call().bind("file_id", fileId))
                .map(KbAccessGrant.map())
                .all();
    }

    /**
     * Walks a folder's ancestry in one query, root first, so a permission check does not issue one
     * lookup per level.
     *
     * @param folderId the folder to walk up from
     * @return the path from the root down to that folder, each with the mode its grants combine in
     */
    public List<FolderPathNode> findFolderPath(int folderId) {
        return query("""
                WITH RECURSIVE ancestry AS (
                    SELECT id, parent_id, name, restriction_mode, 0 AS depth
                    FROM kb_folder
                    WHERE id = :id
                    UNION ALL
                    SELECT parent.id, parent.parent_id, parent.name, parent.restriction_mode, ancestry.depth + 1
                    FROM kb_folder parent
                    JOIN ancestry ON parent.id = ancestry.parent_id
                )
                SELECT id, name, restriction_mode
                FROM ancestry
                ORDER BY depth DESC;""")
                .single(call().bind("id", folderId))
                .map(FolderPathNode::map)
                .all();
    }

    /**
     * Walks the ancestry of many folders in one query, so a listing whose rows sit in different
     * folders costs one round trip rather than one walk per row.
     *
     * @param folderIds the folders to walk up from
     * @return the path from the root down to each of them, keyed by the folder it ends at
     */
    public Map<Integer, List<FolderPathNode>> findFolderPaths(List<Integer> folderIds) {
        if (folderIds.isEmpty()) return Map.of();
        var rows = query("""
                WITH RECURSIVE ancestry AS (
                    SELECT id AS leaf_id, id, parent_id, name, restriction_mode, 0 AS depth
                    FROM kb_folder
                    WHERE id = ANY(:ids)
                    UNION ALL
                    SELECT ancestry.leaf_id, parent.id, parent.parent_id, parent.name, parent.restriction_mode,
                           ancestry.depth + 1
                    FROM kb_folder parent
                    JOIN ancestry ON parent.id = ancestry.parent_id
                )
                SELECT leaf_id, id, name, restriction_mode
                FROM ancestry
                ORDER BY leaf_id, depth DESC;""")
                .single(call().bind("ids", folderIds, PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("leaf_id"), FolderPathNode.map(row)))
                .all();

        var paths = new LinkedHashMap<Integer, List<FolderPathNode>>();
        for (var row : rows) {
            paths.computeIfAbsent(row.getKey(), key -> new ArrayList<FolderPathNode>())
                    .add(row.getValue());
        }
        return paths;
    }

    /**
     * A folder on the path from the root to a node, with its name and the mode its grants combine
     * in.
     */
    public record FolderPathNode(int id, String name, RestrictionMode restrictionMode) {
        static FolderPathNode map(Row row) throws SQLException {
            return new FolderPathNode(
                    row.getInt("id"), row.getString("name"), row.getEnum("restriction_mode", RestrictionMode.class));
        }
    }

    /**
     * Reads the grants of many folders and files at once, so listing a folder costs one query for
     * every child rather than one per child.
     *
     * @param folderIds the folders to read grants for
     * @param fileIds   the files to read grants for
     * @return every grant row on any of those nodes
     */
    public List<KbAccessGrant> findRestrictionsForNodes(List<Integer> folderIds, List<Integer> fileIds) {
        if (folderIds.isEmpty() && fileIds.isEmpty()) return List.of();
        return query("""
                        SELECT %s
                        FROM kb_access_grant
                        WHERE folder_id = ANY(:folder_ids) OR file_id = ANY(:file_ids);""", RESTRICTION_COLUMNS)
                .single(call().bind("folder_ids", folderIds, PostgreSqlTypes.INTEGER)
                        .bind("file_ids", fileIds, PostgreSqlTypes.INTEGER))
                .map(KbAccessGrant.map())
                .all();
    }

    /**
     * Reads the grants of a whole ancestry in one query, so resolving a member's level costs one
     * round trip rather than one per folder along the path.
     *
     * @param folderIds the folders of the path, root first
     * @param fileId    the file at the end of the path, or {@code null} when resolving a folder
     * @return every grant row on any of those nodes
     */
    public List<KbAccessGrant> findRestrictionsForPath(List<Integer> folderIds, Integer fileId) {
        if (folderIds.isEmpty() && fileId == null) return List.of();
        return query("""
                        SELECT %s
                        FROM kb_access_grant
                        WHERE folder_id = ANY(:folder_ids) OR file_id = :file_id;""", RESTRICTION_COLUMNS)
                .single(call().bind("folder_ids", folderIds, PostgreSqlTypes.INTEGER)
                        .bind("file_id", fileId))
                .map(KbAccessGrant.map())
                .all();
    }

    public KbAccessGrant addRestriction(
            Integer folderId,
            Integer fileId,
            StationUserType userType,
            Integer groupId,
            Integer tagId,
            Integer memberId,
            KbAccessLevel level) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    kb_access_grant(folder_id, file_id, user_type, group_id, tag_id, member_id, level)
                VALUES
                    (:folder_id, :file_id, :user_type, :group_id, :tag_id, :member_id, :level)
                RETURNING %s;""",
                call().bind("folder_id", folderId)
                        .bind("file_id", fileId)
                        .bind("user_type", userType)
                        .bind("group_id", groupId)
                        .bind("tag_id", tagId)
                        .bind("member_id", memberId)
                        .bind("level", level),
                KbAccessGrant.map(),
                RESTRICTION_COLUMNS);
    }

    public boolean removeRestriction(int id) {
        return SqlSupport.deleteById("kb_access_grant", id);
    }

    public void clearRestrictions(Integer folderId, Integer fileId) {
        if (folderId != null) {
            query("DELETE FROM kb_access_grant WHERE folder_id = :folder_id;")
                    .single(call().bind("folder_id", folderId))
                    .delete();
        } else if (fileId != null) {
            query("DELETE FROM kb_access_grant WHERE file_id = :file_id;")
                    .single(call().bind("file_id", fileId))
                    .delete();
        }
    }

    // -- Tags --

    public List<KbTag> findTagsByStation(int stationId) {
        return query("SELECT %s FROM kb_tag WHERE station_id = :station_id ORDER BY name;", TAG_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(KbTag.map())
                .all();
    }

    public KbTag findOrCreateTag(int stationId, String name) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    kb_tag(station_id, name)
                VALUES
                    (:station_id, lower(:name))
                ON CONFLICT (station_id, name) DO UPDATE SET
                    name = excluded.name
                RETURNING %s;""", call().bind("station_id", stationId).bind("name", name.toLowerCase()), KbTag.map(), TAG_COLUMNS);
    }

    // Not yet exposed via routes - tag management UI not implemented
    public boolean deleteTag(int id) {
        return SqlSupport.deleteById("kb_tag", id);
    }

    public List<KbTag> findFileTags(int fileId) {
        return query("""
                SELECT
                    %s
                FROM
                    kb_tag t
                        JOIN kb_file_tag ft
                        ON ft.tag_id = t.id
                WHERE ft.file_id = :file_id
                ORDER BY t.name;""", TAG_COLUMNS_ALIASED)
                .single(call().bind("file_id", fileId))
                .map(KbTag.map())
                .all();
    }

    public List<KbFile> findAllFiles(int stationId) {
        return query(
                        "SELECT %s, %s FROM kb_file f WHERE f.station_id = :station_id ORDER BY f.name;",
                        FILE_COLUMNS, FILE_RESTRICTED)
                .single(call().bind("station_id", stationId))
                .map(KbFile.map())
                .all();
    }

    public List<KbFolder> findAllFolders(int stationId) {
        return query(
                        "SELECT %s, %s FROM kb_folder fo WHERE fo.station_id = :station_id;",
                        FOLDER_COLUMNS, FOLDER_RESTRICTED)
                .single(call().bind("station_id", stationId))
                .map(KbFolder.map())
                .all();
    }

    public List<KbFile> findFilesByTag(int stationId, String tagName) {
        return query("""
                SELECT
                    %s, %s
                FROM
                    kb_file f
                        JOIN kb_file_tag ft
                        ON ft.file_id = f.id
                        JOIN kb_tag t
                        ON t.id = ft.tag_id
                WHERE f.station_id = :station_id
                  AND lower(t.name) = lower(:tag_name)
                ORDER BY f.name;""", FILE_COLUMNS, FILE_RESTRICTED)
                .single(call().bind("station_id", stationId).bind("tag_name", tagName))
                .map(KbFile.map())
                .all();
    }

    public void addFileTag(int fileId, int tagId) {
        query("INSERT INTO kb_file_tag(file_id, tag_id) VALUES(:file_id, :tag_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("file_id", fileId).bind("tag_id", tagId))
                .insert();
    }

    public List<KbTag> findFolderTags(int folderId) {
        return query("""
                SELECT
                    %s
                FROM
                    kb_tag t
                        JOIN kb_folder_tag ft
                        ON ft.tag_id = t.id
                WHERE ft.folder_id = :folder_id
                ORDER BY t.name;""", TAG_COLUMNS_ALIASED)
                .single(call().bind("folder_id", folderId))
                .map(KbTag.map())
                .all();
    }

    public void addFolderTag(int folderId, int tagId) {
        query("INSERT INTO kb_folder_tag(folder_id, tag_id) VALUES(:folder_id, :tag_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("folder_id", folderId).bind("tag_id", tagId))
                .insert();
    }

    public void setFileTags(int fileId, List<String> tagNames, int stationId) {
        query("DELETE FROM kb_file_tag WHERE file_id = :file_id;")
                .single(call().bind("file_id", fileId))
                .delete();
        for (String name : tagNames) {
            var tag = findOrCreateTag(stationId, name.trim());
            addFileTag(fileId, tag.id());
        }
    }

    // -- Related Files --

    public List<KbFile> findRelatedFiles(int fileId) {
        return query("""
                SELECT
                    %s, %s
                FROM
                    kb_file f
                        JOIN kb_related_file r
                        ON r.target_file_id = f.id
                WHERE r.source_file_id = :file_id
                ORDER BY r.position, f.name;""", FILE_COLUMNS, FILE_RESTRICTED)
                .single(call().bind("file_id", fileId))
                .map(KbFile.map())
                .all();
    }

    public void setRelatedFiles(int sourceFileId, List<Integer> targetFileIds) {
        query("DELETE FROM kb_related_file WHERE source_file_id = :source_file_id;")
                .single(call().bind("source_file_id", sourceFileId))
                .delete();
        int pos = 0;
        for (int targetId : targetFileIds) {
            if (targetId == sourceFileId) continue;
            query("""
                    INSERT
                    INTO
                        kb_related_file(source_file_id, target_file_id, position)
                    VALUES
                        (:source, :target, :pos)
                    ON CONFLICT DO NOTHING;""")
                    .single(call().bind("source", sourceFileId)
                            .bind("target", targetId)
                            .bind("pos", pos++))
                    .insert();
        }
    }

    // -- Favourites --

    public void addFavourite(int memberId, int fileId) {
        query("INSERT INTO kb_favourite(member_id, file_id) VALUES(:member_id, :file_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("member_id", memberId).bind("file_id", fileId))
                .insert();
    }

    // Not yet exposed via routes - favourites UI not implemented
    public boolean removeFavourite(int memberId, int fileId) {
        return query("DELETE FROM kb_favourite WHERE member_id = :member_id AND file_id = :file_id;")
                .single(call().bind("member_id", memberId).bind("file_id", fileId))
                .delete()
                .changed();
    }

    public List<KbFile> findFavourites(int memberId) {
        return query("""
                SELECT
                    %s, %s
                FROM
                    kb_file f
                        JOIN kb_favourite fav
                        ON fav.file_id = f.id
                WHERE fav.member_id = :member_id
                ORDER BY fav.created_at DESC;""", FILE_COLUMNS, FILE_RESTRICTED)
                .single(call().bind("member_id", memberId))
                .map(KbFile.map())
                .all();
    }

    public boolean isFavourite(int memberId, int fileId) {
        return SqlSupport.exists(
                "SELECT 1 FROM kb_favourite WHERE member_id = :member_id AND file_id = :file_id;",
                call().bind("member_id", memberId).bind("file_id", fileId));
    }

    public void setFolderTags(int folderId, List<String> tagNames, int stationId) {
        query("DELETE FROM kb_folder_tag WHERE folder_id = :folder_id;")
                .single(call().bind("folder_id", folderId))
                .delete();
        for (String name : tagNames) {
            var tag = findOrCreateTag(stationId, name.trim());
            addFolderTag(folderId, tag.id());
        }
    }

    // -- Public Visibility --

    public Optional<Boolean> findPublicVisibility(Integer folderId, Integer fileId) {
        if (folderId != null) {
            return query("SELECT visible FROM kb_public_visibility WHERE folder_id = :folder_id;")
                    .single(call().bind("folder_id", folderId))
                    .map(row -> row.getBoolean("visible"))
                    .first();
        }
        if (fileId != null) {
            return query("SELECT visible FROM kb_public_visibility WHERE file_id = :file_id;")
                    .single(call().bind("file_id", fileId))
                    .map(row -> row.getBoolean("visible"))
                    .first();
        }
        return Optional.empty();
    }

    public void setPublicVisibility(Integer folderId, Integer fileId, boolean visible) {
        if (folderId != null) {
            query("""
                    INSERT
                    INTO
                        kb_public_visibility(folder_id, visible)
                    VALUES
                        (:folder_id, :visible)
                    ON CONFLICT (folder_id) DO UPDATE SET
                        visible = :visible;""")
                    .single(call().bind("folder_id", folderId).bind("visible", visible))
                    .insert();
        } else if (fileId != null) {
            query("""
                    INSERT INTO kb_public_visibility(file_id, visible) VALUES(:file_id, :visible)
                    ON CONFLICT (file_id) DO UPDATE SET visible = :visible;""")
                    .single(call().bind("file_id", fileId).bind("visible", visible))
                    .insert();
        }
    }

    public void removePublicVisibility(Integer folderId, Integer fileId) {
        if (folderId != null) {
            query("DELETE FROM kb_public_visibility WHERE folder_id = :folder_id;")
                    .single(call().bind("folder_id", folderId))
                    .delete();
        } else if (fileId != null) {
            query("DELETE FROM kb_public_visibility WHERE file_id = :file_id;")
                    .single(call().bind("file_id", fileId))
                    .delete();
        }
    }

    public boolean hasRestrictions(Integer folderId, Integer fileId) {
        if (folderId != null) {
            return RestrictionSql.hasAny(
                    RestrictionType.KB_FOLDER.table(), RestrictionType.KB_FOLDER.fkColumn(), folderId);
        }
        if (fileId != null) {
            return RestrictionSql.hasAny(RestrictionType.KB_FILE.table(), RestrictionType.KB_FILE.fkColumn(), fileId);
        }
        return false;
    }
}
