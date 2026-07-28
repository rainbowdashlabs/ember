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
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Creates the demo instance administrator and the primary demo station with all its public
 * surfaces enabled. Every other seeder builds on the station this step publishes.
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
    public void seed(DemoSeederContext context) {
        var admin = accountRepository.create("admin@ember.local", "Admin", "Demo", true);
        accountRepository.setUid(admin.id(), DemoUids.account("admin@ember.local"));
        accountRepository.createCredential(admin.id(), context.passwordHash());
        accountRepository.setInstanceUserType(admin.id(), InstanceUserType.ADMINISTRATOR);
        context.adminAccount(admin);

        var station = stationRepository.create(
                "Jugendfeuerwehr Musterstadt", UUID.fromString("00000000-0000-4000-a000-000000000001"));
        stationRepository.updateTimezone(station.id(), "Europe/Berlin");
        stationRepository.updateLocale(station.id(), "de-DE");
        stationRepository.updatePublicSlug(station.id(), "jugendfeuerwehr-musterstadt");
        stationRepository.updatePublicCalendarEnabled(station.id(), true);
        stationRepository.updatePublicPagesEnabled(station.id(), true);
        stationRepository.updatePublicWaitlistEnabled(station.id(), true);
        stationRepository.updatePublicBlogEnabled(station.id(), true);
        stationRepository.updatePublicKbMode(station.id(), PublicKbMode.ALLOW_ALL);
        stationRepository.updateDiscoverySettings(
                station.id(),
                DiscoveryVisibility.PUBLIC,
                "Jugendfeuerwehr Musterstadt — Übungen, Wettbewerbe und mehr",
                true);
        try {
            var logoBytes = Files.readAllBytes(Path.of("templates", "graphics", "logo.png"));
            stationRepository.updateLogo(station.id(), logoBytes, "image/png");
        } catch (IOException e) {
            log.warn("Demo: Could not load logo.png", e);
        }
        context.station(station);
    }
}
