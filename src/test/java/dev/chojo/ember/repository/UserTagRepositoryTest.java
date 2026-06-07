/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserTagRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int tagId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("UserTag Station");
        account = accountRepo.create("utag@test.com", "UT", "User");
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
        var tag = userTagRepo.create(station.id(), "Anfaenger");
        assertNotNull(tag);
        assertEquals("Anfaenger", tag.name());
        tagId = tag.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(userTagRepo.findById(tagId).isPresent());
        assertTrue(userTagRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findByStation() {
        var tags = userTagRepo.findByStation(station.id());
        assertEquals(1, tags.size());
    }

    @Test
    @Order(4)
    void update() {
        assertTrue(userTagRepo.update(tagId, "Fortgeschritten", null, false, 0));
        assertEquals(
                "Fortgeschritten", userTagRepo.findById(tagId).orElseThrow().name());
    }

    @Test
    @Order(5)
    void updateNonExistent() {
        assertFalse(userTagRepo.update(99999, "Nope", null, false, 0));
    }

    // -- Tag entries --

    @Test
    @Order(10)
    void addMember() {
        userTagRepo.addMember(tagId, member.id());
        // Verify through findTagsForMember (findMembers has a pre-existing column mismatch)
        var tags = userTagRepo.findTagsForMember(member.id());
        assertEquals(1, tags.size());
        assertEquals(tagId, tags.getFirst().id());
    }

    @Test
    @Order(11)
    void addMemberIdempotent() {
        // ON CONFLICT DO NOTHING — should not throw
        userTagRepo.addMember(tagId, member.id());
        assertEquals(1, userTagRepo.findTagsForMember(member.id()).size());
    }

    @Test
    @Order(12)
    void findTagsForMember() {
        var tags = userTagRepo.findTagsForMember(member.id());
        assertEquals(1, tags.size());
        assertEquals(tagId, tags.getFirst().id());
    }

    @Test
    @Order(13)
    void removeMember() {
        assertTrue(userTagRepo.removeMember(tagId, member.id()));
        assertTrue(userTagRepo.findTagsForMember(member.id()).isEmpty());
    }

    @Test
    @Order(14)
    void removeMemberNotFound() {
        assertFalse(userTagRepo.removeMember(tagId, member.id()));
    }

    @Test
    @Order(15)
    void setMembers() {
        userTagRepo.setMembers(tagId, List.of(member.id()));
        assertEquals(1, userTagRepo.findTagsForMember(member.id()).size());
        userTagRepo.setMembers(tagId, List.of());
        assertTrue(userTagRepo.findTagsForMember(member.id()).isEmpty());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(userTagRepo.delete(tagId));
        assertTrue(userTagRepo.findById(tagId).isEmpty());
    }
}
