/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import dev.chojo.ember.feature.signing.entity.IssuerSignature;
import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The letters the station signed for their issuer under the issuer's standing consent, one record per
 * generated document.
 */
@Singleton
public class IssuerSignatureRepository {

    /**
     * Records that a generated letter was signed for its issuer.
     *
     * @param generationId the generation log entry of the letter
     * @param signature    how it was signed
     */
    public void record(int generationId, IssuerSignature signature) {
        query("""
                        INSERT INTO issuer_signature(generation_id, issuer_id, consented_at, image_sha256, seal_level,
                                                     timestamped_by, signed_at)
                        VALUES (:generation_id, :issuer_id, :consented_at, :image_hash, :seal_level, :timestamped_by,
                                :signed_at);""")
                .single(call().bind("generation_id", generationId)
                        .bind("issuer_id", signature.issuerId())
                        .bind("consented_at", signature.consentedAt(), INSTANT_TIMESTAMP)
                        .bind("image_hash", signature.imageSha256())
                        .bind("seal_level", signature.sealLevel())
                        .bind("timestamped_by", signature.timestampedBy())
                        .bind("signed_at", signature.signedAt(), INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * @param generationId the generation log entry of a letter
     * @return how it was signed for its issuer, or empty where it was not
     */
    public Optional<IssuerSignature> findByGeneration(int generationId) {
        return query("""
                        SELECT issuer_id, consented_at, image_sha256, seal_level, timestamped_by, signed_at
                        FROM issuer_signature
                        WHERE generation_id = :generation_id;""")
                .single(call().bind("generation_id", generationId))
                .map(IssuerSignature.map())
                .first();
    }
}
