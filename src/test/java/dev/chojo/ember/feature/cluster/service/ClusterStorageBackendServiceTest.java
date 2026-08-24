/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.service.ClusterStorageBackendService.Expected;
import dev.chojo.ember.feature.cluster.service.ClusterStorageBackendService.Placement;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.migration.MigrationLockRegistry;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageMigrationService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an association decided about storage, and which of its stations are where.
 *
 * <p>The subject is the gap between the two. Every case here is one row of the expected-placement table, and
 * the reason the table exists is that a decision is written in a request while a copy is not.
 */
class ClusterStorageBackendServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ClusterStorageConfigRepository configRepository;
    private static ClusterStationStorageRepository placements;
    private static StationStorageConfigRepository stationConfigRepository;
    private static CredentialCipher cipher;
    private static ClusterStorageBackendService service;

    @BeforeAll
    static void setup() throws IOException {
        configRepository = new ClusterStorageConfigRepository();
        placements = new ClusterStationStorageRepository();
        stationConfigRepository = new StationStorageConfigRepository();
        cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));

        // A factory that hands back an on-disk backend for anything, so a move is a real copy between two
        // directories rather than a connection to somewhere that does not exist
        var local = new LocalStorageBackend(Files.createTempDirectory("cluster-storage-instance"));
        var target = new LocalStorageBackend(Files.createTempDirectory("cluster-storage-target"));
        var factory = new LocalEverywhereFactory(new Storage(), local, target);
        var resolver = new StorageBackendResolver(local);
        service = new ClusterStorageBackendService(
                clusterRepo,
                stationRepo,
                configRepository,
                placements,
                stationConfigRepository,
                new StorageMigrationService(
                        stationRepo,
                        stationConfigRepository,
                        placements,
                        factory,
                        resolver,
                        new MigrationLockRegistry()),
                resolver);
    }

    /**
     * Hands back an on-disk backend whatever it is asked for, so the copy is real and reaches nothing.
     */
    private static final class LocalEverywhereFactory extends StorageBackendFactory {
        private final StorageBackend target;

        private LocalEverywhereFactory(Storage storage, LocalStorageBackend local, StorageBackend target) {
            super(storage, local, null);
            this.target = target;
        }

        @Override
        public StorageBackend buildForStation(StationStorageBackendConfig config) {
            return target;
        }
    }

    private static StationStorageBackendConfig backend(String share) {
        return new StationStorageBackendConfig.SmbVariant(
                "smb.example.invalid",
                445,
                share,
                "WORKGROUP",
                "/base",
                true,
                false,
                cipher.encrypt("{\"username\":\"u\",\"password\":\"p\"}"));
    }

    private static Placement rowFor(int clusterId, int stationId) {
        return service.listPlacements(clusterId).stream()
                .filter(placement -> placement.stationId() == stationId)
                .findFirst()
                .orElseThrow();
    }

    /**
     * A decision needs somewhere to point at, and until the association has said where, its stations belong
     * on the instance's disk whatever else is true.
     */
    @Test
    void aReachWithNowhereToReachIsRefused() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);

        assertThrows(
                BadRequestResponse.class,
                () -> service.setPolicy(cluster.id(), ClusterBackendReach.EVERY_STATION, false));

        clusterService.delete(cluster.id());
    }

    /**
     * The whole expected-placement table, read at one station as the two settings move under it.
     */
    @Test
    void whereAStationBelongsFollowsTheTwoSettings() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Ablage " + NAMES.incrementAndGet());
        var version = service.setBackend(cluster.id(), backend("erste"));

        service.setPolicy(cluster.id(), ClusterBackendReach.OWN_FILES, false);
        assertEquals(
                Expected.INSTANCE_DEFAULT,
                rowFor(cluster.id(), station.id()).expected(),
                "its own files means its own files, and a member station is not one of them");
        assertEquals(
                Expected.THE_CLUSTERS,
                rowFor(cluster.id(), cluster.homeStationId()).expected(),
                "the association's own store is a station like any other, and this is the one it reaches");

        service.setPolicy(cluster.id(), ClusterBackendReach.EVERY_STATION, false);
        assertEquals(Expected.THE_CLUSTERS, rowFor(cluster.id(), station.id()).expected());
        assertFalse(rowFor(cluster.id(), station.id()).inPlace(), "deciding it does not carry anything there");

        placements.place(station.id(), cluster.id(), version.id());
        assertTrue(rowFor(cluster.id(), station.id()).inPlace(), "and carrying it there does");

        // A station that brought its own is opting out, which it may do while the association allows it
        stationConfigRepository.upsert(station.id(), backend("eigen"));
        placements.remove(station.id());
        assertEquals(Expected.ITS_OWN, rowFor(cluster.id(), station.id()).expected());
        assertTrue(rowFor(cluster.id(), station.id()).inPlace());

        service.setPolicy(cluster.id(), ClusterBackendReach.EVERY_STATION, true);
        assertEquals(
                Expected.THE_CLUSTERS,
                rowFor(cluster.id(), station.id()).expected(),
                "and the lock is what takes the opt-out away again");

        stationConfigRepository.delete(station.id());
        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    /**
     * Rotating a secret must move nobody, and pointing somewhere else must move everybody.
     */
    @Test
    void aCredentialChangeMovesNobodyAndANewDestinationMovesEverybody() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Ablage " + NAMES.incrementAndGet());
        var first = service.setBackend(cluster.id(), backend("gleich"));
        service.setPolicy(cluster.id(), ClusterBackendReach.EVERY_STATION, false);
        placements.place(station.id(), cluster.id(), first.id());

        var rotated = service.setBackend(cluster.id(), backend("gleich"));
        assertEquals(first.id(), rotated.id(), "the same destination is the same version with a new secret");
        assertTrue(rowFor(cluster.id(), station.id()).inPlace());

        var moved = service.setBackend(cluster.id(), backend("woanders"));
        assertFalse(moved.id() == first.id(), "somewhere else is a version of its own");
        assertFalse(
                rowFor(cluster.id(), station.id()).inPlace(),
                "and everybody standing on the old one is out of place until they are carried across");

        placements.remove(station.id());
        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    /**
     * Giving up the storage leaves what people stand on where it is, because the alternative is a station
     * pointed at nothing.
     */
    @Test
    void droppingTheStorageKeepsWhatPeopleStandOn() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Ablage " + NAMES.incrementAndGet());
        var version = service.setBackend(cluster.id(), backend("aufgegeben"));
        service.setPolicy(cluster.id(), ClusterBackendReach.EVERY_STATION, true);
        placements.place(station.id(), cluster.id(), version.id());

        service.dropBackend(cluster.id());

        assertEquals(ClusterBackendReach.NONE, service.findPolicy(cluster.id()).reach());
        assertTrue(configRepository.findById(version.id()).isPresent(), "the version somebody stands on stays");
        var row = rowFor(cluster.id(), station.id());
        assertEquals(Expected.INSTANCE_DEFAULT, row.expected());
        assertFalse(row.inPlace(), "and the lock does not hold anybody onto storage that is gone");

        placements.remove(station.id());
        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    /**
     * The move itself: an out-of-place station is carried across and reads as in place afterwards, and a
     * station already where it belongs is not moved again.
     */
    @Test
    void movingAnOutOfPlaceStationCarriesItAndSayingSoTwiceIsRefused() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Ablage " + NAMES.incrementAndGet());
        service.setBackend(cluster.id(), backend("umzug"));
        service.setPolicy(cluster.id(), ClusterBackendReach.EVERY_STATION, false);
        assertFalse(rowFor(cluster.id(), station.id()).inPlace());

        service.moveStation(cluster.id(), station.id());

        assertTrue(rowFor(cluster.id(), station.id()).inPlace());
        assertThrows(BadRequestResponse.class, () -> service.moveStation(cluster.id(), station.id()));

        placements.remove(station.id());
        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    /**
     * The two moments a move is not on demand: a station arrives with its files and leaves with them.
     */
    @Test
    void aStationArrivesWithItsFilesAndLeavesWithThem() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var version = service.setBackend(cluster.id(), backend("beitritt"));
        service.setPolicy(cluster.id(), ClusterBackendReach.EVERY_STATION, false);
        var station = stationRepo.create("Wache Beitritt " + NAMES.incrementAndGet());

        service.takeOverOnJoin(cluster.id(), station.id());
        assertEquals(
                version.id(),
                placements.findByStation(station.id()).orElseThrow().configId(),
                "the copy finished before anybody was taken in");

        service.handBackOnRelease(cluster.id(), station.id());
        assertTrue(
                placements.findByStation(station.id()).isEmpty(), "and the files come home before the membership goes");

        // Neither of them does anything to a station the association is not reaching for
        service.setPolicy(cluster.id(), ClusterBackendReach.NONE, false);
        service.takeOverOnJoin(cluster.id(), station.id());
        assertTrue(placements.findByStation(station.id()).isEmpty());
        service.handBackOnRelease(cluster.id(), station.id());

        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    @Test
    void aStationOfAnotherAssociationIsNotThisOnesToMove() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var other = clusterService.create("Kreisverband Fremd " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(other.id(), "Wache Fremd " + NAMES.incrementAndGet());
        service.setBackend(cluster.id(), backend("fremd"));
        service.setPolicy(cluster.id(), ClusterBackendReach.EVERY_STATION, false);

        assertThrows(BadRequestResponse.class, () -> service.moveStation(cluster.id(), station.id()));

        clusterService.releaseStation(other.id(), station.id());
        stationRepo.delete(station.id());
        clusterService.delete(other.id());
        clusterService.delete(cluster.id());
    }
}
