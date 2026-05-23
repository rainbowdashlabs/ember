/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.InventoryBlock;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for cross-station inventory lending.
 */
@Singleton
public class LendingService {
    private static final Logger log = LoggerFactory.getLogger(LendingService.class);

    private final LendingRepository repository;
    private final FederationHttpClient httpClient;
    private final FederationService federationService;
    private final Demo demo;

    @Inject
    public LendingService(
            LendingRepository repository,
            FederationHttpClient httpClient,
            FederationService federationService,
            Demo demo) {
        this.repository = repository;
        this.httpClient = httpClient;
        this.federationService = federationService;
        this.demo = demo;
    }

    // -- Requests --

    public LendingRequest createRequest(
            int requestingStationId, int owningStationId, LocalDate dateFrom, LocalDate dateTo, int createdBy) {
        return repository.createRequest(requestingStationId, owningStationId, dateFrom, dateTo, createdBy);
    }

    public Optional<LendingRequest> findRequest(int id) {
        return repository.findRequestById(id);
    }

    public List<LendingRequest> findRequestsByStation(int stationId) {
        return repository.findRequestsByStation(stationId);
    }

    public LendingRequestItem addRequestItem(int requestId, Integer inventoryId, Integer itemId, int quantity) {
        return repository.addRequestItem(requestId, inventoryId, itemId, quantity);
    }

    public List<LendingRequestItem> findRequestItems(int requestId) {
        return repository.findItemsByRequest(requestId);
    }

    public boolean assignItem(int requestItemId, int assignedItemId) {
        return repository.assignItem(requestItemId, assignedItemId);
    }

    // -- Status transitions --

    public boolean approveRequest(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.APPROVED);
        if (updated) {
            repository.createMessage(requestId, stationId, null, "Anfrage genehmigt", true);
        }
        return updated;
    }

    public boolean declineRequest(int requestId, int stationId, String reason) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.DECLINED);
        if (updated) {
            String msg = "Anfrage abgelehnt" + (reason != null && !reason.isBlank() ? ": " + reason : "");
            repository.createMessage(requestId, stationId, null, msg, true);
        }
        return updated;
    }

    public boolean markLent(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.LENT);
        if (updated) {
            repository.createMessage(requestId, stationId, null, "Ausrüstung ausgeliehen", true);
        }
        return updated;
    }

    public boolean markReturned(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.RETURNED);
        if (updated) {
            repository.createMessage(requestId, stationId, null, "Ausrüstung zurückgegeben", true);
        }
        return updated;
    }

    public boolean closeRequest(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.CLOSED);
        if (updated) {
            repository.createMessage(requestId, stationId, null, "Anfrage geschlossen", true);
        }
        return updated;
    }

    // -- Messages --

    public LendingMessage sendMessage(int requestId, int senderStationId, int senderMemberId, String message) {
        return repository.createMessage(requestId, senderStationId, senderMemberId, message, false);
    }

    /**
     * Returns only messages sent by the given station for a lending request.
     * Used for distributed message retrieval and the remote federation endpoint.
     */
    public List<LendingMessage> getLocalMessages(int requestId, int stationId) {
        return repository.findLocalMessages(requestId, stationId);
    }

    /**
     * Returns all messages for a lending request by merging local and remote messages.
     * Each station only stores messages it sent. The partner's messages are fetched either
     * via direct repository call (same-instance) or HTTP (cross-instance / dev force-HTTP mode).
     *
     * @param requestId      the lending request ID
     * @param localStationId the station requesting the messages
     * @return merged and time-sorted list of all messages
     */
    public List<LendingMessage> getMessages(int requestId, int localStationId) {
        var request = repository.findRequestById(requestId).orElseThrow();
        int partnerStationId = request.requestingStationId() == localStationId
                ? request.owningStationId()
                : request.requestingStationId();

        var localMessages = repository.findLocalMessages(requestId, localStationId);

        List<LendingMessage> remoteMessages;
        if (demo.federationForceHttp()) {
            remoteMessages = fetchRemoteMessagesViaHttp(requestId, localStationId, partnerStationId);
        } else {
            // Same-instance: directly query partner's messages from local DB
            remoteMessages = repository.findLocalMessages(requestId, partnerStationId);
        }

        var all = new ArrayList<>(localMessages);
        all.addAll(remoteMessages);
        all.sort(Comparator.comparing(LendingMessage::createdAt));
        return all;
    }

    private List<LendingMessage> fetchRemoteMessagesViaHttp(int requestId, int localStationId, int partnerStationId) {
        // Find the federation partner to get the private key for signing
        var partners = federationService.findPartners(localStationId);
        for (var partner : partners) {
            if (partner.partnerStationId() == partnerStationId
                    && partner.status() == FederationPartner.FederationStatus.ACTIVE) {
                return httpClient.fetchRemoteMessages(requestId, localStationId, partner.publicKey());
            }
        }
        log.warn(
                "No active federation partner found for station {} -> {}, falling back to local query",
                localStationId,
                partnerStationId);
        return repository.findLocalMessages(requestId, partnerStationId);
    }

    /**
     * @deprecated Use {@link #getMessages(int, int)} for distributed message retrieval.
     */
    @Deprecated
    public List<LendingMessage> getMessages(int requestId) {
        return repository.findMessagesByRequest(requestId);
    }

    // -- Blocks --

    public InventoryBlock createBlock(
            int stationId, Integer inventoryId, Integer itemId, LocalDate from, LocalDate to, String reason) {
        return repository.createBlock(stationId, inventoryId, itemId, from, to, reason);
    }

    public boolean deleteBlock(int blockId) {
        return repository.deleteBlock(blockId);
    }

    public List<InventoryBlock> findBlocks(int stationId) {
        return repository.findBlocksByStation(stationId);
    }

    public boolean isBlocked(int stationId, Integer inventoryId, Integer itemId, LocalDate dateFrom, LocalDate dateTo) {
        return repository.isBlocked(stationId, inventoryId, itemId, dateFrom, dateTo);
    }
}
