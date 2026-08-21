/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * Who asked a profile question, which is not the same as who answers it.
 *
 * <p>It travels with every field and every answer because the two id spaces are separate: a station's
 * questions and its cluster's are numbered in their own tables, and the same number means a different
 * question in each. It also decides whether the people at the station may write the answer at all.
 */
public enum FieldOrigin {
    /** The station's own question, which the station may change. */
    STATION,
    /** A question the cluster above the station asks, which the station may not, unless it says otherwise. */
    CLUSTER
}
