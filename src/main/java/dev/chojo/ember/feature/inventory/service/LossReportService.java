/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterItemLost;
import dev.chojo.ember.feature.cluster.entity.LossReportRequirement;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemMovementDocument;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ItemMovementDocumentRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Telling the body above the station that a piece of its gear is gone, and asking for another.
 *
 * <p>Three things are kept apart here and were one thing before. Marking gear lost is a fact about where it
 * is, recorded at the station and told to nobody. Reporting it is a request, and the only thing the owner
 * ever hears. What the owner does about it is a third act, and it is answered on the movement like any other
 * step: it may send a replacement or refuse one, and the loss stands either way.
 */
@Singleton
public class LossReportService {
    private static final Logger log = LoggerFactory.getLogger(LossReportService.class);

    private final InventoryRepository inventoryRepository;
    private final ItemMovementService movementService;
    private final ItemMovementDocumentRepository documentRepository;
    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;
    private final StorageService storage;
    private final DomainEventBus eventBus;

    @Inject
    public LossReportService(
            InventoryRepository inventoryRepository,
            ItemMovementService movementService,
            ItemMovementDocumentRepository documentRepository,
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            StorageService storage,
            DomainEventBus eventBus) {
        this.inventoryRepository = inventoryRepository;
        this.movementService = movementService;
        this.documentRepository = documentRepository;
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.storage = storage;
        this.eventBus = eventBus;
    }

    /**
     * What this item's owner wants to see before it will consider a replacement.
     *
     * @param itemId the item somebody is about to report
     * @return the owner's requirement, or empty when the item has no owner here to report to
     */
    public Optional<LossReportRequirement> requirementFor(int itemId) {
        return inventoryRepository
                .findItemById(itemId)
                .filter(item -> item.ownerKind() == ItemOwner.CLUSTER && item.ownerClusterId() != null)
                .flatMap(item -> clusterRepository.findById(item.ownerClusterId()))
                .map(cluster -> cluster.lossReportRequires());
    }

    /**
     * Reports a missing item to the body that owns it.
     *
     * @param stationId  the station raising the report
     * @param itemId     the item that is gone
     * @param note       what the reporting manager wrote, or {@code null}
     * @param document   the file they attached, or {@code null}
     * @param reportedBy who is raising it
     * @return the movement the report walks
     * @throws BadRequestResponse when the item is not missing, has no owner here, or the report is short of
     *                            what that owner asks for
     */
    public ItemMovement report(int stationId, int itemId, String note, Attachment document, int reportedBy) {
        InventoryItem item =
                inventoryRepository.findItemById(itemId).orElseThrow(() -> new BadRequestResponse("No such item"));
        if (item.custody() != ItemCustody.LOST) {
            throw new BadRequestResponse("This gear is not recorded as missing, so there is nothing to report");
        }
        if (item.ownerKind() != ItemOwner.CLUSTER || item.ownerClusterId() == null) {
            throw new BadRequestResponse("The station owns this gear itself, so there is nobody to report it to");
        }
        var cluster = clusterRepository
                .findById(item.ownerClusterId())
                .orElseThrow(() -> new BadRequestResponse("The body that owns this gear is not here to answer"));
        requireEnough(cluster.lossReportRequires(), note, document);

        ItemMovement movement = movementService.create(
                stationId,
                MovementPurpose.EXCHANGE,
                item.assignedTo(),
                null,
                itemId,
                item.inventoryId(),
                item.sizeId(),
                null,
                note != null ? note : "",
                new ItemMovementService.Actor(reportedBy, true, false),
                null,
                true);

        if (document != null) {
            attach(stationId, movement.id(), document, reportedBy);
        }
        log.info(
                "Station {} reported item {} missing to cluster {} as movement {}",
                stationId,
                itemId,
                cluster.id(),
                movement.id());
        eventBus.publish(new ClusterItemLost(cluster.id(), item.name(), stationId));
        return movement;
    }

    /**
     * Refuses a report that falls short of what the owner asks for, before anything is written down.
     */
    private void requireEnough(LossReportRequirement requires, String note, Attachment document) {
        if (requires == LossReportRequirement.NOTHING) return;
        if (note == null || note.isBlank()) {
            throw new BadRequestResponse("The body that owns this gear asks for a note with a loss report");
        }
        if (requires == LossReportRequirement.DOCUMENT && document == null) {
            throw new BadRequestResponse("The body that owns this gear asks for a document with a loss report");
        }
    }

    private void attach(int stationId, int movementId, Attachment document, int uploadedBy) {
        var stored = documentRepository.create(
                movementId, document.fileName(), document.mimeType(), document.data().length, uploadedBy);
        storage.store(
                scope(stationId),
                StorageCategory.MOVEMENT_DOCUMENTS,
                contentKey(stored.id()),
                document.data(),
                document.mimeType());
    }

    /**
     * The file attached to a movement, if one was.
     */
    public Optional<ItemMovementDocument> documentOf(int movementId) {
        return documentRepository.findByMovement(movementId);
    }

    /**
     * The bytes of an attached file, read from the station that raised the report.
     */
    public Optional<byte[]> read(int stationId, ItemMovementDocument document) {
        return storage.readAllBytes(
                scope(stationId), StorageCategory.MOVEMENT_DOCUMENTS, contentKey(document.id()), Variant.ORIGINAL);
    }

    private StorageScope.Station scope(int stationId) {
        return new StorageScope.Station(stationId, stationRepository.resolveUid(stationId));
    }

    private static String contentKey(int documentId) {
        return documentId + "/file";
    }

    /**
     * A file as it arrived, before anything has been written down about it.
     *
     * @param fileName what it was called
     * @param mimeType what it was uploaded as
     * @param data     its bytes
     */
    public record Attachment(String fileName, String mimeType, byte[] data) {}
}
