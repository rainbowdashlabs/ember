/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeder for demo profile pictures and station logo.
 */
@Singleton
public class DemoMediaSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoMediaSeeder.class);
    private static final Path AVATAR_CACHE_DIR = Path.of("data", "demo-avatars");

    private final ImageService imageService;
    private final StationService stationService;
    private final AccountRepository accountRepository;

    @Inject
    public DemoMediaSeeder(
            ImageService imageService, StationService stationService, AccountRepository accountRepository) {
        this.imageService = imageService;
        this.stationService = stationService;
        this.accountRepository = accountRepository;
    }

    public void seedProfilePictures(
            int stationId,
            StationMember admin,
            List<StationMember> betreuer,
            List<StationMember> eltern,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten) {
        // Ensure cache directory exists
        try {
            Files.createDirectories(AVATAR_CACHE_DIR);
        } catch (IOException e) {
            log.warn("Failed to create avatar cache directory: {}", e.getMessage());
        }

        // Assign DiceBear avatars to all members
        var allMembers = new ArrayList<StationMember>();
        allMembers.add(admin);
        allMembers.addAll(betreuer);
        allMembers.addAll(eltern);
        allMembers.addAll(anfaenger);
        allMembers.addAll(fortgeschritten);

        try (var httpClient = HttpClient.newHttpClient()) {
            for (var member : allMembers) {
                String memberKey = member.uid().toString();
                if (imageService.exists(ImageCategory.AVATARS, memberKey)) continue;
                try {
                    String seed = buildSeed(member);
                    byte[] data = fetchAvatar(httpClient, seed);
                    imageService.store(ImageCategory.AVATARS, memberKey, data, "image/png");
                } catch (Exception e) {
                    log.warn("Failed to set demo avatar for member {}: {}", memberKey, e.getMessage());
                }
            }
        }

        // Set station logo (skip if already exists)
        if (stationService.getLogo(stationId).isEmpty()) {
            try {
                byte[] logoData = loadDemoResource("demo/avatars/station_logo.png");
                stationService.setLogo(stationId, logoData, "image/png");
            } catch (Exception e) {
                log.warn("Failed to set demo station logo: {}", e.getMessage());
            }
        }
    }

    private String buildSeed(StationMember member) {
        if (member.accountId() != null) {
            var account = accountRepository.findById(member.accountId());
            if (account.isPresent()) {
                return account.get().firstName() + "+" + account.get().lastName();
            }
        }
        return "member-" + member.id();
    }

    private byte[] fetchAvatar(HttpClient httpClient, String seed) throws IOException, InterruptedException {
        Path cacheFile = AVATAR_CACHE_DIR.resolve(seed + ".png");
        if (Files.exists(cacheFile)) {
            return Files.readAllBytes(cacheFile);
        }

        String url = "https://api.dicebear.com/9.x/adventurer/png?seed=" + seed + "&size=256";
        var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("DiceBear API returned status " + response.statusCode());
        }
        byte[] data = response.body();
        Files.write(cacheFile, data);
        return data;
    }

    private byte[] loadDemoResource(String path) throws IOException {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new FileNotFoundException("Demo resource not found: " + path);
            return is.readAllBytes();
        }
    }
}
