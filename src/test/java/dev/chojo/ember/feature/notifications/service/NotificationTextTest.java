/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.feature.board.entity.BoardTicketAddress;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.notifications.entity.ExpiryReminderKind;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a notification reads in the app, the mail and the feed: messages, plurals, titles, details and
 * the addresses they lead to.
 */
class NotificationTextTest {
    private static final int MEMBER = 1;
    private static final String BASE = "https://ember.example.com";
    private final NotificationText text = new NotificationText();

    @Test
    void resolveLocaleHandlesGermanEnglishAndNull() {
        assertEquals("de", text.resolveLocale("de-DE"));
        assertEquals("de", text.resolveLocale("de"));
        assertEquals("en", text.resolveLocale("en-US"));
        assertEquals("en", text.resolveLocale(null));
        assertEquals("en", text.resolveLocale("fr-FR"));
    }

    @Test
    void resolveLocalizedSubstitutesParamsAndFallsBackToKey() {
        String body = text.resolveLocalized("de", "feed", "title", Map.of("stationName", "Demo"));
        assertTrue(body.contains("Demo"));
        assertEquals("missing.key", text.resolveLocalized("de", "feed", "missing.key", null));
    }

    @Test
    void resolveCategoryReturnsLocalisedLabelAndFallsBackToEnumName() {
        assertEquals("Neuigkeit", text.resolveCategory("de", NotificationType.NEW_NEWS));
        assertEquals("News", text.resolveCategory("en", NotificationType.NEW_NEWS));
        assertEquals("Speicherwarnung", text.resolveCategory("de", NotificationType.STORAGE_WARNING));
        assertEquals("Storage Warning", text.resolveCategory("en", NotificationType.STORAGE_WARNING));
    }

    /** A type without a template of its own reads as its parameters joined. */
    @Test
    void resolveMessageSubstitutesParamsAndFallsBackToJoinedParams() {
        var event = notificationWith(
                NotificationType.NEW_EVENT, new NotificationParams.NewEvent("Sprechstunde", "Etwas Beschreibung"), 1);
        assertTrue(text.resolveMessage("de", event).contains("Sprechstunde"));

        var storage = notificationWith(
                NotificationType.STORAGE_WARNING, new NotificationParams.StorageWarning(95, "9.5 GB", "10 GB"), 2);
        assertTrue(text.resolveMessage("de", storage).contains("95"));
    }

    /**
     * An expiry reminder is worded by what it is about: a date still ahead, the last valid day, a date
     * passed, or member management's list, each counted in its plural.
     */
    @Test
    void anExpiryReminderIsWordedByWhatItIsAbout() {
        assertEquals(
                "Erste Hilfe von Anna läuft in 12 Tagen ab (2026-03-31)",
                expiryMessage(ExpiryReminderKind.EXPIRES_IN, 12, null, null));
        assertEquals(
                "Erste Hilfe von Anna läuft morgen ab (2026-03-31)",
                expiryMessage(ExpiryReminderKind.EXPIRES_IN, 1, null, null));
        assertEquals(
                "Erste Hilfe von Anna läuft heute ab (2026-03-31)",
                expiryMessage(ExpiryReminderKind.EXPIRES_TODAY, 0, null, null));
        assertEquals(
                "Erste Hilfe von Anna war bis vor 3 Tagen gültig (2026-03-31)",
                expiryMessage(ExpiryReminderKind.EXPIRED, 3, null, null));
        assertEquals(
                "Erste Hilfe von Anna war bis gestern gültig (2026-03-31)",
                expiryMessage(ExpiryReminderKind.EXPIRED, 1, null, null));
        assertEquals(
                "Erste Hilfe: bei 5 Mitgliedern fällig (Anna, Ben, Carla, …)",
                expiryMessage(ExpiryReminderKind.MEMBERS_DUE, null, "Anna, Ben, Carla, …", 5));

        var title = text.resolveFeedTitle(
                "en", expiryNotification(ExpiryReminderKind.EXPIRED, 3, null, null, NotificationLinks.ownProfile()));
        assertEquals("Expired: Erste Hilfe - Anna", title);
    }

