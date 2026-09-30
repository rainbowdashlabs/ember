/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.feed.FeedRateLimiter;
import dev.chojo.ember.feature.feed.entity.FeedToken;
import dev.chojo.ember.feature.feed.render.IcalEventRenderer;
import dev.chojo.ember.feature.feed.render.NotificationFeedRenderer;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundImageService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static dev.chojo.ember.api.RouteHarness.header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wiring-level integration tests for {@link UserFeedRoutes}. These ask the feed over HTTP, through
 * the same application production serves, so the cross-cutting concerns (conditional GET, rate
 * limiting, privacy headers) are checked as a feed reader meets them - independent of any single
 * helper's unit test.
 */
class UserFeedRoutesIntegrationTest {

    private static final String TOKEN_VALUE = "test-token";
    private static final String RSS = RouteHarness.PREFIX + "/public/feed/" + TOKEN_VALUE + "/notifications.rss";
    private static final int MEMBER_ID = 7;
    private static final int STATION_ID = 1;

    private NotificationService notificationService;
    private ControllableClock clock;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        FeedTokenService tokenService = mock(FeedTokenService.class);
        notificationService = mock(NotificationService.class);
        StationMemberRepository memberRepository = mock(StationMemberRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);
        EmailService emailService = mock(EmailService.class);
        clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        MemberNameResolver memberNameResolver = mock(MemberNameResolver.class);
        when(memberNameResolver.called(MEMBER_ID)).thenReturn("Max Mustermann");

        harness = RouteHarness.serving(new UserFeedRoutes(
                tokenService,
                mock(EventCrudService.class),
                mock(EventCategoryService.class),
                mock(EventRegistrationService.class),
                notificationService,
                memberRepository,
                stationRepository,
                emailService,
                mock(AccountRepository.class),
                mock(IcalEventRenderer.class),
                mock(LostAndFoundService.class),
                mock(LostAndFoundImageService.class),
                mock(NotificationFeedRenderer.class),
                new FeedRateLimiter(clock),
                mock(FeedMetricsService.class),
                memberNameResolver,
                mock(OccurrenceCalendar.class)));

        FeedToken token = new FeedToken(MEMBER_ID, TOKEN_VALUE, Instant.EPOCH, null, null);
        StationMember member = new StationMember(
                MEMBER_ID,
                STATION_ID,
                UUID.randomUUID(),
                null,
                false,
                null,
                "Test Member",
                StationUserType.MEMBER,
                null);
        Station station = new Station(
                STATION_ID,
                UUID.randomUUID(),
                "Test Station",
                "Europe/Berlin",
                "de-DE",
                null,
                null,
                false,
                null,
                ThemeFeel.ROUNDED,
                false,
                PublicKbMode.OFF,
                DiscoveryVisibility.NONE,
                null,
                false,
                false,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                StationKind.REGULAR,
                null,
                false,
                false);

        when(tokenService.findByToken(TOKEN_VALUE)).thenReturn(Optional.of(token));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(station));
        when(notificationService.resolveLocale(any())).thenReturn("de");
        when(notificationService.resolveLocalized(any(), any(), any(), any())).thenReturn("text");
        when(emailService.getBaseUrl()).thenReturn("https://ember.example.com");
        when(notificationService.getNotificationSettings(MEMBER_ID)).thenReturn(Map.of());
        when(notificationService.findAll(MEMBER_ID)).thenReturn(List.of());
        when(notificationService.findMaxStamp(MEMBER_ID))
                .thenReturn(new NotificationRepository.Stamp(0, Instant.EPOCH));
    }

    @Test
    void rssEmitsEtagOnFirstCallAndReturns304OnSecondWithMatch() {
        harness.run((server, client) -> {
            var first = client.get(RSS);
            String etag = header(first, "ETag");
            assertEquals(200, first.code());
            assertNotNull(etag, "First response should emit an ETag");

            clock.advanceSeconds(120);
            var second = revalidate(client, etag);

            assertEquals(304, second.code(), "Matching If-None-Match should yield 304");
            assertEquals(etag, header(second, "ETag"));
        });
    }

    @Test
    void rssChangingFingerprintInvalidatesEtag() {
        harness.run((server, client) -> {
            String firstEtag = header(client.get(RSS), "ETag");
            when(notificationService.findMaxStamp(MEMBER_ID))
                    .thenReturn(new NotificationRepository.Stamp(99, Instant.parse("2026-06-12T11:00:00Z")));
            when(notificationService.resolveLocalized(any(), any(), any(), any()))
                    .thenReturn("changed");

            clock.advanceSeconds(120);
            var second = revalidate(client, firstEtag);

            assertEquals(200, second.code(), "Stale If-None-Match should not short-circuit");
            assertNotEquals(firstEtag, header(second, "ETag"));
        });
    }

    @Test
    void rateLimitRejectsTheCallThatExceedsTheBurstCapacity() {
        harness.run((server, client) -> {
            for (int i = 0; i < FeedRateLimiter.BURST_CAPACITY; i++) {
                assertEquals(200, client.get(RSS).code(), "Admission " + (i + 1) + " should pass");
                clock.advanceSeconds(1);
            }

            var rateLimited = client.get(RSS);

            assertEquals(429, rateLimited.code());
            assertTrue(Integer.parseInt(header(rateLimited, "Retry-After")) > 0);
        });
    }

    @Test
    void privacyHeadersAreEmittedOnEveryResponsePath() {
        harness.run((server, client) -> {
            var ok = client.get(RSS);
            assertEquals("no-referrer", header(ok, "Referrer-Policy"));
            assertEquals("noindex", header(ok, "X-Robots-Tag"));

            for (int i = 1; i < FeedRateLimiter.BURST_CAPACITY; i++) {
                client.get(RSS);
                clock.advanceSeconds(1);
            }
            var rateLimited = client.get(RSS);

            assertEquals(429, rateLimited.code());
            assertEquals("no-referrer", header(rateLimited, "Referrer-Policy"));
            assertEquals("noindex", header(rateLimited, "X-Robots-Tag"));
        });
    }

    private static Response revalidate(HttpClient client, String etag) {
        return client.get(RSS, request -> request.header("If-None-Match", etag));
    }

    /**
     * Single-element holder so we can stub time without depending on the JVM clock. Re-used
     * by the rate-limiter unit tests; the same shape works here.
     */
    private static final class ControllableClock extends Clock {
        private final AtomicReference<Instant> now;

        ControllableClock(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        void advanceSeconds(long s) {
            now.updateAndGet(i -> i.plusSeconds(s));
        }
    }
}
