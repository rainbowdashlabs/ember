/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Which of a movement's two items a step is about.
 */
public enum StepSubject {
    /**
     * The item leaving.
     */
    OUTGOING,
    /**
     * The item arriving.
     */
    INCOMING
}
