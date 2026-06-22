/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.util;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.Permission;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PermissionValidationTest {

    // Test permission fixtures matching DB IDs
    static final Permission LOGIN = new Permission(1, StationPermission.LOGIN);
    static final Permission USER = new Permission(2, StationPermission.USER);
    static final Permission MEMBER_GUARDIAN = new Permission(3, StationPermission.MEMBER_GUARDIAN);
    static final Permission MEMBER_MANAGER = new Permission(9, StationPermission.MEMBER_MANAGER);
    static final Permission STATION_ADMINISTRATOR = new Permission(13, StationPermission.STATION_ADMINISTRATOR);
    static final Permission INVENTORY_MANAGER = new Permission(7, StationPermission.INVENTORY_MANAGER);

    static final List<Permission> ALL_PERMISSIONS =
            List.of(LOGIN, USER, MEMBER_GUARDIAN, MEMBER_MANAGER, STATION_ADMINISTRATOR, INVENTORY_MANAGER);

    // --- validatePermissionChanges ---

    @Test
    void allowsValidPermissionAssignment() {
        List<Permission> current = List.of(LOGIN);
        List<Integer> desired = List.of(LOGIN.id(), INVENTORY_MANAGER.id());
        Set<StationPermission> callerPermissions = EnumSet.of(
                StationPermission.STATION_ADMINISTRATOR, StationPermission.INVENTORY_MANAGER, StationPermission.LOGIN);

        assertDoesNotThrow(() ->
                PermissionValidation.validatePermissionChanges(current, desired, ALL_PERMISSIONS, callerPermissions));
    }

    @Test
    void rejectsGrantingPermissionCallerDoesNotHave() {
        List<Permission> current = List.of(LOGIN);
        List<Integer> desired = List.of(LOGIN.id(), STATION_ADMINISTRATOR.id());
        Set<StationPermission> callerPermissions = EnumSet.of(StationPermission.MEMBER_MANAGER);

        assertThrows(
                ForbiddenResponse.class,
                () -> PermissionValidation.validatePermissionChanges(
                        current, desired, ALL_PERMISSIONS, callerPermissions));
    }

    @Test
    void allowsKeepingExistingPermissionEvenWithoutCallerHavingIt() {
        List<Permission> current = List.of(LOGIN, STATION_ADMINISTRATOR);
        List<Integer> desired = List.of(LOGIN.id(), STATION_ADMINISTRATOR.id()); // no change
        Set<StationPermission> callerPermissions = EnumSet.of(StationPermission.MEMBER_MANAGER);

        assertDoesNotThrow(() ->
                PermissionValidation.validatePermissionChanges(current, desired, ALL_PERMISSIONS, callerPermissions));
    }

    @Test
    void rejectsUnknownPermissionId() {
        List<Permission> current = List.of(LOGIN);
        List<Integer> desired = List.of(LOGIN.id(), 999);
        Set<StationPermission> callerPermissions = EnumSet.of(StationPermission.STATION_ADMINISTRATOR);

        assertThrows(
                BadRequestResponse.class,
                () -> PermissionValidation.validatePermissionChanges(
                        current, desired, ALL_PERMISSIONS, callerPermissions));
    }

    @Test
    void allowsRemovingPermission() {
        List<Permission> current = List.of(LOGIN, INVENTORY_MANAGER);
        List<Integer> desired = List.of(LOGIN.id()); // removing INVENTORY_MANAGER
        Set<StationPermission> callerPermissions = EnumSet.of(StationPermission.LOGIN);

        // Removing a permission is always allowed (no authorization check on removal)
        assertDoesNotThrow(() ->
                PermissionValidation.validatePermissionChanges(current, desired, ALL_PERMISSIONS, callerPermissions));
    }

    @Test
    void allowsGuardianAndLoginTogether() {
        List<Permission> current = List.of();
        List<Integer> desired = List.of(MEMBER_GUARDIAN.id(), LOGIN.id());
        Set<StationPermission> callerPermissions = EnumSet.of(
                StationPermission.STATION_ADMINISTRATOR, StationPermission.MEMBER_GUARDIAN, StationPermission.LOGIN);

        assertDoesNotThrow(() ->
                PermissionValidation.validatePermissionChanges(current, desired, ALL_PERMISSIONS, callerPermissions));
    }
}
