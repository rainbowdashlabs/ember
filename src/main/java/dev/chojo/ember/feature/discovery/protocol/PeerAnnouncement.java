/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.protocol;

import java.time.Instant;

/**
 * One peer entry inside a {@link DiscoveryCallbackMessage}.
 *
 * @param firstSeenBy instance id of the peer that the sender originally learned this entry
 *                    from, for trust attribution
 */
public record PeerAnnouncement(
        String baseUrl, String publicKey, String instanceId, String firstSeenBy, Instant lastSeenAt) {}
