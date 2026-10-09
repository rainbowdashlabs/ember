/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.FederationHeaders;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.LendingMessageSent;
import dev.chojo.ember.event.events.LendingStatusChanged;
import dev.chojo.ember.feature.equipment.repository.EquipmentAvailabilityRepository;
import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.equipment.service.EquipmentAvailabilityService;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingNotice;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A loan between two stations of one installation, after one of them has moved to another: the
 * station staying behind switches over to the moved station at its new address and keeps a loan
 * between two installations.
 *
 * <p>The move is what the source installation does once the destination reports the station as
 * imported: the copy left here is marked as moved away and the stations here switch over. What the
 * moved station does with its own copy of a request is the destination's business, which the station
 * transfer tests carry over.
 */
class LendingAcrossAStationMoveTest extends RepositoryTestBase {
    private static final String DESTINATION = "https://destination.example";
    private static final List<DomainEvent> heard = new CopyOnWriteArrayList<>();

    private static LendingService lending;
    private static LendingRepository requests;
    private static FederationRepository federationRepo;
    private static FederationService federation;
    private static StationKeyStore keys;
    private static PartnersLeftBehind partners;
    private static MovedStationSwitchover switchover;
    private static FederationHttpClient httpClient;

    private Station stayed;
    private Station moved;

    @BeforeAll
    static void setup() {
        httpClient = mock(FederationHttpClient.class);
        lending = newLendingService(
                new DomainEventBus(Set.of(recorder(LendingMessageSent.class), recorder(LendingStatusChanged.class))),
                new EquipmentAvailabilityService(
                        new EquipmentAvailabilityRepository(),
                        new EquipmentNeedRepository(),
                        eventRepo,
                        occurrenceCalendar),
                httpClient);
        requests = new LendingRepository();
        federationRepo = new FederationRepository();
        keys = TestStationKeys.store();
        federation = TestFederationServices.of(federationRepo, stationRepo);
        partners = new PartnersLeftBehind(federationRepo, stationRepo, keys);
        switchover = new MovedStationSwitchover(
                federationRepo,
                TestStationKeys.partnerFixup(federationRepo, mock(FederationHttpClient.class)),
                keys,
                requests,
                inventoryRepo);
    }

    @BeforeEach
    void pairTwoStations() {
        stayed = stationRepo.create("Move Stayed");
        moved = stationRepo.create("Move Moved");
        federation.acceptInvite(
                stayed.id(), moved.id(), federation.encodePublicKey(federation.generateKeyPair()), null, null);
        when(httpClient.canSign(stayed.id())).thenReturn(true);
        when(httpClient.post(any(), any(), any(), any(), eq(stayed.id()))).thenReturn(true);
        heard.clear();
    }

    @Test
    void theLenderLeftBehindTakesItsGearBackAndTellsTheNewAddress() {
        int pump = gearOf(stayed, "TS-1");
        var loan = lend(stayed, pump, moved);
        assertEquals(moved.id(), custodyPartnerOf(pump));

        moveAway();

        assertNull(custodyPartnerOf(pump), "the partner holding it runs elsewhere now");
        var line = requests.findItemsByRequest(loan.id()).getFirst();
        assertEquals(pump, line.itemId(), "the line still names the gear that stayed");
        assertEquals(List.of(pump), requests.findAssignedItems(line.id()));

        assertTrue(lending.markReturned(loan.id(), stayed.id()));

        assertEquals(ItemCustody.WITH_OWNER, custodyOf(pump), "the gear is home again");
        verify(httpClient)
                .post(
                        eq(DESTINATION),
                        pathIs("/remote/lending/requests/" + loan.uid() + "/status"),
                        any(),
                        eq(moved.uid()),
                        eq(stayed.id()));
        assertTrue(heard.stream()
                .filter(LendingStatusChanged.class::isInstance)
                .map(LendingStatusChanged.class::cast)
                .noneMatch(event -> event.targetStationId() == moved.id()));
    }

