/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * How a request for a new register name ended.
 */
public enum NameChangeOutcome {
    /** A member manager accepted it and the account carries the new name. */
    APPROVED,
    /** A member manager turned it down; the account keeps its name. */
    DENIED,
    /** The member took it back before anybody decided. */
    WITHDRAWN
}
