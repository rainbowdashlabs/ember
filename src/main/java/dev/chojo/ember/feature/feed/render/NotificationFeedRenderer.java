/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.modules.mediarss.types.UrlReference;
import com.rometools.rome.feed.module.Module;
import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndCategoryImpl;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.util.SizeParser;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Builds {@link SyndEntry} entries for the personal RSS/Atom notification feeds.
 *
 * <p>Each entry carries two content bodies - a rich, semantic HTML block (rendered by
 * Thunderbird, Feedly, NetNewsWire, etc.) and a plain-text fallback for clients that strip
 * HTML - plus per-entry author, categories, and an embedded image when the notification
 * surfaces a lost-and-found item.
 */
@Singleton
public class NotificationFeedRenderer {
    private final NotificationService notificationService;
    private final EventCrudService crudService;
    private final EventFieldService eventFieldService;
    private final LostAndFoundService lostAndFoundService;
    private final LendingService lendingService;
    private final StorageQuotaService storageQuotaService;
    private final InventoryService inventoryService;
    private final BoardTicketService boardTicketService;
    private final ProcedureService procedureService;

    @Inject
    public NotificationFeedRenderer(
            NotificationService notificationService,
            EventCrudService crudService,
            EventFieldService eventFieldService,
            LostAndFoundService lostAndFoundService,
            LendingService lendingService,
            StorageQuotaService storageQuotaService,
            InventoryService inventoryService,
            BoardTicketService boardTicketService,
            ProcedureService procedureService) {
        this.notificationService = notificationService;
        this.crudService = crudService;
        this.eventFieldService = eventFieldService;
        this.lostAndFoundService = lostAndFoundService;
        this.lendingService = lendingService;
        this.storageQuotaService = storageQuotaService;
        this.inventoryService = inventoryService;
        this.boardTicketService = boardTicketService;
        this.procedureService = procedureService;
    }

    private static Integer extractLinkParam(Notification notification, String key) {
        var link = notification.data().link();
        if (link == null || link.routeParams() == null) return null;
        Object raw = link.routeParams().get(key);
        if (raw == null) return null;
        try {
            return raw instanceof Number n ? n.intValue() : Integer.parseInt(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer extractLinkId(Notification notification) {
        return extractLinkParam(notification, "id");
    }

    // -- HTML body --

    private static String formatLocalDate(LocalDate date, String locale) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.forLanguageTag(locale))
                .format(date);
    }

    private static String formatLocalDateRange(LocalDate from, LocalDate to, String locale) {
        if (from.equals(to)) return formatLocalDate(from, locale);
        return formatLocalDate(from, locale) + " – " + formatLocalDate(to, locale);
    }

    private static boolean sameLocalDate(Instant a, Instant b) {
        var zone = ZoneId.systemDefault();
        return a.atZone(zone).toLocalDate().equals(b.atZone(zone).toLocalDate());
    }

    private static String formatRangeSameDay(Instant start, Instant end, String locale) {
        var zone = ZoneId.systemDefault();
        var loc = Locale.forLanguageTag(locale);
        var dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(loc)
                .withZone(zone);
        var timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(loc)
                .withZone(zone);
        return dateFmt.format(start) + ", " + timeFmt.format(start) + " – " + timeFmt.format(end);
    }

    private static String formatInstant(Instant instant, String locale) {
        var fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag(locale))
                .withZone(ZoneId.systemDefault());
        return fmt.format(instant);
    }

    /**
     * Returns the actor on the notification, if any - used by readers as the entry's author.
     */
    private static String resolveAuthor(Notification notification) {
        var params = notification.data().params();
        if (params == null) return null;
        return switch (notification.type()) {
            case NEW_NEWS -> ((NotificationParams.NewNews) params).author();
            case NEWS_COMMENT -> ((NotificationParams.NewsComment) params).author();
            case COMMENT_MENTION -> ((NotificationParams.CommentMention) params).author();
            case EXCHANGE_NEW_REQUEST -> ((NotificationParams.ExchangeNewRequest) params).memberName();
            case LENDING_NEW_MESSAGE -> ((NotificationParams.LendingNewMessage) params).senderName();
            case PROCEDURE_ASSIGNED -> ((NotificationParams.ProcedureAssigned) params).assignedByName();
            case PROCEDURE_ITEM_CHECKED -> ((NotificationParams.ProcedureItemCheckedParams) params).checkedByName();
            default -> null;
        };
    }

    private static SyndCategory category(String name) {
        var c = new SyndCategoryImpl();
        c.setName(name);
        return c;
    }

