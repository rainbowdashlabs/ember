/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

/**
 * How far an association's own storage reaches.
 *
 * <p>What it decided, not where anything is: a station's bytes move when somebody moves them, and until then
 * a station under {@link #EVERY_STATION} is out of place rather than relocated.
 */
public enum ClusterBackendReach {
    /**
     * The association keeps no storage of its own, and the instance's is what stands behind everybody.
     */
    NONE,
    /**
     * The association's own files live on its storage; its stations' do not.
     */
    OWN_FILES,
    /**
     * The association's storage is where its stations' files belong too.
     */
    EVERY_STATION
}
