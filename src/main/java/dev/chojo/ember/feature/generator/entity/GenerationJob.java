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
 * A run that generates one template for many members in the background, with how far it got.
 *
 * @param id            the run
 * @param stationId     the station the documents are filed at
 * @param templateId    the template generated for every member
 * @param templateName  what the template is called
 * @param startedBy     the manager who started it and is the uploader of its documents
 * @param acceptMissing whether a member whose data is incomplete still gets a document with gaps
 * @param startedAt     when it was started
 * @param finishedAt    when the last member was done, or null while it runs
 * @param total         how many members it generates for
 * @param filed         how many documents it filed so far
 * @param failed        how many members it could not file a document for
 */
public record GenerationJob(
        int id,
        int stationId,
        int templateId,
        String templateName,
        int startedBy,
        boolean acceptMissing,
        Instant startedAt,
        @Nullable Instant finishedAt,
        int total,
        int filed,
        int failed) {

    /** @return whether every member of the run is done */
    public boolean finished() {
        return finishedAt != null;
    }

    public static RowMapping<GenerationJob> map() {
        return row -> new GenerationJob(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("template_id"),
                row.getString("template_name"),
                row.getInt("started_by"),
                row.getBoolean("accept_missing"),
                row.get("started_at", INSTANT_TIMESTAMP),
                row.get("finished_at", INSTANT_TIMESTAMP),
                row.getInt("total"),
                row.getInt("filed"),
                row.getInt("failed"));
    }
}
