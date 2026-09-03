/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.repository;

import dev.chojo.ember.feature.passkey.entity.PasskeyDeviceRequest;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Stores the device handshake. Every mutation that must happen exactly once is a single guarded
 * UPDATE, so two racing calls cannot both win: approving, minting the enrolment token, and
 * spending it.
 */
@Singleton
public class PasskeyDeviceRequestRepository {
    private static final String COLUMNS = "id, approved_account_id, approved_at, consumed_at, expires_at, attempts, "
            + "requested_user_agent, requested_country, enroll_token_hash IS NOT NULL AS enroll_token_issued, created_at";

    public int create(String codeHash, String pollSecretHash, String userAgent, String country, Instant expiresAt) {
        return query("""
                INSERT INTO passkey_device_request (code_hash, poll_secret_hash, requested_user_agent, requested_country, expires_at)
                VALUES (:code_hash, :poll_secret_hash, :user_agent, :country, :expires_at)
                RETURNING id;""")
                .single(call().bind("code_hash", codeHash)
                        .bind("poll_secret_hash", pollSecretHash)
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
    public Optional<PasskeyDeviceRequest> findOpenByCode(String codeHash) {
        return query("""
                SELECT %s FROM passkey_device_request
                WHERE code_hash = :code_hash AND approved_at IS NULL AND consumed_at IS NULL
                AND expires_at > now();""", COLUMNS)
                .single(call().bind("code_hash", codeHash))
                .map(PasskeyDeviceRequest.map())
                .first();
    }

    public Optional<PasskeyDeviceRequest> findByPollSecret(String pollSecretHash) {
        return query("SELECT %s FROM passkey_device_request WHERE poll_secret_hash = :hash;", COLUMNS)
                .single(call().bind("hash", pollSecretHash))
                .map(PasskeyDeviceRequest.map())
                .first();
    }

    public Optional<PasskeyDeviceRequest> findByEnrollToken(String enrollTokenHash) {
        return query("SELECT %s FROM passkey_device_request WHERE enroll_token_hash = :hash;", COLUMNS)
                .single(call().bind("hash", enrollTokenHash))
                .map(PasskeyDeviceRequest.map())
                .first();
    }

    public boolean approve(int id, int accountId) {
        return query("""
                UPDATE passkey_device_request
                SET approved_account_id = :account_id, approved_at = now()
                WHERE id = :id AND approved_at IS NULL AND consumed_at IS NULL AND expires_at > now();""")
                .single(call().bind("id", id).bind("account_id", accountId))
                .update()
                .changed();
    }

    /**
     * Mints the enrolment token exactly once: the guard on {@code enroll_token_hash IS NULL}
     * means only one of two racing polls stores its token, and only that one's raw value is
     * ever delivered.
     */
    public boolean storeEnrollToken(int id, String enrollTokenHash) {
        return query("""
                UPDATE passkey_device_request
                SET enroll_token_hash = :hash
                WHERE id = :id AND enroll_token_hash IS NULL AND consumed_at IS NULL AND expires_at > now();""")
                .single(call().bind("id", id).bind("hash", enrollTokenHash))
                .update()
                .changed();
    }

    /**
     * Spends the enrolment token: the consume is the claim, taken before the ceremony verifies
     * rather than after it, so there is no gap in which a second ceremony could open. A claim
     * whose ceremony then fails leaves the token dead, which is the fail-closed direction: the
     * member asks for a new code rather than an attacker getting a second try.
     */
    public Optional<PasskeyDeviceRequest> claimByEnrollToken(String enrollTokenHash) {
        return query("""
                UPDATE passkey_device_request
                SET consumed_at = now()
                WHERE enroll_token_hash = :hash AND consumed_at IS NULL AND expires_at > now()
                RETURNING %s;""", COLUMNS)
                .single(call().bind("hash", enrollTokenHash))
                .map(PasskeyDeviceRequest.map())
                .first();
    }

    /**
     * Counts a failed attempt against the request and reports the new total; the caller kills
     * the request at five.
     */
    public int incrementAttempts(int id) {
        return query("""
                UPDATE passkey_device_request SET attempts = attempts + 1 WHERE id = :id
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
        return query("DELETE FROM passkey_device_request WHERE expires_at < now();")
                .single(call())
                .update()
                .rows();
    }
}
