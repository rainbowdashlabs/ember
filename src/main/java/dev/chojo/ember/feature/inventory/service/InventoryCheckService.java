/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.CheckItemRequest;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.EnrichedCheckDetail;
import dev.chojo.ember.feature.inventory.entity.EnrichedCheckItem;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryCheck;
import dev.chojo.ember.feature.inventory.entity.InventoryCheckLock;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.RequiredInventoryItem;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository.MemberCheckSummary;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.ConflictResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing inventory checks on members.
 * Handles the check workflow including locking, item verification, requirement calculation, and check completion.
 */
@Singleton
public class InventoryCheckService {
    /**
     * Maximum duration in minutes before a check lock expires automatically.
     */
    private static final int LOCK_TIMEOUT_MINUTES = 30;

    private final InventoryCheckRepository checkRepository;
    private final InventoryRepository inventoryRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final AccountRepository accountRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public InventoryCheckService(
            InventoryCheckRepository checkRepository,
            InventoryRepository inventoryRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            AccountRepository accountRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.checkRepository = checkRepository;
        this.inventoryRepository = inventoryRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.accountRepository = accountRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    /**
     * Retrieves the check overview for all members of a station, enriched with member roles.
     * Automatically releases expired locks before building the overview.
     *
     * @param stationId the station ID
     * @return list of member check summaries with roles
     */
    public List<MemberCheckSummary> getCheckOverview(int stationId) {
        checkRepository.releaseExpiredLocks(LOCK_TIMEOUT_MINUTES);
        return checkRepository.checkOverview(stationId);
    }

    /**
     * Starts an inventory check for a member by acquiring a lock and loading the check state.
     * If the same checker already holds the lock, the check continues. Otherwise, a new lock is acquired
     * and any previous lock held by this checker is released.
     *
     * @param stationId the station ID
     * @param memberId  the member to check
     * @param lockedBy  the member performing the check
     * @return the current check state including required items, assigned items, and unassigned items
     * @throws ConflictResponse if the member is already locked by a different checker
     */
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

        MemberIdentity identity = memberIdentityFactory.local(stationId, memberId);
        return new MemberCheckState(memberName, identity, required, assigned, lastCheck, unassigned);
    }

    /**
     * Completes an inventory check by recording all item results, marking lost items, and releasing the lock.
     *
     * @param stationId the station ID
     * @param memberId  the member being checked
     * @param checkedBy the member performing the check
     * @param results   the check results for each item
     * @return the created inventory check record
     */
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

    /**
     * Cancels an ongoing inventory check by releasing the lock, only if the caller holds it.
     *
     * @param memberId the member whose check to cancel
     * @param lockedBy the member who should hold the lock
     */
    public void cancelCheck(int memberId, int lockedBy) {
        var lock = checkRepository.findLock(memberId);
        if (lock.isPresent() && lock.get().lockedBy() == lockedBy) {
            checkRepository.releaseLock(memberId);
        }
    }

    /**
     * Retrieves the last check detail for a member, enriched with item names, inventory names, and size labels.
     *
     * @param memberId the member ID
     * @return the enriched check detail, or empty if no checks exist
     */
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

    /**
     * Finds the next member to check based on who was checked least recently.
     *
     * @param stationId       the station ID
     * @param currentMemberId the current member to exclude
     * @return the next member ID, or empty if none available
     */
    public Optional<Integer> nextMember(int stationId, int currentMemberId, boolean teamOnly) {
        return checkRepository.nextUncheckedMember(stationId, currentMemberId, teamOnly);
    }

    /**
     * Calculates the inventory items required for a member based on their roles and groups.
     * Aggregates requirement quantities per inventory and compares against currently assigned items.
     *
     * @param stationId the station ID
     * @param memberId  the member ID
     * @return list of required inventory items with quantities and assignment counts
     */
    public List<RequiredInventoryItem> getRequiredItems(int stationId, int memberId) {
        var member = stationMemberRepository.findById(memberId).orElse(null);
        String memberUserType = member != null ? member.userType().name() : null;
        List<MemberGroup> memberGroups = memberGroupRepository.findGroupsForMember(memberId);
        List<InventoryRequirement> allRequirements = inventoryRepository.findAllRequirementsByStation(stationId);

        var memberGroupIds = memberGroups.stream().map(MemberGroup::id).toList();

        // Filter requirements applicable to this member
        List<InventoryRequirement> applicable = allRequirements.stream()
                .filter(req -> (req.userType() != null && req.userType().equals(memberUserType))
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

    /**
     * The state of an inventory check for a member, including requirements, assigned items, and available unassigned items.
     *
     * @param memberName the member's full name
     * @param required   the list of required inventory items
     * @param assigned   the items currently assigned to the member
     * @param lastCheck  the member's most recent check, or {@code null} if never checked
     * @param unassigned available unassigned items per inventory, keyed by inventory ID
     */
    public record MemberCheckState(
            String memberName,
            MemberIdentity memberIdentity,
            List<RequiredInventoryItem> required,
            List<InventoryItem> assigned,
            InventoryCheck lastCheck,
            Map<Integer, List<InventoryItem>> unassigned) {}
}
