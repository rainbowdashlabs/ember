/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.federation.service.FederationWebhookService;
import dev.chojo.ember.feature.federation.service.FederationWebhookService.WebhookEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Sends webhook notifications to federated board partners.
 */
@Singleton
public class FederatedBoardNotificationService {
    private static final Logger log = LoggerFactory.getLogger(FederatedBoardNotificationService.class);

    private final FederationWebhookService webhookService;
    private final FederatedBoardService federatedBoardService;

    @Inject
    public FederatedBoardNotificationService(
            FederationWebhookService webhookService, FederatedBoardService federatedBoardService) {
        this.webhookService = webhookService;
        this.federatedBoardService = federatedBoardService;
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
        webhookService.fireEventToPartner(
                partnerId,
                WebhookEvent.BOARD_MENTION,
                new TicketMemberPayload(boardId, ticketId, ticketKey, remoteMemberId));
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
        webhookService.fireEventToPartner(
                partnerId,
                WebhookEvent.BOARD_ASSIGNMENT,
                new TicketMemberPayload(boardId, ticketId, ticketKey, remoteMemberId));
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
        webhookService.fireEventToPartner(
                partnerId,
                WebhookEvent.BOARD_UNASSIGNMENT,
                new TicketMemberPayload(boardId, ticketId, ticketKey, remoteMemberId));
        log.info("Notified partner {} of unassignment on ticket {} ({})", partnerId, ticketId, ticketKey);
    }

    public void notifyBoardRenamed(int boardId, String newName, String newShortKey) {
        var targets = federatedBoardService.findShareTargets(boardId);
        for (var target : targets) {
            webhookService.fireEventToPartner(
                    target.partnerId(),
                    WebhookEvent.BOARD_RENAMED,
                    new BoardRenamedPayload(boardId, newName, newShortKey));
        }
        log.info("Notified {} partner(s) of rename on board {}", targets.size(), boardId);
    }

    public void notifyBoardUnshared(int boardId) {
        var targets = federatedBoardService.findShareTargets(boardId);
        for (var target : targets) {
            webhookService.fireEventToPartner(
                    target.partnerId(), WebhookEvent.BOARD_UNSHARED, new BoardIdPayload(boardId));
        }
        log.info("Notified {} partner(s) of unshare on board {}", targets.size(), boardId);
    }

    public void notifyShareModeChanged(int partnerId, int boardId, BoardShareMode newMode) {
        webhookService.fireEventToPartner(
                partnerId, WebhookEvent.BOARD_SHARE_MODE_CHANGED, new ShareModeChangedPayload(boardId, newMode));
        log.info("Notified partner {} of share mode change on board {} to {}", partnerId, boardId, newMode);
    }

    private boolean isFullMode(int boardId, int partnerId) {
        return federatedBoardService
                .getShareMode(boardId, partnerId)
                .map(mode -> mode == BoardShareMode.FULL)
                .orElse(false);
    }

    public record TicketMemberPayload(int boardId, int ticketId, String ticketKey, UUID remoteMemberId) {}

    public record BoardRenamedPayload(int boardId, String newName, String newShortKey) {}

    public record BoardIdPayload(int boardId) {}

    public record ShareModeChangedPayload(int boardId, BoardShareMode shareMode) {}
}
