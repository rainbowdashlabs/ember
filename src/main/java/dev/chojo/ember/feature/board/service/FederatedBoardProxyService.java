/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.FederationBoardBookmark;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Partner station service that discovers federated boards, proxies requests to owning stations,
 * and enforces local access overrides.
 * Local partners (same instance) use direct DB access; remote partners use HTTP.
 */
@Singleton
public class FederatedBoardProxyService {
    private static final Logger log = LoggerFactory.getLogger(FederatedBoardProxyService.class);

    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardRepository federatedBoardRepository;
    private final BoardService boardService;
    private final FederationService federationService;
    private final FederationHttpClient httpClient;
    private final StationRepository stationRepository;
    private final StationMemberService memberService;
    private final MemberGroupService groupService;
    private final UserTagService tagService;

    @Inject
    public FederatedBoardProxyService(
            FederatedBoardService federatedBoardService,
            FederatedBoardRepository federatedBoardRepository,
            BoardService boardService,
            FederationService federationService,
            FederationHttpClient httpClient,
            StationRepository stationRepository,
            StationMemberService memberService,
            MemberGroupService groupService,
            UserTagService tagService) {
        this.federatedBoardService = federatedBoardService;
        this.federatedBoardRepository = federatedBoardRepository;
        this.boardService = boardService;
        this.federationService = federationService;
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;
        this.memberService = memberService;
        this.groupService = groupService;
        this.tagService = tagService;
    }

    // -- Discovery --

