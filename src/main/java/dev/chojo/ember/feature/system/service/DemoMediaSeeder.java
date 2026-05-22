/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.service.StationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeder for demo profile pictures and station logo.
 */
@Singleton
public class DemoMediaSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoMediaSeeder.class);

    private final ImageService imageService;
    private final StationService stationService;

    @Inject
    public DemoMediaSeeder(ImageService imageService, StationService stationService) {
        this.imageService = imageService;
        this.stationService = stationService;
    }

    public void seedProfilePictures(
            int stationId,
            StationMember admin,
            List<StationMember> betreuer,
            List<StationMember> eltern,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten) {
        String[] avatarFiles = {"avatar1.png", "avatar2.png", "avatar3.png", "avatar4.png", "avatar5.png"};

        // Assign avatars round-robin to all members
        var allMembers = new ArrayList<StationMember>();
        allMembers.add(admin);
        allMembers.addAll(betreuer);
        allMembers.addAll(eltern);
        allMembers.addAll(anfaenger);
        allMembers.addAll(fortgeschritten);

        for (int i = 0; i < allMembers.size(); i++) {
            String file = avatarFiles[i % avatarFiles.length];
            try {
                byte[] data = loadDemoResource("demo/avatars/" + file);
                imageService.store(
                        ImageCategory.AVATARS, String.valueOf(allMembers.get(i).id()), data, "image/png");
            } catch (Exception e) {
                log.warn(
                        "Failed to set demo avatar for member {}: {}",
                        allMembers.get(i).id(),
                        e.getMessage());
            }
        }

        // Set station logo
        try {
            byte[] logoData = loadDemoResource("demo/avatars/station_logo.png");
            stationService.setLogo(stationId, logoData, "image/png");
        } catch (Exception e) {
            log.warn("Failed to set demo station logo: {}", e.getMessage());
        }
    }

    private byte[] loadDemoResource(String path) throws IOException {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new FileNotFoundException("Demo resource not found: " + path);
            return is.readAllBytes();
        }
    }
}
