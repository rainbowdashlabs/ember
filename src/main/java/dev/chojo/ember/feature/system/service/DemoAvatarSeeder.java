/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AvatarService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Gives every account in the demo a face.
 *
 * <p>Runs last, after every other band has made whoever it makes. Anything earlier leaves the people
 * created after it without a picture, which is what happened to the association's own members and to
 * the administrator of the un-set-up station: the pictures were seeded halfway through the run.
 *
 * <p>Nobody is left out. A drawn picture is fetched where the machine can reach the internet and
 * cached on disk, and where it cannot, one is painted here from the initials. A demo whose lists show
 * a grey circle on half their rows says nothing about how Ember looks in use.
 */
@Singleton
public class DemoAvatarSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoAvatarSeeder.class);
    private static final Path AVATAR_CACHE_DIR = Path.of("data", "demo-avatars");
    private static final int SIZE = 256;

    /**
     * The colours a painted picture picks from, chosen to carry white initials at any size.
     */
    private static final Color[] BACKGROUNDS = {
        new Color(0xC7, 0x11, 0x00),
        new Color(0xFF, 0x64, 0x21),
        new Color(0x36, 0x94, 0xFF),
        new Color(0x00, 0x8A, 0x53),
        new Color(0x6B, 0x4E, 0xC7),
        new Color(0xB8, 0x6E, 0x00),
    };

    private final AvatarService avatarService;
    private final AccountRepository accountRepository;

    @Inject
    public DemoAvatarSeeder(AvatarService avatarService, AccountRepository accountRepository) {
        this.avatarService = avatarService;
        this.accountRepository = accountRepository;
    }

    @Override
    public int order() {
        return PORTRAITS;
    }

    @Override
    public void seed(DemoRunContext run) {
        int given = seedMissingAvatars();
        log.info("Demo: Gave {} account(s) a profile picture", given);
    }

    /**
     * Gives a picture to every account that has none.
     *
     * @return how many were given one
     */
    public int seedMissingAvatars() {
        try {
            Files.createDirectories(AVATAR_CACHE_DIR);
        } catch (IOException e) {
            log.warn("Failed to create avatar cache directory: {}", e.getMessage());
        }

        int given = 0;
        try (var httpClient = HttpClient.newHttpClient()) {
            for (var account : accountRepository.findAll()) {
                if (account.uid() == null) continue;
                if (avatarService.exists(account.uid())) continue;
                try {
                    avatarService.store(account.uid(), pictureFor(httpClient, account), "image/png");
                    given++;
                } catch (Exception e) {
                    log.warn("Failed to set demo avatar for account {}: {}", account.id(), e.getMessage());
                }
            }
        }
        return given;
    }

    /**
     * The cached picture, else a fetched one, else one painted here. Only the last of the three
     * cannot fail, which is why it is the one that answers when the others do.
     */
    private byte[] pictureFor(HttpClient httpClient, Account account) throws IOException {
        String seed = buildSeed(account);
        Path cacheFile = AVATAR_CACHE_DIR.resolve(seed + ".png");
        if (Files.exists(cacheFile)) {
            return Files.readAllBytes(cacheFile);
        }
        try {
            byte[] fetched = fetchAvatar(httpClient, seed);
            Files.write(cacheFile, fetched);
            return fetched;
        } catch (Exception e) {
            log.debug("Drawing an avatar for account {} rather than fetching one: {}", account.id(), e.getMessage());
            return paintInitials(account);
        }
    }

    private String buildSeed(Account account) {
        String first = account.firstName() != null ? account.firstName() : "";
        String last = account.lastName() != null ? account.lastName() : "";
        String combined = (first + "+" + last).trim();
        return combined.isBlank() ? "account-" + account.id() : combined;
    }

    private byte[] fetchAvatar(HttpClient httpClient, String seed) throws IOException, InterruptedException {
        String url = "https://api.dicebear.com/9.x/adventurer/png?seed=" + seed + "&size=" + SIZE;
        var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("DiceBear API returned status " + response.statusCode());
        }
        return response.body();
    }

    /**
     * The initials on a coloured disc, which needs nothing but the account itself. The colour follows
     * from the name, so the same person is the same colour on every run.
     */
    private byte[] paintInitials(Account account) throws IOException {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            String initials = initialsOf(account);
            graphics.setColor(BACKGROUNDS[Math.floorMod(initials.hashCode() + account.id(), BACKGROUNDS.length)]);
            graphics.fillRect(0, 0, SIZE, SIZE);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, SIZE / 2));
            var metrics = graphics.getFontMetrics();
            int x = (SIZE - metrics.stringWidth(initials)) / 2;
            int y = (SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
            graphics.drawString(initials, x, y);
        } finally {
            graphics.dispose();
        }
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String initialsOf(Account account) {
        String first = letterOf(account.firstName());
        String last = letterOf(account.lastName());
        String initials = (first + last).trim();
        if (!initials.isEmpty()) return initials.toUpperCase(Locale.ROOT);
        String fromAddress = letterOf(account.email());
        return fromAddress.isBlank() ? "?" : fromAddress.toUpperCase(Locale.ROOT);
    }

    private String letterOf(String value) {
        return value == null || value.isBlank() ? "" : value.trim().substring(0, 1);
    }
}
