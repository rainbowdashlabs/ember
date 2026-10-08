/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.signing.entity.DueReminder;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.GuardianLink;
import dev.chojo.ember.feature.signing.entity.PendingSignature;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Requests for signatures on generated documents and their fields: who must sign what, and where each
 * field stands.
 */
@Singleton
public class SignatureRequestRepository {

    /**
     * Writes a request with its fields, the retention copied from the template the document came from.
     *
     * @param request what the request is about
     * @param fields  the fields it asks to be signed, in their order
     * @return the request as written
     */
    public SignatureRequest create(SignatureRequest.Draft request, List<RequestedSignature.Draft> fields) {
        var created = SqlSupport.insertReturning(
                """
                        INSERT INTO signing_request(station_id, generation_id, document_id, member_id, member_name,
                                                    content_sha256, retention_months, copy_attached, created_by)
                        VALUES (:station_id, :generation_id, :document_id, :member_id, :member_name, :content_hash,
                                (SELECT signature_retention_months FROM document_template WHERE id = :template_id),
                                coalesce((SELECT signed_copy_attached FROM document_template WHERE id = :template_id),
                                         FALSE),
                                :created_by)
                        RETURNING %s;""",
                call().bind("station_id", request.stationId())
                        .bind("generation_id", request.generationId())
                        .bind("document_id", request.documentId())
                        .bind("member_id", request.memberId())
                        .bind("member_name", request.memberName())
                        .bind("content_hash", request.contentSha256())
                        .bind("template_id", request.templateId())
                        .bind("created_by", request.createdBy()),
                SignatureRequest.map(),
                SignatureRequest.COLUMNS);
        for (var field : fields) {
            query("""
                    INSERT INTO signing_request_field(request_id, field_name, role, member_id, signer_id, signer_name,
                                                      capacity, statement)
                    VALUES (:request_id, :field_name, :role, :member_id, :signer_id, :signer_name, :capacity,
                            :statement);""")
                    .single(call().bind("request_id", created.id())
                            .bind("field_name", field.fieldName())
                            .bind("role", field.role())
                            .bind("member_id", request.memberId())
                            .bind("signer_id", field.signerId())
                            .bind("signer_name", field.signerName())
                            .bind("capacity", field.capacity())
                            .bind("statement", field.statement()))
                    .insert();
        }
        return created;
    }

    /** @return the request, or empty where none has this uid */
    public Optional<SignatureRequest> findByUid(UUID uid) {
        return query("SELECT %s FROM signing_request WHERE uid = :uid::UUID;", SignatureRequest.COLUMNS)
                .single(call().bind("uid", uid, UUID_STRING))
                .map(SignatureRequest.map())
                .first();
    }

    /** @return the request, or empty where none has this id */
    public Optional<SignatureRequest> findById(int requestId) {
        return SqlSupport.findById("signing_request", SignatureRequest.COLUMNS, requestId, SignatureRequest.map());
    }

    /**
     * Whether a generated document already has a request that is open or complete, which a second request
     * would contradict. A withdrawn or superseded one does not count.
     *
     * @param generationId the generation log entry
     * @return whether such a request exists
     */
    public boolean liveFor(int generationId) {
        return SqlSupport.exists("""
                        SELECT 1 FROM signing_request
                        WHERE generation_id = :generation_id
                          AND state IN ('OPEN', 'COMPLETE');""", call().bind("generation_id", generationId));
    }

    /** @return the fields of a request, in the order they were asked for */
    public List<RequestedSignature> fieldsOf(int requestId) {
        return query("""
                        SELECT %s FROM signing_request_field
                        WHERE request_id = :request_id
                        ORDER BY id;""", RequestedSignature.COLUMNS)
                .single(call().bind("request_id", requestId))
                .map(RequestedSignature.map())
                .all();
    }

    /**
     * Reads a request and holds it until the transaction ends. Every change to a request or its fields
     * takes the request first and its fields after it, so two changes never wait for each other the other
     * way round.
     *
     * @param requestId the request
     * @return the request as it stands, or empty where none has this id
     */
    public Optional<SignatureRequest> lockRequest(int requestId) {
        return query("SELECT %s FROM signing_request WHERE id = :id FOR UPDATE;", SignatureRequest.COLUMNS)
                .single(call().bind("id", requestId))
                .map(SignatureRequest.map())
                .first();
    }

