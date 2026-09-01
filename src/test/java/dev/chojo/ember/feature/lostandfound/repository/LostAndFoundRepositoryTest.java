/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.repository;

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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LostAndFoundRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int itemId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("LostFound Station");
        account = accountRepo.create("lf@test.com", "LF", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        var item = lostAndFoundRepo.create(station.id(), "Red jacket", LocalDate.of(2026, 6, 1), member.id());
        assertNotNull(item);
        assertEquals("Red jacket", item.description());
        assertEquals(LocalDate.of(2026, 6, 1), item.foundAt());
        itemId = item.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(lostAndFoundRepo.findById(itemId).isPresent());
        assertTrue(lostAndFoundRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findByStation() {
        assertEquals(1, lostAndFoundRepo.findByStation(station.id()).size());
    }

    @Test
    @Order(4)
    void findUnclaimedByStation() {
        assertEquals(1, lostAndFoundRepo.findUnclaimedByStation(station.id()).size());
    }

    @Test
    @Order(5)
    void findUnclaimedOrClaimedBy() {
        assertEquals(
                1,
                lostAndFoundRepo
                        .findUnclaimedOrClaimedBy(station.id(), member.id())
                        .size());
    }

    @Test
    @Order(10)
    void countClaimedNotProvidedNone() {
        // Item exists but is not claimed yet
        assertEquals(0, lostAndFoundRepo.countClaimedNotProvided(station.id()));
    }

    @Test
    @Order(20)
    void claim() {
        assertTrue(lostAndFoundRepo.claim(itemId, member.id()));
        var item = lostAndFoundRepo.findById(itemId).orElseThrow();
        assertEquals(member.id(), item.claimedBy());
        assertNotNull(item.claimedAt());
    }

    @Test
    @Order(21)
    void claimAlreadyClaimed() {
        assertFalse(lostAndFoundRepo.claim(itemId, member.id()));
    }

    @Test
    @Order(22)
    void findUnclaimedAfterClaim() {
        assertEquals(0, lostAndFoundRepo.findUnclaimedByStation(station.id()).size());
    }

    @Test
    @Order(23)
    void countClaimedNotProvidedAfterClaim() {
        // Item is now claimed, should count as 1
        assertEquals(1, lostAndFoundRepo.countClaimedNotProvided(station.id()));
    }

    @Test
    @Order(24)
    void findUnclaimedOrClaimedByAfterClaim() {
        // Should still find it since we claimed it ourselves
        assertEquals(
                1,
                lostAndFoundRepo
                        .findUnclaimedOrClaimedBy(station.id(), member.id())
                        .size());
    }

    @Test
    @Order(30)
    void releaseFreesTheItemAgain() {
        assertTrue(lostAndFoundRepo.release(itemId));
        var item = lostAndFoundRepo.findById(itemId).orElseThrow();
        assertNull(item.claimedBy());
        assertNull(item.claimedAt());
        assertEquals(1, lostAndFoundRepo.findUnclaimedByStation(station.id()).size());
        assertEquals(0, lostAndFoundRepo.countClaimedNotProvided(station.id()));
    }

    @Test
    @Order(31)
    void releaseAnUnclaimedItemChangesNothing() {
        assertFalse(lostAndFoundRepo.release(itemId));
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(lostAndFoundRepo.delete(itemId));
        assertTrue(lostAndFoundRepo.findById(itemId).isEmpty());
    }
}
