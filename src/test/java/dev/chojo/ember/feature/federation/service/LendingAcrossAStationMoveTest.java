/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.equipment.repository.EquipmentAvailabilityRepository;
import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.equipment.service.EquipmentAvailabilityService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A loan between two stations of one installation, after one of them has moved to another: the
 * station staying behind still ends the loan, and the station that moved hears of it at its new
 * address.
 *
 * <p>The move itself is what the source installation does once the destination reports the station
 * as imported: the partnership with the station that left points at the destination from then on.
 * What the moved station does with what it hears is the destination's own copy of the request, which
 * the station transfer tests carry over.
 */
class LendingAcrossAStationMoveTest extends RepositoryTestBase {
    private static final String DESTINATION = "https://destination.example";

    private static LendingService lending;
    private static LendingRepository requests;
    private static FederationRepository federationRepo;
    private static FederationPartnerTransferFixupService fixup;
    private static FederationHttpClient httpClient;

    private Station stayed;
    private Station moved;

    @BeforeAll
    static void setup() {
        httpClient = mock(FederationHttpClient.class);
        lending = newLendingService(
                new DomainEventBus(Set.of()),
                new EquipmentAvailabilityService(
                        new EquipmentAvailabilityRepository(),
                        new EquipmentNeedRepository(),
                        eventRepo,
                        occurrenceCalendar),
                httpClient);
        requests = new LendingRepository();
        federationRepo = new FederationRepository();
        fixup = new FederationPartnerTransferFixupService(federationRepo, mock(FederationHttpClient.class));
    }

    @BeforeEach
    void pairTwoStations() {
        stayed = stationRepo.create("Move Stayed");
        moved = stationRepo.create("Move Moved");
        var federation = TestFederationServices.of(federationRepo, stationRepo);
        federation.acceptInvite(
                moved.id(), stayed.id(), federation.encodePublicKey(federation.generateKeyPair()), null, null);
        when(httpClient.canSign(stayed.id())).thenReturn(true);
        when(httpClient.post(any(), any(), any(), any(), eq(stayed.id()))).thenReturn(true);
    }

    @Test
    void theLenderLeftBehindTakesItsGearBackAndTellsTheNewAddress() {
        int pump = gearOf(stayed, "TS-1");
        var loan = lend(stayed, pump, moved);
        assertEquals(ItemCustody.WITH_PARTNER, custodyOf(pump));

        fixup.flipSourceSideRetainedPartners(moved.uid(), DESTINATION);
        assertEquals(DESTINATION, partnershipWithMoved().remoteHost(), "the partner is told where it went");

        assertTrue(lending.markReturned(loan.id(), stayed.id()));

        assertEquals(ItemCustody.WITH_OWNER, custodyOf(pump), "the gear is home again");
        verify(httpClient)
                .post(
                        eq(DESTINATION),
                        pathIs("/remote/lending/requests/" + loan.uid() + "/status"),
                        any(),
                        eq(moved.uid()),
                        eq(stayed.id()));
    }

    @Test
    void theBorrowerLeftBehindHandsGearBackAndTellsTheNewAddress() {
        int ladder = gearOf(moved, "SL-1");
        var loan = lend(moved, ladder, stayed);
        assertEquals(1, inventoryRepo.findBorrowedItems(stayed.id()).size());

        fixup.flipSourceSideRetainedPartners(moved.uid(), DESTINATION);
        assertTrue(lending.markReturned(loan.id(), stayed.id()));

        assertEquals(List.of(), inventoryRepo.findBorrowedItems(stayed.id()), "the borrowed row went home");
        verify(httpClient)
                .post(
                        eq(DESTINATION),
                        pathIs("/remote/lending/requests/" + loan.uid() + "/status"),
                        any(),
                        eq(moved.uid()),
                        eq(stayed.id()));
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

    private FederationPartner partnershipWithMoved() {
        return federationRepo.findPartners(stayed.id()).stream()
                .filter(partner -> moved.uid().equals(partner.partnerStationId()))
                .findFirst()
                .orElseThrow();
    }
}
