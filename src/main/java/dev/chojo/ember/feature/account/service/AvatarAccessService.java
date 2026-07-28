/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves which account an avatar request may be served for. Every lookup ends in the account
 * UUID the avatar is stored under, or an empty result when the caller has no relationship to the
 * target — existence of the target is never distinguishable from a missing permission.
 */
@Singleton
public class AvatarAccessService {
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final StationRepository stationRepository;
    private final FederationRepository federationRepository;

    @Inject
    public AvatarAccessService(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            StationRepository stationRepository,
            FederationRepository federationRepository) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.stationRepository = stationRepository;
        this.federationRepository = federationRepository;
    }

    /**
     * Returns the account UUID the calling session's own avatar is stored under.
     *
     * @param session the calling session
     * @return the account UUID, or empty when the session carries no account or the account has no UUID
     */
    public Optional<UUID> ownAvatarUid(UserSession session) {
        if (session.account() == null) return Optional.empty();
        return Optional.ofNullable(
                accountRepository.resolveUid(session.account().id()));
    }

    /**
     * Returns the account UUID to serve for an account-keyed avatar request.
     *
     * @param session    the calling session
     * @param accountUid the requested account UUID
     * @return the account UUID to read, or empty when the account is unknown or invisible to the caller
     */
    public Optional<UUID> accountAvatarUid(UserSession session, UUID accountUid) {
        var target = accountRepository.findByUid(accountUid).orElse(null);
        if (target == null) return Optional.empty();
        if (!canSeeAccountAvatar(session, target.id())) return Optional.empty();
        return Optional.of(accountUid);
    }

    /**
     * Returns the account UUID to serve for a member-keyed avatar request. Resolves the station and
     * member first, then the underlying account.
     *
     * @param session    the calling session
     * @param stationUid the station the member belongs to
     * @param memberUid  the member UUID
     * @return the account UUID to read, or empty when the member is unknown, has no account, or is
     *         invisible to the caller
     */
    public Optional<UUID> memberAvatarUid(UserSession session, UUID stationUid, UUID memberUid) {
        var targetStation = stationRepository.findByUid(stationUid).orElse(null);
        if (targetStation == null) return Optional.empty();
        var targetMember =
                stationMemberRepository.findByUid(targetStation.id(), memberUid).orElse(null);
        if (targetMember == null) return Optional.empty();
        if (!canSeeMemberAvatar(session, targetStation.id())) return Optional.empty();
        if (targetMember.accountId() == null) return Optional.empty();
        return Optional.ofNullable(accountRepository.resolveUid(targetMember.accountId()));
    }

    /**
     * Returns true when the calling session is allowed to view an avatar belonging
     * to {@code targetStationId}: the caller has a membership at the target station,
     * is an instance administrator, or the caller's currently selected station has
     * an active federation partnership with the target station. All other cases —
     * including a logged-in account with no station memberships — fall through to
     * an empty result to avoid leaking whether the target member exists.
     */
    private boolean canSeeMemberAvatar(UserSession session, int targetStationId) {
        if (session.account() == null) return false;
        if (session.account().instanceUserType() == InstanceUserType.ADMINISTRATOR) {
            return true;
        }
        if (stationMemberRepository
                .findByStationAndAccount(targetStationId, session.account().id())
                .isPresent()) {
            return true;
        }
        if (session.stationId() == null) return false;
        UUID targetUid = stationRepository.resolveUid(targetStationId);
        if (targetUid == null) return false;
        return federationRepository
                .findPartnerByStationAndRemoteUid(session.stationId(), targetUid)
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .isPresent();
    }

    /**
     * Returns true when the calling session is allowed to view the avatar of the
     * account with id {@code targetAccountId}. Visibility rules: caller is the owner,
     * caller is an instance administrator, caller shares any station membership with
     * the target, or caller's currently-selected station has an active federation
     * partnership with any station the target is a member of.
     */
    private boolean canSeeAccountAvatar(UserSession session, int targetAccountId) {
        if (session.account() == null) return false;
        if (session.account().id() == targetAccountId) return true;
        if (session.account().instanceUserType() == InstanceUserType.ADMINISTRATOR) {
            return true;
        }
        var targetMemberships = stationMemberRepository.findByAccount(targetAccountId);
        if (targetMemberships.isEmpty()) return false;
        var callerMemberships =
                stationMemberRepository.findByAccount(session.account().id());
        var callerStationIds =
                callerMemberships.stream().map(StationMember::stationId).toList();
        for (var targetMembership : targetMemberships) {
            if (callerStationIds.contains(targetMembership.stationId())) return true;
        }
        if (session.stationId() == null) return false;
        for (var targetMembership : targetMemberships) {
            UUID targetUid = stationRepository.resolveUid(targetMembership.stationId());
            if (targetUid == null) continue;
            var partner = federationRepository
                    .findPartnerByStationAndRemoteUid(session.stationId(), targetUid)
                    .orElse(null);
            if (partner != null && partner.status() == FederationPartner.FederationStatus.ACTIVE) {
                return true;
            }
        }
        return false;
    }
}
