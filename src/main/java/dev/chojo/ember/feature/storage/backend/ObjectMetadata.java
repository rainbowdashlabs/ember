/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import java.util.Optional;

/**
 * Metadata carried alongside every stored object. Filesystem backends persist this as a
 * JSON sidecar next to the bytes; S3 maps it to native object metadata. Producers never
 * write sidecars directly — {@link StorageBackend} implementations are responsible for
 * round-tripping this record on store / read.
 *
 * @param contentType      MIME type detected at upload (after magic-byte sniffing for
 *                         image categories) — authoritative for download {@code Content-Type}.
 * @param sha256           Hex SHA-256 of the bytes as written. Empty until populated by the
 *                         digesting stream wrapper that drives the upload.
 * @param originalFilename Optional client-supplied filename, used for attachment downloads.
 * @param contentEncoding  Optional {@code Content-Encoding} (e.g. {@code gzip}) — the producer
 *                         already compressed the bytes and download responses must advertise it.
 */
public record ObjectMetadata(
        String contentType, String sha256, Optional<String> originalFilename, Optional<String> contentEncoding) {

    /**
     * Convenience constructor for the common case: known MIME type, no filename, no encoding.
     */
    public static ObjectMetadata of(String contentType) {
        return new ObjectMetadata(contentType, "", Optional.empty(), Optional.empty());
    }

    /**
     * Constructor variant that pins a content type and original filename.
     */
    public static ObjectMetadata of(String contentType, String originalFilename) {
        return new ObjectMetadata(contentType, "", Optional.ofNullable(originalFilename), Optional.empty());
    }

    /**
     * Returns a copy with the SHA-256 hash filled in.
     */
    public ObjectMetadata withSha256(String hash) {
        return new ObjectMetadata(contentType, hash, originalFilename, contentEncoding);
    }

    /**
     * Returns a copy with the content encoding filled in.
     */
    public ObjectMetadata withContentEncoding(String encoding) {
        return new ObjectMetadata(contentType, sha256, originalFilename, Optional.ofNullable(encoding));
    }
}
