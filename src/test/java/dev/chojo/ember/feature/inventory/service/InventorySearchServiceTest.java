/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.InventoryMatch;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.repository.InventorySearchRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Searching a station's inventories by name and description, of the inventory itself, of its kinds
 * and of its pieces, in the station's language and by part of a compound word.
 */
class InventorySearchServiceTest extends RepositoryTestBase {
    private static Station station;
    private static Station elsewhere;
    private static int radios;
    private static int ladders;
    private static int games;
    private static int rescue;
    private static int handheldKind;
    private static int catan;
    private static InventorySearchService search;

    @BeforeAll
    static void setUp() {
        station = stationRepo.create("Search Station " + UUID.randomUUID());
        stationRepo.updateLocale(station.id(), "de-DE");
        elsewhere = stationRepo.create("Search Elsewhere " + UUID.randomUUID());
        search = new InventorySearchService(new InventorySearchRepository(), stationRepo);

        radios = inventoryRepo
                .create(station.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        handheldKind = artRepo.create(radios, "Handfunkgerät", "", 0).id();
        artRepo.setDescription(handheldKind, "Digital, mit Lautsprechermikrofon");

        ladders = inventoryRepo
                .create(station.id(), "Leitern", InventoryType.INTERNAL, true)
                .id();
        inventoryRepo.setDescription(ladders, "Steckleitern für Übungen und Einsätze");

        rescue = inventoryRepo
                .create(station.id(), "Rettungsgeräte", InventoryType.INTERNAL, true)
                .id();
        inventoryRepo.setDescription(rescue, "Mit Leitern und Tragen");

        games = inventoryRepo
                .create(station.id(), "Gesellschaftsspiele", InventoryType.INTERNAL, false)
                .id();
        catan = inventoryRepo
                .createItem(games, "SP-14", "Siedler von Catan", null, null)
                .id();

        int foreign = inventoryRepo
                .create(elsewhere.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        artRepo.create(foreign, "Handfunkgerät", "", 0);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
    }

    /** Part of a compound word in a kind's name finds the inventory through that kind. */
    @Test
    void partOfACompoundWordFindsTheKind() {
        var match = search.match(station.id(), "funkger").get(radios);

        assertTrue(match.kinds().stream().anyMatch(hit -> hit.id() == handheldKind));
    }

    /** A word of a kind's description finds the inventory, which itself did not match. */
    @Test
    void aKindsDescriptionFindsTheInventory() {
        var match = search.match(station.id(), "Lautsprecher").get(radios);

        assertFalse(match.itself());
        assertEquals(
                List.of("Handfunkgerät"),
                match.kinds().stream().map(InventoryMatch.Hit::name).toList());
    }

    /**
     * The station's language stems the description, so the singular finds the plural even where the
     * spelling changes ("Einsatz" in "Einsätze"), which no prefix match does.
     */
    @Test
    void theStationsLanguageStemsTheDescription() {
        var match = search.match(station.id(), "Einsatz").get(ladders);

        assertTrue(match.itself());
    }

    /** A piece's name finds its inventory and returns the piece. */
    @Test
    void aPieceFindsItsInventory() {
        var match = search.match(station.id(), "Catan").get(games);

        assertEquals(
                List.of(catan),
                match.pieces().stream().map(InventoryMatch.Hit::id).toList());
    }

    /** A match on the inventory's own name ranks above one on a description alone. */
    @Test
    void theNameRanksAboveTheDescription() {
        var matches = search.match(station.id(), "Leitern");

        assertEquals(List.of(ladders, rescue), List.copyOf(matches.keySet()));
        assertTrue(matches.get(ladders).rank() > matches.get(rescue).rank());
    }

    /** Every word has to be found, each either as part of the name or in the station's language. */
    @Test
    void everyWordIsFoundInTheSameRow() {
        var match = search.match(station.id(), "funkger Lautsprecher").get(radios);

        assertEquals(
                List.of(handheldKind),
                match.kinds().stream().map(InventoryMatch.Hit::id).toList());
        assertFalse(search.match(station.id(), "funkger Zebrafant").containsKey(radios));
    }

    /**
     * A common word the language leaves out does not stop the others from finding the description,
     * and a search of nothing but such a word does not find every description.
     */
    @Test
    void aCommonWordDoesNotStopTheSearch() {
        assertTrue(search.match(station.id(), "Leitern und Tragen").containsKey(rescue));
        assertFalse(search.match(station.id(), "und").containsKey(rescue));
    }

    /** A search is cut to a length and a number of words nobody types, so a partner cannot make it heavy. */
    @Test
    void aSearchIsCutToASaneSize() {
        assertEquals(
                InventorySearchService.MAX_WORDS,
                InventorySearchService.wordsOf("a b c d e f g h i j k").size());
        assertEquals(
                InventorySearchService.MAX_LENGTH,
                InventorySearchService.wordsOf("x".repeat(500)).getFirst().length());
        assertTrue(InventorySearchService.wordsOf(null).isEmpty());
    }

    /** Another station's inventories are never found, and a blank search finds nothing. */
    @Test
    void onlyTheStationsOwnInventoriesAreFound() {
        assertEquals(
                List.of(radios), List.copyOf(search.match(station.id(), "Funk").keySet()));
        assertTrue(search.match(station.id(), "  ").isEmpty());
        assertFalse(search.match(elsewhere.id(), "Leitern").containsKey(ladders));
    }
}
