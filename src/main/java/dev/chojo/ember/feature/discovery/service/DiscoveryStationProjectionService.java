/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.PublicOffer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationAddresses;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.PublicStationInfoService;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.util.WebOrigins;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Projects local stations into the {@link DiscoveryStationCard} format used by the public
 * stations endpoint. Only {@link DiscoveryVisibility#PUBLIC PUBLIC} stations leak out via
 * this projection - the {@code INSTANCE} and {@code NONE} scopes are filtered server-side.
 *
 * <p>Clusters, public offers and member counts are read for all stations together, one query each,
 * and handed to the card of each station.
 *
 * <p>Country, region, city and tags are placeholders until the station schema grows columns
 * for those - once the admin UI exposes them they can be wired in here without a wire-format
 * change.
 */
@Singleton
public class DiscoveryStationProjectionService {

    private final StationRepository stationRepository;
    private final ClusterRepository clusterRepository;
    private final StationMemberRepository memberRepository;
    private final Conf conf;
    private final StationLogoService logoService;
    private final PublicStationInfoService publicStationInfo;

    @Inject
    public DiscoveryStationProjectionService(
            StationRepository stationRepository,
            ClusterRepository clusterRepository,
            StationMemberRepository memberRepository,
            Conf conf,
            StationLogoService logoService,
            PublicStationInfoService publicStationInfo) {
        this.stationRepository = stationRepository;
        this.clusterRepository = clusterRepository;
        this.memberRepository = memberRepository;
        this.conf = conf;
        this.logoService = logoService;
        this.publicStationInfo = publicStationInfo;
    }

    /**
     * Returns the {@link DiscoveryVisibility#PUBLIC PUBLIC}-scoped station cards exposed to
     * other instances.
     */
    public List<DiscoveryStationCard> publicCards() {
        var stations = stationRepository.findDiscoverable(0, DiscoveryVisibility.PUBLIC, DiscoveryVisibility.PUBLIC);
        var ids = stations.stream().map(Station::id).toList();
        var clusters = clusterRepository.findByStations(ids);
        var offers = publicStationInfo.offers(stations);
        var memberCounts = memberRepository.countCurrent(ids);
        String baseUrl = WebOrigins.stripTrailingSlash(conf.main().api().baseUrl());
        List<DiscoveryStationCard> cards = new ArrayList<>(stations.size());
        for (var station : stations) {
            cards.add(toCard(
                    station,
                    baseUrl,
                    clusters.get(station.id()),
                    Objects.requireNonNull(offers.get(station.id()), "every listed station has an offer"),
                    memberCounts.getOrDefault(station.id(), 0)));
        }
        return cards;
    }

    /**
     * One station's card. Its cluster is carried so a reader can group the cards; a station outside
     * any cluster sends none, which means the same as a peer too old to know about clusters. The logo
     * address goes out only for a station that has a logo, and the public page only for one whose page
     * shows something, so no reader is sent to an address that leads nowhere. The public offers are the
     * ones this instance's own discovery page shows for the station.
     */
    private DiscoveryStationCard toCard(
            Station station, String baseUrl, @Nullable Cluster cluster, PublicOffer offer, int memberCount) {
        PublicOffer shown = offer.inDiscoveryOf(station);
        return new DiscoveryStationCard(
                station.uid().toString(),
                station.name(),
                station.discoveryDescription(),
                logoService.exists(station.id()) ? StationAddresses.logo(baseUrl, station) : null,
                station.country(),
                null,
                station.city(),
                offer.isEmpty() ? null : StationAddresses.publicPage(baseUrl, station),
                List.of(),
                DiscoveryStationCard.bucketMemberCount(memberCount),
                Instant.now(),
                station.addressLine(),
                station.latitude(),
                station.longitude(),
                cluster == null ? null : cluster.uid().toString(),
                cluster == null ? null : cluster.name(),
                station.publicSlug(),
                shown.knowledgeBase(),
                shown.calendar(),
                shown.blog(),
                shown.waitlist(),
                station.acceptsFederation());
    }
}
