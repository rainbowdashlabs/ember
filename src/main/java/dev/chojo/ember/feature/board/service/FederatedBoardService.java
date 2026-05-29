/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.BoardTicketFederatedAssignee;
import dev.chojo.ember.feature.board.entity.BoardTicketFederatedCommentAuthor;
import dev.chojo.ember.feature.board.entity.BoardTicketFederatedCreator;
import dev.chojo.ember.feature.board.entity.BoardTicketFederatedWatcher;
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

    // -- Federated Assignees --

    public void setFederatedAssignee(int ticketId, int partnerId, UUID remoteMemberId) {
        repository.setFederatedAssignee(ticketId, partnerId, remoteMemberId);
    }

    public void removeFederatedAssignee(int ticketId) {
        repository.removeFederatedAssignee(ticketId);
    }

    public Optional<BoardTicketFederatedAssignee> findFederatedAssignee(int ticketId) {
        return repository.findFederatedAssignee(ticketId);
    }

    // -- Federated Comment Authors --

    public void setFederatedCommentAuthor(int commentId, int partnerId, UUID remoteMemberId) {
        repository.setFederatedCommentAuthor(commentId, partnerId, remoteMemberId);
    }

    public Optional<BoardTicketFederatedCommentAuthor> findFederatedCommentAuthor(int commentId) {
        return repository.findFederatedCommentAuthor(commentId);
    }

    // -- Federated Creators --

    public void setFederatedCreator(int ticketId, int partnerId, UUID remoteMemberId) {
        repository.setFederatedCreator(ticketId, partnerId, remoteMemberId);
    }

    public Optional<BoardTicketFederatedCreator> findFederatedCreator(int ticketId) {
        return repository.findFederatedCreator(ticketId);
    }

    // -- Federated Watchers --

    public void addFederatedWatcher(int ticketId, int partnerId, UUID remoteMemberId) {
        repository.addFederatedWatcher(ticketId, partnerId, remoteMemberId);
    }

    public void removeFederatedWatcher(int ticketId, int partnerId, UUID remoteMemberId) {
        repository.removeFederatedWatcher(ticketId, partnerId, remoteMemberId);
    }

    public List<BoardTicketFederatedWatcher> findFederatedWatchers(int ticketId) {
        return repository.findFederatedWatchers(ticketId);
    }

    public boolean isFederatedWatching(int ticketId, int partnerId, UUID remoteMemberId) {
        return repository.isFederatedWatching(ticketId, partnerId, remoteMemberId);
    }

    // -- Bookmarks --

    public FederationBoardBookmark createBookmark(
            int memberId,
            int partnerId,
            int remoteBoardId,
            String remoteBoardName,
            String remoteBoardShortKey,
            BoardShareMode shareMode) {
        return repository.createBookmark(
                memberId, partnerId, remoteBoardId, remoteBoardName, remoteBoardShortKey, shareMode);
    }

    public void deleteBookmark(int bookmarkId) {
        repository.deleteBookmark(bookmarkId);
    }

    public void deleteBookmarkByBoard(int memberId, int partnerId, int remoteBoardId) {
        repository.deleteBookmarkByBoard(memberId, partnerId, remoteBoardId);
    }

    public List<FederationBoardBookmark> findBookmarks(int memberId) {
        return repository.findBookmarks(memberId);
    }

    public void updateBookmarkName(int partnerId, int remoteBoardId, String newName, String newShortKey) {
        repository.updateBookmarkName(partnerId, remoteBoardId, newName, newShortKey);
    }

    public void deleteBookmarksByBoard(int partnerId, int remoteBoardId) {
        repository.deleteBookmarksByBoard(partnerId, remoteBoardId);
    }

    public void updateBookmarkShareMode(int partnerId, int remoteBoardId, BoardShareMode shareMode) {
        repository.updateBookmarkShareMode(partnerId, remoteBoardId, shareMode);
    }

    // -- Local Overrides --

    public void setLocalViewOverride(int partnerId, int remoteBoardId, AccessData access) {
        repository.setLocalViewOverride(partnerId, remoteBoardId, access);
    }

    public void setLocalEditOverride(int partnerId, int remoteBoardId, AccessData access) {
        repository.setLocalEditOverride(partnerId, remoteBoardId, access);
    }

    public AccessData getLocalViewOverride(int partnerId, int remoteBoardId) {
        return repository.findLocalViewOverride(partnerId, remoteBoardId);
    }

    public AccessData getLocalEditOverride(int partnerId, int remoteBoardId) {
        return repository.findLocalEditOverride(partnerId, remoteBoardId);
    }

    public boolean hasLocalViewOverride(int partnerId, int remoteBoardId) {
        return repository.hasLocalViewOverride(partnerId, remoteBoardId);
    }

    public boolean hasLocalEditOverride(int partnerId, int remoteBoardId) {
        return repository.hasLocalEditOverride(partnerId, remoteBoardId);
    }
}
