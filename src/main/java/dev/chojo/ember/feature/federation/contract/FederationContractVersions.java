/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import dev.chojo.ember.feature.federation.entity.FederationContract;

/**
 * The contract vector of the running build, computed once from the declared catalog. The
 * committed {@code federation_version.json} resource mirrors this value for the frontend
 * and the pinning test; the runtime always trusts its own computation, which cannot drift
 * from the binary.
 */
public final class FederationContractVersions {

    private static final FederationContract CURRENT = FederationVersionComputer.computeContract();

    private FederationContractVersions() {}

    public static FederationContract current() {
        return CURRENT;
    }
}
