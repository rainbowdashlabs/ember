/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

/** How the sidebar lists its sections, as an account chooses it. */
public enum SidebarMode {
    /** One section at a time: opening a section shows only its pages. */
    SECTIONS,
    /** Every section at once, each opened in place to show its pages. */
    TREE
}
