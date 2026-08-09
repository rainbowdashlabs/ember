/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.UUID;

/**
 * Decides what a local member may do on a board owned by a federation partner.
 * Combines the share mode the owning station granted with the view and edit overrides the local
 * station configured on top of it.
 */
@Singleton
public class FederatedBoardAccessService {
    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardRepository federatedBoardRepository;
    private final BoardService boardService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final StationMemberService memberService;
    private final MemberGroupService groupService;
    private final UserTagService tagService;

    @Inject
    public FederatedBoardAccessService(
            FederatedBoardService federatedBoardService,
            FederatedBoardRepository federatedBoardRepository,
            BoardService boardService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            StationMemberService memberService,
            MemberGroupService groupService,
            UserTagService tagService) {
        this.federatedBoardService = federatedBoardService;
        this.federatedBoardRepository = federatedBoardRepository;
        this.boardService = boardService;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.memberService = memberService;
        this.groupService = groupService;
        this.tagService = tagService;
    }

    /**
     * Returns the effective share mode. The share target is stored on the owning station's
     * partner record. When queried from the partner station, the local partner record ID
     * differs from the owning station's partner record ID. We try both the direct lookup
     * and the reverse lookup (finding the owning station's partner record that points to us).
     *
     * @param partnerId the partner record id
     * @param boardId   the board id
     * @return the share mode if the board is shared with the partner
     */
    public Optional<BoardShareMode> getEffectiveShareMode(int partnerId, int boardId) {
        var mode = federatedBoardService.getShareMode(boardId, partnerId);
        if (mode.isPresent()) return mode;

        var partner = federationRepository.findPartnerById(partnerId).orElse(null);
        if (partner == null) return Optional.empty();

        var ourStationUid = stationRepository
                .findById(partner.stationId())
                .map(Station::uid)
                .orElse(null);
        if (ourStationUid == null) return Optional.empty();

        var board = boardService.findById(boardId).orElse(null);
        if (board == null) return Optional.empty();

        var owningPartner = federationRepository.findPartnerByStationAndRemoteUid(board.stationId(), ourStationUid);
        return owningPartner.flatMap(op -> federatedBoardService.getShareMode(boardId, op.id()));
    }

    /**
     * Checks if a member passes the local view override (if one exists).
     * Returns true if no override is set (all members can view).
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     * @param memberId       the local member id
     * @return whether the member passes
     */
    public boolean passesLocalViewOverride(int partnerId, UUID remoteBoardUid, int memberId) {
        if (!federatedBoardRepository.hasLocalViewOverride(partnerId, remoteBoardUid)) return true;
        var override = federatedBoardRepository.findLocalViewOverride(partnerId, remoteBoardUid);
        return matchesAccess(memberId, override);
    }

    /**
     * Checks if a member passes the local edit override (if one exists).
     * Returns true if no override is set.
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     * @param memberId       the local member id
     * @return whether the member passes
     */
    public boolean passesLocalEditOverride(int partnerId, UUID remoteBoardUid, int memberId) {
        if (!federatedBoardRepository.hasLocalEditOverride(partnerId, remoteBoardUid)) return true;
        var override = federatedBoardRepository.findLocalEditOverride(partnerId, remoteBoardUid);
        return matchesAccess(memberId, override);
    }

    /**
     * Full view access check. The board must be shared with this partner. A local view override
     * replaces the shared requirement entirely, otherwise the required user type of the share
     * target decides.
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     * @param boardId        the board id
     * @param memberId       the local member id
     * @return whether the member may view the board
     */
    public boolean canView(int partnerId, UUID remoteBoardUid, int boardId, int memberId) {
        var shareInfo = getEffectiveShareInfo(partnerId, boardId);
        if (shareInfo == null) return false;

        if (federatedBoardRepository.hasLocalViewOverride(partnerId, remoteBoardUid)) {
            var override = federatedBoardRepository.findLocalViewOverride(partnerId, remoteBoardUid);
            return matchesAccess(memberId, override);
        }

        return memberHasUserType(memberId, shareInfo.requiredUserType());
    }

