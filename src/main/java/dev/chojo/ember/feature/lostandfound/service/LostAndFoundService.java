/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.service;

import dev.chojo.ember.api.auth.StationPermission;
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
    private final LostAndFoundImageService imageService;

    @Inject
    public LostAndFoundService(
            LostAndFoundRepository repository,
            NotificationService notificationService,
            LostAndFoundImageService imageService) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.imageService = imageService;
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
     * Finds items that are unclaimed or claimed for one of the given members.
     *
     * @param stationId the station to query
     * @param memberIds the members whose claimed items should also be included
     * @return matching items ordered by creation date descending
     */
    public List<LostAndFoundItem> findUnclaimedOrClaimedBy(int stationId, List<Integer> memberIds) {
        return repository.findUnclaimedOrClaimedBy(stationId, memberIds);
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
                        linkTo(item.id())),
                createdBy);
        log.info("Created lost-and-found item {} at station {} by member {}", item.id(), stationId, createdBy);
        return item;
    }

    /**
     * Claims a lost and found item for a member. On success, notifies the members who look after
     * the lost and found and withdraws the "new item" notification for this one item.
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
                    StationPermission.LOST_AND_FOUND_MANAGER.name(),
                    NotificationType.LOST_AND_FOUND_CLAIMED,
                    NotificationData.of(new NotificationParams.LostAndFoundClaimed(claimerName, desc), linkTo(id)),
                    claimedBy);
            notificationService.deleteByTypeAndLink(NotificationType.LOST_AND_FOUND_NEW, linkTo(id));
            log.info("Claimed lost-and-found item {} by member {} at station {}", id, claimedBy, stationId);
        } else {
            log.warn("Failed to claim lost-and-found item {} by member {} at station {}", id, claimedBy, stationId);
        }
        return success;
    }

    /**
     * Takes a claim back off an item, so it stands unclaimed again and anybody may claim it. The
     * "claimed" notification is withdrawn with it, because it is no longer true.
     *
     * @param id the item ID
     * @return true if a claim was actually taken back
     */
    public boolean release(int id) {
        boolean released = repository.release(id);
        if (released) {
            notificationService.deleteByTypeAndLink(NotificationType.LOST_AND_FOUND_CLAIMED, linkTo(id));
            log.info("Released the claim on lost-and-found item {}", id);
        } else {
            log.warn("Failed to release lost-and-found item {} (not found or unclaimed)", id);
        }
        return released;
    }

    /**
     * Deletes a lost and found item together with everything that only existed for it: its image
     * and the notifications pointing at it. One way out for both the handover and the removal, so
     * neither can leave the other's leftovers behind.
     *
     * @param stationId the station the item belongs to
     * @param id        the item ID
     * @return true if the item was deleted
     */
    public boolean delete(int stationId, int id) {
        boolean deleted = repository.delete(id);
        if (deleted) {
            imageService.delete(stationId, id);
            notificationService.deleteByTypeAndLink(NotificationType.LOST_AND_FOUND_NEW, linkTo(id));
            notificationService.deleteByTypeAndLink(NotificationType.LOST_AND_FOUND_CLAIMED, linkTo(id));
            log.info("Deleted lost-and-found item {}", id);
        } else {
            log.warn("Failed to delete lost-and-found item {} (not found)", id);
        }
        return deleted;
    }

    private static NotificationLink linkTo(int id) {
        return new NotificationLink("lost-and-found", Map.of("id", id));
    }
}
