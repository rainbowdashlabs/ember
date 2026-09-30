/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.service;

import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.content.entity.CellConfig;

/**
 * Checks, on the way in, that a block naming something of another feature names only what every
 * reader of its content may see.
 *
 * <p>An interface because the things named belong to the features that own them, news entries and
 * appointments, while the blocks naming them are saved here. Checking in those features would leave
 * a page, a news entry and a wiki article each with a check of its own; the arrow keeps pointing the
 * way it already does, the same as {@link CellDescriptions.PageAddressing}: blocks know nothing of
 * news or appointments, and the owning feature answers.
 */
public interface BlockReferences {

    /**
     * Refuses a block that names something not every reader of the audience may see. A block of
     * another kind, and one naming nothing, passes.
     *
     * @param stationId the station the content belongs to, or {@code null} for the instance's, where
     *                  nothing a station owns may be named
     * @param audience  who reads the content the block sits in
     * @param config    the block's settings
     */
    void requireReachable(Integer stationId, BlockAudience audience, CellConfig config);
}
