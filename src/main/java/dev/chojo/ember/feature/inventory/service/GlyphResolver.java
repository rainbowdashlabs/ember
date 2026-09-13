/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.Glyph;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * The picture a piece of gear is drawn with, for the payloads that carry the answer rather than the
 * parts of it.
 *
 * <p>The order is the kind, then the inventory, then a plain shape. The two halves resolve together
 * rather than field by field: a kind that set a colour and no shape is drawn in its colour on the
 * inventory's shape, which is what somebody choosing only a colour meant.
 *
 * <p>A screen that has its inventories and kinds already loaded resolves this itself, in the frontend,
 * by the same order. This exists for the payloads that flatten the rest of an inventory's facts too,
 * because most screens that list pieces load no inventories at all.
 */
@Singleton
public class GlyphResolver {
    /** The shape a stock falls back to: one thing in many copies. */
    private static final String STOCK_FALLBACK = "cube";

    /** The shape a collection falls back to: a drawer of different things. */
    private static final String COLLECTION_FALLBACK = "box";

    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;

    @Inject
    public GlyphResolver(InventoryRepository inventoryRepository, InventoryArtRepository artRepository) {
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
    }

    /**
     * The picture one piece is drawn with.
     *
     * @param item the piece
     * @return the shape and the colour, the shape always answered
     */
    public Glyph forItem(InventoryItem item) {
        if (item == null) return new Glyph(STOCK_FALLBACK, null);
        Optional<InventoryArt> art = item.artId() == null ? Optional.empty() : artRepository.findById(item.artId());
        Optional<Inventory> inventory = inventoryRepository.findById(item.inventoryId());
        return resolve(
                art.map(InventoryArt::icon).orElse(null),
                art.map(InventoryArt::color).orElse(null),
                inventory.map(Inventory::icon).orElse(null),
                inventory.map(Inventory::color).orElse(null),
                inventory.map(Inventory::homogeneous).orElse(true));
    }

    /**
     * The picture one piece is drawn with, looked up by id.
     *
     * @param itemId the piece, or {@code null} for a row about no piece yet
     * @return the shape and the colour, the shape always answered
     */
    public Glyph forItemId(Integer itemId) {
        if (itemId == null) return new Glyph(STOCK_FALLBACK, null);
        return inventoryRepository.findItemById(itemId).map(this::forItem).orElse(new Glyph(STOCK_FALLBACK, null));
    }

    /**
     * The picture a whole inventory is drawn with, for a row that is about a kind of thing rather than
     * about one piece.
     *
     * @param inventoryId the inventory, or {@code null} for a row about no inventory
     * @return the shape and the colour, the shape always answered
     */
    public Glyph forInventoryId(Integer inventoryId) {
        if (inventoryId == null) return new Glyph(STOCK_FALLBACK, null);
        return inventoryRepository
                .findById(inventoryId)
                .map(inventory -> resolve(null, null, inventory.icon(), inventory.color(), inventory.homogeneous()))
                .orElse(new Glyph(STOCK_FALLBACK, null));
    }

    private Glyph resolve(
            String artIcon, String artColor, String inventoryIcon, String inventoryColor, boolean homogeneous) {
        String icon = firstNamed(artIcon, inventoryIcon);
        return new Glyph(
                icon != null ? icon : (homogeneous ? STOCK_FALLBACK : COLLECTION_FALLBACK),
                firstNamed(artColor, inventoryColor));
    }

    private String firstNamed(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }
}
