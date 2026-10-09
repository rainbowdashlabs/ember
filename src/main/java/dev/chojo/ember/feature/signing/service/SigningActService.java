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
import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.generator.service.pdf.FillInFields;
import dev.chojo.ember.feature.signing.entity.ActPicture;
import dev.chojo.ember.feature.signing.entity.ActPictureSource;
import dev.chojo.ember.feature.signing.entity.BatchAttempt;
import dev.chojo.ember.feature.signing.entity.BatchChoice;
import dev.chojo.ember.feature.signing.entity.DocumentToSign;
import dev.chojo.ember.feature.signing.entity.OpenSignature;
import dev.chojo.ember.feature.signing.entity.ParkedSigningStart;
import dev.chojo.ember.feature.signing.entity.PendingSignature;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.entity.SignaturePicture;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignedAct;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation.StepUpPassed;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SignerEntryDraft;
import dev.chojo.ember.feature.signing.entity.SigningAnswer;
import dev.chojo.ember.feature.signing.entity.SigningAttempt;
import dev.chojo.ember.feature.signing.entity.SigningBatch;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningOutcome;
import dev.chojo.ember.feature.signing.entity.SigningPicture;
import dev.chojo.ember.feature.signing.entity.SigningPictures;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Runs a person's signing act at a station, from the start to the stored evidence.
 *
 * <p><b>Start.</b> The field has to be open and the caller's to sign, in the capacity the household rules
 * give them ({@link SignatureRequestService#openFor}); the document is read from where it is filed and has
 * to still be the one the request froze. The provider issues the nonce and the challenge, and the start is
 * kept on the server ({@link SigningStarts}). For a passkey or security key the browser gets request
 * options built around that challenge. What the signer typed into the fields the frozen document asks them
 * to fill in ({@link FillInFields}) is checked against those fields here ({@link SignerEntries}) and bound
 * into the challenge with the rest; the completion takes the values from the kept start, never again from
 * the browser.
 *
 * <p><b>Completion.</b> The start is spent first, so a refused confirmation spends it too. The request is
 * rebuilt from the field and the kept start, never from what the browser sends, and has to still yield
 * the challenge the start was issued with, which catches a document or statement that changed in between
 * for every proof, bound or not. An authenticator app code or the password is checked here and only the
 * fact that it passed reaches the provider; the fixed code a development instance takes for any account is
 * never a code here. A passkey or security key answer goes to the provider as it came. The evidence is
 * stored against each field by {@link SignatureFieldService#recordAll}, which checks once more that every
 * field is open and the caller's.
 *
 * <p><b>Several fields at once.</b> One act can cover every field the caller may sign now, across documents
 * and across the members in their care ({@link #startBatch}, {@link #completeBatch}): each field is checked
 * as it would be on its own, the provider binds them all into one challenge ({@link SigningBatchChallenge}),
 * and one proof confirms them. Each field still gets evidence of its own, which names the batch. A single
 * field is a batch of one and is bound exactly as a field signed on its own always was.
 *
 * <p><b>Signature picture.</b> Every act leaves the signer's picture in its field: one made for the act, or
 * the one the account keeps. It is stored with the evidence together with how it came to the act (drawn,
 * typed or uploaded for it, or saved before), so each sealed version draws the picture the act was given and
 * not whatever the account keeps later, and names it by its hash and source in the evidence and the record.
 *
 * <p><b>Sealing.</b> Once the evidence is stored, each request's new state is sealed into its document once
 * ({@link SigningStateSealer}), however many of its fields the act signed. A seal that fails leaves the act
 * recorded; it is sealed with the next act on the request or by {@link SigningStateSweeper}. Every act reads
 * and binds to the file the document was filed with, never to a sealed version, so a later signer signs the
 * same content as the first.
 */
@Singleton
public class SigningActService {
    /** The most fields one act signs. */
    public static final int MAX_BATCH = 50;

    private static final Logger log = LoggerFactory.getLogger(SigningActService.class);
    private static final Set<StepUpProof> WEBAUTHN_PROOFS = Set.of(StepUpProof.PASSKEY, StepUpProof.SECURITY_KEY);

    private final SignatureRequestRepository requests;
    private final SignatureRequestService requestService;
    private final SignatureFieldService fields;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final SignatureProvider provider;
    private final SigningStateSealer stateSealer;
    private final SigningStarts starts;
    private final SigningAssertions assertions;
    private final SignerNames names;
    private final TwoFactorService twoFactor;
    private final AuthService auth;
    private final SignatureImageService images;

    @Inject
    public SigningActService(
            SignatureRequestRepository requests,
            SignatureRequestService requestService,
            SignatureFieldService fields,
            DocumentRepository documents,
            DocumentService documentService,
            SignatureProvider provider,
            SigningStateSealer stateSealer,
            SigningStarts starts,
            SigningAssertions assertions,
            SignerNames names,
            TwoFactorService twoFactor,
            AuthService auth,
            SignatureImageService images) {
        this.images = images;
        this.requests = requests;
        this.requestService = requestService;
        this.fields = fields;
        this.documents = documents;
        this.documentService = documentService;
        this.provider = provider;
        this.stateSealer = stateSealer;
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
     * <p>Whose the field is comes first: a field that does not ask the caller is refused the same way
     * whether it exists or not, and whatever state it is in, so nobody learns about fields that are not
     * theirs by trying their numbers. Only the caller's own field is told apart as no longer waiting.
     *
     * @param session the signer
     * @param fieldId the field
     * @return the field and the signer the caller acts as
     */
    public OpenSignature requireOwnedField(StationSession session, int fieldId) {
        var open = requestService.openFor(session).stream()
                .filter(signature -> signature.pending().field().id() == fieldId)
                .findFirst();
        if (open.isPresent()) return open.get();
        if (requestService.asks(session, fieldId)) throw DocumentRefusal.SIGNING_FIELD_NOT_OPEN.raise();
        throw DocumentRefusal.SIGNING_FIELD_NOT_YOURS.raise();
    }

    /**
     * The document a field asks the caller to sign, exactly as the request froze it, for reading before
     * the act: the file the document was filed with, also once earlier acts sealed versions of it. Served
     * only while the field waits for the caller, so it shows nobody a document they could not sign, and
     * refused once that file is no longer the frozen one, so what is read is what the act binds to.
     *
     * @param session the signer
     * @param fieldId the field
     * @return the document's file name and bytes
     */
    public DocumentToSign requireOwnedFieldDocument(StationSession session, int fieldId) {
        PendingSignature pending = requireOwnedField(session, fieldId).pending();
        Document document = filedDocument(pending);
        byte[] content = documentService
                .openUploaded(document, DocumentDoor.STATION)
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
        var attempt = start(
                session,
                List.of(open),
                List.of(new BatchChoice(open.pending().field().id(), entries)));
        var item = attempt.items().getFirst();
        return new SigningAttempt(
                attempt.startToken(),
                attempt.expiresAt(),
                item.field(),
                item.signer(),
                item.accountHolderName(),
                item.memberName(),
                item.contentSha256(),
                attempt.acceptedProofs(),
                attempt.webAuthnOptionsJson());
    }

    /**
     * Starts the caller's signing act on several fields at once, confirmed later by one proof: across the
     * documents they are asked to sign and across the members in their care, in the order chosen.
     *
     * <p>Every field has to wait for the caller, each is checked as a field signed on its own would be,
     * and what the caller typed for each is checked against the fields that document asks its signer to
     * fill in. One field alone is started as a single act.
     *
     * @param session the signer
     * @param choices the fields, in the order they are signed, each with what was typed for it
     * @return the started act with what the signer is shown for each field
     */
    public BatchAttempt startBatch(StationSession session, List<BatchChoice> choices) {
        if (choices.isEmpty()) throw DocumentRefusal.SIGNING_BATCH_EMPTY.raise();
        if (choices.size() > MAX_BATCH) throw DocumentRefusal.SIGNING_BATCH_TOO_LARGE.raise();
        var fieldIds = new HashSet<Integer>();
        for (var choice : choices) {
            if (!fieldIds.add(choice.fieldId())) throw DocumentRefusal.SIGNING_BATCH_FIELD_TWICE.raise();
        }
        return start(session, requireOwnedFields(session, choices), choices);
    }

    private BatchAttempt start(StationSession session, List<OpenSignature> opens, List<BatchChoice> choices) {
        var contents = new FrozenContents(session);
        var toSign = new ArrayList<SigningRequest>(opens.size());
        for (int position = 0; position < opens.size(); position++) {
            var open = opens.get(position);
            byte[] content = contents.of(open.pending());
            List<SignerEntry> checked = SignerEntries.checked(
                    choices.get(position).entries(),
                    FillInFields.forSigner(content, open.pending().field().fieldName()));
            toSign.add(requestOf(session, open.pending(), open.signer(), content, checked));
        }
        var batch = new SigningBatch(UUID.randomUUID(), toSign);
        SigningStart.InEmber started =
                switch (provider.start(batch)) {
                    case SigningStart.InEmber inEmber -> inEmber;
                    case SigningStart.Redirect redirect ->
                        throw new IllegalStateException("Signing acts at an outside provider are not offered here");
                };
        var ids = opens.stream().map(open -> open.pending().field().id()).toList();
        var parked = starts.park(ParkedSigningStart.of(session.stationId(), batch, ids, started));
        boolean webAuthn = started.acceptedProofs().stream().anyMatch(WEBAUTHN_PROOFS::contains);
        String accountHolder = names.accountHolder(batch.accountId());
        var items = new ArrayList<BatchAttempt.Item>(opens.size());
        for (int position = 0; position < opens.size(); position++) {
            var open = opens.get(position);
            items.add(new BatchAttempt.Item(
                    open.pending(),
                    open.signer(),
                    accountHolder,
                    names.member(open.signer().memberId()),
                    HexFormat.of().formatHex(toSign.get(position).contentSha256())));
        }
        return new BatchAttempt(
                parked.token(),
                parked.expiresAt(),
                batch.uid(),
                items,
                started.acceptedProofs(),
                webAuthn ? assertions.requestOptions(batch.accountId(), started.challenge()) : null);
    }

    /**
     * Completes a started act on one field with the signer's confirmation, stores its evidence and the
     * signature picture it leaves in its field.
     *
     * @param session       the signer, who has to be the one who started it
     * @param fieldId       the field the act was started for
     * @param startToken    the token the start was handed out under
     * @param answer        the signer's confirmation
     * @param picture       the signature picture to leave in the field
     * @param circumstances where the confirmation came from
     * @return where the field and its request now stand
     * @see #completeBatch
     */
    public SigningOutcome complete(
            StationSession session,
            int fieldId,
            String startToken,
            SigningAnswer answer,
            SigningPicture picture,
            SigningCircumstances circumstances) {
        ParkedSigningStart parked = spend(session, startToken);
        if (parked.items().size() != 1 || parked.items().getFirst().fieldId() != fieldId) {
            throw DocumentRefusal.SIGNING_START_UNKNOWN.raise();
        }
        var signer = parked.signer(parked.items().getFirst());
        return complete(session, parked, answer, SigningPictures.single(picture, signer), circumstances)
                .getFirst();
    }

    /**
     * Completes a started act on one or more fields with the signer's one confirmation, stores the evidence
     * of each field and the signature picture each leaves, and seals each document it touched once.
     *
     * <p>The pictures are one per person: the account holder's, made for the act or kept by the account, for
     * every field they sign themselves or as a guardian, and one made for the act by each member signing
     * their own field through the account, since the account's picture is not theirs. Without a picture for
     * somebody who signs, the act is refused before anything is confirmed, and so is a picture for somebody
     * who signs nothing here. A picture made for the act replaces the account's saved one afterwards where
     * the account holder asked for it; a failure there is logged and the act stands.
     *
     * <p>All fields are recorded or none ({@link SignatureFieldService#recordAll}). Each request touched is
     * then sealed once with every act it received ({@link SigningStateSealer}); a seal that fails leaves the
     * acts recorded, and the sweep seals them later.
     *
     * @param session       the signer, who has to be the one who started it
     * @param startToken    the token the start was handed out under
     * @param answer        the signer's confirmation
     * @param pictures      the signature pictures, one per person who signs
     * @param circumstances where the confirmation came from
     * @return where each field and its request now stand, in the order they were signed
     */
    public List<SigningOutcome> completeBatch(
            StationSession session,
            String startToken,
            SigningAnswer answer,
            SigningPictures pictures,
            SigningCircumstances circumstances) {
        return complete(session, spend(session, startToken), answer, pictures, circumstances);
    }

    private ParkedSigningStart spend(StationSession session, String startToken) {
        ParkedSigningStart parked = starts.spend(startToken, session.accountId());
        if (parked.stationId() != session.stationId()) throw DocumentRefusal.SIGNING_START_UNKNOWN.raise();
        return parked;
    }

    private List<SigningOutcome> complete(
            StationSession session,
            ParkedSigningStart parked,
            SigningAnswer answer,
            SigningPictures pictures,
            SigningCircumstances circumstances) {
        var contents = new FrozenContents(session);
        var pendings = new ArrayList<PendingSignature>(parked.items().size());
        var requestsToSign = new ArrayList<SigningRequest>(parked.items().size());
        for (var item : parked.items()) {
            PendingSignature pending = requests.findField(session.stationId(), item.fieldId())
                    .orElseThrow(DocumentRefusal.SIGNING_FIELD_NOT_FOUND::raise);
            pendings.add(pending);
            requestsToSign.add(requestOf(session, pending, parked.signer(item), contents.of(pending), item.entries()));
        }
        var batch = new SigningBatch(parked.batchUid(), requestsToSign);
        if (!MessageDigest.isEqual(parked.challenge(), SigningChallenge.of(parked.nonce(), batch))) {
            throw DocumentRefusal.SIGNING_CONTENT_DIFFERS.raise();
        }
        var marks = new ActPictures(session.accountId(), pictures, batch);
        var completed = provider.complete(batch, confirmation(parked, answer, circumstances));
        var acts = new ArrayList<SignedAct>(completed.size());
        for (var each : completed) {
            acts.add(new SignedAct(each, marks.of(each.evidence().act().signer())));
        }
        var stored = fields.recordAll(session, acts);
        marks.keep();
        var states = new LinkedHashMap<UUID, SignatureRequest>();
        for (var pending : pendings) {
            states.computeIfAbsent(pending.requestUid(), uid -> requestService.requestAt(session, uid));
        }
        states.values().forEach(this::sealRecorded);
        var outcomes = new ArrayList<SigningOutcome>(pendings.size());
        for (int position = 0; position < pendings.size(); position++) {
            var field = requests.findField(
                            session.stationId(), pendings.get(position).field().id())
                    .orElseThrow(DocumentRefusal.SIGNING_FIELD_NOT_FOUND::raise);
            var evidenceOfField = stored.get(position).evidence();
            outcomes.add(new SigningOutcome(
                    field,
                    states.get(field.requestUid()).state(),
                    evidenceOfField.proof(),
                    evidenceOfField.boundToDocument()));
        }
        return outcomes;
    }

    /**
     * The fields the caller chose that wait for them, in the order chosen, each with how they sign it.
     * Refused as {@link #requireOwnedField} refuses a single field.
     */
    private List<OpenSignature> requireOwnedFields(StationSession session, List<BatchChoice> choices) {
        var open = new HashMap<Integer, OpenSignature>();
        requestService
                .openFor(session)
                .forEach(each -> open.put(each.pending().field().id(), each));
        var chosen = new ArrayList<OpenSignature>(choices.size());
        for (var choice : choices) {
            var found = open.get(choice.fieldId());
            if (found != null) {
                chosen.add(found);
                continue;
            }
            if (requestService.asks(session, choice.fieldId())) throw DocumentRefusal.SIGNING_FIELD_NOT_OPEN.raise();
            throw DocumentRefusal.SIGNING_FIELD_NOT_YOURS.raise();
        }
        return chosen;
    }

    /**
     * The signature picture each person of one confirmation signs with, cleaned and checked before anything
     * is confirmed, and kept as the account's own afterwards where the account holder asked for it.
     */
    private final class ActPictures {
        private final int accountId;
        private final @Nullable ActPicture accountHolder;
        private final Map<Integer, ActPicture> members = new HashMap<>();
        private final SigningPicture accountHolderChoice;
        private final @Nullable SignaturePicture accountHolderMade;

        ActPictures(int accountId, SigningPictures pictures, SigningBatch batch) {
            this.accountId = accountId;
            Set<Integer> through = new HashSet<>();
            boolean ownSigns = false;
            for (var request : batch.requests()) {
                Integer memberId = request.signer().memberId();
                if (request.signer().throughAnotherAccount() && memberId != null) {
                    through.add(memberId);
                } else {
                    ownSigns = true;
                }
            }
            if (!through.containsAll(pictures.members().keySet())) {
                throw DocumentRefusal.SIGNING_PICTURE_FOR_NOBODY.raise();
            }
            accountHolderChoice = pictures.accountHolder();
            byte[] ownMade = accountHolderChoice.made();
            if (!ownSigns && ownMade != null) throw DocumentRefusal.SIGNING_PICTURE_FOR_NOBODY.raise();
            accountHolderMade = ownMade == null ? null : SignatureImages.clean(ownMade);
            accountHolder = ownSigns ? accountHolderPicture() : null;
            for (int memberId : through) {
                SigningPicture picture = pictures.members().get(memberId);
                byte[] made = picture == null ? null : picture.made();
                if (picture == null || made == null) throw DocumentRefusal.SIGNING_MARK_MISSING.raise();
                members.put(
                        memberId,
                        new ActPicture(SignatureImages.clean(made).png(), ActPictureSource.madeAs(picture.source())));
            }
        }

        private ActPicture accountHolderPicture() {
            if (accountHolderMade != null) {
                return new ActPicture(accountHolderMade.png(), ActPictureSource.madeAs(accountHolderChoice.source()));
            }
            byte[] saved = images.image(accountId).orElseThrow(DocumentRefusal.SIGNING_MARK_MISSING::raise);
            return new ActPicture(saved, ActPictureSource.SAVED);
        }

        /** The picture a field is signed with by its signer. */
        ActPicture of(Signer signer) {
            Integer memberId = signer.memberId();
            var picture = signer.throughAnotherAccount() && memberId != null ? members.get(memberId) : accountHolder;
            if (picture == null) throw new IllegalStateException("No picture for a signer of the batch");
            return picture;
        }

        /** Saves the account holder's picture made for the act as the account's own, where they asked for it. */
        void keep() {
            if (accountHolderMade == null || !accountHolderChoice.keep()) return;
            try {
                images.save(
                        accountId,
                        accountHolderMade,
                        Objects.requireNonNullElse(accountHolderChoice.source(), SignatureImageSource.DRAWN));
            } catch (RuntimeException e) {
                log.warn("The signature picture of account {} could not be kept after signing", accountId, e);
            }
        }
    }

    /** The frozen content of each request read once, however many of its fields one act signs. */
    private final class FrozenContents {
        private final StationSession session;
        private final Map<UUID, byte[]> read = new HashMap<>();

        FrozenContents(StationSession session) {
            this.session = session;
        }

        byte[] of(PendingSignature pending) {
            return read.computeIfAbsent(pending.requestUid(), uid -> frozenContent(session, pending));
        }
    }

    /**
     * Seals the state the act left the request in. The act is recorded whatever happens here: a seal that
     * fails is logged, and the next act on the request or {@link SigningStateSweeper} seals the state then.
     */
    private void sealRecorded(SignatureRequest request) {
        try {
            stateSealer.sealLatest(request.id());
        } catch (RuntimeException e) {
            log.warn("Signing request {} was signed but not sealed yet; it is sealed later", request.uid(), e);
        }
    }

    /**
     * The fields a field's signer is asked to fill in when they sign it, as the frozen document carries
     * them, for a field that waits for the caller.
     *
     * @param session the signer
     * @param fieldId the signature field
     * @return the fields to fill in, in the order the document lists them, none where it asks for none
     */
    public List<FillInField> requireOwnedFillIns(StationSession session, int fieldId) {
        PendingSignature pending = requireOwnedField(session, fieldId).pending();
        return FillInFields.forSigner(
                frozenContent(session, pending), pending.field().fieldName());
    }

    /**
     * The file the document was filed with, which has to be the one the request froze. A document sealed
     * by an earlier act keeps that file beside its sealed versions.
     */
    private byte[] frozenContent(StationSession session, PendingSignature pending) {
        byte[] content = documentService
                .readUploaded(filedDocument(pending))
                .orElseThrow(DocumentRefusal.SIGNING_DOCUMENT_GONE::raise);
        requireFrozen(session, pending, content);
        return content;
    }

    /** The request to sign, over the frozen content. */
    private static SigningRequest requestOf(
            StationSession session,
            PendingSignature pending,
            Signer signer,
            byte[] content,
            List<SignerEntry> entries) {
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
                    log.info("Signing code of account {} was not right for batch {}", accountId, parked.batchUid());
                    throw DocumentRefusal.SIGNING_CODE_WRONG.raise();
                }
                yield new StepUpPassed(parked.nonce(), proof, circumstances);
            }
            case PASSWORD -> {
                if (!auth.verifyPassword(accountId, required(answer.secret()))) {
                    log.info("Signing password of account {} was not right for batch {}", accountId, parked.batchUid());
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
