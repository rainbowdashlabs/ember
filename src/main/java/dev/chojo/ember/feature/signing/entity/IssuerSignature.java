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
 * A generated letter the station signed for its issuer under the issuer's standing consent: their picture
 * drawn into the issuer field and the letter sealed before it was filed.
 *
 * @param issuerId      the member whose picture was drawn in, or null once they were deleted
 * @param consentedAt   when the issuer had agreed to it, as it stood at the signing
 * @param imageSha256   SHA-256 of the picture that was drawn in, lower-case hexadecimal
 * @param sealLevel     the level the station's seal reached
 * @param timestampedBy the timestamp service whose timestamp the letter carries, or null without one
 * @param signedAt      when the letter was signed and sealed
 */
public record IssuerSignature(
        @Nullable Integer issuerId,
        Instant consentedAt,
        String imageSha256,
        SealLevel sealLevel,
        @Nullable String timestampedBy,
        Instant signedAt) {

    /** Reads the row as {@code issuer_signature} stores it. */
    public static RowMapping<IssuerSignature> map() {
        return row -> new IssuerSignature(
                row.getObject("issuer_id", Integer.class),
                row.get("consented_at", INSTANT_TIMESTAMP),
                row.getString("image_sha256"),
                row.getEnum("seal_level", SealLevel.class),
                row.getString("timestamped_by"),
                row.get("signed_at", INSTANT_TIMESTAMP));
    }
}
