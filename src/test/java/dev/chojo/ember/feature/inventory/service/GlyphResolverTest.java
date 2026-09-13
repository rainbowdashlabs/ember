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
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The picture a piece wears, as the flattened payloads answer it.
 *
 * <p>The cases worth holding are the fallback chain and the one that makes the pair one answer: a kind
 * that chose a colour and no shape is drawn in its colour on the inventory's shape.
 */
class GlyphResolverTest extends RepositoryTestBase {
    private static GlyphResolver resolver;
    private static Station station;

    @BeforeAll
    static void setup() {
        resolver = new GlyphResolver(inventoryRepo, artRepo);
        station = stationRepo.create("Glyphs");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private Inventory stock(Glyph glyph) {
        return inventoryRepo.create(
                station.id(), "Stock " + System.nanoTime(), InventoryType.INTERNAL, false, true, glyph);
    }

    private Inventory collection(Glyph glyph) {
        return inventoryRepo.create(
                station.id(), "Drawer " + System.nanoTime(), InventoryType.INTERNAL, false, false, glyph);
    }

    private InventoryItem piece(int inventoryId, Integer artId) {
        return inventoryRepo.createItem(inventoryId, null, "Ein Teil", null, artId, null, null, null);
    }

    @Test
    void aPieceWearsItsInventorysPicture() {
        Inventory helmets = stock(new Glyph("helmet-safety", "#dc2626"));

        Glyph glyph = resolver.forItem(piece(helmets.id(), null));

        assertEquals("helmet-safety", glyph.icon());
        assertEquals("#dc2626", glyph.color());
    }

    @Test
    void aKindOverrulesTheInventory() {
        Inventory drawer = collection(new Glyph("box", "#57534e"));
        InventoryArt radios = artRepo.create(drawer.id(), "Funk", "", 0, new Glyph("walkie-talkie", "#2563eb"));

        Glyph glyph = resolver.forItem(piece(drawer.id(), radios.id()));

        assertEquals("walkie-talkie", glyph.icon());
        assertEquals("#2563eb", glyph.color());
    }

    /** The pair is one answer: a colour with no shape lands on the shape the inventory already had. */
    @Test
    void aKindMayCarryOnlyAColour() {
        Inventory drawer = collection(new Glyph("radio", "#0f766e"));
        InventoryArt green = artRepo.create(drawer.id(), "Grün", "", 0, new Glyph(null, "#15803d"));

        Glyph glyph = resolver.forItem(piece(drawer.id(), green.id()));

        assertEquals("radio", glyph.icon());
        assertEquals("#15803d", glyph.color());
    }

    @Test
    void nothingChosenFallsBackToTheKindOfShelf() {
        Inventory plainStock = stock(Glyph.NONE);
        Inventory plainDrawer = collection(Glyph.NONE);

        assertEquals("cube", resolver.forItem(piece(plainStock.id(), null)).icon());
        assertEquals("box", resolver.forItem(piece(plainDrawer.id(), null)).icon());
        assertNull(resolver.forItem(piece(plainStock.id(), null)).color());
    }

    @Test
    void aRowAboutNoPieceOrNoInventoryStillHasAShape() {
        assertEquals("cube", resolver.forItemId(null).icon());
        assertEquals("cube", resolver.forItemId(987654).icon());
        assertEquals("cube", resolver.forInventoryId(null).icon());
        assertEquals("cube", resolver.forInventoryId(987654).icon());
    }

    @Test
    void anInventoryAnswersForARowAboutAKindOfThing() {
        Inventory jackets = stock(new Glyph("shirt", "#b45309"));

        Glyph glyph = resolver.forInventoryId(jackets.id());

        assertEquals("shirt", glyph.icon());
        assertEquals("#b45309", glyph.color());
    }
}
