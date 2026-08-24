/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class PermissionsTest {

    @Test
    void stationAdministratorIncludesLoginWhenExpanded() {
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.STATION_ADMINISTRATOR));
        assertTrue(expanded.contains(StationPermission.LOGIN), "STATION_ADMINISTRATOR should include LOGIN");
    }

    @Test
    void stationAdministratorIncludesAllManagementPermissions() {
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.STATION_ADMINISTRATOR));
        assertTrue(expanded.contains(StationPermission.ATTENDANCE_MANAGER));
        assertTrue(expanded.contains(StationPermission.ATTENDANCE_EXPORT));
        assertTrue(expanded.contains(StationPermission.INVENTORY_MANAGER));
        assertTrue(expanded.contains(StationPermission.EVENT_MANAGER));
        assertTrue(expanded.contains(StationPermission.MEMBER_MANAGER));
        assertTrue(expanded.contains(StationPermission.NEWS_MANAGER));
        assertTrue(expanded.contains(StationPermission.POLL_MANAGER));
        assertTrue(expanded.contains(StationPermission.LOST_AND_FOUND_MANAGER));
        assertTrue(expanded.contains(StationPermission.WAITLIST_MANAGER));
        assertTrue(expanded.contains(StationPermission.TEST_MANAGER));
        assertTrue(expanded.contains(StationPermission.KNOWLEDGE_MANAGER));
        assertTrue(expanded.contains(StationPermission.PROTOCOL_MANAGER));
        assertTrue(expanded.contains(StationPermission.PROTOCOL_TESTER));
        assertTrue(expanded.contains(StationPermission.STATION_FEDERATION));
    }

    @Test
    void stationAdministratorIncludesTransitiveChildren() {
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.STATION_ADMINISTRATOR));
        // STATION_ADMINISTRATOR -> STATION_MANAGER -> includes LOGIN via hierarchy
        assertTrue(expanded.contains(StationPermission.LOGIN));
        assertTrue(expanded.contains(StationPermission.USER));
    }

    @Test
    void managementPermissionsDoNotIncludeLogin() {
        // Individual management permissions should not include LOGIN
        for (var perm : new StationPermission[] {
            StationPermission.ATTENDANCE_MANAGER, StationPermission.INVENTORY_MANAGER,
            StationPermission.EVENT_MANAGER, StationPermission.MEMBER_MANAGER
        }) {
            Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(perm));
            assertFalse(expanded.contains(StationPermission.LOGIN), perm + " should NOT include LOGIN");
        }
    }

    @Test
    void loginIncludesUser() {
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.LOGIN));
        assertTrue(expanded.contains(StationPermission.USER));
    }

    @Test
    void memberGuardianDoesNotIncludeLogin() {
        // MEMBER_GUARDIAN is a standalone permission, not a child of LOGIN
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.MEMBER_GUARDIAN));
        assertFalse(expanded.contains(StationPermission.LOGIN));
    }

    @Test
    void userExpandsToOnlyItself() {
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.USER));
        assertTrue(expanded.contains(StationPermission.USER));
        assertEquals(1, expanded.size(), "USER should only contain itself");
    }

    /**
     * Every permission has to answer with all of its children, whoever asks and whenever they ask.
     * The answer used to be gathered into the very set that was handed out, so anybody asking
     * during that moment was told a manager holds two of their seven rights, and the session built
     * from it looked exactly like rights that had been taken away.
     */
    @Test
    void everyPermissionAnswersTheSameUnderManyThreadsAtOnce() throws Exception {
        var expected = new EnumMap<StationPermission, Set<StationPermission>>(StationPermission.class);
        for (StationPermission permission : StationPermission.values()) {
            expected.put(permission, childrenOf(permission));
        }

        int threads = 32;
        var start = new CountDownLatch(1);
        var wrong = new ConcurrentLinkedQueue<String>();
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (StationPermission permission : StationPermission.values()) {
                        Set<StationPermission> answer = Set.copyOf(permission.allChildren());
                        if (!answer.equals(expected.get(permission))) {
                            wrong.add(permission + " answered " + answer);
                        }
                    }
                });
            }
            start.countDown();
        }

        assertTrue(wrong.isEmpty(), "half-built answers: " + wrong);
    }

    /** What a permission's children are, worked out without asking the cache being tested. */
    private static Set<StationPermission> childrenOf(StationPermission permission) {
        Set<StationPermission> collected = EnumSet.noneOf(StationPermission.class);
        for (StationPermission child : permission.getChildren()) {
            collected.add(child);
            collected.addAll(childrenOf(child));
        }
        return collected;
    }
    /**
     * What a cluster's rights come to at the station the cluster owns.
     *
     * <p>The two jobs are different ones: running an association is not running a station. Only the rights
     * over what the association writes cross over, and they cross over only at that one station.
     */
    @Test
    void clusterContentRightsBecomeStationContentRightsAtItsOwnStation() {
        Set<StationPermission> granted = ClusterPermission.atOwnStation(
                ClusterPermission.expand(EnumSet.of(ClusterPermission.CLUSTER_KNOWLEDGE_MANAGER)));

        assertTrue(granted.contains(StationPermission.KNOWLEDGE_MANAGER), "may run the knowledge base there");
        assertTrue(granted.contains(StationPermission.KNOWLEDGE_EDIT), "which carries writing in it");
        assertTrue(granted.contains(StationPermission.USER), "and may see the station at all");
        assertFalse(granted.contains(StationPermission.MEMBER_MANAGER), "but not its people");
        assertFalse(
                granted.contains(StationPermission.INVENTORY_EDIT),
                "and not its gear, which is a different trust from a different right");
    }

    /**
     * The association's gear is kept on the station it owns, so the rights over it have to arrive there.
     *
     * <p>Read alone reaches no further than reading. Editing carries creating in it, which is what makes
     * defining gear from the association's own screens possible at all.
     */
    @Test
    void clusterGearRightsBecomeStationGearRightsAtItsOwnStation() {
        Set<StationPermission> reader = ClusterPermission.atOwnStation(
                ClusterPermission.expand(EnumSet.of(ClusterPermission.CLUSTER_INVENTORY_READ)));

        assertTrue(reader.contains(StationPermission.INVENTORY_READ), "may see the gear");
        assertFalse(reader.contains(StationPermission.INVENTORY_EDIT), "but not change it");

        Set<StationPermission> editor = ClusterPermission.atOwnStation(
                ClusterPermission.expand(EnumSet.of(ClusterPermission.CLUSTER_INVENTORY_EDIT)));

        assertTrue(editor.contains(StationPermission.INVENTORY_EDIT), "may change it");
        assertTrue(editor.contains(StationPermission.INVENTORY_CREATE), "which carries creating in it");
    }

    /** Moving gear is one right at the association and two at a station, because a station splits them. */
    @Test
    void theRightToMoveGearReachesBothAssigningAndStorage() {
        Set<StationPermission> granted = ClusterPermission.atOwnStation(
                ClusterPermission.expand(EnumSet.of(ClusterPermission.CLUSTER_INVENTORY_TRANSFER)));

        assertTrue(granted.contains(StationPermission.INVENTORY_ASSIGN));
        assertTrue(granted.contains(StationPermission.INVENTORY_STORAGE));
        assertFalse(granted.contains(StationPermission.INVENTORY_EDIT), "moving is not describing");
    }

    /**
     * Looking after the association's gear reaches everything a station's own gear manager may do.
     *
     * <p>Creating and the manager role itself were withheld once, on the reasoning that the manager role
     * carries lending. It also carries defining a kind of gear and throwing one away, so the association
     * could do neither and the screen that says it defines its gear opened with a refusal. Nobody else
     * stands on this station, so there is nothing here to keep from its owner.
     */
    @Test
    void aClusterGearManagerGetsTheWholeStationRightOverItsOwnStore() {
        Set<StationPermission> granted = ClusterPermission.atOwnStation(
                ClusterPermission.expand(EnumSet.of(ClusterPermission.CLUSTER_INVENTORY_MANAGER)));

        assertTrue(granted.contains(StationPermission.INVENTORY_EDIT));
        assertTrue(granted.contains(StationPermission.INVENTORY_ASSIGN));
        assertTrue(granted.contains(StationPermission.INVENTORY_CHECK));
        assertTrue(granted.contains(StationPermission.INVENTORY_EXCHANGE));
        assertTrue(granted.contains(StationPermission.INVENTORY_PROCUREMENT));
        assertTrue(granted.contains(StationPermission.INVENTORY_STORAGE));

        assertTrue(granted.contains(StationPermission.INVENTORY_CREATE), "may define a kind of gear");
        assertTrue(granted.contains(StationPermission.INVENTORY_MANAGER), "and throw one away again");
    }

    /** Running the whole association still does not mean running a station. */
    @Test
    void aClusterAdministratorIsNotAStationAdministrator() {
        Set<StationPermission> granted = ClusterPermission.atOwnStation(
                ClusterPermission.expand(EnumSet.of(ClusterPermission.CLUSTER_ADMINISTRATOR)));

        assertFalse(granted.contains(StationPermission.STATION_ADMINISTRATOR));
        assertFalse(granted.contains(StationPermission.STATION_GENERAL));
        assertTrue(granted.contains(StationPermission.NEWS_MANAGER), "the content rights do cross over");
        assertTrue(granted.contains(StationPermission.EVENT_MANAGER));
        assertTrue(granted.contains(StationPermission.KNOWLEDGE_MANAGER));
    }

    /** Somebody who holds nothing at the association holds nothing at its station either. */
    @Test
    void holdingNothingAtAClusterGrantsNothingAtItsStation() {
        assertTrue(ClusterPermission.atOwnStation(EnumSet.noneOf(ClusterPermission.class))
                .isEmpty());
    }
}
