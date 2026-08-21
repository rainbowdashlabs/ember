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
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.*;

class ClusterServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private int freshCluster() {
        return clusterService
                .create("Kreisverband " + NAMES.incrementAndGet(), "Der Träger")
                .id();
    }

    /** An account of its own per case, so no test depends on another leaving one behind. */
    private Account freshAccount() {
        int n = NAMES.incrementAndGet();
        return accountRepo.create("cluster" + n + "@test.com", "Clus", "Ter" + n);
    }

    @Test
    void aClusterArrivesWithTheStationShellItOwns() {
        var cluster = clusterService.create("Kreisverband Nord", "Der Träger");

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
        assertThrows(BadRequestResponse.class, () -> clusterService.create("  ", null));
    }

    @Test
    void renamingACluster1KeepsItsHomeStationInStep() {
        var cluster = clusterService.create("Kreisverband Alt", null);

        assertTrue(clusterService.rename(cluster.id(), "Kreisverband Neu", "Umbenannt"));

        assertEquals(
                "Kreisverband Neu",
                clusterService.findById(cluster.id()).orElseThrow().name());
        assertEquals(
                "Kreisverband Neu",
                stationRepo.findById(cluster.homeStationId()).orElseThrow().name(),
                "federation reads a partner's label off the station row, so the two cannot drift");
    }

    @Test
    void theHomeStationIsNotOfferedAsSomethingToJoin() {
        var cluster = clusterService.create("Kreisverband Versteckt", null);

        assertTrue(stationRepo.findAll().stream().anyMatch(s -> s.id() == cluster.homeStationId()));
        assertFalse(
                stationRepo.findAllRegular().stream().anyMatch(s -> s.id() == cluster.homeStationId()),
                "the user-facing listing leaves the shell out");
    }

    @Test
    void aStationBelongsToTheClusterItIsPutUnder() {
        int clusterId = freshCluster();
        var station = stationRepo.create("Wache unter dem Träger");

        stationRepo.setCluster(station.id(), clusterId);

        assertEquals(
                clusterId,
                clusterService.findByStation(station.id()).orElseThrow().id());
        assertTrue(clusterService.findStationIds(clusterId).contains(station.id()));

        // Released, it answers to nobody again
        stationRepo.setCluster(station.id(), null);
        assertTrue(clusterService.findByStation(station.id()).isEmpty());
        stationRepo.delete(station.id());
    }

    @Test
    void aStationTheClusterMakesBelongsToItFromTheStart() {
        int clusterId = freshCluster();

        var station = clusterService.createStation(clusterId, "Löschzug Neu");

        assertEquals(clusterId, stationRepo.findById(station.id()).orElseThrow().clusterId());
        assertTrue(clusterService.findStations(clusterId).stream().anyMatch(s -> s.id() == station.id()));

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aHomeStationCannotBeTakenIntoAnotherCluster() {
        var first = clusterService.create("Kreisverband Eins", null);
        int otherClusterId = freshCluster();

        assertThrows(BadRequestResponse.class, () -> clusterService.joinStation(otherClusterId, first.homeStationId()));
    }

    @Test
    void aStationCannotBeTakenFromTheClusterItAlreadyAnswersTo() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Umkämpfte Wache");

        assertThrows(BadRequestResponse.class, () -> clusterService.joinStation(otherClusterId, station.id()));

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void releasingAStationThatWasNeverInTheClusterIsRefused() {
        int clusterId = freshCluster();
        var station = stationRepo.create("Freie Wache");

        assertThrows(BadRequestResponse.class, () -> clusterService.releaseStation(clusterId, station.id()));
        stationRepo.delete(station.id());
    }

    @Test
    void aReleasedStationGetsItsFreedomAndTheClusterItsGearBack() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache mit geliehenem Gerät");
        var inventory = inventoryRepo.create(station.id(), "Einsatzkleidung", InventoryType.EXTERNAL, false);
        var account = freshAccount();
        var member = stationMemberRepo.create(station.id(), account.id());
        var item = inventoryRepo.createItem(inventory.id(), "HK-1", "Helm", null, null, ItemOwner.CLUSTER, clusterId);
        itemCustodyService.assignToMember(item.id(), member.id(), "Wer auch immer");

        clusterService.releaseStation(clusterId, station.id());

        var recalled = inventoryRepo.findItemById(item.id()).orElseThrow();
        assertEquals(ItemCustody.WITH_OWNER, recalled.custody(), "the cluster has its gear back");
        assertNull(recalled.assignedTo(), "and nobody at the released station still holds it");
        assertNull(stationRepo.findById(station.id()).orElseThrow().clusterId());

        stationRepo.delete(station.id());
    }

    @Test
    void aUserTypeCarriesItsPermissionsWithoutAnythingBeingGranted() {
        int clusterId = freshCluster();
        var admin = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_ADMIN);

        var permissions = clusterService.resolvePermissions(admin);
        assertTrue(permissions.contains(ClusterPermission.CLUSTER_ADMINISTRATOR));
        assertTrue(permissions.contains(ClusterPermission.CLUSTER_INVENTORY_EXCHANGE), "expanded, not just held");
        assertTrue(permissions.contains(ClusterPermission.USER), "LOGIN reaches USER through its children");

        clusterService.removeMember(admin.id());
    }

    @Test
    void aPlainMemberHoldsNothingUntilSomethingIsGranted() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);

        // Being a member is itself worth something: it opens the cluster's own pages and nothing else,
        // the way belonging to a station opens that station's. Everything past that is granted.
        assertEquals(
                Set.of(ClusterPermission.LOGIN, ClusterPermission.USER),
                clusterService.resolvePermissions(member),
                "a member who has been granted nothing may still see the cluster they belong to");

        clusterService.grant(member.id(), ClusterPermission.CLUSTER_INVENTORY_MANAGER);

        var permissions = clusterService.resolvePermissions(member);
        assertTrue(permissions.contains(ClusterPermission.CLUSTER_INVENTORY_MANAGER));
        assertTrue(permissions.contains(ClusterPermission.CLUSTER_INVENTORY_READ), "and everything it carries");
        assertFalse(permissions.contains(ClusterPermission.CLUSTER_STATIONS), "and nothing it does not");

        assertTrue(clusterService.revoke(member.id(), ClusterPermission.CLUSTER_INVENTORY_MANAGER));
        assertEquals(
                Set.of(ClusterPermission.LOGIN, ClusterPermission.USER),
                clusterService.resolvePermissions(member),
                "taking a grant away leaves them a member and nothing more");
        clusterService.removeMember(member.id());
    }

    @Test
    void anAccountBelongsToOneClusterOnlyOnce() {
        int clusterId = freshCluster();
        int accountId = freshAccount().id();
        var member = clusterService.addMember(clusterId, accountId, ClusterUserType.CLUSTER_USER);

        assertThrows(
                BadRequestResponse.class,
                () -> clusterService.addMember(clusterId, accountId, ClusterUserType.CLUSTER_USER));

        clusterService.removeMember(member.id());
    }

    @Test
    void aClusterIsFoundByTheIdentityTheRequestHeaderCarries() {
        var cluster = clusterService.create("Kreisverband Kennung", null);

        assertEquals(
                cluster.id(),
                clusterService.findByUid(cluster.uid()).orElseThrow().id());
        assertTrue(clusterService.findByUid(java.util.UUID.randomUUID()).isEmpty());
        assertTrue(clusterService.findAll().stream().anyMatch(c -> c.id() == cluster.id()));
    }

    @Test
    void deletingAClusterTakesItsHomeStationWithIt() {
        var cluster = clusterService.create("Kreisverband Kurzlebig", null);
        int homeId = cluster.homeStationId();

        assertTrue(clusterService.delete(cluster.id()));

        assertTrue(clusterService.findById(cluster.id()).isEmpty());
        assertTrue(stationRepo.findById(homeId).isEmpty(), "the shell goes with what owned it");
    }

    @Test
    void aClusterWithStationsIsNotDeletedOutFromUnderThem() {
        var cluster = clusterService.create("Kreisverband Voll", null);
        var station = stationRepo.create("Wache im Verband");
        stationRepo.setCluster(station.id(), cluster.id());

        var refused = assertThrows(BadRequestResponse.class, () -> clusterService.delete(cluster.id()));
        assertTrue(refused.getMessage().contains("Release them first"));
        assertTrue(clusterService.findById(cluster.id()).isPresent());

        stationRepo.setCluster(station.id(), null);
        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    @Test
    void aClusterThatIsNotThereCannotBeRenamedOrDeleted() {
        assertThrows(BadRequestResponse.class, () -> clusterService.rename(999_999, "Egal", null));
        assertThrows(BadRequestResponse.class, () -> clusterService.delete(999_999));
    }

    @Test
    void theIdentityOnTheWireIsResolvedFromTheInternalOne() {
        var cluster = clusterService.create("Kreisverband Kennnummer", null);
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
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);

        for (ClusterPermission permission : ClusterPermission.values()) {
            clusterService.grant(member.id(), permission);
        }

        assertTrue(clusterService.resolvePermissions(member).contains(ClusterPermission.CLUSTER_ADMINISTRATOR));
        assertFalse(
                clusterService.revoke(member.id(), ClusterPermission.CLUSTER_STORAGE)
                        && clusterService.revoke(member.id(), ClusterPermission.CLUSTER_STORAGE),
                "revoking twice takes nothing the second time");
    }

    @Test
    void aClustersMembersAreListedForIt() {
        int clusterId = freshCluster();
        var first = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_ADMIN);
        var second = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);

        var members = clusterService.findMembers(clusterId);
        assertEquals(2, members.size());
        assertTrue(members.stream().anyMatch(m -> m.id() == first.id()));
        assertTrue(members.stream().anyMatch(m -> m.id() == second.id()));
        assertTrue(clusterService.findMember(clusterId, first.accountId()).isPresent());
    }

    /**
     * A database that has run a newer version carries permission rows this build has never heard of. They are
     * skipped rather than refusing the whole request, because a member holding one should not be locked out
     * by a rollback.
     */
    @Test
    void aPermissionTheCodeDoesNotKnowIsSkippedRatherThanFatal() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);

        int unknownId = insertUnknownPermission();
        clusterRepo.grantPermission(member.id(), unknownId);
        clusterService.grant(member.id(), ClusterPermission.CLUSTER_FIELD_EDIT);

        var permissions = clusterService.resolvePermissions(member);
        assertEquals(
                Set.of(ClusterPermission.LOGIN, ClusterPermission.USER, ClusterPermission.CLUSTER_FIELD_EDIT),
                permissions,
                "what the code knows, plus what belonging carries; the row it cannot read is passed over");
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
        var a = clusterService.addMember(first, accountId, ClusterUserType.CLUSTER_ADMIN);
        var b = clusterService.addMember(second, accountId, ClusterUserType.CLUSTER_USER);

        var reachable = clusterService.findClustersForAccount(accountId);
        assertTrue(reachable.stream().anyMatch(c -> c.id() == first));
        assertTrue(reachable.stream().anyMatch(c -> c.id() == second));

        clusterService.removeMember(a.id());
        clusterService.removeMember(b.id());
    }

    @Test
    void switchingAutoFederationOnFillsInTheMissingPairs() {
        var cluster = clusterService.create("Kreisverband Vernetzung " + NAMES.incrementAndGet(), null);
        var first = clusterService.createStation(cluster.id(), "Wache Netz A " + NAMES.incrementAndGet());
        var second = clusterService.createStation(cluster.id(), "Wache Netz B " + NAMES.incrementAndGet());

        clusterService.setAutoFederate(cluster.id(), false);
        assertFalse(clusterService.findById(cluster.id()).orElseThrow().autoFederate());

        clusterService.setAutoFederate(cluster.id(), true);
        assertTrue(clusterService.findById(cluster.id()).orElseThrow().autoFederate());

        clusterService.releaseStation(cluster.id(), first.id());
        clusterService.releaseStation(cluster.id(), second.id());
        stationRepo.delete(first.id());
        stationRepo.delete(second.id());
    }
}
