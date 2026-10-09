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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The sealed versions of sealed documents. A version is only ever added and, once a later one takes its
 * place, marked superseded; the database refuses every other change to what it records. The one thing kept
 * beside that is when adding a later timestamp to it last failed.
 */
@Singleton
public class SealedVersionRepository {

    private static final String COLUMNS = """
            id, document_id, version, sha256, size_bytes, seal_level, timestamped_by, timestamp_valid_until, sealed_at,
            superseded_at""";

    private static final String STATION_HERE = """
            NOT EXISTS (SELECT 1
                        FROM member_document d
                                 JOIN station s ON s.id = d.station_id
                        WHERE d.id = member_document_version.document_id
                          AND s.moved_away_at IS NOT NULL)""";

    /**
     * Adds a version as the document's current one, numbered one above its highest. Whatever was current
     * has to be superseded first, in the same transaction.
     *
     * @param documentId    the sealed document
     * @param sha256        SHA-256 of the sealed file, lower-case hexadecimal
     * @param sizeBytes     how large the sealed file is
     * @param sealLevel     the level its seal reached
     * @param timestampedBy the timestamp service whose newest timestamp it carries, or null
     * @param timestampValidUntil when the certificates its newest timestamp rests on start running out, or null
     *                      without a timestamp
     * @return the version as it was written
     */
    public SealedVersion add(
            int documentId,
            String sha256,
            long sizeBytes,
            SealLevel sealLevel,
            @Nullable String timestampedBy,
            @Nullable Instant timestampValidUntil) {
        return query("""
                        INSERT INTO member_document_version(document_id, version, sha256, size_bytes, seal_level,
                                                            timestamped_by, timestamp_valid_until)
                        SELECT :document_id, coalesce(max(version), 0) + 1, :content_hash, :size_bytes, :seal_level,
                               :timestamped_by, :timestamp_valid_until
                        FROM member_document_version
                        WHERE document_id = :document_id
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("document_id", documentId)
                        .bind("content_hash", sha256)
                        .bind("size_bytes", sizeBytes)
                        .bind("seal_level", sealLevel)
                        .bind("timestamped_by", timestampedBy)
                        .bind("timestamp_valid_until", timestampValidUntil, INSTANT_TIMESTAMP))
                .map(SealedVersion.map())
                .first()
                .orElseThrow();
    }

    /**
     * Current versions sealed without a timestamp, of stations that run here: the ones where adding a
     * timestamp never failed first, oldest first, then the others in the order it last failed. A version of a
     * station that moved away stays as it is, since the station's documents live on elsewhere.
     *
     * @param limit how many at most
     * @return the versions
     */
    public List<SealedVersion> currentWithoutTimestamp(int limit) {
        return query("""
                        SELECT %s FROM member_document_version
                        WHERE superseded_at IS NULL
                          AND seal_level = 'BASELINE_B'
                          AND %s
                        ORDER BY timestamps_failed_at NULLS FIRST, sealed_at, id
                        LIMIT :limit;""", COLUMNS, STATION_HERE)
                .single(call().bind("limit", limit))
                .map(SealedVersion.map())
                .all();
    }

    /**
     * Current versions whose newest timestamp rests on a certificate that ends before the given time, of
     * stations that run here: the ones where renewing never failed first, the soonest ending first, then the
     * others in the order it last failed.
     *
     * @param before the time the certificates have to outlast
     * @param limit  how many at most
     * @return the versions
     */
    public List<SealedVersion> currentWithTimestampEndingBefore(Instant before, int limit) {
        return query("""
                        SELECT %s FROM member_document_version
                        WHERE superseded_at IS NULL
                          AND timestamp_valid_until < :before
                          AND %s
                        ORDER BY timestamps_failed_at NULLS FIRST, timestamp_valid_until, id
                        LIMIT :limit;""", COLUMNS, STATION_HERE)
                .single(call().bind("before", before, INSTANT_TIMESTAMP).bind("limit", limit))
                .map(SealedVersion.map())
                .all();
    }

    /**
     * Notes that adding a later timestamp to a version failed for a reason of the version itself, which puts
     * it behind every version where it did not.
     *
     * @param versionId the version
     * @param at        when it failed
     */
    public void markTimestampsFailed(int versionId, Instant at) {
        query("""
                        UPDATE member_document_version
                        SET timestamps_failed_at = :failed_at
                        WHERE id = :id;""")
                .single(call().bind("id", versionId).bind("failed_at", at, INSTANT_TIMESTAMP))
                .update();
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
