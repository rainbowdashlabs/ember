/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserTagServiceTest extends RepositoryTestBase {
    private static UserTagService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int tagId;

    @BeforeAll
    static void setup() {
        service = new UserTagService(userTagRepo, memberGroupRepo);
        station = stationRepo.create("TagStation");
        account = accountRepo.create("tag-svc@test.com", "Tag", "Tester");
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
        var tag = service.create(station.id(), "Schwimmer");
        assertNotNull(tag);
        assertEquals("Schwimmer", tag.name());
        tagId = tag.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(tagId).isPresent());
    }

    @Test
    @Order(3)
    void findByStation() {
        var tags = service.findByStation(station.id());
        assertTrue(tags.stream().anyMatch(t -> t.id() == tagId));
    }

    @Test
    @Order(10)
    void setMembersAndFind() {
        service.setMembers(tagId, List.of(member.id()));
        var members = service.findMembers(tagId);
        assertTrue(members.stream().anyMatch(m -> m.id() == member.id()));
    }

    @Test
    @Order(11)
    void findTagsForMember() {
        var tags = service.findTagsForMember(member.id());
        assertTrue(tags.stream().anyMatch(t -> t.id() == tagId));
    }

    @Test
    @Order(12)
    void clearMembers() {
        service.setMembers(tagId, List.of());
        var members = service.findMembers(tagId);
        assertFalse(members.stream().anyMatch(m -> m.id() == member.id()));
    }

    @Test
    @Order(20)
    void update() {
        assertTrue(service.update(tagId, "Rettungsschwimmer", null, false, 0));
        var tag = service.findById(tagId).orElseThrow();
        assertEquals("Rettungsschwimmer", tag.name());
    }

    @Test
    @Order(30)
    void delete() {
        assertTrue(service.delete(tagId));
        assertTrue(service.findById(tagId).isEmpty());
    }

    @Test
    @Order(40)
    void convertToGroup() {
        // Create a fresh tag with the member in it
        var tag2 = service.create(station.id(), "ToBeGroup");
        service.setMembers(tag2.id(), List.of(member.id()));

        service.convertToGroup(tag2.id());

        // Tag should be gone
        assertTrue(service.findById(tag2.id()).isEmpty());

        // Group should exist with the same name
        var groups = memberGroupRepo.findByStation(station.id());
        assertTrue(groups.stream().anyMatch(g -> "ToBeGroup".equals(g.name())));

        // Cleanup
        groups.stream()
                .filter(g -> "ToBeGroup".equals(g.name()))
                .findFirst()
                .ifPresent(g -> memberGroupRepo.delete(g.id()));
    }
}
