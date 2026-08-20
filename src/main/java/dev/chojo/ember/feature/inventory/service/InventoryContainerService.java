/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.ContainerHistoryDetails;
import dev.chojo.ember.feature.inventory.entity.ContainerPath;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryContainer;
import dev.chojo.ember.feature.inventory.entity.InventoryContainerHistory;
import dev.chojo.ember.feature.inventory.entity.InventoryContainerKind;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.repository.InventoryContainerKindRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryContainerRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Service for managing storage containers and the lifecycle ledger that
 * tracks their creation, renames, moves and deletions. Also owns:
 *
 * <ul>
 *   <li>Default-kind seeding on first container creation in a station.</li>
 *   <li>Cycle prevention when re-parenting a container.</li>
 *   <li>Internal-id uniqueness across containers and items in the same station.</li>
 *   <li>Path lookups for the per-item "Location" display.</li>
 * </ul>
 */
@Singleton
public class InventoryContainerService {
    private static final Logger log = LoggerFactory.getLogger(InventoryContainerService.class);

    /**
     * The seven default kinds seeded into every station on first container use.
     * Mirrors the catalogue described in the storage concept doc.
     */
    public static final List<DefaultKind> DEFAULT_KINDS = List.of(
            new DefaultKind("station_building", "Gerätehaus", "house", 10),
            new DefaultKind("vehicle_bay", "Fahrzeughalle", "warehouse", 20),
            new DefaultKind("equipment_room", "Geräteraum", "building", 30),
            new DefaultKind("workshop", "Werkstatt", "gears", 40),
            new DefaultKind("shelf", "Regal", "layer-group", 50),
            new DefaultKind("drawer", "Schublade", "inbox", 60),
            new DefaultKind("gear_box", "Materialkiste", "box", 70),
            new DefaultKind("operation_case", "Einsatzkoffer", "suitcase", 80),
            new DefaultKind("other", "Sonstiges", "circle-question", 90));

    private final InventoryContainerRepository containerRepository;
    private final InventoryContainerKindRepository kindRepository;
    private final InventoryRepository inventoryRepository;
    private final ItemCustodyService custodyService;

    @Inject
    public InventoryContainerService(
            InventoryContainerRepository containerRepository,
            InventoryContainerKindRepository kindRepository,
            InventoryRepository inventoryRepository,
            ItemCustodyService custodyService) {
        this.containerRepository = containerRepository;
        this.kindRepository = kindRepository;
        this.inventoryRepository = inventoryRepository;
        this.custodyService = custodyService;
    }

    /**
     * Ensures every default kind exists for the given station, returning the
     * complete catalogue. Re-running is a no-op for kinds that already exist.
     *
     * @param stationId the station ID
     * @return all kinds defined for the station after seeding
     */
    public List<InventoryContainerKind> seedDefaultKinds(int stationId) {
        for (DefaultKind defaults : DEFAULT_KINDS) {
            if (kindRepository.findByKey(stationId, defaults.key()).isEmpty()) {
                kindRepository.create(
                        stationId, defaults.key(), defaults.label(), defaults.icon(), defaults.sortOrder(), true);
            }
        }
        return kindRepository.findByStation(stationId);
    }

    /**
     * Returns the kind catalogue for a station, seeding defaults if the
     * station has none yet.
     */
    public List<InventoryContainerKind> listKinds(int stationId) {
        if (!kindRepository.stationHasAnyKind(stationId)) {
            return seedDefaultKinds(stationId);
        }
        return kindRepository.findByStation(stationId);
    }

