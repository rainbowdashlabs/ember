/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.FieldStatements;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.service.pdf.SignatureFields;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.OpenSignature;
import dev.chojo.ember.feature.signing.entity.PendingSignature;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureAsk;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignatureRequestView;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SigningStatements;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Asks for signatures on generated documents, and answers who is asked to sign what.
 *
 * <p>A request binds to the generated file exactly as it was filed: its SHA-256 is the generation log's,
 * and the filed bytes have to still be those. Its fields are the empty signature fields the file carries,
 * each resolved to who must sign it ({@link SignerResolver}). A document is never edited once signatures
 * are asked for; a correction is a new generated document with a new request that supersedes the old one,
 * whose signatures and evidence stay.
 *
 * <p>Signatures are asked for on purpose, never by generating alone: a manager looks at who would be asked
 * ({@link #requireOwnedAsk}) and then asks ({@link #request}), right after generating or later from the list of
 * generated documents. What each field confirms comes from the template ({@link DocumentStatements}) and is
 * copied into the request with the template's retention period and whether a signer's copy carries the
 * PDF. A later change of the template leaves a request as it was made; only a new document asks anew.
 *
 * <p>The copies of the documents an appointment asks for are the exception: registering for the
 * appointment asks for their signatures ({@link #requestForAppointment}), and no longer taking part
 * withdraws what is still open ({@link #withdrawForAppointment}). So are the documents a partner station's
 * appointment asks a member here to sign ({@link #requestForPartner}). However a participant's copy is asked
 * for, by the appointment or by a manager on a copy generated for it, one document of a date never stands
 * asked twice for the same participant.
 *
 * <p>Everybody a new request asks is told so ({@link SignatureNotices}), and the requests for fields that
 * were withdrawn are taken back.
 */
@Singleton
public class SignatureRequestService {
    private static final Logger log = LoggerFactory.getLogger(SignatureRequestService.class);

    private final SignatureRequestRepository requests;
    private final SigningEvidenceRepository evidence;
    private final DocumentGenerationRepository generations;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final StationMemberRepository members;
    private final MemberNameResolver names;
    private final GuardianPolicy guardianPolicy;
    private final SignerResolver signers;
    private final SigningGuards guards;
    private final SignatureNotices notices;
    private final DocumentStatements statements;

    @Inject
    public SignatureRequestService(
            SignatureRequestRepository requests,
            SigningEvidenceRepository evidence,
            DocumentGenerationRepository generations,
            DocumentRepository documents,
            DocumentService documentService,
            StationMemberRepository members,
            MemberNameResolver names,
            GuardianPolicy guardianPolicy,
            SignerResolver signers,
            SigningGuards guards,
            SignatureNotices notices,
            DocumentStatements statements) {
        this.requests = requests;
        this.evidence = evidence;
        this.generations = generations;
        this.documents = documents;
        this.documentService = documentService;
        this.members = members;
        this.names = names;
        this.guardianPolicy = guardianPolicy;
        this.signers = signers;
        this.guards = guards;
        this.notices = notices;
        this.statements = statements;
    }

    /**
     * What asking for the signatures of a generated document would ask, before anybody is asked: each field
     * with who signs it and what they confirm. Where signatures were already asked for, the request and
     * its fields as they stand.
     *
     * @param session      who would ask, who has to be allowed to change the document
     * @param generationId the generation log entry of the document
     * @return the fields, none where the document carries no field left to sign
     */
    public SignatureAsk requireOwnedAsk(StationSession session, int generationId) {
        var generation = generationAt(session, generationId);
        var live = requests.findLiveFor(generation.id()).orElse(null);
        if (live != null) {
            guards.requireMayManage(session, live.documentId());
            return SignatureAsk.asked(live, requests.fieldsOf(live.id()));
        }
        return SignatureAsk.notYet(plan(session, generation).fields());
    }

    /**
     * Asks for the signatures a generated document's signature fields call for. Each field confirms the
     * statement its template words for it, else the default of its signer, copied into the request as it
     * stands now.
     *
     * @param session      who asks, who has to be allowed to change the document
     * @param generationId the generation log entry of the document
     * @return the request
     */
    public SignatureRequest request(StationSession session, int generationId) {
        var generation = generationAt(session, generationId);
        var created = onceForTheDocument(() -> create(session, generation, null));
        notices.asked(created, requests.fieldsOf(created.id()));
        return created;
    }

    /**
     * Asks for the signatures a generated document's fields call for, as {@link #request} does, and answers
     * the request with its fields as {@link #requireOwnedAsk} would. The document has to be the station's.
     *
     * @param session      who asks, who has to be allowed to change the document
     * @param generationId the generation log entry of the document
     * @return the request and its fields
     */
    public SignatureAsk requireOwnedThenRequest(StationSession session, int generationId) {
        var created = request(session, generationId);
        return SignatureAsk.asked(created, requests.fieldsOf(created.id()));
    }

    /**
     * Replaces a request by one for the corrected document, generated anew for the same member. The open
     * fields of the old request are withdrawn; what was signed on it, and the evidence, stays.
     *
     * @param session      who corrects, who has to be allowed to change both documents
     * @param requestUid   the request to replace
     * @param generationId the generation log entry of the corrected document
     * @return the request for the corrected document
     */
    public SignatureRequest rectify(StationSession session, UUID requestUid, int generationId) {
        var old = requestAt(session, requestUid);
        guards.requireMayManage(session, old.documentId());
        if (old.state() == RequestState.SUPERSEDED || old.state() == RequestState.WITHDRAWN) {
            throw DocumentRefusal.SIGNING_REQUEST_ENDED.raise();
        }
        var generation = generationAt(session, generationId);
        if (!Objects.equals(generation.memberId(), old.memberId())) {
            throw DocumentRefusal.SIGNING_CORRECTION_OTHER_MEMBER.raise();
        }
        int by = session.member().id();
        var created = onceForTheDocument(() -> {
            var held = requests.lockRequest(old.id()).orElseThrow(DocumentRefusal.SIGNING_REQUEST_NOT_FOUND::raise);
            if (held.state() == RequestState.SUPERSEDED || held.state() == RequestState.WITHDRAWN) {
                throw DocumentRefusal.SIGNING_REQUEST_ENDED.raise();
            }
            var replacement = create(session, generation, old.id());
            requests.withdrawOpen(old.id(), by, names.official(by));
            requests.supersede(old.id(), replacement.id());
            return replacement;
        });
        log.info("Signing request {} superseded by {} at station {}", old.uid(), created.uid(), session.stationId());
        notices.settled(withdrawnFieldsOf(old.id()));
        notices.asked(created, requests.fieldsOf(created.id()));
        return created;
    }

    /**
     * Withdraws every signature a request still waits for. What was already signed stays, and the request
     * counts as complete where anything was.
     *
     * @param session    who withdraws, who has to be allowed to change the document
     * @param requestUid the request
     * @return the request as it now stands
     */
    public SignatureRequest withdraw(StationSession session, UUID requestUid) {
        var request = requestAt(session, requestUid);
        guards.requireMayManage(session, request.documentId());
        if (!request.open()) throw DocumentRefusal.SIGNING_REQUEST_NOT_OPEN.raise();
        int by = session.member().id();
        Transactions.run(() -> {
            var held = requests.lockRequest(request.id()).orElseThrow(DocumentRefusal.SIGNING_REQUEST_NOT_FOUND::raise);
            if (!held.open()) throw DocumentRefusal.SIGNING_REQUEST_NOT_OPEN.raise();
            requests.withdrawOpen(request.id(), by, names.official(by));
            requests.closeIfSettled(request.id());
        });
        notices.settled(withdrawnFieldsOf(request.id()));
        return requests.findById(request.id()).orElse(request);
    }

    /**
     * Asks for the signatures of a participant's copy of a document an appointment asks for, generated as
     * they registered. Nobody in particular asks: the appointment does, so the request names nobody who
     * asked and nobody is told who signed. The caller has made sure the copy is the participant's for an
     * appointment they take part in, so no right to change member documents is asked for. Where the signed
     * scan of the copy already waits for a manager, nobody is told yet: the fields are asked for once the
     * scan is turned down.
     *
     * @param generationId the generation log entry of the copy
     * @return the request
     */
    public SignatureRequest requestForAppointment(int generationId) {
        var generation =
                generations.findById(generationId).orElseThrow(DocumentRefusal.SIGNING_GENERATION_NOT_FOUND::raise);
        var created = Transactions.call(() -> {
            var plan = planOf(generation, memberOf(generation), filedDocument(generation));
            return create(generation, plan, null, null);
        });
        if (requests.waitsForScan(created.id())) return created;
        notices.asked(created, requests.fieldsOf(created.id()));
        return created;
    }

    /**
     * Withdraws every signature still open on a participant's copies for one date of an appointment, once
     * they no longer take part. What was already signed stays, and their reminders stop with the fields.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @param memberId  the participant
     * @return how many requests were withdrawn
     */
    public int withdrawForAppointment(int eventId, LocalDate eventDate, int memberId) {
        int withdrawn = 0;
        for (var open : requests.openForAppointment(eventId, eventDate, memberId)) {
            if (!withdrawUnasked(open.request().id())) continue;
            withdrawn++;
            log.info(
                    "Signing request {} withdrawn: member {} no longer takes part in appointment {} on {}",
                    open.request().uid(),
                    memberId,
                    eventId,
                    eventDate);
        }
        return withdrawn;
    }

    /**
     * Withdraws every signature a request still waits for, where nobody in particular withdraws it: the
     * member no longer takes part in what asked for it. What was signed stays, and the reminders stop.
     *
     * @param requestId the request
     * @return whether it was open and is withdrawn now
     */
    public boolean withdrawUnasked(int requestId) {
        boolean changed = Transactions.call(() -> {
            var held = requests.lockRequest(requestId).orElse(null);
            if (held == null || !held.open()) return false;
            requests.withdrawOpen(requestId, null, null);
            requests.closeIfSettled(requestId);
            return true;
        });
        if (changed) notices.settled(withdrawnFieldsOf(requestId));
        return changed;
    }

    /**
     * Asks for the signatures of a document a partner station's appointment asks a member here to sign, as
     * the partner handed it out and as it was filed in the member's documents. The signers are the member and
     * their guardians here, resolved like those of any document; what each confirms is what the partner's
     * template words, else the default of the signer in the document's language. Nobody here asks, so the
     * request names nobody who asked. The caller has made sure the member takes part.
     *
     * @param ask       the document, its member and what the partner said about it
     * @param alongside what the caller writes about the request in the same transaction
     * @return the request
     */
    public SignatureRequest requestForPartner(PartnerAsk ask, Consumer<SignatureRequest> alongside) {
        var member = members.findById(ask.memberId())
                .filter(found -> !found.former())
                .orElseThrow(DocumentRefusal.SIGNING_MEMBER_GONE::raise);
        String memberName = names.official(member.id());
        var worded = SigningStatements.of(ask.statements(), memberName);
        var fields = signers.resolve(member, null, SignatureFields.unsigned(ask.content()), worded);
        if (fields.isEmpty()) throw DocumentRefusal.SIGNING_NO_FIELDS.raise();
        var draft = new SignatureRequest.PartnerDraft(
                ask.stationId(),
                ask.documentId(),
                member.id(),
                memberName,
                Sha256.hex(ask.content()),
                ask.retentionMonths(),
                ask.copyAttached());
        var created = Transactions.call(() -> {
            var request = requests.createForPartner(draft, fields);
            alongside.accept(request);
            return request;
        });
        log.info(
                "Asked for {} signatures on document {} at station {} for a partner's appointment ({})",
                fields.size(),
                ask.documentId(),
                ask.stationId(),
                created.uid());
        notices.asked(created, requests.fieldsOf(created.id()));
        return created;
    }

    /**
     * A document a partner station handed out for its appointment, filed in a member's documents here.
     *
     * <p>The array is handed over as it is, without a copy.
     *
     * @param stationId       the station of the member
     * @param documentId      the member document it was filed as
     * @param memberId        the member asked to sign
     * @param content         the file exactly as it was handed out
     * @param statements      what the partner's template words for its fields, in the document's language
     * @param retentionMonths how long the partner's template keeps signed documents, or null
     * @param copyAttached    whether a signer's copy by mail may carry the sealed PDF
     */
    public record PartnerAsk(
            int stationId,
            int documentId,
            int memberId,
            byte[] content,
            FieldStatements statements,
            @Nullable Integer retentionMonths,
            boolean copyAttached) {}

    private List<Integer> withdrawnFieldsOf(int requestId) {
        return requests.fieldsOf(requestId).stream()
                .filter(field -> field.state() == FieldState.WITHDRAWN)
                .map(RequestedSignature::id)
                .toList();
    }

    /**
     * A request with its fields and its evidence, for a reader who may read its document.
     *
     * @param session    the reader
     * @param requestUid the request
     * @return the request as it stands
     */
    public SignatureRequestView view(StationSession session, UUID requestUid) {
        var request = requestAt(session, requestUid);
        guards.requireReadable(session, request);
        return new SignatureRequestView(request, requests.fieldsOf(request.id()), evidence.evidenceOf(request.id()));
    }

    /**
     * The open signature fields a member is asked to sign: their own, and those they sign on behalf of or
     * lend their account to the members in their care.
     *
     * @param session the member
     * @return each field with how they would sign it
     */
    public List<OpenSignature> openFor(StationSession session) {
        int me = session.member().id();
        return pendingFor(session.stationId(), me).stream()
                .map(pending -> new OpenSignature(pending, signerFor(pending, me, session.accountId())))
                .toList();
    }

    /**
     * The same open signature fields as {@link #openFor}, for a caller that knows the member but holds no
     * session of theirs, such as the list of what a member still owes.
     *
     * @param stationId the station
     * @param memberId  the member
     * @return the fields they sign or lend their account to, oldest request first
     */
    public List<PendingSignature> pendingFor(int stationId, int memberId) {
        return requests.pendingFor(stationId, memberId, wardIdsOf(memberId));
    }

    private List<Integer> wardIdsOf(int memberId) {
        return guardianPolicy.wardsOf(memberId).stream().map(StationMember::id).toList();
    }

    /**
     * @param session    the reader
     * @param requestUid the request
     * @return the request, which belongs to the reader's station
     */
    SignatureRequest requestAt(StationSession session, UUID requestUid) {
        return requests.findByUid(requestUid)
                .filter(request -> request.stationId() == session.stationId())
                .orElseThrow(DocumentRefusal.SIGNING_REQUEST_NOT_FOUND::raise);
    }

    /**
     * Writes a new request in one transaction. The check that the document has no live request yet cannot
     * see one that another manager, or a correction, is writing at the same moment; the database refuses the
     * later of the two, and that is answered as the check would have answered it.
     */
    private static SignatureRequest onceForTheDocument(Supplier<SignatureRequest> creation) {
        try {
            return Transactions.call(creation);
        } catch (RuntimeException e) {
            if (SignatureRequestRepository.isSecondLiveRequest(e)) {
                throw DocumentRefusal.SIGNING_ALREADY_REQUESTED.raise();
            }
            throw e;
        }
    }

    /**
     * Whether a field at the session's station asks its member, whatever state it is in.
     *
     * @param session the member
     * @param fieldId the field
     * @return whether it is a field of theirs, to sign themselves or for a member in their care
     */
    boolean asks(StationSession session, int fieldId) {
        int me = session.member().id();
        return requests.asks(session.stationId(), me, wardIdsOf(me), fieldId);
    }

    private SignatureRequest create(
            StationSession session, DocumentGeneration generation, @Nullable Integer replacing) {
        return create(generation, plan(session, generation), session.member().id(), replacing);
    }

    private SignatureRequest create(
            DocumentGeneration generation, Plan plan, @Nullable Integer askedBy, @Nullable Integer replacing) {
        requireNothingLive(generation, replacing);
        if (plan.fields().isEmpty()) throw DocumentRefusal.SIGNING_NO_FIELDS.raise();
        var created = requests.create(
                new SignatureRequest.Draft(
                        generation.stationId(),
                        generation.id(),
                        generation.templateId(),
                        plan.documentId(),
                        plan.memberId(),
                        plan.memberName(),
                        plan.sha256(),
                        askedBy),
                plan.fields());
        log.info(
                "Asked for {} signatures on document {} at station {} ({})",
                plan.fields().size(),
                plan.documentId(),
                generation.stationId(),
                created.uid());
        return created;
    }

    /**
     * Refuses a second live request: on the generated document itself, and, for a participant's copy of a
     * document an appointment asks for, on any other copy of it for the same participant and date. Asking
     * from the appointment and asking on a generated copy so never leave a participant asked twice for one
     * document of a date. The request a correction replaces does not count.
     */
    private void requireNothingLive(DocumentGeneration generation, @Nullable Integer replacing) {
        if (requests.liveFor(generation.id()) || requests.liveForRequirement(generation.id(), replacing)) {
            throw DocumentRefusal.SIGNING_ALREADY_REQUESTED.raise();
        }
    }

    /**
     * Who a generated document asks to sign, and what each confirms, read from the file as it was filed.
     *
     * @param documentId the member document the file was filed as
     * @param memberId   the member it is about
     * @param memberName their official name
     * @param sha256     the SHA-256 of the file, the generation log's
     * @param fields     the fields to ask for, in the order the file carries them
     */
    private record Plan(
            int documentId, int memberId, String memberName, String sha256, List<RequestedSignature.Draft> fields) {}

    private Plan plan(StationSession session, DocumentGeneration generation) {
        var member = memberOf(generation);
        var document = filedDocument(generation);
        guards.requireMayManage(session, document.id());
        return planOf(generation, member, document);
    }

    private Document filedDocument(DocumentGeneration generation) {
        Integer documentId = generation.documentId();
        var document =
                documentId == null ? null : documents.findById(documentId).orElse(null);
        if (document == null) throw DocumentRefusal.SIGNING_DOCUMENT_NOT_FILED.raise();
        return document;
    }

    private Plan planOf(DocumentGeneration generation, StationMember member, Document document) {
        byte[] content = documentService.read(document).orElseThrow(DocumentRefusal.SIGNING_DOCUMENT_NOT_FILED::raise);
        String sha256 = Sha256.hex(content);
        if (!sha256.equals(generation.fileSha256())) throw DocumentRefusal.SIGNING_DOCUMENT_CHANGED.raise();
        String memberName = names.official(member.id());
        var worded = statements.of(generation.templateId(), member.id(), memberName);
        var fields = signers.resolve(member, generation.issuerId(), SignatureFields.unsigned(content), worded);
        return new Plan(document.id(), member.id(), memberName, sha256, fields);
    }

    private DocumentGeneration generationAt(StationSession session, int generationId) {
        return generations
                .findById(generationId)
                .filter(generation -> generation.stationId() == session.stationId())
                .orElseThrow(DocumentRefusal.SIGNING_GENERATION_NOT_FOUND::raise);
    }

    private StationMember memberOf(DocumentGeneration generation) {
        Integer memberId = generation.memberId();
        if (memberId == null) throw DocumentRefusal.SIGNING_MEMBER_GONE.raise();
        return members.findById(memberId)
                .filter(member -> !member.former())
                .orElseThrow(DocumentRefusal.SIGNING_MEMBER_GONE::raise);
    }

    private static Signer signerFor(PendingSignature pending, int me, int accountId) {
        var field = pending.field();
        return switch (field.role()) {
            case PARTICIPANT ->
                Objects.equals(field.signerId(), me)
                        ? Signer.accountHolder(accountId)
                        : Signer.memberThroughAccount(
                                accountId, Objects.requireNonNull(field.signerId(), "a ward's field names the ward"));
            case ISSUER -> Signer.accountHolder(accountId);
            case GUARDIAN, ANY_GUARDIAN ->
                Signer.guardian(
                        accountId, Objects.requireNonNull(field.memberId(), "a ward's document names the ward"));
        };
    }
}
