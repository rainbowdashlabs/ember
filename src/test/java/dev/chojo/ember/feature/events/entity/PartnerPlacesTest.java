/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules of an arrangement with a partner, apart from where it is kept. */
class PartnerPlacesTest {

    /** A budget hands over the decision, taking the decision back takes the budget, and less than none is refused. */
    @Test
    void whatIsAskedForIsPutIntoOneShape() {
        assertEquals(new PartnerPlaces(1, 2, 3, true), PartnerPlaces.requested(1, 2, 3, false));
        assertEquals(new PartnerPlaces(1, 2, null, true), PartnerPlaces.requested(1, 2, null, true));
        assertEquals(PartnerPlaces.hostDecides(1, 2), PartnerPlaces.requested(1, 2, null, false));
        assertTrue(PartnerPlaces.requested(1, 2, null, false).decidedByHost());
        assertEquals(new PartnerPlaces(1, 2, 0, true), PartnerPlaces.requested(1, 2, 0, false));
        var refused = assertThrows(RefusalResponse.class, () -> PartnerPlaces.requested(1, 2, -1, true));
        assertEquals(EventRefusal.PLACES_CANNOT_BE_NEGATIVE, refused.refusal());
    }

    /** Without a cap everybody fits; with one, the places taken and those kept for the waiting count against it. */
    @Test
    void aPlaceIsLeftWhileTakenAndWaitingStayBelowTheBudget() {
        assertTrue(new PartnerPlaces(1, 2, null, true).admits(100, 100));
        var two = new PartnerPlaces(1, 2, 2, true);
        assertTrue(two.admits(1, 0));
        assertFalse(two.admits(1, 1));
        assertFalse(two.admits(2, 0));
        assertFalse(new PartnerPlaces(1, 2, 0, true).admits(0, 0));
    }
}
