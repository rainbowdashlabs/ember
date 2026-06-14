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

import java.util.UUID;

/**
 * Sends webhook notifications to federated board partners.
 */
@Singleton
public class FederatedBoardNotificationService {
    private final FederationWebhookService webhookService;
    private final FederatedBoardService federatedBoardService;

    @Inject
    public FederatedBoardNotificationService(
            FederationWebhookService webhookService, FederatedBoardService federatedBoardService) {
        this.webhookService = webhookService;
        this.federatedBoardService = federatedBoardService;
    }

    public void notifyMention(int partnerId, int boardId, int ticketId, String ticketKey, UUID remoteMemberId) {
        if (!isFullMode(boardId, partnerId)) return;
        webhookService.fireEventToPartner(
                partnerId,
                WebhookEvent.BOARD_MENTION,
                new TicketMemberPayload(boardId, ticketId, ticketKey, remoteMemberId));
    }

    public void notifyAssignment(int partnerId, int boardId, int ticketId, String ticketKey, UUID remoteMemberId) {
        if (!isFullMode(boardId, partnerId)) return;
        webhookService.fireEventToPartner(
                partnerId,
                WebhookEvent.BOARD_ASSIGNMENT,
                new TicketMemberPayload(boardId, ticketId, ticketKey, remoteMemberId));
    }

    public void notifyUnassignment(int partnerId, int boardId, int ticketId, String ticketKey, UUID remoteMemberId) {
        if (!isFullMode(boardId, partnerId)) return;
        webhookService.fireEventToPartner(
                partnerId,
                WebhookEvent.BOARD_UNASSIGNMENT,
                new TicketMemberPayload(boardId, ticketId, ticketKey, remoteMemberId));
    }

    public void notifyBoardRenamed(int boardId, String newName, String newShortKey) {
        var targets = federatedBoardService.findShareTargets(boardId);
        for (var target : targets) {
            webhookService.fireEventToPartner(
                    target.partnerId(),
                    WebhookEvent.BOARD_RENAMED,
                    new BoardRenamedPayload(boardId, newName, newShortKey));
        }
    }

    public void notifyBoardUnshared(int boardId) {
        var targets = federatedBoardService.findShareTargets(boardId);
        for (var target : targets) {
            webhookService.fireEventToPartner(
                    target.partnerId(), WebhookEvent.BOARD_UNSHARED, new BoardIdPayload(boardId));
        }
    }

    public void notifyShareModeChanged(int partnerId, int boardId, BoardShareMode newMode) {
        webhookService.fireEventToPartner(
                partnerId, WebhookEvent.BOARD_SHARE_MODE_CHANGED, new ShareModeChangedPayload(boardId, newMode));
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
