/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.knowledgebase.entity.ConversionStatus;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessRestriction;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileVersion;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.KbSearchResult;
import dev.chojo.ember.feature.knowledgebase.entity.KbTag;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final String FOLDER_RESTRICTED =
            "EXISTS(SELECT 1 FROM kb_access_restriction r WHERE r.folder_id = fo.id) AS restricted";
    private static final String FILE_COLUMNS_BARE =
            "id, station_id, folder_id, name, description, file_type, mime_type, file_size, icon_url, youtube_url, link_url, position, created_by, created_at, updated_at, source_file_id, source_station_id, restriction_mode, conversion_status";
    private static final String FILE_COLUMNS = SqlSupport.alias("f", FILE_COLUMNS_BARE);
    private static final String FILE_RESTRICTED =
            "EXISTS(SELECT 1 FROM kb_access_restriction r WHERE r.file_id = f.id) AS restricted";
    private static final String FILE_VERSION_COLUMNS = "id, file_id, patch, is_full, version, created_by, created_at";
    private static final String RESTRICTION_COLUMNS = "id, folder_id, file_id, user_type, group_id, tag_id, member_id";
    private static final String TAG_COLUMNS = "id, station_id, name";
    private static final String TAG_COLUMNS_ALIASED = SqlSupport.alias("t", TAG_COLUMNS);

    // -- Folders --
    private static final Set<String> VALID_TS_CONFIGS =
            Set.of("simple", "german", "english", "french", "spanish", "italian", "dutch", "portuguese", "russian");

    /**
     * Build a tsquery that supports prefix matching.
     * Each word gets :* appended so "Notr" matches "Notruf".
     * Multiple words are combined with &amp; (AND).
     */
    private static String buildPrefixTsQuery(String cfg) {
        return "to_tsquery('%s', :tsquery)".formatted(cfg);
    }

    private static String preparePrefixQuery(String query) {
        return Arrays.stream(query.trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .map(w -> w.replaceAll("[^\\w\\p{L}]", "") + ":*")
                .collect(Collectors.joining(" & "));
    }

    private static String sanitizeTsConfig(String config) {
        return VALID_TS_CONFIGS.contains(config) ? config : "simple";
    }

    public List<KbFolder> findFolders(int stationId, Integer parentId) {
        if (parentId == null) {
            return query("""
                    SELECT
                        %s, %s
                    FROM
                        kb_folder fo
                    WHERE fo.station_id = :station_id
                      AND fo.parent_id IS NULL
                    ORDER BY fo.position, fo.name;""", FOLDER_COLUMNS, FOLDER_RESTRICTED)
                    .single(call().bind("station_id", stationId))
                    .map(KbFolder.map())
                    .all();
        }
        return query("""
                SELECT
                    %s, %s
                FROM
                    kb_folder fo
                WHERE fo.station_id = :station_id
                  AND fo.parent_id = :parent_id
                ORDER BY fo.position, fo.name;""", FOLDER_COLUMNS, FOLDER_RESTRICTED)
                .single(call().bind("station_id", stationId).bind("parent_id", parentId))
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

    public List<KbFile> findFiles(int stationId, Integer folderId) {
        if (folderId == null) {
            return query("""
                    SELECT
                        %s, %s
                    FROM
                        kb_file f
                    WHERE f.station_id = :station_id
                      AND f.folder_id IS NULL
                    ORDER BY f.position, f.name;""", FILE_COLUMNS, FILE_RESTRICTED)
                    .single(call().bind("station_id", stationId))
                    .map(KbFile.map())
                    .all();
        }
        return query("""
                SELECT
                    %s, %s
                FROM
                    kb_file f
                WHERE f.station_id = :station_id
                  AND f.folder_id = :folder_id
                ORDER BY f.position, f.name;""", FILE_COLUMNS, FILE_RESTRICTED)
                .single(call().bind("station_id", stationId).bind("folder_id", folderId))
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
        String config = sanitizeTsConfig(tsConfig);
        query("""
                INSERT
                INTO
                    kb_search_index(file_id, search_text)
                VALUES
                    (:file_id, to_tsvector('%s', :text))
                ON CONFLICT (file_id) DO UPDATE SET
                    search_text = excluded.search_text;""", config)
                .single(call().bind("file_id", fileId).bind("text", plainText))
                .insert();
    }

    public List<KbFile> search(int stationId, String query, String tsConfig) {
        String cfg = sanitizeTsConfig(tsConfig);
        String tsq = buildPrefixTsQuery(cfg);
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
                .single(call().bind("station_id", stationId).bind("tsquery", preparePrefixQuery(query)))
                .map(KbFile.map())
                .all();
    }

    public List<KbSearchResult> searchWithSnippets(int stationId, String query, String tsConfig) {
        String cfg = sanitizeTsConfig(tsConfig);
        String tsq = buildPrefixTsQuery(cfg);
        // Strip markdown/HTML from text_content before generating headline snippet
        // strip HTML tags
        String cleanText = """
                regexp_replace(regexp_replace(COALESCE(fc.text_content, f.name || ' ' || COALESCE(f.description, '')), '<[^>]+>', ' ', 'g'), '[#*_~`>\\[\\]()!|]', '', 'g')"""; //
        return query("""
                SELECT
                    %s, %s,
                    ts_headline('%s', %s, %s, 'MaxWords=30, MinWords=10, StartSel=<mark>, StopSel=</mark>') AS snippet
                FROM
                    kb_file f
                        JOIN kb_search_index si
                        ON si.file_id = f.id
                        LEFT JOIN kb_file_content fc
                        ON fc.file_id = f.id
                WHERE f.station_id = :station_id
                  AND si.search_text @@ %s
                ORDER BY ts_rank(si.search_text, %s) DESC
                LIMIT 50;""", FILE_COLUMNS, FILE_RESTRICTED, cfg, cleanText, tsq, tsq, tsq)
                .single(call().bind("station_id", stationId).bind("tsquery", preparePrefixQuery(query)))
                .map(row -> new KbSearchResult(KbFile.map().map(row), row.getString("snippet")))
                .all();
    }

    // -- Access Restrictions --

    public List<KbAccessRestriction> findRestrictions(Integer folderId, Integer fileId) {
        if (folderId != null) {
            return query("SELECT %s FROM kb_access_restriction WHERE folder_id = :folder_id;", RESTRICTION_COLUMNS)
                    .single(call().bind("folder_id", folderId))
                    .map(KbAccessRestriction.map())
                    .all();
        }
        return query("SELECT %s FROM kb_access_restriction WHERE file_id = :file_id;", RESTRICTION_COLUMNS)
                .single(call().bind("file_id", fileId))
                .map(KbAccessRestriction.map())
                .all();
    }

    public KbAccessRestriction addRestriction(
            Integer folderId,
            Integer fileId,
            StationUserType userType,
            Integer groupId,
            Integer tagId,
            Integer memberId) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    kb_access_restriction(folder_id, file_id, user_type, group_id, tag_id, member_id)
                VALUES
                    (:folder_id, :file_id, :user_type, :group_id, :tag_id, :member_id)
                RETURNING %s;""",
                call().bind("folder_id", folderId)
                        .bind("file_id", fileId)
                        .bind("user_type", userType)
                        .bind("group_id", groupId)
                        .bind("tag_id", tagId)
                        .bind("member_id", memberId),
                KbAccessRestriction.map(),
                RESTRICTION_COLUMNS);
    }

    public boolean removeRestriction(int id) {
        return SqlSupport.deleteById("kb_access_restriction", id);
    }

    public void clearRestrictions(Integer folderId, Integer fileId) {
        if (folderId != null) {
            query("DELETE FROM kb_access_restriction WHERE folder_id = :folder_id;")
                    .single(call().bind("folder_id", folderId))
                    .delete();
        } else if (fileId != null) {
            query("DELETE FROM kb_access_restriction WHERE file_id = :file_id;")
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

    // Not yet exposed via routes — tag management UI not implemented
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

    // Not yet exposed via routes — favourites UI not implemented
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
            return SqlSupport.exists(
                    "SELECT 1 FROM kb_access_restriction WHERE folder_id = :folder_id LIMIT 1;",
                    call().bind("folder_id", folderId));
        }
        if (fileId != null) {
            return SqlSupport.exists(
                    "SELECT 1 FROM kb_access_restriction WHERE file_id = :file_id LIMIT 1;",
                    call().bind("file_id", fileId));
        }
        return false;
    }
}
