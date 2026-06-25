/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.inventory.entity.InventoryContainerKind;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing the station-defined container-kind catalogue.
 */
@Singleton
public class InventoryContainerKindRepository {

    /**
     * Finds a kind by its ID.
     *
     * @param id the kind ID
     * @return the kind, or empty if not found
     */
    public Optional<InventoryContainerKind> findById(int id) {
        return query("""
                SELECT id, station_id, key, label, icon, sort_order, enabled
                FROM inventory_container_kind
                WHERE id = :id;""")
                .single(call().bind("id", id))
                .map(InventoryContainerKind.map())
                .first();
    }

    /**
     * Finds the kind with the given key inside a station.
     *
     * @param stationId the station ID
     * @param key       the stable machine key
     * @return the kind, or empty if not found
     */
    public Optional<InventoryContainerKind> findByKey(int stationId, String key) {
        return query("""
                SELECT id, station_id, key, label, icon, sort_order, enabled
                FROM inventory_container_kind
                WHERE station_id = :station_id AND key = :key;""")
                .single(call().bind("station_id", stationId).bind("key", key))
                .map(InventoryContainerKind.map())
                .first();
    }

    /**
     * Returns every kind defined for a station, ordered by {@code sort_order} then {@code key}.
     *
     * @param stationId the station ID
     * @return ordered list of kinds
     */
    public List<InventoryContainerKind> findByStation(int stationId) {
        return query("""
                SELECT id, station_id, key, label, icon, sort_order, enabled
                FROM inventory_container_kind
                WHERE station_id = :station_id
                ORDER BY sort_order, key;""")
                .single(call().bind("station_id", stationId))
                .map(InventoryContainerKind.map())
                .all();
    }

    /**
     * Creates a new kind.
     *
     * @param stationId the station ID
     * @param key       stable machine key
     * @param label     display label
     * @param icon      FontAwesome icon name
     * @param sortOrder ordering hint
     * @param enabled   whether the kind is selectable for new containers
     * @return the created kind
     */
    public InventoryContainerKind create(
            int stationId, String key, String label, String icon, int sortOrder, boolean enabled) {
        return query("""
                INSERT INTO inventory_container_kind(station_id, key, label, icon, sort_order, enabled)
                VALUES(:station_id, :key, :label, :icon, :sort_order, :enabled)
                RETURNING id, station_id, key, label, icon, sort_order, enabled;""")
                .single(call().bind("station_id", stationId)
                        .bind("key", key)
                        .bind("label", label)
                        .bind("icon", icon)
                        .bind("sort_order", sortOrder)
                        .bind("enabled", enabled))
                .map(InventoryContainerKind.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates a kind's mutable fields.
     *
     * @param id        the kind ID
     * @param label     new display label
     * @param icon      new icon name
     * @param sortOrder new ordering hint
     * @param enabled   new enabled flag
     * @return {@code true} if the kind was updated
     */
    public boolean update(int id, String label, String icon, int sortOrder, boolean enabled) {
        return query("""
                UPDATE inventory_container_kind
                SET label = :label, icon = :icon, sort_order = :sort_order, enabled = :enabled
                WHERE id = :id;""")
                .single(call().bind("label", label)
                        .bind("icon", icon)
                        .bind("sort_order", sortOrder)
                        .bind("enabled", enabled)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes a kind by its ID.
     *
     * @param id the kind ID
     * @return {@code true} if the kind was deleted
     */
    public boolean delete(int id) {
        return query("DELETE FROM inventory_container_kind WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Returns whether any kind exists for the given station.
     */
    public boolean stationHasAnyKind(int stationId) {
        return query("SELECT 1 FROM inventory_container_kind WHERE station_id = :station_id LIMIT 1;")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getInt(1))
                .first()
                .isPresent();
    }
}
