/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.entity;

/**
 * Direction of a recorded discovery ping nonce.
 * {@link #OUT} entries correlate our outbound pings with the matching callback,
 * {@link #IN} entries prevent reuse of nonces we have already processed.
 */
public enum PingDirection {
    OUT,
    IN
}
