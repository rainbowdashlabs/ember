/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.NameChangeOutcome;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The names members asked for, one open request per account, decided once. */
class NameChangeRequestRepositoryTest extends RepositoryTestBase {
    private static NameChangeRequestRepository requests;
    private static Station station;
    private static Station otherStation;

    @BeforeAll
    static void setup() {
        requests = new NameChangeRequestRepository();
        station = stationRepo.create("Name Change Station");
        otherStation = stationRepo.create("Name Change Other Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
    }

    private static Account account(String name) {
        return accountRepo.create("name-change-" + name + "@test.com", name, "Before");
    }

    private static StationMember memberAt(Station at, Account account) {
        return stationMemberRepo.create(at.id(), account.id());
    }

    @Test
    void askingAgainRewritesTheOpenRequest() {
        var account = account("again");
        var first = requests.request(account.id(), "Maxi", "After");
        var second = requests.request(account.id(), "Max", "After");

        assertEquals(first.id(), second.id());
        assertEquals(
                "Max After",
                requests.findOpenByAccount(account.id()).orElseThrow().fullName());
        accountRepo.delete(account.id());
    }

    @Test
    void aRequestIsDecidedOnceAndThenNoLongerOpen() {
        var account = account("once");
        var decider = account("decider");
        var request = requests.request(account.id(), "Lea", "After");

        assertTrue(requests.decide(request.id(), NameChangeOutcome.DENIED, decider.id(), "Typo"));
        assertFalse(requests.decide(request.id(), NameChangeOutcome.APPROVED, decider.id(), null));
        assertTrue(requests.findOpenById(request.id()).isEmpty());
        assertTrue(requests.findOpenByAccount(account.id()).isEmpty());
        accountRepo.delete(account.id());
        accountRepo.delete(decider.id());
    }

    @Test
    void aDecidedRequestLeavesRoomForANewOne() {
        var account = account("room");
        var withdrawn = requests.request(account.id(), "Old", "Ask");
        requests.decide(withdrawn.id(), NameChangeOutcome.WITHDRAWN, null, null);

        var fresh = requests.request(account.id(), "New", "Ask");

        assertFalse(fresh.id() == withdrawn.id());
        assertNull(fresh.outcome());
        assertEquals(fresh, requests.findOpenById(fresh.id()).orElseThrow());
        accountRepo.delete(account.id());
    }

    @Test
    void aStationSeesTheRequestsOfItsCurrentMembersOnly() {
        var here = account("here");
        var former = account("former");
        var elsewhere = account("elsewhere");
        var member = memberAt(station, here);
        stationMemberRepo.setFormer(memberAt(station, former).id(), true);
        memberAt(otherStation, elsewhere);
        requests.request(here.id(), "Here", "After");
        requests.request(former.id(), "Former", "After");
        requests.request(elsewhere.id(), "Elsewhere", "After");

        var pending = requests.findOpenAtStation(station.id());

        assertEquals(
                List.of(member.id()), pending.stream().map(p -> p.memberId()).toList());
        assertEquals(member.uid(), pending.getFirst().memberUid());
        assertEquals("here Before", pending.getFirst().currentName());
        assertEquals("Here After", pending.getFirst().requestedName());
        accountRepo.delete(here.id());
        accountRepo.delete(former.id());
        accountRepo.delete(elsewhere.id());
    }
}
