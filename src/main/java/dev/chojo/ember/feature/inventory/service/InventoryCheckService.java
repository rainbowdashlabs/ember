/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
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
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCheckHistoryEntry;
import dev.chojo.ember.feature.inventory.entity.ItemCorrection;
import dev.chojo.ember.feature.inventory.entity.ItemLastCheck;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.RequiredInventoryItem;
import dev.chojo.ember.feature.inventory.entity.SelfCheck;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository.MemberCheckSummary;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.SelfCheckRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(InventoryCheckService.class);

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
    private final InventoryContainerService containerService;
    private final ItemCustodyService custodyService;
    private final InventoryService inventoryService;
    private final SelfCheckRepository selfCheckRepository;

    @Inject
    public InventoryCheckService(
            InventoryCheckRepository checkRepository,
            InventoryRepository inventoryRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            AccountRepository accountRepository,
            MemberIdentityFactory memberIdentityFactory,
            InventoryContainerService containerService,
            ItemCustodyService custodyService,
            InventoryService inventoryService,
            SelfCheckRepository selfCheckRepository) {
        this.selfCheckRepository = selfCheckRepository;
        this.checkRepository = checkRepository;
        this.inventoryRepository = inventoryRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.accountRepository = accountRepository;
        this.memberIdentityFactory = memberIdentityFactory;
        this.containerService = containerService;
        this.custodyService = custodyService;
        this.inventoryService = inventoryService;
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
        boolean begun = acquireLock(stationId, memberId, lockedBy);
        List<SelfCheck> overtaken = begun ? overtakeSelfChecks(memberId) : List.of();
        return checkState(stationId, memberId, overtaken);
    }

    /**
     * Closes whatever the member had been asked to answer for themselves, keeping what they said and
     * applying none of it.
     *
     * <p>This is the reverse of the lock above, and deliberately so. The lock stops two checkers
     * walking one member, which is a collision. A member's report and a checker's walk are two
     * sources of different quality about the same thing, and the better source does not wait for the
     * worse one. What the member set going without waiting, an exchange or a loss, is untouched: it
     * was never part of the submission.
     *
     * @param memberId the member being walked
     * @return the tasks this walk closed, as they stood before
     */
    private List<SelfCheck> overtakeSelfChecks(int memberId) {
        List<SelfCheck> closed = new ArrayList<>();
        for (SelfCheck task : selfCheckRepository.findUnfinishedForMembers(List.of(memberId))) {
            if (selfCheckRepository.overtake(task.id())) closed.add(task);
        }
        if (!closed.isEmpty()) {
            log.info("A checker's walk overtook {} self-check(s) of member {}", closed.size(), memberId);
        }
        return closed;
    }

    /**
     * Takes the lock that stops two checkers walking the same member at once, or confirms the caller
     * already holds it.
     *
     * <p>Kept apart from reading the state because what looks like a start is also the load path: the
     * check screen calls it again after every assignment, and anything hung on it would fire several
     * times through one walk. What the beginning carries and the loading does not is closing whatever
     * the member had been asked to answer for themselves.
     *
     * @return {@code true} where this call is the one that began the walk
     */
    private boolean acquireLock(int stationId, int memberId, int lockedBy) {
        checkRepository.releaseExpiredLocks(LOCK_TIMEOUT_MINUTES);

        var existingLock = checkRepository.findLock(memberId);
        if (existingLock.isPresent()) {
            if (existingLock.get().lockedBy() != lockedBy) {
                throw new ConflictResponse("Member is already being checked by another user");
            }
            return false;
        }
        checkRepository.releaseLockByLocker(lockedBy);
        Optional<InventoryCheckLock> lock = checkRepository.acquireLock(stationId, memberId, lockedBy);
        if (lock.isEmpty()) {
            throw new ConflictResponse("Member is already being checked by another user");
        }
        log.info("Started check on member {} by member {} (station={})", memberId, lockedBy, stationId);
        return true;
    }

    /**
     * What the station has recorded against a member: what their role asks of them, what they are
     * holding towards it, and when they were last checked.
     *
     * <p>Nothing is locked and nothing is begun. This is what a member reading their own list gets,
     * so it carries no free stock: the shape a walk returns names every unheld piece of the station,
     * and that is a checker's view of the store rather than an answer about one person's boots.
     *
     * @param stationId the station ID
     * @param memberId  the member to read
     * @return the member's own gear and what is required of them
     */
    public MemberGear readGear(int stationId, int memberId) {
        var member = stationMemberRepository.findById(memberId).orElseThrow();
        var account = accountRepository.findById(member.accountId()).orElseThrow();

        var required = getRequiredItems(stationId, memberId);
        var assigned = inventoryRepository.findItemsByMember(memberId);
        var lastCheck = checkRepository.latestCheckForMember(memberId).orElse(null);
        MemberIdentity identity = memberIdentityFactory.local(stationId, memberId);
        return new MemberGear(account.fullName(), identity, required, assigned, lastCheck);
    }

    /**
     * The walk's own view, which is the member's gear widened by the free stock a checker may hand
     * out from.
     */
    private MemberCheckState checkState(int stationId, int memberId, List<SelfCheck> overtaken) {
        MemberGear gear = readGear(stationId, memberId);
        Map<Integer, List<InventoryItem>> unassigned = new HashMap<>();
        for (RequiredInventoryItem req : gear.required()) {
            unassigned.put(req.inventoryId(), inventoryRepository.findUnassignedItems(req.inventoryId()));
        }
        return new MemberCheckState(
                gear.memberName(),
                gear.memberIdentity(),
                gear.required(),
                gear.assigned(),
                gear.lastCheck(),
                unassigned,
                overtaken);
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

            markMissing(result, checkedBy);
        }

        checkRepository.releaseLock(memberId);
        log.info(
                "Completed check {} on member {} by member {} (station={}, results={})",
                check.id(),
                memberId,
                checkedBy,
                stationId,
                results.size());
        return check;
    }

    /**
     * Returns the expected items for a container-scope check. With
     * {@code deep = true} the walk includes every descendant container's
     * items, depth-first; with {@code deep = false} only the direct items
     * placed in the container are returned.
     *
     * @param containerId target container
     * @param deep        whether the walk includes descendants
     * @return the items the operator should find in the container
     */
    public List<InventoryItem> expectedContainerItems(int containerId, boolean deep) {
        return deep
                ? containerService.findItemsInSubtree(containerId)
                : containerService.findItemsInContainer(containerId);
    }

    /**
     * Returns the most recent check result for each item currently expected in the given
     * container, so the walk UI can show "last checked at X, was Y" next to each expected row.
     *
     * @param containerId the container being walked
     * @param deep        whether the walk covers descendants too
     * @return one entry per item that has at least one prior check; never-checked items are
     *         simply absent from the list
     */
    public List<ItemLastCheck> lastCheckForContainerItems(int containerId, boolean deep) {
        List<InventoryItem> expected = expectedContainerItems(containerId, deep);
        List<Integer> itemIds = new ArrayList<>(expected.size());
        for (InventoryItem item : expected) itemIds.add(item.id());
        return checkRepository.latestCheckPerItem(itemIds);
    }

    /**
     * Returns every recorded check for an item, newest-first, for display on the item-detail page.
     */
    public List<ItemCheckHistoryEntry> findCheckHistoryForItem(int itemId) {
        return checkRepository.findCheckHistoryForItem(itemId);
    }

    /**
     * Completes a container-scope check by writing the {@code inventory_check}
     * row plus per-item results, marking any LOST items, and returning the
     * created check.
     *
     * @param stationId   the station ID
     * @param containerId target container
     * @param checkedBy   the member performing the check
     * @param deep        whether the walk included descendants
     * @param results     per-item results
     * @return the created check
     */
    public InventoryCheck completeContainerCheck(
            int stationId, int containerId, int checkedBy, boolean deep, List<CheckItemRequest> results) {
        InventoryCheck check = checkRepository.createContainerCheck(stationId, containerId, checkedBy, deep);
        for (CheckItemRequest result : results) {
            checkRepository.createCheckItem(
                    check.id(), result.itemId(), result.inventoryId(), result.result(), result.note());
            markMissing(result, checkedBy);
        }
        log.info(
                "Completed container check {} on container {} by member {} (station={}, deep={}, results={})",
                check.id(),
                containerId,
                checkedBy,
                stationId,
                deep,
                results.size());
        return check;
    }

    /**
     * Writes the loss a walk found, where there is a loss to write and it is this station's to write.
     *
     * <p>Borrowed gear is walked with everything else, because it is in the building and a shelf that
     * skips a third of what is on it is not a shelf that has been walked. What it does not get is the
     * loss: the borrower's row is a copy that goes away when the loan ends, and writing a loss on it
     * would leave the owner's row still saying a partner has the piece. The check keeps the result, so
     * the walk still says plainly that the piece was not there, and telling the owner happens on the
     * lending request the gear came in on.
     *
     * @param result    one line of the walk
     * @param checkedBy the member walking it
     */
    private void markMissing(CheckItemRequest result, int checkedBy) {
        if (result.result() != CheckResult.LOST || result.itemId() == null) return;
        boolean borrowed = inventoryRepository
                .findItemById(result.itemId())
                .map(InventoryItem::borrowed)
                .orElse(false);
        if (borrowed) {
            log.info(
                    "Check found borrowed item {} missing; the loss stays on the check and goes to the owner "
                            + "on the lending request",
                    result.itemId());
            return;
        }
        custodyService.markLost(result.itemId(), result.note(), checkedBy);
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
            log.info("Cancelled check on member {} by member {}", memberId, lockedBy);
        } else {
            log.warn("Cancel skipped: no matching lock on member {} held by member {}", memberId, lockedBy);
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
     * <p>A piece handed in for an exchange counts towards what the member has. The question here is
     * whether they are equipped, not what is in their hands this minute, and an exchange over the body
     * above the station takes weeks: counting the assignment alone would report a gap for all of it and
     * send whoever walks the check off to order a jacket that is already in the post. The row says how
     * many of them are away that way, so nobody is left wondering at a number that does not add up.
     *
     * @param stationId the station ID
     * @param memberId  the member ID
     * @return list of required inventory items with quantities and assignment counts
     */
    public List<RequiredInventoryItem> getRequiredItems(int stationId, int memberId) {
        var member = stationMemberRepository.findById(memberId).orElse(null);
        StationUserType memberUserType = member != null ? member.userType() : null;
        List<MemberGroup> memberGroups = memberGroupRepository.findGroupsForMember(memberId);
        // The cluster's requirements count here too: one definition, read at the station, never copied
        List<InventoryRequirement> allRequirements = inventoryRepository.findRequirementsCountingAt(stationId);

        var memberGroupIds = memberGroups.stream().map(MemberGroup::id).toList();

        // Filter requirements applicable to this member
        List<InventoryRequirement> applicable = allRequirements.stream()
                .filter(req -> (req.userType() != null && req.userType() == memberUserType)
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

        Map<Integer, Integer> inExchangeByInventory = new HashMap<>();
        for (InventoryItem item : inventoryRepository.findItemsAwayInExchange(memberId)) {
            inExchangeByInventory.merge(item.inventoryId(), 1, Integer::sum);
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
            InventoryType invType = inv != null ? inv.inventoryType() : InventoryType.INTERNAL;
            boolean hasSizes = inv != null && inv.hasSizes();
            List<InventorySize> sizes = hasSizes ? inventoryRepository.findSizes(inventoryId) : List.of();
            result.add(new RequiredInventoryItem(
                    inventoryId,
                    invName,
                    invType,
                    hasSizes,
                    sizes,
                    requiredQty,
                    assignedQty,
                    inExchangeByInventory.getOrDefault(inventoryId, 0)));
        }
        return result;
    }

    /**
     * Puts right what a check found: the member is holding something other than what the record says.
     *
     * <p>Nothing changes hands here. The piece named in the correction is already in the member's
     * hands, so this is one write of the truth and not a movement: no exchange is raised, no chain is
     * walked, and the piece coming off the record was never really out.
     *
     * <p>Where the old piece goes follows from who owns it, and the caller is not asked. The station's
     * own goes back into the station's store. The owner's goes back to the owner where the owner runs
     * on this instance and so has a store of its own. Where it does not, there is no store to go back
     * to and nobody who could ever tidy the row up, so the piece is deleted rather than left standing
     * as a record of something that, as the correction says, was never there.
     *
     * @param memberId   the member being checked
     * @param correction what the member actually holds
     * @return the piece the member holds once the record agrees with them
     * @throws NotFoundResponse   if the inventory or either piece is unknown
     * @throws BadRequestResponse if the old piece is not the member's, the new one is not free, or a
     *                            mixed inventory was not told who owns the new piece
     */
    public InventoryItem correct(int memberId, ItemCorrection correction) {
        Inventory inventory = inventoryRepository
                .findById(correction.inventoryId())
                .orElseThrow(() -> new NotFoundResponse("This inventory does not exist"));
        ItemOwner owner = ownerOfNewPiece(inventory, correction);
        InventoryItem replacement = correction.picksFromStock()
                ? fromStock(correction.pickedItemId(), inventory.id())
                : inventoryService.createItem(
                        inventory.id(),
                        correction.internalId(),
                        inventory.name(),
                        correction.sizeId(),
                        correction.metadata(),
                        owner,
                        null);

        if (correction.replacesAPiece()) release(correction.oldItemId(), memberId);
        custodyService.assignToMember(replacement.id(), memberId, nameOf(memberId));
        log.info(
                "Check corrected member {}: piece {} replaced by {}",
                memberId,
                correction.oldItemId(),
                replacement.id());
        return inventoryRepository.findItemById(replacement.id()).orElse(replacement);
    }

    /**
     * The name the history keeps for a member, which is the member's own and not the checker's.
     */
    private String nameOf(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .flatMap(member -> accountRepository.findById(member.accountId()))
                .map(Account::fullName)
                .orElse("");
    }

    /**
     * Who owns a piece a correction makes. Only an inventory that holds both owners has to be told;
     * anywhere else the inventory itself is the answer and asking would be a question with one option.
     */
    private static ItemOwner ownerOfNewPiece(Inventory inventory, ItemCorrection correction) {
        return switch (inventory.inventoryType()) {
            case INTERNAL -> ItemOwner.STATION;
            case EXTERNAL -> ItemOwner.CLUSTER;
            case MIXED -> {
                if (correction.ownerKind() == null) {
                    throw new BadRequestResponse("This inventory holds both owners, so the new piece needs one named");
                }
                // Gear belonging to a partner arrives by handover and by nothing else. A correction
                // writing a new piece is the station saying what it has, and it cannot say that
                // about somebody else's radio.
                if (correction.ownerKind() == ItemOwner.PARTNER_STATION) {
                    throw new BadRequestResponse("A new piece cannot be written down as a partner station's");
                }
                yield correction.ownerKind();
            }
        };
    }

    /**
     * The free piece a correction picked, once it is certain it is free and belongs to the inventory
     * the correction is about.
     */
    private InventoryItem fromStock(int itemId, int inventoryId) {
        InventoryItem item = inventoryRepository
                .findItemById(itemId)
                .orElseThrow(() -> new NotFoundResponse("This piece does not exist"));
        if (item.inventoryId() != inventoryId) {
            throw new BadRequestResponse("This piece sits in another inventory");
        }
        if (item.assignedTo() != null) {
            throw new BadRequestResponse("This piece is already with somebody");
        }
        return item;
    }

    /**
     * Takes the wrongly recorded piece off the member and sends it where its owner keeps it, or ends
     * it where nobody keeps it.
     */
    private void release(int itemId, int memberId) {
        InventoryItem item = inventoryRepository
                .findItemById(itemId)
                .orElseThrow(() -> new NotFoundResponse("This piece does not exist"));
        if (item.assignedTo() == null || item.assignedTo() != memberId) {
            throw new BadRequestResponse("This piece is not on this member's record");
        }
        if (item.ownerKind() == ItemOwner.CLUSTER && item.ownerClusterId() == null) {
            inventoryService.deleteItem(itemId, null);
            return;
        }
        inventoryRepository.markSpellCorrected(itemId, memberId);
        // Only gear the body above the station owns goes back to an owner with a store of its own.
        // A borrowed piece rests on the shelf of the station that borrowed it, exactly as its own
        // gear does, so it is taken back rather than sent anywhere.
        if (item.ownerKind() == ItemOwner.CLUSTER) custodyService.returnToOwner(itemId);
        else custodyService.takeBack(itemId);
    }

    /**
     * The state of an inventory check for a member, including requirements, assigned items, and available unassigned items.
     *
     * @param memberName the member's full name
     * @param required   the list of required inventory items
     * @param assigned   the items currently assigned to the member
     * @param lastCheck  the member's most recent check, or {@code null} if never checked
     * @param unassigned available unassigned items per inventory, keyed by inventory ID
     * @param overtookSelfChecks the tasks this walk closed, so the walker is told a member had been
     *                           asked to answer for themselves and that their answers are not applied
     */
    public record MemberCheckState(
            String memberName,
            MemberIdentity memberIdentity,
            List<RequiredInventoryItem> required,
            List<InventoryItem> assigned,
            InventoryCheck lastCheck,
            Map<Integer, List<InventoryItem>> unassigned,
            List<SelfCheck> overtookSelfChecks) {}

    /**
     * What the station has recorded against one member, and nothing about anybody else's gear.
     *
     * @param memberName     the member's full name
     * @param memberIdentity the member as the rest of the instance names them
     * @param required       what their role asks of them
     * @param assigned       what they are holding towards it
     * @param lastCheck      their most recent check, or {@code null} if never checked
     */
    public record MemberGear(
            String memberName,
            MemberIdentity memberIdentity,
            List<RequiredInventoryItem> required,
            List<InventoryItem> assigned,
            InventoryCheck lastCheck) {}
}
