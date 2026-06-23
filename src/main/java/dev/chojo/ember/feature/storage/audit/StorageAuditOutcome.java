/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.audit;

/** Whether the action persisted by an audit row succeeded or failed. */
public enum StorageAuditOutcome {
    OK,
    FAILED
}
