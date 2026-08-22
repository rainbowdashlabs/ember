/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cluster's own people and the three ways they come to hold a permission.
 */
class ClusterMemberServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ClusterMemberService service;

    @BeforeAll
    static void setup() {
        service = new ClusterMemberService(clusterRepo, clusterService, new DomainEventBus(Set.of()));
    }

    private int freshCluster() {
        return clusterService
                .create("Kreisverband Mitglieder " + NAMES.incrementAndGet(), null)
                .id();
    }

    private Account freshAccount() {
        int n = NAMES.incrementAndGet();
        return accountRepo.create("clustermember" + n + "@test.com", "Mit", "Glied" + n);
    }

    @Test
    void whatAMemberHoldsIsSplitByWhereItCameFrom() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_ADMIN);

        service.setPermissions(clusterId, member.id(), Set.of(ClusterPermission.CLUSTER_FIELD_EDIT));

        var detail = service.findMemberDetail(clusterId, member.id());
        assertEquals(Set.of(ClusterPermission.CLUSTER_FIELD_EDIT), detail.direct(), "only the grant by name");
        assertTrue(
                detail.resolved().contains(ClusterPermission.CLUSTER_ADMINISTRATOR),
                "the user type's own is in the resolved set");
        assertTrue(detail.resolved().contains(ClusterPermission.CLUSTER_FIELD_EDIT));
    }

    @Test
    void changingWhatSomebodyIsChangesWhatTheyHoldByDefault() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        assertFalse(service.findMemberDetail(clusterId, member.id())
                .resolved()
                .contains(ClusterPermission.CLUSTER_ADMINISTRATOR));

        service.setUserType(clusterId, member.id(), ClusterUserType.CLUSTER_ADMIN);

        assertTrue(service.findMemberDetail(clusterId, member.id())
                .resolved()
                .contains(ClusterPermission.CLUSTER_ADMINISTRATOR));
    }

    @Test
    void takingAGrantAwayLeavesWhatTheTypeCarries() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_ADMIN);
        service.setPermissions(clusterId, member.id(), Set.of(ClusterPermission.CLUSTER_FIELD_EDIT));

        service.setPermissions(clusterId, member.id(), Set.of());

        var detail = service.findMemberDetail(clusterId, member.id());
        assertTrue(detail.direct().isEmpty());
        assertTrue(
                detail.resolved().contains(ClusterPermission.CLUSTER_ADMINISTRATOR),
                "what the type carries is not this member's to lose");
    }

    @Test
    void aGroupIsTheThirdWayToHoldSomething() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(clusterId, "Gerätewarte");

        service.setGroupPermissions(clusterId, group.id(), Set.of(ClusterPermission.CLUSTER_INVENTORY_EDIT));
        service.setGroupMembers(clusterId, group.id(), Set.of(member.id()));

        var detail = service.findMemberDetail(clusterId, member.id());
        assertTrue(detail.direct().isEmpty(), "nothing was granted to them by name");
        assertTrue(detail.resolved().contains(ClusterPermission.CLUSTER_INVENTORY_EDIT));
        assertEquals(1, detail.groups().size());

        // And taking them out of the group takes it away again
        service.setGroupMembers(clusterId, group.id(), Set.of());
        assertFalse(service.findMemberDetail(clusterId, member.id())
                .resolved()
                .contains(ClusterPermission.CLUSTER_INVENTORY_EDIT));
    }

    /**
     * The same membership, written from the member's end.
     *
     * <p>Somebody looking at one person and somebody looking at one role are asking different questions, and
     * both screens exist. What they must not be is two different truths, so the one written here is read back
     * through the group.
     */
    @Test
    void groupsCanBeSetFromTheMemberSideToo() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var gear = service.createGroup(clusterId, "Gerät von der Mitgliedsseite");
        var people = service.createGroup(clusterId, "Mitglieder von der Mitgliedsseite");
        service.setGroupPermissions(clusterId, gear.id(), Set.of(ClusterPermission.CLUSTER_INVENTORY_EDIT));
        service.setGroupPermissions(clusterId, people.id(), Set.of(ClusterPermission.CLUSTER_MEMBER_EDIT));

        service.setMemberGroups(clusterId, member.id(), Set.of(gear.id(), people.id()));

        var detail = service.findMemberDetail(clusterId, member.id());
        assertEquals(2, detail.groups().size(), "both groups, read back from the member");
        assertTrue(detail.resolved().contains(ClusterPermission.CLUSTER_INVENTORY_EDIT));
        assertTrue(detail.resolved().contains(ClusterPermission.CLUSTER_MEMBER_EDIT));
        assertTrue(
                service.findGroupDetail(clusterId, gear.id()).memberIds().contains(member.id()),
                "and the group says so as well");

        // Narrowing to one takes the other away, and setting the same set again changes nothing
        service.setMemberGroups(clusterId, member.id(), Set.of(gear.id()));
        service.setMemberGroups(clusterId, member.id(), Set.of(gear.id()));

        var narrowed = service.findMemberDetail(clusterId, member.id());
        assertEquals(1, narrowed.groups().size());
        assertTrue(narrowed.resolved().contains(ClusterPermission.CLUSTER_INVENTORY_EDIT));
        assertFalse(narrowed.resolved().contains(ClusterPermission.CLUSTER_MEMBER_EDIT));
    }

    /** A group of somebody else's cluster is not a group this member can be put in. */
    @Test
    void aMemberCannotBePutInAnotherClustersGroup() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var elsewhere = service.createGroup(freshCluster(), "Fremde Gruppe");

        assertThrows(
                NotFoundResponse.class, () -> service.setMemberGroups(clusterId, member.id(), Set.of(elsewhere.id())));
    }

    @Test
    void aGroupCanBeRenamedAndRemoved() {
        int clusterId = freshCluster();
        var group = service.createGroup(clusterId, "Vorläufig");

        service.renameGroup(clusterId, group.id(), "Endgültig");
        assertEquals(
                "Endgültig",
                service.findGroupDetail(clusterId, group.id()).group().name());

        service.deleteGroup(clusterId, group.id());
        assertTrue(service.findGroups(clusterId).isEmpty());
    }

    @Test
    void aGroupNeedsAName() {
        int clusterId = freshCluster();

        assertThrows(BadRequestResponse.class, () -> service.createGroup(clusterId, "  "));
    }

    @Test
    void oneClusterCannotReachIntoAnothersPeopleOrGroups() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var member = clusterService.addMember(otherClusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(otherClusterId, "Fremd");

        assertThrows(NotFoundResponse.class, () -> service.findMemberDetail(clusterId, member.id()));
        assertThrows(NotFoundResponse.class, () -> service.findGroupDetail(clusterId, group.id()));
        assertThrows(
                NotFoundResponse.class,
                () -> service.setUserType(clusterId, member.id(), ClusterUserType.CLUSTER_ADMIN));
    }

    @Test
    void somebodyFromAnotherClusterCannotBePutIntoThisOnesGroup() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var stranger = clusterService.addMember(otherClusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(clusterId, "Eigene");

        assertThrows(
                NotFoundResponse.class, () -> service.setGroupMembers(clusterId, group.id(), Set.of(stranger.id())));
    }

    @Test
    void aGroupSaysWhatItCarriesAndWhoIsInIt() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(clusterId, "Auskunft");

        service.setGroupPermissions(clusterId, group.id(), Set.of(ClusterPermission.CLUSTER_NEWS_EDIT));
        service.setGroupMembers(clusterId, group.id(), Set.of(member.id()));

        var detail = service.findGroupDetail(clusterId, group.id());
        assertEquals(Set.of(ClusterPermission.CLUSTER_NEWS_EDIT), detail.permissions());
        assertEquals(List.of(member.id()), detail.memberIds());
        assertEquals(1, service.findGroups(clusterId).size());
        assertTrue(service.findMembers(clusterId).stream().anyMatch(m -> m.id() == member.id()));
    }

    @Test
    void settingTheSameThingTwiceChangesNothing() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(clusterId, "Ruhig");

        service.setUserType(clusterId, member.id(), ClusterUserType.CLUSTER_USER);
        service.setPermissions(clusterId, member.id(), Set.of());
        service.setGroupPermissions(clusterId, group.id(), Set.of());

        assertTrue(service.findMemberDetail(clusterId, member.id()).direct().isEmpty());
        assertTrue(service.findGroupDetail(clusterId, group.id()).permissions().isEmpty());
    }

    @Test
    void aGroupNeedsANameToBeRenamedTo() {
        int clusterId = freshCluster();
        var group = service.createGroup(clusterId, "Vorher");

        assertThrows(BadRequestResponse.class, () -> service.renameGroup(clusterId, group.id(), " "));
    }

    @Test
    void takingARightBackOffAGroupTakesItOffItsMembers() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(clusterId, "Wechselhaft");
        service.setGroupMembers(clusterId, group.id(), Set.of(member.id()));
        service.setGroupPermissions(clusterId, group.id(), Set.of(ClusterPermission.CLUSTER_NEWS_EDIT));

        service.setGroupPermissions(clusterId, group.id(), Set.of(ClusterPermission.CLUSTER_EVENT_EDIT));

        var detail = service.findMemberDetail(clusterId, member.id());
        assertFalse(detail.resolved().contains(ClusterPermission.CLUSTER_NEWS_EDIT), "the old one is gone");
        assertTrue(detail.resolved().contains(ClusterPermission.CLUSTER_EVENT_EDIT), "the new one is there");
    }

    /**
     * A name the code no longer knows is skipped rather than fatal, wherever it is read from. Rows like that
     * are what a removed permission leaves behind, and a cluster screen refusing to open because of one would
     * be worse than the row itself.
     */
    @Test
    void aPermissionNameTheCodeDoesNotKnowIsSkippedEverywhere() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(clusterId, "Veraltet");
        service.setGroupMembers(clusterId, group.id(), Set.of(member.id()));
        int ghostId = insertGhostPermission();

        query("INSERT INTO cluster_member_permission(member_id, permission_id) VALUES (:m, :p);")
                .single(call().bind("m", member.id()).bind("p", ghostId))
                .insert();
        query("INSERT INTO cluster_member_group_permission(group_id, permission_id) VALUES (:g, :p);")
                .single(call().bind("g", group.id()).bind("p", ghostId))
                .insert();

        assertTrue(service.findMemberDetail(clusterId, member.id()).direct().isEmpty());
        assertTrue(service.findGroupDetail(clusterId, group.id()).permissions().isEmpty());
    }

    /** A permission row whose name no enum constant matches, which is what a removal leaves behind. */
    private int insertGhostPermission() {
        return query("""
                INSERT INTO cluster_permission(name) VALUES (:name)
                ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                RETURNING id;""")
                .single(call().bind("name", "CLUSTER_PERMISSION_THAT_NO_LONGER_EXISTS"))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }
}
