/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feed;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.feed.FeedRateLimiter;
import dev.chojo.ember.feature.feed.entity.FeedToken;
import dev.chojo.ember.feature.feed.render.IcalEventRenderer;
import dev.chojo.ember.feature.feed.render.NotificationFeedRenderer;
import dev.chojo.ember.feature.feed.route.UserFeedRoutes;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wiring-level integration tests for {@link UserFeedRoutes}. These exercise the route
 * handlers via a heavily-mocked Javalin {@link Context} so we can verify the cross-cutting
 * concerns (conditional GET, rate limiting, privacy headers, metrics) actually fire in the
 * intended order — independent of any single helper's unit test.
 */
class UserFeedRoutesIntegrationTest {

    private static final String TOKEN_VALUE = "test-token";
    private static final int MEMBER_ID = 7;
    private static final int STATION_ID = 1;

    private FeedTokenService tokenService;
    private EventService eventService;
    private NotificationService notificationService;
    private StationMemberRepository memberRepository;
    private StationRepository stationRepository;
    private EmailService emailService;
    private AccountRepository accountRepository;
    private IcalEventRenderer icalRenderer;
    private LostAndFoundService lostAndFoundService;
    private ImageService imageService;
    private NotificationFeedRenderer notificationRenderer;
    private FeedRateLimiter rateLimiter;
    private FeedMetricsService metricsService;
    private ControllableClock clock;
    private UserFeedRoutes routes;

    private Station station;
    private StationMember member;
    private FeedToken token;

