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
 * @param artId          the kind of thing this piece is, or {@code null} when nobody has said. Null
 *                       is the ordinary state and not a gap: five of the seven ways a piece comes
 *                       into being have nobody present to name a kind, so every read of this
 *                       tolerates its absence rather than treating it as unfinished
 * @param metadata       JSON metadata associated with the item
 * @param assignedTo     the member this item is assigned to, or {@code null} if unassigned
 * @param lostAt         when the item was marked as lost, or {@code null} if not lost
 * @param lostNote       what was written when it was marked lost, or {@code null} if nothing was
 * @param lostNoteBy     who wrote that note, which is the guardian when one acted for a member
 * @param ownerKind      who owns the item: the station, the one body above it, or a federation partner
 * @param ownerClusterId the owning body when it runs on this instance, or {@code null} when it does not
 * @param ownerStationId the owning partner station, set only for {@link ItemOwner#PARTNER_STATION}
 * @param loanRequestItemId the line of the lending request this borrowed copy came in on, set only
 *                          for {@link ItemOwner#PARTNER_STATION}
 * @param custody        who has the item right now
 * @param custodyStationId the station the custody runs through, or {@code null} for {@link ItemCustody#WITH_OWNER}
 * @param custodyPartnerStationId the partner holding the item while it is {@link ItemCustody#WITH_PARTNER},
 *                                or {@code null} for every other custody
 * @param custodyMovementId the movement holding the item while it is in transit, or {@code null}
 * @param containerId    the container that physically holds this item, or {@code null} if unlocated
 */
public record InventoryItem(
        int id,
        int inventoryId,
        String internalId,
        String name,
        Integer sizeId,
        Integer artId,
        InventoryItemMetadata metadata,
        Integer assignedTo,
        Instant lostAt,
        String lostNote,
        Integer lostNoteBy,
        ItemOwner ownerKind,
        Integer ownerClusterId,
        Integer ownerStationId,
        Integer loanRequestItemId,
        ItemCustody custody,
        Integer custodyStationId,
        Integer custodyPartnerStationId,
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
                row.getObject("art_id", Integer.class),
                InventoryItemMetadata.parse(row.getString("metadata")),
                row.getObject("assigned_to", Integer.class),
                row.get("lost_at", INSTANT_TIMESTAMP),
                row.getString("lost_note"),
                row.getObject("lost_note_by", Integer.class),
                row.getEnum("owner_kind", ItemOwner.class),
                row.getObject("owner_cluster_id", Integer.class),
                row.getObject("owner_station_id", Integer.class),
                row.getObject("loan_request_item_id", Integer.class),
                row.getEnum("custody", ItemCustody.class),
                row.getObject("custody_station_id", Integer.class),
                row.getObject("custody_partner_station_id", Integer.class),
                row.getObject("custody_movement_id", Integer.class),
                row.getObject("container_id", Integer.class));
    }

    /**
     * Whether the station running this item's inventory owns the item itself.
     *
     * @return {@code true} when the station owns it, {@code false} when somebody else does
     */
    public boolean ownedByStation() {
        return ownerKind == ItemOwner.STATION;
    }

    /**
     * Whether this row is a borrowed copy of a partner's gear rather than the station's record of a
     * thing of its own.
     *
     * @return {@code true} when a federation partner owns it
     */
    public boolean borrowed() {
        return ownerKind == ItemOwner.PARTNER_STATION;
    }
}
