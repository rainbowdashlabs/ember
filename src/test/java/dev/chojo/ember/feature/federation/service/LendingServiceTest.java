/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.equipment.EquipmentTestSupport;
import dev.chojo.ember.feature.equipment.repository.EquipmentAvailabilityRepository;
import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.equipment.service.EquipmentAvailabilityService;
import dev.chojo.ember.feature.federation.FederationTestContracts;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.InventoryShareRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LendingServiceTest extends RepositoryTestBase {

    private static LendingService service;
    private static LendingRepository lendingRepo;
    private static FederationRepository federationRepo;
    private static InventoryShareRepository shareRepo;
    private static InventoryShareService shareService;
    private static FederationService federationService;
    private static FederationHttpClient httpClient;

    private static Station stationA;
    private static Station stationB;
    private static Station clusterHome;
    private static Account account;
    private static StationMember memberA;
    private static StationMember memberB;

    private static int requestId;
    private static int requestItemId;
    private static int inventoryIdA;
    private static int itemIdA;
    private static int clusterId;
    private static int partnerIdBtoA;
    private static int partnerIdAtoB;

    @BeforeAll
    static void setup() {
        lendingRepo = new LendingRepository();
        federationRepo = new FederationRepository();
        shareRepo = new InventoryShareRepository();
        federationService = TestFederationServices.of(federationRepo, stationRepo);
        shareService = new InventoryShareService(shareRepo, federationService, inventoryRepo, artRepo);
        httpClient = mock(FederationHttpClient.class);
        service = newLendingService(
                new DomainEventBus(Set.of()),
                new EquipmentAvailabilityService(
                        new EquipmentAvailabilityRepository(),
                        new EquipmentNeedRepository(),
                        eventRepo,
                        occurrenceCalendar),
                httpClient);

        stationA = stationRepo.create("LendSvcTestStationA");
        stationB = stationRepo.create("LendSvcTestStationB");

        clusterHome = stationRepo.create("LendSvcClusterHome");
        clusterId = clusterRepo
                .create("LendSvcKreisverband", null, clusterHome.id())
                .id();

        account = accountRepo.create("lendsvc@test.com", "Lend", "SvcTester");
        memberA = stationMemberRepo.create(stationA.id(), account.id());
        memberB = stationMemberRepo.create(stationB.id(), account.id());

        var inv = inventoryRepo.create(stationA.id(), "LendSvcInventory", InventoryType.INTERNAL, false);
        inventoryIdA = inv.id();
        var item = inventoryRepo.createItem(inventoryIdA, "LSVC-001", "Lend Svc Item", null, null);
        itemIdA = item.id();

        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                stationB.id(), stationA.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerIdBtoA = federationService.findPartners(stationB.id()).stream()
                .filter(p -> stationA.uid().equals(p.partnerStationId()))
                .findFirst()
                .orElseThrow()
                .id();
        partnerIdAtoB = federationService.findPartners(stationA.id()).stream()
                .filter(p -> stationB.uid().equals(p.partnerStationId()))
                .findFirst()
                .orElseThrow()
                .id();

        shareService.setInventoryShare(
                stationA.id(), inventoryIdA, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(stationA.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationB.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
        clusterRepo.delete(clusterId);
        stationRepo.delete(clusterHome.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createRequest() {
        var dateFrom = LocalDate.now();
        var dateTo = LocalDate.now().plusDays(7);
        var request =
                service.createRequest(stationB.id(), stationA.id(), dateFrom, dateTo, memberB.id(), null, null, "");
        assertNotNull(request);
        assertTrue(request.id() > 0);
        assertEquals(LendingStatus.REQUESTED, request.status());
        requestId = request.id();
    }

    @Test
    @Order(2)
    void findRequest() {
        var found = service.findRequest(requestId);
        assertTrue(found.isPresent());
        assertEquals(requestId, found.get().id());
    }

    @Test
    @Order(3)
    void addRequestItem() {
        var item = service.addRequestItem(requestId, inventoryIdA, itemIdA, null, 2, null);
        assertNotNull(item);
        assertEquals(2, item.quantity());
        requestItemId = item.id();
    }

    @Test
    @Order(4)
    void findRequestItems() {
        var items = service.findRequestItems(requestId);
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.id() == requestItemId));
    }

    @Test
    @Order(10)
    void approveRequest() {
        assertTrue(service.approveRequest(requestId, stationA.id()));
        var request = service.findRequest(requestId).orElseThrow();
        assertEquals(LendingStatus.APPROVED, request.status());
    }

    @Test
    @Order(11)
    void markLent() {
        assertTrue(service.markLent(requestId, stationA.id()));
        var request = service.findRequest(requestId).orElseThrow();
        assertEquals(LendingStatus.LENT, request.status());
    }

    @Test
    @Order(12)
    void markReturned() {
        assertTrue(service.markReturned(requestId, stationA.id()));
        var request = service.findRequest(requestId).orElseThrow();
        assertEquals(LendingStatus.RETURNED, request.status());
    }

    @Test
    @Order(13)
    void closeRequest() {
        assertTrue(service.closeRequest(requestId, stationA.id()));
        var request = service.findRequest(requestId).orElseThrow();
        assertEquals(LendingStatus.CLOSED, request.status());
    }

    @Test
    @Order(14)
    void declineRequest() {
        var req = service.createRequest(
                stationB.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                memberB.id(),
                null,
                null,
                "");
        assertTrue(service.declineRequest(req.id(), stationA.id(), "Not available"));
        var declined = service.findRequest(req.id()).orElseThrow();
        assertEquals(LendingStatus.DECLINED, declined.status());
    }

    @Test
    @Order(20)
    void sendMessage() {
        var msg = service.sendMessage(requestId, stationA.id(), memberA.id(), "Tester A", "Test message from A");
        assertNotNull(msg);
        assertEquals("Test message from A", msg.message());
        assertFalse(msg.isSystem());
    }

    @Test
    @Order(21)
    void getMessagesForLocalPartner() {
        service.sendMessage(requestId, stationA.id(), memberA.id(), "Tester A", "Hello from A");
        service.sendMessage(requestId, stationB.id(), memberB.id(), "Tester B", "Hello from B");

        var messages = service.getMessages(requestId, stationA.id());
        assertFalse(messages.isEmpty());
        assertTrue(messages.stream().anyMatch(m -> stationA.uid().equals(m.senderStationUid())));
        assertTrue(messages.stream().anyMatch(m -> stationB.uid().equals(m.senderStationUid())));

        verify(httpClient, never()).getList(anyString(), any(FederationRequest.class), any(), anyInt(), any());
    }

    @Test
    @Order(22)
    void getMessagesForRemotePartner() {
        var stationC = stationRepo.create("LendSvcTestStationC");
        var memberC = stationMemberRepo.create(stationC.id(), account.id());

        var keyPairC = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationC.id(),
                stationA.id(),
                federationService.encodePublicKey(keyPairC),
                null,
                "https://remote.example.com");

        var req = service.createRequest(
                stationC.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                memberC.id(),
                null,
                null,
                "");
        service.sendMessage(req.id(), stationA.id(), memberA.id(), "Tester A", "Local msg from A");

        var remoteMsg = new LendingMessage(
                9999, req.id(), stationC.uid(), memberC.id(), "Remote msg from C", false, Instant.now());
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        pathIs("/remote/lending/requests/" + req.uid() + "/messages"),
                        any(),
                        eq(stationA.id()),
                        eq(LendingMessage.class)))
                .thenReturn(List.of(remoteMsg));

        when(httpClient.canSign(stationA.id())).thenReturn(true);

        var messages = service.getMessages(req.id(), stationA.id());
        assertFalse(messages.isEmpty());
        assertTrue(messages.stream().anyMatch(m -> stationA.uid().equals(m.senderStationUid())));
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Remote msg from C")));

        verify(httpClient)
                .getList(
                        eq("https://remote.example.com"),
                        pathIs("/remote/lending/requests/" + req.uid() + "/messages"),
                        any(),
                        eq(stationA.id()),
                        eq(LendingMessage.class));

        federationService.endFederation(partner.id());
        stationRepo.delete(stationC.id());
    }

    @Test
    @Order(30)
    void assignItem() {
        assertTrue(service.assignItem(requestItemId, itemIdA, stationA.id()));
        assertEquals(List.of(itemIdA), lendingRepo.findAssignedItems(requestItemId));
    }

    @Test
    @Order(40)
    void createBlock() {
        var block = service.createBlock(
                stationA.id(), null, null, LocalDate.now(), LocalDate.now().plusDays(7), "Maintenance");
        assertNotNull(block);
        assertTrue(block.id() > 0);
        assertEquals("Maintenance", block.reason());
    }

    @Test
    @Order(41)
    void findBlocks() {
        var blocks = service.findBlocks(stationA.id());
        assertFalse(blocks.isEmpty());
    }

    @Test
    @Order(42)
    void isBlocked() {
        assertTrue(service.isBlocked(
                stationA.id(),
                inventoryIdA,
                itemIdA,
                LocalDate.now(),
                LocalDate.now().plusDays(1)));
    }

    @Test
    @Order(43)
    void deleteBlock() {
        var blocks = service.findBlocks(stationA.id());
        assertFalse(blocks.isEmpty());
        for (var block : blocks) {
            assertTrue(service.deleteBlock(block.id(), stationA.id()));
        }
        assertFalse(service.isBlocked(
                stationA.id(),
                inventoryIdA,
                itemIdA,
                LocalDate.now(),
                LocalDate.now().plusDays(1)));
    }

    @Test
    @Order(50)
    void findRequestsByStation() {
        var requests = service.findRequestsByStation(stationA.id());
        assertNotNull(requests);
        assertTrue(requests.stream().anyMatch(r -> r.id() == requestId));
    }

    @Test
    @Order(51)
    void getLocalMessages() {
        var request = service.findRequest(requestId).orElseThrow();
        var msgs = service.serveMessages(servingAForB(stationB.uid()), request.uid());
        assertNotNull(msgs);
        assertTrue(msgs.stream().anyMatch(m -> stationA.uid().equals(m.senderStationUid())));
    }

    /**
     * A partner reads the messages of a request it is a party to and no other. Without the check,
     * one partner reads what this station said to another.
     */
    @Test
    @Order(51)
    void getLocalMessagesRefusesAPartnerOutsideTheRequest() {
        var outsider = stationRepo.create("LendServiceOutsider");
        var request = service.findRequest(requestId).orElseThrow();

        var refused = assertThrows(
                RefusalResponse.class, () -> service.serveMessages(servingAForB(outsider.uid()), request.uid()));
        assertEquals(FederationRefusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS, refused.refusal());
        assertThrows(
                RefusalResponse.class, () -> service.serveMessages(servingAForB(stationB.uid()), UUID.randomUUID()));

        stationRepo.delete(outsider.id());
    }

    @Test
    @Order(52)
    void declineRequestWithNoReason() {
        var req = service.createRequest(
                stationB.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                memberB.id(),
                null,
                null,
                "");
        assertTrue(service.declineRequest(req.id(), stationA.id(), null));
        var found = service.findRequest(req.id()).orElseThrow();
        assertEquals(LendingStatus.DECLINED, found.status());
    }

    @Test
    @Order(53)
    void declineRequestWithBlankReason() {
        var req = service.createRequest(
                stationB.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                memberB.id(),
                null,
                null,
                "");
        assertTrue(service.declineRequest(req.id(), stationA.id(), ""));
        var found = service.findRequest(req.id()).orElseThrow();
        assertEquals(LendingStatus.DECLINED, found.status());
    }

    @Test
    @Order(55)
    void isBlockedReturnsFalseWhenNotBlocked() {
        assertFalse(service.isBlocked(
                stationA.id(),
                inventoryIdA,
                itemIdA,
                LocalDate.now().plusYears(5),
                LocalDate.now().plusYears(5).plusDays(1)));
    }

    @Test
    @Order(56)
    void findRequestNotFound() {
        assertTrue(service.findRequest(999999).isEmpty());
    }

    @Test
    @Order(57)
    void createRequestWithItemsBuildsSummary() {
        var req = service.createRequest(
                stationB.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                memberB.id(),
                null,
                null,
                "");
        service.addRequestItem(req.id(), inventoryIdA, itemIdA, null, 3, null);

        var req2 = service.createRequest(
                stationB.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                memberB.id(),
                null,
                null,
                "");
        service.addRequestItem(req2.id(), inventoryIdA, itemIdA, null, 1, null);

        assertTrue(service.approveRequest(req.id(), stationB.id()));
    }

    @Test
    @Order(58)
    void getMessagesFromRequestingStationPerspective() {
        var req = service.createRequest(
                stationB.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                memberB.id(),
                null,
                null,
                "");
        service.sendMessage(req.id(), stationA.id(), memberA.id(), "A", "msg from A");
        service.sendMessage(req.id(), stationB.id(), memberB.id(), "B", "msg from B");

        var messages = service.getMessages(req.id(), stationB.id());
        assertFalse(messages.isEmpty());
        assertTrue(messages.stream().anyMatch(m -> stationA.uid().equals(m.senderStationUid())));
        assertTrue(messages.stream().anyMatch(m -> stationB.uid().equals(m.senderStationUid())));
    }

    @Test
    @Order(59)
    void getMessagesRemotePartnerNoPrivateKey() {
        var stationC = stationRepo.create("LendNoKeyC");
        var stationD = stationRepo.create("LendNoKeyD");
        var memberC = stationMemberRepo.create(stationC.id(), account.id());

        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                stationD.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPair),
                "https://remote-lending.example.com",
                null);

        var req = service.createRequest(
                stationC.id(),
                stationD.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                memberC.id(),
                null,
                null,
                "");
        service.sendMessage(req.id(), stationC.id(), memberC.id(), "C", "msg from C");

        var messages = service.getMessages(req.id(), stationD.id());
        assertNotNull(messages);

        stationRepo.delete(stationC.id());
        stationRepo.delete(stationD.id());
    }

    @Test
    @Order(200)
    void findAvailableInventoryFromPartner() {
        var results = service.findAvailableInventory(stationB.id(), null, null, null);
        assertFalse(results.entries().isEmpty());
        assertNull(results.emptyReason());
        assertTrue(results.entries().stream()
                .anyMatch(e -> e.inventoryId() == inventoryIdA && stationA.uid().equals(e.stationId())));
    }

    @Test
    @Order(201)
    void findAvailableInventoryWithQuery() {
        var results = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                .entries();
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(e -> e.inventoryId() == inventoryIdA));

        var empty = service.findAvailableInventory(stationB.id(), "NonExistentXYZ", null, null);
        assertTrue(empty.entries().stream().noneMatch(e -> e.inventoryId() == inventoryIdA));
        assertEquals(LendingService.EmptyReason.NOTHING_FREE, empty.emptyReason());
    }

    @Test
    @Order(202)
    void findAvailableInventoryNoItems() {
        var emptyInv = inventoryRepo.create(stationA.id(), "EmptyInvForLending", InventoryType.INTERNAL, false);
        var results = service.findAvailableInventory(stationB.id(), "EmptyInvForLending", null, null)
                .entries();
        assertTrue(results.stream().noneMatch(e -> e.inventoryId() == emptyInv.id()));
        inventoryRepo.delete(emptyInv.id());
    }

    @Test
    @Order(203)
    void getMessagesLocalPartner() {
        var req = service.createRequest(
                stationB.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                memberB.id(),
                null,
                null,
                "");

        lendingRepo.createMessage(req.id(), stationA.uid(), memberA.id(), "Msg from A side", false);
        lendingRepo.createMessage(req.id(), stationB.uid(), memberB.id(), "Msg from B side", false);

        var messages = service.getMessages(req.id(), stationA.id());
        assertFalse(messages.isEmpty());
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Msg from A side")));
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Msg from B side")));
        for (int i = 1; i < messages.size(); i++) {
            assertFalse(
                    messages.get(i).createdAt().isBefore(messages.get(i - 1).createdAt()),
                    "Messages should be sorted by createdAt");
        }
    }

    @Test
    @Order(204)
    void availableInventoryEntryRecord() {
        var station = UUID.randomUUID();
        var entry =
                new LendingService.AvailableInventoryEntry(42, "Test Inv", 9, "Blau", station, "Station X", 5, null);
        assertEquals(42, entry.inventoryId());
        assertEquals("Test Inv", entry.inventoryName());
        assertEquals(9, entry.artId());
        assertEquals("Blau", entry.artName());
        assertEquals(station, entry.stationId());
        assertEquals("Station X", entry.stationName());
        assertEquals(5, entry.availableCount());
        assertNull(entry.distanceKm());
    }

    @Test
    @Order(204)
    void findAvailableInventoryDistanceEnrichment() {
        var before = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                .entries();
        assertTrue(before.stream().anyMatch(e -> e.inventoryId() == inventoryIdA));
        assertTrue(before.stream().allMatch(e -> e.distanceKm() == null));

        stationRepo.updateLocation(
                stationB.id(), null, null, null, null, new BigDecimal("52.520008"), new BigDecimal("13.404954"));
        stationRepo.updateLocation(
                stationA.id(), null, null, null, null, new BigDecimal("48.137154"), new BigDecimal("11.576124"));

        var enriched = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                .entries();
        var aEntry = enriched.stream()
                .filter(e -> e.inventoryId() == inventoryIdA)
                .findFirst()
                .orElseThrow();
        assertNotNull(aEntry.distanceKm());
        assertTrue(aEntry.distanceKm() > 400 && aEntry.distanceKm() < 600);

        stationRepo.updateLocation(stationA.id(), null, null, null, null, null, null);
        var partial = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                .entries();
        assertTrue(partial.stream().filter(e -> e.inventoryId() == inventoryIdA).allMatch(e -> e.distanceKm() == null));

        stationRepo.updateLocation(stationB.id(), null, null, null, null, null, null);
    }

    @Test
    @Order(205)
    void getMessagesRemotePartnerSortsCorrectly() {
        var stationR = stationRepo.create("LendRemoteSortR");
        var memberR = stationMemberRepo.create(stationR.id(), account.id());

        var keyPairR = federationService.generateKeyPair();
        var partnerR = federationService.acceptInvite(
                stationR.id(),
                stationA.id(),
                federationService.encodePublicKey(keyPairR),
                null,
                "https://remote-sort.example.com");

        var req = service.createRequest(
                stationR.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                memberR.id(),
                null,
                null,
                "");
        service.sendMessage(req.id(), stationA.id(), memberA.id(), "A", "Local msg 1");

        var now = Instant.now();
        var earlyMsg = new LendingMessage(
                8001, req.id(), stationR.uid(), memberR.id(), "Remote early", false, now.minusSeconds(60));
        var lateMsg = new LendingMessage(
                8002, req.id(), stationR.uid(), memberR.id(), "Remote late", false, now.plusSeconds(60));
        when(httpClient.getList(
                        eq("https://remote-sort.example.com"),
                        pathIs("/remote/lending/requests/" + req.uid() + "/messages"),
                        any(),
                        eq(stationA.id()),
                        eq(LendingMessage.class)))
                .thenReturn(List.of(lateMsg, earlyMsg));

        when(httpClient.canSign(stationA.id())).thenReturn(true);

        var messages = service.getMessages(req.id(), stationA.id());
        assertFalse(messages.isEmpty());
        for (int i = 1; i < messages.size(); i++) {
            assertFalse(
                    messages.get(i).createdAt().isBefore(messages.get(i - 1).createdAt()),
                    "Messages should be sorted by createdAt");
        }
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Local msg 1")));
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Remote early")));
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Remote late")));

        federationService.endFederation(partnerR.id());
        stationRepo.delete(stationR.id());
    }

    @Test
    @Order(206)
    void findAvailableInventoryWithDateRange() {
        var results = service.findAvailableInventory(
                        stationB.id(), null, LocalDate.now(), LocalDate.now().plusDays(7))
                .entries();
        assertTrue(results.stream().anyMatch(e -> e.inventoryId() == inventoryIdA));
    }

    @Test
    @Order(207)
    void findAvailableInventoryBlockedStation() {
        var block = service.createBlock(
                stationA.id(), null, null, LocalDate.now(), LocalDate.now().plusDays(7), "Test");
        var results = service.findAvailableInventory(
                        stationB.id(), null, LocalDate.now(), LocalDate.now().plusDays(7))
                .entries();
        assertTrue(results.stream().noneMatch(e -> stationA.uid().equals(e.stationId())));
        service.deleteBlock(block.id(), stationA.id());
    }

    @Test
    @Order(208)
    void findAvailableInventoryBlockedInventory() {
        var block = service.createBlock(
                stationA.id(),
                inventoryIdA,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                "Inv block");
        var results = service.findAvailableInventory(
                        stationB.id(), null, LocalDate.now(), LocalDate.now().plusDays(7))
                .entries();
        assertTrue(results.stream().noneMatch(e -> e.inventoryId() == inventoryIdA));
        service.deleteBlock(block.id(), stationA.id());
    }

    @Test
    @Order(209)
    void fetchRemoteMessagesNoPrivateKey() {
        var stationNoPk = stationRepo.create("LendNoPK");
        var memberNoPk = stationMemberRepo.create(stationNoPk.id(), account.id());
        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationNoPk.id(),
                stationA.id(),
                federationService.encodePublicKey(keyPair),
                null,
                "https://remote-nopk.example.com");

        when(httpClient.canSign(stationNoPk.id())).thenReturn(false);

        var req = lendingRepo.createRequest(
                stationNoPk.uid(),
                stationA.uid(),
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                memberNoPk.id(),
                null,
                null,
                "");
        lendingRepo.createMessage(req.id(), stationNoPk.uid(), memberNoPk.id(), "local only", false);

        var messages = service.getMessages(req.id(), stationNoPk.id());
        assertNotNull(messages);
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("local only")));

        federationService.endFederation(partner.id());
        stationRepo.delete(stationNoPk.id());
    }

    @Test
    @Order(60)
    void getMessagesWithNoPartner() {
        var stationE = stationRepo.create("LendNoPartnerE");
        var stationF = stationRepo.create("LendNoPartnerF");
        var memberE = stationMemberRepo.create(stationE.id(), account.id());

        var req = lendingRepo.createRequest(
                stationE.uid(),
                stationF.uid(),
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                memberE.id(),
                null,
                null,
                "");
        lendingRepo.createMessage(req.id(), stationE.uid(), memberE.id(), "hello", false);

        var messages = service.getMessages(req.id(), stationE.id());
        assertNotNull(messages);
        assertFalse(messages.isEmpty());

        stationRepo.delete(stationE.id());
        stationRepo.delete(stationF.id());
    }

    /**
     * A station holding the body's jacket is not its owner and may not pass it on. The manual path
     * is the one a person drives, so it says no out loud.
     */
    @Test
    @Order(300)
    void assignItemRefusesGearTheStationOnlyHolds() {
        var inv = inventoryRepo.create(stationA.id(), "LendSvcHeldGear", InventoryType.INTERNAL, false);
        var held =
                inventoryRepo.createItem(inv.id(), "HELD-001", "Kreis-Jacke", null, null, ItemOwner.CLUSTER, clusterId);
        var line = lineOn(stationA, inv.id(), null);

        var refused = assertThrows(RefusalResponse.class, () -> service.assignItem(line, held.id(), stationA.id()));
        assertEquals(FederationRefusal.LENDING_GEAR_NOT_THE_STATIONS, refused.refusal());
    }

    /**
     * The body lending its own gear is the owner acting, and that gear sits as ordinary inventory on
     * the station shell it owns. The blunt refusal of everything cluster-owned was never the rule.
     */
    @Test
    @Order(301)
    void assignItemAllowsTheOwningBodyOnItsOwnShell() {
        var inv = inventoryRepo.create(clusterHome.id(), "LendSvcClusterGear", InventoryType.INTERNAL, false);
        var own = inventoryRepo.createItem(inv.id(), "CLU-001", "Kreis-Zelt", null, null, ItemOwner.CLUSTER, clusterId);
        var line = lineOn(clusterHome, inv.id(), null);

        assertTrue(service.assignItem(line, own.id(), clusterHome.id()));
    }

    /** Gear in another station's inventory is never this station's to lend, whoever owns it. */
    @Test
    @Order(302)
    void assignItemRefusesGearFromAnotherStationsInventory() {
        var inv = inventoryRepo.create(stationB.id(), "LendSvcForeignInv", InventoryType.INTERNAL, false);
        var foreign = inventoryRepo.createItem(inv.id(), "FOR-001", "Fremde Leiter", null, null);
        var line = lineOn(stationA, inv.id(), null);

        var refused = assertThrows(RefusalResponse.class, () -> service.assignItem(line, foreign.id(), stationA.id()));
        assertEquals(FederationRefusal.LENDING_GEAR_NOT_THE_STATIONS, refused.refusal());
    }

    /**
     * The automatic path filters rather than refusing. The status change is already committed when
     * it runs, so a refusal would reject a call for an approval that has already happened.
     */
    @Test
    @Order(303)
    void approveLeavesGearTheStationOnlyHoldsUnassigned() {
        var inv = inventoryRepo.create(stationA.id(), "LendSvcHeldOnlyInv", InventoryType.INTERNAL, false);
        inventoryRepo.createItem(inv.id(), "HO-001", "Kreis-Pumpe", null, null, ItemOwner.CLUSTER, clusterId);
        var request = requestOn(stationA);
        var line = lendingRepo
                .addRequestItem(request, inv.id(), null, null, 1, null)
                .id();

        assertTrue(service.approveRequest(request, stationA.id()));
        assertEquals(
                LendingStatus.APPROVED,
                service.findRequest(request).orElseThrow().status());
        assertNull(assignedItemOf(request, line));
    }

    /** The branch that writes a piece the requesting side named leaks the same way. */
    @Test
    @Order(304)
    void approveLeavesANamedPieceTheStationOnlyHoldsUnassigned() {
        var inv = inventoryRepo.create(stationA.id(), "LendSvcNamedHeld", InventoryType.INTERNAL, false);
        var held = inventoryRepo.createItem(
                inv.id(), "NH-001", "Kreis-Schlauch", null, null, ItemOwner.CLUSTER, clusterId);
        var request = requestOn(stationA);
        var line = lendingRepo
                .addRequestItem(request, inv.id(), held.id(), null, 1, null)
                .id();

        assertTrue(service.approveRequest(request, stationA.id()));
        assertNull(assignedItemOf(request, line));
    }

    /** The requesting side names the inventory too, and nothing checked whose inventory it was. */
    @Test
    @Order(305)
    void approveIgnoresAnInventoryThatIsNotTheOwningStations() {
        var inv = inventoryRepo.create(stationB.id(), "LendSvcAskersOwnInv", InventoryType.INTERNAL, false);
        inventoryRepo.createItem(inv.id(), "AO-001", "Eigene Leiter", null, null);
        var request = requestOn(stationA);
        var line = lendingRepo
                .addRequestItem(request, inv.id(), null, null, 1, null)
                .id();

        assertTrue(service.approveRequest(request, stationA.id()));
        assertNull(assignedItemOf(request, line));
    }

    /** The station's own gear is still filled in, which is what the filter must not break. */
    @Test
    @Order(306)
    void approveStillAssignsTheStationsOwnGear() {
        var inv = inventoryRepo.create(stationA.id(), "LendSvcOwnedInv", InventoryType.INTERNAL, false);
        var own = inventoryRepo.createItem(inv.id(), "OW-001", "Wachen-Leiter", null, null);
        var request = requestOn(stationA);
        var line = lendingRepo
                .addRequestItem(request, inv.id(), null, null, 1, null)
                .id();

        assertTrue(service.approveRequest(request, stationA.id()));
        assertEquals(own.id(), assignedItemOf(request, line));
    }

    @Test
    @Order(310)
    void findAvailableInventoryLeavesOutGearTheStationOnlyHolds() {
        var inv = inventoryRepo.create(stationA.id(), "LendSvcOfferHeld", InventoryType.INTERNAL, false);
        inventoryRepo.createItem(inv.id(), "OH-001", "Kreis-Zelt", null, null, ItemOwner.CLUSTER, clusterId);
        shareService.setInventoryShare(stationA.id(), inv.id(), ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        var results = service.findAvailableInventory(stationB.id(), "LendSvcOfferHeld", null, null)
                .entries();
        assertTrue(results.stream().noneMatch(e -> e.inventoryId() == inv.id()));

        inventoryRepo.createItem(inv.id(), "OH-002", "Wachen-Zelt", null, null);
        var again = service.findAvailableInventory(stationB.id(), "LendSvcOfferHeld", null, null)
                .entries();
        var entry = again.stream()
                .filter(e -> e.inventoryId() == inv.id())
                .findFirst()
                .orElseThrow();
        assertEquals(1, entry.availableCount());
    }

    @Test
    @Order(311)
    void findAssignableItemsLeavesOutGearTheStationOnlyHolds() {
        var inv = inventoryRepo.create(stationA.id(), "LendSvcPicker", InventoryType.INTERNAL, false);
        var own = inventoryRepo.createItem(inv.id(), "PK-001", "Wachen-Pumpe", null, null);
        inventoryRepo.createItem(inv.id(), "PK-002", "Kreis-Pumpe", null, null, ItemOwner.CLUSTER, clusterId);

        var offered = service.findAssignableItems(stationA.id(), inv.id());
        assertEquals(List.of(own.id()), offered.stream().map(InventoryItem::id).toList());

        assertTrue(service.findAssignableItems(stationB.id(), inv.id()).isEmpty());
    }

    @Test
    @Order(320)
    void lendingSwitchedOffHidesThePartnerAndRefusesTheRequest() {
        federationService.setCapability(partnerIdBtoA, CapabilityType.INVENTORY_LEND, Direction.IMPORT, false);
        try {
            var results = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                    .entries();
            assertTrue(results.stream().noneMatch(e -> stationA.uid().equals(e.stationId())));
            assertThrows(
                    RefusalResponse.class,
                    () -> service.createRequest(
                            stationB.id(),
                            stationA.id(),
                            LocalDate.now(),
                            LocalDate.now().plusDays(1),
                            memberB.id(),
                            null,
                            null,
                            ""));
        } finally {
            federationService.setCapability(partnerIdBtoA, CapabilityType.INVENTORY_LEND, Direction.IMPORT, true);
        }
        var restored = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                .entries();
        assertTrue(restored.stream().anyMatch(e -> stationA.uid().equals(e.stationId())));
    }

    @Test
    @Order(321)
    void createRequestRefusesAStationThatIsNoPartner() {
        var stranger = stationRepo.create("LendSvcStranger");

        assertThrows(
                RefusalResponse.class,
                () -> service.createRequest(
                        stationB.id(),
                        stranger.id(),
                        LocalDate.now(),
                        LocalDate.now().plusDays(1),
                        memberB.id(),
                        null,
                        null,
                        ""));

        stationRepo.delete(stranger.id());
    }

    /** A request from stationB to the given owner, written straight to the repository. */
    private static int requestOn(Station owner) {
        return lendingRepo
                .createRequest(
                        stationB.uid(),
                        owner.uid(),
                        LocalDate.now(),
                        LocalDate.now().plusDays(2),
                        memberB.id(),
                        null,
                        null,
                        "")
                .id();
    }

    /** One line of such a request, for tests that only need something to assign against. */
    private static int lineOn(Station owner, Integer inventoryId, Integer itemId) {
        return lendingRepo
                .addRequestItem(requestOn(owner), inventoryId, itemId, null, 1, null)
                .id();
    }

    /** Station A's side of its partnership with B, asked by the given station. */
    private static ServingPartner servingAForB(UUID asking) {
        var row = federationRepo
                .findPartnerByStationAndRemoteUid(stationA.id(), stationB.uid())
                .orElseThrow();
        return new ServingPartner(row, asking);
    }

    private static Integer assignedItemOf(int requestId, int requestItemId) {
        return lendingRepo.findAssignedItems(requestItemId).stream().findFirst().orElse(null);
    }

    /**
     * Gear nobody has said anything about is not on offer, and the answer says so rather than
     * reading as a fault to whoever is looking at an empty screen.
     */
    @Test
    @Order(400)
    void nothingIsOfferedUntilAShareSaysSo() {
        var inventory = inventoryRepo.create(stationA.id(), "LendSvcOptIn", InventoryType.INTERNAL, false);
        inventoryRepo.createItem(inventory.id(), "OPT-001", "Opt In Item", null, null);

        var before = service.findAvailableInventory(stationB.id(), "LendSvcOptIn", null, null);
        assertTrue(before.entries().isEmpty());
        assertEquals(LendingService.EmptyReason.NOTHING_FREE, before.emptyReason());

        shareService.setInventoryShare(
                stationA.id(), inventory.id(), ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        assertTrue(service.findAvailableInventory(stationB.id(), "LendSvcOptIn", null, null).entries().stream()
                .anyMatch(e -> e.inventoryId() == inventory.id()));
    }

    /** The drawer goes out, the one good radio stays: the narrower row wins over the wider one. */
    @Test
    @Order(401)
    void anItemIsWithheldFromASharedInventory() {
        var inventory = inventoryRepo.create(stationA.id(), "LendSvcWithheld", InventoryType.INTERNAL, false);
        var kept = inventoryRepo.createItem(inventory.id(), "WH-001", "Gutes Funkgerät", null, null);
        shareService.setInventoryShare(
                stationA.id(), inventory.id(), ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        assertTrue(service.findAvailableInventory(stationB.id(), "LendSvcWithheld", null, null).entries().stream()
                .anyMatch(e -> e.inventoryId() == inventory.id()));

        shareService.setItemShare(stationA.id(), kept.id(), ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());
        var results = service.findAvailableInventory(stationB.id(), "LendSvcWithheld", null, null);
        assertTrue(results.entries().isEmpty());
        assertEquals(LendingService.EmptyReason.NOTHING_FREE, results.emptyReason());

        shareService.removeItemShare(stationA.id(), kept.id());
        assertTrue(service.findAvailableInventory(stationB.id(), "LendSvcWithheld", null, null).entries().stream()
                .anyMatch(e -> e.inventoryId() == inventory.id()));
    }

    /** Turning lending off for a partner takes the whole offer away from that partner. */
    @Test
    @Order(402)
    void aPartnerWithoutLendingSeesNothing() {
        federationService.setCapability(partnerIdAtoB, CapabilityType.INVENTORY_LEND, Direction.EXPORT, false);
        var results = service.findAvailableInventory(stationB.id(), null, null, null);
        assertTrue(results.entries().isEmpty());
        assertEquals(LendingService.EmptyReason.NOTHING_SHARED, results.emptyReason());

        federationService.setCapability(partnerIdAtoB, CapabilityType.INVENTORY_LEND, Direction.EXPORT, true);
        assertFalse(service.findAvailableInventory(stationB.id(), null, null, null)
                .entries()
                .isEmpty());
    }

    /**
     * A share the station has since withdrawn does not reach back into a request that was already
     * approved: the partner was told yes and has planned around it.
     */
    @Test
    @Order(403)
    void anApprovedRequestRunsToCompletionAfterTheShareIsWithdrawn() {
        var inventory = inventoryRepo.create(stationA.id(), "LendSvcRunsToEnd", InventoryType.INTERNAL, false);
        inventoryRepo.createItem(inventory.id(), "LSVC-303", "Runs To End", null, null);
        shareService.setInventoryShare(
                stationA.id(), inventory.id(), ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        var request = service.createRequest(
                stationB.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                memberB.id(),
                null,
                null,
                "");
        service.addRequestItem(request.id(), inventory.id(), null, null, 1, null);
        assertTrue(service.approveRequest(request.id(), stationA.id()));
        assertFalse(lendingRepo
                .findAssignedItems(
                        service.findRequestItems(request.id()).getFirst().id())
                .isEmpty());

        shareService.removeInventoryShare(stationA.id(), inventory.id());
        assertTrue(service.findAvailableInventory(stationB.id(), "LendSvcRunsToEnd", null, null)
                .entries()
                .isEmpty());
        assertTrue(service.markLent(request.id(), stationA.id()));
        assertTrue(service.markReturned(request.id(), stationA.id()));
        assertTrue(service.closeRequest(request.id(), stationA.id()));
    }

    /** A share aimed at named partners reaches only those, and the row still beats the one above it. */
    @Test
    @Order(404)
    void aShareAimedAtNamedPartnersReachesOnlyThose() {
        var inventory = inventoryRepo.create(stationA.id(), "LendSvcNamed", InventoryType.INTERNAL, false);
        inventoryRepo.createItem(inventory.id(), "NM-001", "Genannte Leiter", null, null);

        shareService.setInventoryShare(
                stationA.id(), inventory.id(), ShareScope.SPECIFIC, ShareGrant.GRANT, List.of(partnerIdAtoB));
        assertTrue(service.findAvailableInventory(stationB.id(), "LendSvcNamed", null, null).entries().stream()
                .anyMatch(e -> e.inventoryId() == inventory.id()));

        shareService.setInventoryShare(stationA.id(), inventory.id(), ShareScope.SPECIFIC, ShareGrant.GRANT, List.of());
        var results = service.findAvailableInventory(stationB.id(), "LendSvcNamed", null, null);
        assertTrue(results.entries().isEmpty());
        assertEquals(LendingService.EmptyReason.NOTHING_FREE, results.emptyReason());
    }

    /**
     * A request that names no return date is approved and filled like any other. The open end is a
     * window reaching forward, not a moment outside every calendar.
     */
    @Test
    @Order(500)
    void aRequestWithNoReturnDateIsApproved() {
        var inventory = inventoryRepo.create(stationA.id(), "LendSvcOpenEnd", InventoryType.INTERNAL, false);
        inventoryRepo.createItem(inventory.id(), "OE-001", "Offenes Ende", null, null);
        shareService.setInventoryShare(
                stationA.id(), inventory.id(), ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        var request = service.createRequest(
                stationB.id(), stationA.id(), LocalDate.now(), null, memberB.id(), null, null, "");
        service.addRequestItem(request.id(), inventory.id(), null, null, 1, null);

        assertTrue(service.approveRequest(request.id(), stationA.id()));
        assertEquals(
                LendingStatus.APPROVED,
                service.findRequest(request.id()).orElseThrow().status());
        assertTrue(lendingRepo.findMessagesByRequest(request.id()).stream().anyMatch(LendingMessage::isSystem));
        assertFalse(lendingRepo
                .findAssignedItems(
                        service.findRequestItems(request.id()).getFirst().id())
                .isEmpty());
        assertTrue(service.closeRequest(request.id(), stationA.id()));
    }

    /**
     * What the station's own appointment needs is not on offer, even though no piece has been picked for
     * it: a loose claim takes a count out of the drawer all the same.
     */
    @Test
    @Order(501)
    void anOwnAppointmentKeepsBackWhatItNeeds() {
        var inventory = inventoryRepo.create(stationA.id(), "LendSvcOwnNeed", InventoryType.INTERNAL, false);
        for (int piece = 1; piece <= 3; piece++) {
            inventoryRepo.createItem(inventory.id(), "ON-00" + piece, "Funkgerät " + piece, null, null);
        }
        shareService.setInventoryShare(
                stationA.id(), inventory.id(), ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        var day = LocalDate.now().plusDays(20);
        var event = EquipmentTestSupport.oneOff(eventRepo, stationA.id(), "LendSvcOwnNeedEvent", day);
        equipmentNeedRepo.create(event.id(), null, null, null, inventory.id(), 2, 0, 0);

        var request = service.createRequest(stationB.id(), stationA.id(), day, day, memberB.id(), null, null, "");
        var line = service.addRequestItem(request.id(), inventory.id(), null, null, 3, null);

        assertTrue(service.approveRequest(request.id(), stationA.id()));
        assertEquals(1, lendingRepo.findAssignedItems(line.id()).size());

        assertTrue(service.closeRequest(request.id(), stationA.id()));
        equipmentNeedRepo.deleteByEvent(event.id());
        eventRepo.delete(event.id());
    }

    /**
     * A piece that is already at a partner is owned by the station and is not there to be lent, so it
     * is not promised a second time.
     */
    @Test
    @Order(502)
    void aPieceAlreadyAtAPartnerIsNotPromisedAgain() {
        var inventory = inventoryRepo.create(stationA.id(), "LendSvcAway", InventoryType.INTERNAL, false);
        var away = inventoryRepo.createItem(inventory.id(), "AW-001", "Schon unterwegs", null, null);
        shareService.setInventoryShare(
                stationA.id(), inventory.id(), ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        inventoryRepo.updateCustody(away.id(), ItemCustody.WITH_PARTNER, stationA.id(), null, null, stationB.id());

        var request = service.createRequest(
                stationB.id(),
                stationA.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                memberB.id(),
                null,
                null,
                "");
        var line = service.addRequestItem(request.id(), inventory.id(), away.id(), null, 1, null);

        assertTrue(service.approveRequest(request.id(), stationA.id()));
        assertTrue(lendingRepo.findAssignedItems(line.id()).isEmpty());

        assertTrue(service.closeRequest(request.id(), stationA.id()));
    }

    /** A partnership of station A with a station on another instance, with lending switched on. */
    private static FederationPartner partnerElsewhere(UUID elsewhere) {
        var keyPair = federationService.generateKeyPair();
        var created = federationRepo.createPartner(
                stationA.id(),
                elsewhere,
                "lend-elsewhere-" + elsewhere,
                federationService.encodePublicKey(keyPair),
                "https://lending-elsewhere.example.com");
        federationRepo.activatePartner(created.id(), federationService.encodePublicKey(keyPair));
        federationRepo.upsertCapability(created.id(), CapabilityType.INVENTORY_LEND, Direction.IMPORT, true);
        federationRepo.upsertCapability(created.id(), CapabilityType.INVENTORY_LEND, Direction.EXPORT, true);
        FederationTestContracts.storeCurrentContractOnRemotePartners(federationService, federationRepo, stationA.id());
        return federationRepo.findPartnerById(created.id()).orElseThrow();
    }

    /**
     * A partner on another instance sees what this station offers, asks for it, and the request
     * moves on at both ends: this station agrees here, the partner gives the gear back there.
     */
    @Test
    @Order(600)
    void aPartnerElsewhereBorrowsFromThisStation() {
        UUID elsewhere = UUID.randomUUID();
        var row = partnerElsewhere(elsewhere);
        var asking = new ServingPartner(row, elsewhere);

        var offered = service.serveAvailability(asking, null, null, null);
        assertTrue(offered.offersAnything());
        assertTrue(offered.entries().stream().anyMatch(entry -> entry.inventoryId() == inventoryIdA));

        UUID uid = UUID.randomUUID();
        var sent = new RemoteLendingRoutes.RemoteLendingRequest(
                uid,
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                "Übung",
                List.of(new RemoteLendingRoutes.RemoteLendingLine(inventoryIdA, null, null, 1)));
        var accepted = service.serveRequest(asking, sent);
        assertEquals(List.of("LendSvcInventory"), accepted.labels());
        assertEquals(accepted, service.serveRequest(asking, sent), "a request sent twice is written down once");

        var copy = lendingRepo.findRequestByUid(uid).orElseThrow();
        assertEquals(elsewhere, copy.requestingStationUid());
        assertEquals(stationA.uid(), copy.owningStationUid());
        assertEquals("Übung", copy.occasion());
        assertNull(copy.createdBy());

        assertTrue(service.approveRequest(copy.id(), stationA.id()));
        var early = assertThrows(
                RefusalResponse.class,
                () -> service.serveStatus(
                        asking, uid, new RemoteLendingRoutes.RemoteLendingStatus(LendingStatus.LENT, null)));
        assertEquals(FederationRefusal.LENDING_NOT_THE_OWNING_STATION, early.refusal());

        service.serveStatus(asking, uid, new RemoteLendingRoutes.RemoteLendingStatus(LendingStatus.RETURNED, null));
        assertEquals(
                LendingStatus.RETURNED,
                service.findRequest(copy.id()).orElseThrow().status());
        service.serveStatus(asking, uid, new RemoteLendingRoutes.RemoteLendingStatus(LendingStatus.RETURNED, null));

        assertTrue(service.serveMessages(asking, uid).stream().anyMatch(message -> message.isSystem()));
        assertDoesNotThrow(
                () -> service.serveNotice(asking, uid, new RemoteLendingRoutes.RemoteLendingNotice("Fern Leser")));

        federationRepo.deletePartner(row.id());
    }

    /** A request from elsewhere naming gear this station does not hold is refused whole. */
    @Test
    @Order(601)
    void aRequestFromElsewhereForSomebodyElsesGearIsRefused() {
        UUID elsewhere = UUID.randomUUID();
        var row = partnerElsewhere(elsewhere);
        var foreign = inventoryRepo.create(stationB.id(), "LendSvcForeign", InventoryType.INTERNAL, false);
        var sent = new RemoteLendingRoutes.RemoteLendingRequest(
                UUID.randomUUID(),
                LocalDate.now(),
                null,
                "",
                List.of(new RemoteLendingRoutes.RemoteLendingLine(foreign.id(), null, null, 1)));

        var refused = assertThrows(
                RefusalResponse.class, () -> service.serveRequest(new ServingPartner(row, elsewhere), sent));
        assertEquals(FederationRefusal.LENDING_LINE_NAMES_FOREIGN_GEAR, refused.refusal());
        assertTrue(lendingRepo.findRequestByUid(sent.uid()).isEmpty());

        var stranger = new ServingPartner(row, UUID.randomUUID());
        assertThrows(RefusalResponse.class, () -> service.serveMessages(stranger, UUID.randomUUID()));

        federationRepo.deletePartner(row.id());
    }

    /**
     * This station borrows from a partner on another instance. The lines name the partner's gear,
     * which is not here, so they keep the names the partner gave them; a partner that cannot be
     * reached leaves nothing behind.
     */
    @Test
    @Order(602)
    void thisStationBorrowsFromAPartnerElsewhere() {
        UUID elsewhere = UUID.randomUUID();
        var row = partnerElsewhere(elsewhere);
        when(httpClient.canSign(stationA.id())).thenReturn(true);
        when(httpClient.get(
                        eq("https://lending-elsewhere.example.com"),
                        pathIs("/remote/lending/available?q=Funk"),
                        eq(elsewhere),
                        eq(stationA.id()),
                        eq(RemoteLendingRoutes.RemoteAvailability.class)))
                .thenReturn(new RemoteLendingRoutes.RemoteAvailability(
                        List.of(new RemoteLendingRoutes.RemoteAvailableEntry(77, "Funkgeräte", null, null, 4)), true));
        var found = service.findAvailableInventory(stationA.id(), "Funk", null, null)
                .entries();
        assertTrue(found.stream().anyMatch(entry -> entry.inventoryId() == 77 && elsewhere.equals(entry.stationId())));

        when(httpClient.post(
                        eq("https://lending-elsewhere.example.com"),
                        pathIs("/remote/lending/requests"),
                        any(),
                        eq(elsewhere),
                        eq(stationA.id()),
                        eq(RemoteLendingRoutes.RemoteLendingAccepted.class)))
                .thenReturn(new RemoteLendingRoutes.RemoteLendingAccepted(List.of("Funkgeräte")));
        var request = service.createRequest(
                stationA.id(),
                elsewhere,
                LocalDate.now(),
                LocalDate.now(),
                memberA.id(),
                null,
                null,
                "Zeltlager",
                List.of(new LendingService.RequestLine(77, null, null, 2, null)));
        var lines = service.findRequestItems(request.id());
        assertEquals(1, lines.size());
        assertNull(lines.getFirst().inventoryId());
        assertEquals("Funkgeräte", service.inventoryName(lines.getFirst()));
        assertEquals("2x Funkgeräte", service.buildItemSummary(request.id()));
        assertEquals("Funkgeräte", service.lineLabel(lines.getFirst()));

        when(httpClient.post(
                        eq("https://lending-elsewhere.example.com"),
                        pathIs("/remote/lending/requests"),
                        any(),
                        eq(elsewhere),
                        eq(stationA.id()),
                        eq(RemoteLendingRoutes.RemoteLendingAccepted.class)))
                .thenReturn(null);
        int before = service.findRequestsByStation(stationA.id()).size();
        assertThrows(
                RefusalResponse.class,
                () -> service.createRequest(
                        stationA.id(),
                        elsewhere,
                        LocalDate.now(),
                        LocalDate.now(),
                        memberA.id(),
                        null,
                        null,
                        "",
                        List.of(new LendingService.RequestLine(77, null, null, 1, null))));
        assertEquals(before, service.findRequestsByStation(stationA.id()).size());

        assertTrue(service.closeRequest(request.id(), stationA.id()));
        federationRepo.deletePartner(row.id());
    }

    /** A partner's name is the one a station knows it by, here or on another instance. */
    @Test
    @Order(603)
    void aStationIsNamedAsTheViewerKnowsIt() {
        assertEquals("LendSvcTestStationA", service.stationName(stationA.uid(), stationB.id()));
        assertEquals("Unknown", service.stationName(UUID.randomUUID(), stationB.id()));
    }

    /**
     * Gear lent to a partner on another instance is listed in the notice that it was handed over,
     * line by line, for the partner to write down; every line is labelled when it is asked for.
     */
    @Test
    @Order(604)
    void gearLentElsewhereIsListedForThePartnerToWriteDown() {
        UUID elsewhere = UUID.randomUUID();
        var row = partnerElsewhere(elsewhere);
        var inventory = inventoryRepo.create(stationA.id(), "LendSvcLadders", InventoryType.INTERNAL, false);
        var first = inventoryRepo.createItem(inventory.id(), "LD-1", "Leiter 1", null, null);
        var second = inventoryRepo.createItem(inventory.id(), "LD-2", "Leiter 2", null, null);
        UUID uid = UUID.randomUUID();
        service.serveRequest(
                new ServingPartner(row, elsewhere),
                new RemoteLendingRoutes.RemoteLendingRequest(
                        uid,
                        LocalDate.now(),
                        LocalDate.now().plusDays(1),
                        "Übung",
                        List.of(new RemoteLendingRoutes.RemoteLendingLine(inventory.id(), null, null, 2))));
        var copy = lendingRepo.findRequestByUid(uid).orElseThrow();
        var line = lendingRepo.findItemsByRequest(copy.id()).getFirst();
        assertEquals("LendSvcLadders", line.label(), "the line is labelled when it is asked for");
        lendingRepo.assignItem(line.id(), first.id());
        lendingRepo.assignItem(line.id(), second.id());
        when(httpClient.canSign(stationA.id())).thenReturn(true);
        when(httpClient.post(any(), any(), any(), any(), eq(stationA.id()))).thenReturn(true);

        assertTrue(service.markLent(copy.id(), stationA.id()));

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(httpClient)
                .post(
                        eq("https://lending-elsewhere.example.com"),
                        pathIs("/remote/lending/requests/" + uid + "/status"),
                        sent.capture(),
                        eq(elsewhere),
                        eq(stationA.id()));
        var status = (RemoteLendingRoutes.RemoteLendingStatus) sent.getValue();
        assertEquals(LendingStatus.LENT, status.status());
        assertEquals(
                List.of(
                        new RemoteLendingRoutes.RemoteLendingPiece(0, "LD-1", "Leiter 1"),
                        new RemoteLendingRoutes.RemoteLendingPiece(0, "LD-2", "Leiter 2")),
                status.handedOver());
        var lent = inventoryRepo.findItemById(first.id()).orElseThrow();
        assertEquals(ItemCustody.WITH_PARTNER, lent.custody());
        assertNull(lent.custodyPartnerStationId(), "the partner holding it has no row here");

        assertTrue(service.markReturned(copy.id(), stationA.id()));
        federationRepo.deletePartner(row.id());
    }

    /**
     * Gear borrowed from a partner on another instance is written down at the borrower from what the
     * partner listed, owned by the partner's uid, and goes again when it is handed back.
     */
    @Test
    @Order(605)
    void gearBorrowedFromElsewhereIsWrittenDownAndHandedBack() {
        UUID elsewhere = UUID.randomUUID();
        var row = partnerElsewhere(elsewhere);
        UUID uid = UUID.randomUUID();
        var request = lendingRepo.createRequest(
                uid,
                stationA.uid(),
                elsewhere,
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                memberA.id(),
                null,
                null,
                "Übung");
        var ropes = lendingRepo.addRequestItem(request.id(), null, null, null, 2, null);
        var pump = lendingRepo.addRequestItem(request.id(), null, null, null, 1, null);
        lendingRepo.labelItems(request.id(), List.of("Seile", "Pumpe"));
        var asking = new ServingPartner(row, elsewhere);

        service.serveStatus(
                asking,
                uid,
                new RemoteLendingRoutes.RemoteLendingStatus(
                        LendingStatus.LENT,
                        null,
                        List.of(
                                new RemoteLendingRoutes.RemoteLendingPiece(0, "S-1", "Seil 1"),
                                new RemoteLendingRoutes.RemoteLendingPiece(0, "S-2", "Seil 2"),
                                new RemoteLendingRoutes.RemoteLendingPiece(1, null, "Pumpe 1"),
                                new RemoteLendingRoutes.RemoteLendingPiece(7, "X-1", "Ohne Zeile"))));

        var borrowed = inventoryRepo.findBorrowedItems(stationA.id()).stream()
                .filter(piece -> piece.loanRequestId() == request.id())
                .toList();
        assertEquals(3, borrowed.size(), "a piece naming no line of the request is not written down");
        assertTrue(borrowed.stream().allMatch(piece -> elsewhere.equals(piece.ownerStationUid())));
        assertTrue(borrowed.stream().allMatch(piece -> piece.ownerStationId() == null));
        assertEquals(
                List.of(pump.id(), ropes.id(), ropes.id()),
                borrowed.stream()
                        .map(piece -> piece.item().loanRequestItemId())
                        .sorted((left, right) -> Integer.compare(right, left))
                        .toList());
        when(httpClient.canSign(stationA.id())).thenReturn(true);
        when(httpClient.post(any(), any(), any(), any(), eq(stationA.id()))).thenReturn(true);

        assertTrue(service.markReturned(request.id(), stationA.id()));

        assertTrue(inventoryRepo.findBorrowedItems(stationA.id()).stream()
                .noneMatch(piece -> piece.loanRequestId() == request.id()));
        verify(httpClient)
                .post(
                        eq("https://lending-elsewhere.example.com"),
                        pathIs("/remote/lending/requests/" + uid + "/status"),
                        any(),
                        eq(elsewhere),
                        eq(stationA.id()));
        federationRepo.deletePartner(row.id());
    }
}
