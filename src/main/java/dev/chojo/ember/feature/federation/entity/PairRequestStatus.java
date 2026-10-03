/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

/** Where a request to federate between two instances stands. */
public enum PairRequestStatus {
    /** The asked station has not answered yet. */
    PENDING,
    /** The asked station agreed, and both stations are partners. */
    ACCEPTED,
    /** The asked station said no. */
    DECLINED
}
