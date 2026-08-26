/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates the demo instance administrator and every station the demo builds in full, with all their
 * public surfaces enabled. Every other seeder builds on the stations this step publishes.
 *
 * <p>Which stations those are is {@link DemoStations}, and the only thing that differs between them is
 * what a {@link DemoStationProfile} carries: they are otherwise set up line for line the same, so a
 * difference seen later is somebody else's doing.
 */
@Singleton
public class DemoStationSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoStationSeeder.class);
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;

    @Inject
    public DemoStationSeeder(AccountRepository accountRepository, StationRepository stationRepository) {
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
    }

    @Override
    public int order() {
        return STATION;
    }

    @Override
    public void seed(DemoRunContext run) {
        var admin = accountRepository.create("admin@ember.local", "Admin", "Demo", true);
        accountRepository.setUid(admin.id(), DemoUids.account("admin@ember.local"));
        accountRepository.createCredential(admin.id(), run.passwordHash());
        accountRepository.setInstanceUserType(admin.id(), InstanceUserType.ADMINISTRATOR);
        run.adminAccount(admin);

        for (DemoStationProfile profile : DemoStations.ALL) {
            run.addStation(profile, create(profile));
        }
    }

    /** One station, set up the way every full demo station is set up. */
    private Station create(DemoStationProfile profile) {
        var station = stationRepository.create(profile.name(), profile.uid());
        stationRepository.updateTimezone(station.id(), "Europe/Berlin");
        stationRepository.updateLocale(station.id(), "de-DE");
        stationRepository.updatePublicSlug(station.id(), profile.publicSlug());
        stationRepository.updatePublicCalendarEnabled(station.id(), true);
        stationRepository.updatePublicPagesEnabled(station.id(), true);
        stationRepository.updatePublicWaitlistEnabled(station.id(), true);
        stationRepository.updatePublicBlogEnabled(station.id(), true);
        stationRepository.updatePublicKbMode(station.id(), PublicKbMode.ALLOW_ALL);
        stationRepository.updateDiscoverySettings(
                station.id(), DiscoveryVisibility.PUBLIC, profile.name() + " - Übungen, Wettbewerbe und mehr", true);
        try {
            var logoBytes = Files.readAllBytes(Path.of("templates", "graphics", "logo.png"));
            stationRepository.updateLogo(station.id(), logoBytes, "image/png");
        } catch (IOException e) {
            log.warn("Demo: Could not load logo.png", e);
        }
        return station;
    }
}
