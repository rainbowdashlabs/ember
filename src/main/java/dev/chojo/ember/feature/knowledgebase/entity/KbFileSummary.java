/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import java.time.Instant;

/**
 * Sparse representation of a KB file for list views and federated browse responses.
 * Contains only the fields needed to render a list row.
 */
public record KbFileSummary(
        int id,
        int stationId,
        Integer folderId,
        String name,
        String description,
        KbFileType fileType,
        Instant updatedAt,
        boolean restricted) {

    public static KbFileSummary of(KbFile file) {
        return new KbFileSummary(
                file.id(),
                file.stationId(),
                file.folderId(),
                file.name(),
                file.description(),
                file.fileType(),
                file.updatedAt(),
                file.restricted());
    }

    /**
     * Converts this summary back to a full KbFile with default values for fields not in the summary.
     * Used when an API contract requires the full entity shape.
     */
    public KbFile toKbFile() {
        return new KbFile(
                id,
                stationId,
                folderId,
                name,
                description,
                fileType,
                null,
                0,
                null,
                null,
                null,
                0,
                0,
                updatedAt,
                updatedAt,
                null,
                null,
                null,
                restricted,
                null);
    }
}
