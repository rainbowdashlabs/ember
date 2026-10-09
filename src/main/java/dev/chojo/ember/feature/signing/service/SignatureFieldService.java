/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.signing.entity.CompletedSigning;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.GuardianLink;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignedAct;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Settles the signature fields of a request: by a signing act, whose evidence is stored with it, or by a
 * manager who confirms a paper signature, waives the field or withdraws it.
 *
 * <p>A field is settled once. Its row is held while it is settled, so two acts on one field cannot both
 * find it open, and the request is closed in the same transaction once no field is open any more.
 *
 * <p>A signing act is checked against the field before its evidence is stored: the content and the
 * statement must be the ones asked for, the account must be the caller's, and the field must be the
 * caller's to sign in the capacity the act names. The member signs their own field with their own account
 * or through the account of a guardian; a guardian field is signed by the guardian at that place, a field
 * for any guardian by any of them, the issuer's by the issuer. Where somebody acts as or through a guardian,
 * the guardian link as it stands is copied into the evidence, since it is the evidence of guardianship.
 *
 * <p>Once a field is settled, the unread requests and reminders for it are taken back, and a signature is
 * told to whoever asked for it ({@link SignatureNotices}). A field a manager settles is sealed into the
 * document by {@link SigningStateSweeper}. A request completed by the field it settled is handed on to
 * {@link AgreementOutcomes}.
 */
@Singleton
public class SignatureFieldService {
    private static final Logger log = LoggerFactory.getLogger(SignatureFieldService.class);

    private final SignatureRequestRepository requests;
    private final SigningEvidenceRepository evidence;
    private final SignatureRequestService requestService;
    private final SigningGuards guards;
    private final GuardianPolicy guardianPolicy;
    private final MemberNameResolver names;
    private final SignatureNotices notices;
    private final AgreementOutcomes outcomes;

    @Inject
    public SignatureFieldService(
            SignatureRequestRepository requests,
            SigningEvidenceRepository evidence,
            SignatureRequestService requestService,
            SigningGuards guards,
            GuardianPolicy guardianPolicy,
            MemberNameResolver names,
            SignatureNotices notices,
            AgreementOutcomes outcomes) {
        this.requests = requests;
        this.evidence = evidence;
        this.requestService = requestService;
        this.guards = guards;
        this.guardianPolicy = guardianPolicy;
        this.names = names;
        this.notices = notices;
        this.outcomes = outcomes;
    }

    /**
     * Stores the evidence of a signing act against the field it filled, and closes the request once no
     * field is open any more. The evidence is stored unsealed; {@link SigningStateSealer} seals it into the
     * request's document afterwards.
     *
     * @param session the signer, whose account confirmed the act
     * @param signed  what the provider handed back
     * @return the evidence as stored
     */
    public StoredEvidence record(StationSession session, CompletedSigning signed) {
        return recordAll(session, List.of(new SignedAct(signed, null))).getFirst();
    }

    /** Tells {@link AgreementOutcomes} of a request that completed, logging rather than throwing. */
    private void tellIfCompleted(int requestId) {
        var now = requests.findById(requestId).orElse(null);
        if (now == null || now.state() != RequestState.COMPLETE) return;
        try {
            outcomes.completed(now);
        } catch (RuntimeException e) {
            log.warn("Could not carry the completion of signing request {} over", now.uid(), e);
        }
    }

    /**
     * Stores the evidence of the acts one confirmation gave, each against the field it filled, with the
     * signature picture it left there, and closes each request once no field of it is open any more.
     *
     * <p>All of them or none: a field that is no longer open, or no longer the caller's, refuses every act of
     * the confirmation, so a confirmation never leaves some of the fields it covered signed and others not.
     * The notices go out once everything is stored.
     *
     * @param session the signer, whose account confirmed the acts
     * @param acts    what the provider handed back for each field, with its picture
     * @return the evidence as stored, in the order of the acts
     */
    public List<StoredEvidence> recordAll(StationSession session, List<SignedAct> acts) {
        for (var act : acts) {
            if (act.signing().evidence().act().signer().accountId() != session.accountId()) {
                throw DocumentRefusal.SIGNING_FIELD_NOT_YOURS.raise();
            }
        }
        var recorded = Transactions.call(
                () -> acts.stream().map(act -> recordOne(session, act)).toList());
        for (var each : recorded) {
            log.info(
                    "Field {} of signing request {} signed by member {} with {}",
                    each.stored().evidence().act().fieldName(),
                    each.request().uid(),
                    session.member().id(),
                    each.stored().evidence().proof());
        }
        notices.settled(recorded.stream().map(each -> each.stored().fieldId()).toList());
        recorded.forEach(each -> notices.signed(each.request(), each.stored()));
        recorded.stream().map(each -> each.request().id()).distinct().forEach(this::tellIfCompleted);
        return recorded.stream().map(Recorded::stored).toList();
    }

    private Recorded recordOne(StationSession session, SignedAct signedAct) {
        CompletedSigning signed = signedAct.signing();
        var act = signed.evidence().act();
        var request = requestService.requestAt(session, act.requestUid());
        var field = openField(request, act.fieldName());
        boolean sameContent = HexFormat.of().formatHex(act.contentSha256()).equals(request.contentSha256());
        if (!sameContent || !act.statement().equals(field.statement())) {
            throw DocumentRefusal.SIGNING_CONTENT_DIFFERS.raise();
        }
        @Nullable GuardianLink link = linkFor(session, request, field, act.signer());
        int me = session.member().id();
        requests.settle(field.id(), FieldState.SIGNED, me, act.accountHolderName());
        var stored = evidence.record(
                field.id(), signed.level(), signed.evidence(), me, act.signer().memberId(), link);
        var picture = signedAct.picture();
        if (picture != null) evidence.storeMark(stored.id(), picture);
        requests.closeIfSettled(request.id());
        return new Recorded(request, stored);
    }

