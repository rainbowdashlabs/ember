/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.federation.service.FederationPartnerTransferFixupService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where a station moving to another instance stands, as the station and the destination see it.
 */
@Singleton
public class StationTransferService {
    private static final Logger log = LoggerFactory.getLogger(StationTransferService.class);

    private final StationRepository stationRepository;
    private final StationExportService exportService;
    private final FederationPartnerTransferFixupService federationFixup;

    @Inject
    public StationTransferService(
            StationRepository stationRepository,
            StationExportService exportService,
            FederationPartnerTransferFixupService federationFixup) {
        this.stationRepository = stationRepository;
        this.exportService = exportService;
        this.federationFixup = federationFixup;
    }

    /**
     * Whether the station is being moved out, and where to.
     *
     * @param stationId the station
     * @return the state; the target is only named while the station is read-only for the move
     */
    public TransferStatusResponse status(int stationId) {
        boolean readOnly = stationRepository.isReadOnlyForTransfer(stationId);
        String target = readOnly ? exportService.findTransferTarget(stationId).orElse(null) : null;
        return new TransferStatusResponse(readOnly, target);
    }

    /**
     * Records that the destination imported every table, and points the federation partners the
     * station keeps at the instance it moved to.
     *
     * @param stationId      the station that moved
     * @param signalledFrom  the destination as it named itself, or null or blank to use the one
     *                       recorded when the move started
     */
    public void complete(int stationId, String signalledFrom) {
        String destinationUrl = signalledFrom != null && !signalledFrom.isBlank()
                ? signalledFrom
                : exportService.findTransferTarget(stationId).orElse(null);
        log.info(
                "destination signalled completion for station {} (destination url={})",
                stationId,
                destinationUrl == null ? "<unknown>" : destinationUrl);
        exportService.markTransferComplete(stationId);
        stationRepository
                .findById(stationId)
                .map(Station::uid)
                .ifPresent(uid -> federationFixup.flipSourceSideRetainedPartners(uid, destinationUrl));
    }

    /**
     * @param readOnly          whether the station is read-only because it is being moved out
     * @param targetInstanceUrl the instance it moves to, or null
     */
    public record TransferStatusResponse(
            boolean readOnly, @Nullable String targetInstanceUrl) {}
}
