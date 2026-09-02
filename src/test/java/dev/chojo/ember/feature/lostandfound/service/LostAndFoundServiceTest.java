/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.Notification;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LostAndFoundServiceTest extends RepositoryTestBase {
    private static LostAndFoundService service;
    private static LostAndFoundImageService imageService;
    private static Station station;
    private static Account account;
    private static StationMember member;
    /** Somebody other than the reporter, because the report never notifies whoever wrote it. */
    private static Account bystanderAccount;

    private static StationMember bystander;
    private static int itemId;

    @BeforeAll
    static void setup() {
        var emailService = mock(EmailService.class);
        imageService = mock(LostAndFoundImageService.class);
        var notificationService = new NotificationService(
                notificationRepo,
                stationMemberRepo,
                userSettingsRepo,
                notificationSettingsRepo,
                accountRepo,
                stationRepo,
                mock(dev.chojo.ember.feature.station.service.StationLogoService.class),
                emailService,
                new MailRecipientService(accountRepo, stationMemberRepo),
                new Mailing());
        service = new LostAndFoundService(lostAndFoundRepo, notificationService, imageService);

        station = stationRepo.create("LostStation");
        account = accountRepo.create("lost@test.com", "Lost", "Finder");
        member = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(r -> stationMemberRepo.grantPermission(member.id(), r.id()));
        stationMemberRepo
                .findPermissionByName(StationPermission.USER)
                .ifPresent(r -> stationMemberRepo.grantPermission(member.id(), r.id()));

        bystanderAccount = accountRepo.create("lost-bystander@test.com", "Lost", "Bystander");
        bystander = stationMemberRepo.create(station.id(), bystanderAccount.id());
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(r -> stationMemberRepo.grantPermission(bystander.id(), r.id()));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
        accountRepo.delete(bystanderAccount.id());
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
        var items = service.findUnclaimedOrClaimedBy(station.id(), List.of(member.id()));
        assertTrue(items.stream().anyMatch(i -> i.id() == itemId));

        var others = service.findUnclaimedOrClaimedBy(station.id(), List.of(bystander.id()));
        assertFalse(others.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(14)
    void releasedItemIsFreeToClaimAgain() {
        assertTrue(service.release(itemId));
        assertNull(service.findById(itemId).orElseThrow().claimedBy());
        assertTrue(service.findUnclaimedByStation(station.id()).stream().anyMatch(i -> i.id() == itemId));
        assertTrue(service.claim(itemId, member.id(), station.id(), "Lost Finder"));
    }

    @Test
    @Order(15)
    void releasingAnUnclaimedItemReportsNothingDone() {
        var free = service.create(station.id(), "Never claimed", LocalDate.now(), member.id());
        assertFalse(service.release(free.id()));
        assertTrue(service.delete(station.id(), free.id()));
    }

    /**
     * Two items reported without a word about them used to share one notification fragment, so
     * claiming either withdrew the other's notification too. The withdrawal now names the item.
     */
    @Test
    @Order(16)
    void claimingOneItemLeavesTheOtherNotificationsAlone() {
        var first = service.create(station.id(), null, LocalDate.now(), member.id());
        var second = service.create(station.id(), null, LocalDate.now(), member.id());
        var announced = notificationRepo.findUnacknowledged(bystander.id());
        assertTrue(announced.stream().anyMatch(n -> pointsAt(n, first.id())));
        assertTrue(announced.stream().anyMatch(n -> pointsAt(n, second.id())));

        assertTrue(service.claim(first.id(), member.id(), station.id(), "Lost Finder"));

        var left = notificationRepo.findUnacknowledged(bystander.id());
        assertTrue(left.stream().anyMatch(n -> pointsAt(n, second.id())));
        assertFalse(left.stream().anyMatch(n -> pointsAt(n, first.id())));

        assertTrue(service.delete(station.id(), first.id()));
        assertTrue(service.delete(station.id(), second.id()));
    }

    @Test
    @Order(17)
    void deletingAnItemTakesItsNotificationsWithIt() {
        var item = service.create(station.id(), "Withdrawn again", LocalDate.now(), member.id());
        assertTrue(notificationRepo.findUnacknowledged(bystander.id()).stream().anyMatch(n -> pointsAt(n, item.id())));

        assertTrue(service.delete(station.id(), item.id()));

        assertFalse(notificationRepo.findUnacknowledged(bystander.id()).stream().anyMatch(n -> pointsAt(n, item.id())));
    }

    /**
     * The handover and the removal are one path, so the image cannot outlive the item it belongs to.
     */
    @Test
    @Order(20)
    void deleteItemAlsoRemovesItsImage() {
        assertTrue(service.delete(station.id(), itemId));
        assertTrue(service.findById(itemId).isEmpty());
        verify(imageService).delete(station.id(), itemId);
    }

    private static boolean pointsAt(Notification notification, int itemId) {
        var link = notification.data().link();
        return link != null
                && String.valueOf(itemId)
                        .equals(String.valueOf(link.routeParams().get("id")));
    }
}
