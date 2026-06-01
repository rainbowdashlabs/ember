/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * Factory for creating MemberIdentity instances using injected services.
 * Replaces the static MemberIdentity.local() factory that relied on singletons.
 */
@Singleton
public class MemberIdentityFactory {
    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;

    @Inject
    public MemberIdentityFactory(StationRepository stationRepository, StationMemberRepository memberRepository) {
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Creates a MemberIdentity for a local member by resolving station and member UUIDs.
     */
    public MemberIdentity local(int stationId, int memberId) {
        UUID stationUid = stationRepository.resolveUid(stationId);
        UUID memberUid = memberRepository.resolveUid(memberId);
        return new MemberIdentity(stationUid, memberUid);
    }

    /**
     * Creates a MemberIdentity for a federated (remote) member from known UUIDs.
     */
    public MemberIdentity federated(UUID stationUid, UUID memberUid) {
        return new MemberIdentity(stationUid, memberUid);
    }

    /**
     * Resolves a member ID directly to a MemberIdentity (looks up both station and member UIDs in one query).
     */
    public MemberIdentity fromMemberId(int memberId) {
        return memberRepository.resolveIdentity(memberId);
    }
}
