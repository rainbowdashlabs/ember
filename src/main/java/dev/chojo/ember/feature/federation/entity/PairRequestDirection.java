/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

/** Which side of a request to federate between two instances a station here is on. */
public enum PairRequestDirection {
    /** A station of another instance asked a station here. */
    INCOMING,
    /** A station here asked a station of another instance. */
    OUTGOING
}
