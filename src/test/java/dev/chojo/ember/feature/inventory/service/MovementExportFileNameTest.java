/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a queue export is called, which is whatever it turned out to hold.
 *
 * <p>A fixed word said less than the sheet itself does: a reader with three of them in a folder could
 * not tell which was the exchanges and which was the issues without opening all three.
 */
class MovementExportFileNameTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void oneKindOfMovementNamesItself() {
        String name = nameOf(MovementPurpose.EXCHANGE, MovementPurpose.EXCHANGE);

        assertTrue(name.startsWith("Tausch - "), name);
        assertTrue(name.endsWith(".pdf"), name);
    }

    @Test
    void twoKindsBothGoIntoTheName() {
        String name = nameOf(MovementPurpose.EXCHANGE, MovementPurpose.ISSUE);

        assertTrue(name.startsWith("Ausgabe - Tausch - "), name);
    }

    /** The order is the one they are declared in, so ticking rows in another order changes nothing. */
    @Test
    void theOrderDoesNotFollowThePicking() {
        String picked = nameOf(MovementPurpose.REQUEST, MovementPurpose.RETURN);
        String pickedOtherWay = nameOf(MovementPurpose.RETURN, MovementPurpose.REQUEST);

        assertTrue(picked.startsWith("Rückgabe - Anfrage - "), picked);
        assertTrue(picked.equals(pickedOtherWay), picked + " vs " + pickedOtherWay);
    }

    /** Four purposes in one name is a name nobody reads, so it becomes the word for the whole thing. */
    @Test
    void everythingAtOnceIsJustEverything() {
        String name = nameOf(
                MovementPurpose.ISSUE, MovementPurpose.RETURN, MovementPurpose.EXCHANGE, MovementPurpose.REQUEST);

        assertTrue(name.startsWith("Bewegungen - "), name);
    }

    @Test
    void anEnglishStationGetsEnglishWords() {
        String name = MovementExportService.exportFileName(movements(MovementPurpose.EXCHANGE), BERLIN, "en");

        assertTrue(name.startsWith("Exchange - "), name);
    }

    private static String nameOf(MovementPurpose... purposes) {
        return MovementExportService.exportFileName(movements(purposes), BERLIN, "de");
    }

    private static List<ItemMovement> movements(MovementPurpose... purposes) {
        return java.util.Arrays.stream(purposes)
                .map(MovementExportFileNameTest::movementWith)
                .toList();
    }

    private static ItemMovement movementWith(MovementPurpose purpose) {
        return new ItemMovement(
                1,
                1,
                purpose,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false);
    }
}
