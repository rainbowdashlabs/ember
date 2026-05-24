/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

public enum RoleScope {
    /**
     * Represents a primary user type on system level
     * Usually a user should be unly part of one of these roles.
     */
    SYSTEM_USER,
    /**
     * Represents a permission type on system level
     */
    SYSTEM_PERMISSION,
    /**
     * Represents a user type on cluster level
     * Usually a user should be unly part of one of these roles.
     */
    CLUSTER_USER,
    /**
     * Represents a permission type on cluster level
     */
    CLUSTER_PERMISSION,
    /**
     * Represents a primary user type on station level
     * Usually a user should be unly part of one of these roles.
     */
    STATION_USER,
    /**
     * Represents a permission type on station level
     */
    STATION_PERMISSION
}
