/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A station's cart as the database keeps it: counts add up, a piece stays one, sent lines go. */
class LendingCartRepositoryTest extends RepositoryTestBase {
    private static final UUID NORTH = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SOUTH = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    private static LendingCartRepository carts;
    private static Station station;

    @BeforeAll
    static void setup() {
        carts = new LendingCartRepository();
        station = stationRepo.create("CartRepoStation");
    }

    @BeforeEach
    void emptyCart() {
        carts.clear(station.id());
    }

    /** The same count put in twice is one line with both quantities. */
    @Test
    void theSameCountAddsUp() {
        carts.addLine(station.id(), NORTH, 7, 3, null, 2, "Funk", "FuG 8b", null);
        carts.addLine(station.id(), NORTH, 7, 3, null, 1, "Funk", "FuG 8b", "key");

        var lines = carts.lines(station.id());
        assertEquals(1, lines.size());
        assertEquals(3, lines.getFirst().quantity());
        assertEquals("key", lines.getFirst().pictureKey());
    }

    /** Counts add up to at most 999. */
    @Test
    void countsStopAtTheMostALineAsksFor() {
        carts.addLine(station.id(), NORTH, 7, 3, null, 998, "Funk", "FuG 8b", null);
        carts.addLine(station.id(), NORTH, 7, 3, null, 5, "Funk", "FuG 8b", null);

        assertEquals(999, carts.lines(station.id()).getFirst().quantity());
    }

    /** A count of the whole inventory is its own line beside a kind's. */
    @Test
    void aWholeInventoryIsALineOfItsOwn() {
        carts.addLine(station.id(), NORTH, 7, null, null, 1, "Funk", "", null);
        carts.addLine(station.id(), NORTH, 7, null, null, 1, "Funk", "", null);
        carts.addLine(station.id(), NORTH, 7, 3, null, 1, "Funk", "FuG 8b", null);

        assertEquals(2, carts.countLines(station.id()));
    }

    /** A piece put in twice stays one piece, and its count cannot be changed. */
    @Test
    void aPieceStaysOne() {
        carts.addLine(station.id(), NORTH, 9, null, 14, 1, "Spiele", "Catan", null);
        carts.addLine(station.id(), NORTH, 9, null, 14, 1, "Spiele", "Catan", null);

        var line = carts.lines(station.id()).getFirst();
        assertEquals(1, line.quantity());
        assertFalse(carts.setQuantity(station.id(), line.id(), 3));
    }

    /** The days and the note stay with the cart, and only the sent lines go. */
    @Test
    void detailsStayAndSentLinesGo() {
        carts.setDetails(station.id(), LocalDate.of(2030, 6, 12), null, "Sommerfest", "Abholung Freitag");
        carts.addLine(station.id(), NORTH, 7, 3, null, 2, "Funk", "FuG 8b", null);
        carts.addLine(station.id(), SOUTH, 8, null, null, 1, "Zelt", "", null);

        int north = carts.lines(station.id()).getFirst().id();
        carts.removeLines(station.id(), List.of(north));
        carts.clearIfEmpty(station.id());

        var cart = carts.find(station.id()).orElseThrow();
        assertEquals(LocalDate.of(2030, 6, 12), cart.dateFrom());
        assertEquals("Abholung Freitag", cart.note());
        assertEquals(SOUTH, carts.lines(station.id()).getFirst().lendingStationUid());

        carts.removeLines(
                station.id(), List.of(carts.lines(station.id()).getFirst().id()));
        carts.clearIfEmpty(station.id());
        assertTrue(carts.find(station.id()).isEmpty());
    }

    /** One sending holds the cart until it lets go. */
    @Test
    void oneSendingAtATime() {
        carts.addLine(station.id(), NORTH, 7, 3, null, 2, "Funk", "FuG 8b", null);

        assertTrue(carts.claimSending(station.id()));
        assertFalse(carts.claimSending(station.id()));
        carts.releaseSending(station.id());
        assertTrue(carts.claimSending(station.id()));
    }

    /** A line of another station's cart is neither changed nor removed. */
    @Test
    void anotherCartsLineIsLeftAlone() {
        var other = stationRepo.create("CartRepoOther");
        carts.addLine(other.id(), NORTH, 7, 3, null, 2, "Funk", "FuG 8b", null);
        int lineId = carts.lines(other.id()).getFirst().id();

        assertFalse(carts.setQuantity(station.id(), lineId, 5));
        assertFalse(carts.removeLine(station.id(), lineId));
        assertTrue(carts.removeLine(other.id(), lineId));
    }
}
