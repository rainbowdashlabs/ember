/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.service.NotificationService;
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
import static org.mockito.Mockito.mock;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LostAndFoundServiceTest extends RepositoryTestBase {
    private static LostAndFoundService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int itemId;

    @BeforeAll
    static void setup() {
        var emailService = mock(EmailService.class);
        var notificationService = new NotificationService(
                notificationRepo,
                stationMemberRepo,
                userSettingsRepo,
                notificationSettingsRepo,
                accountRepo,
                stationRepo,
                emailService,
                new Mailing());
        service = new LostAndFoundService(lostAndFoundRepo, notificationService);

        station = stationRepo.create("LostStation");
        account = accountRepo.create("lost@test.com", "Lost", "Finder");
        member = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(r -> stationMemberRepo.grantPermission(member.id(), r.id()));
        stationMemberRepo
                .findPermissionByName(StationPermission.USER)
                .ifPresent(r -> stationMemberRepo.grantPermission(member.id(), r.id()));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createItem() {
        var item = service.create(station.id(), "Blue jacket", LocalDate.now(), member.id());
        assertNotNull(item);
        assertEquals("Blue jacket", item.description());
        assertNull(item.claimedBy());
        itemId = item.id();
    }

    @Test
    @Order(2)
    void findByStation() {
        var items = service.findByStation(station.id());
        assertTrue(items.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(3)
    void findById() {
        assertTrue(service.findById(itemId).isPresent());
        assertTrue(service.findById(999999).isEmpty());
    }

    @Test
    @Order(4)
    void findUnclaimed() {
        var items = service.findUnclaimedByStation(station.id());
        assertTrue(items.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(10)
    void claimItem() {
        assertTrue(service.claim(itemId, member.id(), station.id(), "Lost Finder"));
        var item = service.findById(itemId).orElseThrow();
        assertEquals(member.id(), item.claimedBy());
    }

    @Test
    @Order(11)
    void claimAlreadyClaimedFails() {
        assertFalse(service.claim(itemId, member.id(), station.id(), "Other"));
    }

    @Test
    @Order(12)
    void claimedItemNotInUnclaimed() {
        var unclaimed = service.findUnclaimedByStation(station.id());
        assertFalse(unclaimed.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(13)
    void claimedItemVisibleInUnclaimedOrClaimedBy() {
        var items = service.findUnclaimedOrClaimedBy(station.id(), member.id());
        assertTrue(items.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(20)
    void deleteItem() {
        assertTrue(service.delete(itemId));
        assertTrue(service.findById(itemId).isEmpty());
    }
}
