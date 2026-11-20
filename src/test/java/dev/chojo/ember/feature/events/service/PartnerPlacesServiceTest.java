/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.PartnerPlaces;
import dev.chojo.ember.feature.events.entity.PlacesKind;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a host hands its partners on a shared appointment: who may be given places, who decides, and how
 * the arrangements go with the share.
 */
class PartnerPlacesServiceTest extends EventFederationTestBase {
    private int appointment;
    private int serving;
    private int otherServing;

    /** A fresh appointment of station A, and station A's own records of stations B and C. */
    @BeforeEach
    void anAppointment() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        appointment = eventRepo
                .create(
                        stationA.id(),
                        "Plätze",
                        "desc",
                        StationEvent.EventType.ONE_TIME,
                        null,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        null,
                        true,
                        RegistrationDeadline.none(),
                        false,
                        null,
                        null,
                        null,
                        null)
                .id();
        serving = partnerOf(stationA, stationB).id();
        otherServing = partnerOf(stationA, stationC).id();
    }

    @AfterEach
    void removeTheAppointment() {
        eventRepo.delete(appointment);
    }

    /**
     * The three arrangements, and the default being the one the product always had.
     *
     * <p>Nobody has to configure anything for the host to decide: that is what holds where nothing is
     * said, and it is what every shared appointment did before any of this existed.
     */
    @Test
    void aPartnerDecidesOnlyWhereTheHostSaidSo() {
        shareService.setShare(appointment, ShareScope.ALL_PARTNERS, List.of());
        assertTrue(
                arrangement(serving).decidedByHost(),
                "saying nothing leaves the decision with the station holding the appointment");
        assertNull(arrangement(serving).slotBudget(), "and leaves it uncapped, which is what no arrangement means");

        arrange(serving, null, true);
        assertTrue(arrangement(serving).partnerConfirms(), "the partner may be given the pen");
        assertNull(arrangement(serving).slotBudget(), "with no cap on how many come");

        arrange(serving, 2, false);
        assertEquals(2, arrangement(serving).slotBudget(), "or with one, which hands over the decision too");
        assertTrue(arrangement(serving).partnerConfirms());

        assertEquals(
                1,
                placesService.arrangements(PlacesKind.APPOINTMENT, appointment).size(),
                "the screen that arranges this reads every partner's arrangement for the appointment");

        arrange(serving, null, false);
        assertTrue(
                arrangement(serving).decidedByHost(),
                "and the host can take it back, which leaves no arrangement rather than a false one");
        assertTrue(
                placesService.arrangements(PlacesKind.APPOINTMENT, appointment).isEmpty(),
                "taking it back leaves no row, so nothing reads as an arrangement that is not one");
    }

    /** A number of places below none is refused before anything is written. */
    @Test
    void lessThanNoPlacesIsRefused() {
        shareService.setShare(appointment, ShareScope.ALL_PARTNERS, List.of());
        assertRefused(EventRefusal.PLACES_CANNOT_BE_NEGATIVE, () -> arrange(serving, -1, true));
        assertTrue(arrangement(serving).decidedByHost());
    }

    /**
     * Places are only handed to a partner the appointment is shared with: not to one an unshared
     * appointment, not to one a share naming others leaves out, and not to another station's record, not
     * even under a share with every partner.
     * Taking an arrangement back is always possible.
     */
    @Test
    void placesAreOnlyGivenToAPartnerTheAppointmentIsSharedWith() {
        assertRefused(EventRefusal.PLACES_PARTNER_NOT_SHARED_WITH, () -> arrange(serving, 2, true));

        shareService.setShare(appointment, ShareScope.SPECIFIC, List.of(otherServing));
        assertRefused(EventRefusal.PLACES_PARTNER_NOT_SHARED_WITH, () -> arrange(serving, 2, true));
        assertRefused(
                EventRefusal.PLACES_PARTNER_NOT_SHARED_WITH,
                () -> placesService.arrange(PlacesKind.APPOINTMENT, stationA.id(), appointment, partnerId, 2, true));

        assertDoesNotThrow(() -> arrange(otherServing, 2, true));
        assertDoesNotThrow(() -> arrange(serving, null, false));
        assertTrue(arrangement(serving).decidedByHost());

        shareService.setShare(appointment, ShareScope.ALL_PARTNERS, List.of());
        assertRefused(
                EventRefusal.PLACES_PARTNER_NOT_SHARED_WITH,
                () -> placesService.arrange(PlacesKind.APPOINTMENT, stationA.id(), appointment, partnerId, 2, true));
        assertDoesNotThrow(() -> arrange(serving, 2, true));
    }

    /**
     * What each partner was given goes with the share: a share with every partner keeps every
     * arrangement, one naming partners keeps only theirs, and stopping the share drops them all.
     */
    @Test
    void theArrangementsGoWithTheShare() {
        shareService.setShare(appointment, ShareScope.ALL_PARTNERS, List.of());
        arrange(serving, 2, true);
        arrange(otherServing, 3, true);

        shareService.setShare(appointment, ShareScope.ALL_PARTNERS, List.of());
        assertEquals(
                2,
                placesService.arrangements(PlacesKind.APPOINTMENT, appointment).size());

        shareService.setShare(appointment, ShareScope.SPECIFIC, List.of(otherServing));
        assertTrue(arrangement(serving).decidedByHost(), "a partner the share no longer names loses its places");
        assertEquals(3, arrangement(otherServing).slotBudget(), "the one it names keeps them");

        shareService.removeShare(appointment);
        assertTrue(
                placesService.arrangements(PlacesKind.APPOINTMENT, appointment).isEmpty());
    }

    /** The host decides only where it kept the decision, and a partner confirms only where it was given it. */
    @Test
    void whoDecidesIsAskedOfTheArrangement() {
        shareService.setShare(appointment, ShareScope.ALL_PARTNERS, List.of());
        assertDoesNotThrow(() -> placesService.requireHostDecides(PlacesKind.APPOINTMENT, appointment, serving));
        assertRefused(
                EventRefusal.PARTNER_DOES_NOT_CONFIRM_ITS_OWN,
                () -> placesService.requirePartnerDecides(PlacesKind.APPOINTMENT, appointment, serving));

        arrange(serving, 1, true);
        assertRefused(
                EventRefusal.PARTNER_DECIDES_ITS_OWN,
                () -> placesService.requireHostDecides(PlacesKind.APPOINTMENT, appointment, serving));
        assertEquals(
                1,
                placesService
                        .requirePartnerDecides(PlacesKind.APPOINTMENT, appointment, serving)
                        .slotBudget());
        assertFalse(arrangement(serving).decidedByHost());
    }

    private void arrange(int partner, @Nullable Integer slotBudget, boolean partnerConfirms) {
        placesService.arrange(PlacesKind.APPOINTMENT, stationA.id(), appointment, partner, slotBudget, partnerConfirms);
    }

    private PartnerPlaces arrangement(int partner) {
        return placesService.arrangement(PlacesKind.APPOINTMENT, appointment, partner);
    }
}
