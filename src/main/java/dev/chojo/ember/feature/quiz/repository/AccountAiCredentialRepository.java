/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import dev.chojo.ember.feature.quiz.entity.AccountAiCredential;
import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Stores the AI provider key each person keeps, one per account. The key arrives sealed and leaves
 * sealed; encrypting and decrypting it is the caller's business.
 */
@Singleton
public class AccountAiCredentialRepository {

    /**
     * The key an account keeps.
     *
     * @param accountId the account
     * @return the stored credential, or empty when the account keeps none
     */
    public Optional<AccountAiCredential> find(int accountId) {
        return query("""
                SELECT account_id, provider, model, api_key, updated_at
                FROM account_ai_credential
                WHERE account_id = :account_id;""")
                .single(call().bind("account_id", accountId))
                .map(AccountAiCredential.map())
                .first();
    }

    /**
     * Writes an account's key, replacing whatever it kept before.
     *
     * @param accountId the account
     * @param provider  the provider the key is for
     * @param model     the model to ask by default, or {@code null}
     * @param sealedKey the key, already sealed
     */
    public void save(int accountId, String provider, String model, String sealedKey) {
        query("""
                INSERT INTO account_ai_credential(account_id, provider, model, api_key, updated_at)
                VALUES (:account_id, :provider, :model, :api_key, now())
                ON CONFLICT (account_id)
                DO UPDATE SET provider = :provider, model = :model, api_key = :api_key, updated_at = now();""")
                .single(call().bind("account_id", accountId)
                        .bind("provider", provider)
                        .bind("model", model)
                        .bind("api_key", sealedKey))
                .insert();
    }

    /**
     * Forgets an account's key.
     *
     * @param accountId the account
     * @return whether there was one to forget
     */
    public boolean delete(int accountId) {
        return query("DELETE FROM account_ai_credential WHERE account_id = :account_id;")
                .single(call().bind("account_id", accountId))
                .delete()
                .changed();
    }
}
