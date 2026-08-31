/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.InventoryShareRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        shareService = new InventoryShareService(shareRepo, federationService, inventoryRepo);
        httpClient = mock(FederationHttpClient.class);
        service = new LendingService(
                lendingRepo,
                httpClient,
                federationService,
                stationRepo,
                inventoryRepo,
                clusterRepo,
                itemCustodyService,
                borrowedGearService,
                shareService,
                new DomainEventBus(Set.of()));

        stationA = stationRepo.create("LendSvcTestStationA");
        stationB = stationRepo.create("LendSvcTestStationB");

        // The body above the stations, running on this instance with a shell station of its own
        clusterHome = stationRepo.create("LendSvcClusterHome");
        clusterId = clusterRepo
                .create("LendSvcKreisverband", null, clusterHome.id())
                .id();

        account = accountRepo.create("lendsvc@test.com", "Lend", "SvcTester");
        memberA = stationMemberRepo.create(stationA.id(), account.id());
        memberB = stationMemberRepo.create(stationB.id(), account.id());

        // Create inventory
        var inv = inventoryRepo.create(stationA.id(), "LendSvcInventory", InventoryType.INTERNAL, false);
        inventoryIdA = inv.id();
        var item = inventoryRepo.createItem(inventoryIdA, "LSVC-001", "Lend Svc Item", null, null);
        itemIdA = item.id();

        // Create federation between A and B (local, remoteHost = null)
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
        assertTrue(messages.stream().anyMatch(m -> stationA.uid().equals(m.senderStationUid())));
        assertTrue(messages.stream().anyMatch(m -> stationB.uid().equals(m.senderStationUid())));

        // Verify HTTP client was never called (local partner)
        verify(httpClient, never())
                .getList(anyString(), any(FederationRequest.class), any(), anyInt(), anyString(), any());
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
                9999, req.id(), stationC.uid(), memberC.id(), "Remote msg from C", false, Instant.now());
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        pathIs("/remote/lending/messages/" + req.id()),
                        any(),
                        eq(stationA.id()),
                        any(),
                        eq(LendingMessage.class)))
                .thenReturn(List.of(remoteMsg));

        // Set federation private key on station A so the service can call HTTP
        stationRepo.updateFederationPrivateKey(stationA.id(), "dummyPrivateKey");

        var messages = service.getMessages(req.id(), stationA.id());
        assertFalse(messages.isEmpty());
        // Should have both local and remote messages
        assertTrue(messages.stream().anyMatch(m -> stationA.uid().equals(m.senderStationUid())));
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Remote msg from C")));

        // Verify HTTP client was called for the remote partner
        verify(httpClient)
                .getList(
                        eq("https://remote.example.com"),
                        pathIs("/remote/lending/messages/" + req.id()),
                        any(),
                        eq(stationA.id()),
                        any(),
                        eq(LendingMessage.class));

        // Cleanup
        federationService.endFederation(partner.id());
        stationRepo.delete(stationC.id());
    }

    // -- Assign Item --

    @Test
    @Order(30)
    void assignItem() {
        assertTrue(service.assignItem(requestItemId, itemIdA, stationA.id()));
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
        // stationA is the owning station - the requests should include our main requestId
        assertTrue(requests.stream().anyMatch(r -> r.id() == requestId));
    }

    @Test
    @Order(51)
    void getLocalMessages() {
        var msgs = service.getLocalMessages(requestId, stationA.id(), stationB.uid());
        assertNotNull(msgs);
        // We sent at least one message from stationA in order 20/21
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

        assertThrows(NotFoundResponse.class, () -> service.getLocalMessages(requestId, stationA.id(), outsider.uid()));

        stationRepo.delete(outsider.id());
    }

    @Test
    @Order(52)
    void declineRequestWithNoReason() {
        var req = service.createRequest(
                stationB.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(2), memberB.id());
        assertTrue(service.declineRequest(req.id(), stationA.id(), null));
        var found = service.findRequest(req.id()).orElseThrow();
        assertEquals(LendingStatus.DECLINED, found.status());
    }

    @Test
    @Order(53)
    void declineRequestWithBlankReason() {
        var req = service.createRequest(
                stationB.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(2), memberB.id());
        assertTrue(service.declineRequest(req.id(), stationA.id(), ""));
        var found = service.findRequest(req.id()).orElseThrow();
        assertEquals(LendingStatus.DECLINED, found.status());
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

        // Create another request - this triggers buildItemSummary with items in the DB
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
        assertTrue(messages.stream().anyMatch(m -> stationA.uid().equals(m.senderStationUid())));
        assertTrue(messages.stream().anyMatch(m -> stationB.uid().equals(m.senderStationUid())));
    }

    @Test
    @Order(59)
    void getMessagesRemotePartnerNoPrivateKey() {
        // Create a remote federation where the local station has no private key (lines 228-230)
        var stationC = stationRepo.create("LendNoKeyC");
        var stationD = stationRepo.create("LendNoKeyD");
        var memberC = stationMemberRepo.create(stationC.id(), account.id());

        // The remote host sits on stationC, so from stationD's side the partner is the remote one
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                stationD.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPair),
                "https://remote-lending.example.com",
                null);

        // stationC has no federation private key set
        var req = service.createRequest(
                stationC.id(), stationD.id(), LocalDate.now(), LocalDate.now().plusDays(2), memberC.id());
        service.sendMessage(req.id(), stationC.id(), memberC.id(), "C", "msg from C");

        // getMessages from stationD perspective - partner is remote, but stationD has no private key
        // Should return local messages only (remote fetch returns empty due to no key)
        var messages = service.getMessages(req.id(), stationD.id());
        assertNotNull(messages);

        stationRepo.delete(stationC.id());
        stationRepo.delete(stationD.id());
    }

    // -- Federation: Available Inventory --

    @Test
    @Order(200)
    void findAvailableInventoryFromPartner() {
        // stationA has inventory with an unassigned item (created in setup).
        // stationB has an active partnership with stationA.
        // Querying from stationB should see stationA's inventory.
        var results = service.findAvailableInventory(stationB.id(), null, null, null);
        assertFalse(results.entries().isEmpty());
        assertNull(results.emptyReason());
        assertTrue(results.entries().stream()
                .anyMatch(e -> e.inventoryId() == inventoryIdA && e.stationId() == stationA.id()));
    }

    @Test
    @Order(201)
    void findAvailableInventoryWithQuery() {
        // Query matching the inventory name
        var results = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                .entries();
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(e -> e.inventoryId() == inventoryIdA));

        // Non-matching query
        var empty = service.findAvailableInventory(stationB.id(), "NonExistentXYZ", null, null);
        assertTrue(empty.entries().stream().noneMatch(e -> e.inventoryId() == inventoryIdA));
        assertEquals(LendingService.EmptyReason.NOTHING_FREE, empty.emptyReason());
    }

    @Test
    @Order(202)
    void findAvailableInventoryNoItems() {
        // Create an inventory on stationA with no items
        var emptyInv = inventoryRepo.create(stationA.id(), "EmptyInvForLending", InventoryType.INTERNAL, false);
        var results = service.findAvailableInventory(stationB.id(), "EmptyInvForLending", null, null)
                .entries();
        // Should NOT appear - no unassigned items means availableCount == 0
        assertTrue(results.stream().noneMatch(e -> e.inventoryId() == emptyInv.id()));
        // Cleanup
        inventoryRepo.delete(emptyInv.id());
    }

    @Test
    @Order(203)
    void getMessagesLocalPartner() {
        // Create a fresh lending request between stationB (requesting) and stationA (owning)
        var req = service.createRequest(
                stationB.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(5), memberB.id());

        // Add messages from both sides using the repo directly
        lendingRepo.createMessage(req.id(), stationA.uid(), memberA.id(), "Msg from A side", false);
        lendingRepo.createMessage(req.id(), stationB.uid(), memberB.id(), "Msg from B side", false);

        // getMessages from stationA's perspective should return both local and partner messages
        var messages = service.getMessages(req.id(), stationA.id());
        assertFalse(messages.isEmpty());
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Msg from A side")));
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Msg from B side")));
        // Messages should be sorted by createdAt
        for (int i = 1; i < messages.size(); i++) {
            assertFalse(
                    messages.get(i).createdAt().isBefore(messages.get(i - 1).createdAt()),
                    "Messages should be sorted by createdAt");
        }
    }

    @Test
    @Order(204)
    void availableInventoryEntryRecord() {
        var entry = new LendingService.AvailableInventoryEntry(42, "Test Inv", 7, "Station X", 5, null);
        assertEquals(42, entry.inventoryId());
        assertEquals("Test Inv", entry.inventoryName());
        assertEquals(7, entry.stationId());
        assertEquals("Station X", entry.stationName());
        assertEquals(5, entry.availableCount());
        assertNull(entry.distanceKm());
    }

    @Test
    @Order(204)
    void findAvailableInventoryDistanceEnrichment() {
        // Local station (B) has no coords → distance enrichment is a no-op.
        var before = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                .entries();
        assertTrue(before.stream().anyMatch(e -> e.inventoryId() == inventoryIdA));
        assertTrue(before.stream().allMatch(e -> e.distanceKm() == null));

        // Give both stations coordinates: Munich and Berlin.
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

        // Drop A's coordinates again - the partner-side null branch must still produce a
        // result with null distance.
        stationRepo.updateLocation(stationA.id(), null, null, null, null, null, null);
        var partial = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                .entries();
        assertTrue(partial.stream().filter(e -> e.inventoryId() == inventoryIdA).allMatch(e -> e.distanceKm() == null));

        // Cleanup so other tests see fresh state.
        stationRepo.updateLocation(stationB.id(), null, null, null, null, null, null);
    }

    @Test
    @Order(205)
    void getMessagesRemotePartnerSortsCorrectly() {
        // Create a remote federation
        var stationR = stationRepo.create("LendRemoteSortR");
        var memberR = stationMemberRepo.create(stationR.id(), account.id());

        var keyPairR = federationService.generateKeyPair();
        var partnerR = federationService.acceptInvite(
                stationR.id(),
                stationA.id(),
                federationService.encodePublicKey(keyPairR),
                null,
                "https://remote-sort.example.com");

        // stationR sees stationA as remote
        // Create request: stationR requesting from stationA
        var req = service.createRequest(
                stationR.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(3), memberR.id());
        service.sendMessage(req.id(), stationA.id(), memberA.id(), "A", "Local msg 1");

        // Mock remote messages with specific timestamps
        var now = Instant.now();
        var earlyMsg = new LendingMessage(
                8001, req.id(), stationR.uid(), memberR.id(), "Remote early", false, now.minusSeconds(60));
        var lateMsg = new LendingMessage(
                8002, req.id(), stationR.uid(), memberR.id(), "Remote late", false, now.plusSeconds(60));
        when(httpClient.getList(
                        eq("https://remote-sort.example.com"),
                        pathIs("/remote/lending/messages/" + req.id()),
                        any(),
                        eq(stationA.id()),
                        any(),
                        eq(LendingMessage.class)))
                .thenReturn(List.of(lateMsg, earlyMsg));

        // Set federation private key so HTTP call proceeds
        stationRepo.updateFederationPrivateKey(stationA.id(), "dummyKey");

        var messages = service.getMessages(req.id(), stationA.id());
        assertFalse(messages.isEmpty());
        // Verify sorted by createdAt
        for (int i = 1; i < messages.size(); i++) {
            assertFalse(
                    messages.get(i).createdAt().isBefore(messages.get(i - 1).createdAt()),
                    "Messages should be sorted by createdAt");
        }
        // Verify both local and remote messages present
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Local msg 1")));
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Remote early")));
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("Remote late")));

        // Cleanup
        federationService.endFederation(partnerR.id());
        stationRepo.delete(stationR.id());
    }

    @Test
    @Order(206)
    void findAvailableInventoryWithDateRange() {
        // Exercise the date-range path in findAvailableForPartner (lines 329-332)
        var results = service.findAvailableInventory(
                        stationB.id(), null, LocalDate.now(), LocalDate.now().plusDays(7))
                .entries();
        // Should still return inventory from stationA (no blocks exist)
        assertTrue(results.stream().anyMatch(e -> e.inventoryId() == inventoryIdA));
    }

    @Test
    @Order(207)
    void findAvailableInventoryBlockedStation() {
        // Create a station-level block on stationA
        var block = service.createBlock(
                stationA.id(), null, null, LocalDate.now(), LocalDate.now().plusDays(7), "Test");
        var results = service.findAvailableInventory(
                        stationB.id(), null, LocalDate.now(), LocalDate.now().plusDays(7))
                .entries();
        // stationA should be blocked entirely - its inventory should not appear
        assertTrue(results.stream().noneMatch(e -> e.stationId() == stationA.id()));
        service.deleteBlock(block.id(), stationA.id());
    }

    @Test
    @Order(208)
    void findAvailableInventoryBlockedInventory() {
        // Block specific inventory
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
        // Create a remote partnership where the local station has no private key
        var stationNoPk = stationRepo.create("LendNoPK");
        var memberNoPk = stationMemberRepo.create(stationNoPk.id(), account.id());
        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationNoPk.id(),
                stationA.id(),
                federationService.encodePublicKey(keyPair),
                null,
                "https://remote-nopk.example.com");

        // Clear the private key
        stationRepo.updateFederationPrivateKey(stationNoPk.id(), null);

        // Create a request where stationNoPk needs to fetch remote messages
        var req = lendingRepo.createRequest(
                stationNoPk.uid(),
                stationA.uid(),
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                memberNoPk.id());
        lendingRepo.createMessage(req.id(), stationNoPk.uid(), memberNoPk.id(), "local only", false);

        // getMessages should still work - remote messages skipped due to no private key
        var messages = service.getMessages(req.id(), stationNoPk.id());
        assertNotNull(messages);
        assertTrue(messages.stream().anyMatch(m -> m.message().equals("local only")));

        federationService.endFederation(partner.id());
        stationRepo.delete(stationNoPk.id());
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
                stationE.uid(), stationF.uid(), LocalDate.now(), LocalDate.now().plusDays(2), memberE.id());
        lendingRepo.createMessage(req.id(), stationE.uid(), memberE.id(), "hello", false);

        // getMessages - no federation partner exists, so findPartnerForStation returns null
        var messages = service.getMessages(req.id(), stationE.id());
        assertNotNull(messages);
        assertFalse(messages.isEmpty());

        stationRepo.delete(stationE.id());
        stationRepo.delete(stationF.id());
    }

    // -- What is this station's to lend --

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

        assertThrows(ForbiddenResponse.class, () -> service.assignItem(line, held.id(), stationA.id()));
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

        assertThrows(ForbiddenResponse.class, () -> service.assignItem(line, foreign.id(), stationA.id()));
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
        var line = lendingRepo.addRequestItem(request, inv.id(), null, 1).id();

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
        var line = lendingRepo.addRequestItem(request, inv.id(), held.id(), 1).id();

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
        var line = lendingRepo.addRequestItem(request, inv.id(), null, 1).id();

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
        var line = lendingRepo.addRequestItem(request, inv.id(), null, 1).id();

        assertTrue(service.approveRequest(request, stationA.id()));
        assertEquals(own.id(), assignedItemOf(request, line));
    }

    // -- What a partner is shown --

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

        // Another station's inventory offers nothing at all
        assertTrue(service.findAssignableItems(stationB.id(), inv.id()).isEmpty());
    }

    // -- The switch that already existed --

    @Test
    @Order(320)
    void lendingSwitchedOffHidesThePartnerAndRefusesTheRequest() {
        federationService.setCapability(partnerIdBtoA, CapabilityType.INVENTORY_LEND, Direction.IMPORT, false);
        try {
            var results = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                    .entries();
            assertTrue(results.stream().noneMatch(e -> e.stationId() == stationA.id()));
            assertThrows(
                    ForbiddenResponse.class,
                    () -> service.createRequest(
                            stationB.id(),
                            stationA.id(),
                            LocalDate.now(),
                            LocalDate.now().plusDays(1),
                            memberB.id()));
        } finally {
            federationService.setCapability(partnerIdBtoA, CapabilityType.INVENTORY_LEND, Direction.IMPORT, true);
        }
        var restored = service.findAvailableInventory(stationB.id(), "LendSvc", null, null)
                .entries();
        assertTrue(restored.stream().anyMatch(e -> e.stationId() == stationA.id()));
    }

    @Test
    @Order(321)
    void createRequestRefusesAStationThatIsNoPartner() {
        var stranger = stationRepo.create("LendSvcStranger");

        assertThrows(
                ForbiddenResponse.class,
                () -> service.createRequest(
                        stationB.id(),
                        stranger.id(),
                        LocalDate.now(),
                        LocalDate.now().plusDays(1),
                        memberB.id()));

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
                        memberB.id())
                .id();
    }

    /** One line of such a request, for tests that only need something to assign against. */
    private static int lineOn(Station owner, Integer inventoryId, Integer itemId) {
        return lendingRepo
                .addRequestItem(requestOn(owner), inventoryId, itemId, 1)
                .id();
    }

    private static Integer assignedItemOf(int requestId, int requestItemId) {
        return service.findRequestItems(requestId).stream()
                .filter(i -> i.id() == requestItemId)
                .findFirst()
                .orElseThrow()
                .assignedItemId();
    }

    // -- The opt-in share --

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
                stationB.id(), stationA.id(), LocalDate.now(), LocalDate.now().plusDays(3), memberB.id());
        service.addRequestItem(request.id(), inventory.id(), null, 1);
        assertTrue(service.approveRequest(request.id(), stationA.id()));
        assertTrue(service.findRequestItems(request.id()).stream().anyMatch(i -> i.assignedItemId() != null));

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
}
