/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.members.entity.MemberDocument;
import dev.chojo.ember.feature.members.entity.MemberDocumentTag;
import dev.chojo.ember.util.sql.FullTextSearch;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.util.sql.SqlSupport.count;

/**
 * The documents of a station's members, and which members each one is bound to.
 */
@Singleton
public class MemberDocumentRepository {

    private static final String COLUMNS =
            "id, station_id, title, file_name, mime_type, size_bytes, hidden, keep_on_archive, has_thumbnail, uploaded_by, created_at";

    /** The same columns for the join that reads a member's documents. */
    private static final String JOINED_COLUMNS =
            "d.id, d.station_id, d.title, d.file_name, d.mime_type, d.size_bytes, d.hidden, d.keep_on_archive, d.has_thumbnail, d.uploaded_by, d.created_at";

    /**
     * Writes a document and binds it to the members it concerns.
     *
     * @param memberIds the members it belongs to, at least one
     * @return the document as it was written
     */
    public MemberDocument create(
            int stationId,
            String title,
            String fileName,
            String mimeType,
            long sizeBytes,
            boolean hidden,
            boolean keepOnArchive,
            Integer uploadedBy,
            List<Integer> memberIds) {
        var document = query("""
                        INSERT INTO member_document(station_id, title, file_name, mime_type, size_bytes, hidden,
                                                    keep_on_archive, uploaded_by)
                        VALUES (:station_id, :title, :file_name, :mime_type, :size_bytes, :hidden,
                                :keep_on_archive, :uploaded_by)
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("station_id", stationId)
                        .bind("title", title)
                        .bind("file_name", fileName)
                        .bind("mime_type", mimeType)
                        .bind("size_bytes", sizeBytes)
                        .bind("hidden", hidden)
                        .bind("keep_on_archive", keepOnArchive)
                        .bind("uploaded_by", uploadedBy))
                .map(MemberDocument.map())
                .first()
                .orElseThrow();
        bind(document.id(), memberIds);
        return document;
    }

    /**
     * Binds a document to further members. Binding one twice changes nothing.
     */
    public void bind(int documentId, List<Integer> memberIds) {
        for (int memberId : memberIds) {
            query("""
                    INSERT INTO member_document_member(document_id, member_id)
                    VALUES (:document_id, :member_id)
                    ON CONFLICT DO NOTHING;""")
                    .single(call().bind("document_id", documentId).bind("member_id", memberId))
                    .insert();
        }
    }

    /**
     * Gives a document exactly the members named, letting go of the ones left out.
     *
     * <p>Setting rather than adding, because whom a document concerns is a decision that is taken
     * back as often as it is taken: somebody added by mistake has to be removable.
     */
    public void setMembers(int documentId, List<Integer> memberIds) {
        query("DELETE FROM member_document_member WHERE document_id = :document_id;")
                .single(call().bind("document_id", documentId))
                .delete();
        bind(documentId, memberIds);
    }

    /**
     * Records that a picture of the document was produced.
     */
    public void markThumbnail(int documentId) {
        query("UPDATE member_document SET has_thumbnail = TRUE WHERE id = :id;")
                .single(call().bind("id", documentId))
                .update();
    }

    public Optional<MemberDocument> findById(int documentId) {
        return query("SELECT %s FROM member_document WHERE id = :id;", COLUMNS)
                .single(call().bind("id", documentId))
                .map(MemberDocument.map())
                .first();
    }

    /**
     * The documents bound to a member, newest first.
     *
     * @param includeHidden whether the ones kept from the member themselves are listed too
     */
    public List<MemberDocument> findByMember(int memberId, boolean includeHidden) {
        return query("""
                        SELECT %s
                        FROM member_document d
                        JOIN member_document_member m ON m.document_id = d.id
                        WHERE m.member_id = :member_id
                          AND (:include_hidden OR NOT d.hidden)
                        ORDER BY d.created_at DESC;""", JOINED_COLUMNS)
                .single(call().bind("member_id", memberId).bind("include_hidden", includeHidden))
                .map(MemberDocument.map())
                .all();
    }

    /**
     * Records what can be read out of a document, so it can be searched for rather than scrolled to.
     */
    public void updateSearchIndex(int documentId, String plainText, String tsConfig) {
        query("""
                INSERT INTO member_document_search(document_id, search_text)
                VALUES (:document_id, %s)
                ON CONFLICT (document_id) DO UPDATE SET search_text = excluded.search_text;""", FullTextSearch.vector(tsConfig, "text"))
                .single(call().bind("document_id", documentId).bind("text", plainText))
                .insert();
    }

    /**
     * A page of the station's documents, newest first, narrowed by whatever the reader asked for.
     *
     * @param memberIds     only documents bound to one of these members, or empty for all of them
     * @param search        words to look for in the title and in what the documents say, or null
     * @param includeHidden whether the ones kept from their own members are listed too
     */
    public List<MemberDocument> findByStation(
            int stationId,
            List<Integer> memberIds,
            String search,
            boolean includeHidden,
            String tsConfig,
            int limit,
            int offset) {
        var where = WhereBuilder.create()
                .addIf(!includeHidden, "AND NOT d.hidden")
                .addIf(
                        !memberIds.isEmpty(),
                        "AND EXISTS (SELECT 1 FROM member_document_member m"
                                + " WHERE m.document_id = d.id AND m.member_id = ANY (:member_ids))")
                .addIf(
                        search != null,
                        "AND (d.title ILIKE :like OR EXISTS ("
                                + "SELECT 1 FROM member_document_search s"
                                + " WHERE s.document_id = d.id AND s.search_text @@ "
                                + FullTextSearch.prefixQuery(tsConfig, "tsquery") + "))");
        return query("""
                        SELECT %s
                        FROM member_document d
                        WHERE d.station_id = :station_id
                          %s
                        ORDER BY d.created_at DESC
                        LIMIT :limit OFFSET :offset;""", JOINED_COLUMNS, where.fragment())
                .single(bindFilters(call().bind("station_id", stationId), memberIds, search)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(MemberDocument.map())
                .all();
    }

    /** How many documents the same filters match, so the pages can be counted. */
    public int countByStation(
            int stationId, List<Integer> memberIds, String search, boolean includeHidden, String tsConfig) {
        var where = WhereBuilder.create()
                .addIf(!includeHidden, "AND NOT d.hidden")
                .addIf(
                        !memberIds.isEmpty(),
                        "AND EXISTS (SELECT 1 FROM member_document_member m"
                                + " WHERE m.document_id = d.id AND m.member_id = ANY (:member_ids))")
                .addIf(
                        search != null,
                        "AND (d.title ILIKE :like OR EXISTS ("
                                + "SELECT 1 FROM member_document_search s"
                                + " WHERE s.document_id = d.id AND s.search_text @@ "
                                + FullTextSearch.prefixQuery(tsConfig, "tsquery") + "))");
        return count(
                """
                SELECT count(*) AS count
                FROM member_document d
                WHERE d.station_id = :station_id
                  %s;""".formatted(where.fragment()), bindFilters(call().bind("station_id", stationId), memberIds, search));
    }

    /** The values the two filters above need, bound only when the filter is there to use them. */
    private static Call bindFilters(Call call, List<Integer> memberIds, String search) {
        if (!memberIds.isEmpty()) call = call.bind("member_ids", memberIds, PostgreSqlTypes.INTEGER);
        if (search != null) {
            call = call.bind("tsquery", FullTextSearch.prefixTerms(search)).bind("like", "%" + search + "%");
        }
        return call;
    }

    /**
     * Gives a document exactly the tags named, writing the ones the station does not have yet.
     *
     * <p>A tag is free text: it exists because somebody typed it, which is the whole point of
     * being able to sort documents by words nobody agreed on in advance.
     */
    public void setTags(int documentId, int stationId, List<String> tagNames) {
        query("DELETE FROM member_document_tag_entry WHERE document_id = :document_id;")
                .single(call().bind("document_id", documentId))
                .delete();
        for (String raw : tagNames) {
            String name = raw.strip();
            if (name.isEmpty()) continue;
            query("""
                    INSERT INTO member_document_tag(station_id, name)
                    VALUES (:station_id, :name)
                    ON CONFLICT (station_id, name) DO NOTHING;""")
                    .single(call().bind("station_id", stationId).bind("name", name))
                    .insert();
            query("""
                    INSERT INTO member_document_tag_entry(document_id, tag_id)
                    SELECT :document_id, id FROM member_document_tag
                    WHERE station_id = :station_id AND name = :name
                    ON CONFLICT DO NOTHING;""")
                    .single(call().bind("document_id", documentId)
                            .bind("station_id", stationId)
                            .bind("name", name))
                    .insert();
        }
    }

    /** The tags a document carries. */
    public List<MemberDocumentTag> findTags(int documentId) {
        return query("""
                SELECT t.id, t.station_id, t.name
                FROM member_document_tag t
                JOIN member_document_tag_entry e ON e.tag_id = t.id
                WHERE e.document_id = :document_id
                ORDER BY t.name;""")
                .single(call().bind("document_id", documentId))
                .map(MemberDocumentTag.map())
                .all();
    }

    /** Every tag the station has written so far, so a reader can be offered them. */
    public List<MemberDocumentTag> findTagsByStation(int stationId) {
        return query("""
                SELECT id, station_id, name FROM member_document_tag
                WHERE station_id = :station_id ORDER BY name;""")
                .single(call().bind("station_id", stationId))
                .map(MemberDocumentTag.map())
                .all();
    }

    /** The members a document is bound to. */
    public List<Integer> membersOf(int documentId) {
        return query("SELECT member_id FROM member_document_member WHERE document_id = :document_id;")
                .single(call().bind("document_id", documentId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /** Whether the document is one of the member's own. */
    public boolean isBoundTo(int documentId, int memberId) {
        return query("""
                SELECT 1 FROM member_document_member
                WHERE document_id = :document_id AND member_id = :member_id;""")
                .single(call().bind("document_id", documentId).bind("member_id", memberId))
                .map(row -> 1)
                .first()
                .isPresent();
    }

    public boolean delete(int documentId) {
        return query("DELETE FROM member_document WHERE id = :id;")
                .single(call().bind("id", documentId))
                .delete()
                .changed();
    }

    /**
     * Takes a member off every document, and reports the documents that were left bound to nobody.
     *
     * <p>Used when a member is marked former: what is not kept for the record goes, and a document
     * whose last member has gone has nobody left to keep it for.
     *
     * @param keepArchived whether documents marked as kept stay bound to the member
     * @return the documents that no member is bound to any more
     */
    public List<Integer> unbindMember(int memberId, boolean keepArchived) {
        var released = query("""
                DELETE FROM member_document_member m
                WHERE m.member_id = :member_id
                  AND (NOT :keep_archived OR NOT EXISTS (
                        SELECT 1 FROM member_document d
                        WHERE d.id = m.document_id AND d.keep_on_archive))
                RETURNING m.document_id;""")
                .single(call().bind("member_id", memberId).bind("keep_archived", keepArchived))
                .map(row -> row.getInt("document_id"))
                .all();
        return released.stream().filter(this::hasNoMembers).toList();
    }

    /**
     * Whether nobody is bound to the document any more. Asked only of documents a member was just
     * taken off: one that never had a member is the station's own and belongs to nobody by design.
     */
    private boolean hasNoMembers(int documentId) {
        return query("SELECT 1 FROM member_document_member WHERE document_id = :document_id;")
                .single(call().bind("document_id", documentId))
                .map(row -> 1)
                .first()
                .isEmpty();
    }
}
