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
            Map.entry("event-detail", "/station/events/{id}"),
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
        requireLink(type, data);
        if (!isAppEnabled(memberId, type)) return null;
        return notificationRepository.create(memberId, type, data);
    }

    /**
     * Defensive guard: every persisted notification must carry a {@link NotificationData.NotificationLink}
     * so the in-app view, email digest, and feed renderer all have a navigable target. This
     * fails fast in tests if a future handler forgets to attach one.
     */
    private static void requireLink(NotificationType type, NotificationData data) {
        if (data == null || data.link() == null) {
            throw new IllegalArgumentException("Notification " + type + " requires a NotificationLink; got " + data);
        }
    }

    /**
     * Creates a notification for a member only if no identical unacknowledged notification already exists.
     *
     * @param memberId the target member ID
     * @param type     the notification category
     * @param data     localized message data
     */
    public void notifyIfAbsent(int memberId, NotificationType type, NotificationData data) {
        requireLink(type, data);
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
        requireLink(type, data);
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
        requireLink(type, data);
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
        requireLink(type, data);
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
        requireLink(type, data);
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

    /** Returns the latest notification id + created_at for the given member, for feed cache invalidation. */
    public NotificationRepository.Stamp findMaxStamp(int memberId) {
        return notificationRepository.findMaxStamp(memberId);
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
        String locale = resolveLocale(station.locale());
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

    /**
     * Normalizes a station locale string ({@code de-DE}, {@code en-US}, …) to the short codes
     * ({@code de}/{@code en}) used by the bundled translation files.
     */
    public String resolveLocale(String stationLocale) {
        return stationLocale != null && stationLocale.startsWith("de") ? "de" : "en";
    }

    /**
     * Returns a localized string from a section of the {@code notifications} bundle, with optional
     * {@code {param}} substitutions. Falls back to {@code key} when no translation is configured.
     */
    public String resolveLocalized(String locale, String section, String key, Map<String, String> params) {
        var entries = LOCALIZER.get("notifications", locale, section);
        String value = entries.getOrDefault(key, key);
        if (params != null) {
            for (var e : params.entrySet()) {
                value = value.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return value;
    }

    /**
     * Resolves the localized category label for a notification type, e.g. "Neuigkeit" for NEW_NEWS.
     * Falls back to the enum name when no translation is configured.
     */
    public String resolveCategory(String locale, NotificationType type) {
        var labels = LOCALIZER.get("notifications", locale, "category");
        return labels.getOrDefault(type.name(), type.name());
    }

    /**
     * Resolves the localized message body for a notification, substituting any {param} placeholders.
     * Falls back to joining the params with em-dashes (or to the locale key) when no template exists.
     */
    public String resolveMessage(String locale, Notification n) {
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

    /**
     * Returns a short detail string (e.g. news preview, denial reason) when the notification type carries one.
     */
    public String resolveDetail(Notification n) {
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

    /**
     * Resolves a rich, localised multi-line body for syndication feeds. The first line is always
     * the localised headline ({@link #resolveMessage}); subsequent lines surface the most useful
     * params for each notification type (descriptions, previews, status, counts, etc.) so users
     * understand the change without opening the app.
     */
    public String resolveFeedBody(String locale, Notification n) {
        var lines = new ArrayList<String>();
        lines.add(resolveMessage(locale, n));

        var params = n.data().params();
        switch (n.type()) {
            case NEW_NEWS -> {
                if (params instanceof NotificationParams.NewNews p && notBlank(p.preview())) lines.add(p.preview());
            }
            case NEWS_COMMENT -> {
                if (params instanceof NotificationParams.NewsComment p && notBlank(p.preview())) lines.add(p.preview());
            }
            case NEW_EVENT -> {
                if (params instanceof NotificationParams.NewEvent p && notBlank(p.eventDescription())) {
                    lines.add(p.eventDescription());
                }
            }
            case NEW_EVENTS_BATCH -> {
                if (params instanceof NotificationParams.NewEventsBatch p && notBlank(p.eventPreview())) {
                    lines.add(feedKv(locale, "events", p.eventPreview()));
                }
            }
            case EVENT_REGISTRATION_STATUS -> {
                if (params instanceof NotificationParams.EventRegistrationStatus p && notBlank(p.eventDescription())) {
                    lines.add(p.eventDescription());
                }
            }
            case EVENT_CANCELLED -> {
                if (params instanceof NotificationParams.EventCancelled p && notBlank(p.reason())) {
                    lines.add(feedKv(locale, "reason", p.reason()));
                }
            }
            case EVENT_REMINDER -> {
                if (params instanceof NotificationParams.EventReminder p) {
                    if (notBlank(p.eventDate())) lines.add(feedKv(locale, "eventDate", p.eventDate()));
                    lines.add(feedKv(locale, "daysBefore", String.valueOf(p.daysBefore())));
                }
            }
            case EXCHANGE_NEW_REQUEST -> {
                if (params instanceof NotificationParams.ExchangeNewRequest p && notBlank(p.reason())) {
                    lines.add(feedKv(locale, "reason", p.reason()));
                }
            }
            case EXCHANGE_STATUS_CHANGE -> {
                if (params instanceof NotificationParams.ExchangeStatusChange p && notBlank(p.reason())) {
                    lines.add(feedKv(locale, "reason", p.reason()));
                }
            }
            case BOARD_TICKET_UPDATE -> {
                if (params
                        instanceof
                        NotificationParams.BoardTicketUpdate(
                                String boardName,
                                String ticketKey,
                                String changeDescription)) {
                    if (notBlank(changeDescription)) lines.add(changeDescription);
                    if (notBlank(ticketKey)) lines.add(feedKv(locale, "ticketKey", ticketKey));
                    if (notBlank(boardName)) lines.add(feedKv(locale, "board", boardName));
                }
            }
            case LOST_AND_FOUND_NEW -> {
                if (params instanceof NotificationParams.LostAndFoundNew(String description) && notBlank(description)) {
                    lines.add(description);
                }
            }
            case LENDING_NEW_REQUEST -> {
                if (params instanceof NotificationParams.LendingNewRequest p && notBlank(p.itemSummary())) {
                    lines.add(feedKv(locale, "itemSummary", p.itemSummary()));
                }
            }
            case PROCEDURE_ITEM_CHECKED -> {
                if (params instanceof NotificationParams.ProcedureItemCheckedParams p) {
                    if (notBlank(p.itemTitle())) lines.add(feedKv(locale, "item", p.itemTitle()));
                    if (notBlank(p.checkedByName())) lines.add(feedKv(locale, "by", p.checkedByName()));
                }
            }
            case REGISTRATION_DEADLINE_EXPIRED -> {
                if (params instanceof NotificationParams.RegistrationDeadlineExpired p) {
                    lines.add(feedKv(locale, "pendingCount", String.valueOf(p.pendingCount())));
                }
            }
            case STORAGE_WARNING -> {
                if (params
                        instanceof
                        NotificationParams.StorageWarning(
                                int usedPercent,
                                String usedFormatted,
                                String quotaFormatted)) {
                    lines.add(feedKv(locale, "usedPercent", usedPercent + "%"));
                    if (notBlank(usedFormatted)) lines.add(feedKv(locale, "used", usedFormatted));
                    if (notBlank(quotaFormatted)) lines.add(feedKv(locale, "quota", quotaFormatted));
                }
            }
            default -> {
                String detail = resolveDetail(n);
                if (notBlank(detail)) lines.add(detail);
            }
        }
        return String.join("\n", lines);
    }

    private String feedKv(String locale, String labelKey, String value) {
        return resolveLocalized(locale, "feedLabel", labelKey, null) + ": " + value;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Resolves the deep link URL for a notification's target entity, or {@code null} when the
     * notification has no associated link. Unknown routes fall back to the dashboard.
     */
    public String resolveNotificationUrl(String baseUrl, NotificationData data) {
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
