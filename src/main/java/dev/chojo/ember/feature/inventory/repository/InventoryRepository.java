/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemHistory;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventorySummary;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MemberInventoryEntry;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for managing inventories, their items, sizes, history entries, and requirements.
 */
@Singleton
public class InventoryRepository {
    private static final String INVENTORY_COLUMNS = "id, station_id, name, inventory_type, has_sizes";
    private static final String INVENTORY_SIZE_COLUMNS = "id, inventory_id, label, position, note";
    private static final String INVENTORY_ITEM_COLUMNS =
            "id, inventory_id, internal_id, name, size_id, metadata, assigned_to, lost_at, owner_kind, owner_cluster_id, custody, custody_station_id, custody_movement_id, container_id";
    private static final String INVENTORY_ITEM_HISTORY_COLUMNS =
            "id, item_id, member_id, member_name, given_out, returned";
    private static final String INVENTORY_REQUIREMENT_COLUMNS =
            "id, inventory_id, user_type, group_id, quantity, position";

    /**
     * Finds an inventory by its ID.
     *
     * @param id the inventory ID
     * @return the inventory, or empty if not found
     */
    public Optional<Inventory> findById(int id) {
        return SqlSupport.findById("inventory", INVENTORY_COLUMNS, id, Inventory.map());
    }

