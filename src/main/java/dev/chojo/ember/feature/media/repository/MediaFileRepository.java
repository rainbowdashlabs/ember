/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The files of a station's media library and the members who brought them in.
 *
 * <p>Ownership is a set rather than a column because uploads are deduplicated by content hash:
 * the second member to upload the same bytes is handed the existing row, and a single
 * {@code uploaded_by} column would hand them a file owned by somebody else.
 */
@Singleton
public class MediaFileRepository {

    private static final String FILE_COLUMNS =
            "id, page_id, station_id, content_hash, file_name, mime_type, file_size, uploaded_at, default_alt_text, default_description, folder_id";

    /**
     * The same column list qualified with the {@code f} alias, for the queries that join the
     * uploader table and would otherwise leave {@code id} ambiguous.
     */
    private static final String ALIASED_FILE_COLUMNS = "f." + FILE_COLUMNS.replace(", ", ", f.");

    /**
     * @param stationId the station whose library holds the file, or null for one the instance holds
     *                  and every station can be served
     */
    public StationFile create(
            Integer pageId, Integer stationId, String contentHash, String fileName, String mimeType, long fileSize) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    station_file(
                        page_id, station_id, content_hash, file_name, mime_type, file_size,
                        default_alt_text, default_description, folder_id)
                VALUES
                    (:page_id, :station_id, :content_hash, :file_name, :mime_type, :file_size, NULL, NULL, NULL)
                RETURNING %s;""",
                call().bind("page_id", pageId)
                        .bind("station_id", stationId)
                        .bind("content_hash", contentHash)
                        .bind("file_name", fileName)
                        .bind("mime_type", mimeType)
                        .bind("file_size", fileSize),
                StationFile.map(),
                FILE_COLUMNS);
    }

    public Optional<StationFile> findById(int fileId) {
        return SqlSupport.findById("station_file", FILE_COLUMNS, fileId, StationFile.map());
    }

    /**
     * The file with these bytes in one library, which is how the same picture uploaded twice stays
     * one file.
     *
     * <p>The station is compared with {@code IS NOT DISTINCT FROM} rather than {@code =}, so that
     * the instance library, whose files have no station, is looked up by the same query as a
     * station's. Plain equality is never true of a null and would have stored a second copy every
     * time.
     *
     * @param stationId the station whose library to look in, or null for the instance's
     */
    public Optional<StationFile> findByStationAndHash(Integer stationId, String contentHash) {
        return query("""
                SELECT %s
                FROM station_file
                WHERE station_id IS NOT DISTINCT FROM :station_id AND content_hash = :content_hash
                LIMIT 1;""", FILE_COLUMNS)
                .single(call().bind("station_id", stationId).bind("content_hash", contentHash))
                .map(StationFile.map())
                .first();
    }

    public List<StationFile> findByPage(int pageId) {
        return query("SELECT %s FROM station_file WHERE page_id = :page_id;", FILE_COLUMNS)
                .single(call().bind("page_id", pageId))
                .map(StationFile.map())
                .all();
    }

    /**
     * Every file in one library, newest first.
     *
     * @param stationId the station whose library to list, or null for the instance's
     */
    public List<StationFile> findByStation(Integer stationId) {
        return query("""
                        SELECT %s FROM station_file
                        WHERE station_id IS NOT DISTINCT FROM :station_id
                        ORDER BY uploaded_at DESC;""", FILE_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationFile.map())
                .all();
    }

    /**
     * The files a single member uploaded, newest first. This is what the browser shows a member
     * who holds none of the content permissions.
     */
    public List<StationFile> findByUploader(int stationId, int memberId) {
        return query("""
                SELECT %s
                FROM station_file f
                JOIN station_file_uploader u ON u.file_id = f.id
                WHERE f.station_id = :station_id
                  AND u.member_id = :member_id
                ORDER BY u.uploaded_at DESC;""", ALIASED_FILE_COLUMNS)
                .single(call().bind("station_id", stationId).bind("member_id", memberId))
                .map(StationFile.map())
                .all();
    }

    public boolean delete(int fileId) {
        return SqlSupport.deleteById("station_file", fileId);
    }

    public boolean updateMeta(int fileId, String altText, String description) {
        return query("""
                UPDATE station_file
                SET default_alt_text = :alt, default_description = :description
                WHERE id = :id;""")
                .single(call().bind("id", fileId).bind("alt", altText).bind("description", description))
                .update()
                .changed();
    }

    // --- Uploaders ---

    /**
     * Records that this member brought the file in. Idempotent, so a member uploading the same
     * bytes twice keeps the timestamp of the first time they did.
     */
    public void addUploader(int fileId, int memberId) {
        query("""
                INSERT INTO station_file_uploader(file_id, member_id)
                VALUES (:file_id, :member_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("file_id", fileId).bind("member_id", memberId))
                .insert();
    }

    public boolean removeUploader(int fileId, int memberId) {
        return query("DELETE FROM station_file_uploader WHERE file_id = :file_id AND member_id = :member_id;")
                .single(call().bind("file_id", fileId).bind("member_id", memberId))
                .delete()
                .changed();
    }

    public boolean hasUploader(int fileId, int memberId) {
        return SqlSupport.exists(
                "SELECT 1 FROM station_file_uploader WHERE file_id = :file_id AND member_id = :member_id;",
                call().bind("file_id", fileId).bind("member_id", memberId));
    }

    public boolean hasAnyUploader(int fileId) {
        return SqlSupport.exists(
                "SELECT 1 FROM station_file_uploader WHERE file_id = :file_id;", call().bind("file_id", fileId));
    }

    /**
     * The ids of every file in the station somebody claims as their upload. A file in this set
     * survives pruning even when nothing references it: an image may outlive the first place it
     * was used, and deleting it under its uploader is the worse mistake of the two.
     */
    public List<Integer> findOwnedFileIds(int stationId) {
        return query("""
                SELECT DISTINCT u.file_id
                FROM station_file_uploader u
                JOIN station_file f ON f.id = u.file_id
                WHERE f.station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getInt("file_id"))
                .all();
    }

    /**
     * For each of the supplied files, the member who first brought it in. A file that predates
     * uploader tracking is absent from the map rather than mapped to a placeholder.
     */
    public Map<Integer, Integer> findFirstUploaders(List<Integer> fileIds) {
        Map<Integer, Integer> out = new HashMap<>();
        if (fileIds == null || fileIds.isEmpty()) return out;
        query("""
                SELECT DISTINCT ON (file_id) file_id, member_id
                FROM station_file_uploader
                WHERE file_id = ANY(:file_ids)
                ORDER BY file_id, uploaded_at, member_id;""")
                .single(call().bind("file_ids", fileIds, PostgreSqlTypes.INTEGER))
                .map(row -> {
                    out.put(row.getInt("file_id"), row.getInt("member_id"));
                    return null;
                })
                .all();
        return out;
    }
}
