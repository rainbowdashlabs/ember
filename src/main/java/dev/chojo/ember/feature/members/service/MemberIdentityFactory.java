/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

/**
 * Factory for creating MemberIdentity instances with display metadata (name color, tag badge).
 * All identities created through this factory are automatically enriched.
 */
@Singleton
public class MemberIdentityFactory {
    private final StationRepository stationRepository;
    private final MemberLookupService lookupService;
    private final MemberNameResolver nameResolver;

    @Inject
    public MemberIdentityFactory(
            StationRepository stationRepository, MemberLookupService lookupService, MemberNameResolver nameResolver) {
        this.stationRepository = stationRepository;
        this.lookupService = lookupService;
        this.nameResolver = nameResolver;
    }

    /**
     * Creates an enriched MemberIdentity for a local member.
     */
    public MemberIdentity local(int stationId, int memberId) {
        UUID stationUid = stationRepository.resolveUid(stationId);
        UUID memberUid = lookupService.resolveUid(memberId);
        if (stationUid == null || memberUid == null) {
            throw new IllegalStateException(
                    "Cannot resolve MemberIdentity for stationId=%d, memberId=%d (stationUid=%s, memberUid=%s)"
                            .formatted(stationId, memberId, stationUid, memberUid));
        }
        return nameResolver.enrichDisplay(new MemberIdentity(stationUid, memberUid));
    }

    /**
     * Creates a MemberIdentity for a federated (remote) member.
     * Display metadata is not enriched (group colors are not federated).
     */
    public MemberIdentity federated(UUID stationUid, UUID memberUid) {
        return new MemberIdentity(stationUid, memberUid);
    }

    /**
     * Resolves a member ID to an enriched MemberIdentity.
     */
    public MemberIdentity fromMemberId(int memberId) {
        var identity = lookupService.resolveIdentity(memberId);
        return identity != null ? nameResolver.enrichDisplay(identity) : null;
    }

    /**
     * Enriches a DB-loaded MemberIdentity with display metadata.
     * Use this for identities read from entity RowMappings before returning in API responses.
     */
    public MemberIdentity enrich(MemberIdentity identity) {
        return nameResolver.enrichDisplay(identity);
    }

    /**
     * Enriches a list of MemberCompletions with display metadata (nameColor, displayTag, stationName).
     */
    public List<MemberCompletion> enrichCompletions(List<MemberCompletion> completions) {
        return completions.stream()
                .map(c -> {
                    var enriched = nameResolver.enrichDisplay(new MemberIdentity(c.stationUid(), c.memberUid()));
                    return new MemberCompletion(
                            c.id(),
                            c.name(),
                            c.stationUid(),
                            c.memberUid(),
                            enriched != null ? enriched.stationName() : null,
                            enriched != null ? enriched.nameColor() : null,
                            enriched != null ? enriched.displayTag() : null);
                })
                .toList();
    }
}