    /**
     * Reads a field and holds it until the transaction ends, so two acts on one field cannot both find it
     * open. Its request is held first ({@link #lockRequest}), so the request cannot be superseded or
     * withdrawn in between.
     *
     * @param requestId the request
     * @param fieldName the field's name
     * @return the field, or empty where the request asks for no such field
     */
    public Optional<RequestedSignature> lockField(int requestId, String fieldName) {
        lockRequest(requestId);
        return query("""
                        SELECT %s FROM signing_request_field
                        WHERE request_id = :request_id AND field_name = :field_name
                        FOR UPDATE;""", RequestedSignature.COLUMNS)
                .single(call().bind("request_id", requestId).bind("field_name", fieldName))
                .map(RequestedSignature.map())
                .first();
    }

    /**
     * Settles an open field.
     *
     * @param fieldId the field
     * @param state   what it becomes, anything but open
     * @param by      who settled it, or null
     * @param byName  their official name, or null
     * @return whether the field was open and is settled now
     */
    public boolean settle(int fieldId, FieldState state, @Nullable Integer by, @Nullable String byName) {
        return query("""
                        UPDATE signing_request_field
                        SET state           = :state,
                            settled_at      = now(),
                            settled_by      = :settled_by,
                            settled_by_name = :settled_by_name
                        WHERE id = :id AND state = 'OPEN';""")
                .single(call().bind("id", fieldId)
                        .bind("state", state)
                        .bind("settled_by", by)
                        .bind("settled_by_name", byName))
                .update()
                .changed();
    }

    /**
     * Withdraws every field of a request that is still open.
     *
     * @param requestId the request
     * @param by        who withdrew them, or null
     * @param byName    their official name, or null
     * @return how many fields were withdrawn
     */
    public int withdrawOpen(int requestId, @Nullable Integer by, @Nullable String byName) {
        return query("""
                        UPDATE signing_request_field
                        SET state           = 'WITHDRAWN',
                            settled_at      = now(),
                            settled_by      = :settled_by,
                            settled_by_name = :settled_by_name
                        WHERE request_id = :request_id AND state = 'OPEN';""")
                .single(call().bind("request_id", requestId)
                        .bind("settled_by", by)
                        .bind("settled_by_name", byName))
                .update()
                .rows();
    }

    /**
     * Closes an open request once none of its fields is open any more: complete where something was signed
     * or confirmed on paper, withdrawn where nothing was.
     *
     * @param requestId the request
     * @return whether the request was closed now
     */
    public boolean closeIfSettled(int requestId) {
        return query("""
                        UPDATE signing_request r
                        SET state     = CASE
                                            WHEN EXISTS (SELECT 1 FROM signing_request_field f
                                                         WHERE f.request_id = r.id
                                                           AND f.state IN ('SIGNED', 'PAPER_CONFIRMED'))
                                                THEN 'COMPLETE'
                                            ELSE 'WITHDRAWN'
                                        END,
                            closed_at = now()
                        WHERE r.id = :id
                          AND r.state = 'OPEN'
                          AND NOT EXISTS (SELECT 1 FROM signing_request_field f
                                          WHERE f.request_id = r.id AND f.state = 'OPEN');""").single(call().bind("id", requestId)).update().changed();
    }

    /**
     * Records the sealed version that first shows the fields of a request settled since the version before.
     *
     * @param requestId the request
     * @param sha256    SHA-256 of the sealed version, lower-case hexadecimal
     * @return how many fields it shows settled for the first time
     */
    public int markSettledSealed(int requestId, String sha256) {
        return query("""
                        UPDATE signing_request_field
                        SET sealed_sha256 = :sealed_hash
                        WHERE request_id = :request_id
                          AND state <> 'OPEN'
                          AND sealed_sha256 IS NULL;""")
                .single(call().bind("request_id", requestId).bind("sealed_hash", sha256))
                .update()
                .rows();
    }

    /**
     * The open fields due for a reminder: of an open request whose document is still there, asked for at
     * least one interval ago and not reminded of within the last interval, with fewer reminders sent than
     * the limit.
     *
     * @param now          the time to measure against
     * @param interval     how long a field waits before its first reminder and between two
     * @param maxReminders how many reminders a field gets at most
     * @param limit        how many fields to read at most
     * @return the fields with their station, the one waiting longest first
     */
    public List<DueReminder> dueForReminder(Instant now, Duration interval, int maxReminders, int limit) {
        return query("""
                        SELECT %s, r.uid AS request_uid, r.document_id, r.member_name AS request_member_name,
                               d.title AS document_title, r.station_id AS request_station_id,
                               f.reminders_sent AS field_reminders_sent
                        FROM signing_request_field f
                                 JOIN signing_request r ON r.id = f.request_id
                                 JOIN member_document d ON d.id = r.document_id
                        WHERE r.state = 'OPEN'
                          AND f.state = 'OPEN'
                          AND f.reminders_sent < :max_reminders
                          AND coalesce(f.reminded_at, r.created_at) <= :due_before
                        ORDER BY coalesce(f.reminded_at, r.created_at), f.id
                        LIMIT :limit;""", SqlSupport.alias("f", RequestedSignature.COLUMNS))
                .single(call().bind("max_reminders", maxReminders)
                        .bind("due_before", now.minus(interval), INSTANT_TIMESTAMP)
                        .bind("limit", limit))
                .map(DueReminder.map())
                .all();
    }

