/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.render;

import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.feed.render.NotificationFeedRenderer;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationFeedRendererTest {

    private NotificationService notificationService;
    private NotificationFeedRenderer renderer;

    @BeforeEach
    void setup() {
        notificationService = mock(NotificationService.class);
        when(notificationService.resolveCategory(any(), any()))
                .thenAnswer(inv -> ((NotificationType) inv.getArgument(1)).name());
        when(notificationService.resolveMessage(any(), any())).thenReturn("MESSAGE");
        when(notificationService.resolveFeedBody(any(), any())).thenReturn("PLAINBODY");
        // Mirror NotificationService: echo the key, applying {name} placeholder substitution
        // when params are provided. Lets tests reason about the bundle key without setting up
        // real translation files.
        when(notificationService.resolveLocalized(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        // No deep link by default — renderer must fall back to the dashboard.
        when(notificationService.resolveNotificationUrl(any(), any())).thenReturn(null);
        renderer = new NotificationFeedRenderer(notificationService);
    }

    private NotificationFeedRenderer.RenderContext richCtx() {
        return new NotificationFeedRenderer.RenderContext("de", "https://ember.example.com", "TOKEN", true, true);
    }

    @Test
    void everyEntryHasALinkEvenWithoutMetadata() {
        var n = notification(
                1, NotificationType.MEMBER_ADDED_TO_GROUP, new NotificationParams.MemberAddedToGroup("Alpha"));
        var entry = renderer.render(n, richCtx());
        assertNotNull(entry.getLink());
        assertEquals("https://ember.example.com/station/dashboard/overview", entry.getLink());
    }

    @Test
    void resolvedDeepLinkOverridesDashboardFallback() {
        when(notificationService.resolveNotificationUrl(any(), any()))
                .thenReturn("https://ember.example.com/station/events/42");
        var n = notification(2, NotificationType.NEW_EVENT, new NotificationParams.NewEvent("Probe", "Konzert"));
        var entry = renderer.render(n, richCtx());
        assertEquals("https://ember.example.com/station/events/42", entry.getLink());
    }

    @Test
    void carriesBothHtmlAndPlainTextContent() {
        var n = notification(3, NotificationType.NEW_EVENT, new NotificationParams.NewEvent("Probe", "Konzert"));
        var entry = renderer.render(n, richCtx());
        // Description is the rich HTML body
        assertEquals("text/html", entry.getDescription().getType());
        assertTrue(entry.getDescription().getValue().contains("<dl"));
        assertTrue(entry.getDescription().getValue().contains("<dt"));
        // Plain-text fallback lives in contents
        var plain = entry.getContents().stream()
                .filter(c -> "text/plain".equals(c.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals("PLAINBODY", plain.getValue());
    }

    @Test
    void authorSetFromActorWhenAvailable() {
        var n = notification(4, NotificationType.NEW_NEWS, new NotificationParams.NewNews("Title", "Alice", "Preview"));
        var entry = renderer.render(n, richCtx());
        assertEquals("Alice", entry.getAuthor());
    }

    @Test
    void categoryAlwaysCarriesNotificationType() {
        var n = notification(
                5, NotificationType.STORAGE_WARNING, new NotificationParams.StorageWarning(91, "9.1 GB", "10 GB"));
        var entry = renderer.render(n, richCtx());
        var categories = entry.getCategories();
        assertEquals(1, categories.size());
        assertEquals("STORAGE_WARNING", categories.getFirst().getName());
    }

    @Test
    void lostAndFoundEmbedsImageViaTokenRoute() {
        var n = notification(
                6,
                NotificationType.LOST_AND_FOUND_NEW,
                new NotificationParams.LostAndFoundNew("Blaue Jacke"),
                new NotificationData.NotificationLink("lost-and-found", Map.of("id", 17)));
        var entry = renderer.render(n, richCtx());
        var html = entry.getDescription().getValue();
        assertTrue(html.contains("/api/v1/public/feed/TOKEN/lost-and-found/17/image"));
        assertTrue(html.contains("alt=\"Blaue Jacke\""));
        // MediaRSS thumbnail also wired so readers like Thunderbird get a preview thumbnail.
        var media = entry.getModules().stream()
                .filter(MediaEntryModuleImpl.class::isInstance)
                .map(MediaEntryModuleImpl.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(1, media.getMediaContents().length);
        assertEquals("image", media.getMediaContents()[0].getMedium());
    }

    @Test
    void imagesFlagOmitsImgTagAndMediaModule() {
        var n = notification(
                7,
                NotificationType.LOST_AND_FOUND_NEW,
                new NotificationParams.LostAndFoundNew("Blaue Jacke"),
                new NotificationData.NotificationLink("lost-and-found", Map.of("id", 17)));
        var noImages =
                new NotificationFeedRenderer.RenderContext("de", "https://ember.example.com", "TOKEN", true, false);
        var entry = renderer.render(n, noImages);
        assertFalse(entry.getDescription().getValue().contains("<img"));
        assertTrue(entry.getModules() == null
                || entry.getModules().stream().noneMatch(MediaEntryModuleImpl.class::isInstance));
    }

    @Test
    void compactModeHasHeadlineAndLinkButNoDetailBlock() {
        var n = notification(
                8,
                NotificationType.EVENT_REGISTRATION_STATUS,
                new NotificationParams.EventRegistrationStatus("Probe", RegistrationStatus.ACCEPTED, "Konzert"));
        var compact =
                new NotificationFeedRenderer.RenderContext("de", "https://ember.example.com", "TOKEN", false, true);
        var entry = renderer.render(n, compact);
        var html = entry.getDescription().getValue();
        assertTrue(html.contains("MESSAGE"));
        assertFalse(html.contains("<dl"));
    }

    @Test
    void detailBlockRendersStatusWithUnicodeMarker() {
        var n = notification(
                9,
                NotificationType.EVENT_REGISTRATION_STATUS,
                new NotificationParams.EventRegistrationStatus("Probe", RegistrationStatus.ACCEPTED, "Konzert"));
        var entry = renderer.render(n, richCtx());
        var html = entry.getDescription().getValue();
        assertTrue(html.contains("\u2713 ACCEPTED"), "Description should contain check-mark + ACCEPTED");
        assertTrue(html.contains("Probe"));
    }

    @Test
    void htmlEscapesUserSuppliedContent() {
        var n = notification(
                10,
                NotificationType.LOST_AND_FOUND_NEW,
                new NotificationParams.LostAndFoundNew("<script>alert(1)</script>"));
        var entry = renderer.render(n, richCtx());
        var html = entry.getDescription().getValue();
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    // -- helpers --

    private static Notification notification(int id, NotificationType type, NotificationParams params) {
        return notification(id, type, params, null);
    }

    private static Notification notification(
            int id, NotificationType type, NotificationParams params, NotificationData.NotificationLink link) {
        var data = link == null ? NotificationData.of(params) : NotificationData.of(params, link);
        return new Notification(id, 1, type, data, Instant.now(), null);
    }
}
