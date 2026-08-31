/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What a custom field definition describes.
 *
 * <p>The order is deliberate and is the collision rule: where one key is defined at two levels, the
 * later constant wins, because the narrower definition is the one that was written about this piece
 * rather than about everything in the drawer.
 */
public enum FieldLevel {
    /**
     * Everything in the inventory, which is what every definition was before there were kinds.
     */
    INVENTORY,
    /**
     * Every piece of one kind. Six radios share the field and never the value.
     */
    ART,
    /**
     * One single piece, for the thing that has a plate number nothing else has.
     */
    ITEM
}
