/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.DocumentFilter;
import dev.chojo.ember.feature.documents.entity.DocumentTag;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.util.sql.FullTextSearch;
import dev.chojo.ember.util.sql.MemberNameSql;
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
public class DocumentRepository {

    private static final String COLUMNS = """
            id, station_id, title, file_name, mime_type, size_bytes, hidden, keep_on_archive, has_thumbnail,
            uploaded_by, uploader_account_id, created_at, sealed""";

    /** The same columns for the join that reads a member's documents. */
    private static final String JOINED_COLUMNS = """
            d.id, d.station_id, d.title, d.file_name, d.mime_type, d.size_bytes, d.hidden, d.keep_on_archive,
            d.has_thumbnail, d.uploaded_by, d.uploader_account_id, d.created_at, d.sealed""";

    /**
     * Writes a document and binds it to the members it concerns.
     *
     * @param uploader  who put it in
     * @param memberIds the members it belongs to, at least one
     * @return the document as it was written
     */
    public Document create(
            int stationId,
            String title,
            String fileName,
            String mimeType,
            long sizeBytes,
            boolean hidden,
            boolean keepOnArchive,
            Uploader uploader,
            List<Integer> memberIds) {
        var document = query("""
                        INSERT INTO member_document(station_id, title, file_name, mime_type, size_bytes, hidden,
                                                    keep_on_archive, uploaded_by, uploader_account_id)
                        VALUES (:station_id, :title, :file_name, :mime_type, :size_bytes, :hidden,
                                :keep_on_archive, :uploaded_by, :uploader_account_id)
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("station_id", stationId)
                        .bind("title", title)
                        .bind("file_name", fileName)
                        .bind("mime_type", mimeType)
                        .bind("size_bytes", sizeBytes)
                        .bind("hidden", hidden)
                        .bind("keep_on_archive", keepOnArchive)
                        .bind("uploaded_by", uploader.memberId())
                        .bind("uploader_account_id", uploader.accountId()))
                .map(Document.map())
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
     * back as often as it is taken: somebody added by mistake has to be removable. The names of members
     * who were deleted stay, since nobody can be bound to the document in their place.
     */
    public void setMembers(int documentId, List<Integer> memberIds) {
        query("DELETE FROM member_document_member WHERE document_id = :document_id AND member_id IS NOT NULL;")
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

    /**
     * Seals a document, which locks it for good: it is kept when its members leave, and the database
     * refuses deleting it, its members or its versions while its station exists.
     */
    public void seal(int documentId) {
        query("UPDATE member_document SET keep_on_archive = TRUE, sealed = TRUE WHERE id = :id;")
                .single(call().bind("id", documentId))
                .update();
    }

    /**
     * Takes the row of a sealed document for the rest of the transaction, so versions are added to it
     * one after the other.
     *
     * @return the document, or empty when there is none by that id
     */
    public Optional<Document> lockSealed(int documentId) {
        return query("SELECT %s FROM member_document WHERE id = :id AND sealed FOR UPDATE;", COLUMNS)
                .single(call().bind("id", documentId))
                .map(Document.map())
                .first();
    }

    /** Records the size of the file a document now serves, which for a sealed one is its current version. */
    public void setSize(int documentId, long sizeBytes) {
        query("UPDATE member_document SET size_bytes = :size_bytes WHERE id = :id;")
                .single(call().bind("id", documentId).bind("size_bytes", sizeBytes))
                .update();
    }

    /** Every PDF document of every station, whose picture is its first page drawn. */
    public List<Document> findPdfs() {
        return query("""
                SELECT %s FROM member_document
                WHERE lower(mime_type) = 'application/pdf'
                ORDER BY id;""", COLUMNS).single().map(Document.map()).all();
    }

    public Optional<Document> findById(int documentId) {
        return query("SELECT %s FROM member_document WHERE id = :id;", COLUMNS)
                .single(call().bind("id", documentId))
                .map(Document.map())
                .first();
    }

    /**
     * The documents bound to a member, newest first.
     *
     * <p>The station is asked for as well as the member, because a binding is a row and a row can
     * name a member of another station. A document of one station never belongs on the record of
     * somebody at another, whatever the bindings say.
     *
     * @param stationId     the station the documents belong to
     * @param includeHidden whether the ones kept from the member themselves are listed too
     */
    public List<Document> findByMember(int stationId, int memberId, boolean includeHidden) {
        return query("""
                        SELECT %s
                        FROM member_document d
                        JOIN member_document_member m ON m.document_id = d.id
                        WHERE m.member_id = :member_id
                          AND d.station_id = :station_id
                          AND (:include_hidden OR NOT d.hidden)
                        ORDER BY d.created_at DESC;""", JOINED_COLUMNS)
                .single(call().bind("member_id", memberId)
                        .bind("station_id", stationId)
                        .bind("include_hidden", includeHidden))
                .map(Document.map())
                .all();
    }

    /**
     * Records what can be read out of a document, so it can be searched for rather than scrolled to.
     */
    public void updateSearchIndex(int documentId, String plainText, String tsConfig) {
        query("""
                INSERT INTO member_document_search(document_id, search_text, source_text)
                VALUES (:document_id, %s, :text)
                ON CONFLICT (document_id) DO UPDATE
                    SET search_text = excluded.search_text,
                        source_text = excluded.source_text;""", FullTextSearch.vector(tsConfig, "text"))
                .single(call().bind("document_id", documentId).bind("text", plainText))
                .insert();
    }

    /**
     * The documents whose index was built before its source text was kept, and which can therefore
     * only be rebuilt by reading the file again.
     */
    public List<Document> findWithoutSourceText() {
        return query("""
                SELECT %s
                FROM member_document d
                    LEFT JOIN member_document_search s
                    ON s.document_id = d.id
                WHERE s.source_text IS NULL;""", JOINED_COLUMNS).single().map(Document.map()).all();
    }

    /** The stations that hold at least one document with a kept source text. */
    public List<Integer> stationsWithSourceText() {
        return query("""
                SELECT DISTINCT d.station_id
                FROM member_document d
                    JOIN member_document_search s
                    ON s.document_id = d.id
                WHERE s.source_text IS NOT NULL;""").single().map(row -> row.getInt("station_id")).all();
    }

    /**
     * Builds the search index of a station's documents again from the text it was built from, which
     * is what a new major version of the database needs when it stems words differently.
     *
     * @return how many documents were indexed again
     */
    public int rebuildSearchIndex(int stationId, String tsConfig) {
        return query("""
                UPDATE member_document_search s
                SET search_text = to_tsvector('%s', s.source_text)
                FROM member_document d
                WHERE d.id = s.document_id
                  AND d.station_id = :station_id
                  AND s.source_text IS NOT NULL;""", FullTextSearch.config(tsConfig))
                .single(call().bind("station_id", stationId))
                .update()
                .rows();
    }

    /**
     * A page of the station's documents, newest first, narrowed by whatever the reader asked for.
     */
    public List<Document> findByStation(int stationId, DocumentFilter filter, String tsConfig, int limit, int offset) {
        return query("""
                        SELECT %s
                        FROM member_document d
                        WHERE d.station_id = :station_id
                          %s
                        ORDER BY d.created_at DESC
                        LIMIT :limit OFFSET :offset;""", JOINED_COLUMNS, where(filter, tsConfig))
                .single(bindFilter(call().bind("station_id", stationId), filter)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(Document.map())
                .all();
    }

    /** How many documents the same filter matches, so the pages can be counted. */
    public int countByStation(int stationId, DocumentFilter filter, String tsConfig) {
        return count("""
                SELECT count(*) AS count
                FROM member_document d
                WHERE d.station_id = :station_id
                  %s;""".formatted(where(filter, tsConfig)), bindFilter(call().bind("station_id", stationId), filter));
    }

    /**
     * Every document the filter matches that can be removed, by id, so a reader can remove all of them
     * rather than a page. A sealed document is left out, since it is never removed.
     */
    public List<Integer> idsByStation(int stationId, DocumentFilter filter, String tsConfig) {
        return query("""
                        SELECT d.id
                        FROM member_document d
                        WHERE d.station_id = :station_id
                          AND NOT d.sealed
                          %s
                        ORDER BY d.created_at DESC;""", where(filter, tsConfig))
                .single(bindFilter(call().bind("station_id", stationId), filter))
                .map(row -> row.getInt("id"))
                .all();
    }

    /** The conditions a filter puts on the store, in the order a reader reads the filters. */
    private static String where(DocumentFilter filter, String tsConfig) {
        return WhereBuilder.create()
                .addIf(!filter.includeHidden(), "AND NOT d.hidden")
                .addIf(filter.unboundOnly(), """
                        AND NOT EXISTS (SELECT 1 FROM member_document_member m2 WHERE m2.document_id = d.id)""")
                .addIf(filter.departedOnly(), """
                        AND EXISTS (SELECT 1 FROM member_document_member gone WHERE gone.document_id = d.id)
                        AND NOT EXISTS (SELECT 1
                                        FROM member_document_member here
                                        JOIN station_member sm ON sm.id = here.member_id
                                        WHERE here.document_id = d.id
                                          AND NOT sm.former)""")
                .addIf(!filter.memberIds().isEmpty(), """
                        AND EXISTS (SELECT 1 FROM member_document_member m
                                    WHERE m.document_id = d.id AND m.member_id = ANY (:member_ids))""")
                .addIf(filter.search() != null, """
                        AND (d.title ILIKE :like OR EXISTS (SELECT 1 FROM member_document_search s
                                                            WHERE s.document_id = d.id
                                                              AND s.search_text @@ %s))""".formatted(FullTextSearch.prefixQuery(tsConfig, "tsquery")))
                .fragment();
    }

    /** The values the filter needs, bound only where a condition is there to use them. */
    private static Call bindFilter(Call call, DocumentFilter filter) {
        if (!filter.memberIds().isEmpty()) call = call.bind("member_ids", filter.memberIds(), PostgreSqlTypes.INTEGER);
        String search = filter.search();
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
    public List<DocumentTag> findTags(int documentId) {
        return query("""
                SELECT t.id, t.station_id, t.name
                FROM member_document_tag t
                JOIN member_document_tag_entry e ON e.tag_id = t.id
                WHERE e.document_id = :document_id
                ORDER BY t.name;""")
                .single(call().bind("document_id", documentId))
                .map(DocumentTag.map())
                .all();
    }

    /** Every tag the station has written so far, so a reader can be offered them. */
    public List<DocumentTag> findTagsByStation(int stationId) {
        return query("""
                SELECT id, station_id, name FROM member_document_tag
                WHERE station_id = :station_id ORDER BY name;""")
                .single(call().bind("station_id", stationId))
                .map(DocumentTag.map())
                .all();
    }

    /** The members a document is bound to. */
    public List<Integer> membersOf(int documentId) {
        return query("""
                SELECT member_id FROM member_document_member
                WHERE document_id = :document_id AND member_id IS NOT NULL;""")
                .single(call().bind("document_id", documentId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /** The names of the members who were deleted while the document was kept for them, by name. */
    public List<String> departedOf(int documentId) {
        return query("""
                SELECT departed_name FROM member_document_member
                WHERE document_id = :document_id AND member_id IS NULL
                ORDER BY departed_name;""")
                .single(call().bind("document_id", documentId))
                .map(row -> row.getString("departed_name"))
                .all();
    }

    /**
     * The name of whoever put the document in: the member at the station, or the account of the
     * association manager who filed it.
     *
     * @return the name, or empty where nobody put it in or they are gone
     */
    public Optional<String> uploaderNameOf(int documentId) {
        return query("""
                SELECT coalesce(%s, %s) AS name
                FROM member_document d
                LEFT JOIN station_member sm ON sm.id = d.uploaded_by
                LEFT JOIN account a ON a.id = sm.account_id
                LEFT JOIN account manager ON manager.id = d.uploader_account_id
                WHERE d.id = :id;""", MemberNameSql.ofMemberOrNull("sm", "a"), MemberNameSql.ofAccount("manager"))
                .single(call().bind("id", documentId))
                .map(row -> row.getString("name"))
                .first();
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
     * Turns the member's links on documents kept for the record into their name, before the member is
     * deleted and the links would go with them.
     *
     * <p>A kept document that lost its last link would name nobody, and a document naming nobody is the
     * station's own paperwork, readable far more widely than what was filed about one person. The name
     * keeps it what it was: somebody's paperwork, with somebody to say whose.
     *
     * @param memberId the member about to be deleted
     * @return how many links became a name
     */
    public int keepDepartedName(int memberId) {
        return query("""
                UPDATE member_document_member m
                SET member_id     = NULL,
                    departed_name = %s
                FROM member_document d,
                     station_member sm
                     LEFT JOIN account a ON a.id = sm.account_id
                WHERE m.member_id = :member_id
                  AND d.id = m.document_id
                  AND d.keep_on_archive
                  AND sm.id = m.member_id;""", MemberNameSql.ofMember("sm", "a"))
                .single(call().bind("member_id", memberId))
                .update()
                .rows();
    }

    /**
     * Whether nobody is bound to the document any more, neither a member nor the name of one who was
     * deleted. Such a document is the station's own paperwork; one that lost its last member as they
     * left has nobody left to keep it for.
     */
    public boolean hasNoMembers(int documentId) {
        return query("SELECT 1 FROM member_document_member WHERE document_id = :document_id;")
                .single(call().bind("document_id", documentId))
                .map(row -> 1)
                .first()
                .isEmpty();
    }
}
