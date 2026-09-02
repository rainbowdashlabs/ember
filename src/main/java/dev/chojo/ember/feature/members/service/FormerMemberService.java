/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service for managing former members including converting active members to former status,
 * tracking departure reasons, and cleaning up associated data (inventory, attendance, roles).
 */
@Singleton
public class FormerMemberService {
    private static final Logger log = LoggerFactory.getLogger(FormerMemberService.class);

    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final InventoryRepository inventoryRepository;
    private final ExchangeService exchangeService;
    private final MemberGroupRepository groupRepository;
    private final UserTagRepository tagRepository;
    private final AttendanceRepository attendanceRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final MemberDocumentService documentService;
    private final SelfCheckService selfCheckService;

    @Inject
    public FormerMemberService(
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            InventoryRepository inventoryRepository,
            ExchangeService exchangeService,
            MemberGroupRepository groupRepository,
            UserTagRepository tagRepository,
            AttendanceRepository attendanceRepository,
            ProfileFieldRepository profileFieldRepository,
            MemberDocumentService documentService,
            SelfCheckService selfCheckService) {
        this.selfCheckService = selfCheckService;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.inventoryRepository = inventoryRepository;
        this.exchangeService = exchangeService;
        this.groupRepository = groupRepository;
        this.tagRepository = tagRepository;
        this.attendanceRepository = attendanceRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.documentService = documentService;
    }

    /**
     * Check if a member can be marked as former.
     *
     * @return null if OK, error message otherwise
     */
    public String canMarkFormer(int memberId) {
        var member = memberRepository.findById(memberId).orElse(null);
        if (member == null) return "Member not found";
        if (member.former()) return "Member is already former";

        // Check: no inventory assigned
        var assignedItems = inventoryRepository.findItemsByMember(memberId);
        if (!assignedItems.isEmpty()) {
            return "Member still has " + assignedItems.size() + " inventory items assigned";
        }

        // Check: only TEAM or MEMBER can become former (not GUARDIAN, MANAGER, ADMIN)
        var roles = memberRepository.findPermissions(memberId);
        boolean hasForbiddenRole = roles.stream()
                .anyMatch(r -> r.permission() == StationPermission.MEMBER_GUARDIAN
                        || r.permission() == StationPermission.STATION_ADMINISTRATOR);
        if (hasForbiddenRole) {
            return "Member managers, managers, and admins cannot become former members";
        }

        return null;
    }

    /**
     * Mark a member as former. Performs all cleanup:
     * - Remove all roles (especially LOGIN)
     * - Remove manager relations (both directions)
     * - Delete exchange requests
     * - Close any self-check they were still holding
     * - Remove from all groups
     * - Remove from all tags
     * - Delete absences
     * - Set former flag
     */
    public void markFormer(int memberId) {
        String check = canMarkFormer(memberId);
        if (check != null) {
            throw new IllegalStateException(check);
        }

        log.info("Marking member {} as former", memberId);

        // Remove all roles
        memberRepository.revokeAllPermissions(memberId);

        // Remove all manager relations (both as manager and as managed)
        memberRepository.removeAllManagers(memberId);
        memberRepository.removeAllManaged(memberId);

        // Delete exchange requests
        var exchanges = exchangeService.findByMember(memberId);
        for (var ex : exchanges) {
            exchangeService.delete(ex.id());
        }

        selfCheckService.closeAllFor(memberId);

        // Remove from all groups
        var groups = groupRepository.findGroupsForMember(memberId);
        for (var group : groups) {
            groupRepository.removeMember(group.id(), memberId);
        }

        // Remove from all tags
        var tags = tagRepository.findTagsForMember(memberId);
        for (var tag : tags) {
            tagRepository.removeMember(tag.id(), memberId);
        }

        // Delete absences
        attendanceRepository.deleteAbsencesByMember(memberId);

        // Delete non-archived profile field values
        profileFieldRepository.deleteNonArchivedValues(memberId);

        documentService.releaseMember(memberId);

        // Save display name from account and decouple
        var member = memberRepository.findById(memberId).orElseThrow();
        if (member.accountId() != null) {
            var account = accountRepository.findById(member.accountId()).orElse(null);
            String displayName = account != null ? (account.firstName() + " " + account.lastName()).trim() : "";
            memberRepository.setDisplayNameAndClearAccount(memberId, displayName);
        }

        // Set former flag
        memberRepository.setFormer(memberId, true);

        log.info("Member {} marked as former", memberId);
    }

    /**
     * Takes a former member back on.
     *
     * <p>They are given back the right to be a member and, where their kind of membership signs in of
     * its own accord, the right to sign in. A member or somebody on a trial does not: signing in is
     * something their guardian hands them, and handing it back on a return nobody asked about would
     * give a child an account their family never set up.
     */
    public void reactivate(int memberId) {
        var member = memberRepository.findById(memberId).orElse(null);
        if (member == null) throw new IllegalStateException("Member not found");
        if (!member.former()) throw new IllegalStateException("Member is not former");

        log.info("Reactivating former member {}", memberId);

        memberRepository.setFormer(memberId, false);

        if (signsInOfItsOwnAccord(member.userType())) {
            memberRepository
                    .findPermissionByName(StationPermission.LOGIN)
                    .ifPresent(r -> memberRepository.grantPermission(memberId, r.id()));
        }
        memberRepository
                .findPermissionByName(StationPermission.USER)
                .ifPresent(r -> memberRepository.grantPermission(memberId, r.id()));

        log.info("Former member {} reactivated", memberId);
    }

    /** Whether this kind of membership carries the right to sign in without anybody granting it. */
    private static boolean signsInOfItsOwnAccord(StationUserType userType) {
        return userType != null && List.of(userType.defaultPermissions()).contains(StationPermission.LOGIN);
    }
}
