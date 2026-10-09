/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.signing.entity.PartnerSigning;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * The signing requests at a member's home installation that answer a partner station's appointment, and
 * how far their signed copies have travelled back to it.
 */
@Singleton
public class PartnerSigningRepository {

    /**
     * Writes what a new signing request answers.
     *
     * @param draft the request and the appointment, date and document at the partner
     * @return the row
     */
    public PartnerSigning create(PartnerSigning.Draft draft) {
        return SqlSupport.insertReturning(
                """
                        INSERT INTO partner_signing_request AS p (station_id, request_id, partner_id,
                                                                  partner_station_uid, remote_event_id, event_date,
                                                                  remote_template_id, template_version)
                        VALUES (:station_id, :request_id, :partner_id, :partner_uid::UUID, :event_id, :event_date,
                                :template_id, :version)
                        RETURNING %s;""",
                call().bind("station_id", draft.stationId())
                        .bind("request_id", draft.requestId())
                        .bind("partner_id", draft.partnerId())
                        .bind("partner_uid", draft.partnerStationUid(), UUID_STRING)
                        .bind("event_id", draft.remoteEventId())
                        .bind("event_date", draft.eventDate())
                        .bind("template_id", draft.remoteTemplateId())
                        .bind("version", draft.templateVersion()),
                PartnerSigning.map(),
                PartnerSigning.COLUMNS);
    }

    /** @return what a signing request answers, where it answers a partner's appointment */
    public Optional<PartnerSigning> forRequest(int requestId) {
        return query(
                        "SELECT %s FROM partner_signing_request p WHERE p.request_id = :request_id;",
                        PartnerSigning.COLUMNS)
                .single(call().bind("request_id", requestId))
                .map(PartnerSigning.map())
                .first();
    }

    /**
     * The request of a member for a document of a partner's appointment on a date that is open or complete,
     * for the copy with these bytes.
     *
     * @param partnerStationUid the station holding the appointment
     * @param remoteEventId     the appointment there
     * @param eventDate         the date
     * @param memberId          the member here
     * @param contentSha256     the copy handed out
     * @return the row, where such a request stands
     */
    public Optional<PartnerSigning> live(
            UUID partnerStationUid, int remoteEventId, LocalDate eventDate, int memberId, String contentSha256) {
        return query("""
                        SELECT %s
                        FROM partner_signing_request p
                                 JOIN signing_request r ON r.id = p.request_id
                        WHERE p.partner_station_uid = :partner_uid::UUID
                          AND p.remote_event_id = :event_id
                          AND p.event_date = :event_date
                          AND r.member_id = :member_id
                          AND r.content_sha256 = :content_hash
                          AND r.state IN ('OPEN', 'COMPLETE')
                        ORDER BY p.id DESC
                        LIMIT 1;""", PartnerSigning.COLUMNS)
                .single(call().bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("event_id", remoteEventId)
                        .bind("event_date", eventDate)
                        .bind("member_id", memberId)
                        .bind("content_hash", contentSha256))
                .map(PartnerSigning.map())
                .first();
    }

    /**
     * Whether a member stands asked for a document of a partner's appointment on a date, or signed it, whatever
     * copy of it they were asked on.
     *
     * @param partnerStationUid the station holding the appointment
     * @param remoteEventId     the appointment there
     * @param eventDate         the date
     * @param memberId          the member here
     * @param remoteTemplateId  the document there
     * @return whether a request for it is open or complete
     */
    public boolean asked(
            UUID partnerStationUid, int remoteEventId, LocalDate eventDate, int memberId, int remoteTemplateId) {
        return query("""
                        SELECT exists (SELECT 1
                                       FROM partner_signing_request p
                                                JOIN signing_request r ON r.id = p.request_id
                                       WHERE p.partner_station_uid = :partner_uid::UUID
                                         AND p.remote_event_id = :event_id
                                         AND p.event_date = :event_date
                                         AND p.remote_template_id = :template_id
                                         AND r.member_id = :member_id
                                         AND r.state IN ('OPEN', 'COMPLETE')) AS asked;""")
                .single(call().bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("event_id", remoteEventId)
                        .bind("event_date", eventDate)
                        .bind("template_id", remoteTemplateId)
                        .bind("member_id", memberId))
                .map(row -> row.getBoolean("asked"))
                .first()
                .orElse(false);
    }

