/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * Whoever's turn it was has refused, and the movement has closed. The item that set out is back
 * where it came from, which is what the people at the other end need to hear about.
 *
 * @param stationId     the station it ran at
 * @param movementId    the movement
 * @param memberId      the member it concerned, or {@code null}
 * @param inventoryId   the inventory it was about
 * @param inventoryName that inventory's name, for the reader
 * @param reason        why it was refused, in the words of whoever refused
 * @param actorMemberId who refused, so they are not told about their own doing
 */
public record MovementDeclined(
        int stationId,
        int movementId,
        Integer memberId,
        Integer inventoryId,
        String inventoryName,
        String reason,
        int actorMemberId)
        implements DomainEvent {}
