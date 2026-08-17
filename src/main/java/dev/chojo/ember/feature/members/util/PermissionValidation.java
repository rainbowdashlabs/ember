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

import java.util.List;
import java.util.Set;

/**
 * Utility class for validating permission assignment changes,
 * enforcing caller authorization checks.
 */
public final class PermissionValidation {
    private PermissionValidation() {}

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
                    .orElseThrow(() -> new BadRequestResponse("Unknown permission ID: " + permissionId));

            // Only check newly added permissions - existing are fine
            if (!currentIds.contains(permissionId) && !callerPermissions.contains(perm.permission())) {
                throw new ForbiddenResponse("Cannot grant permission you do not have: " + perm.permission());
            }
        }
    }
}
