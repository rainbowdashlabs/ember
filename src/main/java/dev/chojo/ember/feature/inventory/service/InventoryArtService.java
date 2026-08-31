/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.ArtStock;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemNameCount;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The kinds of thing an inventory holds, and the tidying up that puts pieces under them.
 *
 * <p>Nothing here groups anything by itself and nothing is seeded. One kind per distinct name would
 * carve every typo a station ever made into the model beside the word it meant, which is the exact
 * thing this level exists to let somebody clear up. So a kind appears because a person said so, and
 * a piece joins one because a person said so.
 */
@Singleton
public class InventoryArtService {
    private static final Logger log = LoggerFactory.getLogger(InventoryArtService.class);

    private final InventoryArtRepository artRepository;
    private final InventoryRepository inventoryRepository;

    @Inject
    public InventoryArtService(InventoryArtRepository artRepository, InventoryRepository inventoryRepository) {
        this.artRepository = artRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Every kind an inventory holds, in the order it shows them.
     *
     * <p>An inventory of one thing in many copies has none and always will, so this answers with an
     * empty list rather than refusing: reading is never the place to put the barrier.
     *
     * @param inventoryId the inventory
     * @return its kinds
     */
    public List<InventoryArt> findByInventory(int inventoryId) {
        return artRepository.findByInventory(inventoryId);
    }

    /**
     * Finds one kind by its identifier.
     *
     * @param id the identifier
     * @return the kind, or empty
     */
    public Optional<InventoryArt> findById(int id) {
        return artRepository.findById(id);
    }

    /**
     * The pieces of one kind.
     *
     * @param artId the kind
     * @return its pieces
     */
    public List<InventoryItem> findItems(int artId) {
        return inventoryRepository.findItemsOfArt(artId);
    }

    /**
     * How many pieces an inventory holds of each kind, and how many are free.
     *
     * @param inventoryId the inventory
     * @return one row per kind that has pieces
     */
    public List<ArtStock> stock(int inventoryId) {
        return artRepository.stockByInventory(inventoryId);
    }

    /**
     * How many pieces of one kind are free right now.
     *
     * @param artId the kind
     * @return the count
     */
    public int free(int artId) {
        return artRepository.freeOfArt(artId);
    }

    /**
     * The distinct names written on the pieces of an inventory, with counts. What the tidying screen
     * reads before anybody has decided anything.
     *
     * @param inventoryId the inventory
     * @return one row per distinct name
     */
    public List<ItemNameCount> nameCounts(int inventoryId) {
        return artRepository.nameCounts(inventoryId);
    }

    /**
     * The kinds of the same name elsewhere on this instance.
     *
     * <p>Two stations that write the same word mean the same kind, compared after trimming and
     * lowering, each keeping its own spelling on screen. That is what lets a partner's stock be
     * counted alongside this one's without anybody maintaining a shared list, and it is also the
     * price: two stations using one word for two things are conflated.
     *
     * @param artId the kind to match
     * @return every kind sharing its key, this one among them
     */
    public List<InventoryArt> sameAcrossStations(int artId) {
        InventoryArt art = artRepository.findById(artId).orElseThrow(NotFoundResponse::new);
        return artRepository.findByMergeKey(art.mergeKey());
    }

    /**
     * Writes down a new kind.
     *
     * @param inventoryId the inventory it belongs to
     * @param name        what the station calls it
     * @param note        a free note, may be empty or {@code null}
     * @param position    the sort position
     * @return the kind that was written
     * @throws BadRequestResponse when the inventory holds one thing in many copies, or when the name
     *                            is blank or already taken there
     */
    public InventoryArt create(int inventoryId, String name, String note, int position) {
        Inventory inventory = requireHeterogeneous(inventoryId);
        String trimmed = requireName(name);
        artRepository.findByName(inventoryId, trimmed).ifPresent(existing -> {
            throw new BadRequestResponse("This inventory already has a kind called %s".formatted(existing.name()));
        });
        InventoryArt art = artRepository.create(inventoryId, trimmed, note, position);
        log.info("Created kind {} (name='{}') in inventory {}", art.id(), trimmed, inventory.id());
        return art;
    }

    /**
     * Renames a kind or moves it in the list.
     *
     * <p>Nothing cascades. The pieces keep pointing at the same row and the key two stations compare
     * on is maintained by the database, so a corrected spelling is corrected everywhere at once.
     *
     * @param id       the kind
     * @param name     its new name
     * @param note     its new note
     * @param position its new sort position
     * @return the kind as it now stands, or empty when nothing changed
     */
    public Optional<InventoryArt> update(int id, String name, String note, int position) {
        InventoryArt before = artRepository.findById(id).orElseThrow(NotFoundResponse::new);
        String trimmed = requireName(name);
        artRepository.findByName(before.inventoryId(), trimmed).ifPresent(existing -> {
            if (existing.id() != id) {
                throw new BadRequestResponse("This inventory already has a kind called %s".formatted(existing.name()));
            }
        });
        if (!artRepository.update(id, trimmed, note, position)) {
            log.warn("Update of kind {} did not change any row", id);
            return Optional.empty();
        }
        log.info("Updated kind {} (name='{}')", id, trimmed);
        return artRepository.findById(id);
    }

    /**
     * Removes a kind.
     *
     * <p>Its pieces stay and lose their kind. The values they recorded for the kind's fields stay
     * too, unshown until a kind of that name comes back, because throwing them away is the one
     * choice that cannot be undone.
     *
     * @param id the kind
     * @return {@code true} when a row was removed
     */
    public boolean delete(int id) {
        boolean deleted = artRepository.delete(id);
        if (deleted) log.info("Deleted kind {}", id);
        else log.warn("Delete of kind {} did not change any row", id);
        return deleted;
    }

    /**
     * Puts pieces under a kind, leaving their names exactly as they are.
     *
     * <p>{@code Pager 01} to {@code Pager 16} are sixteen pieces of one kind and sixteen names worth
     * keeping. Passing no kind takes it away again.
     *
     * @param inventoryId the inventory the pieces are in
     * @param artId       the kind, or {@code null} to clear it
     * @param itemIds     the pieces
     * @return how many pieces changed
     */
    public int assign(int inventoryId, Integer artId, List<Integer> itemIds) {
        List<Integer> owned = requireItemsOfInventory(inventoryId, itemIds);
        if (artId != null) requireArtOfInventory(inventoryId, artId);
        int changed = artRepository.setArt(artId, owned);
        log.info("Put {} pieces of inventory {} under kind {}", changed, inventoryId, artId);
        return changed;
    }

    /**
     * Puts pieces under a kind and rewrites their names to it.
     *
     * <p>The rewrite is what makes tidying up worth doing rather than decorative. Setting a kind
     * leaves the name alone, and the name is what every list, both exports and the notification
     * texts read, so without this {@code Funkgerät organge} would go on reading
     * {@code Funkgerät organge} under a heading that says otherwise.
     *
     * <p>It is a destructive edit and is meant to be: it belongs to somebody choosing it on a screen
     * that says what it will do, and never to a migration.
     *
     * @param inventoryId the inventory the pieces are in
     * @param artId       the kind they all become
     * @param itemIds     the pieces
     * @return how many pieces changed
     */
    public int merge(int inventoryId, int artId, List<Integer> itemIds) {
        List<Integer> owned = requireItemsOfInventory(inventoryId, itemIds);
        InventoryArt art = requireArtOfInventory(inventoryId, artId);
        int changed = artRepository.mergeIntoArt(artId, owned, art.name());
        log.info("Merged {} pieces of inventory {} into kind {} and renamed them", changed, inventoryId, artId);
        return changed;
    }

    /**
     * The barrier the flag puts up: kinds live only in a drawer of different things.
     *
     * <p>An inventory of one thing in many copies is structured by its sizes, and letting it carry
     * kinds as well would make "every member needs a Bundhose" quietly mean "any kind of Bundhose"
     * without anybody having chosen that.
     */
    private Inventory requireHeterogeneous(int inventoryId) {
        Inventory inventory = inventoryRepository
                .findById(inventoryId)
                .orElseThrow(() -> new NotFoundResponse("This inventory does not exist"));
        if (inventory.homogeneous()) {
            throw new BadRequestResponse(
                    "Kinds exist only in an inventory that holds a drawer of different things, and this one holds one thing in many copies");
        }
        return inventory;
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw new BadRequestResponse("A kind needs a name");
        return trimmed;
    }

    private InventoryArt requireArtOfInventory(int inventoryId, int artId) {
        InventoryArt art = artRepository.findById(artId).orElseThrow(NotFoundResponse::new);
        if (art.inventoryId() != inventoryId) {
            throw new BadRequestResponse("That kind belongs to another inventory");
        }
        return art;
    }

    private List<Integer> requireItemsOfInventory(int inventoryId, List<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return List.of();
        List<Integer> distinct = itemIds.stream().distinct().toList();
        for (Integer itemId : distinct) {
            InventoryItem item = inventoryRepository.findItemById(itemId).orElseThrow(NotFoundResponse::new);
            if (item.inventoryId() != inventoryId) {
                throw new BadRequestResponse("That piece belongs to another inventory");
            }
        }
        return distinct;
    }
}
