/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.CheckResult;
import dev.chojo.ember.entity.Inventory;
import dev.chojo.ember.entity.InventoryCheck;
import dev.chojo.ember.entity.InventoryCheckLock;
import dev.chojo.ember.entity.InventoryItem;
import dev.chojo.ember.entity.InventoryRequirement;
import dev.chojo.ember.entity.InventorySize;
import dev.chojo.ember.entity.MemberGroup;
import dev.chojo.ember.entity.Role;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.InventoryCheckRepository;
import dev.chojo.ember.repository.InventoryCheckRepository.MemberCheckSummary;
import dev.chojo.ember.repository.InventoryRepository;
import dev.chojo.ember.repository.MemberGroupRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import io.javalin.http.ConflictResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class InventoryCheckService {
    private static final int LOCK_TIMEOUT_MINUTES = 30;

    private final InventoryCheckRepository checkRepository;
    private final InventoryRepository inventoryRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final AccountRepository accountRepository;

    @Inject
    public InventoryCheckService(
            InventoryCheckRepository checkRepository,
            InventoryRepository inventoryRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            AccountRepository accountRepository) {
        this.checkRepository = checkRepository;
        this.inventoryRepository = inventoryRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.accountRepository = accountRepository;
    }

    public List<MemberCheckSummary> getCheckOverview(int stationId) {
        checkRepository.releaseExpiredLocks(LOCK_TIMEOUT_MINUTES);
        var summaries = checkRepository.checkOverview(stationId);
        // Enrich with roles
        return summaries.stream()
                .map(s -> {
                    var roles = stationMemberRepository.findRoles(s.memberId()).stream()
                            .map(Role::role)
                            .toList();
                    return new MemberCheckSummary(
                            s.memberId(),
                            s.firstName(),
                            s.lastName(),
                            s.lastCheckedAt(),
                            s.checkerFirstName(),
                            s.checkerLastName(),
                            s.locked(),
                            s.lockedBy(),
                            s.lockerFirstName(),
                            s.lockerLastName(),
                            roles);
                })
                .toList();
    }

    public MemberCheckState startCheck(int stationId, int memberId, int lockedBy) {
        checkRepository.releaseExpiredLocks(LOCK_TIMEOUT_MINUTES);

        // Check if this member is already locked
        var existingLock = checkRepository.findLock(memberId);
        if (existingLock.isPresent()) {
            if (existingLock.get().lockedBy() != lockedBy) {
                throw new ConflictResponse("Member is already being checked by another user");
            }
            // Same user already holds the lock — continue the check
        } else {
            // Release any other lock held by this checker, then acquire on this member
            checkRepository.releaseLockByLocker(lockedBy);
            Optional<InventoryCheckLock> lock = checkRepository.acquireLock(stationId, memberId, lockedBy);
            if (lock.isEmpty()) {
                throw new ConflictResponse("Member is already being checked by another user");
            }
        }

        // Look up member name
        var member = stationMemberRepository.findById(memberId).orElseThrow();
        var account = accountRepository.findById(member.accountId()).orElseThrow();
        String memberName = account.fullName();

        var required = getRequiredItems(stationId, memberId);
        var assigned = inventoryRepository.findItemsByMember(memberId);
        var lastCheck = checkRepository.latestCheckForMember(memberId).orElse(null);

        // Collect unassigned items for each required inventory
        Map<Integer, List<InventoryItem>> unassigned = new HashMap<>();
        for (RequiredInventoryItem req : required) {
            unassigned.put(req.inventoryId(), inventoryRepository.findUnassignedItems(req.inventoryId()));
        }

        return new MemberCheckState(memberName, required, assigned, lastCheck, unassigned);
    }

    public InventoryCheck completeCheck(int stationId, int memberId, int checkedBy, List<CheckItemRequest> results) {
        InventoryCheck check = checkRepository.createCheck(stationId, memberId, checkedBy);

        for (CheckItemRequest result : results) {
            checkRepository.createCheckItem(
                    check.id(), result.itemId(), result.inventoryId(), result.result(), result.note());

            if (result.result() == CheckResult.LOST && result.itemId() != null) {
                inventoryRepository.markLost(result.itemId());
            }
        }

        checkRepository.releaseLock(memberId);
        return check;
    }

    public void cancelCheck(int memberId, int lockedBy) {
        var lock = checkRepository.findLock(memberId);
        if (lock.isPresent() && lock.get().lockedBy() == lockedBy) {
            checkRepository.releaseLock(memberId);
        }
    }

    public Optional<EnrichedCheckDetail> lastCheckDetail(int memberId) {
        var detail = checkRepository.latestCheckDetail(memberId);
        if (detail.isEmpty()) return Optional.empty();
        var d = detail.get();
        var enrichedItems = d.items().stream()
                .map(ci -> {
                    String itemName;
                    String inventoryName;
                    String sizeName = null;
                    String internalId = null;
                    if (ci.itemId() != null) {
                        var item = inventoryRepository.findItemById(ci.itemId()).orElse(null);
                        itemName = item != null ? item.name() : "#" + ci.itemId();
                        internalId = item != null ? item.internalId() : null;
                        inventoryName = item != null
                                ? inventoryRepository
                                        .findById(item.inventoryId())
                                        .map(Inventory::name)
                                        .orElse("")
                                : "";
                        if (item != null && item.sizeId() != null) {
                            sizeName = inventoryRepository.findSizes(item.inventoryId()).stream()
                                    .filter(s -> s.id() == item.sizeId())
                                    .map(InventorySize::label)
                                    .findFirst()
                                    .orElse(null);
                        }
                    } else {
                        itemName = null;
                        inventoryName = ci.inventoryId() != null
                                ? inventoryRepository
                                        .findById(ci.inventoryId())
                                        .map(Inventory::name)
                                        .orElse("")
                                : "";
                    }
                    return new EnrichedCheckItem(
                            ci.id(),
                            ci.itemId(),
                            itemName,
                            internalId,
                            inventoryName,
                            sizeName,
                            ci.result(),
                            ci.note());
                })
                .toList();
        return Optional.of(
                new EnrichedCheckDetail(d.check(), d.checkerFirstName(), d.checkerLastName(), enrichedItems));
    }

    public Optional<Integer> nextMember(int stationId, int currentMemberId) {
        return checkRepository.nextUncheckedMember(stationId, currentMemberId);
    }

    public List<RequiredInventoryItem> getRequiredItems(int stationId, int memberId) {
        List<Role> memberRoles = stationMemberRepository.findRoles(memberId);
        List<MemberGroup> memberGroups = memberGroupRepository.findGroupsForMember(memberId);
        List<InventoryRequirement> allRequirements = inventoryRepository.findAllRequirementsByStation(stationId);

        var memberRoleIds = memberRoles.stream().map(Role::id).toList();
        var memberGroupIds = memberGroups.stream().map(MemberGroup::id).toList();

        // Filter requirements applicable to this member
        List<InventoryRequirement> applicable = allRequirements.stream()
                .filter(req -> (req.roleId() != 0 && memberRoleIds.contains(req.roleId()))
                        || (req.groupId() != 0 && memberGroupIds.contains(req.groupId())))
                .toList();

        // Aggregate by inventory: sum required quantities (LinkedHashMap preserves position order)
        Map<Integer, Integer> requiredByInventory = new LinkedHashMap<>();
        for (InventoryRequirement req : applicable) {
            requiredByInventory.merge(req.inventoryId(), req.quantity(), Integer::sum);
        }

        // Count assigned items per inventory
        List<InventoryItem> assignedItems = inventoryRepository.findItemsByMember(memberId);
        Map<Integer, Integer> assignedByInventory = new HashMap<>();
        for (InventoryItem item : assignedItems) {
            assignedByInventory.merge(item.inventoryId(), 1, Integer::sum);
        }

        // Build result
        List<RequiredInventoryItem> result = new ArrayList<>();
        for (var entry : requiredByInventory.entrySet()) {
            int inventoryId = entry.getKey();
            int requiredQty = entry.getValue();
            int assignedQty = assignedByInventory.getOrDefault(inventoryId, 0);
            Inventory inv = inventoryRepository.findById(inventoryId).orElse(null);
            String invName = inv != null ? inv.name() : "#" + inventoryId;
            String invType = inv != null ? inv.inventoryType().name() : "INTERNAL";
            boolean hasSizes = inv != null && inv.hasSizes();
            List<InventorySize> sizes = hasSizes ? inventoryRepository.findSizes(inventoryId) : List.of();
            result.add(new RequiredInventoryItem(
                    inventoryId, invName, invType, hasSizes, sizes, requiredQty, assignedQty));
        }
        return result;
    }

    public record MemberCheckState(
            String memberName,
            List<RequiredInventoryItem> required,
            List<InventoryItem> assigned,
            InventoryCheck lastCheck,
            Map<Integer, List<InventoryItem>> unassigned) {}

    public record RequiredInventoryItem(
            int inventoryId,
            String inventoryName,
            String inventoryType,
            boolean hasSizes,
            List<InventorySize> sizes,
            int requiredQuantity,
            int assignedQuantity) {}

    public record CheckItemRequest(Integer itemId, Integer inventoryId, CheckResult result, String note) {}

    public record EnrichedCheckDetail(
            InventoryCheck check, String checkerFirstName, String checkerLastName, List<EnrichedCheckItem> items) {}

    public record EnrichedCheckItem(
            int id,
            Integer itemId,
            String itemName,
            String internalId,
            String inventoryName,
            String sizeName,
            CheckResult result,
            String note) {}
}
