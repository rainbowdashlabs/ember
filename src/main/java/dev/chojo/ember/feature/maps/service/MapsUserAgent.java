/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import dev.chojo.ember.feature.federation.contract.FederationContractVersions;

/**
 * The {@code User-Agent} every map request of this instance carries, naming Ember and where it
 * comes from, as the usage policies of public tile and address search servers ask.
 */
final class MapsUserAgent {
    private MapsUserAgent() {}

    /**
     * The header value.
     *
     * @return Ember, its version and its project address
     */
    static String value() {
        return "Ember/" + FederationContractVersions.current().core() + " (https://github.com/RainbowDashLabs/ember)";
    }
}