    /**
     * Counts a reminder for a field, unless a reminder for it was counted since it was read.
     *
     * @param fieldId       the field
     * @param remindersRead how many reminders it had when it was read
     * @param now           when the reminder goes out
     * @return whether this reminder was counted, so it is to be sent
     */
    public boolean markReminded(int fieldId, int remindersRead, Instant now) {
        return query("""
                        UPDATE signing_request_field
                        SET reminded_at    = :now,
                            reminders_sent = reminders_sent + 1
                        WHERE id = :id
                          AND state = 'OPEN'
                          AND reminders_sent = :reminders_read;""")
                .single(call().bind("id", fieldId)
                        .bind("reminders_read", remindersRead)
                        .bind("now", now, INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * Marks a request as replaced by the request for its corrected document. A request that had already
     * stopped waiting keeps the time it did.
     *
     * @param requestId    the request replaced
     * @param supersededBy the request that replaces it
     */
    public void supersede(int requestId, int supersededBy) {
        query("""
                UPDATE signing_request
                SET state         = 'SUPERSEDED',
                    superseded_by = :superseded_by,
                    closed_at     = coalesce(closed_at, now())
                WHERE id = :id;""")
                .single(call().bind("id", requestId).bind("superseded_by", supersededBy))
                .update();
    }

    /**
     * The open fields a member is asked to sign at a station, together with those they sign on behalf of the
     * members in their care or lend their account to: their own fields as member or issuer, the fields of
     * the guardian place they hold, the fields any guardian of a ward may sign, and the member fields of
     * their wards.
     *
     * @param stationId the station
     * @param memberId  the member
     * @param wardIds   the members in their care
     * @return the fields, oldest request first
     */
    public List<PendingSignature> pendingFor(int stationId, int memberId, List<Integer> wardIds) {
        return query("""
                        SELECT %s, r.uid AS request_uid, r.document_id, r.member_name AS request_member_name,
                               d.title AS document_title
                        FROM signing_request_field f
                                 JOIN signing_request r ON r.id = f.request_id
                                 LEFT JOIN member_document d ON d.id = r.document_id
                        WHERE r.station_id = :station_id
                          AND r.state = 'OPEN'
                          AND f.state = 'OPEN'
                          AND ((f.role IN ('PARTICIPANT', 'ISSUER') AND f.signer_id = :member_id)
                            OR (f.role = 'GUARDIAN' AND f.signer_id = :member_id AND r.member_id = ANY (:ward_ids))
                            OR (f.role = 'ANY_GUARDIAN' AND r.member_id = ANY (:ward_ids))
                            OR (f.role = 'PARTICIPANT' AND f.signer_id = ANY (:ward_ids)))
                        ORDER BY r.created_at, r.id, f.id;""", SqlSupport.alias("f", RequestedSignature.COLUMNS))
                .single(call().bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("ward_ids", wardIds, PostgreSqlTypes.INTEGER))
                .map(PendingSignature.map())
                .all();
    }

    /**
     * A signature field of a request at the station, whatever state it and its request are in.
     *
     * @param stationId the station
     * @param fieldId   the field
     * @return the field with its document, or empty where the station has no such field
     */
    public Optional<PendingSignature> findField(int stationId, int fieldId) {
        return query("""
                        SELECT %s, r.uid AS request_uid, r.document_id, r.member_name AS request_member_name,
                               d.title AS document_title
                        FROM signing_request_field f
                                 JOIN signing_request r ON r.id = f.request_id
                                 LEFT JOIN member_document d ON d.id = r.document_id
                        WHERE r.station_id = :station_id
                          AND f.id = :field_id;""", SqlSupport.alias("f", RequestedSignature.COLUMNS))
                .single(call().bind("station_id", stationId).bind("field_id", fieldId))
                .map(PendingSignature.map())
                .first();
    }

    /**
     * The guardian link between two members as it stands, for the evidence of an act that goes through it.
     *
     * @param guardianId the guardian
     * @param memberId   the member in their care
     * @return the link, or empty where there is none
     */
    public Optional<GuardianLink.Stored> guardianLink(int guardianId, int memberId) {
        return query("""
                        SELECT position, created_at, created_by
                        FROM member_manager
                        WHERE manager_id = :manager_id AND managed_id = :managed_id;""")
                .single(call().bind("manager_id", guardianId).bind("managed_id", memberId))
                .map(row -> new GuardianLink.Stored(
                        row.getInt("position"),
                        row.get("created_at", INSTANT_TIMESTAMP),
                        row.getObject("created_by", Integer.class)))
                .first();
    }

    /**
     * Starts the retention of every request whose member has left or was deleted and whose retention has
     * not started yet: kept from the day they left, or from now for a member who is gone without a date,
     * for the request's retention period.
     *
     * <p>A request without a retention period of its own keeps its member's documents only while they
     * are a member, and an archived member gets {@code archivedGraceMonths} on top, since archiving is
     * undone easily and often. Deleting the member ends that at once: a request without a retention
     * period whose member is deleted is due now, also where an earlier archiving had set a later date.
     *
     * @param now                 the time a member deleted without a date counts as gone
     * @param archivedGraceMonths how long a request without a retention period is kept after its member
     *                            was archived
     * @return how many requests started their retention or had it ended by the deletion of their member
     */
    public int startRetention(Instant now, int archivedGraceMonths) {
        int started = query("""
                        UPDATE signing_request r
                        SET retain_until = gone.left_at + make_interval(
                                months => coalesce(r.retention_months, CASE WHEN gone.archived THEN :grace ELSE 0 END))
                        FROM (SELECT q.id, coalesce(sm.former_at, :now) AS left_at, sm.id IS NOT NULL AS archived
                              FROM signing_request q
                                       LEFT JOIN station_member sm ON sm.id = q.member_id
                              WHERE q.retain_until IS NULL
                                AND (sm.id IS NULL OR sm.former)) gone
                        WHERE r.id = gone.id;""")
                .single(call().bind("now", now, INSTANT_TIMESTAMP).bind("grace", archivedGraceMonths))
                .update()
                .rows();
        int ended = query("""
                        UPDATE signing_request
                        SET retain_until = :now
                        WHERE retention_months IS NULL
                          AND member_id IS NULL
                          AND retain_until > :now;""")
                .single(call().bind("now", now, INSTANT_TIMESTAMP))
                .update()
                .rows();
        return started + ended;
    }

    /**
     * Ends the retention of every request whose member is a member again, which keeps it for as long as
     * they stay.
     *
     * @return how many requests are kept again
     */
    public int stopRetention() {
        return query("""
                        UPDATE signing_request r
                        SET retain_until = NULL
                        FROM station_member sm
                        WHERE sm.id = r.member_id
                          AND NOT sm.former
                          AND r.retain_until IS NOT NULL;""").single().update().rows();
    }

    /**
     * @param now   the time to compare against
     * @param limit how many to read at most
     * @return the requests kept no longer, the longest overdue first
     */
    public List<SignatureRequest> expired(Instant now, int limit) {
        return query("""
                        SELECT %s FROM signing_request
                        WHERE retain_until <= :now
                        ORDER BY retain_until, id
                        LIMIT :limit;""", SignatureRequest.COLUMNS)
                .single(call().bind("now", now, INSTANT_TIMESTAMP).bind("limit", limit))
                .map(SignatureRequest.map())
                .all();
    }

    /**
     * Whether a member document still has to be kept once one request on it is gone: another request on it
     * is still kept, or it is about a member who is still at the station.
     *
     * @param documentId the document
     * @param requestId  the request about to go
     * @param now        the time to compare against
     * @return whether something else keeps it
     */
    public boolean keptBesides(int documentId, int requestId, Instant now) {
        return SqlSupport.exists(
                """
                        SELECT 1 FROM signing_request
                        WHERE document_id = :document_id
                          AND id <> :request_id
                          AND (retain_until IS NULL OR retain_until > :now)
                        UNION ALL
                        SELECT 1 FROM member_document_member m
                                 JOIN station_member sm ON sm.id = m.member_id
                        WHERE m.document_id = :document_id
                          AND NOT sm.former;""",
                call().bind("document_id", documentId)
                        .bind("request_id", requestId)
                        .bind("now", now, INSTANT_TIMESTAMP));
    }

    /**
     * Deletes a request with its fields and their evidence.
     *
     * @param requestId the request
     * @return whether it existed
     */
    public boolean delete(int requestId) {
        return SqlSupport.deleteById("signing_request", requestId);
    }
}
