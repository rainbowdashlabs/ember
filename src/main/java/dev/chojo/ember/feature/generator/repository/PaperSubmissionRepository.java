/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.generator.entity.PaperState;
import dev.chojo.ember.feature.generator.entity.PaperSubmission;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The scans of signed paper copies handed in for the documents appointments ask for, and what became of
 * them.
 *
 * <p>A submission whose scan was deleted stays on record without it, but is read by nothing here: it no
 * longer stands, cannot be reviewed and does not show, so the document is open again.
 */
@Singleton
public class PaperSubmissionRepository {
    private static final String COLUMNS = """
            id, event_id, event_date, template_id, member_id, document_id, state, submitted_at, reviewed_at,
            reject_reason""";

    /**
     * What a submission is about: one participant, one document and one date of one appointment.
     *
     * @param stationId  the station of the appointment
     * @param eventId    the appointment
     * @param eventDate  the date
     * @param templateId the document asked for
     * @param memberId   the participant
     */
    public record Subject(int stationId, int eventId, LocalDate eventDate, int templateId, int memberId) {}

    /**
     * Holds the scan that waits or was confirmed for a participant's document on a date, so a second
     * hand-in cannot slip in beside it.
     *
     * @param subject what the scan is for
     * @return the scan that stands, or empty where none does
     */
    public Optional<PaperSubmission> lockStanding(Subject subject) {
        return query("""
                        SELECT %s FROM event_document_submission
                        WHERE event_id = :event_id
                          AND event_date = :event_date
                          AND template_id = :template_id
                          AND member_id = :member_id
                          AND state IN ('SUBMITTED', 'CONFIRMED')
                          AND document_id IS NOT NULL
                        FOR UPDATE;""", COLUMNS)
                .single(call().bind("event_id", subject.eventId())
                        .bind("event_date", subject.eventDate())
                        .bind("template_id", subject.templateId())
                        .bind("member_id", subject.memberId()))
                .map(PaperSubmission.map())
                .first();
    }

    /**
     * Records a scan handed in, unless another one came to stand for the same subject in the meantime.
     *
     * @param subject     what the scan is for
     * @param documentId  the scan as filed
     * @param submittedBy who handed it in
     * @param confirmed   whether it counts as confirmed at once, by whoever handed it in
     * @return the submission, or empty where another scan handed in at the same time stands already
     */
    public Optional<PaperSubmission> create(Subject subject, int documentId, int submittedBy, boolean confirmed) {
        return query("""
                        INSERT INTO event_document_submission(station_id, event_id, event_date, template_id, member_id,
                                                              document_id, state, submitted_by, reviewed_at,
                                                              reviewed_by)
                        VALUES (:station_id, :event_id, :event_date, :template_id, :member_id, :document_id, :state,
                                :submitted_by, CASE WHEN :confirmed THEN now() END,
                                CASE WHEN :confirmed THEN :submitted_by END)
                        ON CONFLICT DO NOTHING
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("station_id", subject.stationId())
                        .bind("event_id", subject.eventId())
                        .bind("event_date", subject.eventDate())
                        .bind("template_id", subject.templateId())
                        .bind("member_id", subject.memberId())
                        .bind("document_id", documentId)
                        .bind("state", confirmed ? PaperState.CONFIRMED : PaperState.SUBMITTED)
                        .bind("submitted_by", submittedBy)
                        .bind("confirmed", confirmed))
                .map(PaperSubmission.map())
                .first();
    }

    /**
     * Removes a submission, leaving its scan where it is filed.
     *
     * @param id the submission
     */
    public void delete(int id) {
        query("DELETE FROM event_document_submission WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }

    /**
     * Removes a submission that still waits, leaving its scan where it is filed.
     *
     * @param id the submission
     * @return the submission as it stood, or empty where it no longer waited
     */
    public Optional<PaperSubmission> deleteWaiting(int id) {
        return query("""
                        DELETE FROM event_document_submission
                        WHERE id = :id AND state = 'SUBMITTED'
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("id", id))
                .map(PaperSubmission.map())
                .first();
    }

    /**
     * Marks the confirmed scan of a participant's document on a date as withdrawn, once the agreement it was
     * confirmed for was withdrawn, so it no longer settles the document and a new scan can be handed in.
     *
     * @param eventId    the appointment
     * @param eventDate  the date
     * @param templateId the document
     * @param memberId   the participant
     * @return whether a confirmed scan stood and is withdrawn now
     */
    public boolean withdrawConfirmed(int eventId, LocalDate eventDate, int templateId, int memberId) {
        return query("""
                        UPDATE event_document_submission
                        SET state = 'WITHDRAWN'
                        WHERE event_id = :event_id
                          AND event_date = :event_date
                          AND template_id = :template_id
                          AND member_id = :member_id
                          AND state = 'CONFIRMED';""")
                .single(call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("template_id", templateId)
                        .bind("member_id", memberId))
                .update()
                .changed();
    }

    /**
     * @param stationId the station
     * @param eventId   the appointment
     * @param id        the submission
     * @return the submission, where it was handed in for that appointment of that station and its scan is
     *         still filed
     */
    public Optional<PaperSubmission> find(int stationId, int eventId, int id) {
        return query("""
                        SELECT %s FROM event_document_submission
                        WHERE id = :id AND station_id = :station_id AND event_id = :event_id
                          AND document_id IS NOT NULL;""", COLUMNS)
                .single(call().bind("id", id).bind("station_id", stationId).bind("event_id", eventId))
                .map(PaperSubmission.map())
                .first();
    }

    /**
     * Confirms or turns down a scan that waits.
     *
     * @param id         the submission
     * @param state      what it becomes
     * @param reviewerId the manager deciding
     * @param reason     why it was turned down, null where it is confirmed
     * @return the submission as it now stands, or empty where it no longer waited
     */
    public Optional<PaperSubmission> review(int id, PaperState state, int reviewerId, @Nullable String reason) {
        return query("""
                        UPDATE event_document_submission
                        SET state         = :state,
                            reviewed_at   = now(),
                            reviewed_by   = :reviewed_by,
                            reject_reason = :reason
                        WHERE id = :id AND state = 'SUBMITTED' AND document_id IS NOT NULL
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("id", id)
                        .bind("state", state)
                        .bind("reviewed_by", reviewerId)
                        .bind("reason", reason))
                .map(PaperSubmission.map())
                .first();
    }

    /**
     * The latest scan still filed for each document of each participant on a date of an appointment,
     * whatever became of it.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @param memberIds the participants
     * @return at most one submission per document and participant
     */
    public List<PaperSubmission> latest(int eventId, LocalDate eventDate, Collection<Integer> memberIds) {
        return query("""
                        SELECT DISTINCT ON (template_id, member_id) %s
                        FROM event_document_submission
                        WHERE event_id = :event_id
                          AND event_date = :event_date
                          AND member_id = ANY(:member_ids)
                          AND document_id IS NOT NULL
                        ORDER BY template_id, member_id, submitted_at DESC, id DESC;""", COLUMNS)
                .single(call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(PaperSubmission.map())
                .all();
    }
}
