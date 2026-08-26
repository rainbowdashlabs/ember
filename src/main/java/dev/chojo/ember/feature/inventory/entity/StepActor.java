/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * The party that acknowledges a step.
 *
 * <p>Making the acknowledger a property of the step rather than a branch in the code is what lets
 * one flow serve an owner that uses Ember and one that does not: the owner's steps are the owner's
 * either way, and only who presses the button changes.
 */
public enum StepActor {
    /**
     * The member the movement concerns.
     */
    MEMBER,
    /**
     * The station running the movement.
     */
    STATION,
    /**
     * The body above the station that owns the gear. When that body does not use Ember, the station
     * stands in for it and the log records that it was asserted rather than confirmed.
     */
    OWNER
}
