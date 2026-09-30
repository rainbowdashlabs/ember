/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.entity.ChangeType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the federation screens and the partner-facing endpoints find partner rows, name them and
 * record what a partner registered or polled.
 */
class FederationPartnerLookupTest extends RepositoryTestBase {
    private final FederationRepository federationRepo = new FederationRepository();
    private FederationService service;
    private Station asking;
    private Station asked;

    @BeforeEach
    void setup() {
        service = new FederationService(federationRepo, stationRepo, TestStationKeys.store(), new Api());
        asking = stationRepo.create("LookupAsking");
        asked = stationRepo.create("LookupAsked");
    }

    @AfterEach
    void cleanup() {
        for (var p : service.findPartners(asking.id())) federationRepo.deletePartner(p.id());
        for (var p : service.findPartners(asked.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(asking.id());
        stationRepo.delete(asked.id());
    }

    @Test
    void aPartnerRowIsFoundByTheStationItPartnersWith() {
        var request = service.createPairRequest(asking.id(), asked.id());

        assertEquals(
                request.id(),
                service.findPartnerByRemoteUid(asking.id(), asked.uid())
                        .orElseThrow()
                        .id());
        assertTrue(service.findPartnerByRemoteUid(asked.id(), asking.uid()).isEmpty());
    }

    @Test
    void aPairRequestIsFoundOnlyByTheStationItAsks() {
        var request = service.createPairRequest(asking.id(), asked.id());

        assertEquals(
                request.id(),
                service.findRequestTo(request.id(), asked.id()).orElseThrow().id());
        assertTrue(service.findRequestTo(request.id(), asking.id()).isEmpty());
        assertTrue(service.findRequestTo(-1, asked.id()).isEmpty());
    }

    @Test
    void partnersAndRequestersAreNamedFromTheStationsHere() {
        var request = service.createPairRequest(asking.id(), asked.id());

        assertEquals("LookupAsked", service.partnerName(request));
        assertEquals("LookupAsking", service.requesterName(request));
        var gone = new FederationPartner(
                0,
                -1,
                UUID.randomUUID(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.PENDING,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null);
        assertEquals("Unknown", service.partnerName(gone));
        assertEquals("Unknown", service.requesterName(gone));
    }

    @Test
    void anInviteIsMadeOnlyForAStationThatExists() {
        assertTrue(service.generateStationInvite(asking.id()).orElseThrow().startsWith("ember-"));
        assertTrue(service.generateStationInvite(-1).isEmpty());
    }

    @Test
    void aWebhookIsRememberedAndAPollIsNoted() {
        var request = service.createPairRequest(asking.id(), asked.id());
        service.registerWebhook(request.id(), "https://partner.example/hook");
        service.logChange(asking.id(), ContentType.KB, 5, ChangeType.UPDATED);

        var changes = service.syncChanges(request, Instant.EPOCH);

        assertEquals("https://partner.example/hook", federationRepo.getWebhookUrl(request.id()));
        assertTrue(changes.stream().anyMatch(change -> change.contentId() == 5));
    }
}
