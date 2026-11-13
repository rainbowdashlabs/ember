/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

/** How a reported message was handled. */
public enum ReportOutcome {
    /** The message was removed for the station. */
    DELETED,
    /** The message stays; the report was set aside. */
    DISMISSED
}
