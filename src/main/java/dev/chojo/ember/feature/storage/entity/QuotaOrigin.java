/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

/**
 * Where a resolved quota came from, so a screen can say "inherited" without guessing.
 *
 * <p>The order below is the order they are consulted in.
 */
public enum QuotaOrigin {
    /** The cluster granted this station this number. */
    CLUSTER_GRANT,
    /** The cluster granted this station nothing here, and this is what it gives its stations by default. */
    CLUSTER_DEFAULT,
    /** An instance administrator set this number for this station, which only reaches a station under no cluster. */
    INSTANCE_OVERRIDE,
    /** Nobody set anything, so the instance configuration decides. */
    INSTANCE_DEFAULT,
    /** Nobody bounds this station here, because nobody who could is paying for its storage. */
    UNLIMITED
}
