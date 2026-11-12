/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

/** What the log holds around an occurrence of a problem, which decides what the page says. */
public enum ProblemContextState {
    /** Lines were found and are listed. */
    LINES,
    /** Nothing was found, and the log is not written to the database, which is why. */
    LOGGING_OFF,
    /** Nothing was found although the log is written: it was pruned, or its level was not kept. */
    NOTHING_KEPT,
    /** The problem holds no occurrence to look around, as one recorded before occurrences were kept. */
    NO_OCCURRENCE
}
