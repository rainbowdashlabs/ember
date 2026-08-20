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
}
