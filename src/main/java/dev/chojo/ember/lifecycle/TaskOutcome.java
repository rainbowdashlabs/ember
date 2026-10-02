/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

/**
 * How the most recent run of a scheduled task went, as the admin task page shows it.
 */
public enum TaskOutcome {
    /** The task has not run since the instance started. */
    NOT_RUN_YET,
    /** A run is in progress right now. */
    RUNNING,
    /** The last run returned normally. */
    SUCCEEDED,
    /** The last run threw. */
    FAILED
}
