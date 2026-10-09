/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * What the destination of a station transfer does with the partnerships its own stations kept with the
 * station that arrived, which reached it on another installation until now.
 *
 * <p>Source and destination are one database here, but the stations are made apart: the station that
 * arrives carries the uid its bundle claims, nobody else here carrying it, so turning a partnership
 * over is seen as it happens rather than hidden behind a uid that could not be taken.
 */
class FederationPartnerWelcomeHomeTest extends RepositoryTestBase {
    private static final String ELSEWHERE = "https://elsewhere.example";
    private static final String SOURCE = "https://source.example";

    private static FederationRepository federationRepo;
    private static StationKeyStore keys;
    private static FederationPartnerTransferFixupService service;

    private final List<Station> made = new ArrayList<>();
    private Station holder;
    private UUID claimedUid;
    private PrivateKey realKey;

    @BeforeAll
    static void setupServices() {
        federationRepo = new FederationRepository();
        keys = TestStationKeys.store();
        service = TestStationKeys.partnerFixup(federationRepo, mock(FederationHttpClient.class));
    }

    /** A station here that keeps a partnership with a station on another installation, by its key. */
    @BeforeEach
    void partnershipWithAStationElsewhere() throws Exception {
        holder = station("Welcome Holder");
        claimedUid = UUID.randomUUID();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        realKey = generator.generateKeyPair().getPrivate();
        federationRepo.createRemotePartner(
                holder.id(),
                claimedUid,
                keys.ensurePublicKey(holder.id()),
                StationKeyStore.publicKeyOf(realKey),
                ELSEWHERE,
                "Elsewhere",
                FederationContractVersions.current());
    }

    @AfterEach
    void cleanup() {
        made.forEach(station -> stationRepo.delete(station.id()));
        made.clear();
    }

    /** The station that moved here brought the key its partners know it by, so they reach it here now. */
    @Test
    void aStationThatBroughtItsKeyIsReachedHere() {
        var arrived = arrivedAs(claimedUid);
        keys.adopt(arrived.id(), realKey);

        service.rewriteAfterImport(arrived.id(), SOURCE);

        assertNull(partnership().remoteHost());
    }

    /**
     * A bundle claiming the uid of a station elsewhere, without its key, turns no partnership here: the
     * holder keeps reaching the real station where it runs.
     */
    @Test
    void aStationClaimingAForeignUidTurnsNoPartnership() {
        var arrived = arrivedAs(claimedUid);
        keys.ensurePublicKey(arrived.id());

        service.rewriteAfterImport(arrived.id(), SOURCE);

        assertEquals(ELSEWHERE, partnership().remoteHost());
    }

    @Test
    void aStationWithoutAnyKeyTurnsNoPartnership() {
        var arrived = arrivedAs(claimedUid);

        service.rewriteAfterImport(arrived.id(), SOURCE);

        assertEquals(ELSEWHERE, partnership().remoteHost());
    }

    private Station arrivedAs(UUID uid) {
        var arrived = station("Welcome Arrived");
        stationRepo.updateUid(arrived.id(), uid);
        return arrived;
    }

    private FederationPartner partnership() {
        return federationRepo
                .findPartnerByStationAndRemoteUid(holder.id(), claimedUid)
                .orElseThrow();
    }

    private Station station(String name) {
        var station = stationRepo.create(name);
        made.add(station);
        return station;
    }
}
