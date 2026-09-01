/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entering a pairing code, from the side that typed it.
 *
 * <p>Nothing about a pairing code expires, so every refusal these tests pin down names a situation
 * rather than a deadline.
 */
class FederationPairingCodeTest extends RepositoryTestBase {

    private FederationService service;
    private FederationRepository federationRepo;
    private Station issuer;
    private Station enterer;

    @BeforeEach
    void setup() {
        federationRepo = new FederationRepository();
        service = new FederationService(federationRepo, stationRepo, new Api());
        issuer = stationRepo.create("PairingCodeIssuer" + UUID.randomUUID());
        enterer = stationRepo.create("PairingCodeEnterer" + UUID.randomUUID());
    }

    @AfterEach
    void cleanup() {
        for (var partner : service.findPartners(issuer.id())) federationRepo.deletePartner(partner.id());
        for (var partner : service.findPartners(enterer.id())) federationRepo.deletePartner(partner.id());
        stationRepo.delete(issuer.id());
        stationRepo.delete(enterer.id());
    }

    private List<FederationPartner> partnersTowards(Station from, Station to) {
        return service.findPartners(from.id()).stream()
                .filter(partner -> partner.partnerStationId().equals(to.uid()))
                .toList();
    }

    @Test
    void aCodeWithATokenConnectsTheTwoStations() {
        var code = service.generateStationInvite(issuer.id(), issuer.uid());

        var outcome = service.enterPairingCode(enterer.id(), code);

        assertInstanceOf(FederationService.CodeOutcome.Partnered.class, outcome);
        assertEquals(
                FederationPartner.FederationStatus.ACTIVE,
                partnersTowards(enterer, issuer).getFirst().status());
    }

    /**
     * The station that was asked to connect answers by handing over a code instead of using the
     * request page. The open request is the same connection, so it must not stand in the way of the
     * code, and it must not survive it either.
     */
    @Test
    void aCodeIsAcceptedWhileTheEnteringStationHasAskedToConnect() {
        service.createPairRequest(enterer.id(), issuer.id());
        var code = service.generateStationInvite(issuer.id(), issuer.uid());

        var outcome = service.enterPairingCode(enterer.id(), code);

        assertInstanceOf(FederationService.CodeOutcome.Partnered.class, outcome);
        var towardsIssuer = partnersTowards(enterer, issuer);
        assertEquals(1, towardsIssuer.size(), "the answered request is gone");
        assertEquals(
                FederationPartner.FederationStatus.ACTIVE,
                towardsIssuer.getFirst().status());
    }

    /**
     * The same in the other direction: the station that made the code had already asked, and its own
     * request is spent once the code is used.
     */
    @Test
    void theIssuingStationsOwnRequestIsDroppedWhenItsCodeIsUsed() {
        service.createPairRequest(issuer.id(), enterer.id());
        var code = service.generateStationInvite(issuer.id(), issuer.uid());

        var outcome = service.enterPairingCode(enterer.id(), code);

        assertInstanceOf(FederationService.CodeOutcome.Partnered.class, outcome);
        var towardsEnterer = partnersTowards(issuer, enterer);
        assertEquals(1, towardsEnterer.size(), "the spent request is gone");
        assertEquals(
                FederationPartner.FederationStatus.ACTIVE,
                towardsEnterer.getFirst().status());
    }

    @Test
    void aCodeWithoutATokenAsksTheOtherStation() {
        var code = service.generatePairingCode(issuer.uid());

        var outcome = service.enterPairingCode(enterer.id(), code);

        assertInstanceOf(FederationService.CodeOutcome.Requested.class, outcome);
        assertEquals(
                FederationPartner.FederationStatus.PENDING,
                partnersTowards(enterer, issuer).getFirst().status());
        assertEquals(
                FederationService.CodeRefusal.REQUEST_PENDING, refusal(service.enterPairingCode(enterer.id(), code)));
    }

    @Test
    void aConnectedStationIsNotConnectedTwice() {
        service.enterPairingCode(enterer.id(), service.generateStationInvite(issuer.id(), issuer.uid()));

        var outcome = service.enterPairingCode(enterer.id(), service.generateStationInvite(issuer.id(), issuer.uid()));

        assertEquals(FederationService.CodeRefusal.ALREADY_PARTNERED, refusal(outcome));
    }

    @Test
    void aTokenThisInstanceHasNoRecordOfIsRefusedAsUsed() {
        var code = service.generatePairingCode(issuer.uid()) + "-neverissued";

        assertEquals(FederationService.CodeRefusal.SPENT_TOKEN, refusal(service.enterPairingCode(enterer.id(), code)));
    }

    @Test
    void aCodeMadeSomewhereElseSaysWhereItCameFrom() {
        var encodedUid = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(issuer.uid().toString().getBytes(StandardCharsets.UTF_8));
        var encodedHost = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("other.example.org".getBytes(StandardCharsets.UTF_8));
        var code = "ember-" + encodedUid + "-" + encodedHost + "-sometoken";

        var outcome = service.enterPairingCode(enterer.id(), code);

        assertEquals(FederationService.CodeRefusal.OTHER_INSTANCE, refusal(outcome));
        assertEquals("other.example.org", ((FederationService.CodeOutcome.Refused) outcome).detail());
    }

    @Test
    void aCodeNamingNoStationHereIsRefused() {
        var code = service.generatePairingCode(UUID.randomUUID());

        assertEquals(
                FederationService.CodeRefusal.UNKNOWN_STATION, refusal(service.enterPairingCode(enterer.id(), code)));
    }

    @Test
    void aStationCannotEnterItsOwnCode() {
        var code = service.generateStationInvite(issuer.id(), issuer.uid());

        assertEquals(FederationService.CodeRefusal.OWN_STATION, refusal(service.enterPairingCode(issuer.id(), code)));
    }

    @Test
    void somethingThatIsNotACodeIsRefusedAsSuch() {
        assertEquals(
                FederationService.CodeRefusal.MALFORMED, refusal(service.enterPairingCode(enterer.id(), "nonsense")));
    }

    /**
     * Nothing in the pairing machinery reads a clock, so a code that was made and then left alone is
     * still the same code when it is finally typed.
     */
    @Test
    void aCodeIsStillGoodAfterOtherCodesWereMade() {
        var code = service.generateStationInvite(issuer.id(), issuer.uid());
        service.generateStationInvite(issuer.id(), issuer.uid());
        service.generateStationInvite(issuer.id(), issuer.uid());

        assertTrue(service.enterPairingCode(enterer.id(), code) instanceof FederationService.CodeOutcome.Partnered);
    }

    private FederationService.CodeRefusal refusal(FederationService.CodeOutcome outcome) {
        return assertInstanceOf(FederationService.CodeOutcome.Refused.class, outcome)
                .reason();
    }
}
