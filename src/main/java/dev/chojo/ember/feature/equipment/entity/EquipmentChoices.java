/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.entity;

import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What a line of an appointment can ask for: the station's inventories, the kinds of its mixed
 * inventories and its pieces, each with no more than a picker shows.
 *
 * <p>Whoever plans an appointment chooses from the station's gear without holding the right to read
 * the inventory, so notes, metadata, assignments and containers stay out. A piece says only whether it
 * is at the station or with a member, which is what the count of pieces at hand is taken from.
 *
 * @param inventories every inventory of the station
 * @param arts        every kind of every inventory of different things
 * @param items       every piece the station holds
 */
public record EquipmentChoices(List<InventoryChoice> inventories, List<ArtChoice> arts, List<ItemChoice> items) {

    /**
     * An inventory as a picker names and draws it.
     *
     * @param id            the inventory
     * @param name          its name
     * @param inventoryType whose gear it holds
     * @param homogeneous   whether it holds one thing in many copies
     * @param icon          its picture, or {@code null}
     * @param color         the colour of its picture, or {@code null}
     */
    public record InventoryChoice(
            int id,
            String name,
            InventoryType inventoryType,
            boolean homogeneous,
            @Nullable String icon,
            @Nullable String color) {

        /**
         * The parts of an inventory a picker shows.
         *
         * @param inventory the inventory
         * @return the choice
         */
        public static InventoryChoice of(Inventory inventory) {
            return new InventoryChoice(
                    inventory.id(),
                    inventory.name(),
                    inventory.inventoryType(),
                    inventory.homogeneous(),
                    inventory.icon(),
                    inventory.color());
        }
    }

    /**
     * A kind as a picker names and draws it.
     *
     * @param id          the kind
     * @param inventoryId the inventory it belongs to
     * @param name        its name
     * @param icon        its picture, or {@code null}
     * @param color       the colour of its picture, or {@code null}
     */
    public record ArtChoice(
            int id,
            int inventoryId,
            String name,
            @Nullable String icon,
            @Nullable String color) {

        /**
         * The parts of a kind a picker shows.
         *
         * @param art the kind
         * @return the choice
         */
        public static ArtChoice of(InventoryArt art) {
            return new ArtChoice(art.id(), art.inventoryId(), art.name(), art.icon(), art.color());
        }
    }

    /**
     * A piece as a picker names it, and whether it counts as at hand.
     *
     * @param id          the piece
     * @param inventoryId the inventory it sits in
     * @param artId       its kind, or {@code null}
     * @param name        its name
     * @param internalId  the code written on it, or {@code null}
     * @param sizeLabel   its size, or {@code null}
     * @param ownerKind   whose piece it is
     * @param custody     where it is, without saying with whom
     */
    public record ItemChoice(
            int id,
            int inventoryId,
            @Nullable Integer artId,
            String name,
            @Nullable String internalId,
            @Nullable String sizeLabel,
            ItemOwner ownerKind,
            ItemCustody custody) {

        /**
         * The parts of a piece a picker shows.
         *
         * @param item      the piece
         * @param sizeLabel the label of its size, or {@code null}
         * @return the choice
         */
        public static ItemChoice of(InventoryItem item, @Nullable String sizeLabel) {
            return new ItemChoice(
                    item.id(),
                    item.inventoryId(),
                    item.artId(),
                    item.name(),
                    item.internalId(),
                    sizeLabel,
                    item.ownerKind(),
                    item.custody());
        }
    }
}
