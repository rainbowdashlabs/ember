/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Covers the contract vector refresh: the ping round trip that stores a remote partner's
 * vector, the short-circuits for local partners and stations without a signing key, and the
 * asynchronous variants triggered by mismatch detection.
 */
class FederationContractRefreshServiceTest extends RepositoryTestBase {
    private static final String REMOTE_HOST = "https://remote-contract.example.com";

    private static FederationRepository federationRepo;
    private static FederationService federationService;
    private static FederationHttpClient httpClient;
    private static FederationContractRefreshService service;
    private static Station station;
    private static Station localStation;
    private static Station remoteStation;
    private static Station keylessStation;
    private static FederationPartner remotePartner;
    private static FederationPartner localPartner;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        httpClient = mock(FederationHttpClient.class);

        station = stationRepo.create("ContractRefreshStation");
        localStation = stationRepo.create("ContractRefreshLocal");
        remoteStation = stationRepo.create("ContractRefreshRemote");
        keylessStation = stationRepo.create("ContractRefreshKeyless");

        federationService.acceptInvite(
                station.id(),
                localStation.id(),
                federationService.encodePublicKey(federationService.generateKeyPair()),
                null,
                null);
        federationService.acceptInvite(
                station.id(),
                remoteStation.id(),
                federationService.encodePublicKey(federationService.generateKeyPair()),
                REMOTE_HOST,
                null);
        var partners = federationService.findPartners(station.id());
        remotePartner = partners.stream()
                .filter(FederationPartner::isRemote)
                .findFirst()
                .orElseThrow();
        localPartner = partners.stream()
                .filter(partner -> !partner.isRemote())
                .findFirst()
                .orElseThrow();
    }

    @AfterAll
    static void cleanup() {
        for (var partner : federationService.findPartners(station.id())) federationRepo.deletePartner(partner.id());
        for (var partner : federationService.findPartners(localStation.id())) {
            federationRepo.deletePartner(partner.id());
        }
        for (var partner : federationService.findPartners(remoteStation.id())) {
            federationRepo.deletePartner(partner.id());
        }
        stationRepo.delete(station.id());
        stationRepo.delete(localStation.id());
        stationRepo.delete(remoteStation.id());
        stationRepo.delete(keylessStation.id());
    }

    /**
     * A fresh service per test also resets the per-partner retry interval, which would
     * otherwise swallow every async refresh after the first one.
     */
    @BeforeEach
    void resetClientAndService() {
        reset(httpClient);
        service = new FederationContractRefreshService(federationRepo, stationRepo, httpClient);
    }

    private static void stubPing(FederationContract contract) {
        when(httpClient.get(
                        eq(REMOTE_HOST),
                        pathIs(RemoteFederationRoutes.VERSION_PING.path()),
                        any(),
                        eq(station.id()),
                        any(),
                        eq(RemoteFederationRoutes.VersionPingResponse.class)))
                .thenReturn(contract == null ? null : new RemoteFederationRoutes.VersionPingResponse(contract));
    }

    private static FederationContract storedContract() {
        return federationRepo.findPartnerById(remotePartner.id()).orElseThrow().federationContract();
    }

    private static void awaitStoredContract(FederationContract expected) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (expected.equals(storedContract())) return;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertEquals(expected, storedContract());
    }

    @Test
    void refreshSkipsLocalPartner() {
        assertFalse(service.refresh(localPartner));
    }

    @Test
    void refreshSkipsPartnerWithoutSigningKey() {
        var keyless = federationRepo.createPartner(keylessStation.id(), station.uid(), null, null, REMOTE_HOST);
        assertFalse(service.refresh(keyless));
        federationRepo.deletePartner(keyless.id());
    }

    @Test
    void refreshReturnsFalseWhenPingUnanswered() {
        stubPing(null);
        assertFalse(service.refresh(remotePartner));
    }

    @Test
    void refreshStoresReceivedVector() {
        var contract = new FederationContract("1111222233334444", Map.of("KB_SHARE", "aaaabbbbccccdddd"));
        stubPing(contract);
        assertTrue(service.refresh(remotePartner));
        assertEquals(contract, storedContract());
    }

    @Test
    void refreshKeepsUnchangedVector() {
        var contract = new FederationContract("5555666677778888", Map.of("QUIZ_SHARE", "eeeeffff00001111"));
        federationRepo.updateFederationContract(remotePartner.id(), contract);
        stubPing(contract);
        assertTrue(service.refresh(remotePartner));
        assertEquals(contract, storedContract());
    }

    @Test
    void refreshAsyncSkipsLocalPartner() {
        service.refreshAsync(localPartner);
        assertNull(
                federationRepo.findPartnerById(localPartner.id()).orElseThrow().federationContract());
    }

    @Test
    void refreshAsyncStoresReceivedVector() {
        var contract = FederationContractVersions.current();
        stubPing(contract);
        service.refreshAsync(remotePartner);
        awaitStoredContract(contract);
    }

    @Test
    void refreshAsyncByStationPairStoresReceivedVector() {
        var contract = new FederationContract("9999aaaabbbbcccc", Map.of("NEWS_SHARE", "2222333344445555"));
        stubPing(contract);
        service.refreshAsync(station.id(), remoteStation.uid());
        awaitStoredContract(contract);
    }

    @Test
    void refreshAsyncIgnoresUnknownStationPair() {
        service.refreshAsync(station.id(), UUID.randomUUID());
    }
}
