/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.signing.entity.HandedOutAgreement;
import dev.chojo.ember.feature.signing.entity.PartnerAgreement;
import dev.chojo.ember.feature.signing.entity.PartnerAgreementState;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * What the station holding a shared appointment keeps of the documents it asks partners to sign: the one
 * copy it hands out per date, where each partner's member stands with it, and the sealed copies that came
 * back. A copy that came back is only ever added, never changed; the database refuses both, and deleting
 * a row that holds copies before its retention is over.
 */
@Singleton
public class PartnerAgreementRepository {

    /**
     * @return the copy handed out for the appointment, date and version of the template, where it was drawn
     */
    public Optional<HandedOutAgreement> handedOut(int eventId, LocalDate eventDate, int templateId, int version) {
        return query("""
                        SELECT %s FROM event_partner_agreement
                        WHERE event_id = :event_id AND event_date = :event_date
                          AND template_id = :template_id AND template_version = :version;""", HandedOutAgreement.COLUMNS)
                .single(call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("template_id", templateId)
                        .bind("version", version))
                .map(HandedOutAgreement.map())
                .first();
    }

    /**
     * @return the copy handed out for the appointment, date and template with these bytes, of whichever
     *         version
     */
    public Optional<HandedOutAgreement> handedOut(int eventId, LocalDate eventDate, int templateId, String sha256) {
        return query("""
                        SELECT %s FROM event_partner_agreement
                        WHERE event_id = :event_id AND event_date = :event_date
                          AND template_id = :template_id AND sha256 = :file_hash;""", HandedOutAgreement.COLUMNS)
                .single(call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("template_id", templateId)
                        .bind("file_hash", sha256))
                .map(HandedOutAgreement.map())
                .first();
    }

    /**
     * Keeps a copy drawn for an appointment, date and version of a template, unless one was kept meanwhile;
     * the first one kept is the one handed out.
     *
     * @param stationId the station holding the appointment
     * @param drawn     the copy
     */
    public void handOut(int stationId, HandedOutAgreement drawn) {
        query("""
                INSERT INTO event_partner_agreement(station_id, event_id, event_date, template_id, template_version,
                                                    title, file_name, content, sha256)
                VALUES (:station_id, :event_id, :event_date, :template_id, :version, :title, :file_name, :content,
                        :file_hash)
                ON CONFLICT ON CONSTRAINT event_partner_agreement_once DO NOTHING;""")
                .single(call().bind("station_id", stationId)
                        .bind("event_id", drawn.eventId())
                        .bind("event_date", drawn.eventDate())
                        .bind("template_id", drawn.templateId())
                        .bind("version", drawn.templateVersion())
                        .bind("title", drawn.title())
                        .bind("file_name", drawn.fileName())
                        .bind("content", drawn.content())
                        .bind("file_hash", drawn.sha256()))
                .insert();
    }

    /**
     * @param eventId   the appointment
     * @param eventDate the date
     * @return where every member of a partner stands with every document, the oldest row first
     */
    public List<PartnerAgreement> forDate(int eventId, LocalDate eventDate) {
        return query("""
                        SELECT %s FROM partner_agreement a
                        WHERE a.event_id = :event_id AND a.event_date = :event_date
                        ORDER BY a.id;""", PartnerAgreement.COLUMNS)
                .single(call().bind("event_id", eventId).bind("event_date", eventDate))
                .map(PartnerAgreement.map())
                .all();
    }

    /**
     * @param eventId the appointment
     * @param from    the first date that counts
     * @return the partnerships that reported on a document of the appointment for that date or a later one
     */
    public List<Integer> partnersFrom(int eventId, LocalDate from) {
        return query("""
                        SELECT DISTINCT partner_id FROM partner_agreement
                        WHERE event_id = :event_id AND event_date >= :from AND partner_id IS NOT NULL
                        ORDER BY partner_id;""")
                .single(call().bind("event_id", eventId).bind("from", from))
                .map(row -> row.getInt("partner_id"))
                .all();
    }

