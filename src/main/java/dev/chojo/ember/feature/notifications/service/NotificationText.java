/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.feature.notifications.entity.DigestGroup;
import dev.chojo.ember.feature.notifications.entity.LinkHome;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.i18n.Localizer;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * How a notification reads: its message, its feed title, its category and the address it leads to,
 * in the reader's language.
 *
 * <p>A notification records what happened, not how it reads. The language is only known when it is
 * shown, in the app, in a mail or in a feed, so every one of those asks here and all of them read
 * the same words.
 *
 * <p>The address a link opens comes from {@link NotificationPages}, the one table of pages for
 * stations and associations alike.
 */
@Singleton
public class NotificationText {
    /** Default cap for entity-identifier fragments embedded in feed titles. */
    public static final int TITLE_FRAGMENT_MAX = 80;

    /** Default cap for free-form body snippets (previews, descriptions). */
    public static final int BODY_SNIPPET_MAX = 500;

    private static final Localizer LOCALIZER = new Localizer();

    /** Param keys that drive pluralisation in {@link #resolveMessage}. */
    private static final List<String> COUNT_PARAMS = List.of("count", "days", "daysBefore", "pendingCount");

    /**
     * Truncates a snippet to {@code maxChars} on a word boundary, appending a Unicode ellipsis
     * when truncation occurs. Multi-byte characters count as one code-unit (Java string length).
     * Returns {@code null} for null input and the original string when shorter than the limit.
     *
     * <p>Centralised here so all feed-bound text (titles, body previews, descriptions, change
     * descriptions) shares the same cap and reader inboxes stay scannable. The cut prefers a line
     * break, so half a list item is not stranded, then the last space, and only then a hard cut.
     */
    public static String truncateSnippet(String text, int maxChars) {
        if (text == null) return null;
        if (maxChars <= 0 || text.length() <= maxChars) return text;
        String head = text.substring(0, maxChars);
        int cut = Math.max(head.lastIndexOf('\n'), head.lastIndexOf(' '));
        if (cut > 0) {
            head = head.substring(0, cut);
        }
        return head + "…";
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
    public String resolveLocalized(String locale, String section, String key, @Nullable Map<String, String> params) {
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
     * doesn't blow past the reader's inbox row width. Placeholders left over from a missing param
     * are stripped, whitespace collapsed and a dangling separator trimmed.
     */
    public String resolveFeedTitle(String locale, Notification n) {
        var templates = LOCALIZER.get("notifications", locale, "feedTitle");
        String typeKey = n.type().name();
        var params = new LinkedHashMap<>(n.data().paramsAsMap());
        augmentTitleParams(locale, n, params);

        String template = templateFor(templates, typeKey, n, params);
        if (template == null) return resolveCategory(locale, n.type());

        for (var entry : params.entrySet()) {
            entry.setValue(truncateSnippet(entry.getValue(), TITLE_FRAGMENT_MAX));
        }
        String result = template;
        for (var entry : params.entrySet()) {
            if (entry.getValue() == null) continue;
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        result = result.replaceAll("\\{[^}]+\\}", "").replaceAll("\\s+", " ").trim();
        result = result.replaceAll("[\\s\\--:,]+$", "").trim();
        if (result.isBlank()) return resolveCategory(locale, n.type());
        return result;
    }

    /**
     * Resolves the localized message body for a notification, substituting any {param} placeholders.
     * Falls back to joining the params with hyphens (or to the locale key) when no template exists.
     * The params are copied first, so rewriting the status to its label leaves the notification's
     * own map untouched.
     */
    public String resolveMessage(String locale, Notification n) {
        var templates = LOCALIZER.get("notifications", locale, "message");
        String localeKey = n.type().localeKey();
        var params = new LinkedHashMap<>(n.data().paramsAsMap());
        localizeStatusParam(locale, params);

        String template = templateFor(templates, localeKey, n, params);
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
    public @Nullable String resolveDetail(Notification n) {
        var params = n.data().params();
        if (params == null) return null;
        return switch (n.type()) {
            case NEW_NEWS -> params instanceof NotificationParams.NewNews p ? p.preview() : null;
            case NEWS_COMMENT -> params instanceof NotificationParams.NewsComment p ? p.preview() : null;
            case MOVEMENT_ADVANCED -> params instanceof NotificationParams.MovementMoved p ? p.stepLabel() : null;
            case MOVEMENT_RAISED -> params instanceof NotificationParams.MovementRaised p ? p.reason() : null;
            case EVENT_REGISTRATION_STATUS ->
                params instanceof NotificationParams.EventRegistrationStatus p ? p.eventDescription() : null;
            case NEW_EVENT -> params instanceof NotificationParams.NewEvent p ? p.eventDescription() : null;
            default -> null;
        };
    }

    /**
     * Resolves the deep link URL for a notification's target entity, or {@code null} when the
     * notification has no associated link. A route the page table does not know falls back to the
     * start page of the area the link is read in: a station's dashboard, or the association's overview.
     *
     * <p>A link into the area it is read in carries that station's or association's identity as
     * {@code ?station=<uid>} or {@code ?cluster=<uid>}. This survives the login redirect and the
     * pickers, so a reader of several stations or associations lands in the right one.
     *
     * @param baseUrl public base URL of the deployment
     * @param home    the station or association the notification belongs to
     * @param data    the notification's link metadata
     * @return the resolved URL or {@code null} when the notification has no link
     */
    public @Nullable String resolveNotificationUrl(String baseUrl, LinkHome home, NotificationData data) {
        var link = data.link();
        if (link == null) return null;
        return resolveLinkUrl(baseUrl, home, link);
    }

    /**
     * The address a link leads to, the same way {@link #resolveNotificationUrl} reads a notification's,
     * for a mail that links into the app without being a notification.
     *
     * @param baseUrl public base URL of the deployment
     * @param home    the station or association the link belongs to
     * @param link    the link
     * @return the resolved URL
     */
    public String resolveLinkUrl(String baseUrl, LinkHome home, NotificationData.NotificationLink link) {
        return NotificationPages.pathOf(link.route())
                .map(template ->
                        withHome(baseUrl + filled(template, link.routeParams()) + queryString(link.query()), home))
                .orElseGet(() -> landingUrl(baseUrl, home));
    }

    /**
     * The start page of a station or an association, where a mail's main button and a link nobody
     * knows lead.
     *
     * @param baseUrl public base URL of the deployment
     * @param home    the station or association
     * @return the address, carrying its identity where it is known
     */
    public static String landingUrl(String baseUrl, LinkHome home) {
        return withHome(baseUrl + NotificationPages.landingOf(home.kind()), home);
    }

    /**
     * The notification preferences of a station or an association, where a mail's footer leads.
     *
     * @param baseUrl public base URL of the deployment
     * @param home    the station or association
     * @return the address, carrying its identity where it is known
     */
    public static String preferencesUrl(String baseUrl, LinkHome home) {
        return withHome(baseUrl + NotificationPages.preferencesOf(home.kind()), home);
    }

    private static String filled(String template, @Nullable Map<String, Object> routeParams) {
        String path = template;
        if (routeParams != null) {
            for (var entry : routeParams.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return path;
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
     * Replaces the params that carry a raw enum name with the label for it in the reader's
     * language: a status (e.g. {@code "ACCEPTED"}, {@code "DONE"}, {@code "APPROVED"}) and the
     * answer somebody gave to a waiting-list invitation.
     *
     * <p>That lets the message templates use a single {@code {status}} or {@code {answer}}
     * placeholder for every type carrying one. No-op when the param is absent or the bundle has no
     * matching entry.
     */
    private static void localizeStatusParam(String locale, Map<String, String> params) {
        localizeLabelParam(locale, params, "status", "ical", "status.");
        localizeLabelParam(locale, params, "answer", "waitlistAnswer", "");
    }

    private static void localizeLabelParam(
            String locale, Map<String, String> params, String param, String section, String prefix) {
        String name = params.get(param);
        if (name == null) return;
        String localized = LOCALIZER.get("notifications", locale, section).get(prefix + name);
        if (localized != null) {
            params.put(param, localized);
        }
    }

    /**
     * The template one notification is worded by: the sentence its parameters name as their variant
     * where one is written, otherwise the type's own, each in its plural where a count calls for one.
     *
     * <p>Languages like German and English need different words for one and for several, so a count
     * among the parameters ({@code count}, {@code days}, {@code daysBefore}, {@code pendingCount})
     * prefers the {@code .one} or {@code .other} spelling of a key where it is written.
     *
     * @param templates the section of the bundle the key lives in
     * @param key       the type's key in that section
     * @param n         the notification, whose parameters may name a variant
     * @param params    its parameters as text
     * @return the template, or {@code null} where none is written
     */
    private static String templateFor(
            Map<String, String> templates, String key, Notification n, Map<String, String> params) {
        var typed = n.data().params();
        String variant = typed == null ? null : typed.variant();
        if (variant != null) {
            String chosen = pluralOf(templates, key + "." + variant, params);
            if (chosen != null) return chosen;
        }
        return pluralOf(templates, key, params);
    }

    private static String pluralOf(Map<String, String> templates, String key, Map<String, String> params) {
        Integer count = extractCountParam(params);
        if (count != null) {
            String plural = templates.get(key + (count == 1 ? ".one" : ".other"));
            if (plural != null) return plural;
        }
        return templates.get(key);
    }

    /**
     * Picks the first integer-valued count-like param ({@code count}, {@code days},
     * {@code daysBefore}, {@code pendingCount}) so {@link #resolveMessage} can route to the right
     * plural variant. Returns {@code null} when no recognised count param is present or parseable,
     * trying the next candidate after one that is not a number.
     */
    private static @Nullable Integer extractCountParam(Map<String, String> params) {
        for (String key : COUNT_PARAMS) {
            Integer count = parseCount(params.get(key));
            if (count != null) return count;
        }
        return null;
    }

    private static @Nullable Integer parseCount(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notACount) {
            return null;
        }
    }

    /**
     * Renders the link's query, which is how a mail or feed entry about a comment opens on that
     * comment rather than on the top of the page it hangs under.
     */
    private static String queryString(@Nullable Map<String, Object> query) {
        if (query == null || query.isEmpty()) return "";
        var rendered = new StringJoiner("&", "?", "");
        for (var entry : query.entrySet()) {
            rendered.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return rendered.toString();
    }

    /**
     * Appends {@code station=<uid>} or {@code cluster=<uid>} to {@code url} when the home's identity
     * is known and the URL points into the home's own area. A link into another area is left
     * untouched, so a station's mail never names a station on an association's page.
     */
    private static String withHome(String url, LinkHome home) {
        UUID uid = home.uid();
        if (uid == null) return url;
        int pathStart = url.indexOf("/", url.indexOf("://") + 3);
        String path = pathStart < 0 ? "" : url.substring(pathStart);
        String area = home.kind() == DigestGroup.Kind.STATION ? "station" : "cluster";
        if (!path.equals("/" + area) && !path.startsWith("/" + area + "/") && !path.startsWith("/" + area + "?")) {
            return url;
        }
        char separator = url.contains("?") ? '&' : '?';
        return url + separator + area + "=" + uid;
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
}
