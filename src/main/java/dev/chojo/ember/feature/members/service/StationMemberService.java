/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.util.PermissionValidation;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Singleton
public class StationMemberService {
    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final AuthService authService;

    @Inject
    public StationMemberService(
            StationMemberRepository memberRepository,
            StationRepository stationRepository,
            AccountRepository accountRepository,
            AuthService authService) {
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.authService = authService;
    }

    public List<StationMember> findByStation(int stationId) {
        return memberRepository.findByStation(stationId);
    }

    public List<StationMember> findByStation(int stationId, boolean includeFormer) {
        return memberRepository.findByStation(stationId, includeFormer);
    }

    public Optional<StationMember> findById(int id) {
        return memberRepository.findById(id);
    }

    public UUID resolveUid(int memberId) {
        return memberRepository.resolveUid(memberId);
    }

    public Optional<Integer> resolveId(int stationId, UUID memberUid) {
        return memberRepository.resolveId(stationId, memberUid);
    }

    public MemberIdentity resolveIdentity(int memberId) {
        return memberRepository.resolveIdentity(memberId);
    }

    public List<StationMember> findByAccount(int accountId) {
        return memberRepository.findByAccount(accountId);
    }

    public StationMember create(int stationId, int accountId) {
        return memberRepository.create(stationId, accountId);
    }

    public boolean delete(int id) {
        return memberRepository.delete(id);
    }

    // -- Permissions --

    public List<Permission> findPermissions(int memberId) {
        return memberRepository.findPermissions(memberId);
    }

    public List<Permission> findAllPermissions() {
        return memberRepository.findAllPermissions();
    }

    public List<Permission> setPermissions(
            int memberId, List<Integer> desiredPermissionIds, Set<StationPermission> callerPermissions) {
        List<Permission> allPermissions = memberRepository.findAllPermissions();
        List<Permission> currentPermissions = memberRepository.findPermissions(memberId);
        var currentIds = currentPermissions.stream().map(Permission::id).toList();

        PermissionValidation.validatePermissionChanges(
                currentPermissions, desiredPermissionIds, allPermissions, callerPermissions);

        // Check if LOGIN permission is being added — requires account with email
        var loginPerm = allPermissions.stream()
                .filter(p -> p.permission() == StationPermission.LOGIN)
                .findFirst();
        boolean addingLogin = loginPerm.isPresent()
                && desiredPermissionIds.contains(loginPerm.get().id())
                && !currentIds.contains(loginPerm.get().id());

        var member = memberRepository.findById(memberId).orElse(null);
        if (addingLogin && member != null && member.accountId() != null) {
            var account = accountRepository.findById(member.accountId()).orElse(null);
            if (account == null || account.email() == null) {
                throw new BadRequestResponse("Cannot grant LOGIN permission: account has no email address");
            }
        }

        for (int permId : currentIds) {
            if (!desiredPermissionIds.contains(permId)) {
                memberRepository.revokePermission(memberId, permId);
            }
        }
        for (int permId : desiredPermissionIds) {
            if (!currentIds.contains(permId)) {
                memberRepository.grantPermission(memberId, permId);
            }
        }

        // If LOGIN was just granted and the account has no credentials, send onboarding email
        if (addingLogin && authService != null && member != null && member.accountId() != null) {
            var credential = accountRepository.findCredential(member.accountId());
            if (credential.isEmpty()) {
                var account = accountRepository.findById(member.accountId()).orElseThrow();
                authService.sendPasswordSetup(member.accountId(), account.email(), account.firstName());
            }
        }

        return memberRepository.findPermissions(memberId);
    }

    // -- User Type --

    public boolean setUserType(int memberId, StationUserType userType) {
        return memberRepository.setUserType(memberId, userType);
    }

    // -- Manager relations --

    public List<StationMember> findManaged(int managerId) {
        return memberRepository.findManaged(managerId);
    }

    public List<StationMember> findManagers(int managedId) {
        return memberRepository.findManagers(managedId);
    }

    public List<StationMember> setManaged(int managerId, List<Integer> desiredManagedIds) {
        List<StationMember> currentManaged = memberRepository.findManaged(managerId);
        var currentManagedIds = currentManaged.stream().map(StationMember::id).toList();

        for (int managedId : currentManagedIds) {
            if (!desiredManagedIds.contains(managedId)) {
                memberRepository.removeManager(managerId, managedId);
            }
        }
        for (int managedId : desiredManagedIds) {
            if (!currentManagedIds.contains(managedId)) {
                memberRepository.addManager(managerId, managedId);
            }
        }

        return memberRepository.findManaged(managerId);
    }

    public List<StationMember> setManagers(int managedId, List<Integer> desiredManagerIds) {
        List<StationMember> currentManagers = memberRepository.findManagers(managedId);
        var currentManagerIds = currentManagers.stream().map(StationMember::id).toList();

        for (int managerId : currentManagerIds) {
            if (!desiredManagerIds.contains(managerId)) {
                memberRepository.removeManager(managerId, managedId);
            }
        }
        for (int managerId : desiredManagerIds) {
            if (!currentManagerIds.contains(managerId)) {
                memberRepository.addManager(managerId, managedId);
            }
        }

        return memberRepository.findManagers(managedId);
    }
}
