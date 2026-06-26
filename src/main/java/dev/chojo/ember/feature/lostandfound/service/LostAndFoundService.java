/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.service;

import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import dev.chojo.ember.feature.lostandfound.repository.LostAndFoundRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing lost and found items. Handles creation, claiming, image uploads,
 * and deletion, and sends notifications to station members when items are found or claimed.
 */
@Singleton
public class LostAndFoundService {
    private static final Logger log = LoggerFactory.getLogger(LostAndFoundService.class);
    private final LostAndFoundRepository repository;
    private final NotificationService notificationService;

    @Inject
    public LostAndFoundService(LostAndFoundRepository repository, NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    /**
     * Finds all lost and found items for a station, including claimed items.
     *
     * @param stationId the station to query
     * @return all items ordered by creation date descending
     */
    public List<LostAndFoundItem> findByStation(int stationId) {
        return repository.findByStation(stationId);
    }

    /**
     * Finds only unclaimed items for a station.
     *
     * @param stationId the station to query
     * @return unclaimed items ordered by creation date descending
     */
    public List<LostAndFoundItem> findUnclaimedByStation(int stationId) {
        return repository.findUnclaimedByStation(stationId);
    }

    /**
     * Finds items that are unclaimed or claimed by a specific member.
     *
     * @param stationId the station to query
     * @param memberId  the member whose claimed items should also be included
     * @return matching items ordered by creation date descending
     */
    public List<LostAndFoundItem> findUnclaimedOrClaimedBy(int stationId, int memberId) {
        return repository.findUnclaimedOrClaimedBy(stationId, memberId);
    }

    /**
     * Finds a lost and found item by its ID.
     *
     * @param id the item ID
     * @return the item, or empty if not found
     */
    public Optional<LostAndFoundItem> findById(int id) {
        return repository.findById(id);
    }

    /**
     * Creates a new lost and found item and notifies all station members.
     *
     * @param stationId   the station where the item was found
     * @param description a description of the item
     * @param foundAt     the date the item was found
     * @param createdBy   the member ID of the person reporting the item
     * @return the created item
     */
    public LostAndFoundItem create(int stationId, String description, LocalDate foundAt, int createdBy) {
        var item = repository.create(stationId, description, foundAt, createdBy);
        notificationService.notifyStation(
                stationId,
                NotificationType.LOST_AND_FOUND_NEW,
                NotificationData.of(
                        new NotificationParams.LostAndFoundNew(description != null ? description : ""),
                        new NotificationLink("lost-and-found", Map.of("id", item.id()))),
                createdBy);
        log.info("Created lost-and-found item {} at station {} by member {}", item.id(), stationId, createdBy);
        return item;
    }

    /**
     * Claims a lost and found item for a member. On success, notifies managers with the
     * LOST_AND_FOUND_MANAGER role and removes the "new item" notification.
     *
     * @param id          the item ID to claim
     * @param claimedBy   the member ID claiming the item
     * @param stationId   the station ID for notification routing
     * @param claimerName the display name of the claimer for the notification
     * @return true if the item was successfully claimed
     */
    public boolean claim(int id, int claimedBy, int stationId, String claimerName) {
        var item = repository.findById(id).orElse(null);
        boolean success = repository.claim(id, claimedBy);
        if (success && item != null) {
            String desc = item.description() != null ? item.description() : "";
            notificationService.notifyMembersWithRole(
                    stationId,
                    "LOST_AND_FOUND_MANAGER",
                    NotificationType.LOST_AND_FOUND_CLAIMED,
                    NotificationData.of(
                            new NotificationParams.LostAndFoundClaimed(claimerName, desc),
                            new NotificationLink("lost-and-found", Map.of("id", id))),
                    claimedBy);
            notificationService.deleteByTypeContaining(
                    NotificationType.LOST_AND_FOUND_NEW,
                    NotificationData.of(new NotificationParams.LostAndFoundNew(desc))
                            .toJson());
            log.info("Claimed lost-and-found item {} by member {} at station {}", id, claimedBy, stationId);
        } else {
            log.warn("Failed to claim lost-and-found item {} by member {} at station {}", id, claimedBy, stationId);
        }
        return success;
    }

    /**
     * Deletes a lost and found item.
     *
     * @param id the item ID
     * @return true if the item was deleted
     */
    public boolean delete(int id) {
        boolean deleted = repository.delete(id);
        if (deleted) {
            log.info("Deleted lost-and-found item {}", id);
        } else {
            log.warn("Failed to delete lost-and-found item {} (not found)", id);
        }
        return deleted;
    }
}
