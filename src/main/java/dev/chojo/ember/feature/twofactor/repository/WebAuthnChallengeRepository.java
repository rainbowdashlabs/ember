/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.repository;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnChallenge;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Stores pending WebAuthn challenges. Raw tokens are hashed on the way in and never stored;
 * consuming is a single {@code DELETE ... RETURNING}, so a token can only ever be spent once,
 * two racing finishes included.
 */
@Singleton
public class WebAuthnChallengeRepository {
    private static final String COLUMNS = "id, purpose, account_id, options_json, created_at, expires_at";

    private final TokenHasher tokenHasher;

    @Inject
    public WebAuthnChallengeRepository(TokenHasher tokenHasher) {
        this.tokenHasher = tokenHasher;
    }

    /**
     * Parks a challenge under the hash of {@code token}.
     *
     * @param accountId the account the ceremony belongs to, or {@code null} for a passwordless
     *         sign-in
     */
    public void create(
            String token, ChallengePurpose purpose, Integer accountId, String optionsJson, Instant expiresAt) {
        query("""
                INSERT INTO webauthn_challenge (token_hash, purpose, account_id, options_json, expires_at)
                VALUES (:token_hash, :purpose, :account_id, :options_json, :expires_at);""")
                .single(call().bind("token_hash", tokenHasher.hash(token))
                        .bind("purpose", purpose.name())
                        .bind("account_id", accountId)
                        .bind("options_json", optionsJson)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * Deletes the challenge stored under {@code token} and returns it. Empty when no such
     * challenge exists or it was already spent. Expiry and purpose are the caller's checks:
     * the row comes back either way, and is gone either way.
     */
    public Optional<WebAuthnChallenge> consume(String token) {
        return query("""
                DELETE FROM webauthn_challenge WHERE token_hash = :token_hash
                RETURNING %s;""", COLUMNS)
                .single(call().bind("token_hash", tokenHasher.hash(token)))
                .map(WebAuthnChallenge.map())
                .first();
    }

    /**
     * Removes every challenge past its expiry. Called by the scheduled sweep; an anonymous
     * visitor can mint rows here, so this table cannot rely on lookups consuming them.
     */
    public int deleteExpired() {
        return query("DELETE FROM webauthn_challenge WHERE expires_at < now();")
                .single(call())
                .update()
                .rows();
    }
}