    /**
     * Discovers all boards shared with the local station from all active partners.
     * Returns board info with partner station name and share mode.
     */
    public List<DiscoveredBoard> discoverBoards(int stationId) {
        var futures = new ArrayList<CompletableFuture<List<DiscoveredBoard>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            if (!federationService.hasCapability(partner.id(), CapabilityType.BOARD_SHARE, Direction.IMPORT)) continue;

            futures.add(CompletableFuture.supplyAsync(() -> {
                if (partner.isRemote()) {
                    return discoverBoardsViaHttp(stationId, partner);
                } else {
                    return discoverBoardsDirect(partner);
                }
            }));
        }
        return collectResults(futures);
    }

    private List<DiscoveredBoard> discoverBoardsDirect(FederationPartner partner) {
        var boardIds = federatedBoardService.findSharedBoardIds(partner.id());
        return boardIds.stream()
                .map(boardId -> boardService
                        .findById(boardId)
                        .map(board -> {
                            var mode = federatedBoardService
                                    .getShareMode(boardId, partner.id())
                                    .orElse(BoardShareMode.READ_ONLY);
                            return new DiscoveredBoard(
                                    partner.id(),
                                    board.id(),
                                    board.name(),
                                    board.shortKey(),
                                    board.description(),
                                    mode,
                                    partnerStationName(partner));
                        })
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<DiscoveredBoard> discoverBoardsViaHttp(int localStationId, FederationPartner partner) {
        try {
            var remoteBoards = httpClient.signedGetList(
                    partner.remoteHost(), "/federation/remote/boards", localStationId, getPrivateKey(localStationId));
            return remoteBoards.stream()
                    .map(m -> new DiscoveredBoard(
                            partner.id(),
                            ((Number) m.get("id")).intValue(),
                            (String) m.get("name"),
                            (String) m.get("shortKey"),
                            (String) m.get("description"),
                            BoardShareMode.valueOf((String) m.get("shareMode")),
                            partnerStationName(partner)))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to discover boards from partner {}", partner.id(), e);
            return List.of();
        }
    }

    // -- Access Control --

    /**
     * Returns the effective share mode, which is the ceiling of the owning station's mode
     * and the local override. Local overrides can only restrict, not escalate.
     * For local partners, reads the share mode directly. For remote, it was cached in the bookmark.
     */
    public Optional<BoardShareMode> getEffectiveShareMode(int partnerId, int boardId) {
        return federatedBoardService.getShareMode(boardId, partnerId);
    }

    /**
     * Checks if a member passes the local view override (if one exists).
     * Returns true if no override is set (all members can view).
     */
    public boolean passesLocalViewOverride(int partnerId, int remoteBoardId, int memberId) {
        if (!federatedBoardRepository.hasLocalViewOverride(partnerId, remoteBoardId)) return true;
        var override = federatedBoardRepository.findLocalViewOverride(partnerId, remoteBoardId);
        return matchesAccess(memberId, override);
    }

    /**
     * Checks if a member passes the local edit override (if one exists).
     * Returns true if no override is set.
     */
    public boolean passesLocalEditOverride(int partnerId, int remoteBoardId, int memberId) {
        if (!federatedBoardRepository.hasLocalEditOverride(partnerId, remoteBoardId)) return true;
        var override = federatedBoardRepository.findLocalEditOverride(partnerId, remoteBoardId);
        return matchesAccess(memberId, override);
    }

    /**
     * Full access check: share mode must be at least the required level,
     * and the member must pass the local override.
     */
    public boolean canView(int partnerId, int remoteBoardId, int memberId) {
        var mode = getEffectiveShareMode(partnerId, remoteBoardId);
        if (mode.isEmpty()) return false;
        return passesLocalViewOverride(partnerId, remoteBoardId, memberId);
    }

    public boolean canWrite(int partnerId, int remoteBoardId, int memberId) {
        var mode = getEffectiveShareMode(partnerId, remoteBoardId);
        if (mode.isEmpty() || mode.get() != BoardShareMode.FULL) return false;
        return passesLocalViewOverride(partnerId, remoteBoardId, memberId)
                && passesLocalEditOverride(partnerId, remoteBoardId, memberId);
    }

    // -- Local Overrides --

    public void setLocalViewOverride(int partnerId, int remoteBoardId, AccessData access) {
        federatedBoardRepository.setLocalViewOverride(partnerId, remoteBoardId, access);
    }

    public void setLocalEditOverride(int partnerId, int remoteBoardId, AccessData access) {
        federatedBoardRepository.setLocalEditOverride(partnerId, remoteBoardId, access);
    }

    public AccessData getLocalViewOverride(int partnerId, int remoteBoardId) {
        return federatedBoardRepository.findLocalViewOverride(partnerId, remoteBoardId);
    }

    public AccessData getLocalEditOverride(int partnerId, int remoteBoardId) {
        return federatedBoardRepository.findLocalEditOverride(partnerId, remoteBoardId);
    }

    // -- Bookmarks --

    public FederationBoardBookmark createBookmark(
            int memberId, int partnerId, int remoteBoardId, String name, String shortKey, BoardShareMode shareMode) {
        return federatedBoardService.createBookmark(memberId, partnerId, remoteBoardId, name, shortKey, shareMode);
    }

    public void deleteBookmark(int bookmarkId) {
        federatedBoardService.deleteBookmark(bookmarkId);
    }

    public void deleteBookmarkByBoard(int memberId, int partnerId, int remoteBoardId) {
        federatedBoardService.deleteBookmarkByBoard(memberId, partnerId, remoteBoardId);
    }

    public List<FederationBoardBookmark> findBookmarks(int memberId) {
        return federatedBoardService.findBookmarks(memberId);
    }

    /**
     * Called via webhook when the owning station renames a board.
     * Updates all bookmarks for this board.
     */
    public void onBoardRenamed(int partnerId, int remoteBoardId, String newName, String newShortKey) {
        federatedBoardRepository.updateBookmarkName(partnerId, remoteBoardId, newName, newShortKey);
    }

    /**
     * Called via webhook when the owning station unshares a board.
     * Deletes all bookmarks for this board.
     */
    public void onBoardUnshared(int partnerId, int remoteBoardId) {
        federatedBoardRepository.deleteBookmarksByBoard(partnerId, remoteBoardId);
    }

    /**
     * Called via webhook when the share mode changes.
     */
    public void onShareModeChanged(int partnerId, int remoteBoardId, BoardShareMode newMode) {
        federatedBoardRepository.updateBookmarkShareMode(partnerId, remoteBoardId, newMode);
    }

    // -- Helpers --

    private boolean matchesAccess(int memberId, AccessData access) {
        if (!access.roleIds().isEmpty()) {
            var memberRoles =
                    memberService.findRoles(memberId).stream().map(Role::id).toList();
            if (memberRoles.stream().anyMatch(access.roleIds()::contains)) return true;
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
            if (memberTagIds.stream().anyMatch(access.tagIds()::contains)) return true;
        }
        return false;
    }

    private String partnerStationName(FederationPartner partner) {
        return stationRepository
                .findById(partner.partnerStationId())
                .map(Station::name)
                .orElse("Partner #" + partner.id());
    }

    private String getPrivateKey(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(Station::federationPrivateKey)
                .orElse(null);
    }

    private <T> List<T> collectResults(List<CompletableFuture<List<T>>> futures) {
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel federation board discovery", e);
        }
        var result = new ArrayList<T>();
        for (var future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                log.error("Error collecting federation board results", e);
            }
        }
        return result;
    }

    public record DiscoveredBoard(
            int partnerId,
            int remoteBoardId,
            String name,
            String shortKey,
            String description,
            BoardShareMode shareMode,
            String partnerStationName) {}
}
