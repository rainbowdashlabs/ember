/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a form (survey/questionnaire) belonging to a station.
 *
 * @param id             unique form identifier
 * @param stationId      the station this form belongs to
 * @param title          display title of the form
 * @param description    optional description shown to respondents
 * @param status         lifecycle status (DRAFT, OPEN, CLOSED)
 * @param shuffleQuestions whether question order should be randomized for respondents
 * @param allowEdit      whether respondents may edit their submitted response
 * @param startAt        optional start time after which the form accepts responses
 * @param endAt          optional end time after which the form stops accepting responses
 * @param closedAt       timestamp when the form was explicitly closed, or {@code null}
 * @param createdBy      member ID of the form creator
 * @param createdAt      creation timestamp
 * @param updatedAt      last-update timestamp
 */
public record Form(
        int id,
        int stationId,
        String title,
        String description,
        FormStatus status,
        boolean shuffleQuestions,
        boolean allowEdit,
        Instant startAt,
        Instant endAt,
        Instant closedAt,
        int createdBy,
        Instant createdAt,
        Instant updatedAt) {

    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<Form> map() {
        return row -> new Form(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("title"),
                row.getString("description"),
                FormStatus.valueOf(row.getString("status")),
                row.getBoolean("shuffle_questions"),
                row.getBoolean("allow_edit"),
                row.get("start_at", INSTANT_TIMESTAMP),
                row.get("end_at", INSTANT_TIMESTAMP),
                row.get("closed_at", INSTANT_TIMESTAMP),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }

    /** Lifecycle status of a form. */
    public enum FormStatus {
        DRAFT,
        OPEN,
        CLOSED
    }
}
