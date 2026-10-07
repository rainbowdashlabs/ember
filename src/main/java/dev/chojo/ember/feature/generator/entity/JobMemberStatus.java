/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * How far a generation run got with one of its members.
 */
public enum JobMemberStatus {
    /** Not generated yet. */
    WAITING,
    /** Generated and filed with the member. */
    FILED,
    /** Not filed, for the reason the run recorded. */
    FAILED
}
