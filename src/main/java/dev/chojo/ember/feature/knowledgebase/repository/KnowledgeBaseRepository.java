/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessRestriction;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileVersion;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.KbSearchResult;
import dev.chojo.ember.feature.knowledgebase.entity.KbTag;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class KnowledgeBaseRepository {

    private static final String FOLDER_COLUMNS =
            "fo.id, fo.station_id, fo.parent_id, fo.name, fo.description, fo.icon_url, fo.position, fo.created_by, fo.created_at, fo.updated_at, fo.restriction_mode, EXISTS(SELECT 1 FROM kb_access_restriction r WHERE r.folder_id = fo.id) AS restricted";
    private static final String FOLDER_COLUMNS_BARE =
            "id, station_id, parent_id, name, description, icon_url, position, created_by, created_at, updated_at, restriction_mode, EXISTS(SELECT 1 FROM kb_access_restriction r WHERE r.folder_id = id) AS restricted";
    private static final String FILE_COLUMNS =
            "f.id, f.station_id, f.folder_id, f.name, f.description, f.file_type, f.mime_type, f.file_size, f.icon_url, f.youtube_url, f.link_url, f.position, f.created_by, f.created_at, f.updated_at, f.source_file_id, f.source_station_id, f.restriction_mode, EXISTS(SELECT 1 FROM kb_access_restriction r WHERE r.file_id = f.id) AS restricted";
    private static final String FILE_COLUMNS_BARE =
            "id, station_id, folder_id, name, description, file_type, mime_type, file_size, icon_url, youtube_url, link_url, position, created_by, created_at, updated_at, source_file_id, source_station_id, restriction_mode, EXISTS(SELECT 1 FROM kb_access_restriction r WHERE r.file_id = id) AS restricted";

    // -- Folders --

    public List<KbFolder> findFolders(int stationId, Integer parentId) {
        if (parentId == null) {
            return Query.query(
                            "SELECT " + FOLDER_COLUMNS
                                    + " FROM kb_folder fo WHERE fo.station_id = :station_id AND fo.parent_id IS NULL ORDER BY fo.position, fo.name;")
                    .single(Call.of().bind("station_id", stationId))
                    .map(KbFolder.map())
                    .all();
        }
        return Query.query(
                        "SELECT " + FOLDER_COLUMNS
                                + " FROM kb_folder fo WHERE fo.station_id = :station_id AND fo.parent_id = :parent_id ORDER BY fo.position, fo.name;")
                .single(Call.of().bind("station_id", stationId).bind("parent_id", parentId))
                .map(KbFolder.map())
                .all();
    }

    public Optional<KbFolder> findFolderById(int id) {
        return Query.query("SELECT " + FOLDER_COLUMNS + " FROM kb_folder fo WHERE fo.id = :id;")
                .single(Call.of().bind("id", id))
                .map(KbFolder.map())
                .first();
    }

    public KbFolder createFolder(int stationId, Integer parentId, String name, String description, int createdBy) {
        return Query.query("""
                        INSERT INTO kb_folder(station_id, parent_id, name, description, created_by)
                        VALUES (:station_id, :parent_id, :name, :description, :created_by)
                        RETURNING\s""" + FOLDER_COLUMNS_BARE + ";")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("parent_id", parentId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("created_by", createdBy))
                .map(KbFolder.map())
                .first()
                .orElseThrow();
    }

    public boolean updateFolder(int id, String name, String description, String iconUrl, int position) {
        return Query.query("""
                        UPDATE kb_folder SET name = :name, description = :description, icon_url = :icon_url,
                        position = :position, updated_at = now() WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("icon_url", iconUrl)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean deleteFolder(int id) {
        return Query.query("DELETE FROM kb_folder WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Files --

    public List<KbFile> findFiles(int stationId, Integer folderId) {
        if (folderId == null) {
            return Query.query(
                            "SELECT " + FILE_COLUMNS
                                    + " FROM kb_file f WHERE f.station_id = :station_id AND f.folder_id IS NULL ORDER BY f.position, f.name;")
                    .single(Call.of().bind("station_id", stationId))
                    .map(KbFile.map())
                    .all();
        }
        return Query.query(
                        "SELECT " + FILE_COLUMNS
                                + " FROM kb_file f WHERE f.station_id = :station_id AND f.folder_id = :folder_id ORDER BY f.position, f.name;")
                .single(Call.of().bind("station_id", stationId).bind("folder_id", folderId))
                .map(KbFile.map())
                .all();
    }

    public Optional<KbFile> findFileById(int id) {
        return Query.query("SELECT " + FILE_COLUMNS + " FROM kb_file f WHERE f.id = :id;")
                .single(Call.of().bind("id", id))
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
        return Query.query("""
                        INSERT INTO kb_file(station_id, folder_id, name, description, file_type, mime_type, file_size, youtube_url, link_url, created_by)
                        VALUES (:station_id, :folder_id, :name, :description, :file_type, :mime_type, :file_size, :youtube_url, :link_url, :created_by)
                        RETURNING\s""" + FILE_COLUMNS_BARE + ";")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("folder_id", folderId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("file_type", fileType.name())
                        .bind("mime_type", mimeType)
                        .bind("file_size", fileSize)
                        .bind("youtube_url", youtubeUrl)
                        .bind("link_url", linkUrl)
                        .bind("created_by", createdBy))
                .map(KbFile.map())
                .first()
                .orElseThrow();
    }

    public boolean updateFile(int id, String name, String description, String iconUrl, int position) {
        return Query.query("""
                        UPDATE kb_file SET name = :name, description = :description, icon_url = :icon_url,
                        position = :position, updated_at = now() WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("icon_url", iconUrl)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean setSourceReference(int fileId, int sourceFileId, int sourceStationId) {
        return Query.query(
                        "UPDATE kb_file SET source_file_id = :source_file_id, source_station_id = :source_station_id WHERE id = :id;")
                .single(Call.of()
                        .bind("id", fileId)
                        .bind("source_file_id", sourceFileId)
                        .bind("source_station_id", sourceStationId))
                .update()
                .changed();
    }

    public boolean deleteFile(int id) {
        return Query.query("DELETE FROM kb_file WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- File Content --

    public void storeTextContent(int fileId, String textContent) {
        Query.query("""
                        INSERT INTO kb_file_content(file_id, text_content) VALUES (:file_id, :text_content)
                        ON CONFLICT (file_id) DO UPDATE SET text_content = :text_content;""")
                .single(Call.of().bind("file_id", fileId).bind("text_content", textContent))
                .insert();
    }

    public Optional<String> readTextContent(int fileId) {
        return Query.query("SELECT text_content FROM kb_file_content WHERE file_id = :file_id;")
                .single(Call.of().bind("file_id", fileId))
                .map(row -> row.getString("text_content"))
                .first();
    }

    // -- Version History (Markdown) --

    public List<KbFileVersion> findVersions(int fileId) {
        return Query.query("SELECT * FROM kb_file_version WHERE file_id = :file_id ORDER BY version DESC;")
                .single(Call.of().bind("file_id", fileId))
                .map(KbFileVersion.map())
                .all();
    }

    public Optional<KbFileVersion> findVersion(int fileId, int version) {
        return Query.query("SELECT * FROM kb_file_version WHERE file_id = :file_id AND version = :version;")
                .single(Call.of().bind("file_id", fileId).bind("version", version))
                .map(KbFileVersion.map())
                .first();
    }

    public int getNextVersion(int fileId) {
        return Query.query(
                        "SELECT COALESCE(MAX(version), 0) + 1 AS next FROM kb_file_version WHERE file_id = :file_id;")
                .single(Call.of().bind("file_id", fileId))
                .map(row -> row.getInt("next"))
                .first()
                .orElse(1);
    }

    public KbFileVersion createVersion(int fileId, String patch, boolean isFull, int version, int createdBy) {
        return Query.query("""
                        INSERT INTO kb_file_version(file_id, patch, is_full, version, created_by)
                        VALUES (:file_id, :patch, :is_full, :version, :created_by)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("file_id", fileId)
                        .bind("patch", patch)
                        .bind("is_full", isFull)
                        .bind("version", version)
                        .bind("created_by", createdBy))
                .map(KbFileVersion.map())
                .first()
                .orElseThrow();
    }

    // -- Search Index --

    public void updateSearchIndex(int fileId, String plainText, String tsConfig) {
        Query.query("INSERT INTO kb_search_index(file_id, search_text) VALUES (:file_id, to_tsvector('"
                        + sanitizeTsConfig(tsConfig)
                        + "', :text)) ON CONFLICT (file_id) DO UPDATE SET search_text = to_tsvector('"
                        + sanitizeTsConfig(tsConfig) + "', :text);")
                .single(Call.of().bind("file_id", fileId).bind("text", plainText))
                .insert();
    }

    /**
     * Build a tsquery that supports prefix matching.
     * Each word gets :* appended so "Notr" matches "Notruf".
     * Multiple words are combined with &amp; (AND).
     */
    private static String buildPrefixTsQuery(String cfg, String query) {
        // to_tsquery with :* suffix for prefix matching
        return "to_tsquery('" + cfg + "', :tsquery)";
    }

    private static String preparePrefixQuery(String query) {
        return Arrays.stream(query.trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .map(w -> w.replaceAll("[^\\w\\p{L}]", "") + ":*")
                .collect(Collectors.joining(" & "));
    }

    public List<KbFile> search(int stationId, String query, String tsConfig) {
        String cfg = sanitizeTsConfig(tsConfig);
        String tsq = buildPrefixTsQuery(cfg, query);
        return Query.query("SELECT " + FILE_COLUMNS + " FROM kb_file f JOIN kb_search_index si ON si.file_id = f.id"
                        + " WHERE f.station_id = :station_id AND si.search_text @@ " + tsq
                        + " ORDER BY ts_rank(si.search_text, " + tsq + ") DESC LIMIT 50;")
                .single(Call.of().bind("station_id", stationId).bind("tsquery", preparePrefixQuery(query)))
                .map(KbFile.map())
                .all();
    }

    public List<KbSearchResult> searchWithSnippets(int stationId, String query, String tsConfig) {
        String cfg = sanitizeTsConfig(tsConfig);
        String tsq = buildPrefixTsQuery(cfg, query);
        // Strip markdown/HTML from text_content before generating headline snippet
        String cleanText = "regexp_replace(regexp_replace("
                + "COALESCE(fc.text_content, f.name || ' ' || COALESCE(f.description, ''))"
                + ", '<[^>]+>', ' ', 'g')" // strip HTML tags
                + ", '[#*_~`>\\[\\]()!|]', '', 'g')"; // strip markdown syntax
        return Query.query("SELECT " + FILE_COLUMNS + ", ts_headline('" + cfg
                        + "', " + cleanText + ", "
                        + tsq
                        + ", 'MaxWords=30, MinWords=10, StartSel=<mark>, StopSel=</mark>') as snippet"
                        + " FROM kb_file f"
                        + " JOIN kb_search_index si ON si.file_id = f.id"
                        + " LEFT JOIN kb_file_content fc ON fc.file_id = f.id"
                        + " WHERE f.station_id = :station_id AND si.search_text @@ " + tsq
                        + " ORDER BY ts_rank(si.search_text, " + tsq + ") DESC LIMIT 50;")
                .single(Call.of().bind("station_id", stationId).bind("tsquery", preparePrefixQuery(query)))
                .map(row -> new KbSearchResult(KbFile.map().map(row), row.getString("snippet")))
                .all();
    }

    private static final Set<String> VALID_TS_CONFIGS =
            Set.of("simple", "german", "english", "french", "spanish", "italian", "dutch", "portuguese", "russian");

    private static String sanitizeTsConfig(String config) {
        return VALID_TS_CONFIGS.contains(config) ? config : "simple";
    }

    // -- Access Restrictions --

    public List<KbAccessRestriction> findRestrictions(Integer folderId, Integer fileId) {
        if (folderId != null) {
            return Query.query("SELECT * FROM kb_access_restriction WHERE folder_id = :folder_id;")
                    .single(Call.of().bind("folder_id", folderId))
                    .map(KbAccessRestriction.map())
                    .all();
        }
        return Query.query("SELECT * FROM kb_access_restriction WHERE file_id = :file_id;")
                .single(Call.of().bind("file_id", fileId))
                .map(KbAccessRestriction.map())
                .all();
    }

    public KbAccessRestriction addRestriction(
            Integer folderId, Integer fileId, Integer roleId, Integer groupId, Integer tagId, Integer memberId) {
        return Query.query("""
                        INSERT INTO kb_access_restriction(folder_id, file_id, role_id, group_id, tag_id, member_id)
                        VALUES (:folder_id, :file_id, :role_id, :group_id, :tag_id, :member_id)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("folder_id", folderId)
                        .bind("file_id", fileId)
                        .bind("role_id", roleId)
                        .bind("group_id", groupId)
                        .bind("tag_id", tagId)
                        .bind("member_id", memberId))
                .map(KbAccessRestriction.map())
                .first()
                .orElseThrow();
    }

    public boolean removeRestriction(int id) {
        return Query.query("DELETE FROM kb_access_restriction WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public void clearRestrictions(Integer folderId, Integer fileId) {
        if (folderId != null) {
            Query.query("DELETE FROM kb_access_restriction WHERE folder_id = :folder_id;")
                    .single(Call.of().bind("folder_id", folderId))
                    .delete();
        } else if (fileId != null) {
            Query.query("DELETE FROM kb_access_restriction WHERE file_id = :file_id;")
                    .single(Call.of().bind("file_id", fileId))
                    .delete();
        }
    }

    // -- Tags --

    public List<KbTag> findTagsByStation(int stationId) {
        return Query.query("SELECT id, station_id, name FROM kb_tag WHERE station_id = :station_id ORDER BY name;")
                .single(Call.of().bind("station_id", stationId))
                .map(KbTag.map())
                .all();
    }

    public KbTag findOrCreateTag(int stationId, String name) {
        return Query.query(
                        "INSERT INTO kb_tag(station_id, name) VALUES(:station_id, lower(:name)) ON CONFLICT (station_id, name) DO UPDATE SET name = EXCLUDED.name RETURNING id, station_id, name;")
                .single(Call.of().bind("station_id", stationId).bind("name", name.toLowerCase()))
                .map(KbTag.map())
                .first()
                .orElseThrow();
    }

    // Not yet exposed via routes — tag management UI not implemented
    public boolean deleteTag(int id) {
        return Query.query("DELETE FROM kb_tag WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public List<KbTag> findFileTags(int fileId) {
        return Query.query(
                        "SELECT t.id, t.station_id, t.name FROM kb_tag t JOIN kb_file_tag ft ON ft.tag_id = t.id WHERE ft.file_id = :file_id ORDER BY t.name;")
                .single(Call.of().bind("file_id", fileId))
                .map(KbTag.map())
                .all();
    }

    public List<KbFile> findFilesByTag(int stationId, String tagName) {
        return Query.query(
                        "SELECT " + FILE_COLUMNS
                                + " FROM kb_file f JOIN kb_file_tag ft ON ft.file_id = f.id JOIN kb_tag t ON t.id = ft.tag_id WHERE f.station_id = :station_id AND lower(t.name) = lower(:tag_name) ORDER BY f.name;")
                .single(Call.of().bind("station_id", stationId).bind("tag_name", tagName))
                .map(KbFile.map())
                .all();
    }

    public void addFileTag(int fileId, int tagId) {
        Query.query("INSERT INTO kb_file_tag(file_id, tag_id) VALUES(:file_id, :tag_id) ON CONFLICT DO NOTHING;")
                .single(Call.of().bind("file_id", fileId).bind("tag_id", tagId))
                .insert();
    }

    public List<KbTag> findFolderTags(int folderId) {
        return Query.query(
                        "SELECT t.id, t.station_id, t.name FROM kb_tag t JOIN kb_folder_tag ft ON ft.tag_id = t.id WHERE ft.folder_id = :folder_id ORDER BY t.name;")
                .single(Call.of().bind("folder_id", folderId))
                .map(KbTag.map())
                .all();
    }

    public void addFolderTag(int folderId, int tagId) {
        Query.query("INSERT INTO kb_folder_tag(folder_id, tag_id) VALUES(:folder_id, :tag_id) ON CONFLICT DO NOTHING;")
                .single(Call.of().bind("folder_id", folderId).bind("tag_id", tagId))
                .insert();
    }

    public void setFileTags(int fileId, List<String> tagNames, int stationId) {
        Query.query("DELETE FROM kb_file_tag WHERE file_id = :file_id;")
                .single(Call.of().bind("file_id", fileId))
                .delete();
        for (String name : tagNames) {
            var tag = findOrCreateTag(stationId, name.trim());
            addFileTag(fileId, tag.id());
        }
    }

    // -- Related Files --

    public List<KbFile> findRelatedFiles(int fileId) {
        return Query.query(
                        "SELECT " + FILE_COLUMNS
                                + " FROM kb_file f JOIN kb_related_file r ON r.target_file_id = f.id WHERE r.source_file_id = :file_id ORDER BY r.position, f.name;")
                .single(Call.of().bind("file_id", fileId))
                .map(KbFile.map())
                .all();
    }

    public void setRelatedFiles(int sourceFileId, List<Integer> targetFileIds) {
        Query.query("DELETE FROM kb_related_file WHERE source_file_id = :source_file_id;")
                .single(Call.of().bind("source_file_id", sourceFileId))
                .delete();
        int pos = 0;
        for (int targetId : targetFileIds) {
            if (targetId == sourceFileId) continue;
            Query.query(
                            "INSERT INTO kb_related_file(source_file_id, target_file_id, position) VALUES(:source, :target, :pos) ON CONFLICT DO NOTHING;")
                    .single(Call.of()
                            .bind("source", sourceFileId)
                            .bind("target", targetId)
                            .bind("pos", pos++))
                    .insert();
        }
    }

    // -- Favourites --

    public void addFavourite(int memberId, int fileId) {
        Query.query("INSERT INTO kb_favourite(member_id, file_id) VALUES(:member_id, :file_id) ON CONFLICT DO NOTHING;")
                .single(Call.of().bind("member_id", memberId).bind("file_id", fileId))
                .insert();
    }

    // Not yet exposed via routes — favourites UI not implemented
    public boolean removeFavourite(int memberId, int fileId) {
        return Query.query("DELETE FROM kb_favourite WHERE member_id = :member_id AND file_id = :file_id;")
                .single(Call.of().bind("member_id", memberId).bind("file_id", fileId))
                .delete()
                .changed();
    }

    public List<KbFile> findFavourites(int memberId) {
        return Query.query(
                        "SELECT " + FILE_COLUMNS
                                + " FROM kb_file f JOIN kb_favourite fav ON fav.file_id = f.id WHERE fav.member_id = :member_id ORDER BY fav.created_at DESC;")
                .single(Call.of().bind("member_id", memberId))
                .map(KbFile.map())
                .all();
    }

    public boolean isFavourite(int memberId, int fileId) {
        return Query.query("SELECT 1 FROM kb_favourite WHERE member_id = :member_id AND file_id = :file_id;")
                .single(Call.of().bind("member_id", memberId).bind("file_id", fileId))
                .map(row -> true)
                .first()
                .orElse(false);
    }

    public void setFolderTags(int folderId, List<String> tagNames, int stationId) {
        Query.query("DELETE FROM kb_folder_tag WHERE folder_id = :folder_id;")
                .single(Call.of().bind("folder_id", folderId))
                .delete();
        for (String name : tagNames) {
            var tag = findOrCreateTag(stationId, name.trim());
            addFolderTag(folderId, tag.id());
        }
    }

    // -- Public Visibility --

    public Optional<Boolean> findPublicVisibility(Integer folderId, Integer fileId) {
        if (folderId != null) {
            return Query.query("SELECT visible FROM kb_public_visibility WHERE folder_id = :folder_id;")
                    .single(Call.of().bind("folder_id", folderId))
                    .map(row -> row.getBoolean("visible"))
                    .first();
        }
        if (fileId != null) {
            return Query.query("SELECT visible FROM kb_public_visibility WHERE file_id = :file_id;")
                    .single(Call.of().bind("file_id", fileId))
                    .map(row -> row.getBoolean("visible"))
                    .first();
        }
        return Optional.empty();
    }

    public void setPublicVisibility(Integer folderId, Integer fileId, boolean visible) {
        if (folderId != null) {
            Query.query("""
                            INSERT INTO kb_public_visibility(folder_id, visible) VALUES(:folder_id, :visible)
                            ON CONFLICT (folder_id) DO UPDATE SET visible = :visible;""")
                    .single(Call.of().bind("folder_id", folderId).bind("visible", visible))
                    .insert();
        } else if (fileId != null) {
            Query.query("""
                            INSERT INTO kb_public_visibility(file_id, visible) VALUES(:file_id, :visible)
                            ON CONFLICT (file_id) DO UPDATE SET visible = :visible;""")
                    .single(Call.of().bind("file_id", fileId).bind("visible", visible))
                    .insert();
        }
    }

    public void removePublicVisibility(Integer folderId, Integer fileId) {
        if (folderId != null) {
            Query.query("DELETE FROM kb_public_visibility WHERE folder_id = :folder_id;")
                    .single(Call.of().bind("folder_id", folderId))
                    .delete();
        } else if (fileId != null) {
            Query.query("DELETE FROM kb_public_visibility WHERE file_id = :file_id;")
                    .single(Call.of().bind("file_id", fileId))
                    .delete();
        }
    }

    public boolean hasRestrictions(Integer folderId, Integer fileId) {
        if (folderId != null) {
            return Query.query("SELECT 1 FROM kb_access_restriction WHERE folder_id = :folder_id LIMIT 1;")
                    .single(Call.of().bind("folder_id", folderId))
                    .map(row -> true)
                    .first()
                    .orElse(false);
        }
        if (fileId != null) {
            return Query.query("SELECT 1 FROM kb_access_restriction WHERE file_id = :file_id LIMIT 1;")
                    .single(Call.of().bind("file_id", fileId))
                    .map(row -> true)
                    .first()
                    .orElse(false);
        }
        return false;
    }
}
