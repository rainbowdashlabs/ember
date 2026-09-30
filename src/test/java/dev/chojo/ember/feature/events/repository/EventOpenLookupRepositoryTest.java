/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.content.BlockReferenceTestBase;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an event block may name and show: for the public, appointments of the station on its public
 * calendar and kept to nobody in particular; for the station's members every appointment kept to
 * nobody in particular. One kept to a group, a user type or a tag, and one of another station, are
 * found by neither the lookup nor the picker.
 */
class EventOpenLookupRepositoryTest extends BlockReferenceTestBase {

    private static boolean offered(BlockAudience audience, String name) {
        return eventRepo.searchForPicker(station.id(), audience, "übung", EventRepository.PickerMode.ALL, 20).stream()
                .anyMatch(found -> name.equals(found.name()));
    }

    @Test
    void thePublicFindsOnlyTheAppointmentOnThePublicCalendar() {
        assertEquals(
                publicEvent.id(),
                eventRepo
                        .findOpenByUid(station.id(), BlockAudience.PUBLIC, uidOf(publicEvent))
                        .orElseThrow()
                        .id());
        assertTrue(eventRepo
                .findOpenByUid(station.id(), BlockAudience.PUBLIC, uidOf(internalEvent))
                .isEmpty());
    }

    @Test
    void membersFindTheInternalAppointmentToo() {
        assertTrue(eventRepo
                .findOpenByUid(station.id(), BlockAudience.MEMBERS, uidOf(publicEvent))
                .isPresent());
        assertTrue(eventRepo
                .findOpenByUid(station.id(), BlockAudience.MEMBERS, uidOf(internalEvent))
                .isPresent());
    }

    @Test
    void nobodyFindsWhatNotEveryReaderMaySee() {
        eventsNobodyMayName().forEach((why, event) -> {
            for (var audience : BlockAudience.values()) {
                assertTrue(
                        eventRepo
                                .findOpenByUid(station.id(), audience, uidOf(event))
                                .isEmpty(),
                        why + " for " + audience);
                assertFalse(offered(audience, event.name()), why + " offered to " + audience);
            }
        });
        assertTrue(eventRepo
                .findOpenByUid(station.id(), BlockAudience.MEMBERS, UUID.randomUUID())
                .isEmpty());
    }

    @Test
    void thePickerOffersEachAudienceWhatItMaySee() {
        assertTrue(offered(BlockAudience.PUBLIC, publicEvent.name()));
        assertFalse(offered(BlockAudience.PUBLIC, internalEvent.name()));
        assertTrue(offered(BlockAudience.MEMBERS, publicEvent.name()));
        assertTrue(offered(BlockAudience.MEMBERS, internalEvent.name()));
    }
}
