/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterStationGroup;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterStationGroupRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryFieldDefinition;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemHistory;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventorySummary;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemFieldValues;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MemberInventoryEntry;
import dev.chojo.ember.feature.inventory.entity.SwitchBlocker;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing inventories, their items, sizes, history, and requirements.
 * Provides business logic on top of the repository, including item assignment with history tracking.
 */
@Singleton
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;
    private final InventoryFieldDefinitionService fieldService;
    private final ClusterRepository clusterRepository;
    private final ClusterStationGroupRepository stationGroupRepository;
    private final ItemCustodyService custodyService;

    @Inject
    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryArtRepository artRepository,
            InventoryFieldDefinitionService fieldService,
            ItemCustodyService custodyService,
            ClusterRepository clusterRepository,
            ClusterStationGroupRepository stationGroupRepository) {
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
        this.fieldService = fieldService;
        this.custodyService = custodyService;
        this.clusterRepository = clusterRepository;
        this.stationGroupRepository = stationGroupRepository;
    }

    // -- Inventories --

    /**
     * Finds all inventories for a station.
     *
     * @param stationId the station ID
     * @return list of inventories
     */
    public List<Inventory> findByStation(int stationId) {
        return inventoryRepository.findByStation(stationId);
    }

    public List<InventorySummary> findSummaries(int stationId) {
        return inventoryRepository.findSummariesByStation(stationId);
    }

    public List<InventoryItem> findAllItemsByStation(int stationId) {
        return inventoryRepository.findItemsByStation(stationId);
    }

    public List<InventorySize> findAllSizesByStation(int stationId) {
        return inventoryRepository.findSizesByStation(stationId);
    }

    /**
     * Finds an inventory by its ID.
     *
     * @param id the inventory ID
     * @return the inventory, or empty if not found
     */
    public Optional<Inventory> findById(int id) {
        return inventoryRepository.findById(id);
    }

    /**
     * Finds all sizes for an inventory.
     *
     * @param inventoryId the inventory ID
     * @return list of sizes
     */
    public List<InventorySize> findSizes(int inventoryId) {
        return inventoryRepository.findSizes(inventoryId);
    }

    /**
     * Creates a new inventory.
     *
     * @param stationId     the station ID
     * @param name          the inventory name
     * @param inventoryType the inventory type
     * @param hasSizes      whether the inventory supports sizes
     * @param homogeneous   whether it holds one thing in many copies rather than a drawer of different things
     * @return the created inventory
     */
    public Inventory create(
            int stationId, String name, InventoryType inventoryType, boolean hasSizes, boolean homogeneous) {
        boolean sizes = hasSizes && homogeneous;
        Inventory inventory = inventoryRepository.create(stationId, name, inventoryType, sizes, homogeneous);
        log.info(
                "Created inventory {} (name='{}', type={}, hasSizes={}, homogeneous={}) in station {}",
                inventory.id(),
                name,
                inventoryType,
                sizes,
                homogeneous,
                stationId);
        return inventory;
    }

    /**
     * Updates an existing inventory.
     *
     * <p>Changing what kind of thing it holds is refused while something live still depends on the
     * kind being left, and the refusal names every one of those things. Putting the size list away is
     * refused for the same reason and in the same words: the sizes the items are carrying would be
     * left pointing at a list nothing shows any more.
     *
     * @param id            the inventory ID
     * @param name          the new name
     * @param inventoryType the new type
     * @param hasSizes      whether sizes are supported
     * @param homogeneous   whether it holds one thing in many copies rather than a drawer of different things
     * @return the updated inventory, or empty if not found
     * @throws InventorySwitchRefusedException when something live depends on the state being left
     */
    public Optional<Inventory> update(
            int id, String name, InventoryType inventoryType, boolean hasSizes, boolean homogeneous) {
        Inventory before = inventoryRepository.findById(id).orElse(null);
        if (before != null && before.homogeneous() != homogeneous) {
            requireNothingDependsOnIt(before, homogeneous);
        }
        if (before != null && before.hasSizes() && !hasSizes) {
            requireNoSizesLeft(before);
        }
        boolean sizes = hasSizes && homogeneous;
        if (inventoryRepository.update(id, name, inventoryType, sizes, homogeneous)) {
            log.info(
                    "Updated inventory {} (name='{}', type={}, hasSizes={}, homogeneous={})",
                    id,
                    name,
                    inventoryType,
                    sizes,
                    homogeneous);
            return inventoryRepository.findById(id);
        }
        log.warn("Update of inventory {} did not change any row", id);
        return Optional.empty();
    }

    /**
     * Everything live that would have to go before an inventory could change what kind of thing it
     * holds.
     *
     * <p>Leaving the homogeneous half strands the three features that only make sense there, plus the
     * size list, which belongs to that half and whose values the items are already carrying. Coming
     * back the other way is blocked by the kinds the inventory has been given, for the mirror reason:
     * the pieces are carrying those, and a kind cannot exist in an inventory of one thing in many
     * copies.
     *
     * @param inventory     the inventory as it stands
     * @param toHomogeneous the kind it is being asked to become
     * @return what stands in the way, empty when nothing does
     */
    public List<SwitchBlocker> blockersForSwitch(Inventory inventory, boolean toHomogeneous) {
        if (toHomogeneous) {
            // The kinds are the one thing that lives only on the heterogeneous side. Going back with
            // them still there would leave every piece pointing at a level the inventory no longer has.
            return inventoryRepository.findArtBlockers(inventory.id());
        }
        var blockers = new ArrayList<SwitchBlocker>();
        blockers.addAll(inventoryRepository.findRequirementBlockers(inventory.id()));
        blockers.addAll(inventoryRepository.findOpenProcurementBlockers(inventory.id()));
        blockers.addAll(inventoryRepository.findOpenExchangeBlockers(inventory.id()));
        blockers.addAll(inventoryRepository.findSizeBlockers(inventory.id()));
        return List.copyOf(blockers);
    }

    private void requireNothingDependsOnIt(Inventory inventory, boolean toHomogeneous) {
        var blockers = blockersForSwitch(inventory, toHomogeneous);
        if (blockers.isEmpty()) return;
        log.info(
                "Refused to switch inventory {} to {}: {} things depend on it",
                inventory.id(),
                toHomogeneous ? "homogeneous" : "heterogeneous",
                blockers.size());
        throw new InventorySwitchRefusedException(
                toHomogeneous
                        ? "This inventory still holds things that only exist for a drawer of different things"
                        : "Requirements, orders, exchanges and sizes only exist for an inventory of one thing, and this one still has some",
                blockers);
    }

    /**
     * Refuses putting the size list away while there is still a list.
     *
     * <p>The sizes the items carry point at rows of it, so hiding the list would leave every one of
     * them naming something no screen shows any more. Emptying the list first is the same act done in
     * the right order, and the refusal names the sizes so it is clear what has to go.
     */
    private void requireNoSizesLeft(Inventory inventory) {
        var blockers = inventoryRepository.findSizeBlockers(inventory.id());
        if (blockers.isEmpty()) return;
        log.info("Refused to drop the size list of inventory {}: {} sizes are on it", inventory.id(), blockers.size());
        throw new InventorySwitchRefusedException(
                "The size list still has sizes on it, and the pieces are carrying them", blockers);
    }

    /**
     * Refuses one of the three features that only mean something for an inventory of one thing in
     * many copies.
     *
     * <p>The pickers offer only the inventories where these make sense, so this is the second line
     * rather than the first: a request that reached here named a drawer of different things anyway.
     *
     * @param inventoryId the inventory the feature would point at
     * @param what        the feature, named as the refusal should read
     * @throws BadRequestResponse when the inventory holds a drawer of different things
     */
    public void requireHomogeneous(int inventoryId, String what) {
        boolean homogeneous = inventoryRepository
                .findById(inventoryId)
                .map(Inventory::homogeneous)
                .orElseThrow(() -> new BadRequestResponse("That inventory does not exist"));
        if (!homogeneous) {
            throw new BadRequestResponse(
                    "This inventory holds a drawer of different things, so " + what + " does not apply to it");
        }
    }

    /**
     * Deletes an inventory.
     *
     * <p>The shelf for gear belonging to somebody else is refused while anything is still on it.
     * Deleting it would take away rows the station does not own and cannot replace, and the loans
     * they came in on would have nothing to hand back. Once it is empty it goes like any other, and
     * the next handover makes a new one.
     *
     * @param id the inventory ID
     * @return {@code true} if deleted
     * @throws BadRequestResponse when it is the borrowed shelf and it still holds something
     */
    public boolean delete(int id) {
        requireEmptyIfBorrowed(id);
        boolean deleted = inventoryRepository.delete(id);
        if (deleted) log.info("Deleted inventory {}", id);
        else log.warn("Delete of inventory {} did not change any row", id);
        return deleted;
    }

    /**
     * Keeps the shelf for gear belonging to somebody else holding exactly that.
     *
     * <p>It answers one question, "what have we got here that is not ours", and a piece of the
     * station's own filed on it makes the answer wrong. Nothing reaches it except a handover.
     *
     * @param inventoryId the inventory something would be written into or moved onto
     * @throws BadRequestResponse when it is the borrowed shelf
     */
    private void requireNotTheBorrowedShelf(int inventoryId) {
        boolean borrowed = inventoryRepository
                .findById(inventoryId)
                .map(Inventory::borrowed)
                .orElse(false);
        if (borrowed) {
            throw new BadRequestResponse("This shelf only holds gear borrowed from a partner station");
        }
    }

    private void requireEmptyIfBorrowed(int id) {
        Inventory inventory = inventoryRepository.findById(id).orElse(null);
        if (inventory == null || !inventory.borrowed()) return;
        if (inventoryRepository.findItems(id).isEmpty()) return;
        log.info("Refused to delete the borrowed shelf of inventory {}: it still holds gear", id);
        throw new BadRequestResponse("This shelf still holds gear belonging to somebody else");
    }

    // -- Sizes --

    /**
     * Creates a new size and returns all sizes for the inventory.
     *
     * @param inventoryId the inventory ID
     * @param label       the size label
     * @param position    the sort position
     * @param note        an optional note
     * @return the updated list of all sizes for the inventory
     */
    public List<InventorySize> createSize(int inventoryId, String label, int position, String note) {
        requireHomogeneous(inventoryId, "a size list");
        inventoryRepository.createSize(inventoryId, label, position, note);
        log.info("Created size (label='{}', position={}) in inventory {}", label, position, inventoryId);
        return inventoryRepository.findSizes(inventoryId);
    }

    /**
     * Updates a size and returns all sizes for the inventory.
     *
     * @param inventoryId the inventory ID
     * @param sizeId      the size ID
     * @param label       the new label
     * @param position    the new position
     * @param note        the new note
     * @return the updated list of sizes, or empty if the size was not found
     */
    public Optional<List<InventorySize>> updateSize(
            int inventoryId, int sizeId, String label, int position, String note) {
        if (inventoryRepository.updateSize(sizeId, label, position, note)) {
            log.info("Updated size {} (label='{}', position={}) in inventory {}", sizeId, label, position, inventoryId);
            return Optional.of(inventoryRepository.findSizes(inventoryId));
        }
        log.warn("Update of size {} did not change any row", sizeId);
        return Optional.empty();
    }

    /**
     * Deletes a size and returns the remaining sizes for the inventory.
     *
     * @param inventoryId the inventory ID
     * @param sizeId      the size ID to delete
     * @return the remaining sizes, or empty if the size was not found
     */
    public Optional<List<InventorySize>> deleteSize(int inventoryId, int sizeId) {
        if (inventoryRepository.deleteSize(sizeId)) {
            log.info("Deleted size {} from inventory {}", sizeId, inventoryId);
            return Optional.of(inventoryRepository.findSizes(inventoryId));
        }
        log.warn("Delete of size {} did not change any row", sizeId);
        return Optional.empty();
    }

    // -- Items --

    /**
     * Finds all items assigned to a member.
     *
     * @param memberId the member ID
     * @return list of assigned items
     */
    public List<InventoryItem> findItemsByMember(int memberId) {
        return inventoryRepository.findItemsByMember(memberId);
    }

    /**
     * A member's own inventory, which is what they hold plus whatever is on its way to or from them.
     * Use this wherever a member reads their own list; {@link #findItemsByMember(int)} answers the
     * narrower question of what is actually in their hands.
     *
     * @param memberId the member
     * @return their items, each with the movement it is on when there is one
     */
    public List<MemberInventoryEntry> findMemberEntries(int memberId) {
        return inventoryRepository.findMemberEntries(memberId);
    }

    public int countItemsByMember(int memberId) {
        return inventoryRepository.countItemsByMember(memberId);
    }

    /**
     * Finds all items in an inventory.
     *
     * @param inventoryId the inventory ID
     * @return list of items
     */
    public List<InventoryItem> findItems(int inventoryId) {
        return inventoryRepository.findItems(inventoryId);
    }

    /**
     * What an inventory actually holds, which leaves out whatever is in the post.
     *
     * @param inventoryId the inventory ID
     * @return the items that are here
     */
    public List<InventoryItem> findStock(int inventoryId) {
        return inventoryRepository.findStock(inventoryId);
    }

    /**
     * Finds an inventory item by its ID.
     *
     * @param id the item ID
     * @return the item, or empty if not found
     */
    public Optional<InventoryItem> findItemById(int id) {
        return inventoryRepository.findItemById(id);
    }

    public Optional<InventoryItem> findByInternalId(int stationId, String internalId) {
        return inventoryRepository.findByInternalId(stationId, internalId);
    }

    /**
     * Creates a new inventory item with the default item source.
     *
     * @param inventoryId the inventory ID
     * @param internalId  the internal identifier
     * @param name        the item name
     * @param sizeId      the size ID, or {@code null}
     * @param metadata    JSON metadata
     * @return the created item
     */
    public InventoryItem createItem(
            int inventoryId, String internalId, String name, Integer sizeId, InventoryItemMetadata metadata) {
        requireNotTheBorrowedShelf(inventoryId);
        InventoryItem item = inventoryRepository.createItem(inventoryId, internalId, name, sizeId, metadata);
        log.info(
                "Created item {} (name='{}', internalId='{}', sizeId={}) in inventory {}",
                item.id(),
                name,
                internalId,
                sizeId,
                inventoryId);
        return item;
    }

    /**
     * Creates a new inventory item with a named owner.
     *
     * @param inventoryId    the inventory ID
     * @param internalId     the internal identifier
     * @param name           the item name
     * @param sizeId         the size ID, or {@code null}
     * @param metadata       JSON metadata
     * @param ownerKind      who owns the item
     * @param ownerClusterId the owning body when it runs on this instance, or {@code null} when it does not
     * @return the created item
     * @throws BadRequestResponse when the named body is not the one this station answers to
     */
    public InventoryItem createItem(
            int inventoryId,
            String internalId,
            String name,
            Integer sizeId,
            InventoryItemMetadata metadata,
            ItemOwner ownerKind,
            Integer ownerClusterId) {
        return createItem(inventoryId, internalId, name, sizeId, null, metadata, ownerKind, ownerClusterId);
    }

    /**
     * Creates a new inventory item that names the kind of thing it is.
     *
     * <p>The kind sits beside the name and never replaces it: a piece may be called {@code Pager 01}
     * and be of the kind {@code Pager}, and both readings are wanted at once.
     *
     * <p>The overload without a kind is the one the five automatic paths use, and their pieces have
     * none. That is by design rather than a gap: stock-taking, hand-out, quick assign, check
     * correction and procurement fulfilment all run with nobody present to say what a thing is.
     *
     * @param inventoryId    the inventory ID
     * @param internalId     the internal identifier
     * @param name           the item name
     * @param sizeId         the size ID, or {@code null}
     * @param artId          the kind, or {@code null} when nobody said
     * @param metadata       JSON metadata
     * @param ownerKind      who owns the item
     * @param ownerClusterId the owning body when it runs on this instance, or {@code null}
     * @return the created item
     * @throws BadRequestResponse when the named body is not the one this station answers to, or when
     *                            the kind belongs to another inventory
     */
    public InventoryItem createItem(
            int inventoryId,
            String internalId,
            String name,
            Integer sizeId,
            Integer artId,
            InventoryItemMetadata metadata,
            ItemOwner ownerKind,
            Integer ownerClusterId) {
        // Gear belonging to a partner arrives by handover and by nothing else, so it is never
        // written down here. The rows for it are the lending flow's to make and to take away again.
        if (ownerKind == ItemOwner.PARTNER_STATION) {
            throw new BadRequestResponse("Gear belonging to a partner station arrives by handover, not by hand");
        }
        requireNotTheBorrowedShelf(inventoryId);
        requireOwnerFits(inventoryId, ownerKind);
        Integer owner =
                ownerKind == ItemOwner.CLUSTER && ownerClusterId == null ? clusterAbove(inventoryId) : ownerClusterId;
        requireOwningCluster(inventoryId, owner);
        requireArtOfInventory(inventoryId, artId);
        InventoryItem item = inventoryRepository.createItem(
                inventoryId, internalId, name, sizeId, artId, metadata, ownerKind, owner);
        log.info(
                "Created item {} (name='{}', internalId='{}', sizeId={}, artId={}, owner={}, ownerClusterId={}) in inventory {}",
                item.id(),
                name,
                internalId,
                sizeId,
                artId,
                ownerKind,
                owner,
                inventoryId);
        return item;
    }

    /**
     * Refuses a kind that belongs somewhere else.
     *
     * <p>A kind belongs to exactly one inventory, so pairing a piece with a kind from another drawer
     * would describe nothing. No kind at all is always allowed and is the ordinary state.
     */
    private void requireArtOfInventory(int inventoryId, Integer artId) {
        if (artId == null) return;
        boolean fits = artRepository
                .findById(artId)
                .map(art -> art.inventoryId() == inventoryId)
                .orElse(false);
        if (!fits) throw new BadRequestResponse("That kind belongs to another inventory");
    }

    /**
     * The body above the station that keeps this inventory, when there is one.
     *
     * <p>A station recording gear it does not own means the one body above it: there is no second
     * candidate to choose between, so nothing asks. Without this the row says "somebody above us owns
     * this" and cannot say who, the association's own chains never reach it, and the station ends up
     * standing in for a body that is right there and could have answered for itself.
     *
     * @param inventoryId the inventory the gear goes into
     * @return the owning body, or {@code null} at a station that answers to nobody
     */
    private Integer clusterAbove(int inventoryId) {
        return inventoryRepository
                .findById(inventoryId)
                .flatMap(inv -> clusterRepository.findByStation(inv.stationId()))
                .map(Cluster::id)
                .orElse(null);
    }

    /**
     * Refuses an owner the inventory was not made to hold.
     *
     * <p>This is the one job the inventory's type still has. It used to stand in for the owner of every
     * item in it, which is where the mixed-inventory bug came from; now the item says who owns it and the
     * type only says which owners may be written down here. An inventory of the station's own things holds
     * nothing borrowed, one of borrowed things holds nothing of the station's own, and a mixed one is the
     * only place both may stand.
     *
     * @param inventoryId the inventory the item is going into
     * @param ownerKind   who would own it
     * @throws BadRequestResponse when this inventory is not for gear of that owner
     */
    private void requireOwnerFits(int inventoryId, ItemOwner ownerKind) {
        var type = inventoryRepository
                .findById(inventoryId)
                .map(Inventory::inventoryType)
                .orElseThrow(() -> new BadRequestResponse("That inventory does not exist"));
        boolean fits =
                switch (type) {
                    case MIXED -> true;
                    case INTERNAL -> ownerKind == ItemOwner.STATION;
                    case EXTERNAL -> ownerKind == ItemOwner.CLUSTER;
                };
        if (!fits) {
            throw new BadRequestResponse("This inventory does not hold gear owned by the "
                    + ownerKind.name().toLowerCase());
        }
    }

    /**
     * Refuses gear recorded as belonging to a body this station has nothing to do with.
     *
     * <p>There is never more than one body above a station, so naming a second one is not a choice being made
     * but a mistake or an attempt: gear pointed at somebody else's association would appear in that
     * association's list of what it owns, and it would be able to recall it. A station keeping gear for an
     * owner that does not run here names no body at all, which is what a null means and stays allowed.
     *
     * <p>A cluster's own station passes too, because that is where a cluster's store sits and it answers to
     * itself rather than joining anything.
     */
    /**
     * Refuses a group of stations that is not the association's whose inventory this is.
     *
     * <p>A requirement naming a group is an association saying where it applies, so the group has to be
     * one the same association filed. A station writing its own requirement names none, and passes here
     * without a query.
     */
    private void requireGroupOfTheOwningCluster(int inventoryId, Integer stationGroupId) {
        if (stationGroupId == null) return;
        int stationId = inventoryRepository
                .findById(inventoryId)
                .map(Inventory::stationId)
                .orElseThrow(() -> new BadRequestResponse("That inventory does not exist"));
        int groupCluster = stationGroupRepository
                .findById(stationGroupId)
                .map(ClusterStationGroup::clusterId)
                .orElseThrow(() -> new BadRequestResponse("That group of stations does not exist"));
        boolean itsOwnStore = clusterRepository
                .findById(groupCluster)
                .map(cluster -> cluster.homeStationId() == stationId)
                .orElse(false);
        if (!itsOwnStore) {
            throw new BadRequestResponse(
                    "A requirement can only name a group of stations of the association that wrote it");
        }
    }

    private void requireOwningCluster(int inventoryId, Integer ownerClusterId) {
        if (ownerClusterId == null) return;
        int stationId = inventoryRepository
                .findById(inventoryId)
                .map(Inventory::stationId)
                .orElseThrow(() -> new BadRequestResponse("That inventory does not exist"));

        boolean answersToIt = clusterRepository
                .findByStation(stationId)
                .map(cluster -> cluster.id() == ownerClusterId)
                .orElse(false);
        boolean isItsOwnStore = clusterRepository
                .findById(ownerClusterId)
                .map(cluster -> cluster.homeStationId() == stationId)
                .orElse(false);
        if (!answersToIt && !isItsOwnStore) {
            throw new BadRequestResponse(
                    "Gear can only be recorded as belonging to the association this station answers to");
        }
    }

    /**
     * Updates an inventory item, leaving the kind it carries alone.
     *
     * @param id              the item ID
     * @param internalId      the new internal identifier
     * @param name            the new name
     * @param sizeId          the new size ID, or {@code null}
     * @param metadata        the new JSON metadata
     * @param actingClusterId the body the caller answers for, or {@code null} when they act as the station
     * @return the updated item, or empty if not found
     */
    public Optional<InventoryItem> updateItem(
            int id,
            String internalId,
            String name,
            Integer sizeId,
            InventoryItemMetadata metadata,
            Integer actingClusterId) {
        Integer artId =
                inventoryRepository.findItemById(id).map(InventoryItem::artId).orElse(null);
        return updateItem(id, internalId, name, sizeId, artId, metadata, actingClusterId);
    }

    /**
     * Updates an inventory item, including which kind of thing it is.
     *
     * <p>Setting a kind leaves the name where it is. That is the whole reason the tidying screen has
     * a merge of its own: the name is what every list and both exports read, so a kind on its own
     * corrects nothing anybody can see.
     *
     * <p>Values whose describing field has gone, because the kind changed or was cleared, are kept
     * on the piece rather than thrown away. They stop being shown and they read again the day a kind
     * of that name comes back, because losing them is the one choice that cannot be undone.
     *
     * @param id              the item ID
     * @param internalId      the new internal identifier
     * @param name            the new name, which the kind never replaces
     * @param sizeId          the new size ID, or {@code null}
     * @param artId           the kind it is now, or {@code null} for none
     * @param metadata        the new JSON metadata
     * @param actingClusterId the body the caller answers for, or {@code null} when they act as the station
     * @return the updated item, or empty if not found
     */
    public Optional<InventoryItem> updateItem(
            int id,
            String internalId,
            String name,
            Integer sizeId,
            Integer artId,
            InventoryItemMetadata metadata,
            Integer actingClusterId) {
        requireOwned(id, "described", actingClusterId);
        InventoryItem before = inventoryRepository.findItemById(id).orElse(null);
        if (before == null) {
            log.warn("Update of item {} did not find a row to change", id);
            return Optional.empty();
        }
        requireArtOfInventory(before.inventoryId(), artId);
        InventoryItemMetadata kept = keepUndescribedValues(before, artId, metadata);
        if (inventoryRepository.updateItem(id, internalId, name, sizeId, artId, kept)) {
            log.info(
                    "Updated item {} (name='{}', internalId='{}', sizeId={}, artId={})",
                    id,
                    name,
                    internalId,
                    sizeId,
                    artId);
            return inventoryRepository.findItemById(id);
        }
        log.warn("Update of item {} did not find a row to change", id);
        return Optional.empty();
    }

    /**
     * Carries forward every value the form could not have shown.
     *
     * <p>A screen sends back the fields it displayed. Once a kind is cleared or swapped, the values
     * that kind's fields described are no longer displayed anywhere, so a plain overwrite would
     * quietly delete them. Anything the piece still holds under a key that nothing describes after
     * the change is therefore kept exactly as it was, and what arrives from the form wins for every
     * key that is described.
     */
    private InventoryItemMetadata keepUndescribedValues(
            InventoryItem before, Integer artId, InventoryItemMetadata incoming) {
        InventoryItemMetadata arriving = incoming != null ? incoming : InventoryItemMetadata.empty();
        Map<String, ItemFieldValues.FieldValue> existing =
                before.metadata().fields().values();
        if (existing.isEmpty()) return arriving;
        InventoryItem after = new InventoryItem(
                before.id(),
                before.inventoryId(),
                before.internalId(),
                before.name(),
                before.sizeId(),
                artId,
                arriving,
                before.assignedTo(),
                before.lostAt(),
                before.lostNote(),
                before.lostNoteBy(),
                before.ownerKind(),
                before.ownerClusterId(),
                before.ownerStationId(),
                before.loanRequestItemId(),
                before.custody(),
                before.custodyStationId(),
                before.custodyPartnerStationId(),
                before.custodyMovementId(),
                before.containerId());
        Set<String> described = fieldService.resolveForItem(after).stream()
                .map(InventoryFieldDefinition::key)
                .collect(Collectors.toSet());
        var merged = new LinkedHashMap<>(existing);
        merged.keySet().removeIf(described::contains);
        if (merged.isEmpty()) return arriving;
        merged.putAll(arriving.fields().values());
        return new InventoryItemMetadata(new ItemFieldValues(merged));
    }

    /**
     * Moves an item into another inventory of the same station.
     *
     * <p>Splitting one inventory into two used to mean deleting the items and writing them again,
     * which throws away their identifiers, their assignments, their history and their custody chain.
     * The piece is the same piece afterwards; only the drawer it is filed under has changed, so the
     * row moves rather than being replaced.
     *
     * <p>The size cannot come along as it stands, because the size list belongs to the inventory being
     * left. Where the new inventory offers a size of the same name the item keeps that size under the
     * new list, and where it does not the item arrives without one. Nothing is invented and nothing
     * points at a size the item's inventory does not have.
     *
     * @param itemId          the item to move
     * @param inventoryId     the inventory it moves into
     * @param actingClusterId the body the caller answers for, or {@code null} when they act as the station
     * @return the moved item, or empty if it was not found
     * @throws BadRequestResponse when the target inventory belongs to another station
     */
    public Optional<InventoryItem> moveItem(int itemId, int inventoryId, Integer actingClusterId) {
        requireOwned(itemId, "moved", actingClusterId);
        requireNotTheBorrowedShelf(inventoryId);
        InventoryItem item = inventoryRepository.findItemById(itemId).orElseThrow(NotFoundResponse::new);
        Inventory target = inventoryRepository
                .findById(inventoryId)
                .orElseThrow(() -> new BadRequestResponse("That inventory does not exist"));
        Inventory source = inventoryRepository
                .findById(item.inventoryId())
                .orElseThrow(() -> new BadRequestResponse("That inventory does not exist"));
        if (target.stationId() != source.stationId()) {
            throw new BadRequestResponse("A piece can only be moved into an inventory of the same station");
        }
        if (target.id() == source.id()) {
            return Optional.of(item);
        }

        Integer sizeId = remappedSize(item, source, target);
        if (inventoryRepository.moveItemToInventory(itemId, inventoryId, sizeId)) {
            log.info("Moved item {} from inventory {} to {} (sizeId={})", itemId, source.id(), inventoryId, sizeId);
            return inventoryRepository.findItemById(itemId);
        }
        log.warn("Move of item {} did not find a row to change", itemId);
        return Optional.empty();
    }

    /**
     * The size the item carries once it is in the new inventory: the one of the same name there, or
     * none at all.
     */
    private Integer remappedSize(InventoryItem item, Inventory source, Inventory target) {
        if (item.sizeId() == null || !target.hasSizes()) return null;
        String label = inventoryRepository.findSizes(source.id()).stream()
                .filter(size -> size.id() == item.sizeId())
                .map(InventorySize::label)
                .findFirst()
                .orElse(null);
        if (label == null) return null;
        return inventoryRepository.findSizes(target.id()).stream()
                .filter(size -> size.label().equals(label))
                .map(InventorySize::id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Assigns an item to a member (or unassigns it) with full history tracking.
     * If the item is currently assigned, the existing history entry is closed. If a new member is specified,
     * a new history entry is created.
     *
     * @param itemId     the item ID
     * @param memberId   the member to assign to, or {@code null} to unassign
     * @param memberName the member's display name for history
     * @return the updated item, or empty if the item was not found
     */
    public Optional<InventoryItem> assignItem(int itemId, Integer memberId, String memberName) {
        return memberId != null
                ? custodyService.assignToMember(itemId, memberId, memberName)
                : custodyService.takeBack(itemId);
    }

    /**
     * Every piece of one inventory that is in nobody's hands, so a caller can offer them.
     *
     * @param inventoryId the inventory to look in
     * @return the pieces currently assigned to nobody
     */
    public List<InventoryItem> unassignedItems(int inventoryId) {
        return inventoryRepository.findUnassignedItems(inventoryId);
    }

    /**
     * Takes a fresh piece into an inventory and hands it straight to a member.
     *
     * <p>The two steps belong together: a piece created for somebody and then left lying because the
     * assignment failed is worse than no piece at all. Ownership follows the inventory, an external
     * one belonging to the body above the station rather than to the station itself.
     *
     * @param inventoryId the inventory the piece goes into
     * @param sizeId      the size, or {@code null} where the inventory keeps none
     * @param memberId    who receives it
     * @param actorName   who handed it over, for the history
     * @return the created piece
     */
    public InventoryItem createAndHandOut(int inventoryId, Integer sizeId, int memberId, String actorName) {
        Inventory inventory = findById(inventoryId).orElseThrow(() -> new NotFoundResponse("Inventory not found"));
        ItemOwner owner = inventory.inventoryType() == InventoryType.EXTERNAL ? ItemOwner.CLUSTER : ItemOwner.STATION;
        InventoryItem item = createItem(inventoryId, null, inventory.name(), sizeId, null, owner, null);
        return assignItem(item.id(), memberId, actorName).orElse(item);
    }

    /**
     * Marks an item as lost.
     *
     * @param id     the item ID
     * @param note   what whoever reported it wrote, or {@code null}
     * @param noteBy who wrote that note, or {@code null}
     * @return the updated item, or empty if not found
     */
    public Optional<InventoryItem> markLost(int id, String note, Integer noteBy) {
        return custodyService.markLost(id, note, noteBy);
    }

    /**
     * Marks a previously lost item as found.
     *
     * @param id the item ID
     * @return the updated item, or empty if not found
     */
    public Optional<InventoryItem> markFound(int id) {
        return custodyService.markFound(id);
    }

    /**
     * Deletes an inventory item.
     *
     * @param id              the item ID
     * @param actingClusterId the body the caller answers for, or {@code null} when they act as the station
     * @return {@code true} if deleted
     */
    public boolean deleteItem(int id, Integer actingClusterId) {
        requireOwned(id, "deleted", actingClusterId);
        boolean deleted = inventoryRepository.deleteItem(id);
        if (deleted) log.info("Deleted item {}", id);
        else log.warn("Delete skipped: item {} not found", id);
        return deleted;
    }

    /**
     * Refuses to let a station change gear it does not own.
     *
     * <p>Holding something is not owning it. A station may hand a cluster's jacket to a member, put it on a
     * shelf, check it and report it missing, because all of those are facts about where it is. What it may
     * not do is rename it, resize it or delete it, because those are the owner's account of what the thing
     * is, and the same row is what the owner reads.
     *
     * <p>The owner itself arrives here as a station request, because an association's gear sits on the station
     * it owns and its screens act there. So the question is not "is this a station" but "is this the body the
     * gear belongs to", which is what {@code actingClusterId} answers.
     *
     * @param itemId          the item somebody wants to change
     * @param verb            what they wanted to do, for the message
     * @param actingClusterId the body the caller answers for, or {@code null} when they act as the station
     * @throws ForbiddenResponse when the item belongs to a cluster that runs on this instance and is not this one
     */
    private void requireOwned(int itemId, String verb, Integer actingClusterId) {
        inventoryRepository.findItemById(itemId).ifPresent(item -> {
            // A borrowed piece has an owner who is right there and reading the same thing from the other
            // end. What it is stays theirs; where it is stays the borrower's, and none of those verbs is
            // about where it is.
            if (item.borrowed()) {
                throw new ForbiddenResponse(
                        "This gear belongs to a partner station and can only be %s by them".formatted(verb));
            }
            // Only where the owner is actually here to do it themselves. Gear kept for a body that does not
            // use Ember belongs to nobody who could ever correct a name, so refusing the station would leave
            // the record wrong for good with no way to put it right.
            if (item.ownerKind() != ItemOwner.CLUSTER || item.ownerClusterId() == null) return;
            if (item.ownerClusterId().equals(actingClusterId)) return;
            throw new ForbiddenResponse(
                    "This gear belongs to the body above the station and can only be %s by them".formatted(verb));
        });
    }

    // -- History --

    /**
     * Finds the assignment history for an item.
     *
     * @param itemId the item ID
     * @return list of history entries
     */
    public List<InventoryItemHistory> findHistory(int itemId) {
        return inventoryRepository.findHistory(itemId);
    }

    // -- Requirements --

    /**
     * Finds all inventory requirements for a station.
     *
     * @param stationId the station ID
     * @return list of requirements
     */
    public List<InventoryRequirement> findAllRequirementsByStation(int stationId) {
        return inventoryRepository.findAllRequirementsByStation(stationId);
    }

    /**
     * What a station has to show for its people: its own requirements and the cluster's, each saying which
     * it is and what it asks for.
     *
     * @param stationId the station reading them
     * @return its own and the cluster's, ordered by position
     */
    public List<InventoryRepository.VisibleRequirement> findRequirementsVisibleAt(int stationId) {
        return inventoryRepository.findRequirementsVisibleAt(stationId);
    }

    /**
     * The body above this station that keeps its gear in Ember, when there is one.
     *
     * <p>Two screens ask it: a requirement the station did not write is badged with the name, and the
     * button asking that body for a piece appears only where there is somebody to ask.
     *
     * @param stationId the station asking
     * @return the association above it, if it keeps its gear here
     */
    public Optional<String> ownerAbove(int stationId) {
        return clusterRepository
                .findByStation(stationId)
                .filter(Cluster::usesInventory)
                .map(Cluster::name);
    }

    /**
     * Creates a new inventory requirement.
     *
     * @param inventoryId the inventory ID
     * @param userType    the user type name, or {@code null} if not user-type-based
     * @param groupId        the group ID (0 if not group-based)
     * @param stationGroupId the group of stations it counts at, or null for every station reading it
     * @param quantity       the required quantity
     * @return the created requirement
     */
    public InventoryRequirement createRequirement(
            int inventoryId, StationUserType userType, int groupId, Integer stationGroupId, int quantity) {
        requireHomogeneous(inventoryId, "a requirement");
        requireGroupOfTheOwningCluster(inventoryId, stationGroupId);
        InventoryRequirement requirement =
                inventoryRepository.createRequirement(inventoryId, userType, groupId, stationGroupId, quantity);
        log.info(
                "Created requirement {} (userType={}, groupId={}, stationGroupId={}, quantity={}) for inventory {}",
                requirement.id(),
                userType,
                groupId,
                stationGroupId,
                quantity,
                inventoryId);
        return requirement;
    }

    /**
     * Updates the quantity of an inventory requirement.
     *
     * @param id       the requirement ID
     * @param quantity the new quantity
     * @return {@code true} if updated
     */
    public boolean updateRequirement(int id, int quantity) {
        boolean updated = inventoryRepository.updateRequirement(id, quantity);
        if (updated) log.info("Updated requirement {} (quantity={})", id, quantity);
        else log.warn("Update of requirement {} did not change any row", id);
        return updated;
    }

    /**
     * Updates the display position of an inventory requirement.
     *
     * @param id       the requirement ID
     * @param position the new position
     * @return {@code true} if updated
     */
    public boolean updateRequirementPosition(int id, int position) {
        boolean updated = inventoryRepository.updateRequirementPosition(id, position);
        if (updated) log.info("Updated requirement {} position to {}", id, position);
        else log.warn("Position update of requirement {} did not change any row", id);
        return updated;
    }

    /**
     * Deletes an inventory requirement.
     *
     * @param id the requirement ID
     * @return {@code true} if deleted
     */
    public boolean deleteRequirement(int id) {
        boolean deleted = inventoryRepository.deleteRequirement(id);
        if (deleted) log.info("Deleted requirement {}", id);
        else log.warn("Delete of requirement {} did not change any row", id);
        return deleted;
    }
}
