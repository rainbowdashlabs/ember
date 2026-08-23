/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.station.service.StationLogoService;
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

/**
 * Seeder for demo profile pictures and station logo.
 *
 * <p>Avatars are keyed by {@code account.uid} (the public per-account UUID) so a
 * single avatar follows the user across every station they belong to. The seeder
 * fetches DiceBear avatars for every account that does not yet have a picture on
 * disk.
 */
@Singleton
public class DemoMediaSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoMediaSeeder.class);
    private static final Path AVATAR_CACHE_DIR = Path.of("data", "demo-avatars");

    private final AvatarService avatarService;
    private final StationService stationService;
    private final StationLogoService logoService;
    private final AccountRepository accountRepository;

    @Inject
    public DemoMediaSeeder(
            AvatarService avatarService,
            StationService stationService,
            StationLogoService logoService,
            AccountRepository accountRepository) {
        this.avatarService = avatarService;
        this.stationService = stationService;
        this.logoService = logoService;
        this.accountRepository = accountRepository;
    }

    /**
     * Seeds a DiceBear avatar (keyed by {@code account.uid}) for every account
     * that does not yet have one, and the demo station logo for {@code stationId}.
     */
    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        seedProfilePictures(station.stationId());
        log.info("Demo: Created profile pictures");
    }

    public void seedProfilePictures(int stationId) {
        try {
            Files.createDirectories(AVATAR_CACHE_DIR);
        } catch (IOException e) {
            log.warn("Failed to create avatar cache directory: {}", e.getMessage());
        }

        try (var httpClient = HttpClient.newHttpClient()) {
            for (var account : accountRepository.findAll()) {
                if (account.uid() == null) continue;
                if (avatarService.exists(account.uid())) continue;
                try {
                    String seed = buildSeed(account);
                    byte[] data = fetchAvatar(httpClient, seed);
                    avatarService.store(account.uid(), data, "image/png");
                } catch (Exception e) {
                    log.warn("Failed to set demo avatar for account {}: {}", account.id(), e.getMessage());
                }
            }
        }

        if (!logoService.exists(stationId)) {
            try {
                byte[] logoData = loadDemoResource("demo/avatars/station_logo.png");
                logoService.store(stationId, logoData, "image/png");
            } catch (Exception e) {
                log.warn("Failed to set demo station logo: {}", e.getMessage());
            }
        }
    }

    private String buildSeed(Account account) {
        String first = account.firstName() != null ? account.firstName() : "";
        String last = account.lastName() != null ? account.lastName() : "";
        String combined = (first + "+" + last).trim();
        return combined.isBlank() ? "account-" + account.id() : combined;
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
