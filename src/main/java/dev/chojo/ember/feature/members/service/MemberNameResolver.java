/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Singleton
public class MemberNameResolver {
    private final StationMemberService memberService;
    private final AccountRepository accountRepository;
    private final EventFederationRepository eventFederationRepository;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final MemberGroupService groupService;
    private final UserTagService tagService;
    private final Cache<UUID, DisplayData> displayCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /**
     * The halves of a name, kept by member.
     *
     * <p>Expiry is on writing rather than on access: a name that is read every few minutes, which
     * is any member of a station people are working in, would never fall out of a cache that
     * expires on access, and a name changed today would be read for as long as anybody kept
     * looking at the old one. {@link #forget(int)} takes one out the moment it is written.
     */
    private final Cache<Integer, NameParts> partsCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @Inject
    public MemberNameResolver(
            StationMemberService memberService,
            AccountRepository accountRepository,
            EventFederationRepository eventFederationRepository,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            MemberGroupService groupService,
            UserTagService tagService) {
        this.memberService = memberService;
        this.accountRepository = accountRepository;
        this.eventFederationRepository = eventFederationRepository;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.groupService = groupService;
        this.tagService = tagService;
    }

    /**
     * The name a station reads on its own screens.
     *
     * @return the name, or null where the member is not known
     */
    public String called(int memberId) {
        return partsOf(memberId).called();
    }

    /**
     * The name that says who somebody is and what they are called at once, for a list of people.
     *
     * @return the name, or null where the member is not known
     */
    public String identified(int memberId) {
        return partsOf(memberId).identified();
    }

    /**
     * The register name, for a document.
     *
     * @return the name, or null where the member is not known
     */
    public String official(int memberId) {
        return partsOf(memberId).official();
    }

    /**
     * The first name a mail says hello to, standing on its own.
     *
     * @return the first name, or null where the member is not known
     */
    public String greeting(int memberId) {
        return partsOf(memberId).greeting();
    }

    /**
     * The halves a member's name is written from, read once and kept.
     *
     * <p>What is cached is the parts rather than a finished name, because the same member is
     * written four ways and a cache of one string can only serve one of them.
     */
    private NameParts partsOf(int memberId) {
        var cached = partsCache.getIfPresent(memberId);
        if (cached != null) return cached;
        var parts = readParts(memberId);
        if (parts.known()) partsCache.put(memberId, parts);
        return parts;
    }

    private NameParts readParts(int memberId) {
        var memberOpt = memberService.findById(memberId);
        if (memberOpt.isEmpty()) return NameParts.unknown();
        var member = memberOpt.get();
        if (member.accountId() != null) {
            var account = accountRepository.findById(member.accountId()).orElse(null);
            if (account != null) {
                return NameParts.of(account.firstName(), account.lastName());
            }
        }
        return NameParts.frozen(member.displayName());
    }

    /**
     * Forgets what was read about one member, so that a name changed now is read now.
     *
     * <p>The cache expires on access rather than on writing, so a member whose name is read every
     * few minutes would otherwise keep an old one for as long as people keep looking at it.
     */
    public void forget(int memberId) {
        partsCache.invalidate(memberId);
        memberService.findById(memberId).ifPresent(member -> displayCache.invalidate(member.uid()));
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
                .map(p -> FederationDisplayNames.partnerName(stationRepository, p, null))
                .orElse(null);
    }

    /**
     * Resolves name from a history/transition/comment entry that may be local or federated.
     * Tries local (actorMemberId) first, then federated (partnerId + memberUid).
     */
    public String resolve(Integer actorMemberId, Integer federatedPartnerId, UUID federatedMemberUid) {
        if (actorMemberId != null) {
            var name = called(actorMemberId);
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
                var name = called(memberId.get());
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

    public ResolvedMember resolveDisplay(MemberIdentity identity) {
        if (identity == null) return new ResolvedMember(null, null);
        var enriched = enrichDisplay(identity);
        var name = resolve(identity);
        return new ResolvedMember(enriched, name);
    }

    /**
     * Enriches a MemberIdentity with display metadata (name, station name, name color, visible tag badge).
     * Results are cached for 5 minutes after last access.
     */
    public MemberIdentity enrichDisplay(MemberIdentity identity) {
        if (identity == null) return null;

        // Resolve once, then cache only if we have a name. Caching null would keep federated
        // members nameless for the full TTL even after their name lands in the partner cache
        // (e.g. via a later signed federation push or a demo seeder re-run).
        var cached = displayCache.getIfPresent(identity.memberUid());
        var data = cached != null ? cached : resolveDisplayData(identity);
        if (cached == null && data.name() != null) {
            displayCache.put(identity.memberUid(), data);
        }

        return identity.withDisplay(data.name(), data.stationName(), data.nameColor(), data.displayTag());
    }

    private DisplayData resolveDisplayData(MemberIdentity identity) {
        var station = stationRepository.findByUid(identity.stationUid()).orElse(null);
        String stationName = station != null ? station.name() : null;
        if (station != null) {
            var memberId = memberService.resolveId(station.id(), identity.memberUid());
            if (memberId.isPresent()) {
                String name = called(memberId.get());
                return new DisplayData(
                        name, stationName, resolveNameColor(memberId.get()), resolveDisplayTag(memberId.get()));
            }
        }
        // Federated: fall back to the partner name cache for the remote member.
        var partner = federationRepository.findPartnerByRemoteStationUid(identity.stationUid());
        if (partner.isPresent()) {
            var name = eventFederationRepository
                    .getCachedName(partner.get().id(), identity.memberUid())
                    .orElse(null);
            if (name != null) return new DisplayData(name, stationName, null, null);
        }
        return new DisplayData(null, stationName, null, null);
    }

    private String resolveNameColor(int memberId) {
        List<MemberGroup> groups = groupService.findGroupsForMember(memberId);
        return groups.stream()
                .filter(g -> g.color() != null && !g.color().isBlank())
                .max(Comparator.comparingInt(MemberGroup::position))
                .map(MemberGroup::color)
                .orElse(null);
    }

    private MemberIdentity.DisplayTag resolveDisplayTag(int memberId) {
        List<UserTag> tags = tagService.findTagsForMember(memberId);
        return tags.stream()
                .filter(t -> t.visible() && t.color() != null && !t.color().isBlank())
                .max(Comparator.comparingInt(UserTag::position))
                .map(t -> new MemberIdentity.DisplayTag(t.name(), t.color()))
                .orElse(null);
    }

    /**
     * Resolves both the display name and enriched identity (with nameColor and displayTag) in one call.
     * This is the preferred method for building API responses.
     */
    public record ResolvedMember(MemberIdentity identity, String name) {}

    private record DisplayData(
            String name, String stationName, String nameColor, MemberIdentity.DisplayTag displayTag) {}
}