    /** One act as stored, with the request it was on. */
    private record Recorded(SignatureRequest request, StoredEvidence stored) {}

    /**
     * Records that a field was signed on paper.
     *
     * @param session    the manager confirming it
     * @param requestUid the request
     * @param fieldName  the field
     * @return the field as it now stands
     */
    public RequestedSignature confirmOnPaper(StationSession session, UUID requestUid, String fieldName) {
        return settle(session, requestUid, fieldName, FieldState.PAPER_CONFIRMED);
    }

    /**
     * Lets a field go without a signature.
     *
     * @param session    the manager waiving it
     * @param requestUid the request
     * @param fieldName  the field
     * @return the field as it now stands
     */
    public RequestedSignature waive(StationSession session, UUID requestUid, String fieldName) {
        return settle(session, requestUid, fieldName, FieldState.WAIVED);
    }

    /**
     * Stops asking for a field's signature.
     *
     * @param session    the manager withdrawing it
     * @param requestUid the request
     * @param fieldName  the field
     * @return the field as it now stands
     */
    public RequestedSignature withdraw(StationSession session, UUID requestUid, String fieldName) {
        return settle(session, requestUid, fieldName, FieldState.WITHDRAWN);
    }

    /**
     * Records that a field of a participant's copy of a document an appointment asks for was signed on
     * paper, because a manager of the registrations confirmed the scan of the signed copy. Confirming the
     * scan is the right this rests on, so the right to change member documents is not asked for.
     *
     * @param session    the manager of the registrations who confirmed the scan
     * @param requestUid the request on the copy
     * @param fieldName  the field
     * @return the field as it now stands
     */
    public RequestedSignature confirmScanOnPaper(StationSession session, UUID requestUid, String fieldName) {
        return settleChecked(
                session, requestService.requestAt(session, requestUid), fieldName, FieldState.PAPER_CONFIRMED);
    }

    private RequestedSignature settle(StationSession session, UUID requestUid, String fieldName, FieldState state) {
        var request = requestService.requestAt(session, requestUid);
        guards.requireMayManage(session, request.documentId());
        return settleChecked(session, request, fieldName, state);
    }

    private RequestedSignature settleChecked(
            StationSession session, SignatureRequest request, String fieldName, FieldState state) {
        int me = session.member().id();
        var settled = Transactions.call(() -> {
            var field = openField(request, fieldName);
            requests.settle(field.id(), state, me, names.official(me));
            requests.closeIfSettled(request.id());
            return requests.fieldsOf(request.id()).stream()
                    .filter(candidate -> candidate.id() == field.id())
                    .findFirst()
                    .orElse(field);
        });
        notices.settled(List.of(settled.id()));
        tellIfCompleted(request.id());
        return settled;
    }

    /** Holds the field and its request, and refuses a field that no longer waits for a signature. */
    private RequestedSignature openField(SignatureRequest request, String fieldName) {
        var field =
                requests.lockField(request.id(), fieldName).orElseThrow(DocumentRefusal.SIGNING_FIELD_NOT_FOUND::raise);
        boolean requestOpen =
                requests.findById(request.id()).map(SignatureRequest::open).orElse(false);
        if (!requestOpen || field.state() != FieldState.OPEN) throw DocumentRefusal.SIGNING_FIELD_NOT_OPEN.raise();
        return field;
    }

    /**
     * Refuses an act on a field that is not the signer's to sign in the capacity the act names.
     *
     * @return the guardian link the act goes through, or null where the signer signs for themselves
     */
    private @Nullable GuardianLink linkFor(
            StationSession session, SignatureRequest request, RequestedSignature field, Signer signer) {
        int me = session.member().id();
        Integer member = request.memberId();
        Integer signerId = field.signerId();
        SignerCapacity capacity = signer.capacity();
        boolean mine = signerId != null && signerId == me;
        switch (field.role()) {
            case PARTICIPANT -> {
                if (capacity == SignerCapacity.ACCOUNT_HOLDER && mine) return null;
                if (capacity == SignerCapacity.MEMBER_THROUGH_ACCOUNT
                        && signerId != null
                        && signerId.equals(signer.memberId())) {
                    return guardianLink(session, signerId);
                }
            }
            case GUARDIAN -> {
                if (capacity == SignerCapacity.GUARDIAN && mine && member != null && member.equals(signer.memberId())) {
                    return guardianLink(session, member);
                }
            }
            case ANY_GUARDIAN -> {
                if (capacity == SignerCapacity.GUARDIAN && member != null && member.equals(signer.memberId())) {
                    return guardianLink(session, member);
                }
            }
            case ISSUER -> {
                if (capacity == SignerCapacity.ACCOUNT_HOLDER && mine) return null;
            }
        }
        throw DocumentRefusal.SIGNING_FIELD_NOT_YOURS.raise();
    }

    /** The link of the signer as guardian of the member, refusing a signer who is not their guardian now. */
    private GuardianLink guardianLink(StationSession session, int memberId) {
        int me = session.member().id();
        if (memberId == me || !guardianPolicy.mayActFor(session.user(), memberId)) {
            throw DocumentRefusal.SIGNING_FIELD_NOT_YOURS.raise();
        }
        var stored = requests.guardianLink(me, memberId).orElseThrow(DocumentRefusal.SIGNING_FIELD_NOT_YOURS::raise);
        Integer linkedBy = stored.linkedBy();
        String linkedByName = linkedBy == null || !names.parts(linkedBy).known() ? null : names.official(linkedBy);
        return new GuardianLink(stored.position(), stored.linkedAt(), linkedByName);
    }
}
