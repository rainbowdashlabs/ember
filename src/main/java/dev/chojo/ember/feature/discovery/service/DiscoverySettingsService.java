/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Thin wrapper around {@link ApplicationSettingRepository} for discovery-specific knobs.
 *
 * <ul>
 *   <li>{@code discovery_enabled} (bool, default true) — kill switch for outbound pings and
 *       the public stations endpoint.</li>
 *   <li>{@code discovery_max_depth} (int, 0..{@value #MAX_DEPTH}, default {@value
 *       #DEFAULT_MAX_DEPTH}) — fan-out depth attached to outbound pings.</li>
 *   <li>{@code discovery_ping_interval_minutes} (int, default {@value
 *       #DEFAULT_PING_INTERVAL_MINUTES}) — cadence of {@code DiscoveryPingScheduler}.</li>
 * </ul>
 */
@Singleton
public class DiscoverySettingsService {

    public static final int MAX_DEPTH = 10;
    public static final int DEFAULT_MAX_DEPTH = 2;
    public static final int MIN_PING_INTERVAL_MINUTES = 60;
    public static final int DEFAULT_PING_INTERVAL_MINUTES = 60;

    private static final String KEY_ENABLED = "discovery_enabled";
    private static final String KEY_MAX_DEPTH = "discovery_max_depth";
    private static final String KEY_PING_INTERVAL = "discovery_ping_interval_minutes";

    private final ApplicationSettingRepository settings;

    @Inject
    public DiscoverySettingsService(ApplicationSettingRepository settings) {
        this.settings = settings;
    }

    public boolean isEnabled() {
        return settings.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        settings.setBoolean(KEY_ENABLED, enabled);
    }

    public int maxDepth() {
        int raw = settings.get(KEY_MAX_DEPTH).map(Integer::parseInt).orElse(DEFAULT_MAX_DEPTH);
        return clampDepth(raw);
    }

    public void setMaxDepth(int depth) {
        settings.set(KEY_MAX_DEPTH, Integer.toString(clampDepth(depth)));
    }

    public int pingIntervalMinutes() {
        int raw = settings.get(KEY_PING_INTERVAL).map(Integer::parseInt).orElse(DEFAULT_PING_INTERVAL_MINUTES);
        return Math.max(MIN_PING_INTERVAL_MINUTES, raw);
    }

    public void setPingIntervalMinutes(int minutes) {
        settings.set(KEY_PING_INTERVAL, Integer.toString(Math.max(MIN_PING_INTERVAL_MINUTES, minutes)));
    }

    /**
     * Clamps a depth value into {@code [0, MAX_DEPTH]}.
     */
    public static int clampDepth(int depth) {
        if (depth < 0) return 0;
        return Math.min(depth, MAX_DEPTH);
    }
}
