/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.entity;

/** How a link request ended. A request without an answer still waits. */
public enum LinkAnswer {
    /** The person linked their account to the member. */
    ACCEPTED,
    /** The person refused; the member stays without an account. */
    DECLINED,
    /** Thirty days passed without an answer. */
    EXPIRED
}
