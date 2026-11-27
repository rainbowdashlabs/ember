/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The photos of inventories and of their kinds.
 *
 * <p>A piece shows its kind's photo where the kind has one, otherwise its inventory's. Every upload is
 * kept under a new key that its row then names, and the photo it replaces is removed afterwards, so a
 * cached old photo is never served under the new key and a failed upload leaves the old one in place.
 *
 * <p>Every call takes the station and refuses an inventory, kind or piece of another station as gone,
 * so the answer does not say that it exists there.
 *
 * <p>The files are written a moment before their row names them. A storage reconciliation that reads
 * the named keys within that moment takes the new files for orphans and removes them; the row then
 * names a photo that is gone and the page shows none until the next upload. That window is a few
 * milliseconds against a reconciliation that runs rarely, so it is accepted rather than locked
 * against. Two uploads at once leave the loser's files behind, which the reconciliation removes.
 */
@Singleton
public class InventoryPictureService {
    private static final Logger log = LoggerFactory.getLogger(InventoryPictureService.class);
    private static final ImageProfile PROFILE = ImageProfile.CONTENT;
    private static final StorageCategory CATEGORY = StorageCategory.IMAGE_INVENTORY;

    private final ImageVariants images;
    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;
    private final StationRepository stationRepository;

    @Inject
    public InventoryPictureService(
            ImageVariants images,
            InventoryRepository inventoryRepository,
            InventoryArtRepository artRepository,
            StationRepository stationRepository) {
        this.images = images;
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
        this.stationRepository = stationRepository;
    }

    /**
     * Keeps a new photo for an inventory, replacing the one it had.
     *
     * @param stationId   the station the inventory has to belong to
     * @param inventoryId the inventory
     * @param data        the picture as sent
     * @param maxBytes    the most the picture may weigh
     * @return the key the photo is now kept under
     * @throws IOException when the picture cannot be read or written
     */
    public String storeForInventory(int stationId, int inventoryId, byte[] data, int maxBytes) throws IOException {
        Inventory inventory = requireInventory(stationId, inventoryId);
        String key = storeNew(stationId, data, maxBytes);
        inventoryRepository.setPictureKey(inventoryId, key);
        removeFiles(stationId, inventory.pictureKey());
        log.info("Stored photo of inventory {} at station {} ({} bytes)", inventoryId, stationId, data.length);
        return key;
    }

    /**
     * Keeps a new photo for a kind, replacing the one it had.
     *
     * @param stationId   the station the inventory has to belong to
     * @param inventoryId the inventory the kind is in
     * @param artId       the kind
     * @param data        the picture as sent
     * @param maxBytes    the most the picture may weigh
     * @return the key the photo is now kept under
     * @throws IOException when the picture cannot be read or written
     */
    public String storeForArt(int stationId, int inventoryId, int artId, byte[] data, int maxBytes) throws IOException {
        InventoryArt art = requireArt(stationId, inventoryId, artId);
        String key = storeNew(stationId, data, maxBytes);
        artRepository.setPictureKey(artId, key);
        removeFiles(stationId, art.pictureKey());
        log.info("Stored photo of kind {} at station {} ({} bytes)", artId, stationId, data.length);
        return key;
    }

    /**
     * Takes an inventory's photo away. An inventory without one is left as it is.
     *
     * @param stationId   the station the inventory has to belong to
     * @param inventoryId the inventory
     */
    public void removeFromInventory(int stationId, int inventoryId) {
        Inventory inventory = requireInventory(stationId, inventoryId);
        if (inventory.pictureKey() == null) return;
        inventoryRepository.setPictureKey(inventoryId, null);
        removeFiles(stationId, inventory.pictureKey());
        log.info("Removed photo of inventory {} at station {}", inventoryId, stationId);
    }

    /**
     * Takes a kind's photo away. A kind without one is left as it is.
     *
     * @param stationId   the station the inventory has to belong to
     * @param inventoryId the inventory the kind is in
     * @param artId       the kind
     */
    public void removeFromArt(int stationId, int inventoryId, int artId) {
        InventoryArt art = requireArt(stationId, inventoryId, artId);
        if (art.pictureKey() == null) return;
        artRepository.setPictureKey(artId, null);
        removeFiles(stationId, art.pictureKey());
        log.info("Removed photo of kind {} at station {}", artId, stationId);
    }

    /**
     * An inventory's photo at the size asked for.
     *
     * @param stationId   the station the inventory has to belong to
     * @param inventoryId the inventory
     * @param size        the side wanted, {@code 0} for the kept original
     * @return the photo
     */
    public MediaContent readForInventory(int stationId, int inventoryId, int size) {
        return read(stationId, requireInventory(stationId, inventoryId).pictureKey(), size);
    }

