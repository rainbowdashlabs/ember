/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.notifications.entity.Audience;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Who a movement's notifications go to.
 *
 * <p>A chain with two parties in it only works if the message reaches whoever's turn it is next,
 * rather than always landing at the station. That is the whole rule, and it is one place so the
 * handlers cannot drift apart. The party is a station audience or a cluster audience, never both:
 * a station member and a cluster member are different people as far as a notification is concerned.
 */
final class MovementNotificationRouting {

    private MovementNotificationRouting() {}

    /**
     * Whoever's turn it is next.
     *
     * @param stationId      the station the movement belongs to
     * @param memberId       the member the gear belongs to, or {@code null}
     * @param nextActor      who acts next, or {@code null} once the chain has ended
     * @param ownerClusterId the cluster owning the gear, or {@code null}
     * @return the audience to tell
     */
    static Audience nextParty(
            int stationId,
            @Nullable Integer memberId,
            @Nullable StepActor nextActor,
            @Nullable Integer ownerClusterId) {
        if (nextActor == null) {
            return memberId != null ? StationAudience.member(memberId) : StationAudience.members(List.of());
        }
        return switch (nextActor) {
            case MEMBER -> memberId != null ? StationAudience.member(memberId) : stationTeam(stationId);
            case STATION -> stationTeam(stationId);
            case OWNER ->
                ownerClusterId != null
                        ? ClusterAudience.holders(ownerClusterId, ClusterPermission.CLUSTER_INVENTORY_MANAGER)
                        : stationTeam(stationId);
        };
    }

    /** The people who look after the station's inventory. */
    static StationAudience stationTeam(int stationId) {
        return StationAudience.holders(stationId, StationPermission.INVENTORY_MANAGER);
    }

    /**
     * Tells the next party, once while unread. Station members are led to the movement and the actor is
     * left out; cluster members are led to the cluster's movements.
     */
    static void tell(
            Notifier notifier,
            Audience party,
            Integer actorMemberId,
            NotificationType type,
            NotificationParams params,
            int movementId) {
        switch (party) {
            case StationAudience station ->
                notifier.notify(
                        station.except(actorMemberId),
                        type,
                        NotificationData.of(
                                params,
                                new NotificationData.NotificationLink(
                                        "inventory-movement-detail", Map.of("id", movementId))),
                        Delivery.ONCE_WHILE_UNREAD);
            case ClusterAudience cluster ->
                notifier.notify(
                        cluster,
                        type,
                        NotificationData.of(params, new NotificationData.NotificationLink("cluster-movements")),
                        Delivery.ONCE_WHILE_UNREAD);
        }
    }
}
