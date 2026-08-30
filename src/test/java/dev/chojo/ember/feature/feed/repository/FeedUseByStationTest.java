/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a station may see about the subscriptions its own members keep.
 *
 * <p>Two things have to hold. It stops at the station's own door, because a subscription is a
 * standing arrangement between one person and this instance and no other station's business. And it
 * says when each feed was last fetched, because that is the whole point: a calendar nothing has ever
 * pulled looks exactly like one pulled every hour until somebody can see the difference.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FeedUseByStationTest extends RepositoryTestBase {

    private static Station station;
    private static Station otherStation;
    private static Account account;
    private static Account otherAccount;
    private static StationMember member;
    private static StationMember stranger;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("FeedUseStation");
        otherStation = stationRepo.create("FeedUseOtherStation");
        account = accountRepo.create("feeduse@test.com", "Feed", "User");
        otherAccount = accountRepo.create("feeduse-other@test.com", "Other", "User");
        member = stationMemberRepo.create(station.id(), account.id());
        stranger = stationMemberRepo.create(otherStation.id(), otherAccount.id());
        feedTokenRepo.create(member.id(), "feed-use-token-here");
        feedTokenRepo.create(stranger.id(), "feed-use-token-elsewhere");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accountRepo.delete(account.id());
        accountRepo.delete(otherAccount.id());
    }

    @Test
    @Order(1)
    void aStationSeesItsOwnSubscriptionsAndNobodyElses() {
        var ours = feedTokenRepo.findUseByStation(station.id());

        assertEquals(1, ours.size(), "one row, for our own member");
        assertEquals(member.id(), ours.getFirst().memberId());
        assertTrue(
                feedTokenRepo.findUseByStation(otherStation.id()).stream()
                        .noneMatch(use -> use.memberId() == member.id()),
                "and the other station sees nothing of ours");
    }

    @Test
    @Order(2)
    void aSubscriptionSaysWhenItWasLastFetched() {
        var before = feedTokenRepo.findUseByStation(station.id()).getFirst();
        assertNotNull(before.createdAt(), "it says when it was set up");
        assertNull(before.icalPolledAt(), "and that nothing has fetched the calendar yet");

        feedTokenRepo.updateIcalPolled(member.id());

        var after = feedTokenRepo.findUseByStation(station.id()).getFirst();
        assertNotNull(after.icalPolledAt(), "once something has, it says so");
        assertNull(after.notificationPolledAt(), "and the notifications are counted apart from it");
    }
}
