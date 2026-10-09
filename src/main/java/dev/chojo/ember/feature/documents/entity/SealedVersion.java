/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One sealed file of a sealed document. The file is stored under its SHA-256 and never replaced; a
 * later signature files a new version and marks this one superseded, which keeps it.
 *
 * @param id            the version's row id
 * @param documentId    the sealed document it is a version of
 * @param version       its number within the document, counting from 1
 * @param sha256        SHA-256 of the sealed file, lower-case hexadecimal
 * @param sizeBytes     how large the sealed file is
 * @param sealLevel     the level its seal reached
 * @param timestampedBy the timestamp service whose newest timestamp it carries, or null when it carries none
 * @param timestampValidUntil the earliest end of validity among the certificates its newest timestamp rests
 *                      on, or null when it carries no timestamp
 * @param sealedAt      when it was filed
 * @param supersededAt  when a later version took its place, or null while it is the current one
 */
public record SealedVersion(
        int id,
        int documentId,
        int version,
        String sha256,
        long sizeBytes,
        SealLevel sealLevel,
        @Nullable String timestampedBy,
        @Nullable Instant timestampValidUntil,
        Instant sealedAt,
        @Nullable Instant supersededAt) {

    /** @return whether this is the version the document serves */
    public boolean current() {
        return supersededAt == null;
    }

    /** Maps a row of the sealed versions. */
    public static RowMapping<SealedVersion> map() {
        return row -> new SealedVersion(
                row.getInt("id"),
                row.getInt("document_id"),
                row.getInt("version"),
                row.getString("sha256"),
                row.getLong("size_bytes"),
                row.getEnum("seal_level", SealLevel.class),
                row.getString("timestamped_by"),
                row.get("timestamp_valid_until", INSTANT_TIMESTAMP),
                row.get("sealed_at", INSTANT_TIMESTAMP),
                row.get("superseded_at", INSTANT_TIMESTAMP));
    }
}
