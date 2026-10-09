/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterMemberRoleChanged;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AccountNameRequiredException;
import dev.chojo.ember.feature.accountlink.entity.LinkStatus;
import dev.chojo.ember.feature.accountlink.service.AssociationLinkService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The cluster's own people and the three ways they come to hold a permission.
 */
class ClusterMemberServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ClusterMemberService service;

    @BeforeAll
    static void setup() {
        service = clusterMemberService;
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

    /**
     * The association is the one body that could not take on somebody Ember had never seen, on the
     * reasoning that a cluster member is an account and not a station member. That was true of the
     * membership and never a reason to refuse the account.
     */
    @Test
    void anAddressNobodyHasYetIsRefusedUntilANameComesWithIt() {
        int clusterId = freshCluster();
        int n = NAMES.incrementAndGet();
        String address = "neuimverband" + n + "@test.com";

        var refused = assertThrows(
                AccountNameRequiredException.class,
                () -> service.addByEmail(clusterId, address, ClusterUserType.CLUSTER_USER, null, null));
        assertTrue(refused.getMessage().contains("first and last name"));
        assertTrue(accountRepo.findByEmail(address).isEmpty(), "and nothing was made in the meantime");

        var added = service.addByEmail(clusterId, address, ClusterUserType.CLUSTER_USER, "Erika", "Neu" + n);
        assertTrue(accountRepo.findByEmail(address).isPresent(), "the account exists afterwards");
        var member = assertInstanceOf(ClusterMemberService.Added.class, added).member();
        assertEquals(ClusterUserType.CLUSTER_USER, member.userType());
    }

    /**
     * An address that already has an account belongs to a person who has not agreed to act for the
     * association. They are asked, and until they accept the association has no member for the account.
     */
    @Test
    void anAddressWithAnAccountIsAskedInsteadOfTakenOn() {
        int clusterId = freshCluster();
        var known = freshAccount();

        var added = service.addByEmail(clusterId, known.email(), ClusterUserType.CLUSTER_ADMIN, null, null);

        var request = assertInstanceOf(ClusterMemberService.Asked.class, added).request();
        assertEquals(LinkStatus.WAITING, request.status());
        assertEquals(ClusterUserType.CLUSTER_ADMIN, request.role());
        assertEquals(known.email(), request.address());
        assertTrue(clusterRepo.findMember(clusterId, known.id()).isEmpty(), "no membership before the answer");

        var again = assertThrows(
                RefusalResponse.class,
                () -> service.addByEmail(clusterId, known.email(), ClusterUserType.CLUSTER_USER, null, null));
        assertEquals(ClusterRefusal.CLUSTER_LINK_ALREADY_WAITING, again.refusal());
    }

    @Test
    void anEmptyAddressIsNotAnInvitation() {
        int clusterId = freshCluster();
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.addByEmail(clusterId, "  ", ClusterUserType.CLUSTER_USER, "Erika", "Leer"));
        assertEquals(ClusterRefusal.CLUSTER_MEMBER_ADDRESS_MISSING, refused.refusal());
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

        service.setGroupMembers(clusterId, group.id(), Set.of());
        assertFalse(
                service.findMemberDetail(clusterId, member.id())
                        .resolved()
                        .contains(ClusterPermission.CLUSTER_INVENTORY_EDIT),
                "leaving the group takes it away again");
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
                RefusalResponse.class, () -> service.setMemberGroups(clusterId, member.id(), Set.of(elsewhere.id())));
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

    /**
     * Closing a group takes what it carried from everybody in it, which is as much a change of their
     * standing as being taken out of it one by one, and they hear about it the same way.
     */
    @Test
    void closingAGroupTellsEverybodyWhoWasInIt() {
        int clusterId = freshCluster();
        var bus = mock(DomainEventBus.class);
        var watched = new ClusterMemberService(
                clusterRepo,
                clusterService,
                accountRepo,
                mock(AccountInviteService.class),
                mock(AssociationLinkService.class),
                bus);
        var first = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var second = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(clusterId, "Aufgelöst");
        service.setGroupMembers(clusterId, group.id(), Set.of(first.id(), second.id()));
        String clusterName = clusterRepo.findById(clusterId).orElseThrow().name();

        watched.deleteGroup(clusterId, group.id());

        verify(bus).publish(new ClusterMemberRoleChanged(first.id(), clusterName));
        verify(bus).publish(new ClusterMemberRoleChanged(second.id(), clusterName));
        assertTrue(service.findGroups(clusterId).isEmpty());
    }

    /** A list naming somebody of another cluster puts nobody in, not the ones named before them. */
    @Test
    void aRefusedMembershipWritesNobody() {
        int clusterId = freshCluster();
        var member = clusterService.addMember(clusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var stranger = clusterService.addMember(freshCluster(), freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(clusterId, "Ganz oder gar nicht");

        assertThrows(
                RefusalResponse.class,
                () -> service.setGroupMembers(
                        clusterId, group.id(), new LinkedHashSet<>(List.of(member.id(), stranger.id()))));

        assertTrue(service.findGroupDetail(clusterId, group.id()).memberIds().isEmpty());
    }

    @Test
    void aGroupNameIsTrimmedAndTakenWhateverTheCase() {
        int clusterId = freshCluster();
        var group = service.createGroup(clusterId, "  Vorstand ");
        assertEquals("Vorstand", group.name());

        var refused = assertThrows(RefusalResponse.class, () -> service.createGroup(clusterId, "VORSTAND"));
        assertEquals(ClusterRefusal.CLUSTER_MEMBER_GROUP_NAME_TAKEN_ON_CREATE, refused.refusal());
        service.renameGroup(clusterId, group.id(), "vorstand");
        assertEquals(
                "vorstand",
                service.findGroupDetail(clusterId, group.id()).group().name());
    }

    @Test
    void aGroupNeedsAName() {
        int clusterId = freshCluster();

        var refused = assertThrows(RefusalResponse.class, () -> service.createGroup(clusterId, "  "));
        assertEquals(ClusterRefusal.CLUSTER_MEMBER_GROUP_NAME_MISSING_ON_CREATE, refused.refusal());
    }

    @Test
    void oneClusterCannotReachIntoAnothersPeopleOrGroups() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var member = clusterService.addMember(otherClusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(otherClusterId, "Fremd");

        assertThrows(RefusalResponse.class, () -> service.findMemberDetail(clusterId, member.id()));
        assertThrows(RefusalResponse.class, () -> service.findGroupDetail(clusterId, group.id()));
        assertThrows(
                RefusalResponse.class,
                () -> service.setUserType(clusterId, member.id(), ClusterUserType.CLUSTER_ADMIN));
    }

    @Test
    void somebodyFromAnotherClusterCannotBePutIntoThisOnesGroup() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var stranger = clusterService.addMember(otherClusterId, freshAccount().id(), ClusterUserType.CLUSTER_USER);
        var group = service.createGroup(clusterId, "Eigene");

        assertThrows(
                RefusalResponse.class, () -> service.setGroupMembers(clusterId, group.id(), Set.of(stranger.id())));
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

        var refused = assertThrows(RefusalResponse.class, () -> service.renameGroup(clusterId, group.id(), " "));
        assertEquals(ClusterRefusal.CLUSTER_MEMBER_GROUP_NAME_MISSING_ON_CHANGE, refused.refusal());
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
