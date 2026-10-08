/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.twofactor.entity.CredentialKeyStamp;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The machine-readable evidence a signed document carries as an attachment, one file per signing state.
 *
 * <p>It holds everything the record page shows and everything a reader needs to check a passkey's or
 * security key's answer again without Ember: the parts the challenge was computed from (the layout named by
 * {@code challengeLayout}), the authenticator's answer and the credential's public key with its timestamp.
 * The account and member ids are in it because the challenge was computed from them; they are this
 * installation's own numbers and mean nothing elsewhere. Binary values are written as Base64, hashes as
 * lower-case hexadecimal and times in ISO 8601 UTC.
 *
 * <p>The arrays and lists are handed over as they are, without a copy.
 *
 * @param format          the name and version of this file's layout, {@value #FORMAT}
 * @param challengeLayout the layout of the challenge a passkey or security key signed
 * @param requestUid      the request for signatures the document belongs to
 * @param contentSha256   SHA-256 of the frozen content every signature binds to, lower-case hexadecimal
 * @param memberName      the official name of the member the document is about
 * @param assembledAt     when this signing state was put together
 * @param fields          every signature field of the request, in the order they were asked for
 */
public record SigningEvidenceFile(
        String format,
        String challengeLayout,
        UUID requestUid,
        String contentSha256,
        String memberName,
        Instant assembledAt,
        List<Field> fields) {

    /** The name and version of the layout. */
    public static final String FORMAT = "ember-signing-evidence-v1";

    /** The name the file carries inside the document. */
    public static final String FILE_NAME = "signing-evidence.json";

    /**
     * One signature field and how it stands.
     *
     * @param fieldName           the name of the signature field in the document
     * @param role                who signs it
     * @param state               where it stands
     * @param requestedSignerName the official name of the person asked to sign it, or null where nobody in
     *                            particular was named
     * @param statement           the statement the field asks to confirm
     * @param settledAt           when it stopped being open, or null while it is open
     * @param settledByName       the official name of whoever settled it, or null
     * @param act                 the signing act that filled it, or null where none did
     */
    public record Field(
            String fieldName,
            FieldRole role,
            FieldState state,
            @Nullable String requestedSignerName,
            String statement,
            @Nullable Instant settledAt,
            @Nullable String settledByName,
            @Nullable Act act) {}

    /**
     * One signing act, as the evidence recorded it.
     *
     * @param level             the legal level the signature reached
     * @param proof             what the signer confirmed with
     * @param boundToDocument   whether the proof itself signs the content, rather than being recorded next
     *                          to it
     * @param capacity          in what capacity the signature was given
     * @param accountId         the account whose step-up confirmed, as the challenge took it
     * @param memberId          the member signed for or signing through the account, as the challenge took
     *                          it, or null where the account holder signed for themselves
     * @param accountHolderName the official name of the account holder
     * @param memberName        the official name of that member, or null
     * @param signerName        the official name of the person whose signature this is
     * @param fieldName         the signature field the act filled
     * @param statement         the statement the signer confirmed, exactly as shown
     * @param contentSha256     SHA-256 of the content the signer read, lower-case hexadecimal
     * @param entries           what the signer typed, in the order the challenge took it
     * @param nonce             the nonce issued when the act started
     * @param signedAt          when the act was accepted, by the server's clock
     * @param truncatedIp       the client's address with its host part zeroed, or null
     * @param userAgent         the browser's user agent, or null
     * @param guardianLink      the guardian link the act went through as it stood then, or null
     * @param webAuthn          the authenticator's answer, or null for a proof that is not a passkey or a
     *                          security key
     * @param picture           the signature picture the act left in its field, or null for an act that left
     *                          none, whose field shows the name and date only
     */
    public record Act(
            SignatureLevel level,
            StepUpProof proof,
            boolean boundToDocument,
            SignerCapacity capacity,
            int accountId,
            @Nullable Integer memberId,
            String accountHolderName,
            @Nullable String memberName,
            String signerName,
            String fieldName,
            String statement,
            String contentSha256,
            List<SignerEntry> entries,
            byte[] nonce,
            Instant signedAt,
            @Nullable String truncatedIp,
            @Nullable String userAgent,
            @Nullable GuardianLink guardianLink,
            @Nullable WebAuthn webAuthn,
            @Nullable Picture picture) {}

    /**
     * The signature picture an act left in its field. The picture is not in the file, only its hash: the
     * document shows it in the field, and Ember keeps it with the evidence.
     *
     * <p>The picture is bound to the document by the station's seal alone, not by the signer's proof: the
     * challenge a passkey or security key signs ({@code challengeLayout}) does not include it, since the
     * picture is handed over when the act completes, after the challenge was issued. The signer's proof
     * covers the content, the statement and the entries; the picture is what the station drew in for them,
     * and its hash here is as trustworthy as the seal over this file.
     *
     * @param sha256 SHA-256 of the picture as a transparent PNG, before it was drawn in, lower-case
     *               hexadecimal
     * @param source how it came to the act: made for it (drawn, typed, uploaded) or saved before
     */
    public record Picture(String sha256, ActPictureSource source) {}

    /**
     * What a passkey or security key answered, and the key it answered with.
     *
     * @param relyingPartyId          the relying party id the credential is bound to
     * @param challenge               the challenge the authenticator signed
     * @param credentialId            the id of the credential
     * @param credentialPublicKeyCose the credential's public key, COSE encoded
     * @param clientDataJson          the client data the browser collected
     * @param authenticatorData       the authenticator data
     * @param signature               the authenticator's signature over the authenticator data and the
     *                                SHA-256 of the client data
     * @param userVerified            whether the authenticator verified its user
     * @param signatureCount          the authenticator's signature counter
     * @param credentialKeyStamp      the RFC 3161 timestamp over the SHA-256 of the public key, or null when
     *                                the key had none
     */
    public record WebAuthn(
            String relyingPartyId,
            byte[] challenge,
            byte[] credentialId,
            byte[] credentialPublicKeyCose,
            byte[] clientDataJson,
            byte[] authenticatorData,
            byte[] signature,
            boolean userVerified,
            long signatureCount,
            @Nullable CredentialKeyStamp credentialKeyStamp) {}
}
