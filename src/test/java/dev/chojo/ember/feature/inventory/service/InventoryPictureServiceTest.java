/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.media.MediaLayoutFixtures;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The photos of inventories and kinds: kept under a new key each time, the old files gone, a piece
 * shown with its kind's photo before its inventory's, and another station's inventory refused as gone.
 */
class InventoryPictureServiceTest extends RepositoryTestBase {
    @TempDir
    Path root;

    private Station station;
    private Station elsewhere;
    private int inventoryId;
    private InventoryPictureService pictures;

    @BeforeEach
    void setUp() {
        station = stationRepo.create("PictureStation");
        elsewhere = stationRepo.create("PictureElsewhere");
        inventoryId = inventoryRepo
                .create(station.id(), "Festmaterial", InventoryType.INTERNAL, false)
                .id();
        var backend = new LocalStorageBackend(root);
        pictures = new InventoryPictureService(
                new ImageVariants(new StorageService(new StorageBackendResolver(backend), backend)),
                inventoryRepo,
                artRepo,
                stationRepo);
    }

    @AfterEach
    void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
    }

    private Path filesOf(String key) {
        return root.resolve("station/" + stationRepo.requireUid(station.id()) + "/images/inventory/" + key);
    }

    @Test
    void aNewPhotoGetsANewKeyAndTheOldFilesGo() throws IOException {
        byte[] png = MediaLayoutFixtures.samplePng();

        String first = pictures.storeForInventory(station.id(), inventoryId, png, 0);
        String second = pictures.storeForInventory(station.id(), inventoryId, png, 0);

        assertNotEquals(first, second);
        assertEquals(second, inventoryRepo.findById(inventoryId).orElseThrow().pictureKey());
        assertFalse(Files.exists(filesOf(first)));
        assertTrue(Files.exists(filesOf(second)));
        assertEquals(
                "image/png",
                pictures.readForInventory(station.id(), inventoryId, 0).contentType());

        pictures.removeFromInventory(station.id(), inventoryId);

        assertNull(inventoryRepo.findById(inventoryId).orElseThrow().pictureKey());
        assertFalse(Files.exists(filesOf(second)));
        var none = assertThrows(RefusalResponse.class, () -> pictures.readForInventory(station.id(), inventoryId, 0));
        assertEquals(InventoryRefusal.INVENTORY_PICTURE_NOT_HERE, none.refusal());
    }

    @Test
    void aPieceShowsItsKindsPhotoBeforeItsInventorys() throws IOException {
        byte[] png = MediaLayoutFixtures.samplePng();
        var bench = artRepo.create(inventoryId, "Bank", "", 0);
        var table = artRepo.create(inventoryId, "Tisch", "", 1);
        var onBench = inventoryRepo.createItem(inventoryId, "P-1", "Bierbank", null, null);
        var onTable = inventoryRepo.createItem(inventoryId, "P-2", "Biertisch", null, null);
        artRepo.setArt(bench.id(), List.of(onBench.id()));
        artRepo.setArt(table.id(), List.of(onTable.id()));

        var none = assertThrows(RefusalResponse.class, () -> pictures.readForItem(station.id(), onBench.id(), 0));
        assertEquals(InventoryRefusal.INVENTORY_PICTURE_NOT_HERE, none.refusal());

        String inventoryKey = pictures.storeForInventory(station.id(), inventoryId, png, 0);
        String benchKey = pictures.storeForArt(station.id(), inventoryId, bench.id(), png, 0);

        assertEquals(benchKey, pictures.pictureKeyOfItem(station.id(), onBench.id()));
        assertEquals(inventoryKey, pictures.pictureKeyOfItem(station.id(), onTable.id()));
        assertArrayEquals(
                pictures.readForArt(station.id(), inventoryId, bench.id(), 64).data(),
                pictures.readForItem(station.id(), onBench.id(), 64).data());

        assertEquals(List.of(inventoryKey, benchKey), pictures.keysOfInventory(inventoryId));
        assertEquals(List.of(benchKey), pictures.keysOfArt(bench.id()));
        assertEquals(List.of(), pictures.keysOfArt(table.id()));

        pictures.removeFiles(station.id(), pictures.keysOfInventory(inventoryId));
        assertFalse(Files.exists(filesOf(inventoryKey)));
        assertFalse(Files.exists(filesOf(benchKey)));
    }

    @Test
    void anotherStationsInventoryOrKindIsRefusedAsGone() throws IOException {
        byte[] png = MediaLayoutFixtures.samplePng();
        var strangerInventory = inventoryRepo
                .create(elsewhere.id(), "Fremdes", InventoryType.INTERNAL, false)
                .id();
        var strangerArt = artRepo.create(strangerInventory, "Fremd", "", 0);
        var strangerItem = inventoryRepo.createItem(strangerInventory, "P-9", "Fremdgerät", null, null);

        var inventory = assertThrows(
                RefusalResponse.class, () -> pictures.storeForInventory(station.id(), strangerInventory, png, 0));
        assertEquals(InventoryRefusal.INVENTORY_PICTURE_INVENTORY_NOT_HERE, inventory.refusal());

        var art = assertThrows(
                RefusalResponse.class, () -> pictures.storeForArt(station.id(), inventoryId, strangerArt.id(), png, 0));
        assertEquals(InventoryRefusal.INVENTORY_PICTURE_KIND_NOT_HERE, art.refusal());

        var item = assertThrows(RefusalResponse.class, () -> pictures.readForItem(station.id(), strangerItem.id(), 0));
        assertEquals(InventoryRefusal.INVENTORY_PICTURE_ITEM_NOT_HERE, item.refusal());
        assertNull(inventoryRepo.findById(strangerInventory).orElseThrow().pictureKey());
    }
}
