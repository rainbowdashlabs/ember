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
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URISyntaxException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Builds {@link SyndEntry} entries for the personal RSS/Atom notification feeds.
 *
 * <p>Each entry carries two content bodies - a rich, semantic HTML block (rendered by
 * Thunderbird, Feedly, NetNewsWire, etc.) and a plain-text fallback for clients that strip
 * HTML - plus per-entry author, categories, and an embedded image where the notification has one.
 *
 * <p>This class writes what every entry has. What a notification is about, who acted and what
 * picture it shows come from the {@link FeedDetailsContributor} of the feature it belongs to, so
 * the renderer knows no feature.
 *
 * <p>The summary is the short preview a reader shows in its inbox row and the content the rich body
 * shown on expand; readers that strip HTML out of summaries or show only the summary still get a
 * sentence. Every entry has a link, the dashboard where the notification names none. Two categories
 * are set: the localised label people see and the raw type name scripts can match on across locales,
 * the latter under its own scheme so readers that fold equal terms keep both.
 */
@Singleton
public class NotificationFeedRenderer {
    private final NotificationText notificationText;
    private final StationRepository stationRepository;
    private final Set<FeedDetailsContributor> contributors;

    @Inject
    public NotificationFeedRenderer(
            NotificationText notificationText,
            StationRepository stationRepository,
            Set<FeedDetailsContributor> contributors) {
        this.notificationText = notificationText;
        this.stationRepository = stationRepository;
        this.contributors = contributors;
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
     * A MediaRSS thumbnail for an entry's picture. The URL is built here, so should it still be
     * refused the entry goes without the module rather than the whole feed failing.
     */
    private static Module mediaModule(String imageUrl) {
        var module = new MediaEntryModuleImpl();
        try {
            var content = new MediaContent(new UrlReference(imageUrl));
            content.setMedium("image");
            module.setMediaContents(new MediaContent[] {content});
        } catch (URISyntaxException ignored) {
        }
        return module;
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
     * The clock one station's times are written in, UTC where the station is unknown.
     */
    private ZoneId zoneOf(Integer stationId) {
        if (stationId == null) return ZoneOffset.UTC;
        return StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
    }

    /**
     * Renders a single notification into a {@link SyndEntry}. The title is the rich, entity-aware
     * one, so an inbox row can be scanned without expanding the entry.
     */
    public SyndEntry render(Notification notification, RenderContext ctx) {
        SyndEntry entry = new SyndEntryImpl();
        entry.setTitle(notificationText.resolveFeedTitle(ctx.locale(), notification));
        entry.setUri("urn:ember:notification:" + notification.id());
        entry.setPublishedDate(Date.from(notification.createdAt()));
        entry.setUpdatedDate(Date.from(notification.createdAt()));

        String link = notificationText.resolveNotificationUrl(ctx.baseUrl(), ctx.stationUid(), notification.data());
        String fallback = ctx.baseUrl() + "/station/dashboard/overview";
        if (ctx.stationUid() != null) fallback = fallback + "?station=" + ctx.stationUid();
        entry.setLink(link != null ? link : fallback);

        String author = author(notification);
        if (author != null) entry.setAuthor(author);

        entry.setCategories(List.of(
                category(notificationText.resolveCategory(ctx.locale(), notification.type())),
                schemedCategory(notification.type().name(), "urn:ember:notification-type")));

        SyndContent summary = new SyndContentImpl();
        summary.setType("text/plain");
        summary.setValue(notificationText.resolveMessage(ctx.locale(), notification));
        entry.setDescription(summary);

        FeedImage image = image(notification);
        SyndContent html = new SyndContentImpl();
        html.setType("text/html");
        html.setValue(renderHtml(notification, ctx, link, image));
        entry.setContents(new ArrayList<>(List.of(html)));

        if (ctx.images()) {
            String imageUrl = imageUrl(image, ctx);
            if (imageUrl != null) {
                entry.setModules(new ArrayList<>(List.of(mediaModule(imageUrl))));
            }
        }
        return entry;
    }

    /** The actor on the notification, which readers show as the entry's author. */
    private String author(Notification notification) {
        var params = notification.data().params();
        if (params == null) return null;
        return contributors.stream()
                .map(contributor -> contributor.author(params))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private FeedImage image(Notification notification) {
        var params = notification.data().params();
        if (params == null) return null;
        return contributors.stream()
                .map(contributor -> contributor.image(params, notification))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Where a reader fetches an entry's picture: below the feed token, so no sign-in is needed. An
     * entry without a picture, or a render without a token, has none.
     */
    private static String imageUrl(FeedImage image, RenderContext ctx) {
        if (image == null || ctx.feedToken() == null) return null;
        return ctx.baseUrl() + "/api/v1/public/feed/" + ctx.feedToken() + "/" + image.path();
    }

    /**
     * The rich body: category badge, headline, and in verbose mode the detail rows and the picture,
     * then the action button (a tap target of at least 44px with an underlined link, as WCAG 2.5.5
     * asks). Detail values are escaped before their line breaks become {@code <br>}, so a multi-line
     * preview keeps its paragraphs.
     */
    private String renderHtml(Notification notification, RenderContext ctx, String link, FeedImage image) {
        var sb = new StringBuilder();
        sb.append("<div lang=\"")
                .append(ctx.locale())
                .append(
                        "\" dir=\"auto\" style=\"font-family:system-ui,Segoe UI,Roboto,sans-serif;font-size:14px;line-height:1.5;color:#1c1c1c;max-width:640px\">");

        String category = notificationText.resolveCategory(ctx.locale(), notification.type());
        sb.append(
                        "<div style=\"display:inline-block;padding:2px 8px;border-radius:999px;background:#eef2ff;color:#3730a3;font-size:12px;font-weight:600\">")
                .append(escapeHtml(category))
                .append("</div>");

        String headline = notificationText.resolveMessage(ctx.locale(), notification);
        sb.append("<h2 style=\"margin:8px 0 4px;font-size:16px\">")
                .append(escapeHtml(headline))
                .append("</h2>");

        if (ctx.verbose()) {
            appendDetails(sb, collectDetails(notification, ctx));
            if (ctx.images()) {
                String imageUrl = imageUrl(image, ctx);
                if (imageUrl != null) {
                    sb.append("<img src=\"")
                            .append(escapeHtml(imageUrl))
                            .append("\" alt=\"")
                            .append(escapeHtml(image.alt()))
                            .append("\" style=\"margin-top:8px;max-width:100%;border-radius:8px\">");
                }
            }
        }

        sb.append("<p style=\"margin-top:16px\"><a href=\"")
                .append(escapeHtml(link))
                .append(
                        "\" style=\"display:inline-block;padding:12px 16px;background:#3730a3;color:#fff;text-decoration:underline;border-radius:6px;min-height:44px;box-sizing:border-box\">")
                .append(escapeHtml(notificationText.resolveLocalized(ctx.locale(), "ical", "label.link", null)))
                .append("</a></p>");
        sb.append("</div>");
        return sb.toString();
    }

    private static void appendDetails(StringBuilder sb, Map<String, String> details) {
        if (details.isEmpty()) return;
        sb.append(
                "<dl style=\"border:1px solid #e5e7eb;border-radius:8px;padding:10px 12px;background:#f9fafb;margin:8px 0\">");
        int i = 0;
        for (var entry : details.entrySet()) {
            if (i++ > 0) sb.append("<br>");
            sb.append("<dt style=\"color:#6b7280;display:inline\">")
                    .append(escapeHtml(entry.getKey()))
                    .append(":</dt> <dd style=\"display:inline;margin:0 0 0 4px\">")
                    .append(escapeHtmlWithLineBreaks(entry.getValue()))
                    .append("</dd>");
        }
        sb.append("</dl>");
    }

    /** The detail rows the notification's feature contributes. */
    private Map<String, String> collectDetails(Notification notification, RenderContext ctx) {
        var details = new FeedDetails(notificationText, this::zoneOf, notification, ctx.locale());
        var params = notification.data().params();
        if (params == null) return details.rows();
        for (var contributor : contributors) {
            contributor.contribute(params, notification, details);
        }
        return details.rows();
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
