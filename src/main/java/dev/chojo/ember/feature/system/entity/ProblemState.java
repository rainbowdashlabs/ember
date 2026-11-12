/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

/**
 * Where a stored problem stands with the administrators.
 */
public enum ProblemState {
    /** Waiting for somebody to look at it. */
    OPEN,
    /** Taken care of; happening again opens it. */
    RESOLVED,
    /** Known noise; happening again only counts. */
    MUTED
}
