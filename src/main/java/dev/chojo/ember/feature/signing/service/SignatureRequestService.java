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
import dev.chojo.ember.feature.signing.entity.OpenSignature;
import dev.chojo.ember.feature.signing.entity.PendingSignature;
import dev.chojo.ember.feature.signing.entity.RequestState;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Asks for signatures on generated documents, and answers who is asked to sign what.
 *
 * <p>A request binds to the generated file exactly as it was filed: its SHA-256 is the generation log's,
 * and the filed bytes have to still be those. Its fields are the empty signature fields the file carries,
 * each resolved to who must sign it ({@link SignerResolver}). A document is never edited once signatures
 * are asked for; a correction is a new generated document with a new request that supersedes the old one,
 * whose signatures and evidence stay.
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
            SigningGuards guards) {
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
    }

    /**
     * Asks for the signatures a generated document's signature fields call for.
     *
     * @param session      who asks, who has to be allowed to change the document
     * @param generationId the generation log entry of the document
     * @param statements   what each kind of signer confirms
     * @return the request
     */
    public SignatureRequest request(StationSession session, int generationId, SigningStatements statements) {
        var generation = generationAt(session, generationId);
        return Transactions.call(() -> create(session, generation, statements));
    }

    /**
     * Replaces a request by one for the corrected document, generated anew for the same member. The open
     * fields of the old request are withdrawn; what was signed on it, and the evidence, stays.
     *
     * @param session      who corrects, who has to be allowed to change both documents
     * @param requestUid   the request to replace
     * @param generationId the generation log entry of the corrected document
     * @param statements   what each kind of signer confirms
     * @return the request for the corrected document
     */
    public SignatureRequest rectify(
            StationSession session, UUID requestUid, int generationId, SigningStatements statements) {
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
        var created = Transactions.call(() -> {
            var held = requests.lockRequest(old.id()).orElseThrow(DocumentRefusal.SIGNING_REQUEST_NOT_FOUND::raise);
            if (held.state() == RequestState.SUPERSEDED || held.state() == RequestState.WITHDRAWN) {
                throw DocumentRefusal.SIGNING_REQUEST_ENDED.raise();
            }
            var replacement = create(session, generation, statements);
            requests.withdrawOpen(old.id(), by, names.official(by));
            requests.supersede(old.id(), replacement.id());
            return replacement;
        });
        log.info("Signing request {} superseded by {} at station {}", old.uid(), created.uid(), session.stationId());
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
        return requests.findById(request.id()).orElse(request);
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
        var wards =
                guardianPolicy.wardsOf(memberId).stream().map(StationMember::id).toList();
        return requests.pendingFor(stationId, memberId, wards);
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

    private SignatureRequest create(
            StationSession session, DocumentGeneration generation, SigningStatements statements) {
        var member = memberOf(generation);
        Integer documentId = generation.documentId();
        var document =
                documentId == null ? null : documents.findById(documentId).orElse(null);
        if (document == null) throw DocumentRefusal.SIGNING_DOCUMENT_NOT_FILED.raise();
        guards.requireMayManage(session, document.id());
        byte[] content = documentService.read(document).orElseThrow(DocumentRefusal.SIGNING_DOCUMENT_NOT_FILED::raise);
        String sha256 = Sha256.hex(content);
        if (!sha256.equals(generation.fileSha256())) throw DocumentRefusal.SIGNING_DOCUMENT_CHANGED.raise();
        if (requests.liveFor(generation.id())) throw DocumentRefusal.SIGNING_ALREADY_REQUESTED.raise();
        var fields = signers.resolve(member, generation.issuerId(), SignatureFields.unsigned(content), statements);
        if (fields.isEmpty()) throw DocumentRefusal.SIGNING_NO_FIELDS.raise();
        var created = requests.create(
                new SignatureRequest.Draft(
                        generation.stationId(),
                        generation.id(),
                        generation.templateId(),
                        document.id(),
                        member.id(),
                        names.official(member.id()),
                        sha256,
                        session.member().id()),
                fields);
        log.info(
                "Asked for {} signatures on document {} at station {} ({})",
                fields.size(),
                document.id(),
                generation.stationId(),
                created.uid());
        return created;
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
