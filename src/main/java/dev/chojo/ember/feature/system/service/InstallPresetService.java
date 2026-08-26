/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.util.LeakyBucket;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Holds the answers somebody clicked together on the install page, under a code short enough to
 * read out or type.
 *
 * <p>Kept in memory rather than in the database on purpose. A preset is worth nothing an hour after
 * it was made, there is no reason for it to outlive a restart, and an endpoint anybody may write to
 * has no business filling a table. What it can fill is this cache, which is why it is bounded on
 * both size and age.
 */
@Singleton
public class InstallPresetService {
    private static final Logger log = LoggerFactory.getLogger(InstallPresetService.class);

    /** Long enough that guessing is pointless, short enough to type from a phone screen. */
    private static final int CODE_LENGTH = 6;

    /** No vowels, no look-alikes: a code that is read out loud should survive the journey. */
    private static final String ALPHABET = "23456789BCDFGHJKLMNPQRSTVWXZ";

    private static final Duration TTL = Duration.ofHours(2);
    private static final long MAX_ENTRIES = 5_000L;

    /**
     * The answers a preset may carry. Everything else is dropped rather than stored: the script
     * reads what comes back into its own environment, so what is kept here decides what a stranger
     * could put into somebody else's shell.
     */
    private static final Set<String> ALLOWED = Set.of(
            "EMBER_MODE",
            "EMBER_HOST",
            "EMBER_PORT",
            "EMBER_BIND",
            "EMBER_TAG",
            "EMBER_TRAEFIK_NETWORK",
            "EMBER_TRAEFIK_ENTRYPOINT",
            "EMBER_TRAEFIK_RESOLVER",
            "EMBER_DB_MODE",
            "EMBER_DB_HOST",
            "EMBER_DB_PORT",
            "EMBER_DB_NAME",
            "EMBER_DB_USER",
            "EMBER_DB_SCHEMA",
            "EMBER_DB_NETWORK",
            "EMBER_EXPOSE_DB",
            "EMBER_DB_VOLUME",
            "EMBER_CONFIG_DIR",
            "EMBER_DATA_DIR",
            "EMBER_TRUSTED_PROXIES",
            "EMBER_CLOUDFLARE");

    /** What a value may look like. Anything a shell would read as more than a value is refused. */
    private static final String VALUE_PATTERN = "[A-Za-z0-9._:/,@ -]{0,200}";

    /**
     * Lookups admitted from one address before it has to wait.
     *
     * <p>A code is six characters and the store holds thousands at a time, so guessing one is not
     * hopeless for somebody willing to try all day. Typing a code in takes one lookup, or a handful
     * with fingers involved; hunting for someone else's takes many more than this allows.
     */
    public static final int LOOKUP_CAPACITY = 20;

    private static final Duration LOOKUP_REFILL = Duration.ofMinutes(3);

    private final SecureRandom random = new SecureRandom();

    private final LeakyBucket lookups =
            new LeakyBucket(LOOKUP_CAPACITY, LOOKUP_REFILL, Duration.ofHours(1), Clock.systemUTC());

    private final Cache<String, Map<String, String>> presets =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(MAX_ENTRIES).build();

    /**
     * Keeps a set of answers and returns the code that fetches it back.
     *
     * @param answers what was clicked together, of which only the known keys are kept
     * @return the code to hand to the installer
     */
    public String store(Map<String, String> answers) {
        var kept = new LinkedHashMap<String, String>();
        for (var entry : answers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!ALLOWED.contains(key) || value == null || value.isBlank()) continue;
            if (!value.matches(VALUE_PATTERN)) continue;
            kept.put(key, value);
        }
        String code = generateCode();
        presets.put(code, Map.copyOf(kept));
        log.info("Install preset stored with {} of {} answer(s), good for {}", kept.size(), answers.size(), TTL);
        return code;
    }

    /**
     * @param clientIp the address asking for a preset
     * @return empty when the lookup is allowed, or the seconds until the next refill
     */
    public Optional<Long> tryLookup(String clientIp) {
        return lookups.tryAcquire(clientIp);
    }

    /**
     * @param code the code from the install page, in any case and with any spacing
     * @return the answers it stands for, or empty once it has expired or never existed
     */
    public Optional<Map<String, String>> find(String code) {
        if (code == null) return Optional.empty();
        var preset = Optional.ofNullable(presets.getIfPresent(normalize(code)));
        if (preset.isEmpty()) log.info("Install preset asked for under a code that has expired or never existed");
        return preset;
    }

    /** How long a code lasts, so the page can say it rather than leave somebody guessing. */
    public Duration lifetime() {
        return TTL;
    }

    private String generateCode() {
        var code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    private static String normalize(String code) {
        return code.trim().replace("-", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
    }
}
