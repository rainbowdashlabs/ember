/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Closes the setup wizard for a station the demo has finished building.
 *
 * <p>The station left deliberately un-set-up, so the first-login flow can be walked through, is the
 * instance's business rather than any station's and is {@link DemoFreshStationSeeder}.
 */
@Singleton
public class DemoSetupSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoSetupSeeder.class);
    private final StationRepository stationRepository;

    @Inject
    public DemoSetupSeeder(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Override
    public int order() {
        return SETUP_STATE;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        stationRepository.markSetupComplete(station.stationId());
        log.info("Demo: Marked station {} setup complete", station.station().name());
    }
}