    /**
     * Same as {@link #category(String)} but sets the {@code scheme} attribute so machine-
     * readable category terms (like the raw enum name) can be distinguished from the
     * primary human-readable category by feed readers and scripts.
     */
    private static SyndCategory schemedCategory(String name, String scheme) {
        var c = new SyndCategoryImpl();
        c.setName(name);
        c.setTaxonomyUri(scheme);
        return c;
    }

    /**
     * Returns the token-scoped image URL for a notification when the underlying entity carries
     * one and the link metadata exposes the entity id. Currently only lost-and-found items are
     * supported; other entity types fall through.
     */
    private static String resolveImageUrl(Notification notification, RenderContext ctx) {
        if (ctx.feedToken() == null) return null;
        if (notification.type() != NotificationType.LOST_AND_FOUND_NEW
                && notification.type() != NotificationType.LOST_AND_FOUND_CLAIMED) {
            return null;
        }
        var link = notification.data().link();
        if (link == null || link.routeParams() == null) return null;
        Object id = link.routeParams().get("id");
        if (id == null) return null;
        return ctx.baseUrl() + "/api/v1/public/feed/" + ctx.feedToken() + "/lost-and-found/" + id + "/image";
    }

    private static String resolveImageAlt(Notification notification) {
        var params = notification.data().params();
        if (params instanceof NotificationParams.LostAndFoundNew(String description)) return description;
        if (params instanceof NotificationParams.LostAndFoundClaimed p) return p.description();
        return "";
    }

    private static Module mediaModule(String imageUrl) {
        var module = new MediaEntryModuleImpl();
        try {
            var content = new MediaContent(new UrlReference(imageUrl));
            content.setMedium("image");
            module.setMediaContents(new MediaContent[] {content});
        } catch (URISyntaxException ignored) {
            // We constructed the URL ourselves; skip the module rather than fail the whole render
            // if validation rejects it for any reason.
        }
        return module;
    }

    private static void putIfPresent(Map<String, String> details, String key, String value) {
        if (notBlank(value)) details.put(key, value);
    }

