/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.repository.BoardRepository;
import dev.chojo.ember.feature.board.route.RemoteBoardWebhookRoutes;
import dev.chojo.ember.feature.federation.service.FederationWebhookService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Sends webhook notifications to federated board partners.
 *
 * <p>Every notification goes to the matching endpoint of {@link RemoteBoardWebhookRoutes} on the
 * partner, and names the board by its UUID, the only board identity the partner knows.
 */
@Singleton
public class FederatedBoardNotificationService {
    private static final Logger log = LoggerFactory.getLogger(FederatedBoardNotificationService.class);

    private final FederationWebhookService webhookService;
    private final FederatedBoardService federatedBoardService;
    private final BoardRepository boardRepository;

    @Inject
    public FederatedBoardNotificationService(
            FederationWebhookService webhookService,
            FederatedBoardService federatedBoardService,
            BoardRepository boardRepository) {
        this.webhookService = webhookService;
        this.federatedBoardService = federatedBoardService;
        this.boardRepository = boardRepository;
    }

    public void notifyMention(int partnerId, int boardId, int ticketId, String ticketKey, UUID remoteMemberId) {
        if (!isFullMode(boardId, partnerId)) {
            log.warn(
                    "Skipping mention webhook for ticket {} on board {} (partner {} not in FULL mode)",
                    ticketId,
                    boardId,
                    partnerId);
            return;
        }
        boardUid(boardId)
                .ifPresent(boardUid -> webhookService.notifyPartner(
                        partnerId,
                        RemoteBoardWebhookRoutes.MENTION.at(),
                        new TicketMemberPayload(boardUid, ticketKey, remoteMemberId)));
        log.info("Notified partner {} of mention on ticket {} ({})", partnerId, ticketId, ticketKey);
    }

    public void notifyAssignment(int partnerId, int boardId, int ticketId, String ticketKey, UUID remoteMemberId) {
        if (!isFullMode(boardId, partnerId)) {
            log.warn(
                    "Skipping assignment webhook for ticket {} on board {} (partner {} not in FULL mode)",
                    ticketId,
                    boardId,
                    partnerId);
            return;
        }
        boardUid(boardId)
                .ifPresent(boardUid -> webhookService.notifyPartner(
                        partnerId,
                        RemoteBoardWebhookRoutes.ASSIGNMENT.at(),
                        new TicketMemberPayload(boardUid, ticketKey, remoteMemberId)));
        log.info("Notified partner {} of assignment on ticket {} ({})", partnerId, ticketId, ticketKey);
    }

    public void notifyUnassignment(int partnerId, int boardId, int ticketId, String ticketKey, UUID remoteMemberId) {
        if (!isFullMode(boardId, partnerId)) {
            log.warn(
                    "Skipping unassignment webhook for ticket {} on board {} (partner {} not in FULL mode)",
                    ticketId,
                    boardId,
                    partnerId);
            return;
        }
        boardUid(boardId)
                .ifPresent(boardUid -> webhookService.notifyPartner(
                        partnerId,
                        RemoteBoardWebhookRoutes.UNASSIGNMENT.at(),
                        new TicketMemberPayload(boardUid, ticketKey, remoteMemberId)));
        log.info("Notified partner {} of unassignment on ticket {} ({})", partnerId, ticketId, ticketKey);
    }

    public void notifyBoardRenamed(int boardId, String newName, String newShortKey) {
        var targets = federatedBoardService.findShareTargets(boardId);
        boardUid(boardId).ifPresent(boardUid -> {
            for (var target : targets) {
                webhookService.notifyPartner(
                        target.partnerId(),
                        RemoteBoardWebhookRoutes.BOARD_RENAMED.at(),
                        new BoardRenamedPayload(boardUid, newName, newShortKey));
            }
        });
        log.info("Notified {} partner(s) of rename on board {}", targets.size(), boardId);
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
                .ifPresent(boardUid -> webhookService.notifyPartner(
                        partnerId, RemoteBoardWebhookRoutes.BOARD_UNSHARED.at(), new BoardUnsharedPayload(boardUid)));
        log.info("Notified partner {} of unshare on board {}", partnerId, boardId);
    }

    public void notifyShareModeChanged(int partnerId, int boardId, BoardShareMode newMode) {
        boardUid(boardId)
                .ifPresent(boardUid -> webhookService.notifyPartner(
                        partnerId,
                        RemoteBoardWebhookRoutes.SHARE_MODE_CHANGED.at(),
                        new ShareModeChangedPayload(boardUid, newMode)));
        log.info("Notified partner {} of share mode change on board {} to {}", partnerId, boardId, newMode);
    }

    private boolean isFullMode(int boardId, int partnerId) {
        return federatedBoardService
                .getShareMode(boardId, partnerId)
                .map(mode -> mode == BoardShareMode.FULL)
                .orElse(false);
    }

    private Optional<UUID> boardUid(int boardId) {
        var uid = boardRepository.findById(boardId).map(Board::uid);
        if (uid.isEmpty()) {
            log.warn("Skipping webhook for board {}: the board no longer exists", boardId);
        }
        return uid;
    }

    /**
     * Body of the mention and (un)assignment notifications.
     *
     * @param boardUid       the board the ticket lives on
     * @param ticketKey      the ticket's display key, such as {@code FTB-1}
     * @param remoteMemberId the partner's member the notification is about
     */
    public record TicketMemberPayload(UUID boardUid, String ticketKey, UUID remoteMemberId) {}

    /** Body of the rename notification, shaped like the partner's {@code board-renamed} request. */
    public record BoardRenamedPayload(UUID boardUid, String newName, String newShortKey) {}

    /** Body of the unshare notification, shaped like the partner's {@code board-unshared} request. */
    public record BoardUnsharedPayload(UUID boardUid) {}

    /** Body of the share mode notification, shaped like the partner's {@code share-mode-changed} request. */
    public record ShareModeChangedPayload(UUID boardUid, BoardShareMode shareMode) {}
}
