/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
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
import dev.chojo.ember.feature.inventory.entity.SwitchBlocker;
import dev.chojo.ember.feature.inventory.entity.SwitchBlockerKind;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
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
    private static final String INVENTORY_COLUMNS = "id, station_id, name, inventory_type, has_sizes, homogeneous";
    private static final String INVENTORY_SIZE_COLUMNS = "id, inventory_id, label, position, note";
    private static final String INVENTORY_ITEM_COLUMNS =
            "id, inventory_id, internal_id, name, size_id, art_id, metadata, assigned_to, lost_at, lost_note, lost_note_by, owner_kind, owner_cluster_id, custody, custody_station_id, custody_movement_id, container_id";
    private static final String INVENTORY_ITEM_HISTORY_COLUMNS =
            "id, item_id, member_id, member_name, given_out, returned, corrected";
    private static final String INVENTORY_REQUIREMENT_COLUMNS =
            "id, inventory_id, user_type, group_id, station_group_id, quantity, position";

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
                    WHERE custody <> 'IN_TRANSIT'
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
     * Creates a new inventory holding one thing in many copies.
     *
     * <p>That is the permissive kind, the one every inventory that existed before the distinction was
     * drawn became, so it is what a caller with no opinion gets.
     *
     * @param stationId     the station ID
     * @param name          the inventory name
     * @param inventoryType the inventory type
     * @param hasSizes      whether the inventory supports sizes
     * @return the created inventory
     */
    public Inventory create(int stationId, String name, InventoryType inventoryType, boolean hasSizes) {
        return create(stationId, name, inventoryType, hasSizes, true);
    }

    /**
     * Creates a new inventory for a station.
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
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory(station_id, name, inventory_type, has_sizes, homogeneous)
                VALUES(:station_id, :name, :inventory_type, :has_sizes, :homogeneous)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("inventory_type", inventoryType)
                        .bind("has_sizes", hasSizes)
                        .bind("homogeneous", homogeneous),
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
     * @param homogeneous   whether it holds one thing in many copies rather than a drawer of different things
     * @return {@code true} if the inventory was updated
     */
    public boolean update(int id, String name, InventoryType inventoryType, boolean hasSizes, boolean homogeneous) {
        return query("""
                UPDATE inventory
                SET name           = :name,
                    inventory_type = :inventory_type,
                    has_sizes      = :has_sizes,
                    homogeneous    = :homogeneous
                WHERE id = :id;""")
                .single(call().bind("name", name)
                        .bind("inventory_type", inventoryType)
                        .bind("has_sizes", hasSizes)
                        .bind("homogeneous", homogeneous)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * The requirements pointing at an inventory, named well enough to go and deal with.
     *
     * <p>A requirement has no status: it is a standing profile rather than an event, so every one of
     * them counts against a switch, however old.
     *
     * @param inventoryId the inventory ID
     * @return one entry per requirement, labelled with the group or user type it asks of
     */
    public List<SwitchBlocker> findRequirementBlockers(int inventoryId) {
        return query("""
                SELECT r.id, coalesce(g.name, r.user_type, '') AS label
                FROM inventory_requirement r
                LEFT JOIN member_group g ON g.id = r.group_id
                WHERE r.inventory_id = :inventory_id
                ORDER BY r.position, r.id;""")
                .single(call().bind("inventory_id", inventoryId))
                .map(row -> new SwitchBlocker(SwitchBlockerKind.REQUIREMENT, row.getInt("id"), row.getString("label")))
                .all();
    }

    /**
     * The orders on an inventory that nothing has arrived for yet.
     *
     * <p>An order is open while it has no fulfilment time. One fulfilled two years ago is history,
     * and history never blocks.
     *
     * @param inventoryId the inventory ID
     * @return one entry per open order, labelled with who it is for
     */
    public List<SwitchBlocker> findOpenProcurementBlockers(int inventoryId) {
        return query("""
                SELECT p.id,
                       trim(coalesce(a.first_name, '') || ' ' || coalesce(a.last_name, '')) AS label
                FROM equipment_procurement p
                LEFT JOIN station_member sm ON sm.id = p.member_id
                LEFT JOIN account a ON a.id = sm.account_id
                WHERE p.inventory_id = :inventory_id AND p.fulfilled_at IS NULL
                ORDER BY p.requested_at;""")
                .single(call().bind("inventory_id", inventoryId))
                .map(row -> new SwitchBlocker(SwitchBlockerKind.PROCUREMENT, row.getInt("id"), row.getString("label")))
                .all();
    }

    /**
     * The exchanges on an inventory that are still walking their flow.
     *
     * <p>An exchange that reached its end, was declined or was called off has stopped moving, and a
     * finished one must not hold an inventory in place forever.
     *
     * @param inventoryId the inventory ID
     * @return one entry per open exchange, labelled with the member it is for
     */
    public List<SwitchBlocker> findOpenExchangeBlockers(int inventoryId) {
        return query("""
                SELECT m.id,
                       trim(coalesce(a.first_name, '') || ' ' || coalesce(a.last_name, '')) AS label
                FROM item_movement m
                LEFT JOIN station_member sm ON sm.id = m.member_id
                LEFT JOIN account a ON a.id = sm.account_id
                WHERE m.inventory_id = :inventory_id AND m.purpose = 'EXCHANGE' AND m.state = 'OPEN'
                ORDER BY m.created_at;""")
                .single(call().bind("inventory_id", inventoryId))
                .map(row -> new SwitchBlocker(SwitchBlockerKind.EXCHANGE, row.getInt("id"), row.getString("label")))
                .all();
    }

    /**
     * The sizes an inventory offers, as things that stand in the way of leaving the sized half.
     *
     * @param inventoryId the inventory ID
     * @return one entry per size, labelled with the size itself
     */
    public List<SwitchBlocker> findSizeBlockers(int inventoryId) {
        return findSizes(inventoryId).stream()
                .map(size -> new SwitchBlocker(SwitchBlockerKind.SIZE, size.id(), size.label()))
                .toList();
    }

    /**
     * The kinds of thing an inventory has been given, as things standing in the way of it becoming
     * an inventory of one thing in many copies.
     *
     * <p>The one thing that blocks the move in that direction, and it names them rather than
     * counting them, so somebody reading the refusal can go and clear them.
     *
     * @param inventoryId the inventory ID
     * @return one entry per kind, labelled with the kind's name
     */
    public List<SwitchBlocker> findArtBlockers(int inventoryId) {
        return query("""
                SELECT id, name FROM inventory_art
                WHERE inventory_id = :inventory_id
                ORDER BY position, name;""")
                .single(call().bind("inventory_id", inventoryId))
                .map(row -> new SwitchBlocker(SwitchBlockerKind.ART, row.getInt("id"), row.getString("name")))
                .all();
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
     * Every row of an inventory, whatever state it is in.
     *
     * <p>For readers that answer "what is recorded here": the export, which is read to prove what a
     * station is responsible for. The list a station reads to see its stock is {@link #findStock(int)}
     * and leaves out what is in the post.
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
     * The stock of an inventory: everything except what is in the post.
     *
     * <p>A piece on its way to its owner is at neither end. Counting it as stock says the station has
     * something it cannot lay a hand on, and every figure drawn from the list inherits that. Where it
     * is instead is the movement carrying it, which the overview lists.
     *
     * @param inventoryId the inventory ID
     * @return the items that are actually here
     */
    public List<InventoryItem> findStock(int inventoryId) {
        return query("""
                SELECT %s FROM inventory_item
                WHERE inventory_id = :inventory_id AND custody <> 'IN_TRANSIT';""", INVENTORY_ITEM_COLUMNS)
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

    /**
     * Names the cluster on every piece of gear at a station that was already recorded as the body above it
     * owning, and had nobody to name.
     *
     * <p>One statement, and nothing else moves: the items keep their inventory, their custody, whoever has
     * them and the chains they were walking. All that changes is that the body they already belonged to can
     * now be pointed at, which is the difference between an owner nobody can ask and one who can answer.
     *
     * @param clusterId the cluster the station has joined
     * @param stationId the station joining it
     * @return how many pieces of gear found their owner
     */
    public int adoptClusterItemsAt(int clusterId, int stationId) {
        return query("""
                UPDATE inventory_item ii
                SET owner_cluster_id = :cluster_id
                FROM inventory i
                WHERE i.id = ii.inventory_id
                  AND i.station_id = :station_id
                  AND ii.owner_kind = 'CLUSTER'
                  AND ii.owner_cluster_id IS NULL;""")
                .single(call().bind("cluster_id", clusterId).bind("station_id", stationId))
                .update()
                .rows();
    }

    /**
     * Everything a cluster owns, wherever it currently is.
     *
     * <p>The mirror of {@code findItemsByStation}: that one asks what a station holds, this one asks what a
     * cluster owns. Ownership and custody are separate, so the two lists overlap without either containing
     * the other.
     *
     * @param clusterId the owning cluster
     * @return its items, in store and out at stations alike
     */
    public List<InventoryItem> findItemsOwnedByCluster(int clusterId) {
        return query("""
                SELECT %s FROM inventory_item
                WHERE owner_kind = 'CLUSTER' AND owner_cluster_id = :cluster_id
                ORDER BY name, internal_id;""", INVENTORY_ITEM_COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * How much a cluster owns of each kind of thing, and where those pieces stand.
     *
     * <p>Counted in the database rather than over a list fetched into memory: an association with a few
     * thousand pieces has no use for every row when the question is how many jackets there are. The
     * grouping is the inventory the piece belongs to and the size it is cut to, which is what somebody
     * ordering a batch reads; a size of null is a row from an inventory that keeps none.
     *
     * @param clusterId the owning cluster
     * @return one row per inventory and size, the inventory's name on each
     */
    public List<OwnedCount> countItemsOwnedByCluster(int clusterId) {
        return query("""
                SELECT
                    i.id                                                          AS inventory_id,
                    i.name                                                        AS inventory_name,
                    it.size_id                                                    AS size_id,
                    s.label                                                       AS size_label,
                    count(*)                                                      AS total,
                    count(*) FILTER (WHERE it.custody = 'WITH_OWNER')             AS in_store,
                    count(*) FILTER (WHERE it.custody = 'WITH_MEMBER')            AS with_member,
                    count(*) FILTER (WHERE it.custody = 'WITH_PARTNER')           AS lent,
                    count(*) FILTER (WHERE it.custody = 'LOST')                   AS lost,
                    count(*) FILTER (WHERE it.custody IN ('AT_STATION', 'IN_TRANSIT')) AS at_station
                FROM inventory_item it
                JOIN inventory i ON i.id = it.inventory_id
                LEFT JOIN inventory_size s ON s.id = it.size_id
                WHERE it.owner_kind = 'CLUSTER' AND it.owner_cluster_id = :cluster_id
                GROUP BY i.id, i.name, it.size_id, s.label, s.position
                ORDER BY i.name, s.position NULLS FIRST, s.label;""")
                .single(call().bind("cluster_id", clusterId))
                .map(row -> new OwnedCount(
                        row.getInt("inventory_id"),
                        row.getString("inventory_name"),
                        row.getObject("size_id", Integer.class),
                        row.getString("size_label"),
                        row.getInt("total"),
                        row.getInt("in_store"),
                        row.getInt("at_station"),
                        row.getInt("with_member"),
                        row.getInt("lent"),
                        row.getInt("lost")))
                .all();
    }

    /**
     * One kind of thing in one size, and where its pieces stand.
     *
     * @param sizeId    the size, or null for an inventory that keeps none
     * @param inStore   resting in the owner's own store
     * @param atStation at one of its stations, on the way there included
     */
    public record OwnedCount(
            int inventoryId,
            String inventoryName,
            Integer sizeId,
            String sizeLabel,
            int total,
            int inStore,
            int atStation,
            int withMember,
            int lent,
            int lost) {}

    public List<InventorySize> findSizesByStation(int stationId) {
        return query("""
                SELECT %s FROM inventory_size s
                JOIN inventory i ON i.id = s.inventory_id
                WHERE i.station_id = :station_id ORDER BY s.position;""", SqlSupport.alias("s", INVENTORY_SIZE_COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(InventorySize.map())
                .all();
    }

    /**
     * The sizes behind a set of ids, whichever inventory at whichever station declared them.
     *
     * <p>What a cluster owns sits wherever it was handed to, and a size belongs to the inventory that
     * recorded it. Looking sizes up by the station the owner happens to live on therefore finds none of
     * the sizes on gear that is out, which is most of it.
     *
     * @param sizeIds the sizes the items in hand carry
     * @return them, empty when nothing was asked for
     */
    public List<InventorySize> findSizesByIds(Collection<Integer> sizeIds) {
        if (sizeIds == null || sizeIds.isEmpty()) return List.of();
        return query(
                        "SELECT %s FROM inventory_size WHERE id = ANY(:size_ids) ORDER BY position;",
                        INVENTORY_SIZE_COLUMNS)
                .single(call().bind("size_ids", List.copyOf(sizeIds), PostgreSqlTypes.INTEGER))
                .map(InventorySize.map())
                .all();
    }

    /**
     * The pieces a member has handed in for an exchange and not got back yet.
     *
     * <p>They are nobody's while the exchange runs, so nothing that reads the assignment finds them,
     * and a check would report the member short of gear that is merely in the post. For the question
     * "is this member equipped" they still count: a jacket being exchanged is a jacket they have.
     *
     * <p>Only an exchange. A return is meant to end with the member not having it, and gear on its way
     * to them counts when it arrives and not before.
     *
     * @param memberId the member
     * @return the pieces away in an open exchange of theirs
     */
    public List<InventoryItem> findItemsAwayInExchange(int memberId) {
        return query("""
                SELECT %s FROM inventory_item ii
                JOIN item_movement m ON m.outgoing_item_id = ii.id
                WHERE m.state = 'OPEN'
                  AND m.purpose = 'EXCHANGE'
                  AND m.member_id = :member_id
                  AND (ii.assigned_to IS NULL OR ii.assigned_to <> :member_id);""", SqlSupport.alias("ii", INVENTORY_ITEM_COLUMNS))
                .single(call().bind("member_id", memberId))
                .map(InventoryItem.map())
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
     * A member's own inventory: what they hold, and nothing else.
     *
     * <p>The assignment alone decides. A piece handed in for an exchange leaves the list at that
     * moment and a replacement joins it when it is handed over, which is what the member experiences
     * and therefore what the list should say. What is between the two is a movement, and the
     * movements of a member are read as movements.
     *
     * <p>The join is for a piece they are still holding while something runs on it: it carries the
     * step, so the row can say why the exchange button is missing. The distinct keeps an item that
     * somehow reached two movements to one line.
     *
     * @param memberId the member
     * @return their items, each with the movement it is on when there is one
     */
    public List<MemberInventoryEntry> findMemberEntries(int memberId) {
        return query("""
                SELECT DISTINCT ON (ii.id)
                    %s,
                    m.id AS movement_id,
                    s.label AS movement_step
                FROM inventory_item ii
                LEFT JOIN item_movement m
                       ON m.state = 'OPEN'
                      AND m.member_id = :member_id
                      AND m.outgoing_item_id = ii.id
                LEFT JOIN movement_flow_step s ON s.id = m.current_step_id
                WHERE ii.assigned_to = :member_id
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
        return createItem(inventoryId, internalId, name, sizeId, null, metadata, ownerKind, ownerClusterId);
    }

    /**
     * Creates a new inventory item that names the kind of thing it is.
     *
     * <p>Only the paths where somebody is present to say reach this one. The five that stamp the
     * inventory's name on a piece with nobody watching go through the overload above and leave the
     * kind unset, which is not a gap to be filled in later but the ordinary state of most pieces.
     *
     * @param inventoryId    the inventory ID
     * @param internalId     the internal identifier
     * @param name           the item name, which the kind never replaces
     * @param sizeId         the size ID, or {@code null}
     * @param artId          the kind of thing it is, or {@code null} when nobody said
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
            Integer artId,
            InventoryItemMetadata metadata,
            ItemOwner ownerKind,
            Integer ownerClusterId) {
        ItemOwner owner = ownerKind != null ? ownerKind : ItemOwner.STATION;
        boolean heldByStation = owner == ItemOwner.CLUSTER;
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_item(inventory_id, internal_id, name, size_id, art_id, metadata, owner_kind,
                                           owner_cluster_id, custody, custody_station_id)
                SELECT :inventory_id, :internal_id, :name, :size_id, :art_id, :metadata::JSONB, :owner_kind,
                       :owner_cluster_id, :custody,
                       CASE WHEN :held_by_station THEN i.station_id ELSE NULL END
                FROM inventory i
                WHERE i.id = :inventory_id
                RETURNING %s;""",
                call().bind("inventory_id", inventoryId)
                        .bind("internal_id", internalId)
                        .bind("name", name)
                        .bind("size_id", sizeId)
                        .bind("art_id", artId)
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
     * @param artId      the new kind, or {@code null} when the piece is to have none
     * @param metadata   the new JSON metadata
     * @return {@code true} if the item was updated
     */
    public boolean updateItem(
            int id, String internalId, String name, Integer sizeId, Integer artId, InventoryItemMetadata metadata) {
        return query("""
                UPDATE inventory_item
                SET
                    internal_id = :internal_id,
                    name        = :name,
                    size_id     = :size_id,
                    art_id      = :art_id,
                    metadata    = :metadata::JSONB
                WHERE id = :id;""")
                .single(call().bind("internal_id", internalId)
                        .bind("name", name)
                        .bind("size_id", sizeId)
                        .bind("art_id", artId)
                        .bind("metadata", (metadata != null ? metadata : InventoryItemMetadata.empty()).toJson())
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * The pieces of one kind, whatever state they are in.
     *
     * @param artId the kind
     * @return its pieces, in the order a list shows them
     */
    public List<InventoryItem> findItemsOfArt(int artId) {
        return query("""
                SELECT %s FROM inventory_item
                WHERE art_id = :art_id
                ORDER BY name, internal_id, id;""", INVENTORY_ITEM_COLUMNS)
                .single(call().bind("art_id", artId))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * Moves an item into another inventory, keeping the row it has always been.
     *
     * <p>This is the whole point of the statement being an update rather than a delete and an insert:
     * the identifier, the history, the assignment and the custody chain all hang off this row, and
     * recreating the item somewhere else throws every one of them away.
     *
     * <p>The size goes with the move, because the size list belongs to the inventory being left. The
     * caller has either found the same label in the new inventory or has nothing to put there, and
     * either way what arrives here is a size of the target or {@code null}.
     *
     * <p>The kind goes the same way and for the same reason, except that nothing is remapped: a kind
     * belongs to the inventory being left, so a piece arrives in the new drawer without one and
     * somebody says what it is there.
     *
     * @param id          the item ID
     * @param inventoryId the inventory it is moving into
     * @param sizeId      its size in the new inventory, or {@code null} when it has none there
     * @return {@code true} if the item was moved
     */
    public boolean moveItemToInventory(int id, int inventoryId, Integer sizeId) {
        return query("""
                UPDATE inventory_item
                SET inventory_id = :inventory_id,
                    size_id      = :size_id,
                    art_id       = NULL
                WHERE id = :id;""")
                .single(call().bind("inventory_id", inventoryId)
                        .bind("size_id", sizeId)
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
     * Writes or clears what was said when the item was reported missing.
     *
     * @param itemId the item ID
     * @param note   what was written, or {@code null} to clear it
     * @param noteBy who wrote it, or {@code null}
     * @return {@code true} if the item row was updated
     */
    public boolean setLostNote(int itemId, String note, Integer noteBy) {
        return query("""
                UPDATE inventory_item SET lost_note = :note, lost_note_by = :note_by WHERE id = :id;""")
                .single(call().bind("note", note).bind("note_by", noteBy).bind("id", itemId))
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
     * Marks the open history entry for an item and member as a correction, so that closing it reads
     * as putting the record right rather than as the member handing the item back.
     *
     * <p>Written before the spell is closed, while there is still exactly one open entry to hit.
     *
     * @param itemId   the item ID
     * @param memberId the member ID
     * @return {@code true} if a history entry was updated
     */
    public boolean markSpellCorrected(int itemId, int memberId) {
        return query(
                        "UPDATE inventory_item_history SET corrected = TRUE WHERE item_id = :itemId AND member_id = :memberId AND returned IS NULL;")
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
     * The requirements that count for somebody at a station: the station's own, and those the cluster above
     * it has declared.
     *
     * <p>A cluster's requirement is an ordinary row on the cluster's own inventory, so there is nothing to
     * merge here and no name matching to do: the station reads one definition rather than reconciling two. A
     * cluster that does not keep its gear in Ember contributes none, which is what lets a station under such
     * a cluster carry on exactly as it did before.
     *
     * <p>A cluster's requirement may name a group of stations, and then counts only at the stations in it. A
     * station's own requirement never names one, so the same condition leaves every one of those alone.
     *
     * @param stationId the station reading them
     * @return its own and the cluster's, ordered by position
     */
    public List<InventoryRequirement> findRequirementsCountingAt(int stationId) {
        return query("""
                SELECT %s FROM inventory_requirement r
                JOIN inventory i ON r.inventory_id = i.id
                WHERE (i.station_id = :station_id
                   OR i.station_id IN (SELECT c.home_station_id FROM cluster c
                                       JOIN station s ON s.cluster_id = c.id
                                       WHERE s.id = :station_id AND c.uses_inventory))
                  AND (r.station_group_id IS NULL
                       OR EXISTS (SELECT 1 FROM cluster_station_group_membership m
                                  WHERE m.group_id = r.station_group_id AND m.station_id = :station_id))
                ORDER BY r.position, r.id;""", SqlSupport.alias("r", INVENTORY_REQUIREMENT_COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(InventoryRequirement.map())
                .all();
    }

    /**
     * The same list, each row carrying the name of the inventory it points at and whether it came from the
     * cluster.
     *
     * <p>The name travels with the row because a cluster's inventory is not among the station's own, so the
     * screen has nothing to look it up in.
     *
     * @param stationId the station reading them
     * @return its own and the cluster's, ordered by position
     */
    public List<VisibleRequirement> findRequirementsVisibleAt(int stationId) {
        return query("""
                SELECT %s, i.name AS inventory_name, i.station_id <> :station_id AS from_cluster
                FROM inventory_requirement r
                JOIN inventory i ON r.inventory_id = i.id
                WHERE (i.station_id = :station_id
                   OR i.station_id IN (SELECT c.home_station_id FROM cluster c
                                       JOIN station s ON s.cluster_id = c.id
                                       WHERE s.id = :station_id AND c.uses_inventory))
                  AND (r.station_group_id IS NULL
                       OR EXISTS (SELECT 1 FROM cluster_station_group_membership m
                                  WHERE m.group_id = r.station_group_id AND m.station_id = :station_id))
                ORDER BY r.position, r.id;""", SqlSupport.alias("r", INVENTORY_REQUIREMENT_COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(row -> new VisibleRequirement(
                        InventoryRequirement.map().map(row),
                        row.getString("inventory_name"),
                        row.getBoolean("from_cluster")))
                .all();
    }

    /**
     * One requirement as a station reads it, with the name of what it asks for and where it was written.
     *
     * @param requirement   the row itself
     * @param inventoryName what the requirement points at
     * @param fromCluster   whether the cluster above the station wrote it, in which case the station may
     *                      read it and nothing more
     */
    public record VisibleRequirement(InventoryRequirement requirement, String inventoryName, boolean fromCluster) {}

    /**
     * Creates a new inventory requirement for a role or group.
     *
     * @param inventoryId the inventory ID
     * @param userType    the user type name, or {@code null} for no user type restriction
     * @param groupId        the group ID (0 means no group restriction)
     * @param stationGroupId the group of stations it counts at, or null for every station reading it
     * @param quantity       the required quantity
     * @return the created requirement
     */
    public InventoryRequirement createRequirement(
            int inventoryId, StationUserType userType, int groupId, Integer stationGroupId, int quantity) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_requirement(inventory_id, user_type, group_id, station_group_id, quantity)
                VALUES(:inventoryId, :userType, :groupId, :stationGroupId, :quantity)
                RETURNING %s;""",
                call().bind("inventoryId", inventoryId)
                        .bind("userType", userType)
                        .bind("groupId", groupId == 0 ? null : groupId)
                        .bind("stationGroupId", stationGroupId)
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
