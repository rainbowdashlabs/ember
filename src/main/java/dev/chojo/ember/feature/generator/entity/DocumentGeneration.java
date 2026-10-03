/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One document generated from a template, as the log keeps it.
 *
 * @param id              the entry identifier
 * @param stationId       the station the document was filed at
 * @param templateId      the template it came from
 * @param templateVersion the version of the template at the time
 * @param memberId        the member it is about, or null once they were deleted
 * @param generatedBy     who generated it, or null once they are gone
 * @param generatedAt     when it was generated
 * @param selfService     whether it was generated through self service
 * @param documentId      the member document it was filed as, or null once that was deleted
 * @param fileSha256      the SHA-256 of the file as lowercase hex
 */
public record DocumentGeneration(
        int id,
        int stationId,
        int templateId,
        int templateVersion,
        @Nullable Integer memberId,
        @Nullable Integer generatedBy,
        Instant generatedAt,
        boolean selfService,
        @Nullable Integer documentId,
        String fileSha256) {

    public static RowMapping<DocumentGeneration> map() {
        return row -> new DocumentGeneration(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("template_id"),
                row.getInt("template_version"),
                row.getObject("member_id", Integer.class),
                row.getObject("generated_by", Integer.class),
                row.get("generated_at", INSTANT_TIMESTAMP),
                row.getBoolean("self_service"),
                row.getObject("document_id", Integer.class),
                row.getString("file_sha256"));
    }
}