    /**
     * Full write access check.
     * Share mode must be FULL, then same override logic as view + edit override.
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     * @param boardId        the board id
     * @param memberId       the local member id
     * @return whether the member may write on the board
     */
    public boolean canWrite(int partnerId, UUID remoteBoardUid, int boardId, int memberId) {
        var shareInfo = getEffectiveShareInfo(partnerId, boardId);
        if (shareInfo == null || shareInfo.shareMode() != BoardShareMode.FULL) return false;

        if (!canView(partnerId, remoteBoardUid, boardId, memberId)) return false;

        if (federatedBoardRepository.hasLocalEditOverride(partnerId, remoteBoardUid)) {
            var editOverride = federatedBoardRepository.findLocalEditOverride(partnerId, remoteBoardUid);
            return matchesAccess(memberId, editOverride);
        }
        return true;
    }

    /**
     * Stores the local view override for a federated board.
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     * @param access         the access requirement
     */
    public void setLocalViewOverride(int partnerId, UUID remoteBoardUid, AccessData access) {
        federatedBoardRepository.setLocalViewOverride(partnerId, remoteBoardUid, access);
    }

    /**
     * Stores the local edit override for a federated board.
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     * @param access         the access requirement
     */
    public void setLocalEditOverride(int partnerId, UUID remoteBoardUid, AccessData access) {
        federatedBoardRepository.setLocalEditOverride(partnerId, remoteBoardUid, access);
    }

    /**
     * Returns the local view override for a federated board.
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     * @return the access requirement
     */
    public AccessData getLocalViewOverride(int partnerId, UUID remoteBoardUid) {
        return federatedBoardRepository.findLocalViewOverride(partnerId, remoteBoardUid);
    }

    /**
     * Returns the local edit override for a federated board.
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     * @return the access requirement
     */
    public AccessData getLocalEditOverride(int partnerId, UUID remoteBoardUid) {
        return federatedBoardRepository.findLocalEditOverride(partnerId, remoteBoardUid);
    }

    private boolean memberHasUserType(int memberId, StationUserType requiredUserType) {
        if (requiredUserType == null || requiredUserType == StationUserType.MEMBER) return true;
        var member = memberService.findById(memberId).orElse(null);
        if (member == null) return false;
        return member.userType() == requiredUserType;
    }

    private ShareInfo getEffectiveShareInfo(int partnerId, int boardId) {
        var mode = getEffectiveShareMode(partnerId, boardId);
        if (mode.isEmpty()) return null;
        var requiredUserType = getSharedRequiredUserType(partnerId, boardId);
        return new ShareInfo(mode.get(), requiredUserType);
    }

    private StationUserType getSharedRequiredUserType(int partnerId, int boardId) {
        var partner = federationRepository.findPartnerById(partnerId).orElse(null);
        if (partner == null) return StationUserType.MEMBER;
        var ourStationUid = stationRepository
                .findById(partner.stationId())
                .map(Station::uid)
                .orElse(null);
        if (ourStationUid == null) return StationUserType.MEMBER;
        var board = boardService.findById(boardId).orElse(null);
        if (board == null) return StationUserType.MEMBER;
        var owningPartner = federationRepository.findPartnerByStationAndRemoteUid(board.stationId(), ourStationUid);
        return owningPartner
                .flatMap(op -> federatedBoardService.getRequiredUserType(boardId, op.id()))
                .orElse(StationUserType.MEMBER);
    }

    private boolean matchesAccess(int memberId, AccessData access) {
        if (!access.userTypes().isEmpty()) {
            var member = memberService.findById(memberId);
            if (member.isPresent() && access.userTypes().contains(member.get().userType())) return true;
        }
        if (!access.groupIds().isEmpty()) {
            var memberGroupIds = groupService.findGroupsForMember(memberId).stream()
                    .map(MemberGroup::id)
                    .toList();
            if (memberGroupIds.stream().anyMatch(access.groupIds()::contains)) return true;
        }
        if (!access.tagIds().isEmpty()) {
            var memberTagIds = tagService.findTagsForMember(memberId).stream()
                    .map(UserTag::id)
                    .toList();
            return memberTagIds.stream().anyMatch(access.tagIds()::contains);
        }
        return false;
    }

    private record ShareInfo(BoardShareMode shareMode, StationUserType requiredUserType) {}
}
