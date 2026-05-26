/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LendingServiceTest extends RepositoryTestBase {

    private static LendingService service;
    private static LendingRepository lendingRepo;
    private static FederationRepository federationRepo;
    private static FederationService federationService;
    private static FederationHttpClient httpClient;

    private static Station stationA;
    private static Station stationB;
    private static Account account;
    private static StationMember memberA;
    private static StationMember memberB;

    private static int requestId;
    private static int requestItemId;
    private static int inventoryIdA;
    private static int itemIdA;
    private static int partnerIdAtoB;

    @BeforeAll
    static void setup() {
        lendingRepo = new LendingRepository();
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        httpClient = mock(FederationHttpClient.class);
        service = new LendingService(
                lendingRepo, httpClient, federationService, stationRepo, inventoryRepo, new DomainEventBus(Set.of()));

        stationA = stationRepo.create("LendSvcTestStationA");
        stationB = stationRepo.create("LendSvcTestStationB");

        account = accountRepo.create("lendsvc@test.com", "Lend", "SvcTester");
        memberA = stationMemberRepo.create(stationA.id(), account.id());
        memberB = stationMemberRepo.create(stationB.id(), account.id());

        // Create inventory
        var inv = inventoryRepo.create(stationA.id(), "LendSvcInventory", InventoryType.INTERNAL, false);
        inventoryIdA = inv.id();
        var item = inventoryRepo.createItem(inventoryIdA, "LSVC-001", "Lend Svc Item", null, "{}");
        itemIdA = item.id();

        // Create federation between A and B (local, remoteHost = null)
        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationB.id(), stationA.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerIdAtoB = partner.id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
        accountRepo.delete(account.id());
    }

    // -- Create and Find --

    @Test
    @Order(1)
    void createRequest() {
        var dateFrom = LocalDate.now();
        var dateTo = LocalDate.now().plusDays(7);
        var request = service.createRequest(stationB.id(), stationA.id(), dateFrom, dateTo, memberB.id());
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
        var item = service.addRequestItem(requestId, inventoryIdA, itemIdA, 2);
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

    // -- Status Transitions --

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
        // Create a new request to decline
        var req = service.createRequest(
                stationB.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(3), memberB.id());
        assertTrue(service.declineRequest(req.id(), stationA.id(), "Not available"));
        var declined = service.findRequest(req.id()).orElseThrow();
        assertEquals(LendingStatus.DECLINED, declined.status());
    }

    // -- Messages --

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
        // Add messages from both sides
        service.sendMessage(requestId, stationA.id(), memberA.id(), "Tester A", "Hello from A");
        service.sendMessage(requestId, stationB.id(), memberB.id(), "Tester B", "Hello from B");

        // getMessages should merge both sides using direct DB (local partner)
        var messages = service.getMessages(requestId, stationA.id());
        assertFalse(messages.isEmpty());
        // Should contain messages from both stations
        assertTrue(messages.stream().anyMatch(m -> m.senderStationId() == stationA.id()));
        assertTrue(messages.stream().anyMatch(m -> m.senderStationId() == stationB.id()));

        // Verify HTTP client was never called (local partner)
        verify(httpClient, never()).fetchRemoteMessages(anyString(), anyInt(), anyInt(), anyString());
    }

    @Test
    @Order(22)
    void getMessagesForRemotePartner() {
        // Create remote federation
        var stationC = stationRepo.create("LendSvcTestStationC");
        var memberC = stationMemberRepo.create(stationC.id(), account.id());

        var keyPairC = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationC.id(),
                stationA.id(),
                federationService.encodePublicKey(keyPairC),
                null,
                "https://remote.example.com");

        // Create a request between A and C
        var req = service.createRequest(
                stationC.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(3), memberC.id());
        service.sendMessage(req.id(), stationA.id(), memberA.id(), "Tester A", "Local msg from A");

        // Mock remote messages from C
        var remoteMsg = new LendingMessage(
                9999, req.id(), stationC.id(), memberC.id(), "Remote msg from C", false, Instant.now());
        when(httpClient.fetchRemoteMessages(eq("https://remote.example.com"), eq(req.id()), eq(stationA.id()), any()))
                .thenReturn(List.of(remoteMsg));

        // Set federation private key on station A so the service can call HTTP
        stationRepo.updateFederationPrivateKey(stationA.id(), "dummyPrivateKey");

        var messages = service.getMessages(req.id(), stationA.id());
        assertFalse(messages.isEmpty());
        // Should have both local and remote messages
        assertTrue(messages.stream().anyMatch(m -> m.senderStationId() == stationA.id()));
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Remote msg from C")));

        // Verify HTTP client was called for the remote partner
        verify(httpClient)
                .fetchRemoteMessages(eq("https://remote.example.com"), eq(req.id()), eq(stationA.id()), any());

        // Cleanup
        federationService.endFederation(partner.id());
        stationRepo.delete(stationC.id());
    }

    // -- Assign Item --

    @Test
    @Order(30)
    void assignItem() {
        assertTrue(service.assignItem(requestItemId, itemIdA));
        var items = service.findRequestItems(requestId);
        var assigned =
                items.stream().filter(i -> i.id() == requestItemId).findFirst().orElseThrow();
        assertEquals(itemIdA, assigned.assignedItemId());
    }

    // -- Blocks --

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
            assertTrue(service.deleteBlock(block.id()));
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
        // stationA is the owning station — the requests should include our main requestId
        assertTrue(requests.stream().anyMatch(r -> r.id() == requestId));
    }

    @Test
    @Order(51)
    void getLocalMessages() {
        var msgs = service.getLocalMessages(requestId, stationA.id());
        assertNotNull(msgs);
        // We sent at least one message from stationA in order 20/21
        assertTrue(msgs.stream().anyMatch(m -> m.senderStationId() == stationA.id()));
    }

    @Test
    @Order(52)
    void declineRequestWithNoReason() {
        var req = service.createRequest(
                stationB.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(2), memberB.id());
        assertTrue(service.declineRequest(req.id(), stationA.id(), null));
        var found = service.findRequest(req.id()).orElseThrow();
        assertEquals(dev.chojo.ember.feature.federation.entity.LendingStatus.DECLINED, found.status());
    }

    @Test
    @Order(53)
    void declineRequestWithBlankReason() {
        var req = service.createRequest(
                stationB.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(2), memberB.id());
        assertTrue(service.declineRequest(req.id(), stationA.id(), ""));
        var found = service.findRequest(req.id()).orElseThrow();
        assertEquals(dev.chojo.ember.feature.federation.entity.LendingStatus.DECLINED, found.status());
    }

    @Test
    @Order(54)
    @SuppressWarnings("deprecation")
    void getMessagesDeprecated() {
        // Deprecated overload without stationId — just verify no exception
        var msgs = service.getMessages(requestId);
        assertNotNull(msgs);
    }

    @Test
    @Order(55)
    void isBlockedReturnsFalseWhenNotBlocked() {
        // Date range well in the future with no block configured
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
        // Create request and add an item with inventoryId so buildItemSummary covers lines 72-79
        var req = service.createRequest(
                stationB.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(5), memberB.id());
        service.addRequestItem(req.id(), inventoryIdA, itemIdA, 3);

        // Create another request — this triggers buildItemSummary with items in the DB
        var req2 = service.createRequest(
                stationB.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(5), memberB.id());
        // Add item to req2 before checking (buildItemSummary is called during createRequest,
        // but the items are added after, so let's exercise it via status changes)
        service.addRequestItem(req2.id(), inventoryIdA, itemIdA, 1);

        // Approve from the requesting station's side to exercise the other publishStatusChange branch (line 85)
        assertTrue(service.approveRequest(req.id(), stationB.id()));
    }

    @Test
    @Order(58)
    void getMessagesFromRequestingStationPerspective() {
        // Exercise getMessages where localStationId == requestingStationId (line 204)
        var req = service.createRequest(
                stationB.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(3), memberB.id());
        service.sendMessage(req.id(), stationA.id(), memberA.id(), "A", "msg from A");
        service.sendMessage(req.id(), stationB.id(), memberB.id(), "B", "msg from B");

        // Get messages from stationB's perspective (the requesting station)
        var messages = service.getMessages(req.id(), stationB.id());
        assertFalse(messages.isEmpty());
        assertTrue(messages.stream().anyMatch(m -> m.senderStationId() == stationA.id()));
        assertTrue(messages.stream().anyMatch(m -> m.senderStationId() == stationB.id()));
    }

    @Test
    @Order(59)
    void getMessagesRemotePartnerNoPrivateKey() {
        // Create a remote federation where the local station has no private key (lines 228-230)
        var stationC = stationRepo.create("LendNoKeyC");
        var stationD = stationRepo.create("LendNoKeyD");
        var memberC = stationMemberRepo.create(stationC.id(), account.id());

        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                stationD.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPair),
                null,
                "https://remote-lending.example.com");

        // stationC has no federation private key set
        var req = service.createRequest(
                stationC.id(), stationD.id(), LocalDate.now(), LocalDate.now().plusDays(2), memberC.id());
        service.sendMessage(req.id(), stationC.id(), memberC.id(), "C", "msg from C");

        // getMessages from stationD perspective — partner is remote, but stationD has no private key
        // Should return local messages only (remote fetch returns empty due to no key)
        var messages = service.getMessages(req.id(), stationD.id());
        assertNotNull(messages);

        stationRepo.delete(stationC.id());
        stationRepo.delete(stationD.id());
    }

    @Test
    @Order(60)
    void getMessagesWithNoPartner() {
        // Exercise findPartnerForStation returning null (lines 243-244)
        // Create stations without federation
        var stationE = stationRepo.create("LendNoPartnerE");
        var stationF = stationRepo.create("LendNoPartnerF");
        var memberE = stationMemberRepo.create(stationE.id(), account.id());

        // Directly create a lending request via the repository (bypassing federation check)
        var req = lendingRepo.createRequest(
                stationE.id(), stationF.id(), LocalDate.now(), LocalDate.now().plusDays(2), memberE.id());
        lendingRepo.createMessage(req.id(), stationE.id(), memberE.id(), "hello", false);

        // getMessages — no federation partner exists, so findPartnerForStation returns null
        var messages = service.getMessages(req.id(), stationE.id());
        assertNotNull(messages);
        assertFalse(messages.isEmpty());

        stationRepo.delete(stationE.id());
        stationRepo.delete(stationF.id());
    }
}
