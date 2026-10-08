/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.repository;

import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The sealed versions of sealed documents. A version is only ever added and, once a later one takes its
 * place, marked superseded; the database refuses every other change to it.
 */
@Singleton
public class SealedVersionRepository {

    private static final String COLUMNS = """
            id, document_id, version, sha256, size_bytes, seal_level, timestamped_by, sealed_at, superseded_at""";

    /**
     * Adds a version as the document's current one, numbered one above its highest. Whatever was current
     * has to be superseded first, in the same transaction.
     *
     * @param documentId    the sealed document
     * @param sha256        SHA-256 of the sealed file, lower-case hexadecimal
     * @param sizeBytes     how large the sealed file is
     * @param sealLevel     the level its seal reached
     * @param timestampedBy the timestamp service whose timestamp it carries, or null
     * @return the version as it was written
     */
    public SealedVersion add(
            int documentId, String sha256, long sizeBytes, SealLevel sealLevel, @Nullable String timestampedBy) {
        return query("""
                        INSERT INTO member_document_version(document_id, version, sha256, size_bytes, seal_level,
                                                            timestamped_by)
                        SELECT :document_id, coalesce(max(version), 0) + 1, :content_hash, :size_bytes, :seal_level,
                               :timestamped_by
                        FROM member_document_version
                        WHERE document_id = :document_id
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("document_id", documentId)
                        .bind("content_hash", sha256)
                        .bind("size_bytes", sizeBytes)
                        .bind("seal_level", sealLevel)
                        .bind("timestamped_by", timestampedBy))
                .map(SealedVersion.map())
                .first()
                .orElseThrow();
    }

    /**
     * Marks the document's current version superseded.
     *
     * @return whether there was a current version
     */
    public boolean supersedeCurrent(int documentId) {
        return query("""
                        UPDATE member_document_version
                        SET superseded_at = now()
                        WHERE document_id = :document_id
                          AND superseded_at IS NULL;""")
                .single(call().bind("document_id", documentId))
                .update()
                .changed();
    }

    /** @return the version the document serves, or empty for a document that is not sealed */
    public Optional<SealedVersion> current(int documentId) {
        return query("""
                        SELECT %s FROM member_document_version
                        WHERE document_id = :document_id
                          AND superseded_at IS NULL;""", COLUMNS)
                .single(call().bind("document_id", documentId))
                .map(SealedVersion.map())
                .first();
    }

    /**
     * The first filed version, of any document at any station, whose file has exactly this SHA-256.
     *
     * @param sha256 SHA-256 of a file, lower-case hexadecimal
     * @return that version, or empty when no sealed document here has such a file
     */
    public Optional<SealedVersion> firstWithHash(String sha256) {
        return query("""
                        SELECT %s FROM member_document_version
                        WHERE sha256 = :content_hash
                        ORDER BY sealed_at, id
                        LIMIT 1;""", COLUMNS)
                .single(call().bind("content_hash", sha256))
                .map(SealedVersion.map())
                .first();
    }

    /** @return every version of the document, newest first */
    public List<SealedVersion> versionsOf(int documentId) {
        return query("""
                        SELECT %s FROM member_document_version
                        WHERE document_id = :document_id
                        ORDER BY version DESC;""", COLUMNS)
                .single(call().bind("document_id", documentId))
                .map(SealedVersion.map())
                .all();
    }
}
