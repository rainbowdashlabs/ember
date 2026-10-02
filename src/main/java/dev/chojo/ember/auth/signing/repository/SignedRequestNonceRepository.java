/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing.repository;

import jakarta.inject.Singleton;

import java.time.Instant;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The nonces of signed requests from other installations that have already been taken.
 */
@Singleton
public class SignedRequestNonceRepository {

    /**
     * Records a nonce unless it is already on record.
     *
     * @param scope     whose nonce it is
     * @param nonce     the nonce
     * @param expiresAt when it may be forgotten
     * @return true when the nonce was new and has now been recorded
     */
    public boolean record(String scope, String nonce, Instant expiresAt) {
        return query("""
                        INSERT INTO signed_request_nonce (scope, nonce, expires_at)
                        VALUES (:scope, :nonce, :expires_at)
                        ON CONFLICT (scope, nonce) DO NOTHING;""")
                .single(call().bind("scope", scope)
                        .bind("nonce", nonce)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .insert()
                .changed();
    }

    /**
     * Forgets every nonce whose request could no longer be accepted.
     *
     * @return how many were forgotten
     */
    public int deleteExpired() {
        return query("DELETE FROM signed_request_nonce WHERE expires_at < now();")
                .single(call())
                .delete()
                .rows();
    }
}