    /**
     * A kind's photo at the size asked for.
     *
     * @param stationId   the station the inventory has to belong to
     * @param inventoryId the inventory the kind is in
     * @param artId       the kind
     * @param size        the side wanted, {@code 0} for the kept original
     * @return the photo
     */
    public MediaContent readForArt(int stationId, int inventoryId, int artId, int size) {
        return read(stationId, requireArt(stationId, inventoryId, artId).pictureKey(), size);
    }

    /**
     * The photo a piece is shown with: its kind's where the kind has one, otherwise its inventory's.
     *
     * @param stationId the station the piece has to belong to
     * @param itemId    the piece
     * @param size      the side wanted, {@code 0} for the kept original
     * @return the photo
     */
    public MediaContent readForItem(int stationId, int itemId, int size) {
        return read(stationId, pictureKeyOfItem(stationId, itemId), size);
    }

    /**
     * The key of the photo a piece is shown with, by the same order as {@link #readForItem}.
     *
     * @param stationId the station the piece has to belong to
     * @param itemId    the piece
     * @return the key, or {@code null} where neither the kind nor the inventory has a photo
     */
    public @Nullable String pictureKeyOfItem(int stationId, int itemId) {
        InventoryItem item = inventoryRepository
                .findItemById(itemId)
                .orElseThrow(InventoryRefusal.INVENTORY_PICTURE_ITEM_NOT_HERE::raise);
        Inventory inventory = inventoryRepository
                .findById(item.inventoryId())
                .filter(found -> found.stationId() == stationId)
                .orElseThrow(InventoryRefusal.INVENTORY_PICTURE_ITEM_NOT_HERE::raise);
        String artKey = item.artId() == null
                ? null
                : artRepository
                        .findById(item.artId())
                        .map(InventoryArt::pictureKey)
                        .orElse(null);
        return artKey != null ? artKey : inventory.pictureKey();
    }

    /**
     * The keys of every photo an inventory holds, its own and those of its kinds, read before the
     * inventory is deleted so their files can follow it.
     *
     * @param inventoryId the inventory
     * @return the keys, empty where nothing has a photo
     */
    public List<String> keysOfInventory(int inventoryId) {
        var keys = new ArrayList<String>();
        inventoryRepository.findById(inventoryId).map(Inventory::pictureKey).ifPresent(keys::add);
        artRepository.findByInventory(inventoryId).stream()
                .map(InventoryArt::pictureKey)
                .filter(Objects::nonNull)
                .forEach(keys::add);
        return keys;
    }

    /**
     * The key of a kind's photo, read before the kind is deleted so its files can follow it.
     *
     * @param artId the kind
     * @return the key, or nothing where the kind has no photo
     */
    public List<String> keysOfArt(int artId) {
        return artRepository.findById(artId).map(InventoryArt::pictureKey).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Removes the files of photos whose rows are gone, such as an inventory or a kind just deleted.
     *
     * @param stationId   the station the photos are kept for
     * @param pictureKeys the keys the rows named
     */
    public void removeFiles(int stationId, Collection<String> pictureKeys) {
        pictureKeys.forEach(key -> removeFiles(stationId, key));
    }

    private void removeFiles(int stationId, @Nullable String pictureKey) {
        if (pictureKey == null) return;
        images.delete(scope(stationId), CATEGORY, pictureKey);
    }

    private String storeNew(int stationId, byte[] data, int maxBytes) throws IOException {
        String key = UUID.randomUUID().toString();
        images.store(PROFILE, scope(stationId), CATEGORY, key, data, maxBytes);
        return key;
    }

    private MediaContent read(int stationId, @Nullable String pictureKey, int size) {
        if (pictureKey == null) throw InventoryRefusal.INVENTORY_PICTURE_NOT_HERE.raise();
        Optional<MediaContent> content = images.read(PROFILE, scope(stationId), CATEGORY, pictureKey, size);
        return content.orElseThrow(InventoryRefusal.INVENTORY_PICTURE_NOT_HERE::raise);
    }

    private Inventory requireInventory(int stationId, int inventoryId) {
        return inventoryRepository
                .findById(inventoryId)
                .filter(inventory -> inventory.stationId() == stationId)
                .orElseThrow(InventoryRefusal.INVENTORY_PICTURE_INVENTORY_NOT_HERE::raise);
    }

    private InventoryArt requireArt(int stationId, int inventoryId, int artId) {
        requireInventory(stationId, inventoryId);
        return artRepository
                .findById(artId)
                .filter(art -> art.inventoryId() == inventoryId)
                .orElseThrow(InventoryRefusal.INVENTORY_PICTURE_KIND_NOT_HERE::raise);
    }

    private StorageScope.Station scope(int stationId) {
        UUID uid = stationRepository.requireUid(stationId);
        return new StorageScope.Station(stationId, uid);
    }
}
