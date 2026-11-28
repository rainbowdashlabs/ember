/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.InventoryShare;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.SharePolicy;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.LendingOfferCatalogService.CatalogShare;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemPresentation;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The offer catalog reads each level's own rule, lists pieces only of an inventory of different
 * things, leaves out what the station may not lend, and reads what one partner sees through the
 * partner's policy.
 */
class LendingOfferCatalogServiceTest {
    private static final int STATION = 3;
    private static final int PARTNER = 9;

    private InventoryRepository inventories;
    private InventoryArtRepository arts;
    private InventoryShareService shares;
    private LendingService lending;
    private LendingOfferCatalogService service;

    private static Inventory inventory(int id, String name, boolean homogeneous) {
        return new Inventory(
                id, STATION, name, InventoryType.INTERNAL, false, homogeneous, false, null, null, null, "");
    }

    private static InventoryArt art(int id, int inventoryId, String name) {
        return new InventoryArt(id, inventoryId, name, "", 0, name, null, null, null, "Text " + name);
    }

    private static InventoryItem piece(int id, int inventoryId, Integer artId, String name) {
        var piece = mock(InventoryItem.class);
        when(piece.id()).thenReturn(id);
        when(piece.inventoryId()).thenReturn(inventoryId);
        when(piece.artId()).thenReturn(artId);
        when(piece.name()).thenReturn(name);
        return piece;
    }

    @BeforeEach
    void setUp() {
        inventories = mock(InventoryRepository.class);
        arts = mock(InventoryArtRepository.class);
        shares = mock(InventoryShareService.class);
        lending = mock(LendingService.class);
        service = new LendingOfferCatalogService(inventories, arts, shares, lending);

        var radio = piece(11, 1, 101, "FuG 1");
        var charger = piece(12, 1, 102, "Lader");
        var borrowed = piece(13, 1, 101, "Leihgerät");
        var catan = piece(21, 2, null, "Catan");
        var chess = piece(22, 2, null, "Schach");
        when(lending.lendableBy(STATION)).thenReturn(item -> item.id() != 13);
        when(inventories.findByStation(STATION))
                .thenReturn(
                        List.of(inventory(1, "Funk", true), inventory(2, "Spiele", false), inventory(3, "Leer", true)));
        when(inventories.findStock(1)).thenReturn(List.of(radio, charger, borrowed));
        when(inventories.findStock(2)).thenReturn(List.of(catan, chess));
        when(inventories.findStock(3)).thenReturn(List.of());
        when(inventories.findItemPresentations(2)).thenReturn(List.of(new ItemPresentation(21, "Rohstoffe", "box")));
        when(arts.findByInventory(1))
                .thenReturn(List.of(art(101, 1, "Funkgerät"), art(102, 1, "Ladegerät"), art(103, 1, "Leer")));
        when(arts.findByInventory(2)).thenReturn(List.of());
        when(shares.findShares(STATION))
                .thenReturn(List.of(
                        new InventoryShare(1, STATION, 1, null, null, ShareScope.ALL_PARTNERS, ShareGrant.GRANT),
                        new InventoryShare(2, STATION, null, 102, null, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD),
                        new InventoryShare(3, STATION, null, null, 22, ShareScope.SPECIFIC, ShareGrant.GRANT)));
        when(shares.findTargetsByStation(STATION)).thenReturn(Map.of(3, List.of(PARTNER)));
        when(shares.policyForPartnership(STATION, PARTNER))
                .thenReturn(new SharePolicy(true, Map.of(1, true), Map.of(102, false), Map.of(22, true)));
    }

    /** Each level shows its own rule, and only lendable pieces and kinds with pieces count. */
    @Test
    void eachLevelShowsItsOwnRule() {
        var catalog = service.catalog(STATION, null);

        assertEquals(
                List.of("Funk", "Spiele"), catalog.stream().map(p -> p.name()).toList());
        var radios = catalog.getFirst();
        assertEquals(2, radios.pieceCount());
        assertEquals(new CatalogShare(ShareGrant.GRANT, ShareScope.ALL_PARTNERS, List.of()), radios.share());
        assertEquals(
                List.of("Funkgerät", "Ladegerät"),
                radios.kinds().stream().map(k -> k.name()).toList());
        assertNull(radios.kinds().getFirst().share());
        assertEquals(ShareGrant.WITHHOLD, radios.kinds().get(1).share().grant());
        assertTrue(radios.pieces().isEmpty());
        assertNull(radios.visible());

        var games = catalog.get(1);
        assertEquals(
                List.of("Catan", "Schach"),
                games.pieces().stream().map(p -> p.name()).toList());
        assertEquals("box", games.pieces().getFirst().pictureKey());
        assertEquals(List.of(PARTNER), games.pieces().get(1).share().partnerIds());
    }

    /** Read through a partner, every level shows whether that partner sees it. */
    @Test
    void aPartnerSeesWhatItsPolicyAllows() {
        var catalog = service.catalog(STATION, PARTNER);

        var radios = catalog.getFirst();
        assertEquals(Boolean.TRUE, radios.visible());
        assertEquals(
                List.of(true, false),
                radios.kinds().stream().map(k -> k.visible()).toList());
        var games = catalog.get(1);
        assertEquals(Boolean.TRUE, games.visible());
        assertEquals(
                List.of(false, true),
                games.pieces().stream().map(p -> p.visible()).toList());
    }

    /**
     * A product counts as offered where some piece's narrowest rule offers it: a kind without a rule
     * follows the product, a withheld kind takes its pieces out, and a piece rule counts in an
     * inventory of copies as well.
     */
    @Test
    void aProductIsOfferedByTheNarrowestRuleOfSomePiece() {
        var catalog = service.catalog(STATION, null);
        assertTrue(catalog.getFirst().offered());
        assertTrue(catalog.get(1).offered());

        when(shares.findShares(STATION))
                .thenReturn(List.of(
                        new InventoryShare(1, STATION, 1, null, null, ShareScope.ALL_PARTNERS, ShareGrant.GRANT),
                        new InventoryShare(2, STATION, null, 101, null, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD),
                        new InventoryShare(4, STATION, null, 102, null, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD)));
        assertEquals(false, service.catalog(STATION, null).getFirst().offered());

        when(inventories.findByStation(STATION)).thenReturn(List.of(inventory(1, "Funk", true)));
        when(shares.findShares(STATION))
                .thenReturn(List.of(
                        new InventoryShare(5, STATION, null, null, 11, ShareScope.ALL_PARTNERS, ShareGrant.GRANT)));
        var copies = service.catalog(STATION, null).getFirst();
        assertTrue(copies.offered());
        assertTrue(copies.pieces().isEmpty());
    }
}
