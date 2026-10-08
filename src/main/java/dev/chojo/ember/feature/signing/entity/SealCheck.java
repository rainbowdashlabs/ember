/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * One signature found in a checked document, a station seal or any other.
 *
 * <p>The validator runs with this installation's authorities, the authorities pinned for its federation
 * partners and the pinned timestamp roots as the only trusted certificates, since a signature's timestamps
 * have to be trusted for it to be checked at all. A pinned timestamp root may also issue signing
 * certificates for others, so the validator alone could pass a stranger's seal. The {@link #indication()}
 * therefore never passes a signature that is neither issued here nor recognised as a partner's: such a
 * signature reads {@code INDETERMINATE} with {@code NOT_ISSUED_HERE}, unless the validator found it failed,
 * which stays failed. The validator's own verdict is kept beside it. A partner's seal that passes says so
 * by {@link #partner()}, never by {@link #issuedHere()}.
 *
 * @param signer          the certificate the signature was made with; null when the document does not
 *                        carry it
 * @param issuer          the certificate that issued the signer's, as the chain was built; null when the
 *                        chain has no second certificate, as with a self-signed one
 * @param issuedHere      whether the chain ends in one of this installation's authorities, active or
 *                        retired
 * @param partner         the federation partner whose seal it is: its chain ends in an authority pinned
 *                        for a partnership with that station and the signer's certificate names that
 *                        station; null for any other seal, including one issued here
 * @param indication      the verdict on the signature as a seal of this installation or of the partner
 * @param subIndication   why it did not pass; null when it passed
 * @param validatorIndication    the validator's own verdict, which counts the pinned timestamp roots as
 *                               trusted for the signature too
 * @param validatorSubIndication why the validator did not pass it; null when it did
 * @param level          the PAdES baseline level the signature was found to have
 * @param signingTime     the signing time the signature claims, which is only the signer's clock; null
 *                        when it claims none
 * @param intact          whether the signed bytes are unchanged and the signature value holds
 * @param coversWholeFile whether the signed revision runs to the end of the file. A sealed document
 *                        that carries its validation material has one more revision after the seal,
 *                        so false alone does not mean a change
 * @param modifiedAfterSealing whether a revision after the signed one changes what the document shows or
 *                        holds: its pages, what is drawn on them, its form fields, signatures or
 *                        annotations, or anything the validator cannot name. Validation material and
 *                        document timestamps added later are not such a change
 * @param revocation      what the revocation list of the issuing authority, this installation's or the
 *                        partner's last one taken in, says about the signer's certificate
 * @param timestamps      the timestamps inside the signature, proving when it existed
 */
public record SealCheck(
        @Nullable CertificateFacts signer,
        @Nullable CertificateFacts issuer,
        boolean issuedHere,
        @Nullable SealingPartner partner,
        ValidationIndication indication,
        @Nullable ValidationSubIndication subIndication,
        ValidationIndication validatorIndication,
        @Nullable ValidationSubIndication validatorSubIndication,
        PadesLevel level,
        @Nullable Instant signingTime,
        boolean intact,
        boolean coversWholeFile,
        boolean modifiedAfterSealing,
        SignerRevocation revocation,
        List<TimestampCheck> timestamps) {

    /** Copies the timestamps, so the check cannot change after the fact. */
    public SealCheck {
        timestamps = List.copyOf(timestamps);
    }
}
