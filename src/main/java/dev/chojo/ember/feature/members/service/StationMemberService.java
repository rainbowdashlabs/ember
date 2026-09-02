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
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.util.PermissionValidation;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Singleton
public class StationMemberService {
    private static final Logger log = LoggerFactory.getLogger(StationMemberService.class);
    private final StationMemberRepository memberRepository;
    private final StationRepository stationRepository;
    private final AccountRepository accountRepository;
    private final AuthService authService;
    private final MemberLookupService lookupService;

    @Inject
    public StationMemberService(
            StationMemberRepository memberRepository,
            StationRepository stationRepository,
            AccountRepository accountRepository,
            AuthService authService,
            MemberLookupService lookupService) {
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
        this.accountRepository = accountRepository;
        this.authService = authService;
        this.lookupService = lookupService;
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
        return lookupService.resolveUid(memberId);
    }

    public Optional<Integer> resolveId(int stationId, UUID memberUid) {
        return lookupService.resolveId(stationId, memberUid);
    }

    public MemberIdentity resolveIdentity(int memberId) {
        return lookupService.resolveIdentity(memberId);
    }

    public Optional<Integer> resolveMemberId(MemberIdentity identity) {
        return lookupService.resolveMemberId(identity);
    }

    public List<MemberCompletion> findCompletions(int stationId) {
        return lookupService.findCompletions(stationId);
    }

    public List<StationMember> findByAccount(int accountId) {
        return memberRepository.findByAccount(accountId);
    }

    /**
     * The stations an account actually belongs to, as a person.
     *
     * <p>A cluster's own station is not one of them. Writing for a cluster gives the writer a member row
     * there so an article can name its author, and that row carries no permission and means nobody joined
     * anything: offering it as a station would put a shell nobody runs in the switcher, in the picker and
     * on the cross-station page. Everything that asks "which stations are mine" wants this list; the export
     * and the deletion want the other one, because a row is a row.
     *
     * @param accountId the account
     * @return its memberships, minus any on a cluster's own station
     */
    public List<StationMember> findBelongingByAccount(int accountId) {
        return memberRepository.findByAccount(accountId).stream()
                .filter(member -> stationRepository
                        .findById(member.stationId())
                        .map(station -> station.stationKind() == StationKind.REGULAR)
                        .orElse(false))
                .toList();
    }

    public StationMember create(int stationId, int accountId) {
        var member = memberRepository.create(stationId, accountId);
        log.info("Member created: member={}, station={}, account={}", member.id(), stationId, accountId);
        return member;
    }

    public boolean delete(int id) {
        log.info("Member deleted: member={}", id);
        return memberRepository.delete(id);
    }

    // -- Permissions --

    public List<Permission> findPermissions(int memberId) {
        return memberRepository.findPermissions(memberId);
    }

    public List<Permission> findAllPermissions() {
        return memberRepository.findAllPermissions();
    }

    /**
     * The active members of a station holding a permission, counting the wider rights that carry
     * it. Asked for a whole station at once rather than member by member, which is what keeps a
     * question about everybody to one round trip.
     *
     * @param stationId  the station
     * @param permission the permission to ask for
     * @return the members who hold it
     */
    public List<StationMember> findMembersWithPermission(int stationId, StationPermission permission) {
        return memberRepository.findMembersWithPermission(stationId, permission);
    }

    public List<Permission> setPermissions(
            int memberId,
            List<Integer> desiredPermissionIds,
            Set<StationPermission> callerPermissions,
            Integer callerMemberId) {
        List<Permission> allPermissions = memberRepository.findAllPermissions();
        List<Permission> currentPermissions = memberRepository.findPermissions(memberId);
        var currentIds = currentPermissions.stream().map(Permission::id).toList();

        if (callerMemberId != null && callerMemberId == memberId) {
            for (Permission existing : currentPermissions) {
                if (!desiredPermissionIds.contains(existing.id())) {
                    throw new ForbiddenResponse("You cannot remove your own permissions");
                }
            }
        }

        var target = memberRepository.findById(memberId).orElse(null);
        if (target != null) {
            var station = stationRepository.findById(target.stationId()).orElse(null);
            if (station != null && station.ownerMemberId() != null && station.ownerMemberId() == memberId) {
                var adminPerm = allPermissions.stream()
                        .filter(p -> p.permission() == StationPermission.STATION_ADMINISTRATOR)
                        .findFirst();
                if (adminPerm.isPresent()
                        && currentIds.contains(adminPerm.get().id())
                        && !desiredPermissionIds.contains(adminPerm.get().id())) {
                    throw new ForbiddenResponse("The station owner must keep the Station Administrator permission");
                }
            }
        }

        PermissionValidation.validatePermissionChanges(
                currentPermissions, desiredPermissionIds, allPermissions, callerPermissions);

        // Check if LOGIN permission is being added - requires account with email
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
                authService.sendPasswordSetup(member.accountId());
            }
        }

        log.info("Permissions updated for member {}: {}", memberId, desiredPermissionIds);
        return memberRepository.findPermissions(memberId);
    }

    // -- User Type --

    public boolean setUserType(int memberId, StationUserType userType) {
        log.info("User type changed for member {}: {}", memberId, userType);
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
        for (int managedId : desiredManagedIds) {
            requireManageableType(managedId);
        }
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

        log.info("Managed relations updated for manager {}: {}", managerId, desiredManagedIds);
        return memberRepository.findManaged(managerId);
    }

    public List<StationMember> setManagers(int managedId, List<Integer> desiredManagerIds) {
        if (!desiredManagerIds.isEmpty()) {
            requireManageableType(managedId);
        }
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

        log.info("Manager relations updated for member {}: {}", managedId, desiredManagerIds);
        return memberRepository.findManagers(managedId);
    }

    /**
     * Guardians may only be attached to members of type {@link StationUserType#MEMBER} or
     * {@link StationUserType#TRIAL}. All other types (TEAM, MANAGER, GUARDIAN) represent
     * adults who manage themselves, so allowing a guardian relationship there is rejected.
     */
    private void requireManageableType(int memberId) {
        var member = memberRepository.findById(memberId).orElseThrow(() -> new BadRequestResponse("Member not found"));
        if (member.userType() != StationUserType.MEMBER && member.userType() != StationUserType.TRIAL) {
            throw new BadRequestResponse("Guardians can only be assigned to members of type MEMBER or TRIAL");
        }
    }
}
