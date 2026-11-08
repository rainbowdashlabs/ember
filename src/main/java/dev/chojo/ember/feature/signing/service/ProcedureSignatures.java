/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberPermissionResolver;
import dev.chojo.ember.feature.procedure.service.DocumentStepSigning;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

    @Inject
    public ProcedureSignatures(
            DocumentGenerationService generation,
            SignatureRequestService requests,
            StationMemberRepository members,
            MemberPermissionResolver permissions) {
        this.generation = generation;
        this.requests = requests;
        this.members = members;
        this.permissions = permissions;
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
    public void request(int generationId, int askedBy) {
        requests.requestForProcedure(generationId, askedBy);
    }

    @Override
    public void withdraw(int generationId, int by) {
        requests.withdrawForProcedure(generationId, by);
    }

    private Optional<StationMember> currentAt(int stationId, int memberId) {
        return members.findById(memberId).filter(member -> member.stationId() == stationId && !member.former());
    }
}
