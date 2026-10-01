/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import dev.chojo.ember.api.MemberIdentity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One piece of a member's own gear as the member, or a guardian acting for them, reads it: carrying
 * the step of whatever movement it is on, so an exchange can be watched rather than seen as a jacket
 * vanishing.
 *
 * @param inventoryHomogeneous whether the inventory holds one thing in many copies, which is what makes
 *                             a piece exchangeable. Among a drawer of different things there is nothing
 *                             to swap it for
 * @param ownerKind            who owns it, which a member is entitled to know about what they look after
 * @param lostNote             what was written when it was reported missing, which the member wrote or
 *                             had written for them
 * @param icon                 the picture the piece is drawn with, resolved from its kind and its
 *                             inventory. A member's own page loads neither of those, so the answer
 *                             travels with the row
 */
public record MyInventoryItem(
        int id,
        int inventoryId,
        String name,
        @Nullable String internalId,
        String inventoryName,
        boolean inventoryHomogeneous,
        @Nullable Integer sizeId,
        @Nullable String sizeName,
        @Nullable Instant lostAt,
        ItemCustody custody,
        @Nullable Integer movementId,
        @Nullable String movementStep,
        ItemOwner ownerKind,
        @Nullable Integer ownerClusterId,
        @Nullable String lostNote,
        @Nullable MemberIdentity lostNoteBy,
        @Nullable String icon,
        @Nullable String color) {}
