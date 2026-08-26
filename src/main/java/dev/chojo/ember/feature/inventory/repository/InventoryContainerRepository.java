/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.inventory.entity.ContainerHistoryDetails;
import dev.chojo.ember.feature.inventory.entity.ContainerPath;
import dev.chojo.ember.feature.inventory.entity.InventoryContainer;
import dev.chojo.ember.feature.inventory.entity.InventoryContainerHistory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for storage containers, their lifecycle history, and the
 * read-side queries used to render container trees and item locations.
 */
@Singleton
public class InventoryContainerRepository {

    private static final String INVENTORY_CONTAINER_COLUMNS =
            "id, station_id, parent_id, internal_id, name, kind_id, description, created_at, created_by";
    private static final String INVENTORY_ITEM_COLUMNS =
            "id, inventory_id, internal_id, name, size_id, metadata, assigned_to, lost_at, lost_note, lost_note_by, owner_kind, owner_cluster_id, custody, custody_station_id, custody_movement_id, container_id";
    private static final String INVENTORY_CONTAINER_HISTORY_COLUMNS =
            "id, container_id, station_id, event_kind, event_ts, actor_id";

    /**
     * Finds a container by its ID.
     *
     * @param id the container ID
     * @return the container, or empty if not found
     */
    public Optional<InventoryContainer> findById(int id) {
        return SqlSupport.findById("inventory_container", INVENTORY_CONTAINER_COLUMNS, id, InventoryContainer.map());
    }

    /**
     * Finds a container by its internal id within a station.
     *
     * @param stationId  the station ID
     * @param internalId the internal id (e.g. from a barcode scan)
     * @return the container, or empty if not found
     */
    public Optional<InventoryContainer> findByInternalId(int stationId, String internalId) {
        return query("""
                SELECT %s FROM inventory_container
                WHERE station_id = :station_id AND internal_id = :internal_id LIMIT 1;""", INVENTORY_CONTAINER_COLUMNS)
                .single(call().bind("station_id", stationId).bind("internal_id", internalId))
                .map(InventoryContainer.map())
                .first();
    }

