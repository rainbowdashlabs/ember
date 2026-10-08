/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.InventoryShare;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a station offers its lending partners, each row named the way the overview shows it:
 * the inventory, kind and item it covers and the partners it reaches.
 */
@Singleton
public class InventoryShareOverviewService {
    private static final String UNNAMED = "?";

    private final InventoryShareService shareService;
    private final FederationService federationService;
    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;
    private final StationRepository stationRepository;

    @Inject
    public InventoryShareOverviewService(
            InventoryShareService shareService,
            FederationService federationService,
            InventoryRepository inventoryRepository,
            InventoryArtRepository artRepository,
            StationRepository stationRepository) {
        this.shareService = shareService;
        this.federationService = federationService;
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
        this.stationRepository = stationRepository;
    }

    /**
     * Every share row of a station, described.
     *
     * @param stationId the offering station
     * @return one entry per share row
     */
    public List<ShareDetail> overview(int stationId) {
        var inventoryNames = new HashMap<Integer, String>();
        for (var inventory : inventoryRepository.findByStation(stationId)) {
            inventoryNames.put(inventory.id(), inventory.name());
        }
        var partnerNames = partnerNames(stationId);
        return shareService.findShares(stationId).stream()
                .map(share -> describe(share, inventoryNames, partnerNames))
                .toList();
    }

    private ShareDetail describe(
            InventoryShare share, Map<Integer, String> inventoryNames, Map<Integer, String> partnerNames) {
        String inventoryName = null;
        String artName = null;
        String itemName = null;
        String itemInternalId = null;
        Integer sharedItem = share.itemId();
        Integer sharedArt = share.artId();
        if (sharedItem != null) {
            var item = inventoryRepository.findItemById(sharedItem).orElse(null);
            if (item != null) {
                itemName = item.name();
                itemInternalId = item.internalId();
                inventoryName = inventoryNames.get(item.inventoryId());
                Integer itemArt = item.artId();
                if (itemArt != null) {
                    artName = artRepository
                            .findById(itemArt)
                            .map(InventoryArt::name)
                            .orElse(null);
                }
            }
        } else if (sharedArt != null) {
            var art = artRepository.findById(sharedArt).orElse(null);
            if (art != null) {
                artName = art.name();
                inventoryName = inventoryNames.get(art.inventoryId());
            }
        } else {
            inventoryName = inventoryNames.get(share.inventoryId());
        }
        var targets = shareService.findTargets(share.id()).stream()
                .map(partnerId -> new SharePartner(partnerId, partnerNames.getOrDefault(partnerId, UNNAMED)))
                .toList();
        return new ShareDetail(share, inventoryName, artName, itemName, itemInternalId, targets);
    }

    private Map<Integer, String> partnerNames(int stationId) {
        var names = new HashMap<Integer, String>();
        for (var partner : federationService.findPartners(stationId)) {
            String name = stationRepository
                    .findHereByUid(partner.partnerStationId())
                    .map(Station::name)
                    .orElse(partner.partnerStationName());
            names.put(partner.id(), name != null ? name : UNNAMED);
        }
        return names;
    }

    /** One row of the overview of everything this station offers. */
    public record ShareDetail(
            InventoryShare share,
            @Nullable String inventoryName,
            @Nullable String artName,
            @Nullable String itemName,
            @Nullable String itemInternalId,
            List<SharePartner> partners) {}

    /** A partner named by a share, with the name to show for it. */
    public record SharePartner(int partnerId, String stationName) {}
}
