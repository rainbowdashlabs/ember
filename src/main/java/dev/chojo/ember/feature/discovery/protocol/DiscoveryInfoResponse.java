/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.protocol;

import org.jspecify.annotations.Nullable;

/**
 * Body of {@code GET /api/v1/public/discovery/info} - the cheap, unauthenticated metadata
 * endpoint used during manual peer addition and admin "test connectivity" probes.
 *
 * <p>The public key may be missing when the answer comes from another instance that names none, which
 * is why adding such a peer is refused.
 */
public record DiscoveryInfoResponse(
        String baseUrl,
        String instanceId,
        @Nullable String publicKey,
        String softwareVersion,
        boolean discoveryEnabled) {}
