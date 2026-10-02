/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import java.util.Optional;

/**
 * Metadata stored with every object: a JSON sidecar on the tree-shaped backends, native object
 * metadata on S3.
 *
 * @param contentType      MIME type, served as the download's {@code Content-Type}
 * @param sha256           hex SHA-256 of the bytes as written; empty until sealed
 * @param originalFilename client-supplied filename for attachment downloads
 * @param contentEncoding  {@code Content-Encoding} such as {@code gzip} when the producer compressed the bytes
 */
public record ObjectMetadata(
        String contentType, String sha256, Optional<String> originalFilename, Optional<String> contentEncoding) {

    public static ObjectMetadata of(String contentType) {
        return new ObjectMetadata(contentType, "", Optional.empty(), Optional.empty());
    }

    public static ObjectMetadata of(String contentType, String originalFilename) {
        return new ObjectMetadata(contentType, "", Optional.ofNullable(originalFilename), Optional.empty());
    }

    public ObjectMetadata withSha256(String hash) {
        return new ObjectMetadata(contentType, hash, originalFilename, contentEncoding);
    }
}
