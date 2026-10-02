/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.repository.BoardRepository;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteBoardRenamedWebhook;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteBoardUnsharedWebhook;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteShareModeChangedWebhook;
import dev.chojo.ember.feature.board.route.RemoteBoardWebhookRoutes;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Board notifications between partners: tells the partners a board is shared with that it was
 * renamed, stopped being shared or changed its share mode, and takes in the same notifications from
 * the partners sharing boards with this station, keeping the saved boards of its members current.
 *
 * <p>Every notification names the board by its UUID, the only board identity the partner knows.
 */
@Singleton
public class FederatedBoardNotificationService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(FederatedBoardNotificationService.class);

    private final FederationTransport transport;
    private final FederationRepository federationRepository;
    private final FederatedBoardService federatedBoardService;
    private final BoardRepository boardRepository;

    @Inject
    public FederatedBoardNotificationService(
            FederationTransport transport,
            FederationRepository federationRepository,
            FederatedBoardService federatedBoardService,
            BoardRepository boardRepository) {
        this.transport = transport;
        this.federationRepository = federationRepository;
        this.federatedBoardService = federatedBoardService;
        this.boardRepository = boardRepository;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        for (var ignored : List.of(
                RemoteBoardWebhookRoutes.TICKET_CHANGED,
                RemoteBoardWebhookRoutes.MENTION,
                RemoteBoardWebhookRoutes.ASSIGNMENT,
                RemoteBoardWebhookRoutes.UNASSIGNMENT)) {
            endpoints.serve(ignored, (partner, params, body) -> received(ignored));
        }
        endpoints.<RemoteBoardRenamedWebhook, Void>serve(
                RemoteBoardWebhookRoutes.BOARD_RENAMED, (partner, params, body) -> {
                    federatedBoardService.updateBookmarkName(
                            partner.partnerId(), body.boardUid(), body.newName(), body.newShortKey());
                    return null;
                });
        endpoints.<RemoteBoardUnsharedWebhook, Void>serve(
                RemoteBoardWebhookRoutes.BOARD_UNSHARED, (partner, params, body) -> {
                    federatedBoardService.deleteBookmarksByBoard(partner.partnerId(), body.boardUid());
                    return null;
                });
        endpoints.<RemoteShareModeChangedWebhook, Void>serve(
                RemoteBoardWebhookRoutes.SHARE_MODE_CHANGED, (partner, params, body) -> {
                    federatedBoardService.updateBookmarkShareMode(
                            partner.partnerId(), body.boardUid(), body.shareMode());
                    return null;
                });
    }

    /**
     * Tells one partner the board was renamed.
     *
     * @param partnerId   the partner the board is shared with
     * @param boardId     the renamed board
     * @param newName     its new name
     * @param newShortKey its short key
     */
    public void notifyBoardRenamed(int partnerId, int boardId, String newName, String newShortKey) {
        boardUid(boardId)
                .ifPresent(boardUid -> send(
                        partnerId,
                        RemoteBoardWebhookRoutes.BOARD_RENAMED.at(),
                        new RemoteBoardRenamedWebhook(boardUid, newName, newShortKey)));
        log.info("Notified partner {} of rename on board {}", partnerId, boardId);
    }

    /**
     * Tells one partner that a board is no longer shared with it. Called once the share target is
     * gone, so the partner is named explicitly rather than read from the board's share targets.
     *
     * @param partnerId the partner the board was shared with
     * @param boardId   the board that stopped being shared
     */
    public void notifyBoardUnshared(int partnerId, int boardId) {
        boardUid(boardId)
                .ifPresent(boardUid -> send(
                        partnerId,
                        RemoteBoardWebhookRoutes.BOARD_UNSHARED.at(),
                        new RemoteBoardUnsharedWebhook(boardUid)));
        log.info("Notified partner {} of unshare on board {}", partnerId, boardId);
    }

    /**
     * Tells one partner how it may use the board from now on.
     *
     * @param partnerId the partner the board is shared with
     * @param boardId   the board
     * @param newMode   the share mode it has now
     */
    public void notifyShareModeChanged(int partnerId, int boardId, BoardShareMode newMode) {
        boardUid(boardId)
                .ifPresent(boardUid -> send(
                        partnerId,
                        RemoteBoardWebhookRoutes.SHARE_MODE_CHANGED.at(),
                        new RemoteShareModeChangedWebhook(boardUid, newMode)));
        log.info("Notified partner {} of share mode change on board {} to {}", partnerId, boardId, newMode);
    }

    private @Nullable Void received(FederationEndpoint webhook) {
        log.info("Received board webhook {}", webhook.path());
        return null;
    }

    private void send(int partnerId, FederationRequest webhook, Object body) {
        federationRepository.findPartnerById(partnerId).ifPresent(partner -> transport.notify(partner, webhook, body));
    }

    private Optional<UUID> boardUid(int boardId) {
        var uid = boardRepository.findById(boardId).map(Board::uid);
        if (uid.isEmpty()) {
            log.warn("Skipping webhook for board {}: the board no longer exists", boardId);
        }
        return uid;
    }
}
