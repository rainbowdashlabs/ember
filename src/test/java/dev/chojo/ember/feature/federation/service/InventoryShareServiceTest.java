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
import io.javalin.http.BadRequestResponse;
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
    private static int radioArtId;
    private static int firstRadioId;
    private static int secondRadioId;
    private static int looseItemId;
    private static int foreignInventoryId;
    private static int foreignArtId;
    private static int foreignItemId;
    private static int externalId;
    private static int externalArtId;
    private static int externalItemId;
    private static int mixedId;
    private static int mixedArtId;
    private static int mixedItemId;
    private static int partnerId;

    @BeforeAll
    static void setup() {
        shareRepo = new InventoryShareRepository();
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        service = new InventoryShareService(shareRepo, federationService, inventoryRepo, artRepo);

        owner = stationRepo.create("ShareSvcOwner");
        partnerStation = stationRepo.create("ShareSvcPartner");
        strangerStation = stationRepo.create("ShareSvcStranger");

        inventoryId = inventoryRepo
                .create(owner.id(), "ShareSvcInventory", InventoryType.INTERNAL, false)
                .id();
        radioArtId = artRepo.create(inventoryId, "Funkgerät", "", 0).id();
        firstRadioId = inventoryRepo
                .createItem(inventoryId, "SSV-001", "Funkgerät blau", null, null)
                .id();
        secondRadioId = inventoryRepo
                .createItem(inventoryId, "SSV-002", "Funkgerät grün", null, null)
                .id();
        looseItemId = inventoryRepo
                .createItem(inventoryId, "SSV-003", "Ladeschale", null, null)
                .id();
        artRepo.setArt(radioArtId, List.of(firstRadioId, secondRadioId));

        foreignInventoryId = inventoryRepo
                .create(strangerStation.id(), "ShareSvcForeign", InventoryType.INTERNAL, false)
                .id();
        foreignArtId = artRepo.create(foreignInventoryId, "Fremde Art", "", 0).id();
        foreignItemId = inventoryRepo
                .createItem(foreignInventoryId, "SSV-F01", "Foreign Item", null, null)
                .id();

        externalId = inventoryRepo
                .create(owner.id(), "ShareSvcExternal", InventoryType.EXTERNAL, false)
                .id();
        externalArtId = artRepo.create(externalId, "Fremdes Funkgerät", "", 0).id();
        externalItemId = inventoryRepo
                .createItem(externalId, "SSV-E01", "Kreisgerät", null, null)
                .id();

        mixedId = inventoryRepo
                .create(owner.id(), "ShareSvcMixed", InventoryType.MIXED, false)
                .id();
        mixedArtId = artRepo.create(mixedId, "Gemischte Art", "", 0).id();
        mixedItemId = inventoryRepo
                .createItem(mixedId, "SSV-M01", "Eigenes Gerät", null, null)
                .id();

        var keyPair = federationService.generateKeyPair();
        partnerId = federationService
                .acceptInvite(partnerStation.id(), owner.id(), federationService.encodePublicKey(keyPair), null, null)
                .id();
    }

    @AfterEach
    void clearShares() {
        service.removeInventoryShare(owner.id(), inventoryId);
        service.removeArtShare(owner.id(), radioArtId);
        service.removeItemShare(owner.id(), firstRadioId);
        service.removeItemShare(owner.id(), secondRadioId);
        service.removeItemShare(owner.id(), looseItemId);
        service.removeInventoryShare(owner.id(), mixedId);
        service.removeArtShare(owner.id(), mixedArtId);
        service.removeItemShare(owner.id(), mixedItemId);
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
        assertFalse(policy.allows(inventoryId, radioArtId, firstRadioId));
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
    void sharingAnInventoryReachesEveryPieceInIt() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        var policy = service.policyFor(owner.id(), partnerStation.uid());

        assertTrue(policy.offersAnything());
        assertTrue(policy.allowsInventory(inventoryId));
        assertTrue(policy.allows(inventoryId, radioArtId, firstRadioId));
        assertTrue(policy.allows(inventoryId, null, looseItemId));
        assertEquals(
                3,
                service.filterShared(policy, inventoryRepo.findItems(inventoryId))
                        .size());
    }

    @Test
    void aKindRowBeatsTheInventoryRowAboveIt() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        service.setArtShare(owner.id(), radioArtId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertFalse(policy.allows(inventoryId, radioArtId, firstRadioId));
        assertFalse(policy.allows(inventoryId, radioArtId, secondRadioId));
        assertTrue(policy.allows(inventoryId, null, looseItemId));
        assertEquals(
                List.of(looseItemId),
                service.filterShared(policy, inventoryRepo.findItems(inventoryId)).stream()
                        .map(item -> item.id())
                        .toList());
    }

    @Test
    void aPieceRowBeatsItsKindRow() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        service.setArtShare(owner.id(), radioArtId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());
        service.setItemShare(owner.id(), firstRadioId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertTrue(policy.allows(inventoryId, radioArtId, firstRadioId));
        assertFalse(policy.allows(inventoryId, radioArtId, secondRadioId));
    }

    @Test
    void aKindCanBeOfferedOutOfAnInventoryNobodyOffers() {
        service.setArtShare(owner.id(), radioArtId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertTrue(policy.offersAnything());
        assertTrue(policy.allows(inventoryId, radioArtId, firstRadioId));
        assertFalse(policy.allows(inventoryId, null, looseItemId));
    }

    @Test
    void anItemRowBeatsTheInventoryRowAboveIt() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        service.setItemShare(owner.id(), looseItemId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertFalse(policy.allows(inventoryId, null, looseItemId));
        assertTrue(policy.allows(inventoryId, radioArtId, firstRadioId));
    }

    @Test
    void withholdingOnItsOwnDoesNothing() {
        service.setItemShare(owner.id(), firstRadioId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());
        service.setArtShare(owner.id(), radioArtId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());
        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertFalse(policy.offersAnything());
        assertFalse(policy.allows(inventoryId, radioArtId, firstRadioId));
    }

    @Test
    void aRowThatNamesOtherPartnersStillBeatsTheRowAboveIt() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        service.setArtShare(owner.id(), radioArtId, ShareScope.SPECIFIC, ShareGrant.GRANT, List.of());

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertFalse(policy.allows(inventoryId, radioArtId, firstRadioId));
        assertTrue(policy.allows(inventoryId, null, looseItemId));
    }

    @Test
    void aRowThatNamesThisPartnerReachesIt() {
        service.setItemShare(owner.id(), firstRadioId, ShareScope.SPECIFIC, ShareGrant.GRANT, List.of(partnerId));

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertTrue(policy.allows(inventoryId, radioArtId, firstRadioId));
        assertTrue(policy.offersAnything());
        assertEquals(
                List.of(partnerId),
                service.findTargets(service.findShares(owner.id()).getFirst().id()));
    }

    @Test
    void switchingToEverybodyDropsTheNamedPartners() {
        service.setItemShare(owner.id(), firstRadioId, ShareScope.SPECIFIC, ShareGrant.GRANT, List.of(partnerId));
        service.setItemShare(owner.id(), firstRadioId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of(partnerId));

        int shareId =
                service.findForItem(owner.id(), firstRadioId).orElseThrow().id();
        assertTrue(service.findTargets(shareId).isEmpty());
        assertTrue(service.policyFor(owner.id(), partnerStation.uid()).allows(inventoryId, radioArtId, firstRadioId));
    }

    @Test
    void turningLendingOffForAPartnerTakesTheWholeOfferAway() {
        service.setInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        federationService.setCapability(partnerId, CapabilityType.INVENTORY_LEND, Direction.EXPORT, false);

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertFalse(policy.lendingEnabled());
        assertFalse(policy.offersAnything());
        assertFalse(policy.allows(inventoryId, radioArtId, firstRadioId));
    }

    @Test
    void aStationMayOnlyShareItsOwnGear() {
        assertThrows(
                NotFoundResponse.class,
                () -> service.setInventoryShare(
                        owner.id(), foreignInventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of()));
        assertThrows(
                NotFoundResponse.class,
                () -> service.setArtShare(
                        owner.id(), foreignArtId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of()));
        assertThrows(
                NotFoundResponse.class,
                () -> service.setItemShare(
                        owner.id(), foreignItemId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of()));
        assertThrows(
                NotFoundResponse.class,
                () -> service.setItemShare(
                        owner.id(), Integer.MAX_VALUE, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of()));
        assertThrows(
                NotFoundResponse.class,
                () -> service.setArtShare(
                        owner.id(), Integer.MAX_VALUE, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of()));
    }

    /**
     * The station lends its own gear and nothing else, so where an inventory can hold nothing of its
     * own there is nothing to decide about and the decision is refused rather than written down and
     * quietly ignored later. A mixed inventory holds the station's own pieces beside the body's and
     * therefore stays open, at all three levels.
     */
    @Test
    void anExternalInventoryCannotBeOffered() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.setInventoryShare(
                        owner.id(), externalId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of()));
        assertThrows(
                BadRequestResponse.class,
                () -> service.setArtShare(
                        owner.id(), externalArtId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of()));
        assertThrows(
                BadRequestResponse.class,
                () -> service.setItemShare(
                        owner.id(), externalItemId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of()));
        assertTrue(service.findForInventory(owner.id(), externalId).isEmpty());
    }

    @Test
    void aMixedInventoryCanStillBeOffered() {
        service.setInventoryShare(owner.id(), mixedId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        service.setArtShare(owner.id(), mixedArtId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());
        service.setItemShare(owner.id(), mixedItemId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        var policy = service.policyFor(owner.id(), partnerStation.uid());
        assertTrue(policy.allowsInventory(mixedId));
        assertFalse(policy.allows(mixedId, mixedArtId, Integer.MAX_VALUE));
        assertTrue(policy.allows(mixedId, mixedArtId, mixedItemId));
    }

    @Test
    void removingAShareThatWasNeverThereChangesNothing() {
        assertFalse(service.removeInventoryShare(owner.id(), inventoryId));
        assertFalse(service.removeArtShare(owner.id(), radioArtId));
        assertFalse(service.removeItemShare(owner.id(), firstRadioId));
        assertTrue(service.findShares(owner.id()).isEmpty());
        assertTrue(service.findForInventory(owner.id(), inventoryId).isEmpty());
        assertTrue(service.findForArt(owner.id(), radioArtId).isEmpty());
    }

    @Test
    void aClosedPolicyOffersNothingAtAll() {
        var closed = SharePolicy.closed();
        assertFalse(closed.lendingEnabled());
        assertFalse(closed.offersAnything());
        assertFalse(closed.allows(1, 1, 1));
        assertFalse(closed.allowsInventory(1));
    }

    @Test
    void aPolicyWithoutAGrantOffersNothing() {
        var withheldOnly = new SharePolicy(
                true, Map.of(inventoryId, false), Map.of(radioArtId, false), Map.of(firstRadioId, false));
        assertFalse(withheldOnly.offersAnything());
        assertFalse(withheldOnly.allowsInventory(inventoryId));
    }

    @Test
    void anUnknownStationIsNoPartnerEither() {
        assertFalse(service.policyFor(owner.id(), UUID.randomUUID()).lendingEnabled());
    }
}
