/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.FeedRefusal;
import dev.chojo.ember.feature.feed.FeedFingerprint;
import dev.chojo.ember.feature.feed.FeedRateLimiter;
import dev.chojo.ember.feature.feed.entity.StationCalendarFeed;
import dev.chojo.ember.feature.feed.entity.StationFeedLevel;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.Feed;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.Rendered;
import dev.chojo.ember.feature.feed.service.StationCalendarFeedService;
import dev.chojo.ember.feature.feed.service.StationFeedCalendar;
import dev.chojo.ember.feature.station.entity.StationModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The station calendar feed routes over HTTP: who may manage feeds, and what a calendar fetching an
 * address meets.
 */
class StationCalendarFeedRoutesTest {
    private static final int STATION = 2;
    private static final String TOKEN = "station-feed-token";
    private static final String FEEDS = RouteHarness.PREFIX + "/calendar-feeds";
    private static final String ADDRESS = RouteHarness.PREFIX + "/public/station-feed/" + TOKEN + "/events.ics";

    private final StationCalendarFeedService feeds = mock(StationCalendarFeedService.class);
    private final StationFeedCalendar calendars = mock(StationFeedCalendar.class);
    private final StationCalendarFeed feed = new StationCalendarFeed(
            5, STATION, "Leinwand", TOKEN, StationFeedLevel.MEMBERS, null, Instant.EPOCH, null, List.of());
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        when(feeds.list(STATION)).thenReturn(List.of(feed));
        when(calendars.feed(TOKEN)).thenReturn(feed);
        when(calendars.calendar(eq(feed), anyBoolean()))
                .thenReturn(new Feed(
                        FeedFingerprint.compute(Instant.EPOCH, "station"), () -> new Rendered("BEGIN:VCALENDAR", 0)));
    }

    private RouteHarness harness() {
        return RouteHarness.serving(new StationCalendarFeedRoutes(
                feeds, calendars, new FeedRateLimiter(clock), mock(FeedMetricsService.class)));
    }

    @Test
    void managingFeedsNeedsThePermission() {
        assertEquals(
                403,
                harness()
                        .call(STATION, "GET", FEEDS, null, StationPermission.USER)
                        .code());
        assertEquals(
                200,
                harness()
                        .call(STATION, "GET", FEEDS, null, StationPermission.EVENT_CALENDAR_FEEDS)
                        .code());
    }

    @Test
    void feedsAreNotHereWhileAppointmentsAreSwitchedOff() {
        var off = harness().withModulesOff(StationModule.EVENTS);
        assertEquals(
                404,
                off.call(STATION, "GET", FEEDS, null, StationPermission.EVENT_CALENDAR_FEEDS)
                        .code());
    }

    @Test
    void aCreatedFeedIsAnsweredWithItsAddress() {
        when(feeds.create(eq(STATION), eq("Leinwand"), eq(StationFeedLevel.MEMBERS), eq(List.of(3)), anyInt()))
                .thenReturn(feed);
        var response = harness()
                .call(
                        STATION,
                        "POST",
                        FEEDS,
                        "{\"name\":\"Leinwand\",\"level\":\"MEMBERS\",\"hiddenCategories\":[3]}",
                        StationPermission.EVENT_CALENDAR_FEEDS);
        assertEquals(201, response.code());
        assertEquals(TOKEN, RouteHarness.json(response).get("token").asText());
    }

    @Test
    void theAddressServesTheCalendarWithoutReferrerOrIndexing() {
        var response = harness().request(client -> client.get(ADDRESS));
        assertEquals(200, response.code());
        assertEquals("no-referrer", header(response, "Referrer-Policy"));
        assertEquals("noindex", header(response, "X-Robots-Tag"));
    }

    @Test
    void anAddressThatLeadsNowhereIsNotFound() {
        when(calendars.feed("gone")).thenThrow(FeedRefusal.FEED_LINK_NOT_GOOD.raise());
        var response =
                harness().request(client -> client.get(RouteHarness.PREFIX + "/public/station-feed/gone/events.ics"));
        assertEquals(FeedRefusal.FEED_LINK_NOT_GOOD, RouteHarness.refusalOf(response));
        assertEquals("no-referrer", header(response, "Referrer-Policy"));
    }

    @Test
    void theAddressIsRateLimited() {
        harness().run((server, client) -> {
            int last = 0;
            for (int i = 0; i <= FeedRateLimiter.BURST_CAPACITY; i++) {
                last = client.get(ADDRESS).code();
            }
            assertEquals(429, last);
        });
    }
}
