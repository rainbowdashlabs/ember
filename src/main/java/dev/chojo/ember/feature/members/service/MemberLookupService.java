/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Translates between the internal member ID and the federated member UUID, and owns the caches that
 * keep those translations cheap. Also carries the compositions that need the station table next to
 * the member table, so {@link StationMemberRepository} stays a single-table repository.
 */
@Singleton
public class MemberLookupService {
    private final Cache<Integer, UUID> memberUidCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();
    private final Cache<MemberKey, Integer> memberIdCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();
    private final StationMemberRepository memberRepository;
    private final StationRepository stationRepository;

    @Inject
    public MemberLookupService(StationMemberRepository memberRepository, StationRepository stationRepository) {
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
    }

    /**
     * Resolves an internal member ID to its UUID. Cached.
     */
    public UUID resolveUid(int memberId) {
        return memberUidCache.get(memberId, id -> memberRepository.selectUid(id).orElse(null));
    }

    /**
     * Resolves a member UUID within a station to its internal ID. Cached.
     */
    public Optional<Integer> resolveId(int stationId, UUID memberUid) {
        var key = new MemberKey(stationId, memberUid);
        var cached = memberIdCache.getIfPresent(key);
        if (cached != null) return Optional.of(cached);
        return memberRepository.selectId(stationId, memberUid).map(id -> {
            memberIdCache.put(key, id);
            memberUidCache.put(id, memberUid);
            return id;
        });
    }

    /**
     * Resolves a member ID to a full identity (station UID + member UID).
     */
    public MemberIdentity resolveIdentity(int memberId) {
        return memberRepository.resolveIdentity(memberId);
    }

    /**
     * Resolves a federated identity back to the local member ID it refers to. Empty when either the
     * station or the member is unknown to this instance.
     */
    public Optional<Integer> resolveMemberId(MemberIdentity identity) {
        if (identity == null || identity.stationUid() == null || identity.memberUid() == null) return Optional.empty();
        return stationRepository
                .resolveId(identity.stationUid())
                .flatMap(stationId -> resolveId(stationId, identity.memberUid()));
    }

    /**
     * Active members of a station for autocomplete, carrying the station UUID so the frontend can
     * address them as federated identities.
     */
    public List<MemberCompletion> findCompletions(int stationId) {
        return memberRepository.findCompletions(stationId, stationRepository.resolveUid(stationId));
    }

    /**
     * Pins a member to a fixed UUID and drops the stale cache entries for it.
     */
    public void setUid(int memberId, UUID uid) {
        memberRepository.updateUid(memberId, uid);
        invalidate(memberId);
    }

    /**
     * Drops the cached translations for a member.
     */
    public void invalidate(int memberId) {
        var uid = memberUidCache.getIfPresent(memberId);
        memberUidCache.invalidate(memberId);
        if (uid != null) {
            memberIdCache.asMap().values().remove(memberId);
        }
    }

    private record MemberKey(int stationId, UUID memberUid) {}
}
