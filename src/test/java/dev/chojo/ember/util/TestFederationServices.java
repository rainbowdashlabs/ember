/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.StationKeyStore;
import dev.chojo.ember.feature.station.repository.StationRepository;

import java.util.Set;

/**
 * Builds a {@link FederationService} for tests that work with partnerships and do not listen for what it
 * tells about requests to federate: its events go to a bus nobody listens on.
 */
public final class TestFederationServices {
    private TestFederationServices() {}

    /**
     * A service over the test database, with the test station keys and the default address.
     *
     * @param federation the federation storage
     * @param stations   the station storage
     * @return the service
     */
    public static FederationService of(FederationRepository federation, StationRepository stations) {
        return of(federation, stations, TestStationKeys.store(), new Api());
    }

    /**
     * A service over the test database with the given keys and address.
     *
     * @param federation the federation storage
     * @param stations   the station storage
     * @param keys       the stations' federation keys
     * @param api        the address this instance goes by
     * @return the service
     */
    public static FederationService of(
            FederationRepository federation, StationRepository stations, StationKeyStore keys, Api api) {
        return new FederationService(federation, stations, keys, new DomainEventBus(Set.of()), api);
    }
}