    /**
     * Finds all inventories belonging to a station.
     *
     * @param stationId the station ID
     * @return list of inventories for the station
     */
    public List<Inventory> findByStation(int stationId) {
        return query("""
                SELECT %s FROM inventory WHERE station_id = :station_id;""", INVENTORY_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(Inventory.map())
                .all();
    }

    public List<InventorySummary> findSummariesByStation(int stationId) {
        return query("""
                SELECT %s,
                       coalesce(counts.item_count, 0) AS item_count,
                       coalesce(counts.lost_count, 0) AS lost_count,
                       coalesce(proc.procurement_count, 0) AS procurement_count,
                       coalesce(lent.lent_out_count, 0) AS lent_out_count
                FROM inventory i
                LEFT JOIN (
                    SELECT inventory_id,
                           count(*) AS item_count,
                           count(*) FILTER (WHERE lost_at IS NOT NULL) AS lost_count
                    FROM inventory_item
                    GROUP BY inventory_id
                ) counts ON counts.inventory_id = i.id
                LEFT JOIN (
                    SELECT inventory_id,
                           count(*) AS procurement_count
                    FROM equipment_procurement
                    WHERE fulfilled_at IS NULL
                    GROUP BY inventory_id
                ) proc ON proc.inventory_id = i.id
                LEFT JOIN (
                    SELECT li.inventory_id,
                           count(*) FILTER (WHERE li.assigned_item_id IS NOT NULL) AS lent_out_count
                    FROM federation_lending_request_item li
                    JOIN federation_lending_request lr ON lr.id = li.request_id
                    WHERE lr.status IN ('APPROVED', 'LENT')
                    GROUP BY li.inventory_id
                ) lent ON lent.inventory_id = i.id
                WHERE i.station_id = :station_id
                ORDER BY i.name;""", SqlSupport.alias("i", INVENTORY_COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(InventorySummary.map())
                .all();
    }

    /**
     * Creates a new inventory for a station.
     *
     * @param stationId     the station ID
     * @param name          the inventory name
     * @param inventoryType the inventory type
     * @param hasSizes      whether the inventory supports sizes
     * @return the created inventory
     */
    public Inventory create(int stationId, String name, InventoryType inventoryType, boolean hasSizes) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory(station_id, name, inventory_type, has_sizes)
                VALUES(:station_id, :name, :inventory_type, :has_sizes)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("inventory_type", inventoryType)
                        .bind("has_sizes", hasSizes),
                Inventory.map(),
                INVENTORY_COLUMNS);
    }

    /**
     * Updates an existing inventory.
     *
     * @param id            the inventory ID
     * @param name          the new name
     * @param inventoryType the new inventory type
     * @param hasSizes      whether the inventory supports sizes
     * @return {@code true} if the inventory was updated
     */
    public boolean update(int id, String name, InventoryType inventoryType, boolean hasSizes) {
        return query(
                        "UPDATE inventory SET name = :name, inventory_type = :inventory_type, has_sizes = :has_sizes WHERE id = :id;")
                .single(call().bind("name", name)
                        .bind("inventory_type", inventoryType)
                        .bind("has_sizes", hasSizes)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an inventory by its ID.
     *
     * @param id the inventory ID
     * @return {@code true} if the inventory was deleted
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("inventory", id);
    }

    /**
     * Finds all sizes for an inventory, ordered by position.
     *
     * @param inventoryId the inventory ID
     * @return list of sizes
     */
    public List<InventorySize> findSizes(int inventoryId) {
        return query("""
                SELECT %s FROM inventory_size WHERE inventory_id = :inventory_id ORDER BY position;""", INVENTORY_SIZE_COLUMNS)
                .single(call().bind("inventory_id", inventoryId))
                .map(InventorySize.map())
                .all();
    }

    /**
     * Creates a new size for an inventory.
     *
     * @param inventoryId the inventory ID
     * @param label       the size label
     * @param position    the sort position
     * @param note        an optional note
     */
    public void createSize(int inventoryId, String label, int position, String note) {
        query(
                        "INSERT INTO inventory_size(inventory_id, label, position, note) VALUES(:inventory_id, :label, :position, :note);")
                .single(call().bind("inventory_id", inventoryId)
                        .bind("label", label)
                        .bind("position", position)
                        .bind("note", note != null ? note : ""))
                .insert();
    }

    /**
     * Updates an existing inventory size.
     *
     * @param id       the size ID
     * @param label    the new label
     * @param position the new position
     * @param note     the new note
     * @return {@code true} if the size was updated
     */
    public boolean updateSize(int id, String label, int position, String note) {
        return query("UPDATE inventory_size SET label = :label, position = :position, note = :note WHERE id = :id;")
                .single(call().bind("label", label)
                        .bind("position", position)
                        .bind("note", note != null ? note : "")
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an inventory size by its ID.
     *
     * @param id the size ID
     * @return {@code true} if the size was deleted
     */
    public boolean deleteSize(int id) {
        return SqlSupport.deleteById("inventory_size", id);
    }

    /**
     * Finds an inventory item by its ID.
     *
     * @param id the item ID
     * @return the item, or empty if not found
     */
    public Optional<InventoryItem> findItemById(int id) {
        return SqlSupport.findById("inventory_item", INVENTORY_ITEM_COLUMNS, id, InventoryItem.map());
    }

    /**
     * The station running the inventory an item is defined in. That is the station the item's
     * custody runs through whenever anybody but its owner has it.
     *
     * @param itemId the item ID
     * @return the station ID, or empty if the item is gone
     */
    public Optional<Integer> findStationIdByItem(int itemId) {
        return query("""
                SELECT i.station_id FROM inventory_item ii
                JOIN inventory i ON i.id = ii.inventory_id
                WHERE ii.id = :id;""")
                .single(call().bind("id", itemId))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    /**
     * The scanner lookup: the item a station holds under a scanned code. It reads custody rather
     * than the inventory, so a code on gear the station has already posted back finds nothing here.
     */
    public Optional<InventoryItem> findByInternalId(int stationId, String internalId) {
        return query(
                        """
                SELECT %s FROM inventory_item ii
                %s
                WHERE %s AND ii.internal_id = :internal_id
                LIMIT 1;""",
                        SqlSupport.alias("ii", INVENTORY_ITEM_COLUMNS),
                        ItemCustodySql.joinInventory("ii", "i"),
                        ItemCustodySql.heldBy("ii", "i"))
                .single(call().bind(ItemCustodySql.STATION_BIND, stationId).bind("internal_id", internalId))
                .map(InventoryItem.map())
                .first();
    }

    public List<InventoryItem> findFreeItems(int inventoryId, LocalDate dateFrom, LocalDate dateTo) {
        return query("""
                SELECT %s FROM inventory_item
                WHERE inventory_id = :inventory_id
                  AND %s
                  AND id NOT IN (
                      SELECT li.assigned_item_id FROM federation_lending_request_item li
                      JOIN federation_lending_request lr ON lr.id = li.request_id
                      WHERE li.assigned_item_id IS NOT NULL
                        AND lr.status IN ('APPROVED', 'LENT')
                        AND lr.requested_date_from <= :date_to
                        AND (lr.requested_date_to IS NULL OR lr.requested_date_to >= :date_from)
                  )
                ORDER BY id;""", INVENTORY_ITEM_COLUMNS, ItemCustodySql.freeStock("inventory_item"))
                .single(call().bind("inventory_id", inventoryId)
                        .bind("date_from", dateFrom)
                        .bind("date_to", dateTo))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * Finds all items in an inventory.
     *
     * @param inventoryId the inventory ID
     * @return list of items
     */
    public List<InventoryItem> findItems(int inventoryId) {
        return query("""
                SELECT %s FROM inventory_item WHERE inventory_id = :inventory_id;""", INVENTORY_ITEM_COLUMNS)
                .single(call().bind("inventory_id", inventoryId))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * Every item a station holds, which is not the same as every item it owns.
     */
    public List<InventoryItem> findItemsByStation(int stationId) {
        return query(
                        """
                SELECT %s FROM inventory_item ii
                %s
                WHERE %s;""",
                        SqlSupport.alias("ii", INVENTORY_ITEM_COLUMNS),
                        ItemCustodySql.joinInventory("ii", "i"),
                        ItemCustodySql.heldBy("ii", "i"))
                .single(call().bind(ItemCustodySql.STATION_BIND, stationId))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * The gear a cluster owns that a given station currently holds.
     *
     * <p>The question a release asks. Ownership and custody are separate, so this is neither "everything in
     * that station's inventories" nor "everything the cluster owns": it is the overlap, which is exactly what
     * has to come home when the station stops answering to the cluster.
     *
     * @param clusterId the owning cluster
     * @param stationId the station holding it
     * @return the items to recall
     */
    public List<InventoryItem> findClusterItemsHeldBy(int clusterId, int stationId) {
        return query(
                        """
                SELECT %s FROM inventory_item ii
                %s
                WHERE ii.owner_kind = 'CLUSTER' AND ii.owner_cluster_id = :cluster_id AND %s;""",
                        SqlSupport.alias("ii", INVENTORY_ITEM_COLUMNS),
                        ItemCustodySql.joinInventory("ii", "i"),
                        ItemCustodySql.heldBy("ii", "i"))
                .single(call().bind("cluster_id", clusterId).bind(ItemCustodySql.STATION_BIND, stationId))
                .map(InventoryItem.map())
                .all();
    }

    public List<InventorySize> findSizesByStation(int stationId) {
        return query("""
                SELECT %s FROM inventory_size s
                JOIN inventory i ON i.id = s.inventory_id
                WHERE i.station_id = :station_id ORDER BY s.position;""", SqlSupport.alias("s", INVENTORY_SIZE_COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(InventorySize.map())
                .all();
    }

    public List<InventoryItem> findItemsByMember(int memberId) {
        return query("""
                SELECT %s FROM inventory_item WHERE assigned_to = :member_id;""", INVENTORY_ITEM_COLUMNS)
                .single(call().bind("member_id", memberId))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * A member's own inventory: what they hold, plus whatever is on its way to or from them.
     *
     * <p>An item taken back for an exchange is nobody's until the replacement is handed over, so
     * reading the assignment alone makes a member's jacket disappear for exactly the stretch they
     * most want to watch. The open movement puts it back on the list with the step it is standing
     * on, and the distinct keeps an item that somehow reached two movements to one line.
     *
     * @param memberId the member
     * @return their items, each with the movement it is on when there is one
     */
    public List<MemberInventoryEntry> findMemberEntries(int memberId) {
        return query("""
                SELECT DISTINCT ON (ii.id)
                    %s,
                    m.id AS movement_id,
                    s.label AS movement_step,
                    coalesce(m.incoming_item_id = ii.id, FALSE) AS movement_incoming
                FROM inventory_item ii
                LEFT JOIN item_movement m
                       ON m.state = 'OPEN'
                      AND m.member_id = :member_id
                      AND (m.outgoing_item_id = ii.id OR m.incoming_item_id = ii.id)
                LEFT JOIN movement_flow_step s ON s.id = m.current_step_id
                WHERE ii.assigned_to = :member_id OR m.id IS NOT NULL
                ORDER BY ii.id, m.id;""", SqlSupport.alias("ii", INVENTORY_ITEM_COLUMNS))
                .single(call().bind("member_id", memberId))
                .map(MemberInventoryEntry.map())
                .all();
    }

    public int countItemsByMember(int memberId) {
        return SqlSupport.count(
                "SELECT count(*) FROM inventory_item WHERE assigned_to = :member_id;",
                call().bind("member_id", memberId));
    }

    /**
     * Finds all unassigned and non-lost items in an inventory, ordered by name.
     *
     * @param inventoryId the inventory ID
     * @return list of available items
     */
    public List<InventoryItem> findUnassignedItems(int inventoryId) {
        return query("""
                SELECT %s FROM inventory_item
                WHERE inventory_id = :inventory_id
                  AND %s
                ORDER BY name;""", INVENTORY_ITEM_COLUMNS, ItemCustodySql.freeStock("inventory_item"))
                .single(call().bind("inventory_id", inventoryId))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * Creates a new inventory item owned by the station running the inventory.
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
        return createItem(inventoryId, internalId, name, sizeId, metadata, ItemOwner.STATION, null);
    }

    /**
     * Creates a new inventory item with a named owner.
     *
     * <p>A new item starts in the store of whoever will hold it, which is not always the owner: gear
     * the station records but does not own is gear the station has, so it starts at the station.
     *
     * @param inventoryId    the inventory ID
     * @param internalId     the internal identifier
     * @param name           the item name
     * @param sizeId         the size ID, or {@code null}
     * @param metadata       JSON metadata
     * @param ownerKind      who owns the item, or {@code null} for the station
     * @param ownerClusterId the owning body when it runs on this instance, only ever set for
     *                       {@link ItemOwner#CLUSTER}
     * @return the created item
     */
    public InventoryItem createItem(
            int inventoryId,
            String internalId,
            String name,
            Integer sizeId,
            InventoryItemMetadata metadata,
            ItemOwner ownerKind,
            Integer ownerClusterId) {
        ItemOwner owner = ownerKind != null ? ownerKind : ItemOwner.STATION;
        boolean heldByStation = owner == ItemOwner.CLUSTER;
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_item(inventory_id, internal_id, name, size_id, metadata, owner_kind,
                                           owner_cluster_id, custody, custody_station_id)
                SELECT :inventory_id, :internal_id, :name, :size_id, :metadata::JSONB, :owner_kind,
                       :owner_cluster_id, :custody,
                       CASE WHEN :held_by_station THEN i.station_id ELSE NULL END
                FROM inventory i
                WHERE i.id = :inventory_id
                RETURNING %s;""",
                call().bind("inventory_id", inventoryId)
                        .bind("internal_id", internalId)
                        .bind("name", name)
                        .bind("size_id", sizeId)
                        .bind("metadata", (metadata != null ? metadata : InventoryItemMetadata.empty()).toJson())
                        .bind("owner_kind", owner)
                        .bind("owner_cluster_id", owner == ItemOwner.CLUSTER ? ownerClusterId : null)
                        .bind("custody", heldByStation ? ItemCustody.AT_STATION : ItemCustody.WITH_OWNER)
                        .bind("held_by_station", heldByStation),
                InventoryItem.map(),
                INVENTORY_ITEM_COLUMNS);
    }

    /**
     * Updates an existing inventory item.
     *
     * @param id         the item ID
     * @param internalId the new internal identifier
     * @param name       the new name
     * @param sizeId     the new size ID, or {@code null}
     * @param metadata   the new JSON metadata
     * @return {@code true} if the item was updated
     */
    public boolean updateItem(int id, String internalId, String name, Integer sizeId, InventoryItemMetadata metadata) {
        return query("""
                UPDATE inventory_item
                SET
                    internal_id = :internal_id,
                    name        = :name,
                    size_id     = :size_id,
                    metadata    = :metadata::JSONB
                WHERE id = :id;""")
                .single(call().bind("internal_id", internalId)
                        .bind("name", name)
                        .bind("size_id", sizeId)
                        .bind("metadata", (metadata != null ? metadata : InventoryItemMetadata.empty()).toJson())
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Writes an item's whole custody at once: who has it, the station the custody runs through, the
     * member holding it and the movement carrying it. The four travel together because the database
     * only accepts them as a consistent set, and the lost timestamp follows from the custody rather
     * than being passed separately.
     *
     * <p>This is the only statement in the codebase that writes any of those columns. Everything
     * that moves an item goes through {@code ItemCustodyService}, which is what keeps the four
     * overlapping signals of the old model from growing back.
     *
     * @param itemId            the item ID
     * @param custody           who has the item now
     * @param custodyStationId  the station the custody runs through, or {@code null}
     * @param assignedTo        the member holding it, or {@code null}
     * @param custodyMovementId the movement carrying it, or {@code null}
     * @return {@code true} if the item row was updated
     */
    public boolean updateCustody(
            int itemId, ItemCustody custody, Integer custodyStationId, Integer assignedTo, Integer custodyMovementId) {
        return query("""
                UPDATE inventory_item
                SET custody             = :custody,
                    custody_station_id  = :custody_station_id,
                    custody_movement_id = :custody_movement_id,
                    assigned_to         = :assigned_to,
                    lost_at             = CASE WHEN :mark_lost THEN coalesce(lost_at, now()) ELSE NULL END,
                    container_id        = CASE WHEN :assigned_to::int IS NOT NULL THEN NULL ELSE container_id END
                WHERE id = :id;""")
                .single(call().bind("custody", custody)
                        .bind("custody_station_id", custodyStationId)
                        .bind("custody_movement_id", custodyMovementId)
                        .bind("assigned_to", assignedTo)
                        .bind("mark_lost", custody == ItemCustody.LOST)
                        .bind("id", itemId))
                .update()
                .changed();
    }

    /**
     * Sets or clears the container an item is physically placed in. A container says where in the
     * store an item is, not who has it, so this leaves the custody alone. An item a member holds
     * has to be taken back first, which the custody service does before it calls this.
     *
     * @param itemId      the item ID
     * @param containerId the container ID, or {@code null} to clear the location
     * @return {@code true} if the item row was updated
     */
    public boolean setItemContainer(int itemId, Integer containerId) {
        return query("""
                UPDATE inventory_item
                SET container_id = :container_id
                WHERE id = :id;""")
                .single(call().bind("container_id", containerId).bind("id", itemId))
                .update()
                .changed();
    }

    /**
     * Returns whether the given station has any item whose internal id matches.
     */
    public boolean itemInternalIdExists(int stationId, String internalId, Integer excludeItemId) {
        return SqlSupport.exists(
                """
                SELECT 1 FROM inventory_item ii
                JOIN inventory i ON i.id = ii.inventory_id
                WHERE i.station_id = :station_id
                  AND ii.internal_id = :internal_id
                  AND (:exclude_id::int IS NULL OR ii.id <> :exclude_id)
                LIMIT 1;""",
                call().bind("station_id", stationId)
                        .bind("internal_id", internalId)
                        .bind("exclude_id", excludeItemId));
    }

    /**
     * Deletes an inventory item by its ID.
     *
     * @param id the item ID
     * @return {@code true} if the item was deleted
     */
    public boolean deleteItem(int id) {
        return SqlSupport.deleteById("inventory_item", id);
    }

    /**
     * Finds the assignment history for an item, ordered by most recent first.
     *
     * @param itemId the item ID
     * @return list of history entries
     */
    public List<InventoryItemHistory> findHistory(int itemId) {
        return query("""
                SELECT %s FROM inventory_item_history WHERE item_id = :itemId ORDER BY given_out DESC;""", INVENTORY_ITEM_HISTORY_COLUMNS)
                .single(call().bind("itemId", itemId))
                .map(InventoryItemHistory.map())
                .all();
    }

    /**
     * Creates a new history entry for an item assignment with the current timestamp.
     *
     * @param itemId     the item ID
     * @param memberId   the member the item is assigned to
     * @param memberName the member's name at the time of assignment
     * @return the created history entry
     */
    public InventoryItemHistory createHistory(int itemId, int memberId, String memberName) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_item_history(item_id, member_id, member_name)
                VALUES(:itemId, :memberId, :memberName)
                RETURNING %s;""",
                call().bind("itemId", itemId).bind("memberId", memberId).bind("memberName", memberName),
                InventoryItemHistory.map(),
                INVENTORY_ITEM_HISTORY_COLUMNS);
    }

    /**
     * Creates a history entry with explicit given-out and returned timestamps, used for data import.
     *
     * @param itemId     the item ID
     * @param memberId   the member ID
     * @param memberName the member's name
     * @param givenOut   when the item was given out
     * @param returned   when the item was returned
     */
    public void createHistoryWithDates(
            int itemId, int memberId, String memberName, Instant givenOut, Instant returned) {
        query("""
                INSERT INTO inventory_item_history(item_id, member_id, member_name, given_out, returned)
                VALUES(:itemId, :memberId, :memberName, :givenOut, :returned);""")
                .single(call().bind("itemId", itemId)
                        .bind("memberId", memberId)
                        .bind("memberName", memberName)
                        .bind("givenOut", givenOut, INSTANT_TIMESTAMP)
                        .bind("returned", returned, INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * Marks the open history entry for an item and member as returned by setting the returned timestamp.
     *
     * @param itemId   the item ID
     * @param memberId the member ID
     * @return {@code true} if a history entry was updated
     */
    public boolean returnHistory(int itemId, int memberId) {
        return query(
                        "UPDATE inventory_item_history SET returned = now() WHERE item_id = :itemId AND member_id = :memberId AND returned IS NULL;")
                .single(call().bind("itemId", itemId).bind("memberId", memberId))
                .update()
                .changed();
    }

    /**
     * Finds all inventory requirements for a station, ordered by position.
     *
     * @param stationId the station ID
     * @return list of requirements
     */
    public List<InventoryRequirement> findAllRequirementsByStation(int stationId) {
        return query("""
                SELECT %s FROM inventory_requirement r JOIN inventory i ON r.inventory_id = i.id WHERE i.station_id = :stationId ORDER BY r.position, r.id;""", SqlSupport.alias("r", INVENTORY_REQUIREMENT_COLUMNS))
                .single(call().bind("stationId", stationId))
                .map(InventoryRequirement.map())
                .all();
    }

    /**
     * Creates a new inventory requirement for a role or group.
     *
     * @param inventoryId the inventory ID
     * @param userType    the user type name, or {@code null} for no user type restriction
     * @param groupId     the group ID (0 means no group restriction)
     * @param quantity    the required quantity
     * @return the created requirement
     */
    public InventoryRequirement createRequirement(
            int inventoryId, StationUserType userType, int groupId, int quantity) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_requirement(inventory_id, user_type, group_id, quantity)
                VALUES(:inventoryId, :userType, :groupId, :quantity)
                RETURNING %s;""",
                call().bind("inventoryId", inventoryId)
                        .bind("userType", userType)
                        .bind("groupId", groupId == 0 ? null : groupId)
                        .bind("quantity", quantity),
                InventoryRequirement.map(),
                INVENTORY_REQUIREMENT_COLUMNS);
    }

    /**
     * Updates the quantity of an inventory requirement.
     *
     * @param id       the requirement ID
     * @param quantity the new quantity
     * @return {@code true} if the requirement was updated
     */
    public boolean updateRequirement(int id, int quantity) {
        return query("UPDATE inventory_requirement SET quantity = :quantity WHERE id = :id;")
                .single(call().bind("quantity", quantity).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Updates the display position of an inventory requirement.
     *
     * @param id       the requirement ID
     * @param position the new position
     * @return {@code true} if the requirement was updated
     */
    public boolean updateRequirementPosition(int id, int position) {
        return query("UPDATE inventory_requirement SET position = :position WHERE id = :id;")
                .single(call().bind("position", position).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an inventory requirement by its ID.
     *
     * @param id the requirement ID
     * @return {@code true} if the requirement was deleted
     */
    public boolean deleteRequirement(int id) {
        return SqlSupport.deleteById("inventory_requirement", id);
    }
}
