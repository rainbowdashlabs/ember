/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.entity;

/**
 * Which identity component a {@code discovery_blocklist} row matches against.
 */
public enum BlocklistKind {
    BASE_URL,
    PUBLIC_KEY
}
