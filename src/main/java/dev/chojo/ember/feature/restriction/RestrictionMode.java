/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

/**
 * The mode for combining restrictions across types (role, group, tag).
 * Member restrictions are always OR-connected regardless of this mode.
 */
public enum RestrictionMode {
    /**
     * All non-empty restriction types must match (strict).
     */
    AND,
    /**
     * Any match across any type is sufficient (permissive).
     */
    OR
}
