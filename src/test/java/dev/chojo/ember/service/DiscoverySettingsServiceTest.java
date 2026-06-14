/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.discovery.service.DiscoverySettingsService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscoverySettingsServiceTest extends RepositoryTestBase {

    private static DiscoverySettingsService service;

    @BeforeAll
    static void init() {
        service = new DiscoverySettingsService(applicationSettingRepo);
    }

    @Test
    void defaultsWhenUnset() {
        assertTrue(service.isEnabled());
        assertEquals(DiscoverySettingsService.DEFAULT_MAX_DEPTH, service.maxDepth());
        assertEquals(DiscoverySettingsService.DEFAULT_PING_INTERVAL_MINUTES, service.pingIntervalMinutes());
    }

    @Test
    void setEnabled() {
        service.setEnabled(false);
        assertFalse(service.isEnabled());
        service.setEnabled(true);
        assertTrue(service.isEnabled());
    }

    @Test
    void setMaxDepthClampsHigh() {
        service.setMaxDepth(99);
        assertEquals(DiscoverySettingsService.MAX_DEPTH, service.maxDepth());
    }

    @Test
    void setMaxDepthClampsNegative() {
        service.setMaxDepth(-5);
        assertEquals(0, service.maxDepth());
    }

    @Test
    void setMaxDepthInRange() {
        service.setMaxDepth(4);
        assertEquals(4, service.maxDepth());
    }

    @Test
    void clampDepthStatic() {
        assertEquals(0, DiscoverySettingsService.clampDepth(-1));
        assertEquals(0, DiscoverySettingsService.clampDepth(0));
        assertEquals(5, DiscoverySettingsService.clampDepth(5));
        assertEquals(DiscoverySettingsService.MAX_DEPTH, DiscoverySettingsService.clampDepth(50));
    }

    @Test
    void setPingIntervalRespectsMinimum() {
        service.setPingIntervalMinutes(0);
        assertEquals(DiscoverySettingsService.MIN_PING_INTERVAL_MINUTES, service.pingIntervalMinutes());
        service.setPingIntervalMinutes(120);
        assertEquals(120, service.pingIntervalMinutes());
    }
}
