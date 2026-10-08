/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.signing.entity.DocumentToSign;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.OpenSignature;
import dev.chojo.ember.feature.signing.entity.ParkedSigningStart;
import dev.chojo.ember.feature.signing.entity.PendingSignature;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation.StepUpPassed;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SignerEntryDraft;
import dev.chojo.ember.feature.signing.entity.SigningAnswer;
import dev.chojo.ember.feature.signing.entity.SigningAttempt;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningOutcome;
import dev.chojo.ember.feature.signing.entity.SigningRequest;
import dev.chojo.ember.feature.signing.entity.SigningStart;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Runs a person's signing act at a station, from the start to the stored evidence.
 *
 * <p><b>Start.</b> The field has to be open and the caller's to sign, in the capacity the household rules
 * give them ({@link SignatureRequestService#openFor}); the document is read from where it is filed and has
 * to still be the one the request froze. The provider issues the nonce and the challenge, and the start is
 * kept on the server ({@link SigningStarts}). For a passkey or security key the browser gets request
 * options built around that challenge.
 *
 * <p><b>Completion.</b> The start is spent first, so a refused confirmation spends it too. The request is
 * rebuilt from the field and the kept start, never from what the browser sends, and has to still yield
 * the challenge the start was issued with, which catches a document or statement that changed in between
 * for every proof, bound or not. An authenticator app code or the password is checked here and only the
 * fact that it passed reaches the provider; the fixed code a development instance takes for any account is
 * never a code here. A passkey or security key answer goes to the provider as it came. The evidence is stored against the field by {@link SignatureFieldService#record}, which checks once
 * more that the field is open and the caller's.
 */
@Singleton
public class SigningActService {
    private static final Logger log = LoggerFactory.getLogger(SigningActService.class);
    private static final Set<StepUpProof> WEBAUTHN_PROOFS = Set.of(StepUpProof.PASSKEY, StepUpProof.SECURITY_KEY);

    private final SignatureRequestRepository requests;
    private final SignatureRequestService requestService;
    private final SignatureFieldService fields;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final SignatureProvider provider;
    private final SigningStarts starts;
    private final SigningAssertions assertions;
    private final SignerNames names;
    private final TwoFactorService twoFactor;
    private final AuthService auth;

    @Inject
    public SigningActService(
            SignatureRequestRepository requests,
            SignatureRequestService requestService,
            SignatureFieldService fields,
            DocumentRepository documents,
            DocumentService documentService,
            SignatureProvider provider,
            SigningStarts starts,
            SigningAssertions assertions,
            SignerNames names,
            TwoFactorService twoFactor,
            AuthService auth) {
        this.requests = requests;
        this.requestService = requestService;
        this.fields = fields;
        this.documents = documents;
        this.documentService = documentService;
        this.provider = provider;
        this.starts = starts;
        this.assertions = assertions;
        this.names = names;
        this.twoFactor = twoFactor;
        this.auth = auth;
    }

    /**
     * A field at the caller's station that waits for a signature the caller may give, with how they give
     * it.
     *
     * @param session the signer
     * @param fieldId the field
     * @return the field and the signer the caller acts as
     */
    public OpenSignature requireOwnedField(StationSession session, int fieldId) {
        PendingSignature pending = requests.findField(session.stationId(), fieldId)
                .orElseThrow(DocumentRefusal.SIGNING_FIELD_NOT_FOUND::raise);
        if (pending.field().state() != FieldState.OPEN) throw DocumentRefusal.SIGNING_FIELD_NOT_OPEN.raise();
        return requestService.openFor(session).stream()
                .filter(open -> open.pending().field().id() == fieldId)
                .findFirst()
                .orElseThrow(DocumentRefusal.SIGNING_FIELD_NOT_YOURS::raise);
    }

    /**
     * The document a field asks the caller to sign, exactly as the request froze it, for reading before
     * the act. Served only while the field waits for the caller, so it shows nobody a document they could
     * not sign, and refused once the filed bytes are no longer the frozen ones, so what is read is what the
     * act binds to.
     *
     * @param session the signer
     * @param fieldId the field
     * @return the document's file name and bytes
     */
    public DocumentToSign requireOwnedFieldDocument(StationSession session, int fieldId) {
        PendingSignature pending = requireOwnedField(session, fieldId).pending();
        Document document = filedDocument(pending);
        byte[] content = documentService
                .open(document, DocumentDoor.STATION)
                .orElseThrow(DocumentRefusal.SIGNING_DOCUMENT_GONE::raise);
        requireFrozen(session, pending, content);
        return new DocumentToSign(document.fileName(), content);
    }

    /**
     * Starts the caller's signing act on a field.
     *
     * @param session the signer
     * @param open    the field, as {@link #requireOwnedField} answered it for the caller
     * @param entries what the signer typed into fields of their own, or null for none
     * @return the started act with what the signer is shown
     */
    public SigningAttempt start(StationSession session, OpenSignature open, @Nullable List<SignerEntryDraft> entries) {
        List<SignerEntry> checked = SignerEntries.checked(entries);
        int fieldId = open.pending().field().id();
        Signer signer = open.signer();
        SigningRequest request = rebuild(session, open.pending(), signer, checked);
        SigningStart.InEmber started =
                switch (provider.start(request)) {
                    case SigningStart.InEmber inEmber -> inEmber;
                    case SigningStart.Redirect redirect ->
                        throw new IllegalStateException("Signing acts at an outside provider are not offered here");
                };
        var parked = starts.park(ParkedSigningStart.of(
                session.stationId(), fieldId, request.requestUid(), request.fieldName(), signer, started, checked));
        boolean webAuthn = started.acceptedProofs().stream().anyMatch(WEBAUTHN_PROOFS::contains);
        return new SigningAttempt(
                parked.token(),
                parked.expiresAt(),
                open.pending(),
                signer,
                names.accountHolder(signer.accountId()),
                names.member(signer.memberId()),
                HexFormat.of().formatHex(request.contentSha256()),
                started.acceptedProofs(),
                webAuthn ? assertions.requestOptions(signer.accountId(), started.challenge()) : null);
    }

    /**
     * Completes a started act with the signer's confirmation and stores its evidence.
     *
     * @param session       the signer, who has to be the one who started it
     * @param fieldId       the field the act was started for
     * @param startToken    the token the start was handed out under
     * @param answer        the signer's confirmation
     * @param circumstances where the confirmation came from
     * @return where the field and its request now stand
     */
    public SigningOutcome complete(
            StationSession session,
            int fieldId,
            String startToken,
            SigningAnswer answer,
            SigningCircumstances circumstances) {
        ParkedSigningStart parked = starts.spend(startToken, session.accountId());
        if (parked.stationId() != session.stationId() || parked.fieldId() != fieldId) {
            throw DocumentRefusal.SIGNING_START_UNKNOWN.raise();
        }
        PendingSignature pending = requests.findField(session.stationId(), fieldId)
                .orElseThrow(DocumentRefusal.SIGNING_FIELD_NOT_FOUND::raise);
        SigningRequest request = rebuild(session, pending, parked.signer(), parked.entries());
        if (!MessageDigest.isEqual(parked.challenge(), SigningChallenge.of(parked.nonce(), request))) {
            throw DocumentRefusal.SIGNING_CONTENT_DIFFERS.raise();
        }
        var stored = fields.record(session, provider.complete(request, confirmation(parked, answer, circumstances)));
        var field = requests.findField(session.stationId(), fieldId)
                .orElseThrow(DocumentRefusal.SIGNING_FIELD_NOT_FOUND::raise);
        var requestState = requestService.requestAt(session, field.requestUid()).state();
        return new SigningOutcome(
                field,
                requestState,
                stored.evidence().proof(),
                stored.evidence().boundToDocument());
    }

    /** The request to sign, with the document as it is filed, which has to be the one the request froze. */
    private SigningRequest rebuild(
            StationSession session, PendingSignature pending, Signer signer, List<SignerEntry> entries) {
        byte[] content =
                documentService.read(filedDocument(pending)).orElseThrow(DocumentRefusal.SIGNING_DOCUMENT_GONE::raise);
        requireFrozen(session, pending, content);
        return new SigningRequest(
                pending.requestUid(),
                session.stationId(),
                content,
                pending.field().statement(),
                signer,
                pending.field().fieldName(),
                entries);
    }

    /** The member document a field is on, which has to still be filed. */
    private Document filedDocument(PendingSignature pending) {
        Integer documentId = pending.documentId();
        if (documentId == null) throw DocumentRefusal.SIGNING_DOCUMENT_GONE.raise();
        return documents.findById(documentId).orElseThrow(DocumentRefusal.SIGNING_DOCUMENT_GONE::raise);
    }

    /** Refuses content that is not the one the field's request froze. */
    private void requireFrozen(StationSession session, PendingSignature pending, byte[] content) {
        var frozen = requestService.requestAt(session, pending.requestUid());
        if (!Sha256.hex(content).equals(frozen.contentSha256())) {
            throw DocumentRefusal.SIGNING_DOCUMENT_CHANGED.raise();
        }
    }

    /** The confirmation the provider checks, after checking a code or password here. */
    private SignerConfirmation confirmation(
            ParkedSigningStart parked, SigningAnswer answer, SigningCircumstances circumstances) {
        StepUpProof proof = answer.proof();
        if (!parked.acceptedProofs().contains(proof)) throw DocumentRefusal.SIGNING_PROOF_NOT_ACCEPTED.raise();
        int accountId = parked.accountId();
        return switch (proof) {
            case PASSKEY, SECURITY_KEY ->
                assertions.answer(parked.nonce(), required(answer.credentialJson()), circumstances);
            case TOTP -> {
                if (!twoFactor.verifyAuthenticatorCode(accountId, required(answer.secret()))) {
                    log.info("Signing code of account {} was not right for field {}", accountId, parked.fieldId());
                    throw DocumentRefusal.SIGNING_CODE_WRONG.raise();
                }
                yield new StepUpPassed(parked.nonce(), proof, circumstances);
            }
            case PASSWORD -> {
                if (!auth.verifyPassword(accountId, required(answer.secret()))) {
                    log.info("Signing password of account {} was not right for field {}", accountId, parked.fieldId());
                    throw DocumentRefusal.SIGNING_PASSWORD_WRONG.raise();
                }
                yield new StepUpPassed(parked.nonce(), proof, circumstances);
            }
            case BACKUP_CODE, ANOTHER_DEVICE -> throw DocumentRefusal.SIGNING_PROOF_NOT_ACCEPTED.raise();
        };
    }

    private static String required(@Nullable String value) {
        if (value == null || value.isBlank()) throw DocumentRefusal.SIGNING_ANSWER_MISSING.raise();
        return value;
    }
}
