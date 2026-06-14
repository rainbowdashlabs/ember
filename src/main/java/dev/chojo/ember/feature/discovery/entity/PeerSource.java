/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.entity;

/**
 * How the local instance learned about a {@link DiscoveryPeer}.
 *
 * <ul>
 *   <li>{@link #BOOTSTRAP} — derived from a pre-existing {@code FederationPartner}.</li>
 *   <li>{@link #GOSSIP} — announced by another peer in a callback.</li>
 *   <li>{@link #MANUAL} — explicitly added by an admin via the discovery admin UI.</li>
 * </ul>
 *
 * Manually-added peers are exempt from reputation-driven garbage collection.
 */
public enum PeerSource {
    BOOTSTRAP,
    GOSSIP,
    MANUAL
}
