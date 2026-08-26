/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;

import java.util.List;

/**
 * Who a movement's notifications go to.
 *
 * <p>A chain with two parties in it only works if the message reaches whoever's turn it is next,
 * rather than always landing at the station. That is the whole rule, and it is one place so the
 * three handlers cannot drift apart.
 */
final class MovementNotificationRouting {

    private MovementNotificationRouting() {}

    /**
     * The two audiences a movement can be waiting on.
     *
     * <p>They are separate lists because they are separate id spaces: a station member and a cluster
     * member are different rows in different tables, and a notification is written against one or the
     * other. Exactly one of the two is ever populated.
     *
     * @param stationMembers station members to tell
     * @param clusterMembers cluster members to tell
     */
    record Recipients(List<Integer> stationMembers, List<Integer> clusterMembers) {

        static Recipients station(List<Integer> ids) {
            return new Recipients(ids, List.of());
        }

        static Recipients cluster(List<Integer> ids) {
            return new Recipients(List.of(), ids);
        }

        static Recipients none() {
            return new Recipients(List.of(), List.of());
        }
    }

    /**
     * The people to tell that a movement is now waiting on them.
     *
     * <p>A step belonging to the owner reaches the owner when the owner is a cluster on this instance:
     * its inventory managers are the ones who can answer it, and the station has nothing to do but
     * wait. An owner that does not run here has nobody to tell. It is not left unnotified in silence,
     * though: the station stands in for it, so the station is who hears about it, and the notification
     * is a prompt to go and assert the step rather than a message the owner will never see.
     *
     * @param repository     where station members are looked up
     * @param clusterService where cluster members are looked up
     * @param stationId      the station the movement runs at
     * @param memberId       the member the movement concerns, if any
     * @param nextActor      the party whose turn it is, or {@code null} once the chain has ended
     * @param ownerClusterId the cluster owning the gear, or {@code null} when no cluster here does
     * @return who to notify, which may be nobody
     */
    static Recipients recipients(
            StationMemberRepository repository,
            ClusterService clusterService,
            int stationId,
            Integer memberId,
            StepActor nextActor,
            Integer ownerClusterId) {
        if (nextActor == null) {
            return memberId != null ? Recipients.station(List.of(memberId)) : Recipients.none();
        }
        return switch (nextActor) {
            case MEMBER ->
                memberId != null
                        ? Recipients.station(List.of(memberId))
                        : Recipients.station(stationTeam(repository, stationId));
            case STATION -> Recipients.station(stationTeam(repository, stationId));
            case OWNER ->
                ownerClusterId != null
                        ? Recipients.cluster(clusterService.findMemberIdsWith(
                                ownerClusterId, ClusterPermission.CLUSTER_INVENTORY_MANAGER))
                        : Recipients.station(stationTeam(repository, stationId));
        };
    }

    /**
     * The people who work the station's movement queue.
     */
    static List<Integer> stationTeam(StationMemberRepository repository, int stationId) {
        return repository.findMembersWithPermission(stationId, StationPermission.INVENTORY_MANAGER).stream()
                .map(StationMember::id)
                .toList();
    }
}
