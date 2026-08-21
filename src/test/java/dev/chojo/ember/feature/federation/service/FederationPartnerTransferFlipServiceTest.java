/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * What the source instance does with the partnerships a departed station leaves behind.
 *
 * <p>The other test of this service mocks everything, which is right for the announcing it does
 * over HTTP and wrong for this: the flip is a single statement, so a test that does not run it
 * against a database tests nothing about it. The statement compares a text parameter against a
 * uuid column, which PostgreSQL refuses outright, and the transfer stories that reach this code
 * never asked whether the row moved.
 */
class FederationPartnerTransferFlipServiceTest extends RepositoryTestBase {
    private static final String DESTINATION = "https://elsewhere.example";

    private static FederationPartnerTransferFixupService service;
    private static FederationRepository federationRepo;
    private static Station stayed;
    private static Station departed;
    private static Station departedToo;
    private static Station third;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        service = new FederationPartnerTransferFixupService(
                federationRepo, mock(FederationHttpClient.class), stationRepo);
        stayed = stationRepo.create("FlipStationStayed");
        // One departed station per story: a station may hold only one partnership with any other,
        // and the flip reaches every row naming the station it is given.
        departed = stationRepo.create("FlipStationDeparted");
        departedToo = stationRepo.create("FlipStationDepartedToo");
        third = stationRepo.create("FlipStationThird");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(stayed.id());
        stationRepo.delete(departed.id());
        stationRepo.delete(departedToo.id());
        stationRepo.delete(third.id());
    }

    @Test
    void aPartnershipWithTheDepartedStationPointsAtWhereItWent() {
        var partner = federationRepo.createPartner(stayed.id(), departed.uid(), "FLIP-CODE-1", "publicKey", null);

        service.flipSourceSideRetainedPartners(departed.uid(), DESTINATION);

        var flipped = federationRepo.findPartnerById(partner.id()).orElseThrow();
        assertEquals(
                DESTINATION,
                flipped.remoteHost(),
                "the partner is on another instance now, and nothing else tells this one where");
    }

    /**
     * A partnership that was already reaching across instances is somebody else's arrangement. The
     * station that left has nothing to do with where it points, so it keeps pointing there.
     */
    @Test
    void aPartnershipThatWasAlreadyRemoteIsLeftAlone() {
        var alreadyRemote = federationRepo.createPartner(
                stayed.id(), third.uid(), "FLIP-CODE-2", "publicKey", "https://third.example");

        service.flipSourceSideRetainedPartners(departed.uid(), DESTINATION);

        assertEquals(
                "https://third.example",
                federationRepo.findPartnerById(alreadyRemote.id()).orElseThrow().remoteHost());
    }

    /**
     * Without a destination there is nowhere to point, and pointing a partnership at nothing is
     * worse than leaving it where a later ping can still find it.
     */
    @Test
    void withoutADestinationNothingIsTouched() {
        var partner = federationRepo.createPartner(stayed.id(), departedToo.uid(), "FLIP-CODE-3", "publicKey", null);

        service.flipSourceSideRetainedPartners(departedToo.uid(), "  ");

        assertNull(federationRepo.findPartnerById(partner.id()).orElseThrow().remoteHost());
    }
}
