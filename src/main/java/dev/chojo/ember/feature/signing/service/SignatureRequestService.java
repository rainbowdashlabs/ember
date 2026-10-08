/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
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
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
        var created = onceForTheDocument(() -> create(session, generation));
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
            var replacement = create(session, generation);
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

    private SignatureRequest create(StationSession session, DocumentGeneration generation) {
        var plan = plan(session, generation);
        if (requests.liveFor(generation.id())) throw DocumentRefusal.SIGNING_ALREADY_REQUESTED.raise();
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
                        session.member().id()),
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
        Integer documentId = generation.documentId();
        var document =
                documentId == null ? null : documents.findById(documentId).orElse(null);
        if (document == null) throw DocumentRefusal.SIGNING_DOCUMENT_NOT_FILED.raise();
        guards.requireMayManage(session, document.id());
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
