/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserSettingsRepository;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.notifications.repository.NotificationSettingsRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.i18n.Localizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for creating, querying, and managing notifications.
 * Handles notification dispatch to individual members, stations, and role-based groups,
 * respecting per-member notification preferences. Also runs a scheduled email digest.
 */
@Singleton
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Localizer LOCALIZER = new Localizer();
    private static final Map<String, String> ROUTE_PATHS = Map.ofEntries(
            Map.entry("news-list", "/station/news"),
            Map.entry("events-registrations", "/station/events/registrations"),
            Map.entry("events-upcoming", "/station/events/upcoming"),
            Map.entry("inventory-exchanges", "/station/inventory/exchanges"),
            Map.entry("inventory-procurement", "/station/inventory/procurement"),
            Map.entry("members-detail", "/station/members/detail/{id}"),
            Map.entry("dashboard-overview", "/station/dashboard/overview"),
            Map.entry("lost-and-found", "/station/lost-and-found"),
            Map.entry("lending-request", "/station/inventory/lending/{id}"));
    private final NotificationRepository notificationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final EmailService emailService;

    @Inject
    public NotificationService(
            NotificationRepository notificationRepository,
            StationMemberRepository stationMemberRepository,
            UserSettingsRepository userSettingsRepository,
            NotificationSettingsRepository notificationSettingsRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            EmailService emailService,
            Mailing mailing) {
        this.notificationRepository = notificationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.emailService = emailService;

        int intervalMinutes = mailing.notificationDigestIntervalMinutes();
        if (intervalMinutes > 0) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "notification-digest");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(this::processDigest, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
            log.info("Notification digest scheduled every {} minutes", intervalMinutes);
        } else {
            log.info("Notification digest disabled (interval=0)");
        }
    }

    /**
     * Creates a notification for a single member, if app notifications are enabled for that type.
     *
     * @param memberId the target member ID
     * @param type     the notification category
     * @param data     localized message data
     * @return the created notification, or {@code null} if app notifications are disabled
     */
    public Notification notify(int memberId, NotificationType type, NotificationData data) {
        if (!isAppEnabled(memberId, type)) return null;
        return notificationRepository.create(memberId, type, data);
    }

    /**
     * Creates a notification for a member only if no identical unacknowledged notification already exists.
     *
     * @param memberId the target member ID
     * @param type     the notification category
     * @param data     localized message data
     */
    public void notifyIfAbsent(int memberId, NotificationType type, NotificationData data) {
        if (!isAppEnabled(memberId, type)) return;
        if (!notificationRepository.exists(memberId, type, data.toJson())) {
            notificationRepository.create(memberId, type, data);
        }
    }

    /**
     * Sends a notification to all members of a station.
     *
     * @param stationId the station ID
     * @param type      the notification category
     * @param data      localized message data
     */
    public void notifyStation(int stationId, NotificationType type, NotificationData data) {
        notifyStation(stationId, type, data, -1);
    }

    /**
     * Sends a notification to all members of a station, excluding a specific member.
     *
     * @param stationId       the station ID
     * @param type            the notification category
     * @param data            localized message data
     * @param excludeMemberId member ID to exclude (e.g. the action initiator)
     */
    public void notifyStation(int stationId, NotificationType type, NotificationData data, int excludeMemberId) {
        var members = stationMemberRepository.findByStation(stationId);
        for (var member : members) {
            if (member.id() == excludeMemberId) continue;
            if (!isAppEnabled(member.id(), type)) continue;
            notificationRepository.create(member.id(), type, data);
        }
    }

    /**
     * Sends a notification to all members with a specific permission in a station.
     *
     * @param stationId      the station ID
     * @param permissionName the permission name to filter by
     * @param type           the notification category
     * @param data           localized message data
     */
    public void notifyMembersWithRole(
            int stationId, String permissionName, NotificationType type, NotificationData data) {
        notifyMembersWithRole(stationId, permissionName, type, data, -1);
    }

    /**
     * Sends a notification to all members with a specific permission in a station, excluding a specific member.
     *
     * @param stationId       the station ID
     * @param permissionName  the permission name to filter by
     * @param type            the notification category
     * @param data            localized message data
     * @param excludeMemberId member ID to exclude
     */
    public void notifyMembersWithRole(
            int stationId, String permissionName, NotificationType type, NotificationData data, int excludeMemberId) {
        var permission = StationPermission.valueOf(permissionName);
        var members = stationMemberRepository.findMembersWithPermission(stationId, permission);
        for (var member : members) {
            if (member.id() == excludeMemberId) continue;
            if (!isAppEnabled(member.id(), type)) continue;
            notificationRepository.create(member.id(), type, data);
        }
    }

    /**
     * Sends a notification to a collection of members.
     *
     * @param memberIds the member IDs to notify
     * @param type      the notification category
     * @param data      localized message data
     */
    public void notifyMembers(Collection<Integer> memberIds, NotificationType type, NotificationData data) {
        for (int memberId : memberIds) {
            if (!isAppEnabled(memberId, type)) continue;
            notificationRepository.create(memberId, type, data);
        }
    }

    /**
     * Sends a notification to members only if no identical unacknowledged notification exists,
     * excluding a specific member.
     *
     * @param memberIds       the member IDs to notify
     * @param type            the notification category
     * @param data            localized message data
     * @param excludeMemberId member ID to exclude
     */
    public void notifyMembersIfAbsent(
            Collection<Integer> memberIds, NotificationType type, NotificationData data, int excludeMemberId) {
        String dataJson = data.toJson();
        for (int memberId : memberIds) {
            if (memberId == excludeMemberId) continue;
            if (!isAppEnabled(memberId, type)) continue;
            if (!notificationRepository.exists(memberId, type, dataJson)) {
                notificationRepository.create(memberId, type, data);
            }
        }
    }

    /**
     * Retrieves all unacknowledged notifications for a member.
     *
     * @param memberId the member ID
     * @return list of unacknowledged notifications
     */
    public List<Notification> findUnacknowledged(int memberId) {
        return notificationRepository.findUnacknowledged(memberId);
    }

    /**
     * Retrieves the most recent notifications for a member (up to 50).
     *
     * @param memberId the member ID
     * @return list of notifications
     */
    public List<Notification> findAll(int memberId) {
        return notificationRepository.findAll(memberId);
    }

    public Map<NotificationType, NotificationSetting> getNotificationSettings(int memberId) {
        return notificationSettingsRepository.findByMemberAsMap(memberId);
    }

    /**
     * Counts unacknowledged notifications for a member.
     *
     * @param memberId the member ID
     * @return count of unacknowledged notifications
     */
    public int countUnacknowledged(int memberId) {
        return notificationRepository.countUnacknowledged(memberId);
    }

    /**
     * Acknowledges a single notification.
     *
     * @param id       the notification ID
     * @param memberId the member ID
     * @return {@code true} if the notification was acknowledged
     */
    public boolean acknowledge(int id, int memberId) {
        return notificationRepository.acknowledge(id, memberId);
    }

    /**
     * Acknowledges all unacknowledged notifications for a member.
     *
     * @param memberId the member ID
     * @return the number of notifications acknowledged
     */
    public int acknowledgeAll(int memberId) {
        return notificationRepository.acknowledgeAll(memberId);
    }

    /**
     * Deletes unacknowledged notifications matching a type and partial data JSON fragment.
     *
     * @param type            the notification type
     * @param partialDataJson JSON fragment for containment matching
     * @return the number of notifications deleted
     */
    public int deleteByTypeContaining(NotificationType type, String partialDataJson) {
        return notificationRepository.deleteByTypeContaining(type, partialDataJson);
    }

    /**
     * Removes acknowledged notifications older than 30 days.
     */
    public void cleanupOld() {
        notificationRepository.deleteOldAcknowledged();
    }

    private boolean isAppEnabled(int memberId, NotificationType type) {
        return notificationSettingsRepository.isAppEnabled(memberId, type);
    }

    // -- Digest processing --

    private void processDigest() {
        try {
            var unemailed = notificationRepository.findUnemailed();
            if (unemailed.isEmpty()) return;

            // Group by memberId, preserving order
            Map<Integer, List<Notification>> byMember = new LinkedHashMap<>();
            for (var n : unemailed) {
                byMember.computeIfAbsent(n.memberId(), k -> new ArrayList<>()).add(n);
            }

            List<Integer> emailedIds = new ArrayList<>();

            for (var entry : byMember.entrySet()) {
                int memberId = entry.getKey();
                List<Notification> notifications = entry.getValue();

                try {
                    if (trySendDigest(memberId, notifications)) {
                        for (var n : notifications) {
                            emailedIds.add(n.id());
                        }
                    } else {
                        // User doesn't want emails or can't receive — still mark so we don't retry
                        for (var n : notifications) {
                            emailedIds.add(n.id());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to send digest for member {}", memberId, e);
                    // Mark as emailed anyway to avoid infinite retries
                    for (var n : notifications) {
                        emailedIds.add(n.id());
                    }
                }
            }

            if (!emailedIds.isEmpty()) {
                notificationRepository.markEmailed(emailedIds);
                log.info(
                        "Processed notification digest: {} notifications for {} members",
                        emailedIds.size(),
                        byMember.size());
            }
        } catch (Exception e) {
            log.error("Error processing notification digest", e);
        }
    }

    private boolean trySendDigest(int memberId, List<Notification> notifications) {
        var userSettings = userSettingsRepository.findByMemberId(memberId).orElse(null);
        if (userSettings == null || !userSettings.emailEnabled()) return false;

        var member = stationMemberRepository.findById(memberId).orElse(null);
        if (member == null) return false;

        var account = accountRepository.findById(member.accountId()).orElse(null);
        if (account == null || account.email() == null || account.email().isBlank()) return false;

        int stationId = member.stationId();
        if (!emailService.canStationSend(stationId)) return false;

        var station = stationRepository.findById(stationId).orElse(null);
        if (station == null) return false;

        // Filter to only notification types the user has enabled
        var eligible = notifications.stream()
                .filter(n -> notificationSettingsRepository.isEmailEnabled(memberId, n.type()))
                .toList();
        if (eligible.isEmpty()) return false;

        String stationName = station.name();
        String locale = station.locale() != null && station.locale().startsWith("de") ? "de" : "en";
        String logoApiUrl = stationRepository.findLogo(stationId).isPresent()
                ? emailService.getBaseUrl() + "/api/v1/stations/" + stationId + "/logo"
                : null;

        String name = (account.firstName() + " " + account.lastName()).trim();
        if (name.isEmpty()) name = account.email();

        // Build notification items HTML
        String baseUrl = emailService.getBaseUrl();
        var itemsHtml = new StringBuilder();
        for (var n : eligible) {
            var labels = LOCALIZER.get("notifications", locale, "category");
            String category = labels.getOrDefault(n.type().name(), n.type().name());
            String message = resolveMessage(locale, n);
            String itemUrl = resolveNotificationUrl(baseUrl, n.data());

            itemsHtml.append("<li class=\"notification-item\">");
            if (itemUrl != null) {
                itemsHtml
                        .append("<a href=\"")
                        .append(itemUrl)
                        .append("\" style=\"text-decoration:none;color:inherit\">");
            }
            itemsHtml
                    .append("<span class=\"category\">")
                    .append(category)
                    .append("</span>")
                    .append("<p class=\"message\">")
                    .append(message)
                    .append("</p>");
            String detail = resolveDetail(n);
            if (detail != null) {
                itemsHtml.append("<p class=\"detail\">").append(detail).append("</p>");
            }
            if (itemUrl != null) {
                itemsHtml.append("</a>");
            }
            itemsHtml.append("</li>");
        }

        var vars = new HashMap<String, String>();
        vars.put("name", name);
        vars.put("baseUrl", emailService.getBaseUrl());
        vars.put("stationName", stationName);
        vars.put("count", String.valueOf(eligible.size()));
        vars.put("items", itemsHtml.toString());
        vars.put("actionUrl", emailService.getBaseUrl() + "/station/dashboard/overview");
        vars.put(
                "logoHtml",
                logoApiUrl != null && !logoApiUrl.isBlank()
                        ? "<img src=\"" + logoApiUrl + "\" alt=\"\" style=\"height:40px;border-radius:4px\">"
                        : "");

        String subject = locale.equals("de")
                ? stationName + ": " + eligible.size() + " neue Benachrichtigungen"
                : stationName + ": " + eligible.size() + " new notifications";
        String body = emailService.loadTemplate("notification-digest.html", locale, vars);
        emailService.queueStationEmail(stationId, account.email(), subject, body);
        return true;
    }

    private String resolveMessage(String locale, Notification n) {
        var templates = LOCALIZER.get("notifications", locale, "message");
        String localeKey = n.type().localeKey();
        String template = templates.get(localeKey);
        var params = n.data().paramsAsMap();
        if (template == null) {
            if (params.isEmpty()) return localeKey;
            var sb = new StringBuilder();
            for (var entry : params.entrySet()) {
                if (!sb.isEmpty()) sb.append(" — ");
                sb.append(entry.getValue());
            }
            return sb.toString();
        }
        for (var entry : params.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return template;
    }

    private String resolveDetail(Notification n) {
        var params = n.data().params();
        if (params == null) return null;
        return switch (n.type()) {
            case NEW_NEWS -> params instanceof NotificationParams.NewNews p ? p.preview() : null;
            case NEWS_COMMENT -> params instanceof NotificationParams.NewsComment p ? p.preview() : null;
            case EXCHANGE_STATUS_CHANGE ->
                params instanceof NotificationParams.ExchangeStatusChange p ? p.reason() : null;
            case EXCHANGE_NEW_REQUEST -> params instanceof NotificationParams.ExchangeNewRequest p ? p.reason() : null;
            case EVENT_REGISTRATION_STATUS ->
                params instanceof NotificationParams.EventRegistrationStatus p ? p.eventDescription() : null;
            case NEW_EVENT -> params instanceof NotificationParams.NewEvent p ? p.eventDescription() : null;
            default -> null;
        };
    }

    private String resolveNotificationUrl(String baseUrl, NotificationData data) {
        if (data.link() == null) return null;
        String route = data.link().route();
        String pathTemplate = ROUTE_PATHS.get(route);
        if (pathTemplate == null) return baseUrl + "/station/dashboard/overview";

        String path = pathTemplate;
        var routeParams = data.link().routeParams();
        if (routeParams != null) {
            for (var entry : routeParams.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return baseUrl + path;
    }
}
