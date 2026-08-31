/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.CollectionLine;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryCollection;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ResolvedCollection;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryCollectionServiceTest extends RepositoryTestBase {

    private static InventoryCollectionService service;
    private static Account account;
    private static Station station;
    private static Station other;
    private static StationMember member;
    private static Inventory drawer;
    private static Inventory foreign;

    @BeforeAll
    static void setup() {
        service = new InventoryCollectionService(collectionRepo, inventoryRepo, artRepo);
        account = accountRepo.create("collectionsvc@test.example", "Collection", "Service");
        station = stationRepo.create("CollectionServiceStation");
        other = stationRepo.create("CollectionServiceOther");
        member = stationMemberRepo.create(station.id(), account.id());
        drawer = inventoryRepo.create(station.id(), "Sonstiges", InventoryType.INTERNAL, false, false);
        foreign = inventoryRepo.create(other.id(), "Fremd", InventoryType.INTERNAL, false, false);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(other.id());
        accountRepo.delete(account.id());
    }

    private static InventoryItem item(Inventory inventory, String name) {
        return inventoryRepo.createItem(inventory.id(), null, name, null, InventoryItemMetadata.empty());
    }

    @Test
    void createTrimsAndInsistsOnAName() {
        InventoryCollection created = service.create(station.id(), "  Jugendabend  ", "  Spiele  ", member.id());
        assertEquals("Jugendabend", created.name());
        assertEquals("Spiele", created.note());

        InventoryCollection withoutNote = service.create(station.id(), "Ohne Notiz", null, null);
        assertEquals("", withoutNote.note());
        assertNull(withoutNote.createdBy());

        assertThrows(IllegalArgumentException.class, () -> service.create(station.id(), "   ", "", null));
        assertThrows(IllegalArgumentException.class, () -> service.create(station.id(), null, "", null));

        assertTrue(service.update(created.id(), " Spieleabend ", null));
        InventoryCollection renamed = service.findById(created.id()).orElseThrow();
        assertEquals("Spieleabend", renamed.name());
        assertEquals("", renamed.note());
        assertThrows(IllegalArgumentException.class, () -> service.update(created.id(), "", "x"));

        assertTrue(service.findByStation(station.id()).stream()
                .anyMatch(summary -> summary.collection().name().equals("Spieleabend")));

        assertTrue(service.delete(created.id(), station.id()));
        assertTrue(service.delete(withoutNote.id(), station.id()));
        assertFalse(service.delete(created.id(), station.id()));
    }

    @Test
    void aLineOnlyEverNamesTheStationsOwnGear() {
        InventoryCollection kit = service.create(station.id(), "Eigenes", "", null);
        InventoryItem own = item(drawer, "Laminator");
        InventoryItem elsewhere = item(foreign, "Fremdes Stueck");

        CollectionLine named = service.addItemLine(kit.id(), station.id(), own.id());
        assertEquals(own.id(), named.itemId());
        assertEquals(1, named.quantity());

        assertThrows(IllegalArgumentException.class, () -> service.addItemLine(kit.id(), station.id(), elsewhere.id()));
        assertThrows(IllegalArgumentException.class, () -> service.addItemLine(kit.id(), station.id(), -1));
        assertThrows(IllegalArgumentException.class, () -> service.addItemLine(kit.id(), station.id(), own.id()));

        CollectionLine counted = service.addInventoryLine(kit.id(), station.id(), drawer.id(), 3);
        assertEquals(drawer.id(), counted.inventoryId());
        assertEquals(3, counted.quantity());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.addInventoryLine(kit.id(), station.id(), foreign.id(), 1));
        assertThrows(IllegalArgumentException.class, () -> service.addInventoryLine(kit.id(), station.id(), -1, 1));
        assertThrows(
                IllegalArgumentException.class, () -> service.addInventoryLine(kit.id(), station.id(), drawer.id(), 0));

        InventoryArt kind = artRepo.create(drawer.id(), "Laminiergeraet", "", 0);
        InventoryArt foreignKind = artRepo.create(foreign.id(), "Fremde Art", "", 0);

        CollectionLine perKind = service.addArtLine(kit.id(), station.id(), kind.id(), 4);
        assertEquals(kind.id(), perKind.artId());
        assertEquals(4, perKind.quantity());
        assertNull(perKind.itemId());
        assertNull(perKind.inventoryId());

        assertThrows(
                IllegalArgumentException.class, () -> service.addArtLine(kit.id(), station.id(), foreignKind.id(), 1));
        assertThrows(IllegalArgumentException.class, () -> service.addArtLine(kit.id(), station.id(), -1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.addArtLine(kit.id(), station.id(), kind.id(), 0));

        assertEquals(
                List.of("Eigenes"),
                service.collectionsAskingForArt(kind.id()).stream()
                        .map(InventoryCollection::name)
                        .toList());

        service.delete(kit.id(), station.id());
        artRepo.delete(kind.id());
        artRepo.delete(foreignKind.id());
        inventoryRepo.deleteItem(own.id());
        inventoryRepo.deleteItem(elsewhere.id());
    }

    @Test
    void aNamedLineNeverCarriesACount() {
        InventoryCollection kit = service.create(station.id(), "Anzahl", "", null);
        InventoryItem piece = item(drawer, "Beamer");
        CollectionLine named = service.addItemLine(kit.id(), station.id(), piece.id());
        CollectionLine counted = service.addInventoryLine(kit.id(), station.id(), drawer.id(), 2);

        assertThrows(IllegalArgumentException.class, () -> service.updateLineQuantity(named.id(), 4));
        assertThrows(IllegalArgumentException.class, () -> service.updateLineQuantity(counted.id(), 0));
        assertThrows(IllegalArgumentException.class, () -> service.updateLineQuantity(-1, 2));
        assertTrue(service.updateLineQuantity(counted.id(), 5));

        service.reorderLines(kit.id(), List.of(counted.id(), named.id()));
        assertEquals(
                List.of(counted.id(), named.id()),
                service.findLines(kit.id()).stream().map(CollectionLine::id).toList());

        assertTrue(service.deleteLine(named.id()));
        assertFalse(service.deleteLine(named.id()));

        service.delete(kit.id(), station.id());
        inventoryRepo.deleteItem(piece.id());
    }

    @Test
    void resolvingOverAWindowAndOverNone() {
        Inventory box = inventoryRepo.create(station.id(), "Fenster", InventoryType.INTERNAL, false);
        InventoryCollection kit = service.create(station.id(), "Fenster", "", null);
        InventoryItem here = item(box, "Da");
        InventoryItem gone = item(box, "Weg");
        inventoryRepo.updateCustody(gone.id(), ItemCustody.LOST, station.id(), null, null);

        service.addItemLine(kit.id(), station.id(), here.id());
        service.addItemLine(kit.id(), station.id(), gone.id());
        service.addInventoryLine(kit.id(), station.id(), box.id(), 2);

        ResolvedCollection undated = service.resolve(kit.id(), station.id(), null, null);
        assertNull(undated.dateFrom());
        assertNull(undated.dateTo());
        assertFalse(undated.complete());
        assertFalse(undated.holdsClusterOwned());
        assertEquals(1, undated.lines().get(0).available());
        assertEquals(0, undated.lines().get(1).available());
        assertEquals(1, undated.lines().get(2).available());
        assertEquals(1, undated.lines().get(2).missing());

        ResolvedCollection oneDay = service.resolve(kit.id(), station.id(), LocalDate.of(2026, 3, 1), null);
        assertEquals(LocalDate.of(2026, 3, 1), oneDay.dateFrom());
        assertEquals(LocalDate.of(2026, 3, 1), oneDay.dateTo());

        ResolvedCollection openStart = service.resolve(kit.id(), station.id(), null, LocalDate.of(2026, 3, 5));
        assertNull(openStart.dateFrom());
        assertNull(openStart.dateTo());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(kit.id(), station.id(), LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(-1, station.id(), null, null));

        service.delete(kit.id(), station.id());
        inventoryRepo.delete(box.id());
    }

    @Test
    void theWarningNamesWhatStandsToLoseALine() {
        Inventory box = inventoryRepo.create(station.id(), "Warnung", InventoryType.INTERNAL, false);
        InventoryItem piece = item(box, "Playmobil");
        InventoryCollection kit = service.create(station.id(), "Warnung", "", null);
        service.addItemLine(kit.id(), station.id(), piece.id());

        assertEquals(
                List.of("Warnung"),
                service.collectionsHoldingItem(piece.id()).stream()
                        .map(InventoryCollection::name)
                        .toList());
        assertEquals(
                List.of("Warnung"),
                service.collectionsTouchingInventory(box.id()).stream()
                        .map(InventoryCollection::name)
                        .toList());

        service.delete(kit.id(), station.id());
        inventoryRepo.delete(box.id());
    }
}