    /**
     * @param partnerStationUid the station holding the appointment
     * @param remoteEventId     the appointment there
     * @param remoteTemplateIds the documents there
     * @param from              the first date that counts
     * @return the rows of requests for those documents on that date or a later one that still wait for a
     *         signature, the oldest first
     */
    public List<PartnerSigning> openForTemplatesFrom(
            UUID partnerStationUid, int remoteEventId, Collection<Integer> remoteTemplateIds, LocalDate from) {
        if (remoteTemplateIds.isEmpty()) return List.of();
        return query("""
                        SELECT %s
                        FROM partner_signing_request p
                                 JOIN signing_request r ON r.id = p.request_id
                        WHERE p.partner_station_uid = :partner_uid::UUID
                          AND p.remote_event_id = :event_id
                          AND p.remote_template_id = ANY (:template_ids::INT[])
                          AND p.event_date >= :from
                          AND r.state = 'OPEN'
                        ORDER BY p.id;""", PartnerSigning.COLUMNS)
                .single(call().bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("event_id", remoteEventId)
                        .bind("template_ids", List.copyOf(remoteTemplateIds), PostgreSqlTypes.INTEGER)
                        .bind("from", from))
                .map(PartnerSigning.map())
                .all();
    }

    /**
     * @param partnerStationUid the station holding the appointment
     * @param remoteEventId     the appointment there
     * @param eventDate         the date
     * @param memberId          the member here
     * @return the signing requests of the member for that appointment and date that still wait for a signature
     */
    public List<Integer> openRequests(UUID partnerStationUid, int remoteEventId, LocalDate eventDate, int memberId) {
        return query("""
                        SELECT r.id
                        FROM partner_signing_request p
                                 JOIN signing_request r ON r.id = p.request_id
                        WHERE p.partner_station_uid = :partner_uid::UUID
                          AND p.remote_event_id = :event_id
                          AND p.event_date = :event_date
                          AND r.member_id = :member_id
                          AND r.state = 'OPEN'
                        ORDER BY r.id;""")
                .single(call().bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("event_id", remoteEventId)
                        .bind("event_date", eventDate)
                        .bind("member_id", memberId))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * The rows with something the partner has not taken yet and whose next try is due: the partner was never
     * told the document is asked for here, or a sealed copy newer than the one it took is filed.
     *
     * @param now         the moment that counts as now
     * @param maxAttempts the failures in a row after which sending is given up
     * @param limit       the most rows to name
     * @return the rows, the one waiting longest first
     */
    public List<PartnerSigning> due(Instant now, int maxAttempts, int limit) {
        return query("""
                        SELECT %s
                        FROM partner_signing_request p
                                 JOIN signing_request r ON r.id = p.request_id
                                 LEFT JOIN member_document_version v
                                           ON v.document_id = r.document_id AND v.superseded_at IS NULL
                        WHERE p.partner_id IS NOT NULL
                          AND p.next_delivery_at <= :now
                          AND p.delivery_attempts < :max_attempts
                          AND (p.announced_at IS NULL
                            OR (v.sha256 IS NOT NULL AND v.sha256 IS DISTINCT FROM p.delivered_sha256))
                        ORDER BY p.next_delivery_at, p.id
                        LIMIT :limit;""", PartnerSigning.COLUMNS)
                .single(call().bind("now", now, INSTANT_TIMESTAMP)
                        .bind("max_attempts", maxAttempts)
                        .bind("limit", limit))
                .map(PartnerSigning.map())
                .all();
    }

    /**
     * Records that the partner took what was sent.
     *
     * @param id     the row
     * @param sealed SHA-256 of the sealed copy it took, or null where only the request was announced
     */
    public void delivered(int id, @Nullable String sealed) {
        query("""
                UPDATE partner_signing_request
                SET announced_at      = coalesce(announced_at, now()),
                    delivered_sha256  = coalesce(:sealed, delivered_sha256),
                    delivery_attempts = 0,
                    next_delivery_at  = now()
                WHERE id = :id;""").single(call().bind("id", id).bind("sealed", sealed)).update();
    }

    /**
     * Records that sending failed, and when to try again.
     *
     * @param id    the row
     * @param retry when to try again
     */
    public void failed(int id, Instant retry) {
        query("""
                UPDATE partner_signing_request
                SET delivery_attempts = delivery_attempts + 1,
                    next_delivery_at  = :retry
                WHERE id = :id;""")
                .single(call().bind("id", id).bind("retry", retry, INSTANT_TIMESTAMP))
                .update();
    }

    /**
     * Sends again at once what a request has not delivered, since a new sealed state was filed for it.
     *
     * @param requestId the signing request
     */
    public void sendAgain(int requestId) {
        query("""
                UPDATE partner_signing_request
                SET delivery_attempts = 0,
                    next_delivery_at  = now()
                WHERE request_id = :request_id;""").single(call().bind("request_id", requestId)).update();
    }
}
