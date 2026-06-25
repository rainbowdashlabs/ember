/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.protocol;

import java.time.Instant;
import java.util.List;

/**
 * Body of {@code POST /api/v1/discovery/peers} — the asynchronous answer to a ping.
 */
public record DiscoveryCallbackMessage(
        DiscoveryIdentity from, String inReplyTo, Instant issuedAt, List<PeerAnnouncement> peers) {}
