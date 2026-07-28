/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Closes the primary station's setup wizard and adds a second station that is deliberately left
 * un-set-up, so the first-login setup flow can be walked through in the demo.
 */
@Singleton
public class DemoSetupSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoSetupSeeder.class);
    private final StationRepository stationRepository;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public DemoSetupSeeder(
            StationRepository stationRepository,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository) {
        this.stationRepository = stationRepository;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public int order() {
        return SETUP_STATE;
    }

    @Override
    public void seed(DemoSeederContext context) {
        stationRepository.markSetupComplete(context.stationId());
        log.info("Demo: Marked Musterstadt station setup complete");

        var freshStation = stationRepository.create(
                "Wache Neuhausen (Einrichtung)", UUID.fromString("00000000-0000-4000-a000-000000000002"));
        stationRepository.updateTimezone(freshStation.id(), "Europe/Berlin");
        stationRepository.updateLocale(freshStation.id(), "de-DE");

        var freshAdminAccount = accountRepository.create("setup-admin@ember.local", "Setup", "Admin", true);
        accountRepository.setUid(freshAdminAccount.id(), DemoUids.account("setup-admin@ember.local"));
        accountRepository.createCredential(freshAdminAccount.id(), context.passwordHash());

        var freshAdminMember = stationMemberRepository.create(freshStation.id(), freshAdminAccount.id());
        stationMemberRepository.setUserType(freshAdminMember.id(), StationUserType.MANAGER);
        var adminPerm = stationMemberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        stationMemberRepository.grantPermission(freshAdminMember.id(), adminPerm.id());
        stationRepository.setOwner(freshStation.id(), freshAdminMember.id());

        log.info(
                "Demo: Created un-setup demo station — login: setup-admin@ember.local / {} (will land on /station/setup)",
                PASSWORD);
    }
}
