/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FeedTokenServiceTest extends RepositoryTestBase {
    private static FeedTokenService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static String token;

    @BeforeAll
    static void setup() {
        service = new FeedTokenService(feedTokenRepo);
        station = stationRepo.create("FeedStation");
        account = accountRepo.create("feed@test.com", "Feed", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void findByMemberWhenNoneExists() {
        assertTrue(service.findByMember(member.id()).isEmpty());
    }

    @Test
    @Order(2)
    void getOrCreateCreatesToken() {
        var feedToken = service.getOrCreate(member.id());
        assertNotNull(feedToken);
        assertNotNull(feedToken.token());
        assertFalse(feedToken.token().isBlank());
        token = feedToken.token();
    }

    @Test
    @Order(3)
    void getOrCreateReturnsSameToken() {
        var feedToken = service.getOrCreate(member.id());
        assertEquals(token, feedToken.token());
    }

    @Test
    @Order(4)
    void findByMember() {
        var feedToken = service.findByMember(member.id());
        assertTrue(feedToken.isPresent());
        assertEquals(token, feedToken.get().token());
    }

    @Test
    @Order(5)
    void findByToken() {
        var feedToken = service.findByToken(token);
        assertTrue(feedToken.isPresent());
        assertEquals(member.id(), feedToken.get().memberId());
    }

    @Test
    @Order(6)
    void findByTokenNonExistent() {
        assertTrue(service.findByToken("nonexistent-token").isEmpty());
    }

    @Test
    @Order(7)
    void regenerateCreatesNewToken() {
        var newToken = service.regenerate(member.id());
        assertNotNull(newToken);
        assertNotEquals(token, newToken.token());
        token = newToken.token();
    }

    @Test
    @Order(8)
    void oldTokenNoLongerWorks() {
        // The old token should no longer resolve
        var result = service.findByToken("old-invalid-token");
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(8)
    void recordIcalPollUpdatesTimestamp() {
        var feedToken = service.getOrCreate(member.id());
        assertNull(feedToken.icalPolledAt());

        service.recordIcalPoll(member.id());

        var updated = service.findByMember(member.id()).orElseThrow();
        assertNotNull(updated.icalPolledAt());
        assertNull(updated.notificationPolledAt());
    }

    @Test
    @Order(8)
    void recordNotificationPollUpdatesTimestamp() {
        service.recordNotificationPoll(member.id());

        var updated = service.findByMember(member.id()).orElseThrow();
        assertNotNull(updated.notificationPolledAt());
        assertNotNull(updated.icalPolledAt());
    }

    @Test
    @Order(9)
    void revoke() {
        assertTrue(service.revoke(member.id()));
        assertTrue(service.findByMember(member.id()).isEmpty());
    }

    @Test
    @Order(10)
    void revokeNonExistent() {
        assertFalse(service.revoke(member.id()));
    }
}
