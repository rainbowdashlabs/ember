/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What a feed entry says about an inventory: the movement or procurement the notification is about,
 * and from the inventory itself whose it is.
 */
@Singleton
public class InventoryFeedDetails implements FeedDetailsContributor {
    private final InventoryService inventoryService;

    @Inject
    public InventoryFeedDetails(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Adds the movement or procurement. A step is shown in its own words, because a station names
     * its chain of steps itself.
     */
    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        switch (params) {
            case NotificationParams.MovementRaised(String memberName, String inventoryName, String reason) -> {
                details.putIfPresent(details.label("by"), memberName);
                details.putIfPresent(details.label("inventory", "Inventory"), inventoryName);
                details.putIfPresent(details.label("reason"), reason);
                addInventoryType(details);
            }
            case NotificationParams.MovementMoved(String stepLabel, String inventoryName, StepActor _) -> {
                details.putIfPresent(details.label("status", "Status"), stepLabel);
                details.putIfPresent(details.label("inventory", "Inventory"), inventoryName);
                addInventoryType(details);
            }
            case NotificationParams.ProcurementRequested(String inventoryName) -> {
                details.putIfPresent(details.label("inventory", "Inventory"), inventoryName);
                addInventoryType(details);
            }
            case NotificationParams.ProcurementFulfilled(String inventoryName) -> {
                details.putIfPresent(details.label("inventory", "Inventory"), inventoryName);
                addInventoryType(details);
            }
            default -> {}
        }
    }

    @Override
    public String author(NotificationParams params) {
        return params instanceof NotificationParams.MovementRaised p ? p.memberName() : null;
    }

    /**
     * Adds whose the linked inventory is (the organisation's, its members' or both), so a manager
     * sees its nature without opening the app. A missing link, a deleted inventory or a failed lookup
     * adds nothing.
     */
    private void addInventoryType(FeedDetails details) {
        Integer inventoryId = details.linkId();
        if (inventoryId == null) return;
        try {
            var inventory = inventoryService.findById(inventoryId).orElse(null);
            if (inventory == null || inventory.inventoryType() == null) return;
            details.put(
                    details.label("type", "Type"),
                    details.localized("inventoryType", inventory.inventoryType().name(), null));
        } catch (Exception ignored) {
        }
    }
}
