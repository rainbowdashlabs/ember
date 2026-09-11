/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.mailimport.entity.MailImportOutcome;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.james.jdkim.DKIMVerifier;
import org.apache.james.jdkim.api.SignatureRecord;
import org.apache.james.jdkim.exceptions.TempFailException;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Whether a message really comes from the domain its address claims.
 *
 * <p>The address a rule matches against is written by whoever sent the message, so where the domain it
 * names publishes no strict policy of its own, anybody can write it into a {@code From} line and have
 * the attachment filed. A DKIM signature is the thing that can be checked here rather than taken on
 * trust from a header somebody else wrote.
 *
 * <p><b>The alignment is the boundary, not the signature.</b> A signature that verifies proves only that
 * whoever holds a domain's key signed the message, and an attacker signs with a domain of their own and
 * passes. What is asked here is that the signing domain is the domain the sender address claims, or one
 * the address sits under: the organisational reading, where {@code weber.co.uk} and {@code post.co.uk}
 * would count as one, is not used, because telling a registrable domain from a public suffix needs a
 * list this instance does not carry.
 *
 * <p>What is verified are the bytes as they arrived. The body hash is exact to the byte, and a message
 * that has been parsed and written back out is not the message that was sent, so the source comes
 * straight from the server and never from a re-serialised copy.
 */
@Singleton
public class DkimVerification {
    private static final int MAX_REASON = 300;

    private final Supplier<DKIMVerifier> verifiers;

    /**
     * The keys come from DNS, which is where a signing domain publishes them.
     *
     * <p>A verifier is made for each message rather than kept, because one collects the results of the
     * message it looked at. A supplier rather than the verifier itself, so that nothing reaches a
     * resolver until a mailbox that asked for this actually reads a message.
     */
    @Inject
    public DkimVerification() {
        this(DKIMVerifier::new);
    }

    DkimVerification(Supplier<DKIMVerifier> verifiers) {
        this.verifiers = verifiers;
    }

    /**
     * Why a message is not one to file, or nothing where it is.
     *
     * @param outcome what the log records it as
     * @param reason  the readable half, for whoever asks why nothing arrived
     */
    public record Refusal(MailImportOutcome outcome, String reason) {}

    /**
     * What is wrong with this message's signature, or nothing where nothing is.
     *
     * @param source the message exactly as the server handed it back
     * @param sender the bare address it claims to come from
     * @return why it is refused, or empty where it carries an aligned signature that verifies
     * @throws TempFailException where the key could not be looked up this time, which leaves the message
     *                           where it is so that the next visit can try again. A name server that is
     *                           briefly unreachable is not a reason to refuse somebody's post for good
     */
    public Optional<Refusal> objection(byte[] source, String sender) throws TempFailException {
        String claimed = domainOf(sender);
        if (claimed == null) {
            return Optional.of(new Refusal(
                    MailImportOutcome.SIGNATURE_NOT_ALIGNED, "The message names no address to align a signature with"));
        }

        List<SignatureRecord> verified;
        try {
            verified = verifiers.get().verify(new ByteArrayInputStream(source));
        } catch (TempFailException e) {
            throw e;
        } catch (Exception e) {
            return Optional.of(new Refusal(MailImportOutcome.SIGNATURE_FAILED, shortened(reasonOf(e))));
        }
        if (verified == null || verified.isEmpty()) {
            return Optional.of(new Refusal(
                    MailImportOutcome.NO_SIGNATURE,
                    "This mailbox files signed mail only, and nothing signed this message"));
        }

        for (SignatureRecord record : verified) {
            if (aligns(record.getDToken(), claimed)) return Optional.empty();
        }
        return Optional.of(new Refusal(
                MailImportOutcome.SIGNATURE_NOT_ALIGNED,
                shortened("Signed by %s, which is not the domain %s writes from"
                        .formatted(signingDomains(verified), claimed))));
    }

    /**
     * Whether the signing domain is the one the address claims.
     *
     * <p>A subdomain of the signing domain counts, which is how a house that signs everything with its
     * own name sends from a machine under it. The other direction never does: a signature by
     * {@code post.musterstadt.de} says nothing about mail from {@code musterstadt.de}.
     */
    private static boolean aligns(CharSequence signingDomain, String claimed) {
        if (signingDomain == null) return false;
        String signed = signingDomain.toString().trim().toLowerCase(Locale.ROOT);
        if (signed.isEmpty()) return false;
        return claimed.equals(signed) || claimed.endsWith("." + signed);
    }

    private static String signingDomains(List<SignatureRecord> verified) {
        return verified.stream()
                .map(SignatureRecord::getDToken)
                .filter(Objects::nonNull)
                .map(token -> token.toString().trim().toLowerCase(Locale.ROOT))
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("nobody");
    }

    private static String domainOf(String sender) {
        if (sender == null) return null;
        int at = sender.lastIndexOf('@');
        if (at < 0 || at == sender.length() - 1) return null;
        String domain = sender.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        return domain.isEmpty() ? null : domain;
    }

    private static String reasonOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "The signature could not be checked (%s)"
                        .formatted(e.getClass().getSimpleName())
                : "The signature does not hold: " + message;
    }

    private static String shortened(String reason) {
        return reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason;
    }
}
