/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberPermissionResolver;
import dev.chojo.ember.feature.procedure.service.DocumentStepSigning;
import dev.chojo.ember.feature.procedure.service.StepDocumentState;
import dev.chojo.ember.feature.procedure.service.StepDocumentState.Progress;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The documents steps of procedures ask to sign: generated for the member a procedure is about and asked
 * for on behalf of a member who generates documents for members.
 */
@Singleton
public class ProcedureSignatures implements DocumentStepSigning {
    private final DocumentGenerationService generation;
    private final SignatureRequestService requests;
    private final StationMemberRepository members;
    private final MemberPermissionResolver permissions;
    private final DocumentGenerationRepository generations;
    private final SignatureRequestRepository requestRepo;
    private final SignatureNotices notices;

    @Inject
    public ProcedureSignatures(
            DocumentGenerationService generation,
            SignatureRequestService requests,
            StationMemberRepository members,
            MemberPermissionResolver permissions,
            DocumentGenerationRepository generations,
            SignatureRequestRepository requestRepo,
            SignatureNotices notices) {
        this.generation = generation;
        this.requests = requests;
        this.members = members;
        this.permissions = permissions;
        this.generations = generations;
        this.requestRepo = requestRepo;
        this.notices = notices;
    }

    @Override
    public boolean mayAsk(int stationId, int memberId) {
        return currentAt(stationId, memberId)
                .map(member -> permissions.resolve(member).contains(StationPermission.DOCUMENT_EDIT_MEMBER))
                .orElse(false);
    }

    @Override
    public int generate(int stationId, int templateId, int memberId, int askedBy) {
        if (currentAt(stationId, memberId).isEmpty()) throw DocumentRefusal.SIGNING_MEMBER_GONE.raise();
        return generation
                .generateForProcedure(stationId, templateId, memberId, askedBy)
                .generationId();
    }

    @Override
    public void request(int generationId, int askedBy, String procedureName) {
        requests.requestForProcedure(generationId, askedBy, procedureName);
    }

    @Override
    public void withdraw(int generationId, int by) {
        requests.withdrawForProcedure(generationId, by);
    }

    @Override
    public int remind(int generationId, Instant askedBefore) {
        var request = requestRepo
                .findLiveFor(generationId)
                .filter(SignatureRequest::open)
                .filter(live -> live.createdAt().isBefore(askedBefore))
                .orElse(null);
        if (request == null) return 0;
        var open = requestRepo.fieldsOf(request.id()).stream()
                .filter(field -> field.state() == FieldState.OPEN)
                .toList();
        if (!open.isEmpty()) notices.reminded(request, open);
        return open.size();
    }

    @Override
    public StepDocumentState stateOf(int itemId, int generationId) {
        Integer documentId = generations
                .findById(generationId)
                .map(DocumentGeneration::documentId)
                .orElse(null);
        var request = requestRepo.findLatestFor(generationId).orElse(null);
        if (request == null) return new StepDocumentState(itemId, documentId, Progress.NOT_ASKED, 0, 0, null);
        var fields = requestRepo.fieldsOf(request.id());
        int settled = (int) fields.stream()
                .filter(field -> field.state() != FieldState.OPEN)
                .count();
        Integer openFieldId = request.open()
                ? fields.stream()
                        .filter(field -> field.state() == FieldState.OPEN && field.role() != FieldRole.ISSUER)
                        .map(RequestedSignature::id)
                        .findFirst()
                        .orElse(null)
                : null;
        return new StepDocumentState(
                itemId, documentId, progressOf(request, fields), settled, fields.size(), openFieldId);
    }

    private static Progress progressOf(SignatureRequest request, List<RequestedSignature> fields) {
        if (request.open()) return Progress.WAITING;
        if (request.state() == RequestState.REVOKED) return Progress.WITHDRAWN;
        boolean agreed = fields.stream()
                .anyMatch(field -> field.state() == FieldState.SIGNED
                        || field.state() == FieldState.PAPER_CONFIRMED
                        || field.state() == FieldState.WAIVED);
        return agreed ? Progress.AGREED : Progress.NOT_AGREED;
    }

    private Optional<StationMember> currentAt(int stationId, int memberId) {
        return members.findById(memberId).filter(member -> member.stationId() == stationId && !member.former());
    }
}