    /** Member management's reminder opens the member list narrowed to the field's dates running out. */
    @Test
    void anExpiryReminderLeadsToTheNarrowedMemberList() {
        var data = NotificationData.of(
                new NotificationParams.ExpiryReminder(
                        ExpiryReminderKind.MEMBERS_DUE, "Erste Hilfe", null, null, null, "Anna", 1),
                NotificationLinks.runningOut(17));

        assertEquals(
                BASE + "/station/members/list?field=17&state=expiring%2Cexpired",
                text.resolveNotificationUrl(BASE, null, data));
        assertEquals(
                BASE + "/station/profile/managed?member=4",
                text.resolveNotificationUrl(
                        BASE, null, NotificationData.of(data.params(), NotificationLinks.managedProfile(4))));
    }

    @Test
    void resolveDetailReturnsTypeSpecificStringOrNull() {
        var news = notificationWith(
                NotificationType.NEW_NEWS, new NotificationParams.NewNews("Titel", "Autor", "Preview"), 3);
        assertEquals("Preview", text.resolveDetail(news));

        var group = notificationWith(
                NotificationType.MEMBER_ADDED_TO_GROUP, new NotificationParams.MemberAddedToGroup("Alpha", null), 4);
        assertNull(text.resolveDetail(group));
        assertNull(text.resolveDetail(new Notification(
                5, MEMBER, null, NotificationType.NEW_NEWS, new NotificationData(null, null), Instant.now(), null)));
    }

    /**
     * An address is built from the link's route, falls back to the dashboard for a route nobody knows,
     * is missing where there is no link, and carries the station for a reader of several stations.
     */
    @Test
    void resolveNotificationUrlHandlesMissingLinkUnknownRouteAndKnownRoute() {
        var noLink = new NotificationData(new NotificationParams.MemberAddedToGroup("Alpha", null), null);
        assertNull(text.resolveNotificationUrl(BASE, null, noLink));

        var unknown = NotificationData.of(
                new NotificationParams.MemberAddedToGroup("Alpha", null),
                new NotificationData.NotificationLink("bogus-route"));
        assertEquals(BASE + "/station/dashboard/overview", text.resolveNotificationUrl(BASE, null, unknown));

        var known = NotificationData.of(
                new NotificationParams.NewEvent("Probe", ""),
                new NotificationData.NotificationLink("event-detail", Map.of("id", 42)));
        assertEquals(BASE + "/station/events/42", text.resolveNotificationUrl(BASE, null, known));

        var stationUid = UUID.fromString("00000000-0000-0000-0000-000000000042");
        assertEquals(
                BASE + "/station/events/42?station=" + stationUid,
                text.resolveNotificationUrl(BASE, stationUid, known));

        var cluster = NotificationData.of(
                new NotificationParams.MemberAddedToGroup("Alpha", null),
                new NotificationData.NotificationLink("cluster-members"));
        assertEquals(BASE + "/cluster/members", text.resolveNotificationUrl(BASE, stationUid, cluster));
    }

    /**
     * A mail or feed entry about a comment opens on that comment, and still carries the station it
     * belongs to.
     */
    @Test
    void resolveNotificationUrlCarriesTheCommentAndTheStation() {
        var data = NotificationData.of(
                new NotificationParams.NewsComment("Sturm", "Bea", "Danke"),
                NotificationLinks.comment(NotificationLinks.news(7), 42));
        var stationUid = UUID.fromString("00000000-0000-0000-0000-000000000042");

        assertEquals(
                BASE + "/station/news/7?comment=42&station=" + stationUid,
                text.resolveNotificationUrl(BASE, stationUid, data));
    }

    /** The address of a ticket is its board and its number, and a comment on one has to fill both. */
    @Test
    void resolveNotificationUrlFillsEveryPlaceholderOfATicketComment() {
        var data = NotificationData.of(
                new NotificationParams.CommentMention("DEV-42", "Bea", "@With"),
                NotificationLinks.comment(NotificationLinks.ticket(new BoardTicketAddress("DEV", 42), 7), 601));

        String url = text.resolveNotificationUrl(BASE, null, data);

        assertEquals(BASE + "/station/boards/DEV/tickets/42?comment=601", url);
        assertFalse(url.contains("{"), "no part of the route was left unfilled");
    }

