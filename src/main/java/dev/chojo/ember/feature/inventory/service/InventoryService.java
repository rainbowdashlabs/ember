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
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemHistory;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventorySummary;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MemberInventoryEntry;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing inventories, their items, sizes, history, and requirements.
 * Provides business logic on top of the repository, including item assignment with history tracking.
 */
@Singleton
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final InventoryRepository inventoryRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterStationGroupRepository stationGroupRepository;
    private final ItemCustodyService custodyService;

    @Inject
    public InventoryService(
            InventoryRepository inventoryRepository,
            ItemCustodyService custodyService,
            ClusterRepository clusterRepository,
            ClusterStationGroupRepository stationGroupRepository) {
        this.inventoryRepository = inventoryRepository;
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
     * @return the created inventory
     */
    public Inventory create(int stationId, String name, InventoryType inventoryType, boolean hasSizes) {
        Inventory inventory = inventoryRepository.create(stationId, name, inventoryType, hasSizes);
        log.info(
                "Created inventory {} (name='{}', type={}, hasSizes={}) in station {}",
                inventory.id(),
                name,
                inventoryType,
                hasSizes,
                stationId);
        return inventory;
    }

    /**
     * Updates an existing inventory.
     *
     * @param id            the inventory ID
     * @param name          the new name
     * @param inventoryType the new type
     * @param hasSizes      whether sizes are supported
     * @return the updated inventory, or empty if not found
     */
    public Optional<Inventory> update(int id, String name, InventoryType inventoryType, boolean hasSizes) {
        if (inventoryRepository.update(id, name, inventoryType, hasSizes)) {
            log.info("Updated inventory {} (name='{}', type={}, hasSizes={})", id, name, inventoryType, hasSizes);
            return inventoryRepository.findById(id);
        }
        log.warn("Update of inventory {} did not change any row", id);
        return Optional.empty();
    }

    /**
     * Deletes an inventory.
     *
     * @param id the inventory ID
     * @return {@code true} if deleted
     */
    public boolean delete(int id) {
        boolean deleted = inventoryRepository.delete(id);
        if (deleted) log.info("Deleted inventory {}", id);
        else log.warn("Delete of inventory {} did not change any row", id);
        return deleted;
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
        requireOwnerFits(inventoryId, ownerKind);
        requireOwningCluster(inventoryId, ownerClusterId);
        InventoryItem item = inventoryRepository.createItem(
                inventoryId, internalId, name, sizeId, metadata, ownerKind, ownerClusterId);
        log.info(
                "Created item {} (name='{}', internalId='{}', sizeId={}, owner={}, ownerClusterId={}) in inventory {}",
                item.id(),
                name,
                internalId,
                sizeId,
                ownerKind,
                ownerClusterId,
                inventoryId);
        return item;
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
     * Updates an inventory item.
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
        requireOwned(id, "described", actingClusterId);
        if (inventoryRepository.updateItem(id, internalId, name, sizeId, metadata)) {
            log.info("Updated item {} (name='{}', internalId='{}', sizeId={})", id, name, internalId, sizeId);
            return inventoryRepository.findItemById(id);
        }
        log.warn("Update of item {} did not find a row to change", id);
        return Optional.empty();
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
