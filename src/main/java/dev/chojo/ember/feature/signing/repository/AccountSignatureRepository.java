/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import dev.chojo.ember.feature.signing.entity.AccountSignature;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * What each account keeps about its own signature: the saved picture by its hash, and the consent to
 * letters being signed with it. The picture's bytes are in the account's file store.
 */
@Singleton
public class AccountSignatureRepository {

    /**
     * @param accountId the account
     * @return what the account keeps, or empty where it never saved anything
     */
    public Optional<AccountSignature> find(int accountId) {
        return query("""
                        SELECT account_id, image_sha256, image_source, image_saved_at, auto_sign_consented_at
                        FROM account_signature
                        WHERE account_id = :account_id;""")
                .single(call().bind("account_id", accountId))
                .map(AccountSignature.map())
                .first();
    }

    /**
     * Records the picture an account saved, in place of the one before.
     *
     * @param accountId the account
     * @param sha256    SHA-256 of the stored picture, lower-case hexadecimal
     * @param source    how it was made
     * @param savedAt   when it was saved
     */
    public void saveImage(int accountId, String sha256, SignatureImageSource source, Instant savedAt) {
        query("""
                        INSERT INTO account_signature(account_id, image_sha256, image_source, image_saved_at)
                        VALUES (:account_id, :image_hash, :image_source, :saved_at)
                        ON CONFLICT (account_id) DO UPDATE
                            SET image_sha256   = excluded.image_sha256,
                                image_source   = excluded.image_source,
                                image_saved_at = excluded.image_saved_at;""")
                .single(call().bind("account_id", accountId)
                        .bind("image_hash", sha256)
                        .bind("image_source", source)
                        .bind("saved_at", savedAt, INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * Forgets the picture an account saved. The consent stays as it was, and signs nothing without a
     * picture.
     *
     * @param accountId the account
     */
    public void clearImage(int accountId) {
        query("""
                        UPDATE account_signature
                        SET image_sha256   = NULL,
                            image_source   = NULL,
                            image_saved_at = NULL
                        WHERE account_id = :account_id;""").single(call().bind("account_id", accountId)).update();
    }

    /**
     * Gives or takes back the consent to letters being signed with the account's picture.
     *
     * @param accountId   the account
     * @param consentedAt when it was given, or null to take it back
     */
    public void setConsent(int accountId, @Nullable Instant consentedAt) {
        query("""
                        INSERT INTO account_signature(account_id, auto_sign_consented_at)
                        VALUES (:account_id, :consented_at)
                        ON CONFLICT (account_id) DO UPDATE
                            SET auto_sign_consented_at = excluded.auto_sign_consented_at;""")
                .single(call().bind("account_id", accountId).bind("consented_at", consentedAt, INSTANT_TIMESTAMP))
                .insert();
    }
}
