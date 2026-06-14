/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.protocol;

/**
 * Wire-format identity of an Ember instance as exposed by the discovery protocol.
 * Used as the {@code from} field of pings and callbacks and as the body of {@code
 * /public/discovery/info}.
 */
public record DiscoveryIdentity(String baseUrl, String publicKey, String instanceId) {}
