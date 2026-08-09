/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation;

import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import org.mockito.ArgumentMatchers;

/**
 * Test support for federation contract vectors. Remote partners are incompatible until a
 * vector is stored for them, so tests exercising cross-instance fan-out must mark their
 * remote partners as speaking the current contract.
 */
public final class FederationTestContracts {

    private FederationTestContracts() {}

    /**
     * Matches the federation request a stubbed HTTP client call is made with by its resolved
     * path, so tests keep expressing the endpoint they mean as the path it produces.
     */
    public static FederationRequest pathIs(String path) {
        return ArgumentMatchers.argThat(
                request -> request != null && request.path().equals(path));
    }

    /**
     * {@link #pathIs} for calls whose path carries a query string the test does not pin.
     */
    public static FederationRequest pathContains(String path) {
        return ArgumentMatchers.argThat(
                request -> request != null && request.path().contains(path));
    }

    /**
     * Stores this build's contract vector on every remote partner of the given station.
     */
    public static void storeCurrentContractOnRemotePartners(
            FederationService federationService, FederationRepository federationRepo, int stationId) {
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.isRemote()) {
                federationRepo.updateFederationContract(partner.id(), FederationContractVersions.current());
            }
        }
    }
}
