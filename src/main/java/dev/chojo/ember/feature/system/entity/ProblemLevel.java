/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

/**
 * What kind of problem was logged.
 */
public enum ProblemLevel {
    /** A warning. */
    WARN,
    /** An error. */
    ERROR,
    /** A request the application refused, which says more about the interface than about a fault. */
    REFUSAL
}
