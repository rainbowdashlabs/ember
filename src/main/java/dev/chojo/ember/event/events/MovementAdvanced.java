/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.inventory.entity.StepActor;

/**
 * A step of a movement has been acknowledged and the chain has moved on.
 *
 * @param stationId     the station it runs at
 * @param movementId    the movement
 * @param memberId      the member it concerns, or {@code null}
 * @param inventoryId   the inventory it is about
 * @param inventoryName that inventory's name, for the reader
 * @param stepLabel     the words of the step just acknowledged, as the flow gives them
 * @param actorMemberId who acknowledged it, so they are not told about their own doing
 * @param nextActor     the party whose turn it is now, or {@code null} once the chain has ended
 * @param ownerClusterId the cluster that owns the gear, or {@code null} when no cluster on this instance does
 */
public record MovementAdvanced(
        int stationId,
        int movementId,
        Integer memberId,
        Integer inventoryId,
        String inventoryName,
        String stepLabel,
        int actorMemberId,
        StepActor nextActor,
        Integer ownerClusterId)
        implements DomainEvent {}
