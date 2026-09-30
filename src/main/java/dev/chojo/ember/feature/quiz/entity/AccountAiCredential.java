/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The AI provider key one person keeps for generating quiz content.
 *
 * @param accountId the account the key belongs to
 * @param provider  the provider the key is for, as {@link AiVendor#key()} names it
 * @param model     the model to ask by default, or {@code null} for the provider's default
 * @param sealedKey the key as stored: sealed by the credential cipher, never the plaintext
 * @param updatedAt when the key or its settings were last saved
 */
public record AccountAiCredential(int accountId, String provider, String model, String sealedKey, Instant updatedAt) {

    public static RowMapping<AccountAiCredential> map() {
        return row -> new AccountAiCredential(
                row.getInt("account_id"),
                row.getString("provider"),
                row.getString("model"),
                row.getString("api_key"),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
