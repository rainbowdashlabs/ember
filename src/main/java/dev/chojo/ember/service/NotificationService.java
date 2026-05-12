/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.Notification;
import dev.chojo.ember.entity.NotificationType;
import dev.chojo.ember.repository.NotificationRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public NotificationService(
            NotificationRepository notificationRepository, StationMemberRepository stationMemberRepository) {
        this.notificationRepository = notificationRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    public Notification notify(int memberId, NotificationType type, Integer referenceId, String message) {
        return notificationRepository.create(memberId, type, referenceId, message);
    }

    public void notifyStation(int stationId, NotificationType type, Integer referenceId, String message) {
        var members = stationMemberRepository.findByStation(stationId);
        for (var member : members) {
            notificationRepository.create(member.id(), type, referenceId, message);
        }
    }

    public void notifyMembers(List<Integer> memberIds, NotificationType type, Integer referenceId, String message) {
        for (int memberId : memberIds) {
            notificationRepository.create(memberId, type, referenceId, message);
        }
    }

    public List<Notification> findUnacknowledged(int memberId) {
        return notificationRepository.findUnacknowledged(memberId);
    }

    public List<Notification> findAll(int memberId) {
        return notificationRepository.findAll(memberId);
    }

    public int countUnacknowledged(int memberId) {
        return notificationRepository.countUnacknowledged(memberId);
    }

    public boolean acknowledge(int id, int memberId) {
        return notificationRepository.acknowledge(id, memberId);
    }

    public int acknowledgeAll(int memberId) {
        return notificationRepository.acknowledgeAll(memberId);
    }

    public void cleanupOld() {
        notificationRepository.deleteOldAcknowledged();
    }
}
