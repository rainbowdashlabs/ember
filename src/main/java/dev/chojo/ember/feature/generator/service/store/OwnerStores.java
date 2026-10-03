/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.store;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Where an owner of templates and fonts keeps its files, and whose media library its pictures come from.
 *
 * <p>A station keeps both itself. An association keeps everything in the station it owns, its home
 * station: its files under that station's association scope, counted against the room it was given
 * there, and its pictures in that station's media library, the same library its wiki and news draw on.
 * The instance keeps its files in its own storage and has no templates.
 */
@Singleton
public class OwnerStores {
    private final ClusterService clusters;
    private final StationRepository stations;

    @Inject
    public OwnerStores(ClusterService clusters, StationRepository stations) {
        this.clusters = clusters;
        this.stations = stations;
    }

    /**
     * @param owner   who keeps files
     * @param missing what to refuse with where the association is gone
     * @return where the owner's files are kept
     */
    public StorageScope scopeOf(Owner owner, Refusal missing) {
        return switch (owner) {
            case Owner.Station station ->
                new StorageScope.Station(station.stationId(), stations.requireUid(station.stationId()));
            case Owner.Association association -> {
                int home = homeOf(association, missing);
                yield new StorageScope.Association(home, stations.requireUid(home));
            }
            case Owner.Instance ignored -> new StorageScope.Instance();
        };
    }

    /**
     * The station whose media library a template of the owner takes its pictures from.
     *
     * @param owner the station or the association that keeps the template
     * @return the station itself, or the association's home station
     */
    public int libraryOf(Owner owner) {
        return switch (owner) {
            case Owner.Station station -> station.stationId();
            case Owner.Association association -> homeOf(association, DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE);
            case Owner.Instance ignored -> throw DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE.raise();
        };
    }

    /**
     * @param stationId a station
     * @return the association it belongs to, or empty where it belongs to none
     */
    public Optional<Owner.Association> associationOf(int stationId) {
        return clusters.findByStation(stationId).map(cluster -> new Owner.Association(cluster.id()));
    }

    private int homeOf(Owner.Association association, Refusal missing) {
        return clusters.findById(association.clusterId())
                .map(Cluster::homeStationId)
                .orElseThrow(missing::raise);
    }
}
