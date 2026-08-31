/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemFieldValues;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.SwitchBlockerKind;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The kind of thing a piece is: a level between the inventory and the piece.
 *
 * <p>The cases worth holding on to are the ones the concept turns on. A piece with no kind, which is
 * most of them and stays that way. A kind refused in an inventory of one thing in many copies, and a
 * switch back refused while kinds are there. The merge over {@code blau}, {@code Blau} and
 * {@code  blau }, which is one word however it was typed. And a piece whose kind is taken away
 * keeping the values it recorded, because losing them is the one thing that cannot be undone.
 */
class InventoryArtServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static InventoryService service;
    private static Station station;

    @BeforeAll
    static void setup() {
        service = new InventoryService(
                inventoryRepo,
                artRepo,
                fieldDefinitionService,
                itemCustodyService,
                clusterRepo,
                clusterStationGroupRepo);
        station = stationRepo.create("ArtStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static Inventory drawer() {
        return inventoryRepo.create(
                station.id(), "Drawer " + NAMES.incrementAndGet(), InventoryType.INTERNAL, false, false);
    }

    private static Inventory oneThing() {
        return inventoryRepo.create(
                station.id(), "Uniform " + NAMES.incrementAndGet(), InventoryType.INTERNAL, false, true);
    }

    private static InventoryItem piece(int inventoryId, String name, Integer artId) {
        return inventoryRepo.createItem(inventoryId, null, name, null, artId, null, ItemOwner.STATION, null);
    }

    @Test
    void kindsExistOnlyInADrawerOfDifferentThings() {
        Inventory uniform = oneThing();
        var refused = assertThrows(BadRequestResponse.class, () -> artService.create(uniform.id(), "blau", "", 0));
        assertTrue(refused.getMessage().contains("drawer"));

        Inventory drawer = drawer();
        InventoryArt art = artService.create(drawer.id(), "Funkgerät blau", "", 0);
        assertNotNull(art);
        assertEquals("funkgerät blau", art.mergeKey());
    }

    @Test
    void aPieceMayHaveNoKindAndEveryReadCopes() {
        Inventory drawer = drawer();
        InventoryItem loose = piece(drawer.id(), "Laminiergerät", null);
        assertNull(loose.artId());
        assertNull(inventoryRepo.findItemById(loose.id()).orElseThrow().artId());
        // A count over kinds leaves it out rather than inventing a group for it.
        assertTrue(artService.stock(drawer.id()).isEmpty());
        assertEquals(1, artService.nameCounts(drawer.id()).size());
        assertEquals(1, artService.nameCounts(drawer.id()).getFirst().unassigned());
        assertTrue(fieldDefinitionService.resolveForItem(loose).isEmpty());
    }

    @Test
    void oneWordHoweverItWasTyped() {
        Inventory drawer = drawer();
        artService.create(drawer.id(), "blau", "", 0);
        assertThrows(BadRequestResponse.class, () -> artService.create(drawer.id(), "Blau", "", 0));
        assertThrows(BadRequestResponse.class, () -> artService.create(drawer.id(), "  blau  ", "", 0));

        // And the same word in another station's drawer is the same kind, which is what lets a
        // partner's stock be counted alongside this one's.
        Inventory otherDrawer = drawer();
        InventoryArt elsewhere = artService.create(otherDrawer.id(), " BLAU ", "", 0);
        assertEquals("blau", elsewhere.mergeKey());
        assertTrue(artService.sameAcrossStations(elsewhere.id()).size() >= 2);
    }

    @Test
    void kindsBlockTheWayBackAndNothingElseDoes() {
        Inventory drawer = drawer();
        artService.create(drawer.id(), "Funkgerät orange", "", 0);
        var blockers =
                service.blockersForSwitch(inventoryRepo.findById(drawer.id()).orElseThrow(), true);
        assertEquals(1, blockers.size());
        assertEquals(SwitchBlockerKind.ART, blockers.getFirst().kind());
        assertEquals("Funkgerät orange", blockers.getFirst().label());

        assertThrows(
                InventorySwitchRefusedException.class,
                () -> service.update(drawer.id(), drawer.name(), InventoryType.INTERNAL, false, true));

        Inventory empty = drawer();
        assertTrue(service.blockersForSwitch(inventoryRepo.findById(empty.id()).orElseThrow(), true)
                .isEmpty());
    }

    @Test
    void mergingRewritesTheNamesAndAssigningDoesNot() {
        Inventory drawer = drawer();
        InventoryArt orange = artService.create(drawer.id(), "Funkgerät orange", "", 0);
        InventoryItem right = piece(drawer.id(), "Funkgerät orange", null);
        InventoryItem typo = piece(drawer.id(), "Funkgerät organge", null);

        assertEquals(2, artService.merge(drawer.id(), orange.id(), List.of(right.id(), typo.id())));
        assertEquals(
                "Funkgerät orange",
                inventoryRepo.findItemById(typo.id()).orElseThrow().name());

        InventoryArt pager = artService.create(drawer.id(), "Pager", "", 10);
        InventoryItem one = piece(drawer.id(), "Pager 01", null);
        assertEquals(1, artService.assign(drawer.id(), pager.id(), List.of(one.id())));
        assertEquals(
                "Pager 01", inventoryRepo.findItemById(one.id()).orElseThrow().name());
        assertEquals(
                pager.id(), inventoryRepo.findItemById(one.id()).orElseThrow().artId());

        assertEquals(2, artService.stock(drawer.id()).size());
        assertEquals(2, artService.findItems(orange.id()).size());
    }

    @Test
    void aKindFromAnotherDrawerIsRefused() {
        Inventory drawer = drawer();
        Inventory otherDrawer = drawer();
        InventoryArt elsewhere = artService.create(otherDrawer.id(), "Beamer", "", 0);
        assertThrows(
                BadRequestResponse.class,
                () -> service.createItem(
                        drawer.id(), null, "Beamer", null, elsewhere.id(), null, ItemOwner.STATION, null));
        InventoryItem here = piece(drawer.id(), "Beamer", null);
        assertThrows(
                BadRequestResponse.class, () -> artService.assign(drawer.id(), elsewhere.id(), List.of(here.id())));
    }

    @Test
    void theNarrowestDefinitionWins() {
        Inventory drawer = drawer();
        InventoryArt blau = artService.create(drawer.id(), "Funkgerät blau", "", 0);
        InventoryItem radio = piece(drawer.id(), "Funkgerät blau", blau.id());

        fieldDefinitionService.create(drawer.id(), "note", "Notiz des Inventars", FieldType.TEXT, false, 0, null);
        fieldDefinitionService.create(
                drawer.id(), blau.id(), null, "note", "Notiz der Art", FieldType.TEXT, false, 0, null);
        assertEquals(
                "Notiz der Art",
                fieldDefinitionService.resolveForItem(radio).getFirst().label());

        fieldDefinitionService.create(
                drawer.id(), null, radio.id(), "note", "Notiz des Stücks", FieldType.TEXT, false, 0, null);
        var resolved = fieldDefinitionService.resolveForItem(radio);
        assertEquals(1, resolved.size());
        assertEquals("Notiz des Stücks", resolved.getFirst().label());
    }

    @Test
    void takingTheKindAwayKeepsTheValues() {
        Inventory drawer = drawer();
        InventoryArt blau = artService.create(drawer.id(), "Funkgerät blau", "", 0);
        fieldDefinitionService.create(
                drawer.id(), blau.id(), null, "call_sign", "Rufname", FieldType.TEXT, false, 0, null);
        InventoryItem radio = piece(drawer.id(), "Funkgerät blau", blau.id());

        Map<String, ItemFieldValues.FieldValue> values = new LinkedHashMap<>();
        values.put("call_sign", new ItemFieldValues.TextValue("Florian 1"));
        service.updateItem(
                radio.id(),
                null,
                "Funkgerät blau",
                null,
                blau.id(),
                new InventoryItemMetadata(new ItemFieldValues(values)),
                null);
        assertEquals(
                "Florian 1", textValue(inventoryRepo.findItemById(radio.id()).orElseThrow(), "call_sign"));

        // The kind goes, and the form comes back with nothing in it because it showed nothing.
        service.updateItem(radio.id(), null, "Funkgerät blau", null, null, InventoryItemMetadata.empty(), null);
        InventoryItem stripped = inventoryRepo.findItemById(radio.id()).orElseThrow();
        assertNull(stripped.artId());
        assertTrue(fieldDefinitionService.resolveForItem(stripped).isEmpty());
        assertEquals("Florian 1", textValue(stripped, "call_sign"));

        // And it reads again the day the kind comes back, which the tidying screen does without
        // touching what the piece recorded.
        artService.assign(drawer.id(), blau.id(), List.of(radio.id()));
        InventoryItem restored = inventoryRepo.findItemById(radio.id()).orElseThrow();
        assertEquals(1, fieldDefinitionService.resolveForItem(restored).size());
        assertEquals("Florian 1", textValue(restored, "call_sign"));
    }

    @Test
    void movingAPieceLeavesItsKindBehind() {
        Inventory drawer = drawer();
        Inventory otherDrawer = drawer();
        InventoryArt blau = artService.create(drawer.id(), "Funkgerät blau", "", 0);
        InventoryItem radio = piece(drawer.id(), "Funkgerät blau", blau.id());
        service.moveItem(radio.id(), otherDrawer.id(), null);
        assertNull(inventoryRepo.findItemById(radio.id()).orElseThrow().artId());
    }

    @Test
    void removingAKindLeavesItsPiecesWhereTheyAre() {
        Inventory drawer = drawer();
        InventoryArt spiele = artService.create(drawer.id(), "Spiele", "", 0);
        InventoryItem game = piece(drawer.id(), "Mensch ärgere dich nicht", spiele.id());
        assertTrue(artService.delete(spiele.id()));
        InventoryItem after = inventoryRepo.findItemById(game.id()).orElseThrow();
        assertNull(after.artId());
        assertEquals("Mensch ärgere dich nicht", after.name());
    }

    @Test
    void renamingAKindCascadesNowhere() {
        Inventory drawer = drawer();
        InventoryArt typo = artService.create(drawer.id(), "Funkgerät organge", "", 0);
        InventoryItem radio = piece(drawer.id(), "Funkgerät organge", typo.id());
        InventoryArt fixed =
                artService.update(typo.id(), "Funkgerät orange", "", 0).orElseThrow();
        assertEquals("funkgerät orange", fixed.mergeKey());
        assertEquals(
                typo.id(), inventoryRepo.findItemById(radio.id()).orElseThrow().artId());
    }

    @Test
    void aKindNeedsANameAndCannotTakeAnother() {
        Inventory drawer = drawer();
        assertThrows(BadRequestResponse.class, () -> artService.create(drawer.id(), "  ", "", 0));
        assertThrows(BadRequestResponse.class, () -> artService.create(drawer.id(), null, "", 0));

        InventoryArt blau = artService.create(drawer.id(), "blau", "", 0);
        InventoryArt gruen = artService.create(drawer.id(), "grün", "", 1);
        assertThrows(BadRequestResponse.class, () -> artService.update(gruen.id(), " BLAU ", "", 1));
        assertThrows(BadRequestResponse.class, () -> artService.update(gruen.id(), "", "", 1));
        // Keeping its own name is not taking another one.
        assertTrue(artService.update(blau.id(), "blau", "eine Notiz", 5).isPresent());
        assertEquals("eine Notiz", artService.findById(blau.id()).orElseThrow().note());
    }

    @Test
    void whatIsNotThereChangesNothing() {
        assertTrue(artService.findById(987654).isEmpty());
        assertThrows(NotFoundResponse.class, () -> artService.update(987654, "blau", "", 0));
        assertThrows(NotFoundResponse.class, () -> artService.sameAcrossStations(987654));
        assertFalse(artService.delete(987654));

        Inventory drawer = drawer();
        assertEquals(0, artService.assign(drawer.id(), null, List.of()));
        assertEquals(0, artService.assign(drawer.id(), null, null));
    }

    @Test
    void takingTheKindOffAPieceIsATidyingActionToo() {
        Inventory drawer = drawer();
        InventoryArt blau = artService.create(drawer.id(), "Funkgerät blau", "", 0);
        InventoryItem radio = piece(drawer.id(), "Funkgerät blau", blau.id());
        assertEquals(1, artService.free(blau.id()));
        assertEquals(1, artService.assign(drawer.id(), null, List.of(radio.id())));
        assertNull(inventoryRepo.findItemById(radio.id()).orElseThrow().artId());
        assertEquals(0, artService.free(blau.id()));
    }

    @Test
    void aPieceFromAnotherDrawerCannotBeTidiedHere() {
        Inventory drawer = drawer();
        Inventory otherDrawer = drawer();
        InventoryArt blau = artService.create(drawer.id(), "Funkgerät blau", "", 0);
        InventoryItem elsewhere = piece(otherDrawer.id(), "Funkgerät blau", null);
        assertThrows(BadRequestResponse.class, () -> artService.merge(drawer.id(), blau.id(), List.of(elsewhere.id())));
        assertThrows(NotFoundResponse.class, () -> artService.assign(drawer.id(), null, List.of(987654)));
    }

    private static String textValue(InventoryItem item, String key) {
        ItemFieldValues.FieldValue value = item.metadata().fields().values().get(key);
        return value instanceof ItemFieldValues.TextValue text ? text.value() : null;
    }
}
