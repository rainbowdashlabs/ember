/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * What an account keeps about its own signature: which picture is saved, and whether letters it issues
 * may be signed with it without asking each time.
 *
 * @param accountId           the account
 * @param imageSha256         SHA-256 of the saved picture, lower-case hexadecimal, or null while none is saved
 * @param imageSource         how the picture was made, or null while none is saved
 * @param imageSavedAt        when the picture was saved, or null while none is saved
 * @param autoSignConsentedAt when the person agreed to letters being signed with their picture, or null
 *                            while they have not agreed
 */
public record AccountSignature(
        int accountId,
        @Nullable String imageSha256,
        @Nullable SignatureImageSource imageSource,
        @Nullable Instant imageSavedAt,
        @Nullable Instant autoSignConsentedAt) {

    /**
     * @param accountId the account
     * @return what an account that never saved anything keeps
     */
    public static AccountSignature none(int accountId) {
        return new AccountSignature(accountId, null, null, null, null);
    }

    /** @return whether a picture is saved */
    public boolean hasImage() {
        return imageSha256 != null;
    }

    /** Reads the row as {@code account_signature} stores it. */
    public static RowMapping<AccountSignature> map() {
        return row -> {
            String source = row.getString("image_source");
            return new AccountSignature(
                    row.getInt("account_id"),
                    row.getString("image_sha256"),
                    source == null ? null : SignatureImageSource.valueOf(source),
                    row.get("image_saved_at", INSTANT_TIMESTAMP),
                    row.get("auto_sign_consented_at", INSTANT_TIMESTAMP));
        };
    }
}
