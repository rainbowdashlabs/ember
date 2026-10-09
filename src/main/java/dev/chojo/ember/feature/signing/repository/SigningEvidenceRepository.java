/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.signing.entity.ActPicture;
import dev.chojo.ember.feature.signing.entity.ActPictureSource;
import dev.chojo.ember.feature.signing.entity.BatchMembership;
import dev.chojo.ember.feature.signing.entity.GuardianLink;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.twofactor.entity.CredentialKeyStamp;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The evidence of signing acts, one record per signed field, stored as the provider handed it over.
 */
@Singleton
public class SigningEvidenceRepository {

    /**
     * Stores the evidence of the act that filled a field.
     *
     * @param fieldId         the field the act filled
     * @param level           the legal level the signature reached
     * @param evidence        the act and its proof
     * @param accountMemberId the member at the station whose account confirmed
     * @param memberId        the member signed for or signing through the account, or null
     * @param guardianLink    the guardian link the act went through, or null
     * @return the evidence as stored
     */
    public StoredEvidence record(
            int fieldId,
            SignatureLevel level,
            SigningEvidence evidence,
            int accountMemberId,
            @Nullable Integer memberId,
            @Nullable GuardianLink guardianLink) {
        var act = evidence.act();
        var call = call().bind("field_id", fieldId)
                .bind("signature_level", level)
                .bind("proof", evidence.proof())
                .bind("bound", evidence.boundToDocument())
                .bind("capacity", act.signer().capacity())
                .bind("challenge_account_id", act.signer().accountId())
                .bind("challenge_member_id", act.signer().memberId())
                .bind("account_member_id", accountMemberId)
                .bind("member_id", memberId)
                .bind("account_holder_name", act.accountHolderName())
                .bind("member_name", act.memberName())
                .bind("field_name", act.fieldName())
                .bind("statement", act.statement())
                .bind("content_hash", HexFormat.of().formatHex(act.contentSha256()))
                .bind(
                        "entry_fields",
                        act.entries().stream().map(SignerEntry::field).toList(),
                        PostgreSqlTypes.TEXT)
                .bind(
                        "entry_values",
                        act.entries().stream().map(SignerEntry::value).toList(),
                        PostgreSqlTypes.TEXT)
                .bind("nonce", act.nonce())
                .bind("signed_at", act.signedAt(), INSTANT_TIMESTAMP)
                .bind("truncated_ip", act.truncatedIp())
                .bind("user_agent", act.userAgent());
        bindGuardianLink(call, guardianLink);
        bindWebAuthn(call, evidence instanceof SigningEvidence.WebAuthnBound bound ? bound : null);
        bindBatch(call, act.batch());
        int id = SqlSupport.insertReturning("""
                        INSERT INTO signing_evidence(field_id, signature_level, proof, bound, capacity,
                                                     challenge_account_id, challenge_member_id, account_member_id,
                                                     member_id, account_holder_name, member_name, guardian_position,
                                                     guardian_linked_at, guardian_linked_by_name, field_name,
                                                     statement, content_sha256, entry_fields, entry_values, nonce,
                                                     signed_at, truncated_ip, user_agent, relying_party_id,
                                                     challenge, credential_id, credential_public_key,
                                                     client_data_json, authenticator_data, signature, user_verified,
                                                     signature_count, credential_key_stamp_token,
                                                     credential_key_stamped_at, credential_key_stamp_service,
                                                     credential_key_stamp_kind, batch_uid, batch_position,
                                                     batch_request_uids, batch_field_names, batch_digests)
                        VALUES (:field_id, :signature_level, :proof, :bound, :capacity, :challenge_account_id,
                                :challenge_member_id, :account_member_id, :member_id, :account_holder_name,
                                :member_name, :guardian_position, :guardian_linked_at, :guardian_linked_by_name,
                                :field_name, :statement, :content_hash, :entry_fields, :entry_values, :nonce,
                                :signed_at, :truncated_ip, :user_agent, :relying_party_id, :challenge,
                                :credential_id, :credential_public_key, :client_data_json, :authenticator_data,
                                :signature, :user_verified, :signature_count, :key_stamp_token, :key_stamped_at,
                                :key_stamp_service, :key_stamp_kind, :batch_uid::uuid, :batch_position,
                                nullif(:batch_request_uids, '{}'::text[]), nullif(:batch_field_names, '{}'::text[]),
                                nullif(:batch_digests, ''::bytea))
                        RETURNING id;""", call, row -> row.getInt("id"));
        return query("""
                        SELECT %s
                        FROM signing_evidence e
                                 JOIN signing_request_field f ON f.id = e.field_id
                                 JOIN signing_request r ON r.id = f.request_id
                        WHERE e.id = :id;""", StoredEvidence.COLUMNS)
                .single(call().bind("id", id))
                .map(StoredEvidence.map())
                .first()
                .orElseThrow();
    }

