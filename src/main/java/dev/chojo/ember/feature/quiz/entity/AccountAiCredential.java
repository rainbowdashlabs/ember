/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The AI provider key one person keeps for generating quiz content.
 *
 * @param accountId the account the key belongs to
 * @param provider  the provider the key is for
 * @param model     the model to ask by default, or {@code null} for the provider's default
 * @param sealedKey the key as stored: sealed by the credential cipher, never the plaintext
 * @param updatedAt when the key or its settings were last saved
 */
public record AccountAiCredential(int accountId, AiVendor provider, String model, String sealedKey, Instant updatedAt) {

    /**
     * Reads a stored credential, or nothing where the stored provider is none this instance knows,
     * which then counts as no key kept.
     */
    public static RowMapping<Optional<AccountAiCredential>> map() {
        return row -> {
            Optional<AiVendor> provider = AiVendor.fromKey(row.getString("provider"));
            if (provider.isEmpty()) return Optional.empty();
            return Optional.of(new AccountAiCredential(
                    row.getInt("account_id"),
                    provider.get(),
                    row.getString("model"),
                    row.getString("api_key"),
                    row.get("updated_at", INSTANT_TIMESTAMP)));
        };
    }
}
