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
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link SyndEntry} entries for the personal RSS/Atom notification feeds.
 *
 * <p>Each entry carries two content bodies — a rich, semantic HTML block (rendered by
 * Thunderbird, Feedly, NetNewsWire, etc.) and a plain-text fallback for clients that strip
 * HTML — plus per-entry author, categories, and an embedded image when the notification
 * surfaces a lost-and-found item.
 */
@Singleton
public class NotificationFeedRenderer {
    private final NotificationService notificationService;

    @Inject
    public NotificationFeedRenderer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Per-render context. Shared across all entries in a single feed render.
     *
     * @param locale    resolved feed locale ({@code de}/{@code en})
     * @param baseUrl   public base URL of the deployment, used for deep links
     * @param feedToken the feed token, used to construct token-scoped image URLs so readers
     *                  can fetch them without authentication
     * @param verbose   when {@code false} only the headline + deep link are rendered, no
     *                  detail block — for compact feed presets
     * @param images    when {@code false} {@code <img>} tags and MediaRSS thumbnails are
     *                  suppressed (metered connections, screen reader minimisation)
     */
    public record RenderContext(String locale, String baseUrl, String feedToken, boolean verbose, boolean images) {}

    /** Renders a single notification into a {@link SyndEntry}. */
    public SyndEntry render(Notification notification, RenderContext ctx) {
        SyndEntry entry = new SyndEntryImpl();
        entry.setTitle(notificationService.resolveCategory(ctx.locale(), notification.type()));
        entry.setUri("urn:ember:notification:" + notification.id());
        entry.setPublishedDate(Date.from(notification.createdAt()));
        entry.setUpdatedDate(Date.from(notification.createdAt()));

        // Every entry gets a link. Falls back to the dashboard so readers always have a
        // navigable target even if a notification has no deep link metadata.
        String link = notificationService.resolveNotificationUrl(ctx.baseUrl(), notification.data());
        entry.setLink(link != null ? link : ctx.baseUrl() + "/station/dashboard/overview");

        // Author column in readers like Thunderbird.
        String author = resolveAuthor(notification);
        if (author != null) entry.setAuthor(author);

        // Categories let users filter by notification type.
        entry.setCategories(List.of(category(notification.type())));

        // Plain-text fallback for readers that strip HTML.
        SyndContent plain = new SyndContentImpl();
        plain.setType("text/plain");
        plain.setValue(notificationService.resolveFeedBody(ctx.locale(), notification));
        entry.setContents(new ArrayList<>(List.of(plain)));

        // Rich HTML body (description) — readers prefer this over plain when both exist.
        SyndContent html = new SyndContentImpl();
        html.setType("text/html");
        html.setValue(renderHtml(notification, ctx, link));
        entry.setDescription(html);

        // MediaRSS thumbnail for entries that carry an image (currently lost-and-found).
        if (ctx.images()) {
            String imageUrl = resolveImageUrl(notification, ctx);
            if (imageUrl != null) {
                entry.setModules(new ArrayList<>(List.of(mediaModule(imageUrl))));
            }
        }
        return entry;
    }

