/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.inventory.entity.TaggedItemSummary;
import dev.chojo.ember.feature.inventory.repository.InventoryTagRepository;
import dev.chojo.ember.feature.inventory.route.RemoteInventoryTagRoutes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
public class FederatedItemTagService implements FederationServer {
    private final InventoryTagRepository tagRepository;
    private final InventoryTagService tagService;
    private final FederationService federationService;
    private final FederationFanout fanout;
    private final FederationTransport transport;

    @Inject
    public FederatedItemTagService(
            InventoryTagRepository tagRepository,
            InventoryTagService tagService,
            FederationService federationService,
            FederationFanout fanout,
            FederationTransport transport) {
        this.tagRepository = tagRepository;
        this.tagService = tagService;
        this.federationService = federationService;
        this.fanout = fanout;
        this.transport = transport;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteInventoryTagRoutes.GET_TAGGED_ITEMS,
                (partner, params, body) ->
                        serveToPartner(partner.servingStationId(), partner.partnerId(), params.text("tag")));
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
        var request = RemoteInventoryTagRoutes.GET_TAGGED_ITEMS.at(URLEncoder.encode(name, StandardCharsets.UTF_8));
        found.addAll(fanout.fanOut(partners, partner -> transport.getList(partner, request, TaggedItemSummary.class))
                .items());
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
        return tagService.findSharedItemsByTag(stationId, partnerId, name);
    }

    private boolean lendsWith(FederationPartner partner) {
        return federationService.hasCapability(partner, CapabilityType.INVENTORY_LEND, Direction.IMPORT);
    }
}
