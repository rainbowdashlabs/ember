/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A scan of a signed paper copy, handed in for one participant, one document an appointment asks for and
 * one date of the appointment.
 *
 * @param id           the submission
 * @param eventId      the appointment
 * @param eventDate    the date of the appointment
 * @param templateId   the document asked for
 * @param memberId     the participant
 * @param documentId   the scan, filed in the participant's documents
 * @param state        whether it waits, was confirmed or was turned down
 * @param submittedAt  when it was handed in
 * @param reviewedAt   when it was confirmed or turned down, or null while it waits
 * @param rejectReason why it was turned down, or null unless it was
 */
public record PaperSubmission(
        int id,
        int eventId,
        LocalDate eventDate,
        int templateId,
        int memberId,
        int documentId,
        PaperState state,
        Instant submittedAt,
        @Nullable Instant reviewedAt,
        @Nullable String rejectReason) {

    public static RowMapping<PaperSubmission> map() {
        return row -> new PaperSubmission(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getObject("event_date", LocalDate.class),
                row.getInt("template_id"),
                row.getInt("member_id"),
                row.getInt("document_id"),
                row.getEnum("state", PaperState.class),
                row.get("submitted_at", INSTANT_TIMESTAMP),
                row.get("reviewed_at", INSTANT_TIMESTAMP),
                row.getString("reject_reason"));
    }
}
