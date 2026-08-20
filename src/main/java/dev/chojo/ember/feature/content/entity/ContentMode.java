/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

/**
 * How a news entry or a knowledge-base article was written.
 *
 * <p>The simple article stays the default: rich is a choice, not an upgrade everybody is pushed
 * through. The switch only goes one way. Simple to rich puts the existing text into a single
 * markdown block, which loses nothing; the other direction is not offered, because the stored text
 * of a rich article is a projection of its blocks and must stay derived. There has to be no path
 * where somebody edits the projection and expects the blocks to follow.
 */
public enum ContentMode {
    /**
     * Written as text, in one markdown field, which is the right tool for a short notice.
     */
    SIMPLE,
    /**
     * Built from blocks with the page editor, with the stored text derived from them on every save.
     */
    RICH
}
