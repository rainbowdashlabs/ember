/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class MemberNameResolver {
    private final StationMemberService memberService;
    private final AccountRepository accountRepository;
    private final EventFederationRepository eventFederationRepository;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;

    @Inject
    public MemberNameResolver(
            StationMemberService memberService,
            AccountRepository accountRepository,
            EventFederationRepository eventFederationRepository,
            FederationRepository federationRepository,
            StationRepository stationRepository) {
        this.memberService = memberService;
        this.accountRepository = accountRepository;
        this.eventFederationRepository = eventFederationRepository;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
    }

    /**
     * Resolves a local member's display name by member ID.
     * Tries: account first+last name, then member displayName, then null.
     */
    public String resolveLocal(int memberId) {
        var memberOpt = memberService.findById(memberId);
        if (memberOpt.isEmpty()) return null;
        var member = memberOpt.get();
        if (member.accountId() != null) {
            var name = accountRepository
                    .findById(member.accountId())
                    .map(a -> (a.firstName() + " " + a.lastName()).trim())
                    .orElse(null);
            if (name != null && !name.isBlank()) return name;
        }
        if (member.displayName() != null && !member.displayName().isBlank()) {
            return member.displayName();
        }
        return null;
    }

    /**
     * Resolves a federated member's display name.
     * Tries: name cache, then partner station name fallback, then null.
     */
    public String resolveFederated(int partnerId, UUID memberUid) {
        if (memberUid != null) {
            var cached = eventFederationRepository
                    .getCachedName(partnerId, memberUid)
                    .orElse(null);
            if (cached != null) return cached;
        }
        return federationRepository
                .findPartnerById(partnerId)
                .flatMap(p -> stationRepository.findByUid(p.partnerStationId()))
                .map(Station::name)
                .orElse(null);
    }

    /**
     * Resolves name from a history/transition/comment entry that may be local or federated.
     * Tries local (actorMemberId) first, then federated (partnerId + memberUid).
     */
    public String resolve(Integer actorMemberId, Integer federatedPartnerId, UUID federatedMemberUid) {
        if (actorMemberId != null) {
            var name = resolveLocal(actorMemberId);
            if (name != null && !name.isBlank()) return name;
        }
        if (federatedPartnerId != null) {
            return resolveFederated(federatedPartnerId, federatedMemberUid);
        }
        return null;
    }

    /**
     * Resolves a display name from a MemberIdentity.
     * Tries local member resolution first, then federated name cache, then station name fallback.
     */
    public String resolve(MemberIdentity identity) {
        if (identity == null) return null;

        // Try local: resolve the member UUID back to an internal ID via station lookup
        var station = stationRepository.findByUid(identity.stationUid()).orElse(null);
        if (station != null) {
            var memberId = memberService.resolveId(station.id(), identity.memberUid());
            if (memberId.isPresent()) {
                var name = resolveLocal(memberId.get());
                if (name != null) return name;
            }
        }

        // Try federated name cache
        var partner = federationRepository.findPartnerByRemoteStationUid(identity.stationUid());
        if (partner.isPresent()) {
            var cached = eventFederationRepository
                    .getCachedName(partner.get().id(), identity.memberUid())
                    .orElse(null);
            if (cached != null) return cached;
        }

        // Fallback to station name
        if (station != null) return station.name();
        return null;
    }
}