    /**
     * Adds a new kind to a station's catalogue. Throws if the key already
     * exists for the station.
     */
    public InventoryContainerKind createKind(
            int stationId, String key, String label, String icon, int sortOrder, boolean enabled) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Kind key is required");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Kind label is required");
        }
        if (kindRepository.findByKey(stationId, key).isPresent()) {
            throw new IllegalArgumentException("Kind key already exists for this station");
        }
        String resolvedIcon = (icon == null || icon.isBlank()) ? "box" : icon;
        InventoryContainerKind created = kindRepository.create(stationId, key, label, resolvedIcon, sortOrder, enabled);
        log.info("Created container kind {} (key='{}', label='{}') in station {}", created.id(), key, label, stationId);
        return created;
    }

    /**
     * Updates an existing kind's mutable fields.
     */
    public Optional<InventoryContainerKind> updateKind(
            int id, String label, String icon, int sortOrder, boolean enabled) {
        Optional<InventoryContainerKind> existing = kindRepository.findById(id);
        if (existing.isEmpty()) return Optional.empty();
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Kind label is required");
        }
        String resolvedIcon = (icon == null || icon.isBlank()) ? "box" : icon;
        if (!kindRepository.update(id, label, resolvedIcon, sortOrder, enabled)) {
            return Optional.empty();
        }
        log.info("Updated container kind {} (label='{}', enabled={})", id, label, enabled);
        return kindRepository.findById(id);
    }

    /**
     * Deletes a kind from the catalogue. Containers currently pointing at it
     * have their {@code kind_id} cleared via {@code ON DELETE SET NULL}.
     */
    public boolean deleteKind(int id) {
        boolean deleted = kindRepository.delete(id);
        if (deleted) log.info("Deleted container kind {}", id);
        else log.warn("Delete of container kind {} did not change any row", id);
        return deleted;
    }

    /**
     * Finds a single container by id.
     */
    public Optional<InventoryContainer> findById(int id) {
        return containerRepository.findById(id);
    }

    /**
     * Returns every container for a station.
     */
    public List<InventoryContainer> findByStation(int stationId) {
        return containerRepository.findByStation(stationId);
    }

    /**
     * Returns all root containers for a station.
     */
    public List<InventoryContainer> findRoots(int stationId) {
        return containerRepository.findRoots(stationId);
    }

    /**
     * Returns the direct children of a container.
     */
    public List<InventoryContainer> findChildren(int parentId) {
        return containerRepository.findChildren(parentId);
    }

    /**
     * Returns every container in the subtree rooted at the given container,
     * walked depth-first with siblings in display-name order.
     */
    public List<InventoryContainer> findSubtree(int containerId) {
        return containerRepository.findSubtree(containerId);
    }

    /**
     * Resolves a scan against the container internal-id namespace within a station.
     */
    public Optional<InventoryContainer> resolveScan(int stationId, String internalId) {
        if (internalId == null || internalId.isBlank()) return Optional.empty();
        return containerRepository.findByInternalId(stationId, internalId);
    }

    /**
     * Returns the user-facing root-first path leading to a container.
     */
    public ContainerPath pathOf(int containerId) {
        return containerRepository.findPath(containerId);
    }

    /**
     * Returns the path of the container that holds an item, or
     * {@link ContainerPath#empty()} if the item is unlocated.
     */
    public ContainerPath pathOfItem(InventoryItem item) {
        if (item == null || item.containerId() == null) return ContainerPath.empty();
        return containerRepository.findPath(item.containerId());
    }

    /**
     * Direct items in a container, ordered by name.
     */
    public List<InventoryItem> findItemsInContainer(int containerId) {
        return containerRepository.findItemsInContainer(containerId);
    }

    /**
     * Items reachable from a container, including descendants, ordered for
     * depth-first display.
     */
    public List<InventoryItem> findItemsInSubtree(int containerId) {
        return containerRepository.findItemsInSubtree(containerId);
    }

    /**
     * Lifecycle history for a single container.
     */
    public List<InventoryContainerHistory> findHistory(int containerId) {
        return containerRepository.findHistory(containerId);
    }

    /**
     * Recent lifecycle history across the whole station.
     */
    public List<InventoryContainerHistory> findRecentHistory(int stationId, int limit) {
        return containerRepository.findRecentHistory(stationId, Math.max(1, limit));
    }

    /**
     * Creates a new container, recording a {@code CREATED} history event.
     * Throws {@link IllegalArgumentException} if the internal id is already
     * used by another container or item in the station, if {@code parentId}
     * refers to a container in a different station, or if the name is blank.
     */
    public InventoryContainer create(
            int stationId,
            Integer parentId,
            String internalId,
            String name,
            Integer kindId,
            String description,
            Integer createdBy) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Container name is required");
        }
        if (name.contains(ContainerPath.SEPARATOR.trim())) {
            throw new IllegalArgumentException("Container name may not contain '/'");
        }
        verifyParent(stationId, parentId);
        verifyInternalId(stationId, internalId, null, null);
        if (!kindRepository.stationHasAnyKind(stationId)) {
            seedDefaultKinds(stationId);
        }
        InventoryContainer created =
                containerRepository.create(stationId, parentId, internalId, name, kindId, description, createdBy);
        containerRepository.appendHistory(
                created.id(), stationId, createdBy, new ContainerHistoryDetails.Created(name, parentId));
        log.info(
                "Created container {} (name='{}', parentId={}, kindId={}) in station {}",
                created.id(),
                name,
                parentId,
                kindId,
                stationId);
        return created;
    }

    /**
     * Updates a container's mutable fields, recording {@code RENAMED} or
     * {@code MOVED} history rows whenever the name or parent changes.
     * Throws {@link IllegalArgumentException} on cycle attempts, on
     * cross-station moves, or on internal-id collisions.
     */
    public Optional<InventoryContainer> update(
            int id,
            Integer parentId,
            String internalId,
            String name,
            Integer kindId,
            String description,
            Integer actorId) {
        Optional<InventoryContainer> existingOpt = containerRepository.findById(id);
        if (existingOpt.isEmpty()) return Optional.empty();
        InventoryContainer existing = existingOpt.get();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Container name is required");
        }
        if (name.contains(ContainerPath.SEPARATOR.trim())) {
            throw new IllegalArgumentException("Container name may not contain '/'");
        }
        verifyParent(existing.stationId(), parentId);
        if (parentId != null && wouldCreateCycle(id, parentId)) {
            throw new IllegalArgumentException("Container cannot be moved under one of its descendants");
        }
        verifyInternalId(existing.stationId(), internalId, id, null);
        boolean updated = containerRepository.update(id, parentId, internalId, name, kindId, description);
        if (!updated) return Optional.empty();
        if (!existing.name().equals(name)) {
            containerRepository.appendHistory(
                    id, existing.stationId(), actorId, new ContainerHistoryDetails.Renamed(existing.name(), name));
        }
        boolean parentChanged = !Objects.equals(existing.parentId(), parentId);
        if (parentChanged) {
            ContainerHistoryDetails moved = new ContainerHistoryDetails.Moved(existing.parentId(), parentId);
            containerRepository.appendHistory(id, existing.stationId(), actorId, moved);
        }
        log.info(
                "Updated container {} (name='{}', parentId={}, renamed={}, moved={})",
                id,
                name,
                parentId,
                !existing.name().equals(name),
                parentChanged);
        return containerRepository.findById(id);
    }

    /**
     * Deletes a container. Children are promoted to roots and items become
     * unlocated via the schema's {@code ON DELETE SET NULL} hooks. A
     * {@code DELETED} history row is appended with {@code container_id = NULL}.
     */
    public boolean delete(int id, Integer actorId) {
        Optional<InventoryContainer> existingOpt = containerRepository.findById(id);
        if (existingOpt.isEmpty()) {
            log.warn("Delete skipped: container {} not found", id);
            return false;
        }
        InventoryContainer existing = existingOpt.get();
        boolean deleted = containerRepository.delete(id);
        if (deleted) {
            containerRepository.appendHistory(
                    null, existing.stationId(), actorId, new ContainerHistoryDetails.Deleted(id, existing.name()));
            log.info("Deleted container {} (name='{}', station={})", id, existing.name(), existing.stationId());
        } else {
            log.warn("Delete of container {} did not change any row", id);
        }
        return deleted;
    }

    /**
     * Places an item into a container, or clears its location with a
     * {@code null} container id. Validates that the item and the container
     * belong to the same station. A container says where in the store an item
     * is rather than who has it, so this leaves the custody alone unless a
     * member was holding the item, in which case putting it on a shelf is
     * handing it back at the same time.
     */
    public boolean setItemContainer(int itemId, Integer containerId) {
        Optional<InventoryItem> itemOpt = inventoryRepository.findItemById(itemId);
        if (itemOpt.isEmpty()) {
            log.warn("setItemContainer skipped: item {} not found", itemId);
            return false;
        }
        InventoryItem item = itemOpt.get();
        if (containerId != null) {
            Optional<InventoryContainer> targetOpt = containerRepository.findById(containerId);
            if (targetOpt.isEmpty()) {
                log.warn("setItemContainer skipped: container {} not found", containerId);
                return false;
            }
            int itemStationId = inventoryRepository
                    .findById(item.inventoryId())
                    .map(Inventory::stationId)
                    .orElseThrow(() -> new IllegalArgumentException("Item has no inventory"));
            if (itemStationId != targetOpt.get().stationId()) {
                throw new IllegalArgumentException("Item and container belong to different stations");
            }
        }
        boolean updated = custodyService.placeInContainer(itemId, containerId).isPresent();
        if (updated) log.info("Moved item {} to container {}", itemId, containerId);
        else log.warn("setItemContainer of item {} to container {} did not change any row", itemId, containerId);
        return updated;
    }

    /**
     * Returns whether {@code candidateParentId} is the moved container or one
     * of its descendants - moving under it would create a cycle.
     */
    boolean wouldCreateCycle(int movedContainerId, int candidateParentId) {
        if (movedContainerId == candidateParentId) return true;
        Set<Integer> descendants = new HashSet<>();
        for (InventoryContainer descendant : containerRepository.findSubtree(movedContainerId)) {
            descendants.add(descendant.id());
        }
        return descendants.contains(candidateParentId);
    }

    private void verifyParent(int stationId, Integer parentId) {
        if (parentId == null) return;
        InventoryContainer parent = containerRepository
                .findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent container not found"));
        if (parent.stationId() != stationId) {
            throw new IllegalArgumentException("Parent container belongs to a different station");
        }
    }

    private void verifyInternalId(int stationId, String internalId, Integer excludeContainerId, Integer excludeItemId) {
        if (internalId == null || internalId.isBlank()) return;
        if (containerRepository.internalIdExists(stationId, internalId, excludeContainerId)) {
            throw new IllegalArgumentException("Internal id already used by another container in this station");
        }
        if (inventoryRepository.itemInternalIdExists(stationId, internalId, excludeItemId)) {
            throw new IllegalArgumentException("Internal id already used by an inventory item in this station");
        }
    }

    /**
     * Default kind seed entry.
     */
    public record DefaultKind(String key, String label, String icon, int sortOrder) {}
}
