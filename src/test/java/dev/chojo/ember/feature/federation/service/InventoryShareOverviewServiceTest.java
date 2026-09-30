/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.InventoryShare;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Each share row of the overview names what it covers, from the item up to the inventory, and the
 * partners it reaches, a partner here under its own name and one elsewhere under the name recorded.
 */
class InventoryShareOverviewServiceTest {
    private static final int STATION = 3;
    private static final UUID HERE = UUID.fromString("00000000-0000-0000-0000-000000000098");
    private static final UUID ELSEWHERE = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private InventoryShareService shares;
    private InventoryRepository inventories;
    private InventoryArtRepository arts;
    private InventoryShareOverviewService service;

    private static FederationPartner partner(int id, UUID uid, String recordedName) {
        return new FederationPartner(
                id,
                STATION,
                uid,
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                recordedName);
    }

    private static InventoryShare share(int id, Integer inventoryId, Integer artId, Integer itemId) {
        return new InventoryShare(id, STATION, inventoryId, artId, itemId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT);
    }

    @BeforeEach
    void setup() {
        shares = mock(InventoryShareService.class);
        var federation = mock(FederationService.class);
        inventories = mock(InventoryRepository.class);
        arts = mock(InventoryArtRepository.class);
        var stations = mock(StationRepository.class);
        service = new InventoryShareOverviewService(shares, federation, inventories, arts, stations);

        var tents = mock(Inventory.class);
        when(tents.id()).thenReturn(2);
        when(tents.name()).thenReturn("Zelte");
        when(inventories.findByStation(STATION)).thenReturn(List.of(tents));
        var local = mock(Station.class);
        when(local.name()).thenReturn("Wache Süd");
        when(stations.findByUid(HERE)).thenReturn(Optional.of(local));
        when(federation.findPartners(STATION))
                .thenReturn(List.of(
                        partner(7, HERE, "alt"), partner(8, ELSEWHERE, "Wache Nord"), partner(9, ELSEWHERE, null)));
        when(shares.findTargets(anyInt())).thenReturn(List.of());
    }

    @Test
    void anInventoryRowNamesTheInventoryAndItsPartners() {
        when(shares.findShares(STATION)).thenReturn(List.of(share(1, 2, null, null)));
        when(shares.findTargets(1)).thenReturn(List.of(7, 8, 9, 10));

        var row = service.overview(STATION).getFirst();

        assertEquals("Zelte", row.inventoryName());
        assertEquals(
                List.of("Wache Süd", "Wache Nord", "?", "?"),
                row.partners().stream()
                        .map(InventoryShareOverviewService.SharePartner::stationName)
                        .toList());
    }

    @Test
    void aKindRowNamesTheKindAndItsInventory() {
        var kind = mock(InventoryArt.class);
        when(kind.name()).thenReturn("Kuppelzelt");
        when(kind.inventoryId()).thenReturn(2);
        when(arts.findById(4)).thenReturn(Optional.of(kind));
        when(shares.findShares(STATION)).thenReturn(List.of(share(1, null, 4, null), share(2, null, 5, null)));

        var rows = service.overview(STATION);

        assertEquals("Kuppelzelt", rows.get(0).artName());
        assertEquals("Zelte", rows.get(0).inventoryName());
        assertNull(rows.get(1).artName());
    }

    @Test
    void anItemRowNamesTheItemItsKindAndItsInventory() {
        var item = mock(InventoryItem.class);
        when(item.name()).thenReturn("Zelt 1");
        when(item.internalId()).thenReturn("Z-1");
        when(item.inventoryId()).thenReturn(2);
        when(item.artId()).thenReturn(4);
        var plain = mock(InventoryItem.class);
        when(plain.name()).thenReturn("Plane");
        var kind = mock(InventoryArt.class);
        when(kind.name()).thenReturn("Kuppelzelt");
        when(inventories.findItemById(6)).thenReturn(Optional.of(item));
        when(inventories.findItemById(7)).thenReturn(Optional.of(plain));
        when(arts.findById(4)).thenReturn(Optional.of(kind));
        when(shares.findShares(STATION))
                .thenReturn(List.of(share(1, null, null, 6), share(2, null, null, 7), share(3, null, null, 8)));

        var rows = service.overview(STATION);

        assertEquals("Zelt 1", rows.get(0).itemName());
        assertEquals("Z-1", rows.get(0).itemInternalId());
        assertEquals("Kuppelzelt", rows.get(0).artName());
        assertEquals("Zelte", rows.get(0).inventoryName());
        assertNull(rows.get(1).artName());
        assertNull(rows.get(2).itemName());
    }
}
