/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * Somebody has called the movement off, and it is closed.
 *
 * <p>Unlike a refusal this can come from either end and at any point, so where the piece ended up is
 * not something the reader can work out from the fact alone: called off before the handover it is
 * back with whoever had it, called off while it was in the post it stayed where it had got to. The
 * message therefore carries that answer rather than leaving it to be guessed.
 *
 * @param stationId     the station it ran at
 * @param movementId    the movement
 * @param memberId      the member it concerned, or {@code null}
 * @param inventoryId   the inventory it was about
 * @param inventoryName that inventory's name, for the reader
 * @param itemName      the piece it was about, so the reader knows which of their gear this is
 * @param reason        why it was called off, in the words of whoever did it
 * @param itemStayedAway whether the piece did not come back, having already left the station
 * @param actorMemberId who called it off, so they are not told about their own doing
 */
public record MovementCancelled(
        int stationId,
        int movementId,
        Integer memberId,
        Integer inventoryId,
        String inventoryName,
        String itemName,
        String reason,
        boolean itemStayedAway,
        int actorMemberId)
        implements DomainEvent {}