    /** @return the evidence of every act on the fields of a request, in the order the fields were asked for */
    public List<StoredEvidence> evidenceOf(int requestId) {
        return query("""
                        SELECT %s
                        FROM signing_evidence e
                                 JOIN signing_request_field f ON f.id = e.field_id
                                 JOIN signing_request r ON r.id = f.request_id
                        WHERE r.id = :request_id
                        ORDER BY f.id;""", StoredEvidence.COLUMNS)
                .single(call().bind("request_id", requestId))
                .map(StoredEvidence.map())
                .all();
    }

    /**
     * Keeps the signature picture an act left in its field, which every sealed version draws from here.
     *
     * @param evidenceId the evidence of the act
     * @param picture    the picture, a transparent PNG, with how it came to the act
     */
    public void storeMark(int evidenceId, ActPicture picture) {
        query("""
                        UPDATE signing_evidence
                        SET mark_image  = :mark_image,
                            mark_source = :mark_source
                        WHERE id = :id;""")
                .single(call().bind("id", evidenceId)
                        .bind("mark_image", picture.png())
                        .bind("mark_source", picture.source()))
                .update();
    }

    /**
     * @param requestId the request
     * @return the signature pictures of the acts on the request's fields with how each came to its act, by
     *     field id; an act without one is left out
     */
    public Map<Integer, ActPicture> marksOf(int requestId) {
        var marks = new HashMap<Integer, ActPicture>();
        query("""
                        SELECT e.field_id, e.mark_image, e.mark_source
                        FROM signing_evidence e
                                 JOIN signing_request_field f ON f.id = e.field_id
                        WHERE f.request_id = :request_id
                          AND e.mark_image IS NOT NULL;""")
                .single(call().bind("request_id", requestId))
                .map(row -> Map.entry(
                        row.getInt("field_id"),
                        new ActPicture(row.getBytes("mark_image"), row.getEnum("mark_source", ActPictureSource.class))))
                .all()
                .forEach(entry -> marks.put(entry.getKey(), entry.getValue()));
        return marks;
    }

