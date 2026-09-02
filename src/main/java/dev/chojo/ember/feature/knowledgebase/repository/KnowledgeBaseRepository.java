/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.repository;

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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

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
                    SELECT 1 FROM kb_folder WHERE station_id = :station_id AND deleted_at IS NULL
                    UNION ALL
                    SELECT 1 FROM kb_file WHERE station_id = :station_id AND deleted_at IS NULL
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
     * How far the two recursive walks over the folder tree follow it. Nothing in the schema forbids
     * a cycle, and both walks sit in the permission check, so an unbounded one would hang the
     * request thread rather than return a wrong answer.
     */
    private static final int MAX_TREE_DEPTH = 64;
    /**
     * What every reading query adds so an entry in the trash is gone wherever the knowledge base is
     * read. Written once and pasted in rather than left to each query to remember, because the one
     * that forgets it is the one that hands a deleted article to a partner station.
     */
    private static final String FILE_ALIVE = "AND f.deleted_at IS NULL";

    private static final String FOLDER_ALIVE = "AND fo.deleted_at IS NULL";

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
                  %s
                ORDER BY fo.position, fo.name;""", FOLDER_COLUMNS, FOLDER_RESTRICTED, FOLDER_ALIVE, where.fragment())
                .single(where.apply(call().bind("station_id", stationId)))
                .map(KbFolder.map())
                .all();
    }

    // -- Files --

    public Optional<KbFolder> findFolderById(int id) {
        return query(
                        "SELECT %s, %s FROM kb_folder fo WHERE fo.id = :id %s;",
                        FOLDER_COLUMNS, FOLDER_RESTRICTED, FOLDER_ALIVE)
                .single(call().bind("id", id))
                .map(KbFolder.map())
                .first();
    }

    /**
     * Reads a folder whether or not it is in the trash.
     *
     * <p>For the permission check only, which has to answer the same way for an entry in the trash
     * as for one in use: what somebody may take back is decided by the path the entry still sits on,
     * so that check must not be the one thing that cannot see it.
     *
     * @param id the folder to read
     * @return the folder, or empty when it does not exist
     */
    public Optional<KbFolder> findAnyFolderById(int id) {
        return query("SELECT %s, %s FROM kb_folder fo WHERE fo.id = :id;", FOLDER_COLUMNS, FOLDER_RESTRICTED)
                .single(call().bind("id", id))
                .map(KbFolder.map())
                .first();
    }

    /**
     * Reads an article whether or not it is in the trash, for the permission check only. See
     * {@link #findAnyFolderById(int)}.
     *
     * @param id the article to read
     * @return the article, or empty when it does not exist
     */
    public Optional<KbFile> findAnyFileById(int id) {
        return query("SELECT %s, %s FROM kb_file f WHERE f.id = :id;", FILE_COLUMNS, FILE_RESTRICTED)
                .single(call().bind("id", id))
                .map(KbFile.map())
                .first();
    }

    /**
     * Reads a folder that is in the trash, which is the one place a deleted folder still answers by
     * id: restoring it and clearing it out both start from the row.
     *
     * @param id the folder to read
     * @return the folder, or empty when it does not exist or is still in use
     */
    public Optional<KbFolder> findDeletedFolderById(int id) {
        return query(
                        "SELECT %s, %s FROM kb_folder fo WHERE fo.id = :id AND fo.deleted_at IS NOT NULL;",
                        FOLDER_COLUMNS, FOLDER_RESTRICTED)
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

    /**
     * Removes a folder for good, taking everything below it through the cascade.
     *
     * <p>The bytes and the blocks of the articles inside are not the cascade's to take, so a caller
     * clears those out first. {@code KbTrashService} is the only one that does this.
     */
    public boolean purgeFolder(int id) {
        return SqlSupport.deleteById("kb_folder", id);
    }

    /**
     * Hangs a folder under another one, or under the tree root when the new parent is
     * {@code null}. Everything inside it follows without being touched, because the knowledge base
     * reads a subtree through its path rather than through a stored copy of it.
     *
     * @param id          the folder to move
     * @param newParentId the folder it should sit in, or {@code null} for the tree root
     * @return {@code true} when the folder existed
     */
    public boolean moveFolder(int id, Integer newParentId) {
        return query("UPDATE kb_folder SET parent_id = :parent_id, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("parent_id", newParentId))
                .update()
                .changed();
    }

    /**
     * Every folder below one folder, at any depth, the folder itself excluded.
     *
     * <p>The counterpart of {@link #findFolderPath(int)}. Both carry a depth ceiling: a cycle in
     * the tree would otherwise spin the query forever, and this walk sits in the check that is
     * meant to keep a cycle from ever being written.
     *
     * @param folderId the folder to walk down from
     * @return the ids of everything below it
     */
    public List<Integer> descendantFolderIds(int folderId) {
        return query("""
                WITH RECURSIVE descendants AS (
                    SELECT id, 0 AS depth
                    FROM kb_folder
                    WHERE parent_id = :id AND deleted_at IS NULL
                    UNION ALL
                    SELECT child.id, descendants.depth + 1
                    FROM kb_folder child
                    JOIN descendants ON child.parent_id = descendants.id
                    WHERE child.deleted_at IS NULL AND descendants.depth < %d
                )
                SELECT id FROM descendants;""", MAX_TREE_DEPTH)
                .single(call().bind("id", folderId))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * Whether a folder of that name already sits directly in a parent folder.
     *
     * <p>The database says the same thing through {@code UNIQUE (station_id, parent_id, name)}, but
     * only by refusing the statement. Asking first is what turns the refusal into a sentence naming
     * the folder rather than a failed request.
     *
     * @param stationId the station the folder belongs to
     * @param parentId  the parent folder, or {@code null} for the tree root
     * @param name      the name to check
     * @param excludeId the folder being moved, which does not collide with itself
     * @return {@code true} when the name is taken
     */
    public boolean folderNameTaken(int stationId, Integer parentId, String name, int excludeId) {
        var where = parentId == null
                ? WhereBuilder.create().add("AND parent_id IS NULL")
                : WhereBuilder.create().add("AND parent_id = :parent_id", "parent_id", parentId);
        return SqlSupport.exists(
                """
                SELECT 1
                FROM kb_folder
                WHERE station_id = :station_id
                  AND name = :name
                  AND id <> :exclude_id
                  AND deleted_at IS NULL
                  %s
                LIMIT 1;""".formatted(where.fragment()),
                where.apply(
                        call().bind("station_id", stationId).bind("name", name).bind("exclude_id", excludeId)));
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
                  %s
                ORDER BY f.position, f.name;""", FILE_COLUMNS, FILE_RESTRICTED, FILE_ALIVE, where.fragment())
                .single(where.apply(call().bind("station_id", stationId)))
                .map(KbFile.map())
                .all();
    }

    public Optional<KbFile> findFileById(int id) {
        return query("SELECT %s, %s FROM kb_file f WHERE f.id = :id %s;", FILE_COLUMNS, FILE_RESTRICTED, FILE_ALIVE)
                .single(call().bind("id", id))
                .map(KbFile.map())
                .first();
    }

    /**
     * Reads an article that is in the trash, which is the one place a deleted article still answers
     * by id.
     *
     * @param id the article to read
     * @return the article, or empty when it does not exist or is still in use
     */
    public Optional<KbFile> findDeletedFileById(int id) {
        return query(
                        "SELECT %s, %s FROM kb_file f WHERE f.id = :id AND f.deleted_at IS NOT NULL;",
                        FILE_COLUMNS, FILE_RESTRICTED)
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

    /**
     * Puts a file into another folder, or at the tree root when the new folder is {@code null}.
     *
     * @param id          the file to move
     * @param newFolderId the folder it should sit in, or {@code null} for the tree root
     * @return {@code true} when the file existed
     */
    public boolean moveFile(int id, Integer newFolderId) {
        return query("UPDATE kb_file SET folder_id = :folder_id, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("folder_id", newFolderId))
                .update()
                .changed();
    }

    /**
     * The ids of every file sitting directly in any of the given folders.
     *
     * @param folderIds the folders to look in
     * @return the file ids
     */
    public List<Integer> findFileIdsInFolders(List<Integer> folderIds) {
        if (folderIds.isEmpty()) return List.of();
        return query("SELECT id FROM kb_file WHERE folder_id = ANY(:folder_ids) AND deleted_at IS NULL;")
                .single(call().bind("folder_ids", folderIds, PostgreSqlTypes.INTEGER))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * The files of a station that were changed most recently.
     *
     * @param stationId the station to list for
     * @param limit     how many to answer with
     * @return the files, newest change first
     */
    public List<KbFile> findRecentFiles(int stationId, int limit) {
        return query("""
                SELECT %s, %s
                FROM kb_file f
                WHERE f.station_id = :station_id
                  %s
                ORDER BY f.updated_at DESC
                LIMIT :limit;""", FILE_COLUMNS, FILE_RESTRICTED, FILE_ALIVE)
                .single(call().bind("station_id", stationId).bind("limit", limit))
                .map(KbFile.map())
                .all();
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

    /**
     * Removes an article for good. The bytes and the blocks behind it are the caller's to clear.
     */
    public boolean purgeFile(int id) {
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
                  %s
                  AND si.search_text @@ %s
                ORDER BY ts_rank(si.search_text, %s) DESC
                LIMIT 50;""", FILE_COLUMNS, FILE_RESTRICTED, FILE_ALIVE, tsq, tsq)
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
                  %s
                  AND si.search_text @@ %s
                ORDER BY ts_rank(si.search_text, %s) DESC
                LIMIT 50;""", FILE_COLUMNS, FILE_RESTRICTED, snippet, FILE_ALIVE, tsq, tsq)
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
                    SELECT id, parent_id, restriction_mode, 0 AS depth
                    FROM kb_folder
                    WHERE id = :id
                    UNION ALL
                    SELECT parent.id, parent.parent_id, parent.restriction_mode, ancestry.depth + 1
                    FROM kb_folder parent
                    JOIN ancestry ON parent.id = ancestry.parent_id
                    WHERE ancestry.depth < %d
                )
                SELECT id, restriction_mode
                FROM ancestry
                ORDER BY depth DESC;""", MAX_TREE_DEPTH)
                .single(call().bind("id", folderId))
                .map(row ->
                        new FolderPathNode(row.getInt("id"), row.getEnum("restriction_mode", RestrictionMode.class)))
                .all();
    }

    /**
     * A folder on the path from the root to a node, with the mode its grants combine in.
     */
    public record FolderPathNode(int id, RestrictionMode restrictionMode) {}

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
                        "SELECT %s, %s FROM kb_file f WHERE f.station_id = :station_id %s ORDER BY f.name;",
                        FILE_COLUMNS, FILE_RESTRICTED, FILE_ALIVE)
                .single(call().bind("station_id", stationId))
                .map(KbFile.map())
                .all();
    }

    public List<KbFolder> findAllFolders(int stationId) {
        return query(
                        "SELECT %s, %s FROM kb_folder fo WHERE fo.station_id = :station_id %s;",
                        FOLDER_COLUMNS, FOLDER_RESTRICTED, FOLDER_ALIVE)
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
                  %s
                  AND lower(t.name) = lower(:tag_name)
                ORDER BY f.name;""", FILE_COLUMNS, FILE_RESTRICTED, FILE_ALIVE)
                .single(call().bind("station_id", stationId).bind("tag_name", tagName))
                .map(KbFile.map())
                .all();
    }

    public void addFileTag(int fileId, int tagId) {
        query("INSERT INTO kb_file_tag(file_id, tag_id) VALUES(:file_id, :tag_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("file_id", fileId).bind("tag_id", tagId))
                .insert();
    }

    /**
     * Drops one tag from a file, leaving the rest of its tags alone.
     *
     * @param fileId the file
     * @param tagId  the tag to drop
     */
    public void removeFileTag(int fileId, int tagId) {
        query("DELETE FROM kb_file_tag WHERE file_id = :file_id AND tag_id = :tag_id;")
                .single(call().bind("file_id", fileId).bind("tag_id", tagId))
                .delete();
    }

    /**
     * Drops one tag from a folder, leaving the rest of its tags alone.
     *
     * @param folderId the folder
     * @param tagId    the tag to drop
     */
    public void removeFolderTag(int folderId, int tagId) {
        query("DELETE FROM kb_folder_tag WHERE folder_id = :folder_id AND tag_id = :tag_id;")
                .single(call().bind("folder_id", folderId).bind("tag_id", tagId))
                .delete();
    }

    /**
     * Looks a tag up by name, without creating it.
     *
     * @param stationId the station the tag belongs to
     * @param name      the tag name, matched the way tags are stored, in lower case
     * @return the tag, or empty when the station does not know that name
     */
    public Optional<KbTag> findTagByName(int stationId, String name) {
        return query("SELECT %s FROM kb_tag WHERE station_id = :station_id AND name = lower(:name);", TAG_COLUMNS)
                .single(call().bind("station_id", stationId).bind("name", name))
                .map(KbTag.map())
                .first();
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
                  %s
                ORDER BY r.position, f.name;""", FILE_COLUMNS, FILE_RESTRICTED, FILE_ALIVE)
                .single(call().bind("file_id", fileId))
                .map(KbFile.map())
                .all();
    }

    /**
     * The files pointing at a file, which is the same rows read the other way round.
     *
     * <p>The table stays directed and no counter-row is written: whoever wrote the reference still
     * owns it, and every reference that exists today shows up here without anything being migrated.
     *
     * @param fileId the file being pointed at
     * @return the files that point at it
     */
    public List<KbFile> findBacklinks(int fileId) {
        return query("""
                SELECT
                    %s, %s
                FROM
                    kb_file f
                        JOIN kb_related_file r
                        ON r.source_file_id = f.id
                WHERE r.target_file_id = :file_id
                  %s
                ORDER BY f.name;""", FILE_COLUMNS, FILE_RESTRICTED, FILE_ALIVE)
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
                  %s
                ORDER BY fav.created_at DESC;""", FILE_COLUMNS, FILE_RESTRICTED, FILE_ALIVE)
                .single(call().bind("member_id", memberId))
                .map(KbFile.map())
                .all();
    }

    public boolean isFavourite(int memberId, int fileId) {
        return SqlSupport.exists("""
                SELECT 1
                FROM kb_favourite fav
                    JOIN kb_file f ON f.id = fav.file_id
                WHERE fav.member_id = :member_id
                  AND fav.file_id = :file_id
                  AND f.deleted_at IS NULL;""", call().bind("member_id", memberId).bind("file_id", fileId));
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

    // -- Trash --

    /**
     * Puts one article in the trash on its own.
     *
     * @param id       the article
     * @param memberId who deleted it, {@code null} when nobody in particular did
     * @return {@code true} when it was in use until now
     */
    public boolean softDeleteFile(int id, Integer memberId) {
        return query("""
                UPDATE kb_file
                SET deleted_at = now(), deleted_by = :member_id, deleted_with_folder = FALSE
                WHERE id = :id AND deleted_at IS NULL;""")
                .single(call().bind("id", id).bind("member_id", memberId))
                .update()
                .changed();
    }

    /**
     * Puts one folder in the trash. What is inside it is marked separately, by
     * {@link #markSubtreeDeleted(int, Integer)}.
     *
     * @param id       the folder
     * @param memberId who deleted it, {@code null} when nobody in particular did
     * @return {@code true} when it was in use until now
     */
    public boolean softDeleteFolder(int id, Integer memberId) {
        return query("""
                UPDATE kb_folder
                SET deleted_at = now(), deleted_by = :member_id, deleted_with_folder = FALSE
                WHERE id = :id AND deleted_at IS NULL;""")
                .single(call().bind("id", id).bind("member_id", memberId))
                .update()
                .changed();
    }

    /**
     * Marks everything below a folder as having gone to the trash with it.
     *
     * <p>The walk steps only through folders that are in use, so a branch somebody put in the trash
     * earlier keeps its own deletion and comes back on its own. For the same reason nothing already
     * marked is touched: it was deleted for its own reasons and is not this folder's to restore.
     *
     * @param folderId the folder that was deleted
     * @param memberId who deleted it, {@code null} when nobody in particular did
     * @return the ids of the articles that went down with it
     */
    public List<Integer> markSubtreeDeleted(int folderId, Integer memberId) {
        return query("""
                WITH RECURSIVE subtree AS (
                    SELECT id, 0 AS depth
                    FROM kb_folder
                    WHERE parent_id = :id AND deleted_at IS NULL
                    UNION ALL
                    SELECT child.id, subtree.depth + 1
                    FROM kb_folder child
                    JOIN subtree ON child.parent_id = subtree.id
                    WHERE child.deleted_at IS NULL AND subtree.depth < %d
                ),
                marked_folders AS (
                    UPDATE kb_folder
                    SET deleted_at = now(), deleted_by = :member_id, deleted_with_folder = TRUE
                    WHERE id IN (SELECT id FROM subtree)
                    RETURNING id
                ),
                marked_files AS (
                    UPDATE kb_file
                    SET deleted_at = now(), deleted_by = :member_id, deleted_with_folder = TRUE
                    WHERE deleted_at IS NULL
                      AND (folder_id = :id OR folder_id IN (SELECT id FROM subtree))
                    RETURNING id
                )
                SELECT id FROM marked_files;""", MAX_TREE_DEPTH)
                .single(call().bind("id", folderId).bind("member_id", memberId))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * Takes one article back out of the trash.
     *
     * @param id     the article
     * @param toRoot whether it has to go to the top level because the folder it came from is itself
     *               in the trash
     * @return {@code true} when it was in the trash
     */
    public boolean restoreFile(int id, boolean toRoot) {
        String folder = toRoot ? ", folder_id = NULL" : "";
        return query("""
                UPDATE kb_file
                SET deleted_at = NULL, deleted_by = NULL, deleted_with_folder = FALSE%s
                WHERE id = :id AND deleted_at IS NOT NULL;""", folder).single(call().bind("id", id)).update().changed();
    }

    /**
     * Takes one folder back out of the trash. What went down with it is restored separately, by
     * {@link #restoreSubtree(int)}.
     *
     * @param id     the folder
     * @param toRoot whether it has to go to the top level because the folder it came from is itself
     *               in the trash
     * @return {@code true} when it was in the trash
     */
    public boolean restoreFolder(int id, boolean toRoot) {
        String parent = toRoot ? ", parent_id = NULL" : "";
        return query("""
                UPDATE kb_folder
                SET deleted_at = NULL, deleted_by = NULL, deleted_with_folder = FALSE%s
                WHERE id = :id AND deleted_at IS NOT NULL;""", parent).single(call().bind("id", id)).update().changed();
    }

    /**
     * Brings back everything that went to the trash with a folder.
     *
     * <p>The walk steps only through folders that carry the mark, so a branch that was already in
     * the trash before this folder followed it stays there rather than being restored by accident
     * into something the reader never deleted.
     *
     * @param folderId the folder being restored
     * @return the ids of the articles that came back with it
     */
    public List<Integer> restoreSubtree(int folderId) {
        return query("""
                WITH RECURSIVE subtree AS (
                    SELECT id, 0 AS depth
                    FROM kb_folder
                    WHERE parent_id = :id AND deleted_with_folder = TRUE
                    UNION ALL
                    SELECT child.id, subtree.depth + 1
                    FROM kb_folder child
                    JOIN subtree ON child.parent_id = subtree.id
                    WHERE child.deleted_with_folder = TRUE AND subtree.depth < %d
                ),
                restored_folders AS (
                    UPDATE kb_folder
                    SET deleted_at = NULL, deleted_by = NULL, deleted_with_folder = FALSE
                    WHERE id IN (SELECT id FROM subtree)
                    RETURNING id
                ),
                restored_files AS (
                    UPDATE kb_file
                    SET deleted_at = NULL, deleted_by = NULL, deleted_with_folder = FALSE
                    WHERE deleted_with_folder = TRUE
                      AND (folder_id = :id OR folder_id IN (SELECT id FROM subtree))
                    RETURNING id
                )
                SELECT id FROM restored_files;""", MAX_TREE_DEPTH)
                .single(call().bind("id", folderId))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * Every article anywhere below a folder, whatever state it is in, so clearing the folder out
     * for good can take each one's bytes and blocks before the cascade takes its row.
     *
     * @param folderId the folder about to go
     * @return the articles inside it, at any depth
     */
    public List<KbFile> findFilesInSubtree(int folderId) {
        return query("""
                WITH RECURSIVE subtree AS (
                    SELECT id, 0 AS depth
                    FROM kb_folder
                    WHERE id = :id
                    UNION ALL
                    SELECT child.id, subtree.depth + 1
                    FROM kb_folder child
                    JOIN subtree ON child.parent_id = subtree.id
                    WHERE subtree.depth < %d
                )
                SELECT %s, %s
                FROM kb_file f
                WHERE f.folder_id IN (SELECT id FROM subtree);""", MAX_TREE_DEPTH, FILE_COLUMNS, FILE_RESTRICTED)
                .single(call().bind("id", folderId))
                .map(KbFile.map())
                .all();
    }

    /**
     * Drops the search index rows of articles, which is how a deleted article stops being findable
     * rather than by a filter every future query would have to remember.
     *
     * @param fileIds the articles
     */
    public void deleteSearchIndex(List<Integer> fileIds) {
        if (fileIds.isEmpty()) return;
        query("DELETE FROM kb_search_index WHERE file_id = ANY(:file_ids);")
                .single(call().bind("file_ids", fileIds, PostgreSqlTypes.INTEGER))
                .delete();
    }

    /**
     * Every folder of a station that sits in the trash.
     *
     * @param stationId the station
     * @return the folders, newest deletion first
     */
    public List<TrashedFolder> findTrashedFolders(int stationId) {
        return query("""
                SELECT id, parent_id, name, description, deleted_at, deleted_by, deleted_with_folder
                FROM kb_folder
                WHERE station_id = :station_id AND deleted_at IS NOT NULL
                ORDER BY deleted_at DESC;""")
                .single(call().bind("station_id", stationId))
                .map(row -> new TrashedFolder(
                        row.getInt("id"),
                        row.getObject("parent_id", Integer.class),
                        row.getString("name"),
                        row.getString("description"),
                        row.get("deleted_at", INSTANT_TIMESTAMP),
                        row.getObject("deleted_by", Integer.class),
                        row.getBoolean("deleted_with_folder")))
                .all();
    }

    /**
     * Every article of a station that sits in the trash.
     *
     * @param stationId the station
     * @return the articles, newest deletion first
     */
    public List<TrashedFile> findTrashedFiles(int stationId) {
        return query("""
                SELECT id, folder_id, name, description, file_type, file_size, deleted_at, deleted_by,
                       deleted_with_folder
                FROM kb_file
                WHERE station_id = :station_id AND deleted_at IS NOT NULL
                ORDER BY deleted_at DESC;""")
                .single(call().bind("station_id", stationId))
                .map(row -> new TrashedFile(
                        row.getInt("id"),
                        row.getObject("folder_id", Integer.class),
                        row.getString("name"),
                        row.getString("description"),
                        row.getEnum("file_type", KbFileType.class),
                        row.getLong("file_size"),
                        row.get("deleted_at", INSTANT_TIMESTAMP),
                        row.getObject("deleted_by", Integer.class),
                        row.getBoolean("deleted_with_folder")))
                .all();
    }

    /**
     * The entries whose time in the trash is up, across every station, oldest deletion first.
     *
     * <p>Only the entries that stand in a trash on their own: what went down with a folder goes
     * when that folder goes, through the cascade.
     *
     * @param retentionDays how long an entry is kept
     * @param limit         how many to answer with, so one sweep cannot turn into a long one
     * @return what is due
     */
    public List<TrashRef> findExpiredTrash(int retentionDays, int limit) {
        return query("""
                SELECT TRUE AS is_folder, id, deleted_at
                FROM kb_folder
                WHERE deleted_at IS NOT NULL
                  AND deleted_with_folder = FALSE
                  AND deleted_at < now() - make_interval(days := :days)
                UNION ALL
                SELECT FALSE AS is_folder, id, deleted_at
                FROM kb_file
                WHERE deleted_at IS NOT NULL
                  AND deleted_with_folder = FALSE
                  AND deleted_at < now() - make_interval(days := :days)
                ORDER BY deleted_at
                LIMIT :limit;""")
                .single(call().bind("days", retentionDays).bind("limit", limit))
                .map(row -> new TrashRef(row.getBoolean("is_folder"), row.getInt("id")))
                .all();
    }

    /**
     * A folder in the trash.
     *
     * @param deletedWithFolder whether it followed a folder above it, in which case it is not an
     *                          entry of the trash in its own right
     */
    public record TrashedFolder(
            int id,
            Integer parentId,
            String name,
            String description,
            Instant deletedAt,
            Integer deletedBy,
            boolean deletedWithFolder) {}

    /**
     * An article in the trash.
     *
     * @param deletedWithFolder whether it followed the folder around it, in which case it is not an
     *                          entry of the trash in its own right
     */
    public record TrashedFile(
            int id,
            Integer folderId,
            String name,
            String description,
            KbFileType fileType,
            long fileSize,
            Instant deletedAt,
            Integer deletedBy,
            boolean deletedWithFolder) {}

    /**
     * One entry of the trash, named only by what it is and which one.
     */
    public record TrashRef(boolean folder, int id) {}

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
