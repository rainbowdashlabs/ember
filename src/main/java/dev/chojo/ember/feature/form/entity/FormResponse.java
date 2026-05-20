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
 * Represents a member's response to a form, containing metadata about the submission.
 *
 * @param id          unique response identifier
 * @param formId      the form this response belongs to
 * @param memberId    the member who the response is for
 * @param submittedBy the member who actually submitted the response (may differ from memberId for managed members)
 * @param submittedAt timestamp of the initial submission
 * @param updatedAt   timestamp of the last update
 */
public record FormResponse(int id, int formId, int memberId, int submittedBy, Instant submittedAt, Instant updatedAt) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<FormResponse> map() {
        return row -> new FormResponse(
                row.getInt("id"),
                row.getInt("form_id"),
                row.getInt("member_id"),
                row.getInt("submitted_by"),
                row.get("submitted_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
