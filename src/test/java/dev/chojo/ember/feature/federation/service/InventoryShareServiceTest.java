/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.SharePolicy;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.InventoryShareRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryShareServiceTest extends RepositoryTestBase {

    private static InventoryShareService service;
    private static InventoryShareRepository shareRepo;
    private static FederationRepository federationRepo;
    private static FederationService federationService;

    private static Station owner;
    private static Station partnerStation;
    private static Station strangerStation;
    private static int inventoryId;
    private static int firstItemId;
    private static int secondItemId;
    private static int foreignInventoryId;
    private static int foreignItemId;
    private static int partnerId;

    @BeforeAll
    static void setup() {
        shareRepo = new InventoryShareRepository();
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        service = new InventoryShareService(shareRepo, federationService, inventoryRepo);

        owner = stationRepo.create("ShareSvcOwner");
        partnerStation = stationRepo.create("ShareSvcPartner");
        strangerStation = stationRepo.create("ShareSvcStranger");

        inventoryId = inventoryRepo
                .create(owner.id(), "ShareSvcInventory", InventoryType.INTERNAL, false)
                .id();
        firstItemId = inventoryRepo
                .createItem(inventoryId, "SSV-001", "Share Svc One", null, null)
                .id();
        secondItemId = inventoryRepo
                .createItem(inventoryId, "SSV-002", "Share Svc Two", null, null)
                .id();

        foreignInventoryId = inventoryRepo
                .create(strangerStation.id(), "ShareSvcForeign", InventoryType.INTERNAL, false)
                .id();
        foreignItemId = inventoryRepo
                .createItem(foreignInventoryId, "SSV-F01", "Foreign Item", null, null)
                .id();

        var keyPair = federationService.generateKeyPair();
        partnerId = federationService
                .acceptInvite(partnerStation.id(), owner.id(), federationService.encodePublicKey(keyPair), null, null)
                .id();
    }

    @AfterEach
    void clearShares() {
        service.removeInventoryShare(owner.id(), inventoryId);
        service.removeItemShare(owner.id(), firstItemId);
        service.removeItemShare(owner.id(), secondItemId);
        federationService.setCapability(partnerId, CapabilityType.INVENTORY_LEND, Direction.EXPORT, true);
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(owner.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(partnerStation.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(owner.id());
        stationRepo.delete(partnerStation.id());
        stationRepo.delete(strangerStation.id());
    }

    @Test
    void gearNobodyHasSpokenAboutIsNotOffered() {
        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertFalse(policy.offersAnything());
        assertFalse(policy.allows(inventoryId, firstItemId));
        assertFalse(policy.allowsInventory(inventoryId));
    }

    @Test
    void aStationThatIsNoPartnerIsOfferedNothing() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        var policy = service.policyFor(owner.id(), strangerStation.uid());
        assertFalse(policy.lendingEnabled());
        assertFalse(policy.offersAnything());
    }

    @Test
    void sharingAnInventoryReachesEveryItemInIt() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        var policy = service.policyFor(owner.id(), partnerStation.uid());

        assertTrue(policy.offersAnything());
        assertTrue(policy.allowsInventory(inventoryId));
        assertTrue(policy.allows(inventoryId, firstItemId));
        assertTrue(policy.allows(inventoryId, secondItemId));
        assertEquals(
                2,
                service.filterShared(policy, inventoryRepo.findItems(inventoryId))
                        .size());
    }

    @Test
    void anItemRowBeatsTheInventoryRowAboveIt() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        service.setItemShare(owner.id(), firstItemId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertFalse(policy.allows(inventoryId, firstItemId));
        assertTrue(policy.allows(inventoryId, secondItemId));
        assertEquals(
                List.of(secondItemId),
                service.filterShared(policy, inventoryRepo.findItems(inventoryId)).stream()
                        .map(item -> item.id())
                        .toList());
    }

    @Test
    void withholdingOnItsOwnDoesNothing() {
        service.setItemShare(owner.id(), firstItemId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());
        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertFalse(policy.offersAnything());
        assertFalse(policy.allows(inventoryId, firstItemId));
    }

    @Test
    void aRowThatNamesOtherPartnersStillBeatsTheRowAboveIt() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        service.setItemShare(owner.id(), firstItemId, ShareScope.SPECIFIC, ShareGrant.GRANT, List.of());

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertFalse(policy.allows(inventoryId, firstItemId));
        assertTrue(policy.allows(inventoryId, secondItemId));
    }

    @Test
    void aRowThatNamesThisPartnerReachesIt() {
        service.setItemShare(owner.id(), firstItemId, ShareScope.SPECIFIC, ShareGrant.GRANT, List.of(partnerId));

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertTrue(policy.allows(inventoryId, firstItemId));
        assertTrue(policy.offersAnything());
        assertEquals(
                List.of(partnerId),
                service.findTargets(service.findShares(owner.id()).getFirst().id()));
    }

    @Test
    void switchingToEverybodyDropsTheNamedPartners() {
        service.setItemShare(owner.id(), firstItemId, ShareScope.SPECIFIC, ShareGrant.GRANT, List.of(partnerId));
        service.setItemShare(owner.id(), firstItemId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of(partnerId));

        int shareId = service.findForItem(owner.id(), firstItemId).orElseThrow().id();
        assertTrue(service.findTargets(shareId).isEmpty());
        assertTrue(service.policyFor(owner.id(), partnerStation.uid()).allows(inventoryId, firstItemId));
    }

    @Test
    void turningLendingOffForAPartnerTakesTheWholeOfferAway() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        federationService.setCapability(partnerId, CapabilityType.INVENTORY_LEND, Direction.EXPORT, false);

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertFalse(policy.lendingEnabled());
        assertFalse(policy.offersAnything());
        assertFalse(policy.allows(inventoryId, firstItemId));
    }

    @Test
    void aStationMayOnlyShareItsOwnGear() {
        assertThrows(
                NotFoundResponse.class,
                () -> service.setInventoryShare(
                        owner.id(), foreignInventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of()));
        assertThrows(
                NotFoundResponse.class,
                () -> service.setItemShare(
                        owner.id(), foreignItemId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of()));
        assertThrows(
                NotFoundResponse.class,
                () -> service.setItemShare(
                        owner.id(), Integer.MAX_VALUE, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of()));
    }

    @Test
    void removingAShareThatWasNeverThereChangesNothing() {
        assertFalse(service.removeInventoryShare(owner.id(), inventoryId));
        assertFalse(service.removeItemShare(owner.id(), firstItemId));
        assertTrue(service.findShares(owner.id()).isEmpty());
        assertTrue(service.findForInventory(owner.id(), inventoryId).isEmpty());
    }

    @Test
    void aClosedPolicyOffersNothingAtAll() {
        var closed = SharePolicy.closed();
        assertFalse(closed.lendingEnabled());
        assertFalse(closed.offersAnything());
        assertFalse(closed.allows(1, 1));
        assertFalse(closed.allowsInventory(1));
    }

    @Test
    void aPolicyWithoutAGrantOffersNothing() {
        var withheldOnly = new SharePolicy(true, Map.of(inventoryId, false), Map.of(firstItemId, false));
        assertFalse(withheldOnly.offersAnything());
        assertFalse(withheldOnly.allowsInventory(inventoryId));
    }

    @Test
    void anUnknownStationIsNoPartnerEither() {
        assertFalse(service.policyFor(owner.id(), UUID.randomUUID()).lendingEnabled());
    }
}
