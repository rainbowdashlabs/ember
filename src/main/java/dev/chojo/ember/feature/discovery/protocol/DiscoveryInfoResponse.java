/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.protocol;

/**
 * Body of {@code GET /api/v1/public/discovery/info} — the cheap, unauthenticated metadata
 * endpoint used during manual peer addition and admin "test connectivity" probes.
 */
public record DiscoveryInfoResponse(
        String baseUrl, String instanceId, String publicKey, String softwareVersion, boolean discoveryEnabled) {}
