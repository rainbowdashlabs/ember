/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentOrigin;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a comment on a news entry differs in from the other kinds: whose it is, who moderates it and
 * whom it tells.
 */
class NewsCommentTargetTest extends RepositoryTestBase {
    private static NewsCommentTarget target;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int entryId;

    @BeforeAll
    static void setup() {
        target = new NewsCommentTarget(newNewsService(new DomainEventBus(Set.of())));
        station = stationRepo.create("News Comment Target");
        account = accountRepo.create("news-comment-target@test.com", "Nina", "News");
        member = stationMemberRepo.create(station.id(), account.id());
        entryId = newsRepo.create(station.id(), "Eintrag", "Text", "<p>Text</p>", null)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void anEntryIsFoundWithItsStationAndTitle() {
        var info = target.find(entryId).orElseThrow();

        assertEquals(CommentEntityType.NEWS, info.type());
        assertEquals(station.id(), info.stationId());
        assertEquals("Eintrag", info.title());
        assertFalse(info.systemEntry());
        assertTrue(target.find(-1).isEmpty());
    }

    @Test
    void aSystemEntryBelongsToNoStation() {
        var system = newsRepo.createSystem("An alle", "Text", "<p>Text</p>", true);
        try {
            var info = target.find(system.id()).orElseThrow();

            assertNull(info.stationId());
            assertTrue(info.systemEntry());
        } finally {
            newsRepo.delete(system.id());
        }
    }

    @Test
    void onlyANewsManagerRemovesSomebodyElsesCommentAndNobodyRewritesIt() {
        var info = target.find(entryId).orElseThrow();
        var manager = signedIn(member, StationPermission.NEWS_MANAGER);

        assertTrue(target.mayModerate(manager, info, Moderation.DELETE));
        assertFalse(target.mayModerate(manager, info, Moderation.EDIT));
        assertFalse(target.mayModerate(signedIn(member), info, Moderation.DELETE));
    }

    @Test
    void everyCommentTellsTheNewsManagersAndOnlyALocalOneItsMentions() {
        var info = target.find(entryId).orElseThrow();
        var managers = StationAudience.holders(station.id(), StationPermission.NEWS_MANAGER);

        var local = target.audienceFor(info, CommentOrigin.LOCAL);
        var partner = target.audienceFor(info, CommentOrigin.PARTNER);

        assertTrue(local.parentAuthor());
        assertTrue(local.mentions());
        assertEquals(managers, local.others());
        assertTrue(partner.parentAuthor());
        assertFalse(partner.mentions());
        assertEquals(managers, partner.others());
    }

    @Test
    void aTargetWithoutAStationTellsNoManagers() {
        var stationless = new TargetInfo(CommentEntityType.NEWS, entryId, null, "An alle", null, true);

        assertNull(target.audienceFor(stationless, CommentOrigin.LOCAL).others());
    }

    @Test
    void aNotificationOpensTheEntryAtTheComment() {
        var info = target.find(entryId).orElseThrow();

        assertEquals(NotificationLinks.comment(NotificationLinks.news(entryId), 7), target.link(info, 7));
    }
}
