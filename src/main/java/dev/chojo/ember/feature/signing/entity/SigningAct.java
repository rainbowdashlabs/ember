/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What every piece of signing evidence says, whatever the signer confirmed with: who signed what, in
 * which capacity, when and from where.
 *
 * <p>Names are official register names as they stood at signing, kept here because the account, the
 * member and the guardian link may all be gone when somebody reads the evidence. Together with the nonce,
 * the content hash, the signer, the statement, the field and the entries are exactly the parts the
 * challenge was computed from, so a passkey's answer can be checked again from this record alone. An act
 * confirmed together with others by one proof also names its batch, which holds the digests of the other
 * acts the challenge covered. The arrays are handed over as they are, without a copy.
 *
 * @param requestUid        the request the act answered
 * @param signer            who signed, in which capacity and through whose account
 * @param accountHolderName the official name of the account holder whose step-up confirmed the act
 * @param memberName        the official name of the member the guardian signed for or who signed through
 *                          the account, null when the account holder signed for themselves
 * @param fieldName         the signature field the act filled
 * @param statement         the statement the signer confirmed, exactly as shown
 * @param contentSha256     the raw SHA-256 of the frozen content the signer read
 * @param entries           what the signer typed into fields of their own, in the order the challenge took
 *                          them
 * @param nonce             the nonce issued when the act started
 * @param signedAt          when the provider accepted the confirmation, by this server's clock
 * @param truncatedIp       the client's address with its host part zeroed, or null when it was not known
 * @param userAgent         the browser's user agent, or null when it sent none
 * @param batch             the batch the act was confirmed in together with others, or null for an act
 *                          confirmed on its own
 */
public record SigningAct(
        UUID requestUid,
        Signer signer,
        String accountHolderName,
        @Nullable String memberName,
        String fieldName,
        String statement,
        byte[] contentSha256,
        List<SignerEntry> entries,
        byte[] nonce,
        Instant signedAt,
        @Nullable String truncatedIp,
        @Nullable String userAgent,
        @Nullable BatchMembership batch) {
    /** Copies the entries, so they cannot change after the fact. */
    public SigningAct {
        entries = List.copyOf(entries);
    }

    /** An act confirmed on its own, outside any batch. */
    public SigningAct(
            UUID requestUid,
            Signer signer,
            String accountHolderName,
            @Nullable String memberName,
            String fieldName,
            String statement,
            byte[] contentSha256,
            List<SignerEntry> entries,
            byte[] nonce,
            Instant signedAt,
            @Nullable String truncatedIp,
            @Nullable String userAgent) {
        this(
                requestUid,
                signer,
                accountHolderName,
                memberName,
                fieldName,
                statement,
                contentSha256,
                entries,
                nonce,
                signedAt,
                truncatedIp,
                userAgent,
                null);
    }

    /**
     * @param membership the batch the act was confirmed in
     * @return the same act as one of that batch
     */
    public SigningAct inBatch(@Nullable BatchMembership membership) {
        return new SigningAct(
                requestUid,
                signer,
                accountHolderName,
                memberName,
                fieldName,
                statement,
                contentSha256,
                entries,
                nonce,
                signedAt,
                truncatedIp,
                userAgent,
                membership);
    }

    /**
     * The person whose signature this is: the member when they signed through somebody else's account,
     * the account holder otherwise, including a guardian who signed on a member's behalf.
     *
     * @return the official name of the person who signed
     */
    public String signerName() {
        String member = memberName;
        return signer.throughAnotherAccount() && member != null ? member : accountHolderName;
    }
}
