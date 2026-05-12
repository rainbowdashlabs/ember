/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.entity.Role;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;

import java.util.List;
import java.util.Set;

final class RoleValidation {
    private RoleValidation() {}

    /**
     * Validates role changes for both member and group role assignments.
     * <p>
     * Enforces:
     * <ul>
     *     <li>Protected roles (MEMBER_MANAGEMENT, MANAGER) cannot be removed</li>
     *     <li>Caller can only grant roles they themselves have</li>
     * </ul>
     */
    static void validateRoleChanges(
            List<Role> currentRoles, List<Integer> desiredRoleIds, List<Role> allRoles, Set<Roles> callerRoles) {
        // Check protected roles are not being removed
        for (Role current : currentRoles) {
            Roles mapped = current.role();
            if (mapped != null && Roles.PROTECTED_ROLES.contains(mapped) && !desiredRoleIds.contains(current.id())) {
                throw new ForbiddenResponse("Cannot remove protected role: " + mapped.name());
            }
        }

        // Check caller has all roles they are trying to grant
        for (int roleId : desiredRoleIds) {
            Role role = allRoles.stream()
                    .filter(r -> r.id() == roleId)
                    .findFirst()
                    .orElseThrow(() -> new BadRequestResponse("Unknown role ID: " + roleId));

            Roles mapped = role.role();
            if (mapped == null) {
                throw new BadRequestResponse("Unknown role: " + role.role());
            }

            // Only check newly added roles - existing roles are fine
            if (!isCurrentRole(currentRoles, roleId) && !callerRoles.contains(mapped)) {
                throw new ForbiddenResponse("Cannot grant role you do not have: " + mapped.name());
            }
        }
    }

    private static boolean isCurrentRole(List<Role> currentRoles, int roleId) {
        return currentRoles.stream().anyMatch(r -> r.id() == roleId);
    }
}
