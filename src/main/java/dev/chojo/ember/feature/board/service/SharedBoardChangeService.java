/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.FederationBoardShareTarget;
import dev.chojo.ember.feature.board.service.FederatedBoardService.PartnerShareConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Applies the board changes that partner stations holding the board must hear about, and passes
 * them on once the change is stored: a rename, a share that ends for a partner, and a partner's
 * share mode changing.
 *
 * <p>Every partner is told through its webhook endpoint, wherever it runs, and applies the change to
 * the saved boards of its members itself.
 *
 * <p>Partners are told only after the local change is written, and telling them can never undo or
 * fail it: delivery never raises, and anything going wrong while passing a change on is logged and
 * dropped.
 */
@Singleton
public class SharedBoardChangeService {
    private static final Logger log = LoggerFactory.getLogger(SharedBoardChangeService.class);

    private final BoardService boardService;
    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardNotificationService notifications;

    @Inject
    public SharedBoardChangeService(
            BoardService boardService,
            FederatedBoardService federatedBoardService,
            FederatedBoardNotificationService notifications) {
        this.boardService = boardService;
        this.federatedBoardService = federatedBoardService;
        this.notifications = notifications;
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
                        boardId,
                        target.partnerId(),
                        () -> notifications.notifyBoardRenamed(target.partnerId(), boardId, name, board.shortKey()));
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

        before.forEach((partnerId, mode) -> {
            var now = after.get(partnerId);
            if (now == null) {
                passOn(boardId, partnerId, () -> notifications.notifyBoardUnshared(partnerId, boardId));
            } else if (now != mode) {
                passOn(boardId, partnerId, () -> notifications.notifyShareModeChanged(partnerId, boardId, now));
            }
        });
    }

    private void passOn(int boardId, int partnerId, Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException e) {
            log.error("Could not pass a change on board {} on to partner {}", boardId, partnerId, e);
        }
    }

    private static Map<Integer, BoardShareMode> modesByPartner(List<FederationBoardShareTarget> targets) {
        return targets.stream()
                .collect(Collectors.toMap(
                        FederationBoardShareTarget::partnerId, FederationBoardShareTarget::shareMode, (a, b) -> b));
    }
}