    // -- HTML body --

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
                    sb.append("<dt style=\"color:#6b7280;display:inline\">")
                            .append(escapeHtml(entry.getKey()))
                            .append(":</dt> <dd style=\"display:inline;margin:0 0 0 4px\">")
                            .append(escapeHtml(entry.getValue()))
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
                    if (notBlank(p.preview())) details.put("Preview", p.preview());
                }
            }
            case NEWS_COMMENT -> {
                if (params instanceof NotificationParams.NewsComment p) {
                    putIfPresent(details, label(ctx, "by"), p.author());
                    putIfPresent(details, label(ctx, "newsTitle", "News"), p.newsTitle());
                    if (notBlank(p.preview())) details.put("Preview", p.preview());
                }
            }
            case COMMENT_MENTION -> {
                if (params instanceof NotificationParams.CommentMention p) {
                    putIfPresent(details, label(ctx, "by"), p.author());
                    putIfPresent(details, label(ctx, "entity", "Entity"), p.entityTitle());
                }
            }
            case NEW_EVENT -> {
                if (params instanceof NotificationParams.NewEvent p) {
                    if (notBlank(p.eventDescription())) details.put(p.title(), p.eventDescription());
                }
            }
            case NEW_EVENTS_BATCH -> {
                if (params instanceof NotificationParams.NewEventsBatch p) {
                    details.put(label(ctx, "count", "Count"), String.valueOf(p.count()));
                    putIfPresent(details, label(ctx, "events"), p.eventPreview());
                }
            }
            case EVENT_REGISTRATION_STATUS -> {
                if (params instanceof NotificationParams.EventRegistrationStatus p) {
                    putIfPresent(
                            details,
                            label(ctx, "status", "Status"),
                            statusWithSymbol(p.status().name()));
                    putIfPresent(details, label(ctx, "event", "Event"), p.eventName());
                    putIfPresent(details, "Preview", p.eventDescription());
                }
            }
            case EVENT_CANCELLED -> {
                if (params instanceof NotificationParams.EventCancelled p) {
                    putIfPresent(details, label(ctx, "event", "Event"), p.eventName());
                    putIfPresent(details, label(ctx, "reason"), p.reason());
                }
            }
            case EVENT_REMINDER -> {
                if (params instanceof NotificationParams.EventReminder p) {
                    putIfPresent(details, label(ctx, "event", "Event"), p.eventName());
                    putIfPresent(details, label(ctx, "eventDate"), p.eventDate());
                    details.put(label(ctx, "daysBefore"), String.valueOf(p.daysBefore()));
                }
            }
            case EXCHANGE_NEW_REQUEST -> {
                if (params instanceof NotificationParams.ExchangeNewRequest p) {
                    putIfPresent(details, label(ctx, "by"), p.memberName());
                    putIfPresent(details, "Inventory", p.inventoryName());
                    putIfPresent(details, label(ctx, "reason"), p.reason());
                }
            }
            case EXCHANGE_STATUS_CHANGE -> {
                if (params instanceof NotificationParams.ExchangeStatusChange p) {
                    putIfPresent(
                            details,
                            label(ctx, "status", "Status"),
                            statusWithSymbol(p.status().name()));
                    putIfPresent(details, "Inventory", p.inventoryName());
                    putIfPresent(details, label(ctx, "reason"), p.reason());
                }
            }
            case LOST_AND_FOUND_NEW -> {
                if (params instanceof NotificationParams.LostAndFoundNew p) {
                    putIfPresent(details, "Description", p.description());
                }
            }
            case LOST_AND_FOUND_CLAIMED -> {
                if (params instanceof NotificationParams.LostAndFoundClaimed p) {
                    putIfPresent(details, label(ctx, "by"), p.name());
                    putIfPresent(details, "Description", p.description());
                }
            }
            case LENDING_NEW_REQUEST -> {
                if (params instanceof NotificationParams.LendingNewRequest p) {
                    putIfPresent(details, label(ctx, "station", "Station"), p.stationName());
                    putIfPresent(details, label(ctx, "itemSummary"), p.itemSummary());
                }
            }
            case LENDING_STATUS_CHANGE -> {
                if (params instanceof NotificationParams.LendingStatusChange p) {
                    putIfPresent(
                            details, label(ctx, "status", "Status"), p.status().name());
                    putIfPresent(details, label(ctx, "station", "Station"), p.stationName());
                }
            }
            case LENDING_NEW_MESSAGE -> {
                if (params instanceof NotificationParams.LendingNewMessage p) {
                    putIfPresent(details, label(ctx, "by"), p.senderName());
                    putIfPresent(details, label(ctx, "station", "Station"), p.stationName());
                }
            }
            case BOARD_TICKET_UPDATE -> {
                if (params instanceof NotificationParams.BoardTicketUpdate p) {
                    putIfPresent(details, label(ctx, "ticketKey"), p.ticketKey());
                    putIfPresent(details, label(ctx, "board"), p.boardName());
                    putIfPresent(details, "Change", p.changeDescription());
                }
            }
            case STORAGE_WARNING -> {
                if (params instanceof NotificationParams.StorageWarning p) {
                    details.put(label(ctx, "usedPercent"), p.usedPercent() + "%");
                    putIfPresent(details, label(ctx, "used"), p.usedFormatted());
                    putIfPresent(details, label(ctx, "quota"), p.quotaFormatted());
                }
            }
            case REGISTRATION_DEADLINE_EXPIRED -> {
                if (params instanceof NotificationParams.RegistrationDeadlineExpired p) {
                    putIfPresent(details, label(ctx, "event", "Event"), p.eventName());
                    details.put(label(ctx, "pendingCount"), String.valueOf(p.pendingCount()));
                }
            }
            case WAITLIST_NEW_ENTRY -> {
                if (params instanceof NotificationParams.WaitlistNewEntry p) {
                    putIfPresent(details, label(ctx, "child", "Child"), p.childName());
                    putIfPresent(details, label(ctx, "list", "List"), p.listName());
                }
            }
            case WAITLIST_PUBLIC_REGISTRATION -> {
                if (params instanceof NotificationParams.WaitlistPublicRegistration p) {
                    putIfPresent(details, label(ctx, "child", "Child"), p.childName());
                    putIfPresent(details, label(ctx, "list", "List"), p.listName());
                }
            }
            case MEMBER_ADDED_TO_GROUP -> {
                if (params instanceof NotificationParams.MemberAddedToGroup p) {
                    putIfPresent(details, label(ctx, "group", "Group"), p.groupName());
                }
            }
            case PROFILE_FIELD_CHANGED -> {
                if (params instanceof NotificationParams.ProfileFieldChanged p) {
                    putIfPresent(details, label(ctx, "member", "Member"), p.memberName());
                    putIfPresent(details, label(ctx, "field", "Field"), p.fieldName());
                }
            }
            case PROCUREMENT_REQUESTED, PROCUREMENT_FULFILLED -> {
                if (params instanceof NotificationParams.ProcurementRequested p) {
                    putIfPresent(details, "Inventory", p.inventoryName());
                } else if (params instanceof NotificationParams.ProcurementFulfilled p) {
                    putIfPresent(details, "Inventory", p.inventoryName());
                }
            }
            case NEW_FORM -> {
                if (params instanceof NotificationParams.NewForm p) {
                    putIfPresent(details, "Title", p.title());
                }
            }
            case PROCEDURE_ASSIGNED -> {
                if (params instanceof NotificationParams.ProcedureAssigned p) {
                    putIfPresent(details, label(ctx, "procedure", "Procedure"), p.procedureName());
                    putIfPresent(details, label(ctx, "by"), p.assignedByName());
                }
            }
            case PROCEDURE_RESOLVED -> {
                if (params instanceof NotificationParams.ProcedureResolvedParams p) {
                    putIfPresent(details, label(ctx, "procedure", "Procedure"), p.procedureName());
                }
            }
            case PROCEDURE_REOPENED -> {
                if (params instanceof NotificationParams.ProcedureReopenedParams p) {
                    putIfPresent(details, label(ctx, "procedure", "Procedure"), p.procedureName());
                }
            }
            case PROCEDURE_ITEM_CHECKED -> {
                if (params instanceof NotificationParams.ProcedureItemCheckedParams p) {
                    putIfPresent(details, label(ctx, "procedure", "Procedure"), p.procedureName());
                    putIfPresent(details, label(ctx, "item"), p.itemTitle());
                    putIfPresent(details, label(ctx, "by"), p.checkedByName());
                }
            }
        }
        return details;
    }

    // -- author / category --

    /**
     * Returns the actor on the notification, if any — used by readers as the entry's author.
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

    private static SyndCategory category(NotificationType type) {
        var c = new SyndCategoryImpl();
        c.setName(type.name());
        return c;
    }

    // -- image embedding --

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
        if (params instanceof NotificationParams.LostAndFoundNew p) return p.description();
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

    // -- helpers --

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

    private static void putIfPresent(Map<String, String> details, String key, String value) {
        if (notBlank(value)) details.put(key, value);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Unicode markers prefixed to status labels so the registration/exchange/etc. state stays
     * visible in monochrome rendering and for users with colour-blindness.
     */
    private static String statusWithSymbol(String statusName) {
        return switch (statusName) {
            case "ACCEPTED" -> "\u2713 " + statusName;
            case "DENIED", "DECLINED" -> "\u2717 " + statusName;
            case "PENDING" -> "\u2026 " + statusName;
            case "WITHDRAWN" -> "\u21B6 " + statusName;
            default -> statusName;
        };
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