    /**
     * Adds a long-form snippet (preview, description, change text) with the central truncation
     * cap applied so a 5KB article preview doesn't blow up a feed entry. Truncation is shared
     * across handlers - see {@link NotificationService#truncateSnippet(String, int)} - so
     * length policy can be tuned in one place.
     */
    private static void putSnippetIfPresent(Map<String, String> details, String key, String value) {
        if (!notBlank(value)) return;
        details.put(key, NotificationService.truncateSnippet(value, NotificationService.BODY_SNIPPET_MAX));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * HTML-escape and then convert surviving newlines into {@code <br>} tags. Used for body
     * field values that may legitimately span multiple lines (news previews, event
     * descriptions, change descriptions, table rows). Plain single-line values pass through
     * with no effect because they have no newlines to translate.
     */
    private static String escapeHtmlWithLineBreaks(String s) {
        return escapeHtml(s).replace("\n", "<br>");
    }

    /**
     * Renders a single notification into a {@link SyndEntry}.
     */
    public SyndEntry render(Notification notification, RenderContext ctx) {
        SyndEntry entry = new SyndEntryImpl();
        // Rich, entity-aware title (e.g. "News: Q3 training schedule published") so the
        // Thunderbird inbox row is scannable without expanding the entry.
        entry.setTitle(notificationService.resolveFeedTitle(ctx.locale(), notification));
        entry.setUri("urn:ember:notification:" + notification.id());
        entry.setPublishedDate(Date.from(notification.createdAt()));
        entry.setUpdatedDate(Date.from(notification.createdAt()));

        // Every entry gets a link. Falls back to the dashboard so readers always have a
        // navigable target even if a notification has no deep link metadata.
        String link = notificationService.resolveNotificationUrl(ctx.baseUrl(), ctx.stationUid(), notification.data());
        String fallback = ctx.baseUrl() + "/station/dashboard/overview";
        if (ctx.stationUid() != null) fallback = fallback + "?station=" + ctx.stationUid();
        entry.setLink(link != null ? link : fallback);

        // Author column in readers like Thunderbird.
        String author = resolveAuthor(notification);
        if (author != null) entry.setAuthor(author);

        // Categories let users filter by notification type. We expose two categories so
        // readers cover both audiences: the localised label (what humans see in the UI) and
        // the raw enum name (stable identifier scripts can match on across locales). The
        // enum-name category is tagged with a scheme so readers that collapse duplicate
        // `term` values still keep both, and the localised one always wins as the primary.
        entry.setCategories(List.of(
                category(notificationService.resolveCategory(ctx.locale(), notification.type())),
                schemedCategory(notification.type().name(), "urn:ember:notification-type")));

        // Atom semantics: <summary> is the short preview shown in the inbox row,
        // <content> is the rich body shown on expand. Previously we had these reversed -
        // ROME mapped the rich HTML to <summary> and the multi-line plain text to <content>,
        // which broke readers that strip HTML out of summaries or show only the summary.
        SyndContent summary = new SyndContentImpl();
        summary.setType("text/plain");
        summary.setValue(notificationService.resolveMessage(ctx.locale(), notification));
        entry.setDescription(summary);

        SyndContent html = new SyndContentImpl();
        html.setType("text/html");
        html.setValue(renderHtml(notification, ctx, link));
        entry.setContents(new ArrayList<>(List.of(html)));

        // MediaRSS thumbnail for entries that carry an image (currently lost-and-found).
        if (ctx.images()) {
            String imageUrl = resolveImageUrl(notification, ctx);
            if (imageUrl != null) {
                entry.setModules(new ArrayList<>(List.of(mediaModule(imageUrl))));
            }
        }
        return entry;
    }

    private String renderHtml(Notification notification, RenderContext ctx, String link) {
        var sb = new StringBuilder();
        sb.append("<div lang=\"")
                .append(ctx.locale())
                .append(
                        "\" dir=\"auto\" style=\"font-family:system-ui,Segoe UI,Roboto,sans-serif;font-size:14px;line-height:1.5;color:#1c1c1c;max-width:640px\">");

        String category = notificationService.resolveCategory(ctx.locale(), notification.type());
        sb.append(
                        "<div style=\"display:inline-block;padding:2px 8px;border-radius:999px;background:#eef2ff;color:#3730a3;font-size:12px;font-weight:600\">")
                .append(escapeHtml(category))
                .append("</div>");

        String headline = notificationService.resolveMessage(ctx.locale(), notification);
        sb.append("<h2 style=\"margin:8px 0 4px;font-size:16px\">")
                .append(escapeHtml(headline))
                .append("</h2>");

        if (ctx.verbose()) {
            // Per-type detail block with semantic <dl>/<dt>/<dd> markup.
            var details = collectDetails(notification, ctx);
            if (!details.isEmpty()) {
                sb.append(
                        "<dl style=\"border:1px solid #e5e7eb;border-radius:8px;padding:10px 12px;background:#f9fafb;margin:8px 0\">");
                int i = 0;
                for (var entry : details.entrySet()) {
                    if (i++ > 0) sb.append("<br>");
                    // Values can be multi-line (news previews, event descriptions, change
                    // text, markdown tables that were stripped to "col · col · col" rows).
                    // We escape first, then translate the surviving newlines into <br> so
                    // readers see the paragraph structure instead of a wall of text.
                    sb.append("<dt style=\"color:#6b7280;display:inline\">")
                            .append(escapeHtml(entry.getKey()))
                            .append(":</dt> <dd style=\"display:inline;margin:0 0 0 4px\">")
                            .append(escapeHtmlWithLineBreaks(entry.getValue()))
                            .append("</dd>");
                }
                sb.append("</dl>");
            }

            // Inline image with meaningful alt text (lost-and-found description, etc.).
            if (ctx.images()) {
                String imageUrl = resolveImageUrl(notification, ctx);
                if (imageUrl != null) {
                    sb.append("<img src=\"")
                            .append(escapeHtml(imageUrl))
                            .append("\" alt=\"")
                            .append(escapeHtml(resolveImageAlt(notification)))
                            .append("\" style=\"margin-top:8px;max-width:100%;border-radius:8px\">");
                }
            }
        }

        // Action button: WCAG 2.5.5 tap target (≥44px), underlined link affordance.
        sb.append("<p style=\"margin-top:16px\"><a href=\"")
                .append(escapeHtml(link))
                .append(
                        "\" style=\"display:inline-block;padding:12px 16px;background:#3730a3;color:#fff;text-decoration:underline;border-radius:6px;min-height:44px;box-sizing:border-box\">")
                .append(escapeHtml(notificationService.resolveLocalized(ctx.locale(), "ical", "label.link", null)))
                .append("</a></p>");
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Per-type detail extraction. Mirrors the structure of NotificationParams so each
     * variant surfaces the fields a user would expect in the body without opening the app.
     */
    private Map<String, String> collectDetails(Notification notification, RenderContext ctx) {
        var details = new LinkedHashMap<String, String>();
        var params = notification.data().params();
        if (params == null) return details;

        switch (notification.type()) {
            case NEW_NEWS -> {
                if (params instanceof NotificationParams.NewNews p) {
                    putIfPresent(details, label(ctx, "by"), p.author());
                    putSnippetIfPresent(details, label(ctx, "preview", "Preview"), p.preview());
                }
            }
            case NEWS_COMMENT -> {
                if (params instanceof NotificationParams.NewsComment(String newsTitle, String author, String preview)) {
                    putIfPresent(details, label(ctx, "by"), author);
                    putIfPresent(details, label(ctx, "newsTitle", "News"), newsTitle);
                    putSnippetIfPresent(details, label(ctx, "preview", "Preview"), preview);
                }
            }
            case COMMENT_MENTION -> {
                if (params
                        instanceof
                        NotificationParams.CommentMention(String entityTitle, String author, String preview)) {
                    putIfPresent(details, label(ctx, "by"), author);
                    putIfPresent(details, label(ctx, "entity", "Entity"), entityTitle);
                    putSnippetIfPresent(details, label(ctx, "preview", "Preview"), preview);
                }
            }
            case NEW_EVENT -> {
                if (params instanceof NotificationParams.NewEvent(String _, String eventDescription)) {
                    putSnippetIfPresent(details, label(ctx, "description", "Description"), eventDescription);
                    enrichWithEventContext(details, notification, ctx);
                }
            }
            case NEW_EVENTS_BATCH -> {
                if (params
                        instanceof
                        NotificationParams.NewEventsBatch(int count, String eventPreview, LocalDate firstEventDate)) {
                    details.put(label(ctx, "count", "Count"), String.valueOf(count));
                    putIfPresent(details, label(ctx, "events"), eventPreview);
                    if (firstEventDate != null) {
                        details.put(label(ctx, "start", "Start"), formatLocalDate(firstEventDate, ctx.locale()));
                    }
                }
            }
            case EVENT_REGISTRATION_STATUS -> {
                if (params
                        instanceof
                        NotificationParams.EventRegistrationStatus(
                                String eventName,
                                RegistrationStatus status,
                                String eventDescription)) {
                    putIfPresent(
                            details,
                            label(ctx, "status", "Status"),
                            notificationService.resolveStatusWithSymbol(ctx.locale(), status.name()));
                    putIfPresent(details, label(ctx, "event", "Event"), eventName);
                    putSnippetIfPresent(details, label(ctx, "description", "Description"), eventDescription);
                    enrichWithEventContext(details, notification, ctx);
                }
            }
            case EVENT_CANCELLED -> {
                if (params instanceof NotificationParams.EventCancelled(String eventName, String reason)) {
                    putIfPresent(details, label(ctx, "event", "Event"), eventName);
                    putIfPresent(details, label(ctx, "reason"), reason);
                    enrichWithEventContext(details, notification, ctx);
                }
            }
            case EVENT_REMINDER -> {
                if (params
                        instanceof
                        NotificationParams.EventReminder(String eventName, int daysBefore, LocalDate eventDate)) {
                    putIfPresent(details, label(ctx, "event", "Event"), eventName);
                    if (eventDate != null) {
                        details.put(label(ctx, "eventDate"), formatLocalDate(eventDate, ctx.locale()));
                    }
                    details.put(label(ctx, "daysBefore"), String.valueOf(daysBefore));
                    enrichWithEventContext(details, notification, ctx);
                }
            }
            case REGISTRATION_CLOSING -> {
                if (params
                        instanceof
                        NotificationParams.RegistrationClosing(String eventName, int daysBefore, String memberName)) {
                    putIfPresent(details, label(ctx, "event", "Event"), eventName);
                    putIfPresent(details, label(ctx, "member", "Member"), memberName);
                    details.put(label(ctx, "daysBefore"), String.valueOf(daysBefore));
                    enrichWithEventContext(details, notification, ctx);
                }
            }
            case EXCHANGE_NEW_REQUEST -> {
                if (params
                        instanceof
                        NotificationParams.ExchangeNewRequest(String memberName, String inventoryName, String reason)) {
                    putIfPresent(details, label(ctx, "by"), memberName);
                    putIfPresent(details, label(ctx, "inventory", "Inventory"), inventoryName);
                    putIfPresent(details, label(ctx, "reason"), reason);
                    enrichWithInventoryContext(details, notification, ctx);
                }
            }
            case EXCHANGE_STATUS_CHANGE -> {
                if (params
                        instanceof
                        NotificationParams.ExchangeStatusChange(
                                String stepLabel,
                                String inventoryName,
                                StepActor nextActor)) {
                    // The step's own words, because a station names its chain itself
                    putIfPresent(details, label(ctx, "status", "Status"), stepLabel);
                    putIfPresent(details, label(ctx, "inventory", "Inventory"), inventoryName);
                    enrichWithInventoryContext(details, notification, ctx);
                }
            }
            case LOST_AND_FOUND_NEW -> {
                if (params instanceof NotificationParams.LostAndFoundNew(String description)) {
                    putSnippetIfPresent(details, label(ctx, "description", "Description"), description);
                    enrichWithLostAndFoundContext(details, notification, ctx, false);
                }
            }
            case LOST_AND_FOUND_CLAIMED -> {
                if (params instanceof NotificationParams.LostAndFoundClaimed(String name, String description)) {
                    putIfPresent(details, label(ctx, "by"), name);
                    putSnippetIfPresent(details, label(ctx, "description", "Description"), description);
                    enrichWithLostAndFoundContext(details, notification, ctx, true);
                }
            }
            case LENDING_NEW_REQUEST -> {
                if (params instanceof NotificationParams.LendingNewRequest(String stationName, String itemSummary)) {
                    putIfPresent(details, label(ctx, "station", "Station"), stationName);
                    putIfPresent(details, label(ctx, "itemSummary"), itemSummary);
                    enrichWithLendingContext(details, notification, ctx);
                }
            }
            case LENDING_STATUS_CHANGE -> {
                if (params
                        instanceof NotificationParams.LendingStatusChange(String stationName, LendingStatus status)) {
                    putIfPresent(
                            details,
                            label(ctx, "status", "Status"),
                            notificationService.resolveStatusWithSymbol(ctx.locale(), status.name()));
                    putIfPresent(details, label(ctx, "station", "Station"), stationName);
                    enrichWithLendingContext(details, notification, ctx);
                }
            }
            case LENDING_NEW_MESSAGE -> {
                if (params instanceof NotificationParams.LendingNewMessage(String stationName, String senderName)) {
                    putIfPresent(details, label(ctx, "by"), senderName);
                    putIfPresent(details, label(ctx, "station", "Station"), stationName);
                    enrichWithLendingContext(details, notification, ctx);
                }
            }
            case BOARD_TICKET_UPDATE -> {
                if (params
                        instanceof
                        NotificationParams.BoardTicketUpdate(
                                String boardName,
                                String ticketKey,
                                String changeDescription)) {
                    putIfPresent(details, label(ctx, "ticketKey"), ticketKey);
                    putIfPresent(details, label(ctx, "board"), boardName);
                    putSnippetIfPresent(details, label(ctx, "change", "Change"), changeDescription);
                    enrichWithTicketContext(details, notification, ctx);
                }
            }
            case STORAGE_WARNING -> {
                if (params
                        instanceof
                        NotificationParams.StorageWarning(
                                int usedPercent,
                                String usedFormatted,
                                String quotaFormatted)) {
                    details.put(label(ctx, "usedPercent"), usedPercent + "%");
                    putIfPresent(details, label(ctx, "used"), usedFormatted);
                    putIfPresent(details, label(ctx, "quota"), quotaFormatted);
                    enrichWithStorageBreakdown(details, notification, ctx);
                }
            }
            case REGISTRATION_DEADLINE_EXPIRED -> {
                if (params
                        instanceof NotificationParams.RegistrationDeadlineExpired(String eventName, int pendingCount)) {
                    putIfPresent(details, label(ctx, "event", "Event"), eventName);
                    details.put(label(ctx, "pendingCount"), String.valueOf(pendingCount));
                }
            }
            case WAITLIST_NEW_ENTRY -> {
                if (params instanceof NotificationParams.WaitlistNewEntry(String childName, String listName)) {
                    putIfPresent(details, label(ctx, "child", "Child"), childName);
                    putIfPresent(details, label(ctx, "list", "List"), listName);
                }
            }
            case WAITLIST_PUBLIC_REGISTRATION -> {
                if (params
                        instanceof NotificationParams.WaitlistPublicRegistration(String childName, String listName)) {
                    putIfPresent(details, label(ctx, "child", "Child"), childName);
                    putIfPresent(details, label(ctx, "list", "List"), listName);
                }
            }
            case MEMBER_ADDED_TO_GROUP -> {
                if (params instanceof NotificationParams.MemberAddedToGroup(String groupName, String addedByName)) {
                    putIfPresent(details, label(ctx, "group", "Group"), groupName);
                    putIfPresent(details, label(ctx, "by"), addedByName);
                }
            }
            case PROFILE_FIELD_CHANGED -> {
                if (params instanceof NotificationParams.ProfileFieldChanged(String memberName, String fieldName)) {
                    putIfPresent(details, label(ctx, "member", "Member"), memberName);
                    putIfPresent(details, label(ctx, "field", "Field"), fieldName);
                }
            }
            case PROCUREMENT_REQUESTED, PROCUREMENT_FULFILLED -> {
                if (params instanceof NotificationParams.ProcurementRequested(String name)) {
                    putIfPresent(details, label(ctx, "inventory", "Inventory"), name);
                } else if (params instanceof NotificationParams.ProcurementFulfilled(String inventoryName)) {
                    putIfPresent(details, label(ctx, "inventory", "Inventory"), inventoryName);
                }
                enrichWithInventoryContext(details, notification, ctx);
            }
            case NEW_FORM -> {
                if (params instanceof NotificationParams.NewForm(String title)) {
                    putIfPresent(details, label(ctx, "title", "Title"), title);
                }
            }
            case PROCEDURE_ASSIGNED -> {
                if (params
                        instanceof NotificationParams.ProcedureAssigned(String procedureName, String assignedByName)) {
                    putIfPresent(details, label(ctx, "procedure", "Procedure"), procedureName);
                    putIfPresent(details, label(ctx, "by"), assignedByName);
                    enrichWithProcedureProgress(details, notification, ctx);
                }
            }
            case PROCEDURE_RESOLVED -> {
                if (params instanceof NotificationParams.ProcedureResolvedParams(String procedureName)) {
                    putIfPresent(details, label(ctx, "procedure", "Procedure"), procedureName);
                    enrichWithProcedureProgress(details, notification, ctx);
                }
            }
            case PROCEDURE_REOPENED -> {
                if (params instanceof NotificationParams.ProcedureReopenedParams(String procedureName)) {
                    putIfPresent(details, label(ctx, "procedure", "Procedure"), procedureName);
                    enrichWithProcedureProgress(details, notification, ctx);
                }
            }
            case PROCEDURE_ITEM_CHECKED -> {
                if (params
                        instanceof
                        NotificationParams.ProcedureItemCheckedParams(
                                String procedureName,
                                String itemTitle,
                                String checkedByName)) {
                    putIfPresent(details, label(ctx, "procedure", "Procedure"), procedureName);
                    putIfPresent(details, label(ctx, "item"), itemTitle);
                    putIfPresent(details, label(ctx, "by"), checkedByName);
                    enrichWithProcedureProgress(details, notification, ctx);
                }
            }
        }
        return details;
    }

    // -- author / category --

    /**
     * Loads the event referenced by a notification's {@code link.routeParams().id} and
     * appends its start/end time and every non-empty custom field value to the details
     * block. Silently skips when the id is missing or the event has been deleted -
     * notification feeds should never fail because of a stale reference.
     */
    private void enrichWithEventContext(Map<String, String> details, Notification notification, RenderContext ctx) {
        Integer eventId = extractLinkId(notification);
        if (eventId == null) return;
        try {
            var event = crudService.findById(eventId).orElse(null);
            if (event == null) return;
            var start = event.startTime();
            var end = event.endTime();
            // Range-merge: a same-day event collapses to a single "When: 15 Sep 17:00 – 19:00"
            // row so users read one fact, not two. Cross-day events keep separate rows to
            // preserve the absolute date on each side.
            if (start != null && end != null && sameLocalDate(start, end)) {
                details.put(label(ctx, "when", "When"), formatRangeSameDay(start, end, ctx.locale()));
            } else {
                if (start != null) {
                    details.put(label(ctx, "start", "Start"), formatInstant(start, ctx.locale()));
                }
                if (end != null) {
                    details.put(label(ctx, "end", "End"), formatInstant(end, ctx.locale()));
                }
            }
            // Recurrence label so the user knows it's "weekly" / "monthly" / etc. at a glance.
            if (event.eventType() != null && event.eventType() != StationEvent.EventType.ONE_TIME) {
                String recurrence = notificationService.resolveLocalized(
                        ctx.locale(), "ical", "eventType." + event.eventType().name(), null);
                if (!recurrence.equals("eventType." + event.eventType().name())) {
                    details.put(label(ctx, "recurrence", "Recurrence"), recurrence);
                }
            }
            // Registration controls - surfaced so members can act on the notification body
            // alone without round-tripping to the detail view.
            if (event.requiresRegistration() && event.registrationDeadline() != null) {
                details.put(
                        label(ctx, "deadline", "Deadline"), formatInstant(event.registrationDeadline(), ctx.locale()));
            }
            if (event.registrationLimit() != null) {
                details.put(label(ctx, "limit", "Limit"), String.valueOf(event.registrationLimit()));
            }
            // Custom event fields - skip blank values, surface every set value verbatim.
            for (var field : eventFieldService.findByEvent(eventId)) {
                if (field.value() == null || field.value().isBlank()) continue;
                details.put(field.name(), field.value().trim());
            }
        } catch (Exception ignored) {
            // Telemetry-grade enrichment: never block a feed render on a side-effect failure.
        }
    }

    /**
     * Loads the lost-and-found item referenced by a notification's {@code link.routeParams().id}
     * and appends its find date (always) plus claim date (for the claimed variant) to the body.
     * Silently no-ops on a missing id or a stale reference so a deleted item never tanks the
     * whole feed render.
     */
    private void enrichWithLostAndFoundContext(
            Map<String, String> details, Notification notification, RenderContext ctx, boolean includeClaim) {
        Integer itemId = extractLinkId(notification);
        if (itemId == null) return;
        try {
            var item = lostAndFoundService.findById(itemId).orElse(null);
            if (item == null) return;
            if (item.foundAt() != null) {
                details.put(label(ctx, "foundOn", "Found on"), formatLocalDate(item.foundAt(), ctx.locale()));
            }
            if (includeClaim && item.claimedAt() != null) {
                details.put(label(ctx, "claimedOn", "Claimed on"), formatInstant(item.claimedAt(), ctx.locale()));
            }
        } catch (Exception ignored) {
            // Telemetry-grade enrichment: never block a feed render on a side-effect failure.
        }
    }

    /**
     * Loads the lending request referenced by a notification's {@code link.routeParams().id} and
     * adds a "Needed: 5 Oct – 7 Oct" range row. Range-merges into a single line so users read one
     * fact (when it's needed) instead of two separate from/to rows. Silently skips on a missing
     * id, missing dates, or a stale reference.
     */
    private void enrichWithLendingContext(Map<String, String> details, Notification notification, RenderContext ctx) {
        Integer requestId = extractLinkId(notification);
        if (requestId == null) return;
        try {
            var request = lendingService.findRequest(requestId).orElse(null);
            if (request == null) return;
            var from = request.requestedDateFrom();
            var to = request.requestedDateTo();
            if (from != null && to != null) {
                details.put(label(ctx, "needed", "Needed"), formatLocalDateRange(from, to, ctx.locale()));
            } else if (from != null) {
                details.put(label(ctx, "needed", "Needed"), formatLocalDate(from, ctx.locale()));
            }
        } catch (Exception ignored) {
            // Telemetry-grade enrichment: never block a feed render on a side-effect failure.
        }
    }

    // -- image embedding --

    /**
     * Loads the inventory referenced by a notification's {@code link.routeParams().id} and adds
     * its ownership flow (Organisation-owned / Member-owned / Mixed) to the body. Used by
     * procurement and exchange notifications so managers see the inventory's nature without
     * opening the app. Silently no-ops on missing id or stale reference.
     */
    private void enrichWithInventoryContext(Map<String, String> details, Notification notification, RenderContext ctx) {
        Integer inventoryId = extractLinkId(notification);
        if (inventoryId == null) return;
        try {
            var inv = inventoryService.findById(inventoryId).orElse(null);
            if (inv == null || inv.inventoryType() == null) return;
            details.put(label(ctx, "type", "Type"), resolveInventoryTypeLabel(ctx.locale(), inv.inventoryType()));
        } catch (Exception ignored) {
            // Telemetry-grade enrichment: never block a feed render on a side-effect failure.
        }
    }

    private String resolveInventoryTypeLabel(String locale, InventoryType type) {
        String key = type.name();
        String label = notificationService.resolveLocalized(locale, "inventoryType", key, null);
        return label.equals(key) ? key : label;
    }

    /**
     * Loads the board ticket referenced by the notification's {@code link.routeParams().ticketId}
     * and surfaces title, assignee, and priority in the body. Skips silently on missing id or
     * stale reference. The link route itself ({@code ticket-detail}) currently can't be
     * resolved to a working deep link (it expects {@code boardKey}/{@code ticketNumber}, not
     * numeric ids) - that's a separate bug tracked outside this enrichment.
     */
    private void enrichWithTicketContext(Map<String, String> details, Notification notification, RenderContext ctx) {
        Integer ticketId = extractLinkParam(notification, "ticketId");
        if (ticketId == null) return;
        try {
            var ticket = boardTicketService.findById(ticketId).orElse(null);
            if (ticket == null) return;
            putIfPresent(details, label(ctx, "title", "Title"), ticket.title());
            if (ticket.assignee() != null && ticket.assignee().name() != null) {
                details.put(
                        label(ctx, "assignee", "Assignee"), ticket.assignee().name());
            }
            if (ticket.priority() != null) {
                details.put(
                        label(ctx, "priority", "Priority"),
                        resolveTicketPriorityLabel(ctx.locale(), ticket.priority()));
            }
        } catch (Exception ignored) {
            // Telemetry-grade enrichment: never block a feed render on a side-effect failure.
        }
    }

    // -- helpers --

    private String resolveTicketPriorityLabel(String locale, TicketPriority priority) {
        String key = priority.name();
        String label = notificationService.resolveLocalized(locale, "ticketPriority", key, null);
        return label.equals(key) ? key : label;
    }

    /**
     * Counts checked vs total procedure items for the referenced procedure and surfaces it as
     * a single range-merged "Progress: 5 of 12 items" row. Silently skips on missing id or
     * empty item list - a procedure with no items has no meaningful progress to report.
     */
    private void enrichWithProcedureProgress(
            Map<String, String> details, Notification notification, RenderContext ctx) {
        Integer procedureId = extractLinkId(notification);
        if (procedureId == null) return;
        try {
            var items = procedureService.findItems(procedureId);
            if (items.isEmpty()) return;
            long checked = items.stream().filter(ProcedureItem::checked).count();
            int total = items.size();
            String value = notificationService.resolveLocalized(
                    ctx.locale(),
                    "feedLabel",
                    "progressFormat",
                    Map.of("checked", String.valueOf(checked), "total", String.valueOf(total)));
            details.put(label(ctx, "progress", "Progress"), value);
        } catch (Exception ignored) {
            // Telemetry-grade enrichment: never block a feed render on a side-effect failure.
        }
    }

    /**
     * Loads per-category storage usage for the warning's station and appends the top categories
     * as a bulleted child list ({@code Largest categories:\n  • Knowledge base: 4.8 GB\n ...}).
     * The station id rides in the link routeParams so the renderer doesn't need a memberId →
     * station lookup. Silently no-ops on missing id, empty usage, or repository failure.
     */
    private void enrichWithStorageBreakdown(Map<String, String> details, Notification notification, RenderContext ctx) {
        Integer stationId = extractLinkParam(notification, "stationId");
        if (stationId == null) return;
        try {
            var usage = storageQuotaService.getUsage(stationId);
            if (usage.isEmpty()) return;
            // Sort by bytes descending, pick top 5, render as bullet list. Skipping zero-byte
            // categories keeps the body tidy for stations that don't use every feature.
            var top = usage.stream()
                    .filter(u -> u.totalBytes() > 0)
                    .sorted((a, b) -> Long.compare(b.totalBytes(), a.totalBytes()))
                    .limit(5)
                    .toList();
            if (top.isEmpty()) return;
            var sb = new StringBuilder();
            for (StorageUsage u : top) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("• ")
                        .append(resolveStorageCategoryLabel(
                                ctx.locale(), u.category().name()))
                        .append(": ")
                        .append(SizeParser.formatBytes(u.totalBytes()));
            }
            details.put(label(ctx, "largestCategories", "Largest categories"), sb.toString());
        } catch (Exception ignored) {
            // Telemetry-grade enrichment: never block a feed render on a side-effect failure.
        }
    }

    private String resolveStorageCategoryLabel(String locale, String categoryName) {
        String label = notificationService.resolveLocalized(locale, "storageCategory", categoryName, null);
        return label.equals(categoryName) ? categoryName : label;
    }

    private String label(RenderContext ctx, String key) {
        return notificationService.resolveLocalized(ctx.locale(), "feedLabel", key, null);
    }

    /**
     * Resolves a {@code feedLabel} entry, falling back to the provided default when the bundle
     * doesn't carry the key. Lets us add new detail fields without forcing every bundle update
     * in lockstep.
     */
    private String label(RenderContext ctx, String key, String fallback) {
        String value = notificationService.resolveLocalized(ctx.locale(), "feedLabel", key, null);
        return key.equals(value) ? fallback : value;
    }

    /**
     * Per-render context. Shared across all entries in a single feed render.
     *
     * @param locale     resolved feed locale ({@code de}/{@code en})
     * @param baseUrl    public base URL of the deployment, used for deep links
     * @param feedToken  the feed token, used to construct token-scoped image URLs so readers
     *                   can fetch them without authentication
     * @param verbose    when {@code false} only the headline + deep link are rendered, no
     *                   detail block - for compact feed presets
     * @param images     when {@code false} {@code <img>} tags and MediaRSS thumbnails are
     *                   suppressed (metered connections, screen reader minimisation)
     * @param stationUid UUID of the station that owns this feed, appended to every deep link
     *                   as {@code ?station=<uid>} so the recipient lands in the right station
     *                   context after login even when they belong to several stations
     */
    public record RenderContext(
            String locale, String baseUrl, String feedToken, boolean verbose, boolean images, UUID stationUid) {}
}
