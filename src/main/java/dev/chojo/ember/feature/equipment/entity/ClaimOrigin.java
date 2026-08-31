/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.entity;

/**
 * Where a claim on stock comes from.
 *
 * <p>Free over a window means the stock minus every claim that overlaps it, whatever the claim's
 * origin. Keeping the three apart as separate calculations that check each other at the last moment
 * would be two definitions of free, and the conflict would surface when somebody says yes rather than
 * when they plan.
 */
public enum ClaimOrigin {
    /** An appointment of this station needs the gear. */
    OWN_NEED,
    /** A partner station has been promised the gear, or already has it. */
    LOAN,
    /** The station has set a period aside on purpose, so that nothing is lent out over it. */
    BLOCK
}
