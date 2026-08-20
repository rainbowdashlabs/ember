/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.*;

class ClusterServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ClusterService service;

    @BeforeAll
    static void setup() {
        service = new ClusterService(clusterRepo, stationRepo);
    }

    private int freshCluster() {
        return service.create("Kreisverband " + NAMES.incrementAndGet(), "Der Träger")
                .id();
    }

    /** An account of its own per case, so no test depends on another leaving one behind. */
    private Account freshAccount() {
        int n = NAMES.incrementAndGet();
        return accountRepo.create("cluster" + n + "@test.com", "Clus", "Ter" + n);
    }

    @Test
    void aClusterArrivesWithTheStationShellItOwns() {
        var cluster = service.create("Kreisverband Nord", "Der Träger");

        var home = stationRepo.findById(cluster.homeStationId()).orElseThrow();
        assertEquals(StationKind.CLUSTER_HOME, home.stationKind());
        assertEquals("Kreisverband Nord", home.name(), "the shell is named after what owns it");
        assertNull(home.clusterId(), "the home station is owned by the cluster, not a member of it");
        assertNotNull(cluster.uid());

        // Only the four things a cluster owns are left switched on
        var disabled = stationRepo.findDisabledModules(home.id());
        assertFalse(disabled.contains(StationModule.INVENTORY));
        assertFalse(disabled.contains(StationModule.KNOWLEDGE_BASE));
        assertFalse(disabled.contains(StationModule.NEWS));
        assertFalse(disabled.contains(StationModule.EVENTS));
        assertTrue(disabled.contains(StationModule.ATTENDANCE), "a shell nobody joins keeps no attendance");
        assertTrue(disabled.contains(StationModule.WAITING_LIST));
    }

    @Test
    void aClusterNeedsAName() {
        assertThrows(BadRequestResponse.class, () -> service.create("  ", null));
    }

    @Test
    void renamingACluster1KeepsItsHomeStationInStep() {
        var cluster = service.create("Kreisverband Alt", null);

        assertTrue(service.rename(cluster.id(), "Kreisverband Neu", "Umbenannt"));

        assertEquals(
                "Kreisverband Neu", service.findById(cluster.id()).orElseThrow().name());
        assertEquals(
                "Kreisverband Neu",
                stationRepo.findById(cluster.homeStationId()).orElseThrow().name(),
                "federation reads a partner's label off the station row, so the two cannot drift");
    }

    @Test
    void theHomeStationIsNotOfferedAsSomethingToJoin() {
        var cluster = service.create("Kreisverband Versteckt", null);

        assertTrue(stationRepo.findAll().stream().anyMatch(s -> s.id() == cluster.homeStationId()));
        assertFalse(
                stationRepo.findAllRegular().stream().anyMatch(s -> s.id() == cluster.homeStationId()),
                "the user-facing listing leaves the shell out");
    }

    @Test
    void aStationBelongsToTheClusterItIsPutUnder() {
        int clusterId = freshCluster();
        var station = stationRepo.create("Wache unter dem Träger");

        clusterRepo.setStationCluster(station.id(), clusterId);

        assertEquals(
                clusterId, service.findByStation(station.id()).orElseThrow().id());
        assertTrue(service.findStationIds(clusterId).contains(station.id()));

        // Released, it answers to nobody again
        clusterRepo.setStationCluster(station.id(), null);
        assertTrue(service.findByStation(station.id()).isEmpty());
        stationRepo.delete(station.id());
    }

    @Test
    void aUserTypeCarriesItsPermissionsWithoutAnythingBeingGranted() {
        int clusterId = freshCluster();
        var admin = service.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_ADMIN);

        var permissions = service.resolvePermissions(admin);
        assertTrue(permissions.contains(ClusterPermission.CLUSTER_ADMINISTRATOR));
        assertTrue(permissions.contains(ClusterPermission.CLUSTER_INVENTORY_EXCHANGE), "expanded, not just held");
        assertTrue(permissions.contains(ClusterPermission.USER), "LOGIN reaches USER through its children");

        service.removeMember(admin.id());
    }

    @Test
    void aPlainMemberHoldsNothingUntilSomethingIsGranted() {
        int clusterId = freshCluster();
        var member = service.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        assertTrue(service.resolvePermissions(member).isEmpty());

        service.grant(member.id(), ClusterPermission.CLUSTER_INVENTORY_MANAGER);

        var permissions = service.resolvePermissions(member);
        assertTrue(permissions.contains(ClusterPermission.CLUSTER_INVENTORY_MANAGER));
        assertTrue(permissions.contains(ClusterPermission.CLUSTER_INVENTORY_READ), "and everything it carries");
        assertFalse(permissions.contains(ClusterPermission.CLUSTER_STATIONS), "and nothing it does not");

        assertTrue(service.revoke(member.id(), ClusterPermission.CLUSTER_INVENTORY_MANAGER));
        assertTrue(service.resolvePermissions(member).isEmpty());
        service.removeMember(member.id());
    }

    @Test
    void anAccountBelongsToOneClusterOnlyOnce() {
        int clusterId = freshCluster();
        int accountId = freshAccount().id();
        var member = service.addMember(clusterId, accountId, ClusterUserType.CLUSTER_USER);

        assertThrows(
                BadRequestResponse.class, () -> service.addMember(clusterId, accountId, ClusterUserType.CLUSTER_USER));

        service.removeMember(member.id());
    }

    @Test
    void aClusterIsFoundByTheIdentityTheRequestHeaderCarries() {
        var cluster = service.create("Kreisverband Kennung", null);

        assertEquals(
                cluster.id(), service.findByUid(cluster.uid()).orElseThrow().id());
        assertTrue(service.findByUid(java.util.UUID.randomUUID()).isEmpty());
        assertTrue(service.findAll().stream().anyMatch(c -> c.id() == cluster.id()));
    }

    @Test
    void deletingAClusterTakesItsHomeStationWithIt() {
        var cluster = service.create("Kreisverband Kurzlebig", null);
        int homeId = cluster.homeStationId();

        assertTrue(service.delete(cluster.id()));

        assertTrue(service.findById(cluster.id()).isEmpty());
        assertTrue(stationRepo.findById(homeId).isEmpty(), "the shell goes with what owned it");
    }

    @Test
    void aClusterWithStationsIsNotDeletedOutFromUnderThem() {
        var cluster = service.create("Kreisverband Voll", null);
        var station = stationRepo.create("Wache im Verband");
        clusterRepo.setStationCluster(station.id(), cluster.id());

        var refused = assertThrows(BadRequestResponse.class, () -> service.delete(cluster.id()));
        assertTrue(refused.getMessage().contains("Release them first"));
        assertTrue(service.findById(cluster.id()).isPresent());

        clusterRepo.setStationCluster(station.id(), null);
        stationRepo.delete(station.id());
        service.delete(cluster.id());
    }

    @Test
    void aClusterThatIsNotThereCannotBeRenamedOrDeleted() {
        assertThrows(BadRequestResponse.class, () -> service.rename(999_999, "Egal", null));
        assertThrows(BadRequestResponse.class, () -> service.delete(999_999));
    }

    @Test
    void theIdentityOnTheWireIsResolvedFromTheInternalOne() {
        var cluster = service.create("Kreisverband Kennnummer", null);
        assertEquals(cluster.uid(), clusterRepo.resolveUid(cluster.id()));
        assertNull(clusterRepo.resolveUid(999_999));
    }

    /**
     * Every permission of the enum is seeded by the migration, so granting each in turn is what catches the
     * two drifting apart.
     */
    @Test
    void everyPermissionTheCodeKnowsCanActuallyBeGranted() {
        int clusterId = freshCluster();
        var member = service.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);

        for (ClusterPermission permission : ClusterPermission.values()) {
            service.grant(member.id(), permission);
        }

        assertTrue(service.resolvePermissions(member).contains(ClusterPermission.CLUSTER_ADMINISTRATOR));
        assertFalse(
                service.revoke(member.id(), ClusterPermission.CLUSTER_STORAGE)
                        && service.revoke(member.id(), ClusterPermission.CLUSTER_STORAGE),
                "revoking twice takes nothing the second time");
    }

    @Test
    void aClustersMembersAreListedForIt() {
        int clusterId = freshCluster();
        var first = service.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_ADMIN);
        var second = service.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);

        var members = service.findMembers(clusterId);
        assertEquals(2, members.size());
        assertTrue(members.stream().anyMatch(m -> m.id() == first.id()));
        assertTrue(members.stream().anyMatch(m -> m.id() == second.id()));
        assertTrue(service.findMember(clusterId, first.accountId()).isPresent());
    }

    /**
     * A database that has run a newer version carries permission rows this build has never heard of. They are
     * skipped rather than refusing the whole request, because a member holding one should not be locked out
     * by a rollback.
     */
    @Test
    void aPermissionTheCodeDoesNotKnowIsSkippedRatherThanFatal() {
        int clusterId = freshCluster();
        var member = service.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);

        int unknownId = insertUnknownPermission();
        clusterRepo.grantPermission(member.id(), unknownId);
        service.grant(member.id(), ClusterPermission.CLUSTER_FIELD_EDIT);

        var permissions = service.resolvePermissions(member);
        assertEquals(Set.of(ClusterPermission.CLUSTER_FIELD_EDIT), permissions);
    }

    private int insertUnknownPermission() {
        return query("INSERT INTO cluster_permission(name) VALUES (:name) RETURNING id;")
                .single(call().bind("name", "CLUSTER_FROM_THE_FUTURE_" + NAMES.incrementAndGet()))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    @Test
    void anAccountMayActForSeveralClusters() {
        int first = freshCluster();
        int second = freshCluster();
        int accountId = freshAccount().id();
        var a = service.addMember(first, accountId, ClusterUserType.CLUSTER_ADMIN);
        var b = service.addMember(second, accountId, ClusterUserType.CLUSTER_USER);

        var reachable = service.findClustersForAccount(accountId);
        assertTrue(reachable.stream().anyMatch(c -> c.id() == first));
        assertTrue(reachable.stream().anyMatch(c -> c.id() == second));

        service.removeMember(a.id());
        service.removeMember(b.id());
    }
}
