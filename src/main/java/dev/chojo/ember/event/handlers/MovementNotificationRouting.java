/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
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
     * The members to tell that a movement is now waiting on them.
     *
     * <p>An owner that does not run on this instance has nobody to tell. It is not left unnotified
     * in silence, though: the station stands in for it, so the station is who hears about it, and
     * the notification is a prompt to go and assert the step rather than a message the owner will
     * never see.
     *
     * @param repository  where station members are looked up
     * @param stationId   the station the movement runs at
     * @param memberId    the member the movement concerns, if any
     * @param nextActor   the party whose turn it is, or {@code null} once the chain has ended
     * @return the members to notify, which may be empty
     */
    static List<Integer> recipients(
            StationMemberRepository repository, int stationId, Integer memberId, StepActor nextActor) {
        if (nextActor == null) return memberId != null ? List.of(memberId) : List.of();
        return switch (nextActor) {
            case MEMBER -> memberId != null ? List.of(memberId) : stationTeam(repository, stationId);
            case STATION, OWNER -> stationTeam(repository, stationId);
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
