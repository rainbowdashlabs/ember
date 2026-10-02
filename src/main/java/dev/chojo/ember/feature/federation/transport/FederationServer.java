/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

/**
 * A feature that answers federation requests. Each one is bound into the set
 * {@link FederationEndpoints} is built from and registers its serving functions there, next to the
 * data they read.
 */
public interface FederationServer {

    /**
     * Registers every serving function of the feature.
     *
     * @param endpoints the registry to register with
     */
    void serveOn(FederationEndpoints endpoints);
}
