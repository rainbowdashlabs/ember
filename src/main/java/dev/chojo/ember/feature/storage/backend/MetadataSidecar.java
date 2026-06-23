/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Optional;

/**
 * On-disk JSON shape of the metadata file that filesystem-style backends (local, SMB, SFTP)
 * write alongside every stored object. S3 carries metadata natively on the object header
 * + user-metadata fields and never touches this record.
 *
 * <p>The format is deliberately flat — four optional string fields, no nesting — so a future
 * migration tool can move bytes between backends without re-deriving metadata. Missing
 * {@code originalFilename} or {@code contentEncoding} are encoded as empty strings so the JSON
 * shape stays stable regardless of which optionals are populated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetadataSidecar(String contentType, String sha256, String originalFilename, String contentEncoding) {

    /** Captures the persistent fields of {@code metadata} into a typed JSON-friendly record. */
    public static MetadataSidecar from(ObjectMetadata metadata) {
        return new MetadataSidecar(
                metadata.contentType(),
                metadata.sha256(),
                metadata.originalFilename().orElse(""),
                metadata.contentEncoding().orElse(""));
    }

    /** Returns the in-memory {@link ObjectMetadata} this sidecar represents. */
    public ObjectMetadata toObjectMetadata() {
        String ct = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        String sha = sha256 == null ? "" : sha256;
        Optional<String> filename = originalFilename == null || originalFilename.isEmpty()
                ? Optional.empty()
                : Optional.of(originalFilename);
        Optional<String> encoding =
                contentEncoding == null || contentEncoding.isEmpty() ? Optional.empty() : Optional.of(contentEncoding);
        return new ObjectMetadata(ct, sha, filename, encoding);
    }
}
