/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings of an expiry date, and what a setting left out means.
 */
class ExpirySettingsTest {

    /** A field that says nothing warns a month ahead and reminds the member a month and a week ahead. */
    @Test
    void aFieldThatSaysNothingWorksByTheDefaults() {
        var settings = ProfileFieldConfig.empty().expiry();

        assertEquals(30, settings.warnFromDays());
        assertEquals(List.of(30, 7), settings.reminderDays());
        assertNull(settings.repeatEveryDays());
        assertTrue(settings.remindMember());
        assertFalse(settings.remindManagement());
        assertTrue(settings.remindsAnybody());
    }

    @Test
    void whatAFieldSaysOverridesTheDefaults() {
        var settings = ProfileFieldConfig.parse("""
                {"warnFromDays":90,"reminderDays":[30,90,30],"repeatEveryDays":7,
                 "remindMember":false,"remindManagement":true}""").expiry();

        assertEquals(90, settings.warnFromDays());
        assertEquals(List.of(30, 90), settings.reminderDays(), "sorted, and each day once");
        assertEquals(7, settings.repeatEveryDays());
        assertFalse(settings.remindMember());
        assertTrue(settings.remindManagement());
    }

    @Test
    void aFieldRemindingNobodyIsSaidToRemindNobody() {
        var settings = ProfileFieldConfig.parse("{\"remindMember\":false}").expiry();

        assertFalse(settings.remindsAnybody());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"warnFromDays\":-1}", "{\"reminderDays\":[7,-1]}", "{\"repeatEveryDays\":0}"})
    void daysBackwardsOrARepeatWithoutAGapAreOutOfRange(String json) {
        assertTrue(ExpirySettings.outOfRange(ProfileFieldConfig.parse(json)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"{}", "{\"warnFromDays\":0,\"reminderDays\":[0],\"repeatEveryDays\":1}", "{\"reminderDays\":[]}"
            })
    void theLastValidDayAndADailyRepeatAreInRange(String json) {
        assertFalse(ExpirySettings.outOfRange(ProfileFieldConfig.parse(json)));
    }
}