    /**
     * Records the sealed version that carries the acts on a request no sealed version carried yet.
     *
     * @param requestId the request
     * @param sha256    SHA-256 of the sealed version, lower-case hexadecimal
     * @return the evidence ids of the acts it carries for the first time
     */
    public List<Integer> markSealed(int requestId, String sha256) {
        return query("""
                        UPDATE signing_evidence e
                        SET sealed_sha256 = :sealed_hash
                        FROM signing_request_field f
                        WHERE f.id = e.field_id
                          AND f.request_id = :request_id
                          AND e.sealed_sha256 IS NULL
                        RETURNING e.id;""")
                .single(call().bind("request_id", requestId).bind("sealed_hash", sha256))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * The requests whose newest state no sealed version shows yet: an act no version carries, or, on a
     * request somebody signed electronically, a field settled since the last version, such as one confirmed
     * on paper, waived or withdrawn, or the withdrawal of its agreement. Only what happened before a given
     * time counts, so an act whose own sealing is still running is left to it. A request nobody signed
     * electronically has no sealed document and is left out; its agreement cannot be withdrawn either.
     *
     * <p>The requests whose sealing never failed in the sweep come first, the one waiting longest first; the
     * others follow in the order their sealing last failed ({@link #markSealFailed}), so a request that keeps
     * failing holds back no other.
     *
     * @param changedBefore only acts recorded, fields settled and agreements withdrawn before this count
     * @param limit         how many requests to read at most
     * @return the ids of those requests
     */
    public List<Integer> requestsToSeal(Instant changedBefore, int limit) {
        return query("""
                        SELECT waiting.request_id
                        FROM (SELECT f.request_id, e.recorded_at AS since
                              FROM signing_evidence e
                                       JOIN signing_request_field f ON f.id = e.field_id
                              WHERE e.sealed_sha256 IS NULL
                                AND e.recorded_at < :changed_before
                              UNION ALL
                              SELECT f.request_id, f.settled_at AS since
                              FROM signing_request_field f
                              WHERE f.state <> 'OPEN'
                                AND f.sealed_sha256 IS NULL
                                AND f.settled_at < :changed_before
                                AND EXISTS (SELECT 1
                                            FROM signing_evidence e
                                                     JOIN signing_request_field signed ON signed.id = e.field_id
                                            WHERE signed.request_id = f.request_id)
                              UNION ALL
                              SELECT w.request_id, w.withdrawn_at AS since
                              FROM signing_withdrawal w
                              WHERE w.sealed_sha256 IS NULL
                                AND w.withdrawn_at < :changed_before) waiting
                                 JOIN signing_request r ON r.id = waiting.request_id
                        WHERE r.document_id IS NOT NULL
                        GROUP BY waiting.request_id, r.seal_failed_at
                        ORDER BY r.seal_failed_at NULLS FIRST, min(waiting.since), waiting.request_id
                        LIMIT :limit;""")
                .single(call().bind("changed_before", changedBefore, INSTANT_TIMESTAMP)
                        .bind("limit", limit))
                .map(row -> row.getInt("request_id"))
                .all();
    }

    /**
     * Notes that the sweep failed to seal a request, which puts it behind every request whose sealing did not
     * fail.
     *
     * @param requestId the request
     * @param at        when it failed
     */
    public void markSealFailed(int requestId, Instant at) {
        query("""
                        UPDATE signing_request
                        SET seal_failed_at = :failed_at
                        WHERE id = :id;""")
                .single(call().bind("id", requestId).bind("failed_at", at, INSTANT_TIMESTAMP))
                .update();
    }

    /**
     * Forgets an earlier failure to seal a request, once a version shows its state.
     *
     * @param requestId the request
     */
    public void clearSealFailure(int requestId) {
        query("""
                        UPDATE signing_request
                        SET seal_failed_at = NULL
                        WHERE id = :id
                          AND seal_failed_at IS NOT NULL;""").single(call().bind("id", requestId)).update();
    }

    private static void bindBatch(Call call, @Nullable BatchMembership batch) {
        if (batch == null) {
            call.bind("batch_uid", (String) null)
                    .bind("batch_position", (Integer) null)
                    .bind("batch_request_uids", List.<String>of(), PostgreSqlTypes.TEXT)
                    .bind("batch_field_names", List.<String>of(), PostgreSqlTypes.TEXT)
                    .bind("batch_digests", new byte[0]);
            return;
        }
        var digests = new ByteArrayOutputStream();
        batch.items().forEach(item -> digests.writeBytes(item.digest()));
        call.bind("batch_uid", batch.uid().toString())
                .bind("batch_position", batch.position())
                .bind(
                        "batch_request_uids",
                        batch.items().stream()
                                .map(item -> item.requestUid().toString())
                                .toList(),
                        PostgreSqlTypes.TEXT)
                .bind(
                        "batch_field_names",
                        batch.items().stream()
                                .map(BatchMembership.Item::fieldName)
                                .toList(),
                        PostgreSqlTypes.TEXT)
                .bind("batch_digests", digests.toByteArray());
    }

    private static void bindGuardianLink(Call call, @Nullable GuardianLink link) {
        call.bind("guardian_position", link == null ? null : link.position())
                .bind("guardian_linked_at", link == null ? null : link.linkedAt(), INSTANT_TIMESTAMP)
                .bind("guardian_linked_by_name", link == null ? null : link.linkedByName());
    }

    private static void bindWebAuthn(Call call, SigningEvidence.@Nullable WebAuthnBound bound) {
        call.bind("relying_party_id", bound == null ? null : bound.relyingPartyId())
                .bind("challenge", bound == null ? null : bound.challenge())
                .bind("credential_id", bound == null ? null : bound.credentialId())
                .bind("credential_public_key", bound == null ? null : bound.credentialPublicKeyCose())
                .bind("client_data_json", bound == null ? null : bound.clientDataJson())
                .bind("authenticator_data", bound == null ? null : bound.authenticatorData())
                .bind("signature", bound == null ? null : bound.signature())
                .bind("user_verified", bound == null ? null : bound.userVerified())
                .bind("signature_count", bound == null ? null : bound.signatureCount());
        bindKeyStamp(call, bound == null ? null : bound.credentialKeyStamp());
    }

    private static void bindKeyStamp(Call call, @Nullable CredentialKeyStamp stamp) {
        call.bind("key_stamp_token", stamp == null ? null : stamp.token())
                .bind("key_stamped_at", stamp == null ? null : stamp.stampedAt(), INSTANT_TIMESTAMP)
                .bind("key_stamp_service", stamp == null ? null : stamp.service())
                .bind("key_stamp_kind", stamp == null ? null : stamp.kind());
    }
}
