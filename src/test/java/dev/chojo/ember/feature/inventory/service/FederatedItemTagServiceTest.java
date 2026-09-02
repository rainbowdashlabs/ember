/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.TaggedItemSummary;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static dev.chojo.ember.feature.federation.FederationTestContracts.storeCurrentContractOnRemotePartners;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FederatedItemTagServiceTest extends RepositoryTestBase {

    private static FederatedItemTagService service;
    private static FederationRepository federationRepo;
    private static FederationService federationService;
    private static FederationHttpClient httpClient;

    private static Account account;
    private static Station asking;
    private static Station neighbour;
    private static int neighbourInventoryId;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        httpClient = mock(FederationHttpClient.class);
        service = new FederatedItemTagService(
                inventoryTagRepo,
                inventoryTagService,
                federationService,
                federationRepo,
                new FederationFanout(),
                httpClient,
                stationRepo);

        account = accountRepo.create("fedtag@test.example", "Fed", "Tagger");
        asking = stationRepo.create("FedTagAsking");
        neighbour = stationRepo.create("FedTagNeighbour");
        int askingInventoryId = inventoryRepo
                .create(asking.id(), "Eigenes", InventoryType.INTERNAL, false)
                .id();
        neighbourInventoryId = inventoryRepo
                .create(neighbour.id(), "Nachbarschaft", InventoryType.INTERNAL, false)
                .id();

        var ownTag = inventoryTagRepo.create(asking.id(), "Funk", null);
        var ownItem = inventoryRepo.createItem(askingInventoryId, "FT-001", "Eigenes Funkgerät", null, null);
        inventoryTagRepo.setItemTags(ownItem.id(), asking.id(), List.of(ownTag.id()));
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(asking.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(neighbour.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(asking.id());
        stationRepo.delete(neighbour.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aSearchForNoWordAsksNobody() {
        assertTrue(service.findAcrossPartners(asking.id(), null).isEmpty());
        assertTrue(service.findAcrossPartners(asking.id(), "   ").isEmpty());
        assertTrue(service.serveToPartner(asking.id(), 1, null).isEmpty());
        assertTrue(service.serveToPartner(asking.id(), 1, " ").isEmpty());
    }

    @Test
    void withoutAPartnerOnlyTheStationsOwnThingsComeBack() {
        var found = service.findAcrossPartners(asking.id(), " FUNK ");
        assertEquals(1, found.size());
        assertEquals("Eigenes Funkgerät", found.getFirst().name());
    }

    @Test
    void aPartnerOnThisInstanceContributesThroughTheDatabase() {
        pairLocally();
        var theirTag = inventoryTagRepo.create(neighbour.id(), "funk", null);
        var theirItem = inventoryRepo.createItem(neighbourInventoryId, "FT-100", "Nachbar-Antenne", null, null);
        inventoryTagRepo.setItemTags(theirItem.id(), neighbour.id(), List.of(theirTag.id()));

        assertEquals(
                List.of("Eigenes Funkgerät"),
                service.findAcrossPartners(asking.id(), "Funk").stream()
                        .map(TaggedItemSummary::name)
                        .sorted()
                        .toList(),
                "a partner's gear stays out of the answer until that partner offers it");

        inventoryShareService.setItemShare(
                neighbour.id(), theirItem.id(), ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        var found = service.findAcrossPartners(asking.id(), "Funk");
        assertEquals(
                List.of("Eigenes Funkgerät", "Nachbar-Antenne"),
                found.stream().map(TaggedItemSummary::name).sorted().toList());

        inventoryShareService.removeItemShare(neighbour.id(), theirItem.id());
        inventoryTagRepo.delete(theirTag.id(), neighbour.id());
        inventoryRepo.deleteItem(theirItem.id());
        unpair();
    }

    @Test
    void aPartnerOnAnotherInstanceIsAskedOverTheWire() {
        var keyPair = federationService.generateKeyPair();
        UUID remoteUid = UUID.randomUUID();
        var created = federationRepo.createPartner(
                asking.id(),
                remoteUid,
                "fedtag-invite",
                federationService.encodePublicKey(keyPair),
                "https://partner.example");
        var partner = federationRepo.findPartnerById(created.id()).orElseThrow();
        federationRepo.activatePartner(partner.id(), federationService.encodePublicKey(keyPair));
        federationRepo.upsertCapability(partner.id(), CapabilityType.INVENTORY_LEND, Direction.IMPORT, true);
        storeCurrentContractOnRemotePartners(federationService, federationRepo, asking.id());
        stationRepo.updateFederationPrivateKey(asking.id(), "a-private-key");

        var served = new TaggedItemSummary(
                7, "FS-001", "Ferne Antenne", 0, "Fernlager", null, remoteUid, "FernStation", "Funk", true);
        when(httpClient.getList(
                        anyString(),
                        pathIs("/remote/inventory/tagged/Funk"),
                        any(UUID.class),
                        anyInt(),
                        anyString(),
                        eq(TaggedItemSummary.class)))
                .thenReturn(List.of(served));

        var found = service.findAcrossPartners(asking.id(), "Funk");
        assertEquals(
                List.of("Eigenes Funkgerät", "Ferne Antenne"),
                found.stream().map(TaggedItemSummary::name).sorted().toList());

        stationRepo.updateFederationPrivateKey(asking.id(), null);
        assertEquals(1, service.findAcrossPartners(asking.id(), "Funk").size());

        unpair();
    }

    private static void pairLocally() {
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                asking.id(), neighbour.id(), federationService.encodePublicKey(keyPair), null, null);
        for (var partner : federationService.findPartners(asking.id())) {
            federationRepo.upsertCapability(partner.id(), CapabilityType.INVENTORY_LEND, Direction.IMPORT, true);
        }
        for (var partner : federationService.findPartners(neighbour.id())) {
            federationRepo.upsertCapability(partner.id(), CapabilityType.INVENTORY_LEND, Direction.EXPORT, true);
        }
    }

    private static void unpair() {
        for (var p : federationService.findPartners(asking.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(neighbour.id())) federationRepo.deletePartner(p.id());
    }
}