    @BeforeEach
    void setup() {
        tokenService = mock(FeedTokenService.class);
        eventService = mock(EventService.class);
        notificationService = mock(NotificationService.class);
        memberRepository = mock(StationMemberRepository.class);
        stationRepository = mock(StationRepository.class);
        emailService = mock(EmailService.class);
        accountRepository = mock(AccountRepository.class);
        icalRenderer = mock(IcalEventRenderer.class);
        lostAndFoundService = mock(LostAndFoundService.class);
        imageService = mock(ImageService.class);
        notificationRenderer = mock(NotificationFeedRenderer.class);
        clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        rateLimiter = new FeedRateLimiter(clock);
        metricsService = mock(FeedMetricsService.class);

        routes = new UserFeedRoutes(
                tokenService,
                eventService,
                notificationService,
                memberRepository,
                stationRepository,
                emailService,
                accountRepository,
                icalRenderer,
                lostAndFoundService,
                imageService,
                notificationRenderer,
                rateLimiter,
                metricsService);

        // Minimal fixture: real token, member, station for the rss/atom handlers to resolve.
        token = new FeedToken(MEMBER_ID, TOKEN_VALUE, Instant.EPOCH, null, null);
        member = new StationMember(
                MEMBER_ID,
                STATION_ID,
                UUID.randomUUID(),
                null,
                false,
                null,
                "Test Member",
                StationUserType.MEMBER,
                null);
        station = new Station(
                STATION_ID,
                UUID.randomUUID(),
                "Test Station",
                "Europe/Berlin",
                "de-DE",
                null,
                null,
                false,
                null,
                dev.chojo.ember.feature.station.entity.ThemeFeel.ROUNDED,
                false,
                dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode.OFF,
                null,
                dev.chojo.ember.feature.station.entity.DiscoveryVisibility.NONE,
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
                null);

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

    // -- conditional GET --

    @Test
    void rssEmitsEtagOnFirstCallAndReturns304OnSecondWithMatch() throws Exception {
        var first = new RecordingContext();
        first.pathParams.put("token", TOKEN_VALUE);
        invokeRss(first.ctx);

        // First call must populate the ETag for the next caller to echo back.
        String etag = first.headers.get("ETag");
        assertNotNull(etag, "First response should emit an ETag");
        assertNull(first.status.get(), "First response should fall through to 200");

        // Second call (different rate-limit window so we don't trip the limiter).
        clock.advanceSeconds(120);
        var second = new RecordingContext();
        second.pathParams.put("token", TOKEN_VALUE);
        second.requestHeaders.put("If-None-Match", etag);
        invokeRss(second.ctx);

        assertEquals(Integer.valueOf(304), second.status.get(), "Matching If-None-Match should yield 304");
        assertEquals(etag, second.headers.get("ETag"));
    }

    @Test
    void rssChangingFingerprintInvalidatesEtag() throws Exception {
        var first = new RecordingContext();
        first.pathParams.put("token", TOKEN_VALUE);
        invokeRss(first.ctx);
        String firstEtag = first.headers.get("ETag");

        // Bump the notification stamp — the next render must produce a fresh ETag.
        when(notificationService.findMaxStamp(MEMBER_ID))
                .thenReturn(new NotificationRepository.Stamp(99, Instant.parse("2026-06-12T11:00:00Z")));

        clock.advanceSeconds(120);
        var second = new RecordingContext();
        second.pathParams.put("token", TOKEN_VALUE);
        second.requestHeaders.put("If-None-Match", firstEtag);
        invokeRss(second.ctx);

        // Stale ETag → server falls through to a full render and emits the new fingerprint.
        assertNull(second.status.get(), "Stale If-None-Match should not short-circuit");
        assertNotEquals(firstEtag, second.headers.get("ETag"));
    }

    // -- rate limit --

    @Test
    void rateLimitRejectsTheCallThatExceedsTheBurstCapacity() throws Exception {
        // Drain the burst — every call within it must be admitted.
        for (int i = 0; i < dev.chojo.ember.feature.feed.FeedRateLimiter.BURST_CAPACITY; i++) {
            var ctx = new RecordingContext();
            ctx.pathParams.put("token", TOKEN_VALUE);
            invokeRss(ctx.ctx);
            assertNull(ctx.status.get(), "Admission " + (i + 1) + " should pass");
            clock.advanceSeconds(1);
        }
        // The next call has no token left and not enough time for a refill → 429.
        var rateLimited = new RecordingContext();
        rateLimited.pathParams.put("token", TOKEN_VALUE);
        invokeRss(rateLimited.ctx);
        assertEquals(Integer.valueOf(429), rateLimited.status.get());
        assertNotNull(rateLimited.headers.get("Retry-After"));
        assertTrue(Integer.parseInt(rateLimited.headers.get("Retry-After")) > 0);
    }

    // -- privacy headers always present --

    @Test
    void privacyHeadersAreEmittedOnEveryResponsePath() throws Exception {
        // 200 path
        var ok = new RecordingContext();
        ok.pathParams.put("token", TOKEN_VALUE);
        invokeRss(ok.ctx);
        assertEquals("no-referrer", ok.headers.get("Referrer-Policy"));
        assertEquals("noindex", ok.headers.get("X-Robots-Tag"));

        // 429 path — drain the rest of the burst, then trip the limit.
        for (int i = 1; i < dev.chojo.ember.feature.feed.FeedRateLimiter.BURST_CAPACITY; i++) {
            var burn = new RecordingContext();
            burn.pathParams.put("token", TOKEN_VALUE);
            invokeRss(burn.ctx);
            clock.advanceSeconds(1);
        }
        var rateLimited = new RecordingContext();
        rateLimited.pathParams.put("token", TOKEN_VALUE);
        invokeRss(rateLimited.ctx);
        assertEquals(Integer.valueOf(429), rateLimited.status.get());
        assertEquals("no-referrer", rateLimited.headers.get("Referrer-Policy"));
        assertEquals("noindex", rateLimited.headers.get("X-Robots-Tag"));
    }

    // -- helpers --

    private void invokeRss(Context ctx) throws Exception {
        Method m = UserFeedRoutes.class.getDeclaredMethod("rssFeed", Context.class);
        m.setAccessible(true);
        m.invoke(routes, ctx);
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
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        void advanceSeconds(long s) {
            now.updateAndGet(i -> i.plusSeconds(s));
        }
    }

    /**
     * Hand-rolled stub for {@link Context} that records the bits we assert on — status,
     * response headers, request headers, query params. Built around a Mockito {@code mock}
     * so we don't have to implement the entire {@code Context} interface.
     */
    private static final class RecordingContext {

        final Map<String, String> pathParams = new HashMap<>();
        final Map<String, String> queryParams = new HashMap<>();
        final Map<String, String> requestHeaders = new HashMap<>();
        final Map<String, String> headers = new HashMap<>();
        final AtomicReference<Integer> status = new AtomicReference<>();
        final Context ctx;

        RecordingContext() {
            this.ctx = buildCtx();
        }

        private Context buildCtx() {
            var c = mock(Context.class);
            when(c.pathParam(any())).thenAnswer(inv -> pathParams.getOrDefault(inv.getArgument(0), ""));
            when(c.queryParam(any())).thenAnswer(inv -> queryParams.get(inv.getArgument(0)));
            // Single-arg header(name) reads the request; two-arg header(name, value) writes
            // the response.
            when(c.header(any(String.class))).thenAnswer(inv -> requestHeaders.get(inv.getArgument(0)));
            when(c.header(any(String.class), any(String.class))).thenAnswer(inv -> {
                headers.put(inv.getArgument(0), inv.getArgument(1));
                return c;
            });
            when(c.status(anyInt())).thenAnswer(inv -> {
                status.set(inv.getArgument(0));
                return c;
            });
            when(c.contentType(any(String.class))).thenReturn(c);
            when(c.result(any(String.class))).thenReturn(c);
            // status() (no args) returns the current HttpStatus — used by metrics recording.
            when(c.status()).thenAnswer(inv -> {
                Integer s = status.get();
                return s == null ? io.javalin.http.HttpStatus.OK : io.javalin.http.HttpStatus.forStatus(s);
            });
            return c;
        }
    }
}
