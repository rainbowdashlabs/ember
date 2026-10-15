/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.signing.entity.BatchAttempt;
import dev.chojo.ember.feature.signing.entity.BatchChoice;
import dev.chojo.ember.feature.signing.entity.DocumentToSign;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.OpenSignature;
import dev.chojo.ember.feature.signing.entity.PendingSignature;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerEntryDraft;
import dev.chojo.ember.feature.signing.entity.SigningAnswer;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningOutcome;
import dev.chojo.ember.feature.signing.entity.SigningPictures;
import dev.chojo.ember.feature.signing.entity.WitnessPerson;
import dev.chojo.ember.feature.signing.entity.WitnessedField;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Signatures collected in person: a witness picks a person of the station and a document for them, hands
 * their device over, the person reads, agrees and draws their signature, and the witness confirms with their
 * own step-up that this person signed in front of them.
 *
 * <p>The document is an open field of one of the person's documents, or one generated on the spot from a
 * template that asks the member or a guardian to sign, filed with the person alone. Whom the witness
 * reaches and which fields they collect is {@link WitnessReach}'s to say; every step here asks it again, so
 * the witness reads a document only while they may collect a signature on it.
 *
 * <p>The act itself is the one every signature goes through ({@link SigningActService}), with the capacity
 * {@link dev.chojo.ember.feature.signing.entity.SignerCapacity#WITNESSED}: the challenge binds the content,
 * the statement, what was typed, the person who signed and the witness's account; the witness's proof
 * confirms it; the evidence names both. A start made here is completed only here, and a start of the
 * witness's own signatures never here.
 */
@Singleton
public class WitnessedSigningService {
    private final WitnessReach reach;
    private final SignatureRequestRepository requests;
    private final SignatureRequestService requestService;
    private final SigningActService acts;
    private final DocumentGenerationService generation;
    private final MemberNameResolver names;

    @Inject
    public WitnessedSigningService(
            WitnessReach reach,
            SignatureRequestRepository requests,
            SignatureRequestService requestService,
            SigningActService acts,
            DocumentGenerationService generation,
            MemberNameResolver names) {
        this.reach = reach;
        this.requests = requests;
        this.requestService = requestService;
        this.acts = acts;
        this.generation = generation;
        this.names = names;
    }

    /**
     * The people the witness may collect signatures from.
     *
     * @param session the witness
     * @return the members and trial users they reach, by name
     */
    public List<WitnessPerson> people(StationSession session) {
        reach.requireWitness(session);
        return reach.reachable(session).stream()
                .map(this::person)
                .sorted(Comparator.comparing(WitnessPerson::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * What a witness may collect from a person: the open fields of the person's documents that the
     * person's side signs, and the templates a document can be generated from on the spot.
     *
     * @param session  the witness
     * @param memberId the person
     * @return the open fields and the templates
     */
    public Offers offers(StationSession session, int memberId) {
        reach.requireWitness(session);
        reach.requireReachable(session, memberId);
        var fields = requests.openAbout(session.stationId(), memberId).stream()
                .filter(pending -> reach.offers(session, pending.field()))
                .map(pending -> witnessed(session, pending))
                .toList();
        return new Offers(fields, generation.askingMemberSignatures(session.owner()));
    }

    /**
     * What a witness may collect from a person.
     *
     * @param fields    the open fields of the person's documents, each with the people it names
     * @param templates the templates a document can be generated from for them
     */
    public record Offers(List<WitnessedField> fields, List<DocumentTemplate> templates) {}

    /**
     * Generates a document from a template for the person and asks for its signatures, without telling
     * anybody, so they can be signed in front of the witness right away.
     *
     * @param session    the witness
     * @param memberId   the person
     * @param templateId the template
     * @return the fields the witness may collect on the new document
     */
    public List<WitnessedField> generate(StationSession session, int memberId, int templateId) {
        reach.requireWitness(session);
        reach.requireReachable(session, memberId);
        var generated = generation.generateForWitness(session, templateId, memberId);
        var asked = requestService.requestForWitness(session.member().id(), generated.generationId());
        var request = asked.request();
        if (request == null) throw DocumentRefusal.SIGNING_NO_FIELDS.raise();
        return requests.fieldsOf(request.id()).stream()
                .filter(field -> field.state() == FieldState.OPEN && reach.offers(session, field))
                .flatMap(field -> requests.findField(session.stationId(), field.id()).stream())
                .map(pending -> witnessed(session, pending))
                .toList();
    }

    /**
     * An open field the witness may collect, with the people it names.
     *
     * <p>A field the witness may not collect is refused the same way whether it is open or not, so nobody
     * learns how the fields of people out of reach stand.
     *
     * @param session the witness
     * @param fieldId the field
     * @return the field
     */
    public WitnessedField field(StationSession session, int fieldId) {
        reach.requireWitness(session);
        var pending = requests.findField(session.stationId(), fieldId)
                .orElseThrow(DocumentRefusal.SIGNING_FIELD_NOT_FOUND::raise);
        reach.requireCollectable(session, pending.field());
        boolean open = pending.field().state() == FieldState.OPEN
                && requestService.requestAt(session, pending.requestUid()).open();
        if (!open) throw DocumentRefusal.SIGNING_FIELD_NOT_OPEN.raise();
        return witnessed(session, pending);
    }

    private WitnessedField witnessed(StationSession session, PendingSignature pending) {
        return new WitnessedField(
                pending,
                reach.signersOf(session, pending.field()).stream()
                        .map(this::person)
                        .toList());
    }

    private WitnessPerson person(StationMember member) {
        return new WitnessPerson(member.id(), names.official(member.id()), member.userType());
    }

    /**
     * The document of a field the witness may collect, exactly as its request froze it.
     *
     * @param session the witness
     * @param fieldId the field
     * @return the document's file name and bytes
     */
    public DocumentToSign document(StationSession session, int fieldId) {
        return acts.frozenDocument(session, field(session, fieldId).pending());
    }

    /**
     * The fields the signer of a field the witness may collect fills in when they sign it.
     *
     * @param session the witness
     * @param fieldId the field
     * @return the fields to fill in, in the order the document lists them
     */
    public List<FillInField> fillIns(StationSession session, int fieldId) {
        return acts.fillInsOf(session, field(session, fieldId).pending());
    }

    /**
     * Starts the act of a person signing a field in front of the witness.
     *
     * @param session  the witness
     * @param fieldId  the field
     * @param signerId the person in front of them, one of those the field names
     * @param entries  what the person typed into fields of their own, or null for none
     * @return the started act
     */
    public BatchAttempt start(
            StationSession session, int fieldId, int signerId, @Nullable List<SignerEntryDraft> entries) {
        var field = field(session, fieldId);
        reach.requireSigner(session, field.pending().field(), signerId);
        var open = new OpenSignature(field.pending(), Signer.witnessed(session.accountId(), signerId));
        return acts.start(session, List.of(open), List.of(new BatchChoice(fieldId, entries)));
    }

    /**
     * Completes an act signed in front of the witness with the witness's confirmation.
     *
     * @param session       the witness, who started it
     * @param startToken    the token the start was handed out under
     * @param answer        the witness's confirmation
     * @param pictures      the picture the person drew
     * @param circumstances where the confirmation came from
     * @return where the field and its request now stand
     */
    public List<SigningOutcome> complete(
            StationSession session,
            String startToken,
            SigningAnswer answer,
            SigningPictures pictures,
            SigningCircumstances circumstances) {
        reach.requireWitness(session);
        return acts.completeWitnessed(session, startToken, answer, pictures, circumstances);
    }
}
