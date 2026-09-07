/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What the number a member read off a piece turned out to match.
 *
 * <p>Every one of these is a finding rather than a fact. Nothing in the schema makes a number
 * unique, the number a member types shares its namespace with the containers, and a member naming a
 * colleague's jacket is a question for whoever reviews the submission rather than a transfer.
 */
public enum SelfCheckIdentifierFinding {
    /**
     * The member left it blank, which is allowed: a piece with no label is still a piece they hold.
     */
    NOTHING_TYPED,
    /**
     * Nothing the station holds carries this number. The submission still stands and a reviewer may
     * write the piece down.
     */
    NO_MATCH,
    /**
     * One piece carries it and nobody is holding it.
     */
    FREE,
    /**
     * One piece carries it and it is on somebody's record already.
     */
    HELD,
    /**
     * More than one thing carries it, so which one was meant cannot be read off the number.
     */
    SEVERAL,
    /**
     * The only thing carrying it is a container, which shares the numbering with the gear.
     */
    A_CONTAINER
}
