/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.time.Instant;

/**
 * An exchange as the exchange pages read it, derived from the movement underneath.
 *
 * @param id              the unique exchange request identifier
 * @param stationId       the station this exchange belongs to
 * @param memberId        the member requesting the exchange
 * @param itemId          the current item being exchanged, or {@code null} if not item-specific
 * @param inventoryId     the inventory the exchange is for
 * @param oldSizeId       the current size of the item, or {@code null} if not applicable
 * @param newSizeId       the desired new size, or {@code null} if not applicable
 * @param purpose         whether the gear is being issued, handed back or exchanged
 * @param exchangedItemId the replacement item assigned after exchange, or {@code null} if not yet exchanged
 * @param status          the current status of the exchange request
 * @param reason          the reason for the exchange
 * @param createdAt       when the request was created
 * @param updatedAt       when the request was last updated
 * @param createdBy       the member who created the request on behalf of another, or {@code null} if self-created
 */
public record ExchangeRequest(
        int id,
        int stationId,
        MovementPurpose purpose,
        int memberId,
        Integer itemId,
        int inventoryId,
        Integer oldSizeId,
        Integer newSizeId,
        Integer exchangedItemId,
        ExchangeStatus status,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        Integer createdBy) {}
