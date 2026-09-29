/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.FederationBoardShareTarget;
import dev.chojo.ember.feature.board.service.FederatedBoardService.PartnerShareConfig;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Applies the board changes that partner stations holding the board must hear about, and passes
 * them on once the change is stored: a rename, a share that ends for a partner, and a partner's
 * share mode changing.
 *
 * <p>A partner on another instance is told through its webhook endpoint. A partner on this instance
 * gets the same change applied to its saved boards directly, through the same
 * {@link FederatedBoardService} calls the webhook endpoints make, keyed by the partner row that
 * station holds for this one.
 *
 * <p>Partners are told only after the local change is written, and telling them can never undo or
 * fail it: remote delivery runs in the background with its own retries, and anything going wrong
 * while passing a change on is logged and dropped.
 */
@Singleton
public class SharedBoardChangeService {
    private static final Logger log = LoggerFactory.getLogger(SharedBoardChangeService.class);

    private final BoardService boardService;
    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardNotificationService notifications;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;

    @Inject
    public SharedBoardChangeService(
            BoardService boardService,
            FederatedBoardService federatedBoardService,
            FederatedBoardNotificationService notifications,
            FederationRepository federationRepository,
            StationRepository stationRepository) {
        this.boardService = boardService;
        this.federatedBoardService = federatedBoardService;
        this.notifications = notifications;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
    }

    /**
     * Updates a board's settings and, when its name changed, passes the new name on to every
     * partner it is shared with.
     *
     * @return whether the board was updated
     */
    public boolean updateBoard(int boardId, String name, String description, int hideDoneAfterDays) {
        var before = boardService.findById(boardId);
        boolean updated = boardService.update(boardId, name, description, hideDoneAfterDays);
        if (!updated) return false;
        before.filter(board -> !board.name().equals(name)).ifPresent(board -> {
            for (var target : federatedBoardService.findShareTargets(boardId)) {
                passOn(
                        target.partnerId(),
                        board,
                        () -> notifications.notifyBoardRenamed(target.partnerId(), boardId, name, board.shortKey()),
                        (counterpartId, boardUid) -> federatedBoardService.updateBookmarkName(
                                counterpartId, boardUid, name, board.shortKey()));
            }
        });
        return true;
    }

    /**
     * Replaces who a board is shared with and who of them may edit it, then passes the change on to
     * every partner that lost the board and every partner whose share mode changed.
     *
     * @param boardId       the board being configured
     * @param configs       the partners to share with from now on; empty ends the share
     * @param editUserTypes the partner user types allowed to edit
     */
    public void configureSharing(int boardId, List<PartnerShareConfig> configs, List<StationUserType> editUserTypes) {
        var before = modesByPartner(federatedBoardService.findShareTargets(boardId));
        if (configs.isEmpty()) {
            federatedBoardService.unshareBoard(boardId);
        } else {
            federatedBoardService.shareBoard(boardId, configs);
        }
        federatedBoardService.setFederatedEditUserTypes(boardId, editUserTypes);
        var after = modesByPartner(federatedBoardService.findShareTargets(boardId));
        var board = boardService.findById(boardId);
        if (board.isEmpty()) return;

        before.forEach((partnerId, mode) -> {
            var now = after.get(partnerId);
            if (now == null) {
                passOn(
                        partnerId,
                        board.get(),
                        () -> notifications.notifyBoardUnshared(partnerId, boardId),
                        federatedBoardService::deleteBookmarksByBoard);
            } else if (now != mode) {
                passOn(
                        partnerId,
                        board.get(),
                        () -> notifications.notifyShareModeChanged(partnerId, boardId, now),
                        (counterpartId, boardUid) ->
                                federatedBoardService.updateBookmarkShareMode(counterpartId, boardUid, now));
            }
        });
    }

    /**
     * Passes one change on to one partner: a remote partner through its webhook, a partner on this
     * instance by applying the change against the partner row its station holds for this one.
     *
     * @param remote the webhook notification for a remote partner
     * @param local  the change to apply for a local partner, given that station's partner row id
     *               and the board's UUID, exactly as the webhook endpoint would receive them
     */
    private void passOn(int partnerId, Board board, Runnable remote, BiConsumer<Integer, UUID> local) {
        try {
            var partner = federationRepository.findPartnerById(partnerId);
            if (partner.isEmpty()) return;
            if (partner.get().isRemote()) {
                remote.run();
                return;
            }
            counterpartOf(partner.get(), board).ifPresent(counterpart -> local.accept(counterpart.id(), board.uid()));
        } catch (RuntimeException e) {
            log.error("Could not pass a change on board {} on to partner {}", board.id(), partnerId, e);
        }
    }

    /**
     * The partner row the partner's station holds for the board's station, which is the row the
     * partner's saved boards are filed under. Empty when that side is missing or not active.
     */
    private Optional<FederationPartner> counterpartOf(FederationPartner partner, Board board) {
        var ownUid = stationRepository.resolveUid(board.stationId());
        if (ownUid == null || partner.status() != FederationPartner.FederationStatus.ACTIVE) {
            return Optional.empty();
        }
        return federationRepository.findPartnerByLocalAndRemoteStationUid(partner.partnerStationId(), ownUid);
    }

    private static Map<Integer, BoardShareMode> modesByPartner(List<FederationBoardShareTarget> targets) {
        return targets.stream()
                .collect(Collectors.toMap(
                        FederationBoardShareTarget::partnerId, FederationBoardShareTarget::shareMode, (a, b) -> b));
    }
}