    @Test
    void theBorrowerLeftBehindKeepsItsCopyAndHandsGearBackToTheNewAddress() {
        int ladder = gearOf(moved, "SL-1");
        var loan = lend(moved, ladder, stayed);

        moveAway();

        var borrowed = inventoryRepo.findBorrowedItems(stayed.id()).getFirst();
        assertNull(borrowed.ownerStationId(), "the owner runs on another installation now");
        assertEquals(moved.uid(), borrowed.ownerStationUid());
        assertEquals("Move Moved", borrowed.ownerStationName());
        var line = requests.findItemsByRequest(loan.id()).getFirst();
        assertEquals("SL-1", line.label(), "the line keeps what it asked for");
        assertNull(line.itemId(), "the moved station's gear is not here to be named");
        assertEquals(List.of(), requests.findAssignedItems(line.id()));

        assertTrue(lending.markReturned(loan.id(), stayed.id()));

        assertEquals(List.of(), inventoryRepo.findBorrowedItems(stayed.id()), "the borrowed row went home");
        assertEquals(ItemCustody.WITH_PARTNER, custodyOf(ladder), "the moved station's copy is not treated as here");
        verify(httpClient)
                .post(
                        eq(DESTINATION),
                        pathIs("/remote/lending/requests/" + loan.uid() + "/status"),
                        any(),
                        eq(moved.uid()),
                        eq(stayed.id()));
    }

    @Test
    void aStationLeftBehindVerifiesTheMovedStationAtItsNewAddress() throws Exception {
        var leftBehind = partners.of(moved.id());
        assertEquals(
                List.of(new PartnersLeftBehind.LeftBehindPartner(
                        stayed.uid(), "Move Stayed", keys.ensurePublicKey(stayed.id()))),
                leftBehind,
                "the moved station takes along whom it leaves behind and how they sign");

        moveAway();

        var partnership = partnershipWithMoved();
        assertEquals(DESTINATION, partnership.remoteHost());
        assertEquals(FederationContractVersions.current(), partnership.federationContract());
        assertTrue(federation.hasCapability(partnership, CapabilityType.INVENTORY_LEND, Direction.IMPORT));
        var signing = new FederationSigningService();
        String timestamp = Instant.now().toString();
        String signature = signing.sign(
                "POST",
                "/api/v1/remote/lending/requests",
                stayed.uid(),
                "nonce",
                "{}",
                timestamp,
                keys.privateKey(moved.id()).orElseThrow());
        assertTrue(signing.verify(
                "POST",
                "/api/v1/remote/lending/requests",
                stayed.uid(),
                "nonce",
                "{}",
                signature,
                signing.decodePublicKey(partnership.partnerPublicKey()),
                Instant.parse(timestamp)));
        assertEquals(
                0,
                stationRepo.findHereByUid(moved.uid()).map(Station::id).orElse(0),
                "the copy left here is not where the station runs");
    }

    @Test
    void theMovedStationLearnsTheKeysOfThePartnersItLeftBehind() {
        var leftBehind = partners.of(moved.id());
        var arrived = stationRepo.create("Move Arrived");
        var row = federationRepo.createPartner(arrived.id(), stayed.uid(), null, null, "https://source.example");
        federationRepo.activatePartner(row.id(), "a key nobody signs with");

        var page = Map.<String, Object>of(
                PartnersLeftBehind.FIELD,
                leftBehind.stream()
                        .map(partner -> Map.of(
                                "stationUid", partner.stationUid().toString(),
                                "name", partner.name(),
                                "publicKey", partner.publicKey()))
                        .toList());

        assertEquals(1, partners.adopt(arrived.id(), PartnersLeftBehind.read(page)));

        var adopted = federationRepo.findPartnerById(row.id()).orElseThrow();
        assertEquals(keys.ensurePublicKey(stayed.id()), adopted.partnerPublicKey());
        assertEquals("Move Stayed", adopted.partnerStationName());
        assertEquals(FederationContractVersions.current(), adopted.federationContract());
    }

