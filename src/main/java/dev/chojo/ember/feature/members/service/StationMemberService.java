/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.account.entity.AccountAction;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountReach;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.util.PermissionValidation;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
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
    private final DocumentService documentService;
    private final AccountReach accountReach;

    @Inject
    public StationMemberService(
            StationMemberRepository memberRepository,
            StationRepository stationRepository,
            AccountRepository accountRepository,
            AuthService authService,
            MemberLookupService lookupService,
            DocumentService documentService,
            AccountReach accountReach) {
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
        this.accountRepository = accountRepository;
        this.authService = authService;
        this.lookupService = lookupService;
        this.documentService = documentService;
        this.accountReach = accountReach;
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

    /**
     * @param ids members
     * @return those that exist, read in one go, in no particular order
     */
    public List<StationMember> findByIds(List<Integer> ids) {
        return memberRepository.findByIds(ids);
    }

    public @Nullable UUID resolveUid(int memberId) {
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

    /**
     * Deletes a member, taking their documents with them the way archiving does.
     *
     * <p>The link rows cascade on their own, so without this a deleted member's documents were left
     * standing with nobody named on them, and a document that names nobody is the station's own: a
     * medical certificate would quietly have become a station document, readable by a wider audience
     * than the one it was filed for. That is the one outcome this area must not produce.
     *
     * <p>So deletion follows the same rule as marking somebody former. Documents bound only to the
     * departing member go. One marked to be kept for the record cannot stay with a member who is gone,
     * so it keeps their name instead, which keeps it their paperwork. Documents that never had a member
     * are untouched, because they were never about anybody.
     */
    public boolean delete(int id) {
        documentService.memberLeaves(id, DocumentService.Leaving.DELETED);
        log.info("Member deleted: member={}", id);
        return memberRepository.delete(id);
    }

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
            @Nullable Integer callerMemberId) {
        List<Permission> allPermissions = memberRepository.findAllPermissions();
        List<Permission> currentPermissions = memberRepository.findPermissions(memberId);
        var currentIds = currentPermissions.stream().map(Permission::id).toList();

        if (callerMemberId != null && callerMemberId == memberId) {
            for (Permission existing : currentPermissions) {
                if (!desiredPermissionIds.contains(existing.id())) {
                    throw MemberRefusal.MEMBER_OWN_PERMISSION_NOT_REMOVABLE.raise();
                }
            }
        }

        var target = memberRepository.findById(memberId).orElse(null);
        if (target != null) {
            var station = stationRepository.findById(target.stationId()).orElse(null);
            if (station != null && station.isOwnedBy(memberId)) {
                var adminPerm = allPermissions.stream()
                        .filter(p -> p.permission() == StationPermission.STATION_ADMINISTRATOR)
                        .findFirst();
                if (adminPerm.isPresent()
                        && currentIds.contains(adminPerm.get().id())
                        && !desiredPermissionIds.contains(adminPerm.get().id())) {
                    throw MemberRefusal.MEMBER_OWNER_KEEPS_ADMINISTRATION.raise();
                }
            }
        }

        PermissionValidation.validatePermissionChanges(
                currentPermissions, desiredPermissionIds, allPermissions, callerPermissions);

        var loginPerm = allPermissions.stream()
                .filter(p -> p.permission() == StationPermission.LOGIN)
                .findFirst();
        boolean addingLogin = loginPerm.isPresent()
                && desiredPermissionIds.contains(loginPerm.get().id())
                && !currentIds.contains(loginPerm.get().id());

        var member = memberRepository.findById(memberId).orElse(null);
        Integer accountId = member == null ? null : member.accountId();
        if (addingLogin && member != null && accountId != null) {
            accountReach.require(member.stationId(), accountId, AccountAction.SETUP_MAIL);
        }
        if (addingLogin && accountId != null) {
            var account = accountRepository.findById(accountId).orElse(null);
            if (account == null || account.email() == null) {
                throw MemberRefusal.MEMBER_SIGN_IN_NEEDS_AN_ADDRESS.raise();
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

        if (addingLogin && authService != null && accountId != null) {
            var credential = accountRepository.findCredential(accountId);
            if (credential.isEmpty()) {
                authService.sendPasswordSetup(accountId);
            }
        }

        log.info("Permissions updated for member {}: {}", memberId, desiredPermissionIds);
        return memberRepository.findPermissions(memberId);
    }

    public void setJoinDate(int memberId, LocalDate joinDate) {
        memberRepository.setJoinDate(memberId, joinDate);
    }

    public List<StationMember> findFormerByStation(int stationId) {
        return memberRepository.findFormerByStation(stationId);
    }

    /**
     * The permissions a station grants a user type on top of what the type carries by itself.
     */
    public List<Permission> findUserTypePermissions(int stationId, StationUserType userType) {
        return memberRepository.findUserTypePermissions(stationId, userType);
    }

    /**
     * Replaces the permissions a station grants a user type on top of what the type carries.
     *
     * @return the permissions granted now
     */
    public List<Permission> setUserTypePermissions(
            int stationId, StationUserType userType, List<Integer> permissionIds) {
        memberRepository.setUserTypePermissions(stationId, userType, permissionIds);
        return memberRepository.findUserTypePermissions(stationId, userType);
    }

    /**
     * Everything a user type may do at a station: what the type carries by itself, what the
     * station grants it on top, and everything those permissions include.
     *
     * @return the permission names, sorted
     */
    public List<String> effectiveUserTypePermissions(int stationId, StationUserType userType) {
        Set<StationPermission> permissions = EnumSet.noneOf(StationPermission.class);
        permissions.addAll(Arrays.asList(userType.defaultPermissions()));
        memberRepository.findUserTypePermissions(stationId, userType).stream()
                .map(Permission::permission)
                .forEach(permissions::add);
        return StationPermission.expand(permissions).stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }

    public List<StationMember> findManaged(int managerId) {
        return memberRepository.findManaged(managerId);
    }

    public List<StationMember> findManagers(int managedId) {
        return memberRepository.findManagers(managedId);
    }

    /**
     * Sets whom a member looks after, linking the new ones behind the guardians each already has.
     *
     * @param managerId         the guardian
     * @param desiredManagedIds everybody in their care from now on
     * @param actorId           the member who sets it, recorded on every new link, or null
     * @return everybody in their care
     */
    public List<StationMember> setManaged(int managerId, List<Integer> desiredManagedIds, @Nullable Integer actorId) {
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
                memberRepository.addManager(managerId, managedId, actorId);
            }
        }

        log.info("Managed relations updated for manager {}: {}", managerId, desiredManagedIds);
        return memberRepository.findManaged(managerId);
    }

    /**
     * Sets the guardians of a member, in the order given: the first of the list is the first guardian,
     * whom documents name as guardian 1.
     *
     * @param managedId         the member looked after
     * @param desiredManagerIds their guardians from now on, in order
     * @param actorId           the member who sets them, recorded on every new link, or null
     * @return their guardians in order
     */
    public List<StationMember> setManagers(int managedId, List<Integer> desiredManagerIds, @Nullable Integer actorId) {
        if (!desiredManagerIds.isEmpty()) {
            requireManageableType(managedId);
        }
        List<StationMember> currentManagers = memberRepository.findManagers(managedId);
        var currentManagerIds = currentManagers.stream().map(StationMember::id).toList();

        Transactions.run(() -> {
            for (int managerId : currentManagerIds) {
                if (!desiredManagerIds.contains(managerId)) {
                    memberRepository.removeManager(managerId, managedId);
                }
            }
            for (int managerId : desiredManagerIds) {
                if (!currentManagerIds.contains(managerId)) {
                    memberRepository.addManager(managerId, managedId, actorId);
                }
            }
            memberRepository.orderManagers(managedId, desiredManagerIds);
        });

        log.info("Manager relations updated for member {}: {}", managedId, desiredManagerIds);
        return memberRepository.findManagers(managedId);
    }

    /**
     * Guardians may only be attached to members of type {@link StationUserType#MEMBER} or
     * {@link StationUserType#TRIAL}. All other types (TEAM, MANAGER, GUARDIAN) represent
     * adults who manage themselves, so allowing a guardian relationship there is rejected.
     */
    private void requireManageableType(int memberId) {
        var member =
                memberRepository.findById(memberId).orElseThrow(MemberRefusal.MEMBER_NOT_HERE_FOR_GUARDIANS::raise);
        if (member.userType() != StationUserType.MEMBER && member.userType() != StationUserType.TRIAL) {
            throw MemberRefusal.MEMBER_TYPE_TAKES_NO_GUARDIANS.raise();
        }
    }
}
