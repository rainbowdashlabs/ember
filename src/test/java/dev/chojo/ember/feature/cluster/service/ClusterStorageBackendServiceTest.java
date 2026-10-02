/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.service.ClusterStorageBackendService.Expected;
import dev.chojo.ember.feature.cluster.service.ClusterStorageBackendService.Placement;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditEntry;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.core.BackendRequest;
import dev.chojo.ember.feature.storage.core.BackendSummary;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * What an association decided about storage, and which of its stations are where.
 *
 * <p>The subject is the gap between the two. Every case here is one row of the expected-placement table, and
 * the reason the table exists is that a decision is written in a request while a copy is not.
 */
class ClusterStorageBackendServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final Actor ACTOR = Actor.system("association-storage-test");

    private static ClusterStorageConfigRepository configRepository;
    private static ClusterStationStorageRepository placements;
    private static StationStorageConfigRepository stationConfigRepository;
    private static CredentialCipher cipher;
    private static ClusterStorageBackendService service;

    /**
     * Every backend the factory hands back is on disk, so a move is a real copy between two directories
     * rather than a connection to somewhere that does not exist.
     */
    @BeforeAll
    static void setup() throws IOException {
        configRepository = new ClusterStorageConfigRepository();
        placements = new ClusterStationStorageRepository();
        stationConfigRepository = new StationStorageConfigRepository();
        cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));

        var local = new LocalStorageBackend(Files.createTempDirectory("cluster-storage-instance"));
        var target = new LocalStorageBackend(Files.createTempDirectory("cluster-storage-target"));
        var factory = new LocalEverywhereFactory(new Storage(), local, target);
        service = newClusterStorageBackendService(factory, new StorageBackendResolver(local));
    }

    private static List<StorageAuditAction> historyOf(int clusterId) {
        return storageBackendAuditRepo.findByCluster(clusterId, Optional.empty(), 50).reversed().stream()
                .map(StorageAuditEntry::action)
                .toList();
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

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.EVERY_STATION, false));
        assertEquals(ClusterRefusal.CLUSTER_STORAGE_REACH_WITHOUT_STORAGE, refused.refusal());

        clusterService.delete(cluster.id());
    }

    /**
     * The whole expected-placement table, read at one station as the two settings move under it.
     */
    @Test
    void whereAStationBelongsFollowsTheTwoSettings() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Ablage " + NAMES.incrementAndGet());
        var version = service.setBackend(ACTOR, cluster.id(), backend("erste"));

        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.OWN_FILES, false);
        assertEquals(
                Expected.INSTANCE_DEFAULT,
                rowFor(cluster.id(), station.id()).expected(),
                "its own files means its own files, and a member station is not one of them");
        assertEquals(
                Expected.THE_CLUSTERS,
                rowFor(cluster.id(), cluster.homeStationId()).expected(),
                "the association's own store is a station like any other, and this is the one it reaches");

        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.EVERY_STATION, false);
        assertEquals(Expected.THE_CLUSTERS, rowFor(cluster.id(), station.id()).expected());
        assertFalse(rowFor(cluster.id(), station.id()).inPlace(), "deciding it does not carry anything there");

        placements.place(station.id(), cluster.id(), version.id());
        assertTrue(rowFor(cluster.id(), station.id()).inPlace(), "and carrying it there does");

        stationConfigRepository.upsert(station.id(), backend("eigen"));
        placements.remove(station.id());
        assertEquals(
                Expected.ITS_OWN,
                rowFor(cluster.id(), station.id()).expected(),
                "a station that brought its own opts out while the association allows it");
        assertTrue(rowFor(cluster.id(), station.id()).inPlace());

        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.EVERY_STATION, true);
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
        var first = service.setBackend(ACTOR, cluster.id(), backend("gleich"));
        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.EVERY_STATION, false);
        placements.place(station.id(), cluster.id(), first.id());

        var rotated = service.setBackend(ACTOR, cluster.id(), backend("gleich"));
        assertEquals(first.id(), rotated.id(), "the same destination is the same version with a new secret");
        assertTrue(rowFor(cluster.id(), station.id()).inPlace());

        var moved = service.setBackend(ACTOR, cluster.id(), backend("woanders"));
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
        var version = service.setBackend(ACTOR, cluster.id(), backend("aufgegeben"));
        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.EVERY_STATION, true);
        placements.place(station.id(), cluster.id(), version.id());

        service.dropBackend(ACTOR, cluster.id());

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
        service.setBackend(ACTOR, cluster.id(), backend("umzug"));
        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.EVERY_STATION, false);
        assertFalse(rowFor(cluster.id(), station.id()).inPlace());

        service.moveStation(ACTOR, cluster.id(), station.id());

        assertTrue(rowFor(cluster.id(), station.id()).inPlace());
        var refused = assertThrows(RefusalResponse.class, () -> service.moveStation(ACTOR, cluster.id(), station.id()));
        assertEquals(ClusterRefusal.CLUSTER_STORAGE_STATION_ALREADY_IN_PLACE, refused.refusal());

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
        var version = service.setBackend(ACTOR, cluster.id(), backend("beitritt"));
        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.EVERY_STATION, false);
        var station = stationRepo.create("Wache Beitritt " + NAMES.incrementAndGet());

        service.takeOverOnJoin(cluster.id(), station.id());
        assertEquals(
                version.id(),
                placements.findByStation(station.id()).orElseThrow().configId(),
                "the copy finished before anybody was taken in");

        service.handBackOnRelease(cluster.id(), station.id());
        assertTrue(
                placements.findByStation(station.id()).isEmpty(), "and the files come home before the membership goes");

        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.NONE, false);
        service.takeOverOnJoin(cluster.id(), station.id());
        assertTrue(
                placements.findByStation(station.id()).isEmpty(),
                "nothing happens to a station the association is not reaching for");
        service.handBackOnRelease(cluster.id(), station.id());

        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    /**
     * A version nobody stands on any more is gone with its credentials, and one somebody stands on stays
     * until the last of them has been carried off it.
     */
    @Test
    void aRetiredVersionGoesWithItsCredentialsOnceNobodyStandsOnIt() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Ablage " + NAMES.incrementAndGet());
        var unused = service.setBackend(ACTOR, cluster.id(), backend("ungenutzt"));
        var used = service.setBackend(ACTOR, cluster.id(), backend("genutzt"));
        assertTrue(configRepository.findById(unused.id()).isEmpty(), "nobody ever stood on the first one");

        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.EVERY_STATION, false);
        service.moveStation(ACTOR, cluster.id(), station.id());
        var next = service.setBackend(ACTOR, cluster.id(), backend("naechste"));
        assertTrue(configRepository.findById(used.id()).isPresent(), "the station still stands on it");

        service.moveStation(ACTOR, cluster.id(), station.id());
        assertTrue(configRepository.findById(used.id()).isEmpty(), "and the last one carried off takes it");

        service.dropBackend(ACTOR, cluster.id());
        assertTrue(configRepository.findById(next.id()).isPresent());
        clusterService.releaseStation(cluster.id(), station.id());
        assertTrue(configRepository.findById(next.id()).isEmpty(), "a dropped one goes when the station leaves");

        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    /**
     * Everything the association does to its storage is in its history: the storage it set, what it decided,
     * the moves it made and the ones refused, the join and the release, and giving the storage up. A move
     * its checks refuse never started, so the history does not say it did.
     */
    @Test
    void everyActOfTheAssociationIsInItsHistory() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Ablage " + NAMES.incrementAndGet());
        service.setBackend(ACTOR, cluster.id(), backend("verlauf"));
        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.EVERY_STATION, false);
        service.moveStation(ACTOR, cluster.id(), station.id());
        assertThrows(RefusalResponse.class, () -> service.moveStation(ACTOR, cluster.id(), station.id()));
        assertThrows(RefusalResponse.class, () -> service.setPolicy(ACTOR, cluster.id(), null, false));
        var joining = stationRepo.create("Wache Beitritt " + NAMES.incrementAndGet());
        service.takeOverOnJoin(cluster.id(), joining.id());
        service.handBackOnRelease(cluster.id(), joining.id());
        service.dropBackend(ACTOR, cluster.id());

        assertEquals(
                List.of(
                        StorageAuditAction.CREATED,
                        StorageAuditAction.POLICY_CHANGED,
                        StorageAuditAction.MIGRATION_STARTED,
                        StorageAuditAction.MIGRATION_COMPLETED,
                        StorageAuditAction.REJECTED,
                        StorageAuditAction.MIGRATION_STARTED,
                        StorageAuditAction.MIGRATION_COMPLETED,
                        StorageAuditAction.MIGRATION_STARTED,
                        StorageAuditAction.MIGRATION_COMPLETED,
                        StorageAuditAction.DELETED,
                        StorageAuditAction.POLICY_CHANGED),
                historyOf(cluster.id()));
        var joined = storageBackendAuditRepo.findByStation(joining.id(), Optional.empty(), 10);
        assertEquals(Optional.of("association-release"), joined.getFirst().systemActor());
        assertEquals(Optional.of("association-join"), joined.getLast().systemActor());

        placements.remove(station.id());
        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
        stationRepo.delete(joining.id());
        clusterService.delete(cluster.id());
    }

    /**
     * New credentials for the destination the association already has reach the backend built for it, so
     * the next file is written with them rather than with the old ones still cached.
     */
    @Test
    void newCredentialsReachTheBackendItsStationsUse() {
        var resolver = mock(StorageBackendResolver.class);
        var rotating = newClusterStorageBackendService(
                new LocalEverywhereFactory(new Storage(), localStorage(), localStorage()), resolver);
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var version = rotating.setBackend(ACTOR, cluster.id(), backend("gedreht"));

        rotating.setBackend(ACTOR, cluster.id(), backend("gedreht"));

        verify(resolver).invalidateClusterVersion(version.id());
        clusterService.delete(cluster.id());
    }

    @Test
    void storageTheAssociationCannotChooseIsRefusedAndWrittenDown() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);

        var refused = assertThrows(
                RefusalResponse.class, () -> service.apply(ACTOR, cluster.id(), new BackendRequest.LocalRequest(null)));

        assertEquals(StorageRefusal.STORAGE_DESTINATION_NOT_OFFERED, refused.refusal());
        assertEquals(List.of(StorageAuditAction.REJECTED), historyOf(cluster.id()));
        clusterService.delete(cluster.id());
    }

    @Test
    void storageTypedInIsCheckedSavedAndDescribedWithoutItsSecret() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var request = new BackendRequest.SmbRequest(
                "smb.example.invalid", 445, "geteilt", "WORKGROUP", "/basis", true, false, "nutzer", "geheim");

        var described = service.apply(ACTOR, cluster.id(), request);

        var backend = (BackendSummary.SmbSummary) described.backend();
        assertEquals("geteilt", backend.share());
        assertTrue(backend.credentialFingerprint() != null);
        assertEquals(ClusterBackendReach.NONE, described.reach());
        clusterService.delete(cluster.id());
    }

    @Test
    void aStationOfAnotherAssociationIsNotThisOnesToMove() {
        var cluster = clusterService.create("Kreisverband Speicher " + NAMES.incrementAndGet(), null);
        var other = clusterService.create("Kreisverband Fremd " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(other.id(), "Wache Fremd " + NAMES.incrementAndGet());
        service.setBackend(ACTOR, cluster.id(), backend("fremd"));
        service.setPolicy(ACTOR, cluster.id(), ClusterBackendReach.EVERY_STATION, false);

        var refused = assertThrows(RefusalResponse.class, () -> service.moveStation(ACTOR, cluster.id(), station.id()));
        assertEquals(ClusterRefusal.CLUSTER_STORAGE_STATION_NOT_IN_CLUSTER, refused.refusal());

        clusterService.releaseStation(other.id(), station.id());
        stationRepo.delete(station.id());
        clusterService.delete(other.id());
        clusterService.delete(cluster.id());
    }
}
