/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryTag;
import dev.chojo.ember.feature.inventory.entity.TaggedItemSummary;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryTagRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The words a station puts on its things, and which thing wears which.
 *
 * <p>A tag is picked from what exists and never typed onto a single piece. That is the whole point:
 * a word typed per piece drifts into a second spelling of itself within a season, and then nothing
 * finds both. Writing a tag down is therefore an act of its own, and the pickers that offer one ask
 * for it by name so a word that is already there is used rather than repeated.
 *
 * <p>Names are matched trimmed and without regard to case, here and in the database, so a station
 * cannot end up holding two rows for one word.
 */
@Singleton
public class InventoryTagService {
    private static final Logger log = LoggerFactory.getLogger(InventoryTagService.class);

    private final InventoryTagRepository tagRepository;
    private final InventoryRepository inventoryRepository;

    @Inject
    public InventoryTagService(InventoryTagRepository tagRepository, InventoryRepository inventoryRepository) {
        this.tagRepository = tagRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Every tag a station has.
     *
     * @param stationId the station
     * @return its tags, in the order it put them in
     */
    public List<InventoryTag> findByStation(int stationId) {
        return tagRepository.findByStation(stationId);
    }

    /**
     * Finds one tag by its identifier.
     *
     * @param id the identifier
     * @return the tag, or empty
     */
    public Optional<InventoryTag> findById(int id) {
        return tagRepository.findById(id);
    }

    /**
     * How many things carry each of the station's tags.
     *
     * @param stationId the station
     * @return tag id to count, holding only the tags something wears
     */
    public Map<Integer, Integer> countItemsPerTag(int stationId) {
        return tagRepository.countItemsPerTag(stationId);
    }

    /**
     * Writes a tag down, or hands back the one that already carries this word.
     *
     * <p>Answering with the existing row rather than refusing is what lets a picker offer to make a
     * word without having to know whether somebody else made it a moment ago.
     *
     * @param stationId the station
     * @param name      the word
     * @param color     optional hex colour for the badge
     * @return the tag, new or found
     */
    public InventoryTag create(int stationId, String name, String color) {
        String wanted = requireName(name);
        var existing = tagRepository.findByName(stationId, wanted);
        if (existing.isPresent()) return existing.get();
        var tag = tagRepository.create(stationId, wanted, color);
        log.info("Item tag {} created at station {}: '{}'", tag.id(), stationId, tag.name());
        return tag;
    }

    /**
     * Renames a tag and changes its colour and place.
     *
     * @param stationId the station the tag has to belong to
     * @param id        the tag
     * @param name      the new word
     * @param color     the new colour
     * @param position  where it should sit
     * @return the tag as it now stands
     */
    public InventoryTag update(int stationId, int id, String name, String color, int position) {
        var tag = requireOwnTag(stationId, id);
        String wanted = requireName(name);
        var clash = tagRepository.findByName(stationId, wanted);
        if (clash.isPresent() && clash.get().id() != tag.id()) {
            throw new BadRequestResponse("The station already has a tag of that name");
        }
        tagRepository.update(id, wanted, color, position);
        return tagRepository.findById(id).orElseThrow(NotFoundResponse::new);
    }

    /**
     * Removes a tag. The things that wore it keep everything else they have.
     *
     * @param stationId the station the tag has to belong to
     * @param id        the tag
     */
    public void delete(int stationId, int id) {
        requireOwnTag(stationId, id);
        tagRepository.delete(id, stationId);
        log.info("Item tag {} deleted at station {}", id, stationId);
    }

    /**
     * The tags one thing wears.
     *
     * @param stationId the station the thing has to belong to
     * @param itemId    the thing
     * @return its tags
     */
    public List<InventoryTag> findTagsForItem(int stationId, int itemId) {
        requireOwnItem(stationId, itemId);
        return tagRepository.findTagsForItem(itemId);
    }

    /**
     * The tags a whole list of things wears, for a table that shows a column of them.
     *
     * @param itemIds the things
     * @return item id to its tags, holding only the things that wear one
     */
    public Map<Integer, List<InventoryTag>> findTagsForItems(Collection<Integer> itemIds) {
        return tagRepository.findTagsForItems(itemIds);
    }

    /**
     * The tags every thing in one inventory wears, so a table can show a column of them and filter
     * on it without asking once per row.
     *
     * @param stationId   the station the inventory has to belong to
     * @param inventoryId the inventory
     * @return item id to its tags, holding only the things that wear one
     */
    public Map<Integer, List<InventoryTag>> findTagsInInventory(int stationId, int inventoryId) {
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != stationId) throw new NotFoundResponse();
        return tagRepository.findTagsForItems(inventoryRepository.findItems(inventoryId).stream()
                .map(InventoryItem::id)
                .toList());
    }

    /**
     * Says which words a thing wears, writing down any that the station does not have yet.
     *
     * <p>The form speaks in words rather than identifiers because that is what somebody typed, and
     * because it is the only shape in which a word made up on the spot and a word picked from the
     * list are the same thing. Nothing is written down until the form is saved, so an abandoned
     * form leaves no tag behind.
     *
     * @param stationId the station the thing has to belong to
     * @param itemId    the thing
     * @param names     the words it should wear, blanks and repeats ignored
     * @return the tags it now wears
     */
    public List<InventoryTag> setItemTags(int stationId, int itemId, List<String> names) {
        requireOwnItem(stationId, itemId);
        var ids = new ArrayList<Integer>();
        for (String name : names == null ? List.<String>of() : names) {
            if (name == null || name.isBlank()) continue;
            var tag = create(stationId, name, null);
            if (!ids.contains(tag.id())) ids.add(tag.id());
        }
        tagRepository.setItemTags(itemId, stationId, ids);
        return tagRepository.findTagsForItem(itemId);
    }

    /**
     * The things carrying a word, across every inventory of the stations named.
     *
     * @param stationIds the stations to look in
     * @param name       the word as somebody typed it
     * @return what was found
     */
    public List<TaggedItemSummary> findItemsByTag(Collection<Integer> stationIds, String name) {
        if (name == null || name.isBlank()) return List.of();
        return tagRepository.findItemsByTag(stationIds, name);
    }

    /**
     * The things a station serves to one partner for a word, which is only what it has offered.
     *
     * @param stationId the station serving the request
     * @param partnerId the partnership the request arrived on
     * @param name      the word the asking station used
     * @return what may be shown
     */
    public List<TaggedItemSummary> findSharedItemsByTag(int stationId, int partnerId, String name) {
        if (name == null || name.isBlank()) return List.of();
        return tagRepository.findSharedItemsByTag(stationId, partnerId, name);
    }

    private static String requireName(String name) {
        String wanted = name == null ? "" : name.strip();
        if (wanted.isEmpty()) throw new BadRequestResponse("A tag needs a name");
        return wanted;
    }

    private InventoryTag requireOwnTag(int stationId, int id) {
        var tag = tagRepository.findById(id).orElseThrow(NotFoundResponse::new);
        if (tag.stationId() != stationId) throw new NotFoundResponse();
        return tag;
    }

    private void requireOwnItem(int stationId, int itemId) {
        InventoryItem item = inventoryRepository.findItemById(itemId).orElseThrow(NotFoundResponse::new);
        Inventory inventory = inventoryRepository.findById(item.inventoryId()).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != stationId) throw new NotFoundResponse();
    }
}
