/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.signing.entity.AppointmentRequest;
import dev.chojo.ember.feature.signing.entity.DueReminder;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.GuardianLink;
import dev.chojo.ember.feature.signing.entity.ManagedSignatureRequest;
import dev.chojo.ember.feature.signing.entity.PendingSignature;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignatureSummary;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PSQLException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String LIVE_INDEX = "signing_request_live_idx";
    private static final int MAX_CAUSE_DEPTH = 12;
    private static final String ASKED_OF = """
            ((f.role IN ('PARTICIPANT', 'ISSUER') AND f.signer_id = :member_id)
              OR (f.role = 'GUARDIAN' AND f.signer_id = :member_id AND r.member_id = ANY (:ward_ids))
              OR (f.role = 'ANY_GUARDIAN' AND r.member_id = ANY (:ward_ids))
              OR (f.role = 'PARTICIPANT' AND f.signer_id = ANY (:ward_ids)))""";

    /**
     * What {@link AppointmentRequest#map()} reads: the request {@code r} and the template and member of the
     * generation {@code g} its copy came from.
     */
    private static final String APPOINTMENT_COLUMNS = "g.template_id AS generation_template_id, "
            + "g.member_id AS generation_member_id, " + SqlSupport.alias("r", SignatureRequest.COLUMNS);

    /**
     * Whether an open field behind the alias {@code f} is one nobody can sign: a guardian place or an issuer
     * field that names nobody, a participant field whose member is gone or has to sign through a guardian
     * and has none, and a field for any guardian of a member who has none.
     */
    private static final String NOBODY_CAN_SIGN = """
            f.state = 'OPEN'
            AND (f.signer_id IS NULL AND f.role <> 'ANY_GUARDIAN'
              OR f.role = 'PARTICIPANT' AND f.capacity = 'MEMBER_THROUGH_ACCOUNT'
                 AND NOT EXISTS (SELECT 1 FROM member_manager m WHERE m.managed_id = f.signer_id)
              OR f.role = 'ANY_GUARDIAN'
                 AND NOT EXISTS (SELECT 1 FROM member_manager m WHERE m.managed_id = f.member_id))""";

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
        addFields(created.id(), request.memberId(), fields);
        return created;
    }

    /**
     * Writes a request with its fields for a document no template here generated: one a partner station
     * handed out for its appointment, which says itself how long it is kept and whether a signer's copy
     * carries it.
     *
     * @param request what the request is about
     * @param fields  the fields it asks to be signed, in their order
     * @return the request as written
     */
    public SignatureRequest createForPartner(
            SignatureRequest.PartnerDraft request, List<RequestedSignature.Draft> fields) {
        var created = SqlSupport.insertReturning(
                """
                        INSERT INTO signing_request(station_id, document_id, member_id, member_name, content_sha256,
                                                    retention_months, copy_attached)
                        VALUES (:station_id, :document_id, :member_id, :member_name, :content_hash, :retention_months,
                                :copy_attached)
                        RETURNING %s;""",
                call().bind("station_id", request.stationId())
                        .bind("document_id", request.documentId())
                        .bind("member_id", request.memberId())
                        .bind("member_name", request.memberName())
                        .bind("content_hash", request.contentSha256())
                        .bind("retention_months", request.retentionMonths())
                        .bind("copy_attached", request.copyAttached()),
                SignatureRequest.map(),
                SignatureRequest.COLUMNS);
        addFields(created.id(), request.memberId(), fields);
        return created;
    }

    private static void addFields(int requestId, int memberId, List<RequestedSignature.Draft> fields) {
        for (var field : fields) {
            query("""
                    INSERT INTO signing_request_field(request_id, field_name, role, member_id, signer_id, signer_name,
                                                      capacity, statement)
                    VALUES (:request_id, :field_name, :role, :member_id, :signer_id, :signer_name, :capacity,
                            :statement);""")
                    .single(call().bind("request_id", requestId)
                            .bind("field_name", field.fieldName())
                            .bind("role", field.role())
                            .bind("member_id", memberId)
                            .bind("signer_id", field.signerId())
                            .bind("signer_name", field.signerName())
                            .bind("capacity", field.capacity())
                            .bind("statement", field.statement()))
                    .insert();
        }
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

    /**
     * Whether a write failed because it would have given a generated document a second request that is open
     * or complete. The check of {@link #liveFor} cannot see a request another transaction has written but
     * not committed yet; the database's unique index on the live request of a generated document can, and
     * refuses the later of the two.
     *
     * @param failure what the write threw
     * @return whether it is that clash
     */
    public static boolean isSecondLiveRequest(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof PSQLException refused && UNIQUE_VIOLATION.equals(refused.getSQLState())) {
                var message = refused.getServerErrorMessage();
                return message != null && LIVE_INDEX.equals(message.getConstraint());
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /**
     * The request of a generated document that is open or complete, the newest where a correction left
     * more than one.
     *
     * @param generationId the generation log entry
     * @return the request, or empty where none is
     */
    public Optional<SignatureRequest> findLiveFor(int generationId) {
        return query("""
                        SELECT %s FROM signing_request
                        WHERE generation_id = :generation_id
                          AND state IN ('OPEN', 'COMPLETE')
                        ORDER BY id DESC
                        LIMIT 1;""", SignatureRequest.COLUMNS)
                .single(call().bind("generation_id", generationId))
                .map(SignatureRequest.map())
                .first();
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
     * @param requestIds the requests
     * @return the fields of all of them, each request's in the order they were asked for
     */
    public List<RequestedSignature> fieldsOfAll(List<Integer> requestIds) {
        return query("""
                        SELECT %s FROM signing_request_field
                        WHERE request_id = ANY (:request_ids)
                        ORDER BY request_id, id;""", RequestedSignature.COLUMNS)
                .single(call().bind("request_ids", requestIds, PostgreSqlTypes.INTEGER))
                .map(RequestedSignature.map())
                .all();
    }

    /**
     * The latest request on the copies of each document an appointment asks for, per participant, for one
     * of its dates. A request a corrected copy superseded does not count.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @param memberIds the participants
     * @return at most one request per document and participant
     */
    public List<AppointmentRequest> latestForAppointment(
            int eventId, LocalDate eventDate, Collection<Integer> memberIds) {
        return query("""
                        SELECT DISTINCT ON (g.template_id, g.member_id) %s
                        FROM signing_request r
                                 JOIN document_generation g ON g.id = r.generation_id
                        WHERE g.event_id = :event_id
                          AND g.event_date = :event_date
                          AND g.member_id = ANY (:member_ids)
                          AND r.state <> 'SUPERSEDED'
                        ORDER BY g.template_id, g.member_id, r.id DESC;""", APPOINTMENT_COLUMNS)
                .single(call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(AppointmentRequest.map())
                .all();
    }

    /**
     * The requests on a participant's copies for one date of an appointment that are open or complete.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @param memberId  the participant
     * @return the requests, oldest first
     */
    public List<AppointmentRequest> liveForAppointment(int eventId, LocalDate eventDate, int memberId) {
        return forAppointment(eventId, eventDate, memberId, "r.state IN ('OPEN', 'COMPLETE')");
    }

    /**
     * The requests on a participant's copies for one date of an appointment that still wait for a
     * signature.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @param memberId  the participant
     * @return the requests, oldest first
     */
    public List<AppointmentRequest> openForAppointment(int eventId, LocalDate eventDate, int memberId) {
        return forAppointment(eventId, eventDate, memberId, "r.state = 'OPEN'");
    }

    /**
     * @param stateCondition a condition on the request {@code r}, one of the constant ones above
     */
    private static List<AppointmentRequest> forAppointment(
            int eventId, LocalDate eventDate, int memberId, String stateCondition) {
        return query("""
                        SELECT %s
                        FROM signing_request r
                                 JOIN document_generation g ON g.id = r.generation_id
                        WHERE g.event_id = :event_id
                          AND g.event_date = :event_date
                          AND g.member_id = :member_id
                          AND %s
                        ORDER BY r.id;""", APPOINTMENT_COLUMNS, stateCondition)
                .single(call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("member_id", memberId))
                .map(AppointmentRequest.map())
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
     * The open fields due for a reminder: of an open request whose document is still there and whose member
     * is still at the station, asked for at least one interval ago and not reminded of within the last
     * interval, with fewer reminders sent than the limit. A field whose signer has left is not due, and
     * neither is a guardian's field whose guardian no longer looks after the member.
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
                                 JOIN station_member m ON m.id = r.member_id AND NOT m.former
                                 LEFT JOIN station_member s ON s.id = f.signer_id
                        WHERE r.state = 'OPEN'
                          AND f.state = 'OPEN'
                          AND (s.id IS NULL OR NOT s.former)
                          AND (f.role <> 'GUARDIAN'
                            OR EXISTS (SELECT 1 FROM member_manager mm
                                       WHERE mm.manager_id = f.signer_id AND mm.managed_id = r.member_id))
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
                          AND %s
                        ORDER BY r.created_at, r.id, f.id;""", SqlSupport.alias("f", RequestedSignature.COLUMNS), ASKED_OF)
                .single(call().bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("ward_ids", wardIds, PostgreSqlTypes.INTEGER))
                .map(PendingSignature.map())
                .all();
    }

    /**
     * Whether a field at the station asks a member, whatever state it and its request are in, by the same
     * rule as {@link #pendingFor}: so a member can be told that a field of theirs no longer waits, while
     * anybody else learns nothing about it.
     *
     * @param stationId the station
     * @param memberId  the member
     * @param wardIds   the members in their care
     * @param fieldId   the field
     * @return whether the field asks them
     */
    public boolean asks(int stationId, int memberId, List<Integer> wardIds, int fieldId) {
        return SqlSupport.exists(
                """
                        SELECT 1
                        FROM signing_request_field f
                                 JOIN signing_request r ON r.id = f.request_id
                        WHERE r.station_id = :station_id
                          AND f.id = :field_id
                          AND %s;""".formatted(ASKED_OF),
                call().bind("station_id", stationId)
                        .bind("field_id", fieldId)
                        .bind("member_id", memberId)
                        .bind("ward_ids", wardIds, PostgreSqlTypes.INTEGER));
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

    /**
     * How the signatures on documents stand, by the newest request on each.
     *
     * @param documentIds the member documents
     * @return the summary per document id, none for a document nobody was asked to sign
     */
    public Map<Integer, SignatureSummary> summariesOfDocuments(Collection<Integer> documentIds) {
        return summariesBy("document_id", documentIds);
    }

    /**
     * How the signatures on generated documents stand, by the newest request on each.
     *
     * @param generationIds the generation log entries
     * @return the summary per generation id, none for a document nobody was asked to sign
     */
    public Map<Integer, SignatureSummary> summariesOfGenerations(Collection<Integer> generationIds) {
        return summariesBy("generation_id", generationIds);
    }

    private Map<Integer, SignatureSummary> summariesBy(String keyColumn, Collection<Integer> ids) {
        if (ids.isEmpty()) return Map.of();
        return query("""
                        SELECT r.%1$s AS keyed_by, r.uid, r.state,
                               count(f.id) FILTER (WHERE f.state IN ('SIGNED', 'PAPER_CONFIRMED')) AS signed,
                               count(f.id) FILTER (WHERE f.state NOT IN ('WAIVED', 'WITHDRAWN')) AS expected,
                               count(f.id) FILTER (WHERE f.state = 'OPEN') AS open,
                               count(f.id) FILTER (WHERE %2$s) AS nobody_can_sign
                        FROM (SELECT DISTINCT ON (%1$s) id, uid, state, %1$s
                              FROM signing_request
                              WHERE %1$s = ANY (:ids)
                              ORDER BY %1$s, id DESC) r
                                 LEFT JOIN signing_request_field f ON f.request_id = r.id
                        GROUP BY r.%1$s, r.id, r.uid, r.state;""", keyColumn, NOBODY_CAN_SIGN)
                .single(call().bind("ids", List.copyOf(ids), PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("keyed_by"), SignatureSummary.read(row)))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The open fields of a request that nobody can sign.
     *
     * @param requestId the request
     * @return the ids of those fields
     */
    public Set<Integer> fieldsNobodyCanSign(int requestId) {
        return Set.copyOf(query("""
                        SELECT f.id FROM signing_request_field f
                        WHERE f.request_id = :request_id
                          AND %s;""", NOBODY_CAN_SIGN)
                .single(call().bind("request_id", requestId))
                .map(row -> row.getInt("id"))
                .all());
    }

    /**
     * The documents a request could be asked anew on after a correction: generated at its station for its
     * member after its own document, still filed, and not asked to be signed yet.
     *
     * @param request the request
     * @param limit   how many at most
     * @return the documents, the newest first
     */
    public List<ManagedSignatureRequest.Correction> corrections(SignatureRequest request, int limit) {
        if (request.memberId() == null) return List.of();
        return query("""
                        SELECT g.id, g.generated_at, t.name AS template_name
                        FROM document_generation g
                                 JOIN document_template t ON t.id = g.template_id
                                 JOIN signing_request r ON r.id = :request_id
                        WHERE g.station_id = r.station_id
                          AND g.member_id = r.member_id
                          AND g.document_id IS NOT NULL
                          AND (g.id > r.generation_id
                            OR (r.generation_id IS NULL AND g.generated_at > r.created_at))
                          AND NOT EXISTS (SELECT 1 FROM signing_request other
                                          WHERE other.generation_id = g.id
                                            AND other.state IN ('OPEN', 'COMPLETE'))
                        ORDER BY g.generated_at DESC, g.id DESC
                        LIMIT :limit;""")
                .single(call().bind("request_id", request.id()).bind("limit", limit))
                .map(ManagedSignatureRequest.Correction.map())
                .all();
    }
}
