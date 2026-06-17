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
 * Represents a response to a form. Member-attached responses set {@code memberId}; anonymous
 * public submissions instead populate {@code submitterHash} and leave both member fields as
 * {@code null} / {@code 0}. CONTACT-form submissions additionally track an acknowledgement —
 * {@code acknowledgedAt} / {@code acknowledgedBy} are set the first time a member with
 * {@code PAGE_FORMS_VIEW} marks the submission as handled.
 *
 * @param id             unique response identifier
 * @param formId         the form this response belongs to
 * @param memberId       the member who the response is for, or {@code null} for anonymous
 *                       submissions
 * @param submittedBy    the member who actually submitted the response (may differ from memberId
 *                       for managed members), or {@code null} for anonymous submissions
 * @param submittedAt    timestamp of the initial submission
 * @param updatedAt      timestamp of the last update
 * @param submitterHash  SHA-256 hash identifying the anonymous submitter, or {@code null} for
 *                       member responses
 * @param acknowledgedAt timestamp the submission was first acknowledged, or {@code null} when
 *                       still unhandled / not applicable
 * @param acknowledgedBy member id of the acknowledger, or {@code null} when not yet acknowledged
 *                       (kept as id rather than UUID since the column is local-only)
 */
public record FormResponse(
        int id,
        int formId,
        Integer memberId,
        Integer submittedBy,
        Instant submittedAt,
        Instant updatedAt,
        byte[] submitterHash,
        Instant acknowledgedAt,
        Integer acknowledgedBy) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<FormResponse> map() {
        return row -> new FormResponse(
                row.getInt("id"),
                row.getInt("form_id"),
                row.getObject("member_id", Integer.class),
                row.getObject("submitted_by", Integer.class),
                row.get("submitted_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.getBytes("submitter_hash"),
                row.getObject("acknowledged_at", Instant.class),
                row.getObject("acknowledged_by", Integer.class));
    }
}
