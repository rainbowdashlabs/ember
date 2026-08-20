/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.inventory.entity.StepActor;

/**
 * A movement of gear between two parties has been started.
 *
 * @param stationId     the station it runs at
 * @param movementId    the movement
 * @param memberId      the member it concerns, or {@code null} for one with no member at either end
 * @param memberName    that member's name, for the reader
 * @param inventoryId   the inventory it is about
 * @param inventoryName that inventory's name, for the reader
 * @param reason        why it was started
 * @param actorMemberId who started it, so they are not told about their own doing
 * @param nextActor     the party whose turn it is now, or {@code null} if it finished on the spot
 */
public record MovementStarted(
        int stationId,
        int movementId,
        Integer memberId,
        String memberName,
        Integer inventoryId,
        String inventoryName,
        String reason,
        int actorMemberId,
        StepActor nextActor)
        implements DomainEvent {}
