/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * The possible states of an event registration.
 */
public enum RegistrationStatus {
    PENDING,
    ACCEPTED,
    DENIED,
    DECLINED,
    WITHDRAWN
}
