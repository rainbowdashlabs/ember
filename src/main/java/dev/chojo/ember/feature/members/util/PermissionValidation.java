/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.util;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.Permission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Utility class for validating permission assignment changes,
 * enforcing caller authorization checks.
 */
public final class PermissionValidation {
    private PermissionValidation() {}

    /**
     * The first of the permissions a grant would hand out that the caller does not hold, which is the
     * one reason to refuse it. The same rule as for a direct grant: nobody hands out more than they have.
     *
     * @param granted           what the grant would hand out
     * @param callerPermissions what the caller holds
     * @return a permission the caller lacks, or empty where they hold them all
     */
    public static Optional<StationPermission> firstNotHeld(
            Collection<Permission> granted, Set<StationPermission> callerPermissions) {
        return granted.stream()
                .map(Permission::permission)
                .filter(permission -> !holds(callerPermissions, permission))
                .findFirst();
    }

    private static boolean holds(Set<StationPermission> callerPermissions, StationPermission permission) {
        return callerPermissions.contains(permission)
                || callerPermissions.stream().anyMatch(held -> held.includes(permission));
    }

    /**
     * Validates permission changes for both member and group permission assignments.
     * Enforces that the caller can only grant permissions they themselves have.
     */
    public static void validatePermissionChanges(
            List<Permission> currentPermissions,
            List<Integer> desiredPermissionIds,
            List<Permission> allPermissions,
            Set<StationPermission> callerPermissions) {
        var currentIds = currentPermissions.stream().map(Permission::id).toList();

        for (int permissionId : desiredPermissionIds) {
            Permission perm = allPermissions.stream()
                    .filter(p -> p.id() == permissionId)
                    .findFirst()
                    .orElseThrow(() -> MemberRefusal.MEMBER_PERMISSION_UNKNOWN.raise(String.valueOf(permissionId)));

            if (!currentIds.contains(permissionId) && !callerPermissions.contains(perm.permission())) {
                throw MemberRefusal.MEMBER_PERMISSION_NOT_YOURS_TO_GRANT.raise();
            }
        }
    }
}
