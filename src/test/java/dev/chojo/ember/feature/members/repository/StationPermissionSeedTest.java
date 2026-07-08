/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the {@link StationPermission} enum against drifting from the {@code station_permission}
 * seed data. A permission that exists in the enum but has no seeded row renders in the permission
 * picker yet silently cannot be granted — this has slipped through repeatedly (PAGE_EDIT and
 * PAGE_MANAGER backfilled in patch 12; INVENTORY_ASSIGN, INVENTORY_STORAGE, PAGE_FORMS_VIEW,
 * PAGE_POLLS_VIEW and the CHECKLIST permissions backfilled in patch 26). When this test fails,
 * add the missing names to the newest unreleased database patch. The reverse direction — a seeded
 * row without an enum constant — already fails here too, because {@link Permission#map()} parses
 * the row name into the enum.
 */
class StationPermissionSeedTest extends RepositoryTestBase {

    @Test
    void everyPermissionIsSeeded() {
        Set<StationPermission> seeded = stationMemberRepo.findAllPermissions().stream()
                .map(Permission::permission)
                .collect(Collectors.toSet());
        Set<StationPermission> missing = Arrays.stream(StationPermission.values())
                .filter(permission -> !seeded.contains(permission))
                .collect(Collectors.toSet());
        assertEquals(
                Set.of(),
                missing,
                "StationPermission values without a station_permission seed row — add them to the newest unreleased database patch");
    }
}
