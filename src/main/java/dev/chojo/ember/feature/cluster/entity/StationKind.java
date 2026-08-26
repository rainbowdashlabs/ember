/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

/**
 * Tells a station somebody joins from the shell a cluster owns.
 */
public enum StationKind {
    /**
     * A station with members, which people open and work in.
     */
    REGULAR,
    /**
     * The shell a cluster owns. It is a real station row with a real identity, a real federation key pair and
     * a real storage scope, and it is where the cluster's content and its inventory pool live. Nobody joins
     * it, nobody sees it in a switcher, and no account can be added to it.
     */
    CLUSTER_HOME
}
