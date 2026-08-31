/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.cluster.entity.ClusterInventoryTag;
import dev.chojo.ember.feature.cluster.entity.RecommendedTag;
import dev.chojo.ember.feature.cluster.repository.ClusterInventoryTagRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryTagRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * The words an association recommends to its stations.
 *
 * <p>A recommendation never displaces what a station already calls a thing. Both rows stay, each
 * station keeps showing the spelling it gave, and the two are one word for every purpose that
 * matters because they share a canonical name. What the association gains is a shared vocabulary
 * that a station can take up without being made to.
 */
@Singleton
public class ClusterInventoryTagService {
    private static final Logger log = LoggerFactory.getLogger(ClusterInventoryTagService.class);

    private final ClusterInventoryTagRepository clusterTagRepository;
    private final InventoryTagRepository stationTagRepository;

    @Inject
    public ClusterInventoryTagService(
            ClusterInventoryTagRepository clusterTagRepository, InventoryTagRepository stationTagRepository) {
        this.clusterTagRepository = clusterTagRepository;
        this.stationTagRepository = stationTagRepository;
    }

    /**
     * Every word an association recommends.
     *
     * @param clusterId the association
     * @return its recommendations, in the order it put them in
     */
    public List<ClusterInventoryTag> findByCluster(int clusterId) {
        return clusterTagRepository.findByCluster(clusterId);
    }

    /**
     * Finds one recommendation by its identifier.
     *
     * @param id the identifier
     * @return the recommendation, or empty
     */
    public Optional<ClusterInventoryTag> findById(int id) {
        return clusterTagRepository.findById(id);
    }

    /**
     * The words recommended to one station, each saying whether the station already uses it.
     *
     * @param stationId the station
     * @return the recommendations, empty when the station answers to no association
     */
    public List<RecommendedTag> recommendationsFor(int stationId) {
        var own = new HashSet<String>();
        for (var tag : stationTagRepository.findByStation(stationId)) {
            own.add(tag.canonicalName());
        }
        return clusterTagRepository.findForStation(stationId).stream()
                .map(tag -> new RecommendedTag(tag.name(), tag.color(), own.contains(tag.canonicalName())))
                .toList();
    }

    /**
     * Writes a recommendation down.
     *
     * @param clusterId      the association
     * @param name           the word
     * @param color          optional hex colour for the badge
     * @param stationGroupId the group of stations it is meant for, or {@code null} for all of them
     * @return the recommendation
     */
    public ClusterInventoryTag create(int clusterId, String name, String color, Integer stationGroupId) {
        String wanted = requireName(name);
        if (clusterTagRepository.findByName(clusterId, stationGroupId, wanted).isPresent()) {
            throw new BadRequestResponse("The association already recommends that word to these stations");
        }
        var tag = clusterTagRepository.create(clusterId, wanted, color, stationGroupId);
        log.info("Cluster item tag {} created for cluster {}: '{}'", tag.id(), clusterId, tag.name());
        return tag;
    }

    /**
     * Changes a recommendation.
     *
     * @param clusterId      the association the recommendation has to belong to
     * @param id             the recommendation
     * @param name           the new word
     * @param color          the new colour
     * @param position       where it should sit
     * @param stationGroupId the group of stations it should be meant for
     * @return the recommendation as it now stands
     */
    public ClusterInventoryTag update(
            int clusterId, int id, String name, String color, int position, Integer stationGroupId) {
        var tag = requireOwnTag(clusterId, id);
        String wanted = requireName(name);
        var clash = clusterTagRepository.findByName(clusterId, stationGroupId, wanted);
        if (clash.isPresent() && clash.get().id() != tag.id()) {
            throw new BadRequestResponse("The association already recommends that word to these stations");
        }
        clusterTagRepository.update(id, wanted, color, position, stationGroupId);
        return clusterTagRepository.findById(id).orElseThrow(NotFoundResponse::new);
    }

    /**
     * Withdraws a recommendation. Every station that took the word up keeps its own row and its
     * things keep their tags.
     *
     * @param clusterId the association the recommendation has to belong to
     * @param id        the recommendation
     */
    public void delete(int clusterId, int id) {
        requireOwnTag(clusterId, id);
        clusterTagRepository.delete(id, clusterId);
        log.info("Cluster item tag {} withdrawn by cluster {}", id, clusterId);
    }

    private static String requireName(String name) {
        String wanted = name == null ? "" : name.strip();
        if (wanted.isEmpty()) throw new BadRequestResponse("A tag needs a name");
        return wanted;
    }

    private ClusterInventoryTag requireOwnTag(int clusterId, int id) {
        var tag = clusterTagRepository.findById(id).orElseThrow(NotFoundResponse::new);
        if (tag.clusterId() != clusterId) throw new NotFoundResponse();
        return tag;
    }
}
