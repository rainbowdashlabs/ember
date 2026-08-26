/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

/**
 * What an association demands of a station reporting a piece of its gear missing.
 *
 * <p>The loss itself is not the association's to accept or refuse: it has already happened and is already
 * recorded. What is being asked for is a replacement, and this is the association saying what it needs to see
 * before it will consider one.
 */
public enum LossReportRequirement {
    /**
     * The report on its own is enough.
     */
    NOTHING,
    /**
     * The reporting manager has to write what happened.
     */
    NOTE,
    /**
     * A document has to come with it as well, which is what a body answering to somebody else needs.
     */
    DOCUMENT
}
