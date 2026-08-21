/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.api.auth.StationPermission;
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
import dev.chojo.ember.feature.station.service.StationLogoService;
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
import java.util.UUID;
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
    /**
     * Default cap for entity-identifier fragments embedded in feed titles.
     */
    public static final int TITLE_FRAGMENT_MAX = 80;
    /**
     * Default cap for free-form body snippets (previews, descriptions).
     */
    public static final int BODY_SNIPPET_MAX = 500;

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Localizer LOCALIZER = new Localizer();
    private static final Map<String, String> ROUTE_PATHS = Map.ofEntries(
            Map.entry("news-list", "/station/news"),
            Map.entry("events-registrations", "/station/events/registrations"),
            Map.entry("events-upcoming", "/station/events/upcoming"),
            Map.entry("event-detail", "/station/events/{id}"),
            // Date-aware variant for recurring events so the user lands on the right
            // occurrence (e.g. weekly events on 2026-06-12 vs 2026-06-19).
            Map.entry("event-detail-date", "/station/events/{id}/{date}"),
            Map.entry("inventory-exchanges", "/station/inventory/exchanges"),
            Map.entry("inventory-procurement", "/station/inventory/procurement"),
            Map.entry("members-detail", "/station/members/detail/{id}"),
            Map.entry("dashboard-overview", "/station/dashboard/overview"),
            Map.entry("lost-and-found", "/station/lost-and-found"),
            Map.entry("lending-request", "/station/inventory/lending/{id}"),
            // Board ticket routes use the board short key + per-board ticket number, not the
            // numeric primary keys. Handlers must pass {boardKey} and {ticketNumber} accordingly.
            Map.entry("ticket-detail", "/station/boards/{boardKey}/tickets/{ticketNumber}"));
    /**
     * Param keys that drive pluralisation in {@link #resolveMessage}.
     */
    private static final List<String> COUNT_PARAMS = List.of("count", "daysBefore", "pendingCount");

    private final NotificationRepository notificationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final StationLogoService logoService;
    private final EmailService emailService;

    @Inject
    public NotificationService(
            NotificationRepository notificationRepository,
            StationMemberRepository stationMemberRepository,
            UserSettingsRepository userSettingsRepository,
            NotificationSettingsRepository notificationSettingsRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            StationLogoService logoService,
            EmailService emailService,
            Mailing mailing) {
        this.notificationRepository = notificationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.logoService = logoService;
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
     * Truncates a snippet to {@code maxChars} on a word boundary, appending a Unicode ellipsis
     * when truncation occurs. Multi-byte characters count as one code-unit (Java string length).
     * Returns {@code null} for null input and the original string when shorter than the limit.
     *
     * <p>Centralised here so all feed-bound text (titles, body previews, descriptions, change
     * descriptions) shares the same cap and reader inboxes stay scannable.
     */
    public static String truncateSnippet(String text, int maxChars) {
        if (text == null) return null;
        if (maxChars <= 0 || text.length() <= maxChars) return text;
        String head = text.substring(0, maxChars);
        // Prefer ending at a paragraph or line break so we don't strand half a list item;
        // fall back to the last space, and finally to a hard cut if neither exists.
        int lastNewline = head.lastIndexOf('\n');
        int lastSpace = head.lastIndexOf(' ');
        int cut = Math.max(lastNewline, lastSpace);
        if (cut > 0) {
            head = head.substring(0, cut);
        }
        return head + "…";
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
     * Iconic marker for known status values, shared across registration / exchange / lending
     * flows. Empty string for statuses without a natural marker so callers can render the label
     * alone.
     */
    private static String statusSymbol(String statusName) {
        return switch (statusName) {
            case "ACCEPTED", "APPROVED", "DONE", "RECEIVED", "RETURNED" -> "✓";
            case "DENIED", "DECLINED", "REJECTED", "CANCELLED" -> "✗";
            case "PENDING", "REQUESTED", "ANNOUNCED" -> "…";
            case "WITHDRAWN" -> "↶";
            default -> "";
        };
    }

    /**
     * Replaces a raw status enum name (e.g. {@code "ACCEPTED"}, {@code "DONE"},
     * {@code "APPROVED"}) in the params with its localised label from the {@code ical.status.*}
     * bundle. Lets the message templates use a single {@code {status}} placeholder for all
     * status-bearing types (registration, exchange, lending) without each consumer having to
     * translate the enum themselves. No-op when the param is absent or the bundle has no
     * matching entry.
     */
    private static void localizeStatusParam(String locale, Map<String, String> params) {
        String status = params.get("status");
        if (status == null) return;
        var statusLabels = LOCALIZER.get("notifications", locale, "ical");
        String localized = statusLabels.get("status." + status);
        if (localized != null) {
            params.put("status", localized);
        }
    }

    /**
     * Picks the first integer-valued count-like param ({@code count}, {@code daysBefore},
     * {@code pendingCount}) so {@link #resolveMessage} can route to the right plural variant.
     * Returns {@code null} when no recognised count param is present or parseable.
     */
    private static Integer extractCountParam(Map<String, String> params) {
        for (String key : COUNT_PARAMS) {
            String value = params.get(key);
            if (value == null) continue;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // Fall through and try the next candidate.
            }
        }
        return null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
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
     * Creates a notification for a member only if no identical unacknowledged notification already exists.
     *
     * @param memberId the target member ID
     * @param type     the notification category
     * @param data     localized message data
     */
    public void notifyIfAbsent(int memberId, NotificationType type, NotificationData data) {
        requireLink(type, data);
        if (!isAppEnabled(memberId, type)) return;
        if (!notificationRepository.exists(memberId, type, data)) {
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
        for (int memberId : memberIds) {
            if (memberId == excludeMemberId) continue;
            if (!isAppEnabled(memberId, type)) continue;
            if (!notificationRepository.exists(memberId, type, data)) {
                notificationRepository.create(memberId, type, data);
            }
        }
    }

    // -- Cluster members --
    //
    // The per-type settings screen is a station member's, so a cluster member has no rows in it and these
    // do not consult it. What a cluster member hears about is decided by what they may see, which is their
    // cluster permissions, and that is checked where the notification is raised.

    /**
     * Sends a notification to cluster members, skipping any that already have the same one waiting.
     *
     * @param clusterMemberIds       the cluster members to tell
     * @param type                   the notification category
     * @param data                   localized message data
     * @param excludeClusterMemberId the cluster member who caused it, so they are not told about their own
     *                               doing, or {@code null} when nobody should be skipped
     */
    public void notifyClusterMembersIfAbsent(
            Collection<Integer> clusterMemberIds,
            NotificationType type,
            NotificationData data,
            Integer excludeClusterMemberId) {
        requireLink(type, data);
        for (int clusterMemberId : clusterMemberIds) {
            if (excludeClusterMemberId != null && clusterMemberId == excludeClusterMemberId) continue;
            if (!notificationRepository.existsForClusterMember(clusterMemberId, type, data)) {
                notificationRepository.createForClusterMember(clusterMemberId, type, data);
            }
        }
    }

    /**
     * The unread notifications of a cluster member.
     *
     * @param clusterMemberId the cluster member
     * @return what is waiting for them
     */
    public List<Notification> findUnacknowledgedForClusterMember(int clusterMemberId) {
        return notificationRepository.findUnacknowledgedForClusterMember(clusterMemberId);
    }

    /**
     * The recent notifications of a cluster member, read or not.
     *
     * @param clusterMemberId the cluster member
     * @return their feed
     */
    public List<Notification> findAllForClusterMember(int clusterMemberId) {
        return notificationRepository.findAllForClusterMember(clusterMemberId);
    }

    /**
     * How much a cluster member has not read.
     *
     * @param clusterMemberId the cluster member
     * @return the count
     */
    public int countUnacknowledgedForClusterMember(int clusterMemberId) {
        return notificationRepository.countUnacknowledgedForClusterMember(clusterMemberId);
    }

    /**
     * Marks one of a cluster member's notifications read.
     *
     * @param id              the notification
     * @param clusterMemberId the cluster member it must belong to
     */
    public void acknowledgeForClusterMember(int id, int clusterMemberId) {
        notificationRepository.acknowledgeForClusterMember(id, clusterMemberId);
    }

    /**
     * Marks everything a cluster member has waiting as read.
     *
     * @param clusterMemberId the cluster member
     * @return how many were marked
     */
    public int acknowledgeAllForClusterMember(int clusterMemberId) {
        return notificationRepository.acknowledgeAllForClusterMember(clusterMemberId);
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

    // -- Digest processing --

    /**
     * Returns the latest notification id + created_at for the given member, for feed cache invalidation.
     */
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
     */
    public void acknowledge(int id, int memberId) {
        notificationRepository.acknowledge(id, memberId);
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
     * Deletes unacknowledged notifications matching a type and a partial data fragment.
     *
     * @param type        the notification type
     * @param partialData the data fragment used for containment matching
     */
    public void deleteByTypeContaining(NotificationType type, NotificationData partialData) {
        notificationRepository.deleteByTypeContaining(type, partialData);
    }

    /**
     * Removes acknowledged notifications older than 30 days.
     */
    public void cleanupOld() {
        notificationRepository.deleteOldAcknowledged();
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
     * Returns the localised status name + Unicode marker for a status-bearing notification. Marker
     * choice is independent of locale so colour-blind / monochrome readers always have an iconic cue.
     * Falls back to the raw enum name when no translation is configured.
     */
    public String resolveStatusWithSymbol(String locale, String statusName) {
        if (statusName == null) return null;
        var labels = LOCALIZER.get("notifications", locale, "ical");
        String label = labels.getOrDefault("status." + statusName, statusName);
        String symbol = statusSymbol(statusName);
        return symbol.isEmpty() ? label : symbol + " " + label;
    }

    /**
     * Resolves a rich, scannable feed-entry title: {@code "{Category}: {entity identifier}"} or
     * a per-type template carrying status, count, etc. Plural-routing uses the same
     * {@code .one}/{@code .other} convention as {@link #resolveMessage}. Falls back to the bare
     * category when no template is configured or no fragment can be interpolated, so we never
     * render an empty title like "News: ".
     *
     * <p>All string params are truncated to {@link #TITLE_FRAGMENT_MAX} so a 5KB news title
     * doesn't blow past the reader's inbox row width.
     */
    public String resolveFeedTitle(String locale, Notification n) {
        var templates = LOCALIZER.get("notifications", locale, "feedTitle");
        String typeKey = n.type().name();
        var params = new LinkedHashMap<>(n.data().paramsAsMap());
        augmentTitleParams(locale, n, params);

        Integer count = extractCountParam(params);
        String template = null;
        if (count != null) {
            String pluralKey = typeKey + (count == 1 ? ".one" : ".other");
            template = templates.get(pluralKey);
        }
        if (template == null) template = templates.get(typeKey);
        if (template == null) return resolveCategory(locale, n.type());

        // Truncate values defensively - a 5KB description shouldn't fill the inbox row.
        for (var entry : params.entrySet()) {
            entry.setValue(truncateSnippet(entry.getValue(), TITLE_FRAGMENT_MAX));
        }
        String result = template;
        for (var entry : params.entrySet()) {
            if (entry.getValue() == null) continue;
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        // Strip any leftover {placeholder} from missing params, then collapse whitespace.
        result = result.replaceAll("\\{[^}]+\\}", "").replaceAll("\\s+", " ").trim();
        // Trim dangling separators left over after a missing param was stripped.
        result = result.replaceAll("[\\s\\--:,]+$", "").trim();
        if (result.isBlank()) return resolveCategory(locale, n.type());
        return result;
    }

    /**
     * Resolves the localized message body for a notification, substituting any {param} placeholders.
     * Falls back to joining the params with em-dashes (or to the locale key) when no template exists.
     */
    public String resolveMessage(String locale, Notification n) {
        var templates = LOCALIZER.get("notifications", locale, "message");
        String localeKey = n.type().localeKey();
        // Defensive copy so we can rewrite the {status} value in-place without affecting any
        // downstream caller that asked for paramsAsMap() too.
        var params = new LinkedHashMap<>(n.data().paramsAsMap());
        localizeStatusParam(locale, params);

        // Pluralisation: when the params carry an integer "count"/"daysBefore"/"pendingCount",
        // prefer the .one / .other variant of the localeKey when defined. Languages like German
        // and English need different surface text for n=1 vs n=other.
        Integer count = extractCountParam(params);
        String template = null;
        if (count != null) {
            String pluralKey = localeKey + (count == 1 ? ".one" : ".other");
            template = templates.get(pluralKey);
        }
        if (template == null) template = templates.get(localeKey);

        if (template == null) {
            if (params.isEmpty()) return localeKey;
            var sb = new StringBuilder();
            for (var entry : params.entrySet()) {
                if (!sb.isEmpty()) sb.append(" - ");
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
                params instanceof NotificationParams.ExchangeStatusChange p ? p.stepLabel() : null;
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
                    if (p.eventDate() != null)
                        lines.add(feedKv(locale, "eventDate", p.eventDate().toString()));
                    lines.add(feedKv(locale, "daysBefore", String.valueOf(p.daysBefore())));
                }
            }
            case EXCHANGE_NEW_REQUEST -> {
                if (params instanceof NotificationParams.ExchangeNewRequest p && notBlank(p.reason())) {
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

    /**
     * Resolves the deep link URL for a notification's target entity, or {@code null} when the
     * notification has no associated link. Unknown routes fall back to the dashboard.
     *
     * <p>The owning station's UUID is appended as a {@code ?station=<uid>} query parameter for any
     * station-scoped link. This survives the login redirect and the cross-station picker so the
     * recipient lands directly on the right station context even when their account is a member of
     * several stations.
     *
     * @param baseUrl    public base URL of the deployment
     * @param stationUid UUID of the station that owns the notification, or {@code null} for none
     * @param data       the notification's link metadata
     * @return the resolved URL or {@code null} when the notification has no link
     */
    public String resolveNotificationUrl(String baseUrl, UUID stationUid, NotificationData data) {
        if (data.link() == null) return null;
        String route = data.link().route();
        String pathTemplate = ROUTE_PATHS.get(route);
        if (pathTemplate == null) return appendStation(baseUrl + "/station/dashboard/overview", stationUid);

        String path = pathTemplate;
        var routeParams = data.link().routeParams();
        if (routeParams != null) {
            for (var entry : routeParams.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return appendStation(baseUrl + path, stationUid);
    }

    /**
     * Appends {@code ?station=<uid>} (or {@code &station=<uid>}) to {@code url} when {@code uid}
     * is non-null and the URL points at a station-scoped path. Leaves non-station paths untouched
     * so help-center or admin URLs don't accidentally carry station context.
     */
    private static String appendStation(String url, UUID stationUid) {
        if (stationUid == null) return url;
        int pathStart = url.indexOf("/", url.indexOf("://") + 3);
        if (pathStart < 0 || !url.substring(pathStart).startsWith("/station/")) return url;
        char separator = url.contains("?") ? '&' : '?';
        return url + separator + "station=" + stationUid;
    }

    private boolean isAppEnabled(int memberId, NotificationType type) {
        return notificationSettingsRepository.isAppEnabled(memberId, type);
    }

    private void processDigest() {
        try {
            var unemailed = notificationRepository.findUnemailed();
            if (unemailed.isEmpty()) return;

            // Group by memberId, preserving order
            Map<Integer, List<Notification>> byMember = new LinkedHashMap<>();
            for (var n : unemailed) {
                byMember.computeIfAbsent(n.memberId(), _ -> new ArrayList<>()).add(n);
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
                        // User doesn't want emails or can't receive - still mark so we don't retry
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
        String logoApiUrl = logoService.exists(stationId)
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
            String itemUrl = resolveNotificationUrl(baseUrl, station.uid(), n.data());

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

        // Subject text is pluralised via dedicated i18n keys (digest.subject.one / .other)
        // so the singular form reads correctly across locales.
        String subjectKey = eligible.size() == 1 ? "subject.one" : "subject.other";
        String subject = resolveLocalized(
                locale,
                "digest",
                subjectKey,
                Map.of("stationName", stationName, "count", String.valueOf(eligible.size())));
        String body = emailService.loadTemplate("notification-digest.html", locale, vars);
        emailService.queueStationEmail(stationId, account.email(), subject, body);
        return true;
    }

    /**
     * Injects synthetic params used by feed-title templates: status-with-symbol for status-bearing
     * types, etc. Anything that requires a locale or symbol mapping lives here so the template
     * itself stays a simple placeholder string.
     */
    private void augmentTitleParams(String locale, Notification n, Map<String, String> params) {
        var orig = n.data().params();
        if (orig instanceof NotificationParams.EventRegistrationStatus p) {
            params.put("statusLabel", resolveStatusWithSymbol(locale, p.status().name()));
        } else if (orig instanceof NotificationParams.LendingStatusChange p) {
            params.put("statusLabel", resolveStatusWithSymbol(locale, p.status().name()));
        }
    }

    private String feedKv(String locale, String labelKey, String value) {
        return resolveLocalized(locale, "feedLabel", labelKey, null) + ": " + value;
    }
}
