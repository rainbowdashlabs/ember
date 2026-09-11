/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.mailimport.entity.MailImportOutcome;
import org.apache.james.jdkim.DKIMVerifier;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.exceptions.TempFailException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a message really comes from the domain it says it does.
 *
 * <p>The story worth telling twice is the one in the middle: a signature that verifies perfectly and is
 * refused anyway, because the domain that signed it is not the domain the address claims. Anybody can
 * sign their own forgery, so a verifier that stopped at "the signature is good" would look like a
 * boundary and be none.
 */
class DkimVerificationTest {

    private static final String SENDER = "post@musterstadt.de";

    private static DkimVerification verification(PublicKeyRecordRetriever keys) {
        return new DkimVerification(() -> new DKIMVerifier(keys));
    }

    private static byte[] message(String from) {
        return ("From: " + from + "\r\n"
                        + "To: archive@musterstadt.de\r\n"
                        + "Subject: Pruefbescheinigung\r\n"
                        + "\r\n"
                        + "Anbei die Unterlagen.\r\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void aSignatureOfTheDomainTheSenderWritesFromIsAccepted() throws Exception {
        byte[] signed = SignedMail.signedBy("musterstadt.de", message(SENDER));

        assertTrue(
                verification(SignedMail.publishedKeys())
                        .objection(signed, SENDER)
                        .isEmpty(),
                "signed by the domain it says it is from");
    }

    /** The whole point. It verifies, and it is somebody else's signature on somebody else's name. */
    @Test
    void aSignatureOfAnotherDomainIsRefusedEvenThoughItVerifies() throws Exception {
        byte[] signed = SignedMail.signedBy("fremder.de", message(SENDER));

        var objection = verification(SignedMail.publishedKeys())
                .objection(signed, SENDER)
                .orElseThrow();

        assertEquals(MailImportOutcome.SIGNATURE_NOT_ALIGNED, objection.outcome());
        assertTrue(objection.reason().contains("fremder.de"), "and the log says who really signed it");
    }

    /** A house that signs everything with its own name still sends from the machines under it. */
    @Test
    void anAddressUnderTheSigningDomainCountsAsTheSameDomain() throws Exception {
        String fromSubdomain = "post@versand.musterstadt.de";
        byte[] signed = SignedMail.signedBy("musterstadt.de", message(fromSubdomain));

        assertTrue(verification(SignedMail.publishedKeys())
                .objection(signed, fromSubdomain)
                .isEmpty());
    }

    /** The other direction never counts: a signature by one machine says nothing about the house. */
    @Test
    void theDomainSigningACornerOfAHouseDoesNotSpeakForTheWholeHouse() throws Exception {
        byte[] signed = SignedMail.signedBy("versand.musterstadt.de", message(SENDER));

        assertEquals(
                MailImportOutcome.SIGNATURE_NOT_ALIGNED,
                verification(SignedMail.publishedKeys())
                        .objection(signed, SENDER)
                        .orElseThrow()
                        .outcome());
    }

    @Test
    void aMessageNobodySignedIsRefused() throws Exception {
        var objection = verification(SignedMail.publishedKeys())
                .objection(message(SENDER), SENDER)
                .orElseThrow();

        assertEquals(MailImportOutcome.NO_SIGNATURE, objection.outcome());
    }

    /** What a message altered on the way looks like: the body hash is exact to the byte. */
    @Test
    void aBodyChangedAfterItWasSignedIsRefused() throws Exception {
        byte[] signed = SignedMail.signedBy("musterstadt.de", message(SENDER));
        byte[] tampered = new String(signed, StandardCharsets.ISO_8859_1)
                .replace("Anbei die Unterlagen.", "Anbei die Rechnungen.")
                .getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(
                MailImportOutcome.SIGNATURE_FAILED,
                verification(SignedMail.publishedKeys())
                        .objection(tampered, SENDER)
                        .orElseThrow()
                        .outcome());
    }

    @Test
    void aSignatureWhoseDomainPublishesNoKeyIsRefused() throws Exception {
        byte[] signed = SignedMail.signedBy("musterstadt.de", message(SENDER));

        assertEquals(
                MailImportOutcome.SIGNATURE_FAILED,
                verification(SignedMail.noPublishedKeys())
                        .objection(signed, SENDER)
                        .orElseThrow()
                        .outcome());
    }

    /**
     * A name server that is briefly unreachable is not a reason to refuse somebody's post for good, so
     * this leaves the message where it is rather than writing it off.
     */
    @Test
    void aKeyThatCouldNotBeLookedUpThisTimeIsNotAVerdict() throws Exception {
        byte[] signed = SignedMail.signedBy("musterstadt.de", message(SENDER));
        PublicKeyRecordRetriever unreachable = (method, selector, domain) -> {
            throw new TempFailException("the name server did not answer");
        };

        assertThrows(TempFailException.class, () -> verification(unreachable).objection(signed, SENDER));
    }

    @Test
    void aMessageWithNoSenderAtAllHasNothingToAlignWith() throws Exception {
        var objection = verification(SignedMail.publishedKeys())
                .objection(message(SENDER), null)
                .orElseThrow();

        assertEquals(MailImportOutcome.SIGNATURE_NOT_ALIGNED, objection.outcome());
        assertEquals(
                MailImportOutcome.SIGNATURE_NOT_ALIGNED,
                verification(SignedMail.publishedKeys())
                        .objection(message(SENDER), "kein-at-zeichen")
                        .orElseThrow()
                        .outcome());
    }

    /** Where nobody asked for a key, nothing is looked up, which is why this needs no name server. */
    @Test
    void theOrdinaryVerifierLooksKeysUpInDns() throws Exception {
        assertEquals(
                MailImportOutcome.NO_SIGNATURE,
                new DkimVerification()
                        .objection(message(SENDER), SENDER)
                        .orElseThrow()
                        .outcome());
    }
}
