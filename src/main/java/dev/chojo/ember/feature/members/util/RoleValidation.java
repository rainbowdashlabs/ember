/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.util;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.members.entity.Role;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for validating role assignment changes, enforcing protected role rules
 * and caller authorization checks.
 */
public final class RoleValidation {
    private RoleValidation() {}

    /**
     * Validates role changes for both member and group role assignments.
     * <p>
     * Enforces:
     * <ul>
     *     <li>Protected roles (MEMBER_MANAGER, MANAGER) cannot be removed</li>
     *     <li>Station owner's MANAGER role can never be removed</li>
     *     <li>Caller can only grant roles they themselves have</li>
     * </ul>
     */
    public static void validateRoleChanges(
            List<Role> currentRoles,
            List<Integer> desiredRoleIds,
            List<Role> allRoles,
            Set<Roles> callerRoles,
            boolean isStationOwner) {
        // Check protected roles are not being removed
        for (Role current : currentRoles) {
            Roles mapped = current.role();
            if (mapped == null || desiredRoleIds.contains(current.id())) continue;
            if (mapped == Roles.MANAGER && isStationOwner) {
                throw new ForbiddenResponse("Cannot remove MANAGER role from the station owner");
            }
            if (Roles.PROTECTED_ROLES.contains(mapped)) {
                throw new ForbiddenResponse("Cannot remove protected role: " + mapped.name());
            }
        }

        // Check for conflicting roles
        validateNoConflicts(desiredRoleIds, allRoles);

        // Check caller has all roles they are trying to grant
        for (int roleId : desiredRoleIds) {
            Role role = allRoles.stream()
                    .filter(r -> r.id() == roleId)
                    .findFirst()
                    .orElseThrow(() -> new BadRequestResponse("Unknown role ID: " + roleId));

            Roles mapped = role.role();

            // Only check newly added roles - existing roles are fine
            if (!isCurrentRole(currentRoles, roleId) && !callerRoles.contains(mapped)) {
                throw new ForbiddenResponse("Cannot grant role you do not have: " + mapped.name());
            }
        }
    }

    private static void validateNoConflicts(List<Integer> desiredRoleIds, List<Role> allRoles) {
        var desiredRoles = desiredRoleIds.stream()
                .map(id ->
                        allRoles.stream().filter(r -> r.id() == id).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .map(Role::role)
                .filter(Objects::nonNull)
                .toList();
        for (var role : desiredRoles) {
            var conflicts = Roles.CONFLICTING_ROLES.getOrDefault(role, Set.of());
            for (var other : desiredRoles) {
                if (conflicts.contains(other)) {
                    throw new BadRequestResponse("Conflicting roles: " + role.name() + " and " + other.name()
                            + " cannot be assigned together");
                }
            }
        }
    }

    private static boolean isCurrentRole(List<Role> currentRoles, int roleId) {
        return currentRoles.stream().anyMatch(r -> r.id() == roleId);
    }
}
