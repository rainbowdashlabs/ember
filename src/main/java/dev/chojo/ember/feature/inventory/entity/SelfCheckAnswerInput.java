/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * One answer as the member gives it, before anything has been checked about it.
 *
 * @param itemId          the piece it is about, or {@code null} where it is about an empty place
 * @param inventoryId     the inventory the empty place belongs to, ignored on an answer about a
 *                        piece because the piece already says which inventory it sits in
 * @param slot            which empty place in that inventory, counted from zero
 * @param answer          what the member said
 * @param note            what they wrote beside it, which may be blank
 * @param typedInternalId the number they read off a piece nobody wrote down, which may be blank
 * @param sizeId          the size they gave for such a piece, which may be absent because it is
 *                        offered rather than asked for
 */
public record SelfCheckAnswerInput(
        Integer itemId,
        Integer inventoryId,
        Integer slot,
        SelfCheckAnswer answer,
        String note,
        String typedInternalId,
        Integer sizeId) {}
