/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.protocol;

import java.time.Instant;

/**
 * Body of {@code POST /api/v1/discovery/ping}.
 *
 * @param from        identity of the pinging instance
 * @param nonce       32-byte random base64; used to correlate the asynchronous callback and
 *                    to drop replays
 * @param issuedAt    wall-clock time the ping was constructed (used for ±5 min drift check)
 * @param callbackUrl fully-qualified URL the receiver must POST the callback to
 * @param depth       fan-out hint clamped to {@code [0, MAX_DEPTH]} by the receiver
 */
public record DiscoveryPingMessage(
        DiscoveryIdentity from, String nonce, Instant issuedAt, String callbackUrl, int depth) {}
