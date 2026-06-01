/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.FederationBoardBookmark;
import dev.chojo.ember.feature.board.entity.FederationBoardShare;
import dev.chojo.ember.feature.board.entity.FederationBoardShareTarget;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages board sharing configuration and federated operations on the owning station.
 * Handles share targets (per-partner mode), federated edit roles, federated assignees,
 * comment authors, creators, watchers, and bookmarks.
 */
@Singleton
public class FederatedBoardService {
    private final FederatedBoardRepository repository;

    @Inject
    public FederatedBoardService(FederatedBoardRepository repository) {
        this.repository = repository;
    }

    // -- Board Sharing --

    public record PartnerShareConfig(int partnerId, BoardShareMode shareMode) {}

    public void shareBoard(int boardId, List<PartnerShareConfig> partnerConfigs) {
        var share = repository.findShare(boardId).orElseGet(() -> repository.createShare(boardId));
        repository.clearShareTargets(share.id());
        for (var config : partnerConfigs) {
            repository.setShareTarget(share.id(), config.partnerId(), config.shareMode());
        }
    }

    public void unshareBoard(int boardId) {
        repository.deleteShare(boardId);
    }

    public Optional<FederationBoardShare> findShare(int boardId) {
        return repository.findShare(boardId);
    }

    public List<FederationBoardShareTarget> findShareTargets(int boardId) {
        return repository
                .findShare(boardId)
                .map(s -> repository.findShareTargets(s.id()))
                .orElse(List.of());
    }

    public Optional<BoardShareMode> getShareMode(int boardId, int partnerId) {
        return repository.findShareMode(boardId, partnerId);
    }

    public List<Integer> findSharedBoardIds(int partnerId) {
        return repository.findSharedBoardIds(partnerId);
    }

    public boolean isSharedWith(int boardId, int partnerId) {
        return repository.findShareMode(boardId, partnerId).isPresent();
    }

    // -- Access Control --

    public boolean canFederatedView(int boardId, int partnerId) {
        return repository.findShareMode(boardId, partnerId).isPresent();
    }

    public boolean canFederatedWrite(int boardId, int partnerId) {
        return repository
                .findShareMode(boardId, partnerId)
                .map(mode -> mode == BoardShareMode.FULL)
                .orElse(false);
    }

    public boolean canFederatedEdit(int boardId, int partnerId, List<Integer> partnerRoleIds) {
        if (!canFederatedWrite(boardId, partnerId)) return false;
        if (!repository.hasFederatedEditRoles(boardId)) return true;
        var allowedRoleIds = repository.findFederatedEditRoles(boardId);
        return partnerRoleIds.stream().anyMatch(allowedRoleIds::contains);
    }

    public void setFederatedEditRoles(int boardId, List<Integer> roleIds) {
        repository.setFederatedEditRoles(boardId, roleIds);
    }

    public List<Integer> findFederatedEditRoles(int boardId) {
        return repository.findFederatedEditRoles(boardId);
    }

    // -- Bookmarks --

    public FederationBoardBookmark createBookmark(
            int memberId,
            int partnerId,
            UUID remoteBoardUid,
            String remoteBoardName,
            String remoteBoardShortKey,
            BoardShareMode shareMode) {
        return repository.createBookmark(
                memberId, partnerId, remoteBoardUid, remoteBoardName, remoteBoardShortKey, shareMode);
    }

    public void deleteBookmark(int bookmarkId) {
        repository.deleteBookmark(bookmarkId);
    }

    public void deleteBookmarkByBoard(int memberId, int partnerId, UUID remoteBoardUid) {
        repository.deleteBookmarkByBoard(memberId, partnerId, remoteBoardUid);
    }

    public List<FederationBoardBookmark> findBookmarks(int memberId) {
        return repository.findBookmarks(memberId);
    }

    public void updateBookmarkName(int partnerId, UUID remoteBoardUid, String newName, String newShortKey) {
        repository.updateBookmarkName(partnerId, remoteBoardUid, newName, newShortKey);
    }

    public void deleteBookmarksByBoard(int partnerId, UUID remoteBoardUid) {
        repository.deleteBookmarksByBoard(partnerId, remoteBoardUid);
    }

    public void updateBookmarkShareMode(int partnerId, UUID remoteBoardUid, BoardShareMode shareMode) {
        repository.updateBookmarkShareMode(partnerId, remoteBoardUid, shareMode);
    }

    // -- Local Overrides --

    public void setLocalViewOverride(int partnerId, UUID remoteBoardUid, AccessData access) {
        repository.setLocalViewOverride(partnerId, remoteBoardUid, access);
    }

    public void setLocalEditOverride(int partnerId, UUID remoteBoardUid, AccessData access) {
        repository.setLocalEditOverride(partnerId, remoteBoardUid, access);
    }

    public AccessData getLocalViewOverride(int partnerId, UUID remoteBoardUid) {
        return repository.findLocalViewOverride(partnerId, remoteBoardUid);
    }

    public AccessData getLocalEditOverride(int partnerId, UUID remoteBoardUid) {
        return repository.findLocalEditOverride(partnerId, remoteBoardUid);
    }

    public boolean hasLocalViewOverride(int partnerId, UUID remoteBoardUid) {
        return repository.hasLocalViewOverride(partnerId, remoteBoardUid);
    }

    public boolean hasLocalEditOverride(int partnerId, UUID remoteBoardUid) {
        return repository.hasLocalEditOverride(partnerId, remoteBoardUid);
    }
}
