/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.inventory.entity.TaggedItemSummary;
import dev.chojo.ember.feature.inventory.repository.InventoryTagRepository;
import dev.chojo.ember.feature.inventory.route.RemoteInventoryTagRoutes;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Finding things by a word beyond the station that wrote it down.
 *
 * <p>Two stations that use the same word mean the same thing, which is what makes a search worth
 * running past the station's own shelves. What travels is the smallest thing that answers the
 * question, and only what the holding station has actually offered: a word is not a way around the
 * decision about who may see what.
 */
@Singleton
public class FederatedItemTagService {
    private static final Logger log = LoggerFactory.getLogger(FederatedItemTagService.class);

    private final InventoryTagRepository tagRepository;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final FederationFanout fanout;
    private final FederationHttpClient httpClient;
    private final StationRepository stationRepository;

    @Inject
    public FederatedItemTagService(
            InventoryTagRepository tagRepository,
            FederationService federationService,
            FederationRepository federationRepository,
            FederationFanout fanout,
            FederationHttpClient httpClient,
            StationRepository stationRepository) {
        this.tagRepository = tagRepository;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.fanout = fanout;
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;
    }

    /**
     * The things carrying a word at the station itself and at every partner that lends to it.
     *
     * <p>The station's own things come back whole; a partner's come back as it chose to offer them.
     * A partner that cannot be reached loses only its own entries.
     *
     * @param stationId the station asking
     * @param name      the word as somebody typed it
     * @return what was found, the station's own first
     */
    public List<TaggedItemSummary> findAcrossPartners(int stationId, String name) {
        if (name == null || name.isBlank()) return List.of();
        var found = new ArrayList<>(tagRepository.findItemsByTag(List.of(stationId), name));
        var partners = federationService.findPartners(stationId).stream()
                .filter(partner -> partner.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(this::lendsWith)
                .toList();
        found.addAll(fanout.fanOut(
                partners,
                partner -> fromLocalPartner(partner, name),
                partner -> fromRemotePartner(partner, stationId, name)));
        return found;
    }

    /**
     * The things this station offers one partner for a word, which is what a partner's request
     * lands on.
     *
     * @param stationId the station serving the request
     * @param partnerId the partnership the request arrived on
     * @param name      the word the asking station used
     * @return what may be shown
     */
    public List<TaggedItemSummary> serveToPartner(int stationId, int partnerId, String name) {
        if (name == null || name.isBlank()) return List.of();
        return tagRepository.findSharedItemsByTag(stationId, partnerId, name);
    }

    private boolean lendsWith(FederationPartner partner) {
        return federationService.hasCapability(partner, CapabilityType.INVENTORY_LEND, Direction.IMPORT);
    }

    private List<TaggedItemSummary> fromLocalPartner(FederationPartner partner, String name) {
        var holding = stationRepository.findByUid(partner.partnerStationId()).orElse(null);
        if (holding == null) return List.of();
        var asking = stationRepository.resolveUid(partner.stationId());
        var reciprocal = federationRepository
                .findPartnerByStationAndRemoteUid(holding.id(), asking)
                .orElse(null);
        if (reciprocal == null) return List.of();
        return tagRepository.findSharedItemsByTag(holding.id(), reciprocal.id(), name);
    }

    private List<TaggedItemSummary> fromRemotePartner(FederationPartner partner, int stationId, String name) {
        var station = stationRepository.findById(stationId).orElse(null);
        if (station == null || station.federationPrivateKey() == null) {
            log.warn("No private key found for station {}, cannot ask partners for tagged items", stationId);
            return List.of();
        }
        return httpClient.getList(
                partner.remoteHost(),
                RemoteInventoryTagRoutes.GET_TAGGED_ITEMS.at(URLEncoder.encode(name, StandardCharsets.UTF_8)),
                partner.partnerStationId(),
                stationId,
                station.federationPrivateKey(),
                TaggedItemSummary.class);
    }
}