    /**
     * Forgets that a partner took a document on for a member, once it let the request go because the document
     * was taken off the appointment. A row that holds a sealed copy, or anything beyond being taken on, stays.
     *
     * @param eventId           the appointment
     * @param eventDate         the date
     * @param templateId        the template
     * @param partnerStationUid the member's station
     * @param remoteMemberId    the member
     * @return whether a row was forgotten
     */
    public boolean release(
            int eventId, LocalDate eventDate, int templateId, UUID partnerStationUid, UUID remoteMemberId) {
        return query("""
                        DELETE FROM partner_agreement a
                        WHERE a.event_id = :event_id AND a.event_date = :event_date AND a.template_id = :template_id
                          AND a.partner_station_uid = :partner_uid::UUID AND a.remote_member_id = :member_uid::UUID
                          AND a.state = 'ASKED'
                          AND NOT exists (SELECT 1 FROM partner_agreement_copy c WHERE c.agreement_id = a.id);""")
                .single(call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("template_id", templateId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("member_uid", remoteMemberId, UUID_STRING))
                .delete()
                .changed();
    }

    /**
     * @param stationId the station holding the appointment
     * @param id        the row
     * @return the row, where it is the station's
     */
    public Optional<PartnerAgreement> find(int stationId, int id) {
        return query(
                        "SELECT %s FROM partner_agreement a WHERE a.id = :id AND a.station_id = :station_id;",
                        PartnerAgreement.COLUMNS)
                .single(call().bind("id", id).bind("station_id", stationId))
                .map(PartnerAgreement.map())
                .first();
    }

    /**
     * Records where a document stands for a member of a partner station, creating the row on the first
     * notice. Taking it on never sets back a row that is further: a copy that came back or a paper copy
     * confirmed here stays what it is.
     *
     * @param key           the row
     * @param state         what the partner reported
     * @param contentSha256 the copy it asked to be signed
     * @param complete      whether every field is settled at the partner
     * @return the row's id
     */
    public int report(PartnerAgreement.Key key, PartnerAgreementState state, String contentSha256, boolean complete) {
        return query("""
                        INSERT INTO partner_agreement(station_id, event_id, event_date, template_id, template_name,
                                                      partner_id, partner_station_uid, partner_name, remote_member_id,
                                                      state, content_sha256, complete, retention_months, retain_until)
                        VALUES (:station_id, :event_id, :event_date, :template_id, :template_name, :partner_id,
                                :partner_uid::UUID, :partner_name, :member_uid::UUID, :state, :content_hash,
                                :complete, :retention_months, :retain_until)
                        ON CONFLICT ON CONSTRAINT partner_agreement_once DO UPDATE
                            SET partner_id = excluded.partner_id,
                                partner_name = excluded.partner_name,
                                content_sha256 = excluded.content_sha256,
                                state = CASE
                                            WHEN excluded.state = 'ASKED' AND partner_agreement.state <> 'ASKED'
                                                THEN partner_agreement.state
                                            ELSE excluded.state
                                        END,
                                complete = CASE
                                               WHEN excluded.state = 'ASKED' THEN partner_agreement.complete
                                               ELSE excluded.complete
                                           END,
                                updated_at = now()
                        RETURNING id;""")
                .single(keyed(key)
                        .bind("state", state)
                        .bind("content_hash", contentSha256)
                        .bind("complete", complete))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    /**
     * Where a document stands for a member of a partner station, holding the row until the transaction ends,
     * so two reports about it are taken one after the other.
     *
     * @param key the row
     * @return its state, where the row was written
     */
    public Optional<PartnerAgreementState> lockState(PartnerAgreement.Key key) {
        return query("""
                        SELECT state FROM partner_agreement
                        WHERE event_id = :event_id AND event_date = :event_date AND template_id = :template_id
                          AND partner_station_uid = :partner_uid::UUID AND remote_member_id = :member_uid::UUID
                        FOR UPDATE;""")
                .single(call().bind("event_id", key.eventId())
                        .bind("event_date", key.eventDate())
                        .bind("template_id", key.templateId())
                        .bind("partner_uid", key.partnerStationUid(), UUID_STRING)
                        .bind("member_uid", key.remoteMemberId(), UUID_STRING))
                .map(row -> row.getEnum("state", PartnerAgreementState.class))
                .first();
    }

    /**
     * Records a signed paper copy confirmed here for a member of a partner station.
     *
     * @param key               the row
     * @param confirmedBy       the member who confirmed it
     * @param confirmedByName   their official name
     * @return the row's id
     */
    public int confirmPaper(PartnerAgreement.Key key, int confirmedBy, String confirmedByName) {
        return query("""
                        INSERT INTO partner_agreement(station_id, event_id, event_date, template_id, template_name,
                                                      partner_id, partner_station_uid, partner_name, remote_member_id,
                                                      state, confirmed_by, confirmed_by_name, retention_months,
                                                      retain_until)
                        VALUES (:station_id, :event_id, :event_date, :template_id, :template_name, :partner_id,
                                :partner_uid::UUID, :partner_name, :member_uid::UUID, 'PAPER_CONFIRMED',
                                :confirmed_by, :confirmed_by_name, :retention_months, :retain_until)
                        ON CONFLICT ON CONSTRAINT partner_agreement_once DO UPDATE
                            SET state = 'PAPER_CONFIRMED',
                                confirmed_by = excluded.confirmed_by,
                                confirmed_by_name = excluded.confirmed_by_name,
                                updated_at = now()
                        RETURNING id;""")
                .single(keyed(key).bind("confirmed_by", confirmedBy).bind("confirmed_by_name", confirmedByName))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    /**
     * Keeps a sealed copy that came back, unless the same one was kept before.
     *
     * @param agreementId   the row
     * @param sealedSha256  SHA-256 of the copy
     * @param content       the copy
     * @param fieldsJson    what the partner reported of each field, as JSON
     * @param complete      whether every field is settled in it
     * @return true where it was new
     */
    public boolean addCopy(int agreementId, String sealedSha256, byte[] content, String fieldsJson, boolean complete) {
        return query("""
                        INSERT INTO partner_agreement_copy(agreement_id, sealed_sha256, content, fields, complete)
                        VALUES (:agreement_id, :sealed_hash, :content, :fields::jsonb, :complete)
                        ON CONFLICT ON CONSTRAINT partner_agreement_copy_once DO NOTHING;""")
                .single(call().bind("agreement_id", agreementId)
                        .bind("sealed_hash", sealedSha256)
                        .bind("content", content)
                        .bind("fields", fieldsJson)
                        .bind("complete", complete))
                .insert()
                .changed();
    }

    /**
     * @param agreementId the row
     * @return the newest sealed copy that came back, where one did
     */
    public Optional<byte[]> latestCopy(int agreementId) {
        return query("""
                        SELECT content FROM partner_agreement_copy
                        WHERE agreement_id = :agreement_id
                        ORDER BY received_at DESC, id DESC
                        LIMIT 1;""")
                .single(call().bind("agreement_id", agreementId))
                .map(row -> row.getBytes("content"))
                .first();
    }

    /**
     * @param now   the moment that counts as now
     * @param limit the most rows to name
     * @return the rows whose retention is over, the longest over first
     */
    public List<Integer> expired(Instant now, int limit) {
        return query("""
                        SELECT id FROM partner_agreement
                        WHERE retain_until <= :now
                        ORDER BY retain_until, id
                        LIMIT :limit;""")
                .single(call().bind("now", now, INSTANT_TIMESTAMP).bind("limit", limit))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * Deletes a row with its copies; the database refuses where its retention is not over.
     *
     * @param id the row
     */
    public void delete(int id) {
        query("DELETE FROM partner_agreement WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }

    private static Call keyed(PartnerAgreement.Key key) {
        return call().bind("station_id", key.stationId())
                .bind("event_id", key.eventId())
                .bind("event_date", key.eventDate())
                .bind("template_id", key.templateId())
                .bind("template_name", key.templateName())
                .bind("partner_id", key.partnerId())
                .bind("partner_uid", key.partnerStationUid(), UUID_STRING)
                .bind("partner_name", key.partnerName())
                .bind("member_uid", key.remoteMemberId(), UUID_STRING)
                .bind("retention_months", key.retentionMonths())
                .bind("retain_until", key.retainUntil(), INSTANT_TIMESTAMP);
    }

    /**
     * @param eventId          the appointment
     * @param eventDate        the date
     * @param templateId       the template
     * @param partnerStationUid the member's station
     * @param remoteMemberId   the member
     * @return the row, where one was written
     */
    public Optional<PartnerAgreement> find(
            int eventId, LocalDate eventDate, int templateId, UUID partnerStationUid, UUID remoteMemberId) {
        return query("""
                        SELECT %s FROM partner_agreement a
                        WHERE a.event_id = :event_id AND a.event_date = :event_date AND a.template_id = :template_id
                          AND a.partner_station_uid = :partner_uid::UUID AND a.remote_member_id = :member_uid::UUID;""", PartnerAgreement.COLUMNS)
                .single(call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("template_id", templateId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("member_uid", remoteMemberId, UUID_STRING))
                .map(PartnerAgreement.map())
                .first();
    }
}
