/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents an individual item within an inventory that can be assigned to members.
 *
 * @param id             the unique item identifier
 * @param inventoryId    the inventory this item belongs to
 * @param internalId     an internal identifier for the item (e.g. serial number)
 * @param name           the display name of the item
 * @param sizeId         the size variant of the item, or {@code null} if not applicable
 * @param metadata       JSON metadata associated with the item
 * @param assignedTo     the member this item is assigned to, or {@code null} if unassigned
 * @param lostAt         when the item was marked as lost, or {@code null} if not lost
 * @param lostNote       what was written when it was marked lost, or {@code null} if nothing was
 * @param lostNoteBy     who wrote that note, which is the guardian when one acted for a member
 * @param ownerKind      who owns the item: the station, or the one body above it
 * @param ownerClusterId the owning body when it runs on this instance, or {@code null} when it does not
 * @param custody        who has the item right now
 * @param custodyStationId the station the custody runs through, or {@code null} for {@link ItemCustody#WITH_OWNER}
 * @param custodyMovementId the movement holding the item while it is in transit, or {@code null}
 * @param containerId    the container that physically holds this item, or {@code null} if unlocated
 */
public record InventoryItem(
        int id,
        int inventoryId,
        String internalId,
        String name,
        Integer sizeId,
        InventoryItemMetadata metadata,
        Integer assignedTo,
        Instant lostAt,
        String lostNote,
        Integer lostNoteBy,
        ItemOwner ownerKind,
        Integer ownerClusterId,
        ItemCustody custody,
        Integer custodyStationId,
        Integer custodyMovementId,
        Integer containerId) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryItem> map() {
        return row -> new InventoryItem(
                row.getInt("id"),
                row.getInt("inventory_id"),
                row.getString("internal_id"),
                row.getString("name"),
                row.getObject("size_id", Integer.class),
                InventoryItemMetadata.parse(row.getString("metadata")),
                row.getObject("assigned_to", Integer.class),
                row.get("lost_at", INSTANT_TIMESTAMP),
                row.getString("lost_note"),
                row.getObject("lost_note_by", Integer.class),
                row.getEnum("owner_kind", ItemOwner.class),
                row.getObject("owner_cluster_id", Integer.class),
                row.getEnum("custody", ItemCustody.class),
                row.getObject("custody_station_id", Integer.class),
                row.getObject("custody_movement_id", Integer.class),
                row.getObject("container_id", Integer.class));
    }

    /**
     * Whether the station running this item's inventory owns the item itself.
     *
     * @return {@code true} when the station owns it, {@code false} when the body above it does
     */
    public boolean ownedByStation() {
        return ownerKind == ItemOwner.STATION;
    }
}
