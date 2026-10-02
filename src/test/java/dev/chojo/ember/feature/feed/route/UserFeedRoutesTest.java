/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.feed.FeedFingerprint;
import dev.chojo.ember.feature.feed.FeedRateLimiter;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import dev.chojo.ember.feature.feed.service.PersonalFeedService;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.Feed;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.FeedFormat;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.Rendered;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.members.entity.StationMember;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The calendar, the Atom feed and the lost-item picture behind a feed link, over HTTP.
 */
class UserFeedRoutesTest {
    private static final String FEED = PREFIX + "/public/feed/t";
    private static final StationMember MEMBER =
            new StationMember(7, 3, null, 1, false, null, "Mara", StationUserType.MEMBER, null);

    private static Feed feed(String body, int entries) {
        return new Feed(FeedFingerprint.compute(Instant.EPOCH, body), () -> new Rendered(body, entries));
    }

    @Test
    void theCalendarAndTheAtomFeedAreRenderedAndThePictureIsServedForTheMembersStation() {
        var feeds = mock(PersonalFeedService.class);
        when(feeds.member("t")).thenReturn(MEMBER);
        when(feeds.calendar(MEMBER, false)).thenReturn(feed("BEGIN:VCALENDAR", 1));
        when(feeds.notifications(eq(MEMBER), eq("t"), eq(FeedFormat.ATOM), anyBoolean(), anyBoolean()))
                .thenReturn(feed("<feed/>", 1));
        when(feeds.lostAndFoundImage(3, 4, 64)).thenReturn(new MediaContent(new byte[] {1}, "image/png"));
        var metrics = mock(FeedMetricsService.class);
        var harness = RouteHarness.serving(new UserFeedRoutes(feeds, new FeedRateLimiter(Clock.systemUTC()), metrics));

        harness.run((server, client) -> {
            var calendar = client.get(FEED + "/events.ics?verbose=0");
            assertEquals("BEGIN:VCALENDAR", calendar.body().string());
            assertTrue(header(calendar, "Content-Type").startsWith("text/calendar"));
            assertEquals("no-referrer", header(calendar, "Referrer-Policy"));
            var atom = client.get(FEED + "/notifications.atom");
            assertEquals("<feed/>", atom.body().string());
            var picture = client.get(FEED + "/lost-and-found/4/image?size=64");
            assertTrue(header(picture, "Content-Type").startsWith("image/png"));
        });

        verify(feeds).notifications(MEMBER, "t", FeedFormat.ATOM, true, true);
        verify(metrics).recordRender(eq("ics"), eq(200), anyLong(), eq(1), any());
    }
}
