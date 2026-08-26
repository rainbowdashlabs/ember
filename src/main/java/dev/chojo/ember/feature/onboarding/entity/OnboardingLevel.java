/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.entity;

/**
 * Who an onboarding task belongs to: one member, the station and everyone who manages it, or the
 * instance and every administrator. Setting up a station happens once, not once per manager, so a
 * colleague arriving later is not offered work that is already done.
 */
public enum OnboardingLevel {
    MEMBER,
    STATION,
    INSTANCE
}