    /**
     * Returns every container in a station, ordered for display.
     */
    public List<InventoryContainer> findByStation(int stationId) {
        return query("""
                SELECT %s FROM inventory_container
                WHERE station_id = :station_id ORDER BY name;""", INVENTORY_CONTAINER_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(InventoryContainer.map())
                .all();
    }

    /**
     * Returns all root containers for a station (containers with no parent).
     */
    public List<InventoryContainer> findRoots(int stationId) {
        return query("""
                SELECT %s FROM inventory_container
                WHERE station_id = :station_id AND parent_id IS NULL ORDER BY name;""", INVENTORY_CONTAINER_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(InventoryContainer.map())
                .all();
    }

    /**
     * Returns the direct children of a container, ordered by display name.
     */
    public List<InventoryContainer> findChildren(int parentId) {
        return query("""
                SELECT %s FROM inventory_container
                WHERE parent_id = :parent_id ORDER BY name;""", INVENTORY_CONTAINER_COLUMNS)
                .single(call().bind("parent_id", parentId))
                .map(InventoryContainer.map())
                .all();
    }

    /**
     * Returns every container in the subtree rooted at {@code id} (inclusive),
     * walked depth-first with siblings in display-name order. The root
     * appears first, followed by each subtree fully before the next sibling.
     */
    public List<InventoryContainer> findSubtree(int id) {
        return query("""
                WITH RECURSIVE subtree(id, station_id, parent_id, internal_id, name, kind_id,
                                       description, created_at, created_by, sort_path) AS (
                    SELECT id, station_id, parent_id, internal_id, name, kind_id,
                           description, created_at, created_by, ARRAY[name]
                    FROM inventory_container WHERE id = :id
                    UNION ALL
                    SELECT c.id, c.station_id, c.parent_id, c.internal_id, c.name, c.kind_id,
                           c.description, c.created_at, c.created_by, subtree.sort_path || c.name
                    FROM inventory_container c JOIN subtree ON c.parent_id = subtree.id
                )
                SELECT id, station_id, parent_id, internal_id, name, kind_id,
                       description, created_at, created_by
                FROM subtree ORDER BY sort_path;""")
                .single(call().bind("id", id))
                .map(InventoryContainer.map())
                .all();
    }

    /**
     * Returns the root-first path of names and ids leading to the given
     * container, or an empty path if the container does not exist.
     */
    public ContainerPath findPath(int id) {
        List<int[]> ordered = query("""
                WITH RECURSIVE chain(id, parent_id, name, depth) AS (
                    SELECT id, parent_id, name, 0 FROM inventory_container WHERE id = :id
                    UNION ALL
                    SELECT c.id, c.parent_id, c.name, chain.depth + 1
                    FROM inventory_container c JOIN chain ON c.id = chain.parent_id
                )
                SELECT id, name, depth FROM chain ORDER BY depth DESC;""")
                .single(call().bind("id", id))
                .map(row -> new int[] {row.getInt("id"), row.getInt("depth")})
                .all();
        if (ordered.isEmpty()) {
            return ContainerPath.empty();
        }
        List<String> names = query("""
                WITH RECURSIVE chain(id, parent_id, name, depth) AS (
                    SELECT id, parent_id, name, 0 FROM inventory_container WHERE id = :id
                    UNION ALL
                    SELECT c.id, c.parent_id, c.name, chain.depth + 1
                    FROM inventory_container c JOIN chain ON c.id = chain.parent_id
                )
                SELECT name FROM chain ORDER BY depth DESC;""")
                .single(call().bind("id", id))
                .map(row -> row.getString("name"))
                .all();
        List<Integer> ids = new ArrayList<>(ordered.size());
        for (int[] row : ordered) {
            ids.add(row[0]);
        }
        return new ContainerPath(names, ids);
    }

    /**
     * Returns whether the given station has any container row with the
     * supplied internal id, excluding optionally one container by id.
     */
    public boolean internalIdExists(int stationId, String internalId, Integer excludeContainerId) {
        return SqlSupport.exists(
                """
                SELECT 1 FROM inventory_container
                WHERE station_id = :station_id AND internal_id = :internal_id
                  AND (:exclude_id::int IS NULL OR id <> :exclude_id) LIMIT 1;""",
                call().bind("station_id", stationId)
                        .bind("internal_id", internalId)
                        .bind("exclude_id", excludeContainerId));
    }

    /**
     * Creates a new container row.
     */
    public InventoryContainer create(
            int stationId,
            Integer parentId,
            String internalId,
            String name,
            Integer kindId,
            String description,
            Integer createdBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_container(station_id, parent_id, internal_id, name, kind_id, description, created_by)
                VALUES(:station_id, :parent_id, :internal_id, :name, :kind_id, :description, :created_by)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("parent_id", parentId)
                        .bind("internal_id", internalId)
                        .bind("name", name)
                        .bind("kind_id", kindId)
                        .bind("description", description != null ? description : "")
                        .bind("created_by", createdBy),
                InventoryContainer.map(),
                INVENTORY_CONTAINER_COLUMNS);
    }

    /**
     * Updates a container's mutable fields.
     */
    public boolean update(
            int id, Integer parentId, String internalId, String name, Integer kindId, String description) {
        return query("""
                UPDATE inventory_container
                SET parent_id = :parent_id,
                    internal_id = :internal_id,
                    name = :name,
                    kind_id = :kind_id,
                    description = :description
                WHERE id = :id;""")
                .single(call().bind("parent_id", parentId)
                        .bind("internal_id", internalId)
                        .bind("name", name)
                        .bind("kind_id", kindId)
                        .bind("description", description != null ? description : "")
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes a container by id. The schema demotes any direct children to
     * roots and clears {@code container_id} on items via {@code ON DELETE SET NULL}.
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("inventory_container", id);
    }

    /**
     * Returns the direct items placed in a container, ordered by name.
     */
    public List<InventoryItem> findItemsInContainer(int containerId) {
        return query("""
                SELECT %s FROM inventory_item
                WHERE container_id = :container_id ORDER BY name;""", INVENTORY_ITEM_COLUMNS)
                .single(call().bind("container_id", containerId))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * Returns every item in the subtree rooted at the given container, ordered
     * such that items appear next to their containing node in the depth-first
     * traversal.
     */
    public List<InventoryItem> findItemsInSubtree(int containerId) {
        return query("""
                WITH RECURSIVE subtree(id, sort_path) AS (
                    SELECT id, ARRAY[name] FROM inventory_container WHERE id = :id
                    UNION ALL
                    SELECT c.id, subtree.sort_path || c.name
                    FROM inventory_container c JOIN subtree ON c.parent_id = subtree.id
                )
                SELECT %s FROM inventory_item ii
                JOIN subtree ON subtree.id = ii.container_id
                ORDER BY subtree.sort_path, ii.name;""", SqlSupport.alias("ii", INVENTORY_ITEM_COLUMNS))
                .single(call().bind("id", containerId))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * Returns the lifecycle history for a container, most recent first.
     */
    public List<InventoryContainerHistory> findHistory(int containerId) {
        return query("""
                SELECT %s, details::text AS details
                FROM inventory_container_history
                WHERE container_id = :container_id
                ORDER BY event_ts DESC;""", INVENTORY_CONTAINER_HISTORY_COLUMNS)
                .single(call().bind("container_id", containerId))
                .map(InventoryContainerHistory.map())
                .all();
    }

    /**
     * Returns the most recent history rows for an entire station, capped at
     * {@code limit} rows.
     */
    public List<InventoryContainerHistory> findRecentHistory(int stationId, int limit) {
        return query("""
                SELECT %s, details::text AS details
                FROM inventory_container_history
                WHERE station_id = :station_id
                ORDER BY event_ts DESC LIMIT :limit;""", INVENTORY_CONTAINER_HISTORY_COLUMNS)
                .single(call().bind("station_id", stationId).bind("limit", limit))
                .map(InventoryContainerHistory.map())
                .all();
    }

    /**
     * Appends a lifecycle event for a container. The event kind is taken from the
     * payload, which is the only thing that can describe its own shape.
     *
     * @param containerId the subject container, or {@code null} for a deletion
     * @param stationId   the station the event happened in
     * @param actorId     the acting member, or {@code null} for system events
     * @param details     the typed event payload
     */
    public void appendHistory(Integer containerId, int stationId, Integer actorId, ContainerHistoryDetails details) {
        query("""
                INSERT INTO inventory_container_history(container_id, station_id, event_kind, actor_id, details)
                VALUES(:container_id, :station_id, :event_kind, :actor_id, :details::jsonb);""")
                .single(call().bind("container_id", containerId)
                        .bind("station_id", stationId)
                        .bind("event_kind", details.eventKind())
                        .bind("actor_id", actorId)
                        .bind("details", details.toJson()))
                .insert();
    }

    /**
     * Returns every item in the given station whose internal id is in
     * {@code internalIds}. Used to validate scan uniqueness across the shared
     * item-and-container namespace.
     */
    public List<Integer> findContainerIdsByInternalIds(int stationId, List<String> internalIds) {
        if (internalIds == null || internalIds.isEmpty()) return List.of();
        return query("""
                SELECT id FROM inventory_container
                WHERE station_id = :station_id AND internal_id = ANY(:ids);""")
                .single(call().bind("station_id", stationId).bind("ids", internalIds, PostgreSqlTypes.VARCHAR))
                .map(row -> row.getInt("id"))
                .all();
    }
}
