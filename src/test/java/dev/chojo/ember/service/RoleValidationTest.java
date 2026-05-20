/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.util.RoleValidation;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoleValidationTest {

    // Test role fixtures matching DB IDs
    static final Role LOGIN = new Role(1, Roles.LOGIN);
    static final Role MEMBER = new Role(2, Roles.MEMBER);
    static final Role GUARDIAN = new Role(3, Roles.GUARDIAN);
    static final Role TEAM = new Role(4, Roles.TEAM);
    static final Role MEMBER_MANAGEMENT = new Role(9, Roles.MEMBER_MANAGEMENT);
    static final Role MANAGER = new Role(13, Roles.MANAGER);
    static final Role ADMIN = new Role(14, Roles.ADMIN);
    static final Role INVENTORY_MANAGEMENT = new Role(7, Roles.INVENTORY_MANAGEMENT);

    static final List<Role> ALL_ROLES =
            List.of(LOGIN, MEMBER, GUARDIAN, TEAM, MEMBER_MANAGEMENT, MANAGER, ADMIN, INVENTORY_MANAGEMENT);

    // --- validateRoleChanges ---

    @Test
    void allowsValidRoleAssignment() {
        List<Role> current = List.of(LOGIN, TEAM);
        List<Integer> desired = List.of(LOGIN.id(), TEAM.id(), INVENTORY_MANAGEMENT.id());
        Set<Roles> callerRoles = EnumSet.of(Roles.MANAGER, Roles.TEAM, Roles.INVENTORY_MANAGEMENT);

        assertDoesNotThrow(() -> RoleValidation.validateRoleChanges(current, desired, ALL_ROLES, callerRoles, false));
    }

    @Test
    void rejectsRemovingProtectedRole() {
        List<Role> current = List.of(LOGIN, MEMBER_MANAGEMENT);
        List<Integer> desired = List.of(LOGIN.id()); // removing MEMBER_MANAGEMENT
        Set<Roles> callerRoles = EnumSet.of(Roles.ADMIN);

        assertThrows(
                ForbiddenResponse.class,
                () -> RoleValidation.validateRoleChanges(current, desired, ALL_ROLES, callerRoles, false));
    }

    @Test
    void rejectsRemovingManagerFromOwner() {
        List<Role> current = List.of(LOGIN, MANAGER);
        List<Integer> desired = List.of(LOGIN.id()); // removing MANAGER from owner
        Set<Roles> callerRoles = EnumSet.of(Roles.ADMIN);

        assertThrows(
                ForbiddenResponse.class,
                () -> RoleValidation.validateRoleChanges(current, desired, ALL_ROLES, callerRoles, true));
    }

    @Test
    void allowsRemovingManagerFromNonOwner() {
        List<Role> current = List.of(LOGIN, MANAGER);
        List<Integer> desired = List.of(LOGIN.id());
        Set<Roles> callerRoles = EnumSet.of(Roles.ADMIN);

        // MANAGER is protected, so this should still throw
        assertThrows(
                ForbiddenResponse.class,
                () -> RoleValidation.validateRoleChanges(current, desired, ALL_ROLES, callerRoles, false));
    }

    @Test
    void rejectsGrantingRoleCallerDoesNotHave() {
        List<Role> current = List.of(LOGIN);
        List<Integer> desired = List.of(LOGIN.id(), ADMIN.id());
        Set<Roles> callerRoles = EnumSet.of(Roles.MEMBER_MANAGEMENT); // doesn't have ADMIN

        assertThrows(
                ForbiddenResponse.class,
                () -> RoleValidation.validateRoleChanges(current, desired, ALL_ROLES, callerRoles, false));
    }

    @Test
    void allowsKeepingExistingRoleEvenWithoutCallerHavingIt() {
        List<Role> current = List.of(LOGIN, ADMIN);
        List<Integer> desired = List.of(LOGIN.id(), ADMIN.id()); // no change
        Set<Roles> callerRoles = EnumSet.of(Roles.MEMBER_MANAGEMENT); // doesn't have ADMIN

        assertDoesNotThrow(() -> RoleValidation.validateRoleChanges(current, desired, ALL_ROLES, callerRoles, false));
    }

    @Test
    void rejectsUnknownRoleId() {
        List<Role> current = List.of(LOGIN);
        List<Integer> desired = List.of(LOGIN.id(), 999);
        Set<Roles> callerRoles = EnumSet.of(Roles.ADMIN);

        assertThrows(
                BadRequestResponse.class,
                () -> RoleValidation.validateRoleChanges(current, desired, ALL_ROLES, callerRoles, false));
    }

    // --- Conflict validation ---

    @Test
    void rejectsMemberAndGuardianTogether() {
        List<Role> current = List.of();
        List<Integer> desired = List.of(MEMBER.id(), GUARDIAN.id());
        Set<Roles> callerRoles = EnumSet.of(Roles.ADMIN);

        assertThrows(
                BadRequestResponse.class,
                () -> RoleValidation.validateRoleChanges(current, desired, ALL_ROLES, callerRoles, false));
    }

    @Test
    void rejectsMemberAndTeamTogether() {
        List<Role> current = List.of();
        List<Integer> desired = List.of(MEMBER.id(), TEAM.id());
        Set<Roles> callerRoles = EnumSet.of(Roles.ADMIN);

        assertThrows(
                BadRequestResponse.class,
                () -> RoleValidation.validateRoleChanges(current, desired, ALL_ROLES, callerRoles, false));
    }

    @Test
    void allowsGuardianAndLoginTogether() {
        List<Role> current = List.of();
        List<Integer> desired = List.of(GUARDIAN.id(), LOGIN.id());
        Set<Roles> callerRoles = EnumSet.of(Roles.ADMIN, Roles.GUARDIAN, Roles.LOGIN);

        assertDoesNotThrow(() -> RoleValidation.validateRoleChanges(current, desired, ALL_ROLES, callerRoles, false));
    }

    // --- validateGuardianRoleChanges ---

    @Test
    void guardianCanGrantMemberRole() {
        List<Role> current = List.of(LOGIN);
        List<Integer> desired = List.of(LOGIN.id(), MEMBER.id());

        assertDoesNotThrow(() -> RoleValidation.validateGuardianRoleChanges(current, desired, ALL_ROLES));
    }

    @Test
    void guardianCannotGrantManagementRole() {
        List<Role> current = List.of(LOGIN);
        List<Integer> desired = List.of(LOGIN.id(), TEAM.id());

        assertThrows(
                ForbiddenResponse.class, () -> RoleValidation.validateGuardianRoleChanges(current, desired, ALL_ROLES));
    }

    @Test
    void guardianCannotRemoveRoles() {
        List<Role> current = List.of(LOGIN, MEMBER);
        List<Integer> desired = List.of(LOGIN.id()); // removing MEMBER

        assertThrows(
                ForbiddenResponse.class, () -> RoleValidation.validateGuardianRoleChanges(current, desired, ALL_ROLES));
    }

    @Test
    void guardianCannotGrantConflictingRoles() {
        List<Role> current = List.of(LOGIN, MEMBER);
        List<Integer> desired = List.of(LOGIN.id(), MEMBER.id(), GUARDIAN.id());

        assertThrows(
                BadRequestResponse.class,
                () -> RoleValidation.validateGuardianRoleChanges(current, desired, ALL_ROLES));
    }
}
