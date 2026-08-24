/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.FederationBoardBookmark;
import dev.chojo.ember.feature.board.entity.FederationBoardShare;
import dev.chojo.ember.feature.board.entity.FederationBoardShareTarget;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(FederatedBoardService.class);

    private final FederatedBoardRepository repository;

    @Inject
    public FederatedBoardService(FederatedBoardRepository repository) {
        this.repository = repository;
    }

    // -- Board Sharing --

    public void shareBoard(int boardId, List<PartnerShareConfig> partnerConfigs) {
        var share = repository.findShare(boardId).orElseGet(() -> repository.createShare(boardId));
        repository.clearShareTargets(share.id());
        for (var config : partnerConfigs) {
            repository.setShareTarget(
                    share.id(),
                    config.partnerId(),
                    config.shareMode(),
                    config.requiredUserType() != null ? config.requiredUserType() : StationUserType.MEMBER);
        }
        log.info("Shared board {} with {} partners", boardId, partnerConfigs.size());
    }

    public void unshareBoard(int boardId) {
        repository.deleteShare(boardId);
        log.info("Unshared board {}", boardId);
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

    public Optional<StationUserType> getRequiredUserType(int boardId, int partnerId) {
        return repository.findRequiredUserType(boardId, partnerId);
    }

    public List<Integer> findSharedBoardIds(int partnerId) {
        return repository.findSharedBoardIds(partnerId);
    }

    public boolean isSharedWith(int boardId, int partnerId) {
        return repository.findShareMode(boardId, partnerId).isPresent();
    }

    public boolean canFederatedView(int boardId, int partnerId) {
        return repository.findShareMode(boardId, partnerId).isPresent();
    }

    // -- Access Control --

    public boolean canFederatedWrite(int boardId, int partnerId) {
        return repository
                .findShareMode(boardId, partnerId)
                .map(mode -> mode == BoardShareMode.FULL)
                .orElse(false);
    }

    public boolean canFederatedEdit(int boardId, int partnerId, List<StationUserType> partnerUserTypes) {
        if (!canFederatedWrite(boardId, partnerId)) return false;
        if (!repository.hasFederatedEditUserTypes(boardId)) return true;
        var allowedUserTypes = repository.findFederatedEditUserTypes(boardId);
        return partnerUserTypes.stream().anyMatch(allowedUserTypes::contains);
    }

    public void setFederatedEditUserTypes(int boardId, List<StationUserType> userTypes) {
        repository.setFederatedEditUserTypes(boardId, userTypes);
        log.info("Updated federated edit user types for board {} ({} entries)", boardId, userTypes.size());
    }

    public List<StationUserType> findFederatedEditUserTypes(int boardId) {
        return repository.findFederatedEditUserTypes(boardId);
    }

    public FederationBoardBookmark createBookmark(
            int memberId,
            int partnerId,
            UUID remoteBoardUid,
            String remoteBoardName,
            String remoteBoardShortKey,
            BoardShareMode shareMode) {
        var bookmark = repository.createBookmark(
                memberId, partnerId, remoteBoardUid, remoteBoardName, remoteBoardShortKey, shareMode);
        log.info("Created board bookmark {} for member {} (partner {})", bookmark.id(), memberId, partnerId);
        return bookmark;
    }

    // -- Bookmarks --

    public void deleteBookmark(int bookmarkId, int memberId) {
        repository.deleteBookmark(bookmarkId, memberId);
        log.info("Deleted board bookmark {} of member {}", bookmarkId, memberId);
    }

    public void deleteBookmarkByBoard(int memberId, int partnerId, UUID remoteBoardUid) {
        repository.deleteBookmarkByBoard(memberId, partnerId, remoteBoardUid);
        log.info(
                "Deleted board bookmark for member {} (partner {}, remote board {})",
                memberId,
                partnerId,
                remoteBoardUid);
    }

    public List<FederationBoardBookmark> findBookmarks(int memberId) {
        return repository.findBookmarks(memberId);
    }

    public void updateBookmarkName(int partnerId, UUID remoteBoardUid, String newName, String newShortKey) {
        repository.updateBookmarkName(partnerId, remoteBoardUid, newName, newShortKey);
        log.info("Updated bookmark name for partner {} (remote board {})", partnerId, remoteBoardUid);
    }

    public void deleteBookmarksByBoard(int partnerId, UUID remoteBoardUid) {
        repository.deleteBookmarksByBoard(partnerId, remoteBoardUid);
        log.info("Deleted bookmarks for partner {} (remote board {})", partnerId, remoteBoardUid);
    }

    public void updateBookmarkShareMode(int partnerId, UUID remoteBoardUid, BoardShareMode shareMode) {
        repository.updateBookmarkShareMode(partnerId, remoteBoardUid, shareMode);
        log.info(
                "Updated bookmark share mode for partner {} (remote board {}) to {}",
                partnerId,
                remoteBoardUid,
                shareMode);
    }

    public void setLocalViewOverride(int partnerId, UUID remoteBoardUid, AccessData access) {
        repository.setLocalViewOverride(partnerId, remoteBoardUid, access);
        log.info("Updated local view override for partner {} (remote board {})", partnerId, remoteBoardUid);
    }

    // -- Local Overrides --

    public void setLocalEditOverride(int partnerId, UUID remoteBoardUid, AccessData access) {
        repository.setLocalEditOverride(partnerId, remoteBoardUid, access);
        log.info("Updated local edit override for partner {} (remote board {})", partnerId, remoteBoardUid);
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

    public record PartnerShareConfig(int partnerId, BoardShareMode shareMode, StationUserType requiredUserType) {
        public PartnerShareConfig(int partnerId, BoardShareMode shareMode) {
            this(partnerId, shareMode, StationUserType.MEMBER);
        }
    }
}
