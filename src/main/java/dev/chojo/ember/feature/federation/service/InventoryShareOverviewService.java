/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.InventoryShare;
import dev.chojo.ember.feature.inventory.entity.Inventory;
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
        var inventories = new HashMap<Integer, Inventory>();
        for (var inventory : inventoryRepository.findByStation(stationId)) {
            inventories.put(inventory.id(), inventory);
        }
        var partnerNames = partnerNames(stationId);
        return shareService.findShares(stationId).stream()
                .map(share -> describe(share, inventories, partnerNames))
                .toList();
    }

    private ShareDetail describe(
            InventoryShare share, Map<Integer, Inventory> inventories, Map<Integer, String> partnerNames) {
        Inventory inventory = null;
        InventoryArt art = null;
        String itemName = null;
        String itemInternalId = null;
        Integer sharedItem = share.itemId();
        Integer sharedArt = share.artId();
        if (sharedItem != null) {
            var item = inventoryRepository.findItemById(sharedItem).orElse(null);
            if (item != null) {
                itemName = item.name();
                itemInternalId = item.internalId();
                inventory = inventories.get(item.inventoryId());
                Integer itemArt = item.artId();
                if (itemArt != null) art = artRepository.findById(itemArt).orElse(null);
            }
        } else if (sharedArt != null) {
            art = artRepository.findById(sharedArt).orElse(null);
            if (art != null) inventory = inventories.get(art.inventoryId());
        } else {
            inventory = inventories.get(share.inventoryId());
        }
        var targets = shareService.findTargets(share.id()).stream()
                .map(partnerId -> new SharePartner(partnerId, partnerNames.getOrDefault(partnerId, UNNAMED)))
                .toList();
        return new ShareDetail(
                share,
                inventory != null ? inventory.name() : null,
                art != null ? art.name() : null,
                itemName,
                itemInternalId,
                firstNamed(art != null ? art.pictureKey() : null, inventory != null ? inventory.pictureKey() : null),
                firstNamed(art != null ? art.icon() : null, inventory != null ? inventory.icon() : null),
                firstNamed(art != null ? art.color() : null, inventory != null ? inventory.color() : null),
                inventory == null || inventory.homogeneous(),
                targets);
    }

    private static @Nullable String firstNamed(@Nullable String first, @Nullable String second) {
        return first != null && !first.isBlank() ? first : second;
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

    /**
     * One row of the overview of everything this station offers.
     *
     * <p>The photo and the icon are the ones the shared thing is shown with: its kind's where the kind
     * has one, otherwise its inventory's. The photo is read by its key.
     *
     * @param share          the share row
     * @param inventoryName  the inventory the shared thing lies in
     * @param artName        the kind of the shared thing, where it has one
     * @param itemName       the shared piece, for a share of one piece
     * @param itemInternalId the piece's own number, for a share of one piece
     * @param pictureKey     the key of the photo the thing is shown with, or {@code null} for none
     * @param icon           the icon it is drawn with, or {@code null} for the plain shape
     * @param color          the colour of that icon, or {@code null}
     * @param homogeneous    whether the inventory holds one thing in many copies, which decides the plain
     *                       shape drawn where nobody chose an icon
     * @param partners       the partners the share is offered to
     */
    public record ShareDetail(
            InventoryShare share,
            @Nullable String inventoryName,
            @Nullable String artName,
            @Nullable String itemName,
            @Nullable String itemInternalId,
            @Nullable String pictureKey,
            @Nullable String icon,
            @Nullable String color,
            boolean homogeneous,
            List<SharePartner> partners) {}

    /** A partner named by a share, with the name to show for it. */
    public record SharePartner(int partnerId, String stationName) {}
}
