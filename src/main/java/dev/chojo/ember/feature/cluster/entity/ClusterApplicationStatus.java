/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

/**
 * What became of a station's request to join a cluster.
 *
 * <p>Deliberately not the station application's {@code ApplicationStatus}. That one carries an unverified
 * state, because a stranger asking the instance to found a station has to prove their email address first;
 * this one carries a withdrawn state, because a station owner who changed their mind can take the request
 * back. Neither state means anything on the other side, and a shared enum would have to carry both.
 */
public enum ClusterApplicationStatus {
    /** Opened and waiting for somebody at the cluster to decide. */
    PENDING,
    /** The cluster said yes and the station belongs to it. */
    APPROVED,
    /** The cluster said no, with a reason the station owner can read. */
    DENIED,
    /** The station owner took the request back before it was decided. */
    WITHDRAWN;

    /** Whether a decision is still open, which is the only state an application can be acted on in. */
    public boolean open() {
        return this == PENDING;
    }
}
