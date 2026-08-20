/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * How a step came to be acknowledged, which is what makes a gap in a chain visible as a gap rather
 * than papering over it.
 */
public enum AckKind {
    /**
     * The party that owns the step said so itself.
     */
    CONFIRMED,
    /**
     * The station said so on behalf of an owner that does not use Ember and cannot answer.
     */
    ASSERTED,
    /**
     * The flow owner overrode a party that could have answered and did not. Carries a mandatory
     * note, because an unresponsive counterparty must not be able to freeze an item in transit
     * forever and the record should say who decided that it would not.
     */
    FORCED
}
