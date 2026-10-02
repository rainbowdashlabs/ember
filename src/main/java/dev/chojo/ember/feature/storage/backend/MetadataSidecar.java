/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Optional;

/**
 * The JSON sidecar a tree-shaped backend writes beside every object. Flat on purpose, with an absent
 * filename or encoding written as an empty string, so its shape never changes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetadataSidecar(String contentType, String sha256, String originalFilename, String contentEncoding) {

    public static MetadataSidecar from(ObjectMetadata metadata) {
        return new MetadataSidecar(
                metadata.contentType(),
                metadata.sha256(),
                metadata.originalFilename().orElse(""),
                metadata.contentEncoding().orElse(""));
    }

    public ObjectMetadata toObjectMetadata() {
        return new ObjectMetadata(
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                sha256 == null ? "" : sha256,
                present(originalFilename),
                present(contentEncoding));
    }

    private static Optional<String> present(String value) {
        return value == null || value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