    @Test
    void resolveDetailCoversAllNonNullTypeBranches() {
        assertEquals(
                "Preview",
                text.resolveDetail(notificationWith(
                        NotificationType.NEWS_COMMENT,
                        new NotificationParams.NewsComment("Titel", "Autor", "Preview"),
                        20)));
        assertEquals(
                "Ersatz ausgegeben",
                text.resolveDetail(notificationWith(
                        NotificationType.MOVEMENT_ADVANCED,
                        new NotificationParams.MovementMoved("Ersatz ausgegeben", "Inv", null),
                        21)));
        assertEquals(
                "Why",
                text.resolveDetail(notificationWith(
                        NotificationType.MOVEMENT_RAISED,
                        new NotificationParams.MovementRaised("Name", "Inv", "Why"),
                        22)));
        assertEquals(
                "Konzert",
                text.resolveDetail(notificationWith(
                        NotificationType.EVENT_REGISTRATION_STATUS,
                        new NotificationParams.EventRegistrationStatus(
                                "Tim Berger", "Probe", RegistrationStatus.ACCEPTED, "Konzert"),
                        23)));
        assertEquals(
                "Konzert",
                text.resolveDetail(notificationWith(
                        NotificationType.NEW_EVENT, new NotificationParams.NewEvent("Probe", "Konzert"), 24)));
    }

    /** A count among the parameters picks the singular or the plural sentence. */
    @Test
    void resolveMessagePicksPluralVariantBasedOnCount() {
        var one = notificationWith(
                NotificationType.NEW_EVENTS_BATCH, new NotificationParams.NewEventsBatch(1, "A", null), 50);
        var many = notificationWith(
                NotificationType.NEW_EVENTS_BATCH, new NotificationParams.NewEventsBatch(3, "A, B, C", null), 51);
        var enOne = text.resolveMessage("en", one);
        assertTrue(enOne.contains("1 new event"), "Singular variant should win when count == 1");
        assertFalse(enOne.contains("events were"), "Singular variant should not use plural noun");
        assertTrue(text.resolveMessage("en", many).contains("3 new events"));
        var deOne = text.resolveMessage("de", one);
        assertTrue(deOne.startsWith("Ein neuer Termin"), "DE singular variant: " + deOne);
    }

    /** A type without a plural split still finds its one sentence. */
    @Test
    void resolveMessageFallsBackToSingularKeyWhenNoPluralDefined() {
        var notification =
                notificationWith(NotificationType.NEW_EVENT, new NotificationParams.NewEvent("Probe", "Konzert"), 52);
        assertTrue(text.resolveMessage("en", notification).contains("Probe"));
    }

    /** A snippet is cut at a word, keeps a short text whole and cuts hard where there is no space. */
    @Test
    void truncateSnippetCutsAtWordBoundaryAndAppendsEllipsis() {
        var truncated =
                NotificationText.truncateSnippet("The quick brown fox jumps over the lazy dog and runs further", 20);
        assertTrue(truncated.endsWith("…"), "Should append ellipsis when truncating: " + truncated);
        assertTrue(truncated.length() <= 21, "Length should stay near cap: " + truncated);
        assertFalse(truncated.contains("jumps"), "Should cut before the next word");

        assertEquals("hi", NotificationText.truncateSnippet("hi", 100));
        assertNull(NotificationText.truncateSnippet(null, 10));
        assertEquals("aaaaa…", NotificationText.truncateSnippet("aaaaaaaaaaaaaaaaaaaa", 5));
    }

    /**
     * A feed title names its category and the thing it is about, counts in its plural, and carries the
     * status with its symbol.
     */
    @Test
    void resolveFeedTitleEmbedsEntityIdentifierAndRoutesPlurals() {
        var news = notificationWith(
                NotificationType.NEW_NEWS, new NotificationParams.NewNews("Q3 schedule", "Alice", "preview"), 200);
        var enTitle = text.resolveFeedTitle("en", news);
        assertTrue(enTitle.startsWith("News:"), "Expected EN category prefix in: " + enTitle);
        assertTrue(enTitle.contains("Q3 schedule"));
        assertTrue(text.resolveFeedTitle("de", news).startsWith("Neuigkeit:"));

        var one = notificationWith(
                NotificationType.NEW_EVENTS_BATCH, new NotificationParams.NewEventsBatch(1, "A", null), 201);
        assertEquals("1 new event", text.resolveFeedTitle("en", one));
        var many = notificationWith(
                NotificationType.NEW_EVENTS_BATCH, new NotificationParams.NewEventsBatch(3, "A, B, C", null), 202);
        assertEquals("3 new events", text.resolveFeedTitle("en", many));

        var registration = notificationWith(
                NotificationType.EVENT_REGISTRATION_STATUS,
                new NotificationParams.EventRegistrationStatus(
                        "Tim Berger", "Open Training", RegistrationStatus.ACCEPTED, "desc"),
                203);
        var registrationTitle = text.resolveFeedTitle("en", registration);
        assertTrue(registrationTitle.contains("Accepted"), "Should contain localised status: " + registrationTitle);
        assertTrue(registrationTitle.contains("✓"), "Should contain check-mark symbol: " + registrationTitle);
        assertTrue(registrationTitle.contains("Open Training"));

        var tomorrow = notificationWith(
                NotificationType.EVENT_REMINDER,
                new NotificationParams.EventReminder("Probe", 1, LocalDate.parse("2026-09-15")),
                204);
        assertTrue(text.resolveFeedTitle("en", tomorrow).contains("tomorrow"));
        var later = notificationWith(
                NotificationType.EVENT_REMINDER,
                new NotificationParams.EventReminder("Probe", 5, LocalDate.parse("2026-09-20")),
                205);
        assertTrue(text.resolveFeedTitle("en", later).contains("5 days"));
    }

