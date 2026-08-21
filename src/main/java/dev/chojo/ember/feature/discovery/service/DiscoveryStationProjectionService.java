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
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Projects local stations into the {@link DiscoveryStationCard} format used by the public
 * stations endpoint. Only {@link DiscoveryVisibility#PUBLIC PUBLIC} stations leak out via
 * this projection - the {@code INSTANCE} and {@code NONE} scopes are filtered server-side.
 *
 * <p>Country, region, city and tags are placeholders until the station schema grows columns
 * for those - once the admin UI exposes them they can be wired in here without a wire-format
 * change.
 */
@Singleton
public class DiscoveryStationProjectionService {

    private final StationRepository stationRepository;
    private final ClusterRepository clusterRepository;
    private final Conf conf;

    @Inject
    public DiscoveryStationProjectionService(
            StationRepository stationRepository, ClusterRepository clusterRepository, Conf conf) {
        this.stationRepository = stationRepository;
        this.clusterRepository = clusterRepository;
        this.conf = conf;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Returns the {@link DiscoveryVisibility#PUBLIC PUBLIC}-scoped station cards exposed to
     * other instances.
     */
    public List<DiscoveryStationCard> publicCards() {
        var stations = stationRepository.findDiscoverable(0, DiscoveryVisibility.PUBLIC, DiscoveryVisibility.PUBLIC);
        List<DiscoveryStationCard> cards = new ArrayList<>(stations.size());
        String baseUrl = stripTrailingSlash(conf.main().api().baseUrl());
        for (var s : stations) {
            cards.add(toCard(s, baseUrl));
        }
        return cards;
    }

    private DiscoveryStationCard toCard(Station station, String baseUrl) {
        int memberCount = countMembers(station.id());
        // Carried so a reader can group the cards. A station outside any cluster sends nothing, which is
        // also what a peer too old to know about clusters sends, and the two mean the same thing.
        Optional<Cluster> cluster = clusterRepository.findByStation(station.id());
        return new DiscoveryStationCard(
                station.uid().toString(),
                station.name(),
                station.discoveryDescription(),
                baseUrl + "/api/v1/public/stations/" + station.uid() + "/logo",
                station.country(),
                null,
                station.city(),
                station.publicSlug() != null ? baseUrl + "/s/" + station.publicSlug() : baseUrl,
                List.of(),
                DiscoveryStationCard.bucketMemberCount(memberCount),
                Instant.now(),
                station.addressLine(),
                station.latitude(),
                station.longitude(),
                cluster.map(c -> c.uid().toString()).orElse(null),
                cluster.map(Cluster::name).orElse(null));
    }

    private int countMembers(int stationId) {
        return query("SELECT count(*) AS c FROM station_member WHERE station_id = :station_id AND former = FALSE;")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
    }
}
