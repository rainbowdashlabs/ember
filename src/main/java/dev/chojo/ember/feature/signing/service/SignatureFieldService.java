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
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
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

    @Inject
    public SignatureFieldService(
            SignatureRequestRepository requests,
            SigningEvidenceRepository evidence,
            SignatureRequestService requestService,
            SigningGuards guards,
            GuardianPolicy guardianPolicy,
            MemberNameResolver names) {
        this.requests = requests;
        this.evidence = evidence;
        this.requestService = requestService;
        this.guards = guards;
        this.guardianPolicy = guardianPolicy;
        this.names = names;
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
        var act = signed.evidence().act();
        if (act.signer().accountId() != session.accountId()) throw DocumentRefusal.SIGNING_FIELD_NOT_YOURS.raise();
        var request = requestService.requestAt(session, act.requestUid());
        var stored = Transactions.call(() -> {
            var field = openField(request, act.fieldName());
            boolean sameContent = HexFormat.of().formatHex(act.contentSha256()).equals(request.contentSha256());
            if (!sameContent || !act.statement().equals(field.statement())) {
                throw DocumentRefusal.SIGNING_CONTENT_DIFFERS.raise();
            }
            @Nullable GuardianLink link = linkFor(session, request, field, act.signer());
            int me = session.member().id();
            requests.settle(field.id(), FieldState.SIGNED, me, act.accountHolderName());
            var recorded = evidence.record(
                    field.id(),
                    signed.level(),
                    signed.evidence(),
                    me,
                    act.signer().memberId(),
                    link);
            requests.closeIfSettled(request.id());
            return recorded;
        });
        log.info(
                "Field {} of signing request {} signed by member {} with {}",
                act.fieldName(),
                request.uid(),
                session.member().id(),
                signed.evidence().proof());
        return stored;
    }

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

    private RequestedSignature settle(StationSession session, UUID requestUid, String fieldName, FieldState state) {
        var request = requestService.requestAt(session, requestUid);
        guards.requireMayManage(session, request.documentId());
        int me = session.member().id();
        return Transactions.call(() -> {
            var field = openField(request, fieldName);
            requests.settle(field.id(), state, me, names.official(me));
            requests.closeIfSettled(request.id());
            return requests.fieldsOf(request.id()).stream()
                    .filter(settled -> settled.id() == field.id())
                    .findFirst()
                    .orElse(field);
        });
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
