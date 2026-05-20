/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.inventory.repository.ExchangeRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Service for managing former members including converting active members to former status,
 * tracking departure reasons, and cleaning up associated data (inventory, attendance, roles).
 */
@Singleton
public class FormerMemberService {
    private static final Logger log = LoggerFactory.getLogger(FormerMemberService.class);
    private static final Set<String> ALLOWED_ROLES_FOR_FORMER = Set.of("MEMBER", "TEAM", "USER");

    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final InventoryRepository inventoryRepository;
    private final ExchangeRepository exchangeRepository;
    private final MemberGroupRepository groupRepository;
    private final AttendanceRepository attendanceRepository;
    private final ProfileFieldRepository profileFieldRepository;

    @Inject
    public FormerMemberService(
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            InventoryRepository inventoryRepository,
            ExchangeRepository exchangeRepository,
            MemberGroupRepository groupRepository,
            AttendanceRepository attendanceRepository,
            ProfileFieldRepository profileFieldRepository) {
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.inventoryRepository = inventoryRepository;
        this.exchangeRepository = exchangeRepository;
        this.groupRepository = groupRepository;
        this.attendanceRepository = attendanceRepository;
        this.profileFieldRepository = profileFieldRepository;
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
        var roles = memberRepository.findRoles(memberId);
        boolean hasForbiddenRole = roles.stream()
                .anyMatch(r -> r.role() == Roles.GUARDIAN || r.role() == Roles.MANAGER || r.role() == Roles.ADMIN);
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
     * - Remove from all groups
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
        memberRepository.removeAllRoles(memberId);

        // Remove all manager relations (both as manager and as managed)
        memberRepository.removeAllManagers(memberId);
        memberRepository.removeAllManaged(memberId);

        // Delete exchange requests
        var exchanges = exchangeRepository.findByMember(memberId);
        for (var ex : exchanges) {
            exchangeRepository.delete(ex.id());
        }

        // Remove from all groups
        var groups = groupRepository.findGroupsForMember(memberId);
        for (var group : groups) {
            groupRepository.removeMember(group.id(), memberId);
        }

        // Delete absences
        attendanceRepository.deleteAbsencesByMember(memberId);

        // Delete non-archived profile field values
        profileFieldRepository.deleteNonArchivedValues(memberId);

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
     * Reactivate a former member. Assigns LOGIN and MEMBER roles.
     */
    public void reactivate(int memberId) {
        var member = memberRepository.findById(memberId).orElse(null);
        if (member == null) throw new IllegalStateException("Member not found");
        if (!member.former()) throw new IllegalStateException("Member is not former");

        log.info("Reactivating former member {}", memberId);

        memberRepository.setFormer(memberId, false);

        // Assign LOGIN and MEMBER roles
        memberRepository.findRoleByName(Roles.LOGIN).ifPresent(r -> memberRepository.addRole(memberId, r.id()));
        memberRepository.findRoleByName(Roles.MEMBER).ifPresent(r -> memberRepository.addRole(memberId, r.id()));

        log.info("Former member {} reactivated", memberId);
    }
}
