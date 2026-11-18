/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.maps.entity.GeocodingProvider;
import dev.chojo.ember.feature.maps.entity.MapsGeocodingConfig;
import dev.chojo.ember.feature.maps.service.MapsConfigService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sets the instance and station level switches the demo relies on: a personal feed token for the
 * administrator, an open public knowledge base, closed station self-registration, and the address search on the
 * public Photon server, so places of appointments and station addresses can be found on the map.
 */
@Singleton
public class DemoSettingsSeeder implements DemoPerStationSeeder {
    private final FeedTokenService feedTokenService;
    private final StationRepository stationRepository;
    private final ApplicationSettingRepository applicationSettingRepository;
    private final MapsConfigService mapsConfig;

    @Inject
    public DemoSettingsSeeder(
            FeedTokenService feedTokenService,
            StationRepository stationRepository,
            ApplicationSettingRepository applicationSettingRepository,
            MapsConfigService mapsConfig) {
        this.feedTokenService = feedTokenService;
        this.stationRepository = stationRepository;
        this.applicationSettingRepository = applicationSettingRepository;
        this.mapsConfig = mapsConfig;
    }

    @Override
    public int order() {
        return MODULES;
    }

    /**
     * Seeds the station's feed token and public knowledge base mode. Station registration and the address search
     * are instance settings, written alike for every station because the value is the same.
     */
    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        feedTokenService.getOrCreate(station.adminMember().id());
        stationRepository.updatePublicKbMode(station.stationId(), PublicKbMode.ALLOW_ALL);
        applicationSettingRepository.setBoolean("station_registration_enabled", false);
        mapsConfig.updateGeocodingConfig(new MapsGeocodingConfig(GeocodingProvider.PHOTON, ""));
    }
}
