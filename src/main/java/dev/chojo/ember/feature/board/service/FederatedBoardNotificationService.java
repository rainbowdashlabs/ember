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

import java.util.Map;
import java.util.UUID;

/**
 * Handles webhook notifications to federated partners for board changes.
 * Only sends to FULL mode partners (READ_ONLY partners get no webhooks).
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

    /**
     * Notifies federated watchers of a ticket change. Only sends to FULL mode partners.
     * Since the satellite table was removed, watchers are now in the unified board_ticket_watcher table
     * and notifications are handled via the domain event system.
     */
    public void notifyFederatedWatchers(int ticketId, int boardId, String ticketKey, String changeDescription) {
        // No-op: federated watchers are now stored inline in board_ticket_watcher
        // and notified via the standard domain event / notification system.
    }

    /**
     * Notifies a partner about a mention in a board comment.
     */
    public void notifyMention(int partnerId, int boardId, int ticketId, String ticketKey, UUID remoteMemberId) {
        if (!isFullMode(boardId, partnerId)) return;
        webhookService.fireEventToPartner(
                partnerId,
                WebhookEvent.BOARD_MENTION,
                Map.of(
                        "boardId", boardId,
                        "ticketId", ticketId,
                        "ticketKey", ticketKey,
                        "remoteMemberId", remoteMemberId));
    }

    /**
     * Notifies a partner that a remote member was assigned to a ticket.
     */
    public void notifyAssignment(int partnerId, int boardId, int ticketId, String ticketKey, UUID remoteMemberId) {
        if (!isFullMode(boardId, partnerId)) return;
        webhookService.fireEventToPartner(
                partnerId,
                WebhookEvent.BOARD_ASSIGNMENT,
                Map.of(
                        "boardId", boardId,
                        "ticketId", ticketId,
                        "ticketKey", ticketKey,
                        "remoteMemberId", remoteMemberId));
    }

    /**
     * Notifies a partner that a remote member was unassigned from a ticket.
     */
    public void notifyUnassignment(int partnerId, int boardId, int ticketId, String ticketKey, UUID remoteMemberId) {
        if (!isFullMode(boardId, partnerId)) return;
        webhookService.fireEventToPartner(
                partnerId,
                WebhookEvent.BOARD_UNASSIGNMENT,
                Map.of(
                        "boardId", boardId,
                        "ticketId", ticketId,
                        "ticketKey", ticketKey,
                        "remoteMemberId", remoteMemberId));
    }

    /**
     * Notifies all partners that a board was renamed.
     * Updates cached bookmark names on the partner side.
     */
    public void notifyBoardRenamed(int boardId, String newName, String newShortKey) {
        var targets = federatedBoardService.findShareTargets(boardId);
        for (var target : targets) {
            webhookService.fireEventToPartner(
                    target.partnerId(),
                    WebhookEvent.BOARD_RENAMED,
                    Map.of(
                            "boardId", boardId,
                            "newName", newName,
                            "newShortKey", newShortKey));
        }
    }

    /**
     * Notifies all partners that a board was unshared.
     * Deletes bookmarks on the partner side.
     */
    public void notifyBoardUnshared(int boardId) {
        var targets = federatedBoardService.findShareTargets(boardId);
        for (var target : targets) {
            webhookService.fireEventToPartner(
                    target.partnerId(), WebhookEvent.BOARD_UNSHARED, Map.of("boardId", boardId));
        }
    }

    /**
     * Notifies a specific partner that the share mode changed.
     */
    public void notifyShareModeChanged(int partnerId, int boardId, BoardShareMode newMode) {
        webhookService.fireEventToPartner(
                partnerId,
                WebhookEvent.BOARD_SHARE_MODE_CHANGED,
                Map.of("boardId", boardId, "shareMode", newMode.name()));
    }

    private boolean isFullMode(int boardId, int partnerId) {
        return federatedBoardService
                .getShareMode(boardId, partnerId)
                .map(mode -> mode == BoardShareMode.FULL)
                .orElse(false);
    }
}
