/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sets the instance and station level switches the demo relies on: a personal feed token for the
 * administrator, an open public knowledge base, and closed station self-registration.
 */
@Singleton
public class DemoSettingsSeeder implements DemoSeeder {
    private final FeedTokenService feedTokenService;
    private final StationRepository stationRepository;
    private final ApplicationSettingRepository applicationSettingRepository;

    @Inject
    public DemoSettingsSeeder(
            FeedTokenService feedTokenService,
            StationRepository stationRepository,
            ApplicationSettingRepository applicationSettingRepository) {
        this.feedTokenService = feedTokenService;
        this.stationRepository = stationRepository;
        this.applicationSettingRepository = applicationSettingRepository;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seed(DemoSeederContext context) {
        feedTokenService.getOrCreate(context.adminMember().id());
        stationRepository.updatePublicKbMode(context.stationId(), PublicKbMode.ALLOW_ALL);
        applicationSettingRepository.setBoolean("station_registration_enabled", false);
    }
}