    @Test
    void wordAndStatusFromTheMovedStationArriveOverHttp() {
        int ladder = gearOf(moved, "SL-2");
        var loan = lend(moved, ladder, stayed);
        moveAway();
        var harness = RouteHarness.serving(new RemoteLendingRoutes(new FederationEndpoints(Set.of(lending))));
        var asMoved = harness.asPartner(new FederationSession(partnershipWithMoved(), moved.uid()))
                .andThen(request -> request.header(
                        FederationHeaders.HEADER_SURFACE,
                        FederationContractVersions.current().featureHash(CapabilityType.INVENTORY_LEND)));

        var notice = harness.request(client -> client.post(
                PREFIX + "/remote/lending/requests/" + loan.uid() + "/notices",
                body("{\"senderName\": \"Mara\"}"),
                asMoved));
        var returned = harness.request(client -> client.post(
                PREFIX + "/remote/lending/requests/" + loan.uid() + "/status",
                body("{\"status\": \"RETURNED\", \"reason\": null}"),
                asMoved));

        assertEquals(204, notice.code());
        assertEquals(204, returned.code());
        assertTrue(
                heard.stream()
                        .filter(LendingMessageSent.class::isInstance)
                        .map(LendingMessageSent.class::cast)
                        .anyMatch(event -> event.targetStationId() == stayed.id() && "Mara".equals(event.senderName())),
                "the managers left behind hear of the message");
        assertEquals(
                LendingStatus.RETURNED,
                requests.findRequestById(loan.id()).orElseThrow().status());
        assertEquals(List.of(), inventoryRepo.findBorrowedItems(stayed.id()), "the borrowed row went home");
    }

    @Test
    void deletingTheCopyLeftBehindKeepsWhatThePartnerHolds() {
        int ladder = gearOf(moved, "SL-3");
        var loan = lend(moved, ladder, stayed);
        query("UPDATE federation_lending_request_item SET label = '' WHERE request_id = :id;")
                .single(call().bind("id", loan.id()))
                .update();
        moveAway();

        stationRepo.delete(moved.id());

        assertEquals(
                LendingStatus.LENT,
                requests.findRequestById(loan.id()).orElseThrow().status());
        assertEquals(
                "SL-3",
                requests.findItemsByRequest(loan.id()).getFirst().label(),
                "a line without a label is named before the gear it named goes");
        assertEquals(1, inventoryRepo.findBorrowedItems(stayed.id()).size());
        assertTrue(lending.markReturned(loan.id(), stayed.id()));
        assertEquals(List.of(), inventoryRepo.findBorrowedItems(stayed.id()));
    }

    @Test
    void aNoticeFromAStationRunningHereIsNotPassedOnTwice() {
        int pump = gearOf(stayed, "TS-2");
        var loan = lend(stayed, pump, moved);
        var serving = new ServingPartner(partnershipWithMoved(), moved.uid());

        lending.serveNotice(serving, loan.uid(), new RemoteLendingNotice("Mara"));

        assertTrue(heard.stream().noneMatch(LendingMessageSent.class::isInstance));
    }

    /** Completes the move as the source does once the destination reports it, forgetting what was heard before. */
    private void moveAway() {
        stationRepo.markMovedAway(moved.id(), DESTINATION);
        switchover.switchOver(stationRepo.findById(moved.id()).orElseThrow(), DESTINATION);
        heard.clear();
    }

    private static int gearOf(Station station, String internalId) {
        int inventory = inventoryRepo
                .create(station.id(), "Gerät " + internalId, InventoryType.INTERNAL, false)
                .id();
        return inventoryRepo
                .createItem(inventory, internalId, internalId, null, null)
                .id();
    }

    /** Hands one piece of the lender's over to the borrower, both on this installation. */
    private static LendingRequest lend(Station lender, int item, Station borrower) {
        var request = requests.createRequest(
                UUID.randomUUID(),
                borrower.uid(),
                lender.uid(),
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                null,
                null,
                null,
                "Übung");
        int line =
                requests.addRequestItem(request.id(), null, item, null, 1, null).id();
        requests.assignItem(line, item);
        assertTrue(lending.markLent(request.id(), lender.id()));
        return request;
    }

    private static ItemCustody custodyOf(int item) {
        return inventoryRepo.findItemById(item).orElseThrow().custody();
    }

    private static Integer custodyPartnerOf(int item) {
        return inventoryRepo.findItemById(item).orElseThrow().custodyPartnerStationId();
    }

    private FederationPartner partnershipWithMoved() {
        return federationRepo.findPartners(stayed.id()).stream()
                .filter(partner -> moved.uid().equals(partner.partnerStationId()))
                .findFirst()
                .orElseThrow();
    }

    private static <T extends DomainEvent> DomainEventHandler<T> recorder(Class<T> type) {
        return new DomainEventHandler<>() {
            @Override
            public Class<T> eventType() {
                return type;
            }

            @Override
            public void handle(T event) {
                heard.add(event);
            }
        };
    }
}
