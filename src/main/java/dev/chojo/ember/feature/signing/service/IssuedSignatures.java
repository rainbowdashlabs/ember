/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SigningEvidenceFile;
import dev.chojo.ember.feature.signing.repository.IssuerSignatureRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * The issuer's signature a request's document carried from its generation, where it was signed for its
 * issuer then ({@link IssuedLetterSigner}), as its evidence and record name it.
 */
@Singleton
public class IssuedSignatures {
    private final IssuerSignatureRepository signatures;
    private final MemberNameResolver names;

    @Inject
    public IssuedSignatures(IssuerSignatureRepository signatures, MemberNameResolver names) {
        this.signatures = signatures;
        this.names = names;
    }

    /**
     * @param request the request
     * @return the issuer's signature its document carried from its generation, named by the issuer's
     *     official name as it stands now, or null where it was generated without one
     */
    public SigningEvidenceFile.@Nullable Issued of(SignatureRequest request) {
        Integer generationId = request.generationId();
        if (generationId == null) return null;
        return signatures
                .findByGeneration(generationId)
                .map(signed -> new SigningEvidenceFile.Issued(
                        nameOf(signed.issuerId()), signed.consentedAt(), signed.signedAt(), signed.imageSha256()))
                .orElse(null);
    }

    private @Nullable String nameOf(@Nullable Integer issuerId) {
        if (issuerId == null || !names.parts(issuerId).known()) return null;
        return names.official(issuerId);
    }
}
