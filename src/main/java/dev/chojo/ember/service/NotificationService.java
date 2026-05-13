/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.Notification;
import dev.chojo.ember.entity.NotificationData;
import dev.chojo.ember.entity.NotificationType;
import dev.chojo.ember.entity.UserSettings;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.NotificationRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import dev.chojo.ember.repository.StationRepository;
import dev.chojo.ember.repository.UserSettingsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Singleton
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final EmailService emailService;

    private static final Map<String, String> CATEGORY_LABELS_DE = Map.of(
            "NEW_NEWS", "Neuigkeit",
            "EVENT_REGISTRATION_STATUS", "Anmeldung",
            "EXCHANGE_STATUS_CHANGE", "Tausch",
            "EXCHANGE_NEW_REQUEST", "Neue Tausch-Anfrage",
            "NEW_EVENT", "Neuer Termin",
            "MEMBER_ADDED_TO_GROUP", "Gruppenänderung",
            "PROFILE_FIELD_CHANGED", "Profiländerung");

    @Inject
    public NotificationService(
            NotificationRepository notificationRepository,
            StationMemberRepository stationMemberRepository,
            UserSettingsRepository userSettingsRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.emailService = emailService;
    }

    public Notification notify(int memberId, NotificationType type, NotificationData data) {
        var notification = notificationRepository.create(memberId, type, data);
        trySendEmail(memberId, type, data);
        return notification;
    }

    public void notifyIfAbsent(int memberId, NotificationType type, NotificationData data) {
        if (!notificationRepository.exists(memberId, type, data.toJson())) {
            notificationRepository.create(memberId, type, data);
            trySendEmail(memberId, type, data);
        }
    }

    public void notifyStation(int stationId, NotificationType type, NotificationData data) {
        var members = stationMemberRepository.findByStation(stationId);
        for (var member : members) {
            notificationRepository.create(member.id(), type, data);
            trySendEmail(member.id(), type, data);
        }
    }

    public void notifyMembers(Collection<Integer> memberIds, NotificationType type, NotificationData data) {
        for (int memberId : memberIds) {
            notificationRepository.create(memberId, type, data);
            trySendEmail(memberId, type, data);
        }
    }

    public void notifyMembersIfAbsent(
            Collection<Integer> memberIds, NotificationType type, NotificationData data, int excludeMemberId) {
        String dataJson = data.toJson();
        for (int memberId : memberIds) {
            if (memberId == excludeMemberId) continue;
            if (!notificationRepository.exists(memberId, type, dataJson)) {
                notificationRepository.create(memberId, type, data);
                trySendEmail(memberId, type, data);
            }
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

    // -- Email sending for notifications --

    private void trySendEmail(int memberId, NotificationType type, NotificationData data) {
        try {
            var settings = userSettingsRepository.findByMemberId(memberId).orElse(null);
            if (settings == null || !settings.emailEnabled()) return;
            if (!isEmailEnabledForType(settings, type)) return;

            var member = stationMemberRepository.findById(memberId).orElse(null);
            if (member == null) return;

            var account = accountRepository.findById(member.accountId()).orElse(null);
            if (account == null || account.email() == null || account.email().isBlank()) return;

            int stationId = member.stationId();
            if (!emailService.canStationSend(stationId)) return;

            var station = stationRepository.findById(stationId).orElse(null);
            if (station == null) return;

            String stationName = station.name();
            String locale = station.locale() != null && station.locale().startsWith("de") ? "de" : "en";
            String logoApiUrl = stationRepository.findLogo(stationId).isPresent()
                    ? emailService.getBaseUrl() + "/api/v1/stations/" + stationId + "/logo"
                    : null;

            String name = (account.firstName() + " " + account.lastName()).trim();
            if (name.isEmpty()) name = account.email();

            String category = CATEGORY_LABELS_DE.getOrDefault(type.name(), type.name());
            String message = resolveMessage(locale, data);

            emailService.sendStationNotification(
                    stationId, account.email(), name, stationName, logoApiUrl, locale, category, message);
        } catch (Exception e) {
            log.warn("Failed to queue notification email for member {}", memberId, e);
        }
    }

    private boolean isEmailEnabledForType(UserSettings settings, NotificationType type) {
        return switch (type) {
            case NEW_NEWS -> settings.notifyNews();
            case NEW_EVENT -> settings.notifyNewEvents();
            case EVENT_REGISTRATION_STATUS -> settings.notifyEventStatus();
            case EXCHANGE_STATUS_CHANGE, EXCHANGE_NEW_REQUEST -> settings.notifyNews();
            case MEMBER_ADDED_TO_GROUP, PROFILE_FIELD_CHANGED -> settings.emailEnabled();
        };
    }

    private String resolveMessage(String locale, NotificationData data) {
        // Use the localeKey and params to build a simple message
        // The localeKey is like "notification.newNews" — we just use the params directly
        var params = data.params();
        if (params == null || params.isEmpty()) return data.localeKey();

        // Build a readable message from params
        var sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (!sb.isEmpty()) sb.append(" — ");
            sb.append(entry.getValue());
        }
        return sb.toString();
    }
}
