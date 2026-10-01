/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.NewsRefusal;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.service.NewsAttachmentService;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.news.service.PublicBlogService;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A station's public blog over HTTP: each page asks whether the station keeps one open, and says in
 * its own words when it does not.
 */
class PublicBlogRoutesTest {
    @Test
    void anOpenBlogIsListedAndFedAndAClosedOneIsRefusedPerPage() {
        var news = mock(NewsService.class);
        var attachments = mock(NewsAttachmentService.class);
        var blogs = mock(PublicBlogService.class);
        var email = mock(EmailService.class);
        var station = mock(Station.class);
        when(station.id()).thenReturn(3);
        when(station.uid()).thenReturn(UUID.randomUUID());
        when(station.name()).thenReturn("Wache");
        when(blogs.openBlog(eq("wache"), any(), any())).thenReturn(station);
        when(blogs.openBlog(eq("closed"), any(), eq(NewsRefusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_ENTRY)))
                .thenThrow(NewsRefusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_ENTRY.raise());
        when(news.findPublicBlogEntries(3, 0, 20)).thenReturn(List.of());
        when(news.findPublicBlogEntries(3, 0, 50)).thenReturn(List.of());
        when(attachments.listFor(any())).thenReturn(Map.of());
        when(email.getBaseUrl()).thenReturn("https://ember.test");
        var harness = RouteHarness.serving(new NewsRoutes(
                news,
                mock(CommentService.class),
                attachments,
                mock(NewsFederationService.class),
                blogs,
                mock(MemberNameResolver.class),
                mock(MemberIdentityFactory.class),
                email));

        harness.run((server, client) -> {
            assertEquals(200, client.get(PREFIX + "/public/station/wache/blog").code());
            var feed = client.get(PREFIX + "/public/station/wache/blog.rss");
            assertTrue(feed.body().string().contains("Wache"));
            assertTrue(header(feed, "Content-Type").contains("xml"));
            assertEquals(
                    NewsRefusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_ENTRY,
                    refusalOf(client.get(PREFIX + "/public/station/closed/blog/4")));
            assertEquals(
                    NewsRefusal.PUBLIC_BLOG_ENTRY_NOT_HERE,
                    refusalOf(client.get(PREFIX + "/public/station/wache/blog/4")));
        });
    }
}
