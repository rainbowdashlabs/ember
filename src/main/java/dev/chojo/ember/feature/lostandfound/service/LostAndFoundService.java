/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.service;

import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import dev.chojo.ember.feature.lostandfound.repository.LostAndFoundRepository;
import dev.chojo.ember.feature.lostandfound.repository.LostAndFoundRepository.ImageData;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class LostAndFoundService {
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    private final LostAndFoundRepository repository;
    private final NotificationService notificationService;

    @Inject
    public LostAndFoundService(LostAndFoundRepository repository, NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    public List<LostAndFoundItem> findByStation(int stationId) {
        return repository.findByStation(stationId);
    }

    public List<LostAndFoundItem> findUnclaimedByStation(int stationId) {
        return repository.findUnclaimedByStation(stationId);
    }

    public List<LostAndFoundItem> findUnclaimedOrClaimedBy(int stationId, int memberId) {
        return repository.findUnclaimedOrClaimedBy(stationId, memberId);
    }

    public Optional<LostAndFoundItem> findById(int id) {
        return repository.findById(id);
    }

    public LostAndFoundItem create(int stationId, String description, LocalDate foundAt, int createdBy) {
        var item = repository.create(stationId, description, foundAt, createdBy);
        notificationService.notifyStation(
                stationId,
                NotificationType.LOST_AND_FOUND_NEW,
                NotificationData.of(
                        "notification.lostAndFoundNew",
                        Map.of("description", description != null ? description : ""),
                        new NotificationLink("lost-and-found")),
                createdBy);
        return item;
    }

    public boolean uploadImage(int id, byte[] image, String contentType) {
        if (image.length > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Image exceeds maximum size of 5 MB");
        }
        return repository.updateImage(id, image, contentType);
    }

    public Optional<ImageData> findImage(int id) {
        return repository.findImage(id);
    }

    public boolean claim(int id, int claimedBy, int stationId, String claimerName) {
        var item = repository.findById(id).orElse(null);
        boolean success = repository.claim(id, claimedBy);
        if (success && item != null) {
            String desc = item.description() != null ? item.description() : "";
            notificationService.notifyMembersWithRole(
                    stationId,
                    "LOST_AND_FOUND_MANAGEMENT",
                    NotificationType.LOST_AND_FOUND_CLAIMED,
                    NotificationData.of(
                            "notification.lostAndFoundClaimed",
                            Map.of("name", claimerName, "description", desc),
                            new NotificationLink("lost-and-found")),
                    claimedBy);
            // Remove "new lost item" notifications for this item
            notificationService.deleteByTypeContaining(
                    NotificationType.LOST_AND_FOUND_NEW,
                    NotificationData.of("notification.lostAndFoundNew", Map.of("description", desc))
                            .toJson());
        }
        return success;
    }

    public boolean delete(int id) {
        return repository.delete(id);
    }
}
