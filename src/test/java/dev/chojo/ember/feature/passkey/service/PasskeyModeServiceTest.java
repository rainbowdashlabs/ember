/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PasskeyModeServiceTest {

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static PasskeySettings settings(String mode) throws Exception {
        var settings = new PasskeySettings();
        setField(settings, "mode", mode);
        return settings;
    }

    private static Demo demo(boolean enabled, boolean dev) throws Exception {
        var demo = new Demo();
        setField(demo, "enabled", enabled);
        setField(demo, "dev", dev);
        return demo;
    }

    private static RelyingParties parties(boolean localhostFallback) {
        return new RelyingParties(null, null, localhostFallback);
    }

    @Test
    void configuredModeIsInForceOrdinarily() throws Exception {
        var service = new PasskeyModeService(settings("ENCOURAGED"), demo(false, false), parties(false));
        assertEquals(PasskeySettings.Mode.ENCOURAGED, service.effectiveMode());
    }

    @Test
    void unknownModeReadsAsOptional() throws Exception {
        var service = new PasskeyModeService(settings("garbage"), demo(false, false), parties(false));
        assertEquals(PasskeySettings.Mode.OPTIONAL, service.effectiveMode());
    }

    @Test
    void publicDemoIsHeldAtOff() throws Exception {
        var service = new PasskeyModeService(settings("PASSWORDLESS"), demo(true, false), parties(false));
        assertEquals(PasskeySettings.Mode.OFF, service.effectiveMode());
    }

    @Test
    void devRunsAreNotHeldBack() throws Exception {
        var service = new PasskeyModeService(settings("PREFERRED"), demo(false, true), parties(false));
        assertEquals(PasskeySettings.Mode.PREFERRED, service.effectiveMode());
    }

    @Test
    void localhostFallbackIsHeldAtOff() throws Exception {
        var service = new PasskeyModeService(settings("ENCOURAGED"), demo(false, false), parties(true));
        assertEquals(PasskeySettings.Mode.OFF, service.effectiveMode());
    }
}
