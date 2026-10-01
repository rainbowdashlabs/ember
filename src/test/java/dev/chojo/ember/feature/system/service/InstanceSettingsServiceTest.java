/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.UnwritableConf;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.system.service.InstanceSettingsService.ApplicationSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstanceSettingsServiceTest {
    @TempDir
    Path configDirectory;

    @TempDir
    Path templates;

    private ApplicationSettingRepository settings;
    private InstanceSettingsService service;

    @BeforeEach
    void setup() throws IOException {
        Files.createDirectories(templates.resolve("en"));
        Files.createDirectories(templates.resolve("de"));
        settings = mock(ApplicationSettingRepository.class);
        when(settings.getBoolean("station_registration_enabled", true)).thenReturn(true);
        when(settings.getBoolean("force_pride_flag", false)).thenReturn(false);
        when(settings.defaultMailLocale()).thenReturn("en");
        var conf = new Conf(configDirectory);
        service = new InstanceSettingsService(settings, conf, new ConfigChanges(conf), templates);
    }

    private static ApplicationSettings request(String theme, String feel, String mailLocale) {
        return new ApplicationSettings(false, theme, feel, true, true, mailLocale, List.of());
    }

    @Test
    void theSettingsOfferTheLanguagesThereAreTemplatesFor() {
        var answer = service.settings();

        assertEquals(List.of("de", "en"), answer.availableMailLocales());
        assertTrue(answer.stationRegistrationEnabled());
        assertEquals("ember", answer.instanceDefaultTheme());
        assertEquals("ROUNDED", answer.instanceDefaultFeel());
    }

    @Test
    void withoutTemplatesOnlyEnglishIsOffered() {
        var conf = new Conf(configDirectory);
        var bare = new InstanceSettingsService(settings, conf, new ConfigChanges(conf), templates.resolve("missing"));

        assertEquals(List.of("en"), bare.settings().availableMailLocales());
    }

    @Test
    void anUpdateWritesTheLookToTheFileAndTheRestToTheDatabase() {
        service.update(request("forest", "CORNERS", "de"));

        var theming = new Conf(configDirectory).main().theming();
        assertEquals("forest", theming.defaultTheme());
        assertEquals(ThemeFeel.CORNERS, theming.defaultFeel());
        assertTrue(theming.lockFeel());
        verify(settings).setBoolean("station_registration_enabled", false);
        verify(settings).setBoolean("force_pride_flag", true);
        verify(settings).set(ApplicationSettingRepository.DEFAULT_MAIL_LOCALE, "de");
    }

    @Test
    void anEmptyThemeOrFeelKeepsTheOneInForceAndNoLanguageKeepsTheLanguage() {
        service.update(request(null, null, " "));

        var theming = new Conf(configDirectory).main().theming();
        assertEquals("ember", theming.defaultTheme());
        assertEquals(ThemeFeel.ROUNDED, theming.defaultFeel());
        verify(settings, never()).set(eq(ApplicationSettingRepository.DEFAULT_MAIL_LOCALE), anyString());
    }

    @Test
    void aLanguageWithoutTemplatesChangesNothing() {
        var refused = assertThrows(RefusalResponse.class, () -> service.update(request("forest", "CORNERS", "fr")));

        assertEquals(SystemRefusal.NO_MAIL_WRITTEN_IN_THAT_LANGUAGE, refused.refusal());
        assertEquals("ember", service.settings().instanceDefaultTheme());
        verify(settings, never()).setBoolean(anyString(), anyBoolean());
    }

    @Test
    void anUnknownLookChangesNothing() {
        var refused = assertThrows(RefusalResponse.class, () -> service.update(request("forest", "WOBBLY", "de")));

        assertEquals(SystemRefusal.THEME_FEEL_UNKNOWN, refused.refusal());
        assertEquals("ember", service.settings().instanceDefaultTheme());
        verify(settings, never()).setBoolean(anyString(), anyBoolean());
    }

    @Test
    void whenTheFileCannotBeWrittenTheDatabaseIsNotTouchedEither() {
        var unwritable = UnwritableConf.create();
        var failing = new InstanceSettingsService(settings, unwritable, new ConfigChanges(unwritable), templates);

        var refused = assertThrows(RefusalResponse.class, () -> failing.update(request("forest", "CORNERS", "de")));

        assertEquals(SystemRefusal.SETTINGS_NOT_SAVED, refused.refusal());
        assertEquals("ember", failing.settings().instanceDefaultTheme());
        assertFalse(failing.settings().instanceLockFeel());
        verify(settings, never()).setBoolean(anyString(), anyBoolean());
    }

    @Test
    void thePublicLookCarriesThePrideFlag() {
        when(settings.getBoolean("force_pride_flag", false)).thenReturn(true);

        var look = service.publicTheme();

        assertTrue(look.forcePrideFlag());
        assertEquals("ember", look.defaultTheme());
        assertEquals("ROUNDED", look.defaultFeel());
        assertFalse(look.lockFeel());
    }
}