    /** Where every placeholder falls away the title is the bare category, never "News: ". */
    @Test
    void resolveFeedTitleFallsBackToCategoryWhenNoTemplateMatches() {
        var malformed = new Notification(
                300,
                MEMBER,
                null,
                NotificationType.NEW_NEWS,
                new NotificationData(new NotificationParams.NewNews(null, null, null), null),
                Instant.now(),
                null);
        assertEquals("News", text.resolveFeedTitle("en", malformed));
    }

    /** An overlong fragment is shortened, so the title still fits an inbox row. */
    @Test
    void resolveFeedTitleTruncatesOverlongFragments() {
        String longTitle = "A".repeat(200);
        var n = notificationWith(
                NotificationType.NEW_NEWS, new NotificationParams.NewNews(longTitle, "Alice", "p"), 301);
        var title = text.resolveFeedTitle("en", n);
        assertTrue(title.endsWith("…") || title.length() < longTitle.length(), "Title should be truncated: " + title);
        assertTrue(title.length() < 120, "Title shouldn't blow past the cap: " + title);
    }

    /** A status reads with its symbol, an unknown one as its name, and none as nothing. */
    @Test
    void resolveStatusWithSymbolUsesLocalisedLabelAndIcon() {
        assertEquals("✓ Accepted", text.resolveStatusWithSymbol("en", "ACCEPTED"));
        assertEquals("✓ Angenommen", text.resolveStatusWithSymbol("de", "ACCEPTED"));
        assertEquals("MYSTERY", text.resolveStatusWithSymbol("en", "MYSTERY"));
        assertNull(text.resolveStatusWithSymbol("en", null));
        assertTrue(text.resolveStatusWithSymbol("en", "DENIED").startsWith("✗ "));
        assertTrue(text.resolveStatusWithSymbol("en", "PENDING").startsWith("… "));
        assertTrue(text.resolveStatusWithSymbol("en", "WITHDRAWN").startsWith("↶ "));
    }

    /** A lending answer carries its status, with its symbol, in the feed title. */
    @Test
    void aLendingAnswerNamesItsStatusInTheFeedTitle() {
        var answer = notificationWith(
                NotificationType.LENDING_STATUS_CHANGE,
                new NotificationParams.LendingStatusChange("Feuerwehr West", LendingStatus.APPROVED),
                302);
        assertTrue(text.resolveFeedTitle("en", answer).contains("✓"));
    }

    private String expiryMessage(ExpiryReminderKind kind, Integer days, String members, Integer count) {
        return text.resolveMessage(
                "de", expiryNotification(kind, days, members, count, NotificationLinks.ownProfile()));
    }

    private static Notification expiryNotification(
            ExpiryReminderKind kind,
            Integer days,
            String members,
            Integer count,
            NotificationData.NotificationLink link) {
        boolean own = kind != ExpiryReminderKind.MEMBERS_DUE;
        var params = new NotificationParams.ExpiryReminder(
                kind, "Erste Hilfe", own ? "Anna" : null, own ? LocalDate.of(2026, 3, 31) : null, days, members, count);
        return new Notification(
                20,
                MEMBER,
                null,
                NotificationType.EXPIRY_REMINDER,
                NotificationData.of(params, link),
                Instant.now(),
                null);
    }

    private static Notification notificationWith(NotificationType type, NotificationParams params, int id) {
        var data = NotificationData.of(params, new NotificationData.NotificationLink("dashboard-overview"));
        return new Notification(id, MEMBER, null, type, data, Instant.now(), null);
    }
}
