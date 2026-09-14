/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.devicerequest.repository;

import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequest;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequestPurpose;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Stores the device handshake. Every mutation that must happen exactly once is a single guarded
 * UPDATE, so two racing calls cannot both win: approving, minting the claim token, and spending it.
 *
 * <p>The purpose sits in the {@code WHERE} clause of each of those rather than being read back and
 * checked afterwards. A request approved to create a passkey must be unable to buy a session even
 * for an instant, and a guard that reads and then decides leaves exactly that instant open.
 */
@Singleton
public class DeviceRequestRepository {
    private static final String COLUMNS =
            "id, purpose, approved_account_id, subject_account_id, requesting_account_id, requesting_session_id, "
                    + "step_up_category, approved_at, consumed_at, expires_at, attempts, "
                    + "requested_user_agent, requested_country, claim_token_hash IS NOT NULL AS claim_token_issued, "
                    + "created_at";

    /**
     * A request from a device nobody has identified yet, which is how a passkey enrolment and a
     * sign-in both begin.
     */
    public int create(
            DeviceRequestPurpose purpose,
            String codeHash,
            String pollSecretHash,
            String userAgent,
            String country,
            Instant expiresAt) {
        return query("""
                INSERT INTO device_request (purpose, code_hash, poll_secret_hash, requested_user_agent, requested_country, expires_at)
                VALUES (:purpose, :code_hash, :poll_secret_hash, :user_agent, :country, :expires_at)
                RETURNING id;""")
                .single(call().bind("purpose", purpose)
                        .bind("code_hash", codeHash)
                        .bind("poll_secret_hash", pollSecretHash)
                        .bind("user_agent", userAgent)
                        .bind("country", country)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    /**
     * A step-up request, which differs from the other two in being raised by somebody already known:
     * the session that met the demand is recorded here and is the only session an approval can stamp.
     */
    public int createStepUp(
            String codeHash,
            String pollSecretHash,
            int requestingAccountId,
            int requestingSessionId,
            StepUpCategory category,
            String userAgent,
            String country,
            Instant expiresAt) {
        return query("""
                INSERT INTO device_request (purpose, code_hash, poll_secret_hash, requesting_account_id,
                                            requesting_session_id, step_up_category,
                                            requested_user_agent, requested_country, expires_at)
                VALUES ('STEP_UP', :code_hash, :poll_secret_hash, :account_id, :session_id, :category,
                        :user_agent, :country, :expires_at)
                RETURNING id;""")
                .single(call().bind("code_hash", codeHash)
                        .bind("poll_secret_hash", pollSecretHash)
                        .bind("account_id", requestingAccountId)
                        .bind("session_id", requestingSessionId)
                        .bind("category", category)
                        .bind("user_agent", userAgent)
                        .bind("country", country)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    /**
     * The open request behind a typed code: not yet approved, not spent, not expired. Empty is
     * all a wrong code earns; which of the reasons applied is nobody's business.
     */
    public Optional<DeviceRequest> findOpenByCode(String codeHash) {
        return query("""
                SELECT %s FROM device_request
                WHERE code_hash = :code_hash AND approved_at IS NULL AND consumed_at IS NULL
                AND expires_at > now();""", COLUMNS)
                .single(call().bind("code_hash", codeHash))
                .map(DeviceRequest.map())
                .first();
    }

    public Optional<DeviceRequest> findByPollSecret(String pollSecretHash) {
        return query("SELECT %s FROM device_request WHERE poll_secret_hash = :hash;", COLUMNS)
                .single(call().bind("hash", pollSecretHash))
                .map(DeviceRequest.map())
                .first();
    }

    /** The request behind a claim token, for the one purpose that token was minted for. */
    public Optional<DeviceRequest> findByClaimToken(String claimTokenHash, DeviceRequestPurpose purpose) {
        return query("SELECT %s FROM device_request WHERE claim_token_hash = :hash AND purpose = :purpose;", COLUMNS)
                .single(call().bind("hash", claimTokenHash).bind("purpose", purpose))
                .map(DeviceRequest.map())
                .first();
    }

    /**
     * Ties the request to whoever approved it and to whoever the grant is for. The two differ only
     * where a guardian signed in a member in their care; everywhere else they are the same account.
     */
    public boolean approve(int id, int approvedAccountId, int subjectAccountId) {
        return query("""
                UPDATE device_request
                SET approved_account_id = :approved_by, subject_account_id = :subject, approved_at = now()
                WHERE id = :id AND approved_at IS NULL AND consumed_at IS NULL AND expires_at > now();""")
                .single(call().bind("id", id)
                        .bind("approved_by", approvedAccountId)
                        .bind("subject", subjectAccountId))
                .update()
                .changed();
    }

    /**
     * Mints the claim token exactly once: the guard on {@code claim_token_hash IS NULL} means only
     * one of two racing polls stores its token, and only that one's raw value is ever delivered.
     */
    public boolean storeClaimToken(int id, String claimTokenHash) {
        return query("""
                UPDATE device_request
                SET claim_token_hash = :hash
                WHERE id = :id AND claim_token_hash IS NULL AND consumed_at IS NULL AND expires_at > now();""")
                .single(call().bind("id", id).bind("hash", claimTokenHash))
                .update()
                .changed();
    }

    /**
     * Spends the claim token: the consume is the claim, taken before the ceremony verifies rather
     * than after it, so there is no gap in which a second ceremony could open. A claim whose
     * ceremony then fails leaves the token dead, which is the fail-closed direction: the member asks
     * for a new code rather than an attacker getting a second try.
     *
     * <p>The purpose is part of the guard, so a token minted for one grant cannot be spent on
     * another even by a caller that asked the wrong endpoint.
     */
    public Optional<DeviceRequest> claimByToken(String claimTokenHash, DeviceRequestPurpose purpose) {
        return query("""
                UPDATE device_request
                SET consumed_at = now()
                WHERE claim_token_hash = :hash AND purpose = :purpose AND consumed_at IS NULL AND expires_at > now()
                RETURNING %s;""", COLUMNS)
                .single(call().bind("hash", claimTokenHash).bind("purpose", purpose))
                .map(DeviceRequest.map())
                .first();
    }

    /**
     * Counts a failed credential ceremony against the request and reports the new total; the caller
     * kills the request at five. A mistyped code never reaches this: it is thrown away by the lookup
     * and throttled by the rate limiter on the approval screen.
     */
    public int incrementAttempts(int id) {
        return query("""
                UPDATE device_request SET attempts = attempts + 1 WHERE id = :id
                RETURNING attempts;""")
                .single(call().bind("id", id))
                .map(row -> row.getInt("attempts"))
                .first()
                .orElse(0);
    }

    /**
     * Removes every request past its expiry. Called by the scheduled sweep; the requester is
     * unauthenticated, so this table cannot rely on lookups consuming rows.
     */
    public int deleteExpired() {
        return query("DELETE FROM device_request WHERE expires_at < now();")
                .single(call())
                .update()
                .rows();
    }
}
