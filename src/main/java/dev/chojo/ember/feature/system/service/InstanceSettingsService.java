/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.file.elements.Theming;
import dev.chojo.ember.feature.mail.service.MailTemplateRenderer;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * The general settings of the instance: whether new stations may register, the look every
 * station starts from, and the language system mails fall back to.
 *
 * <p>Part of them is kept in the database and part in the configuration file. A change is checked
 * in full before either is written, so a refused request leaves both as they were.
 */
@Singleton
public class InstanceSettingsService {
    private static final String STATION_REGISTRATION_ENABLED = "station_registration_enabled";
    private static final String FORCE_PRIDE_FLAG = "force_pride_flag";

    private final ApplicationSettingRepository settings;
    private final Conf conf;
    private final ConfigChanges changes;
    private final Path templateRoot;

    @Inject
    public InstanceSettingsService(ApplicationSettingRepository settings, Conf conf, ConfigChanges changes) {
        this(settings, conf, changes, Path.of(MailTemplateRenderer.TEMPLATE_ROOT));
    }

    /**
     * The same, reading the mail templates from the given directory.
     *
     * @param templateRoot the directory holding one directory of mail templates per language
     */
    InstanceSettingsService(
            ApplicationSettingRepository settings, Conf conf, ConfigChanges changes, Path templateRoot) {
        this.settings = settings;
        this.conf = conf;
        this.changes = changes;
        this.templateRoot = templateRoot;
    }

    /**
     * Whether a stranger may register a new station on this instance.
     */
    public boolean stationRegistrationEnabled() {
        return settings.getBoolean(STATION_REGISTRATION_ENABLED, true);
    }

    /**
     * The look every visitor starts from, before any station or account chose one.
     */
    public PublicTheme publicTheme() {
        var theming = conf.main().theming();
        return new PublicTheme(
                theming.defaultTheme(), theming.defaultFeel().name(), theming.lockFeel(), forcePrideFlag());
    }

    public ApplicationSettings settings() {
        var theming = conf.main().theming();
        return new ApplicationSettings(
                stationRegistrationEnabled(),
                theming.defaultTheme(),
                theming.defaultFeel().name(),
                theming.lockFeel(),
                forcePrideFlag(),
                settings.defaultMailLocale(),
                availableMailLocales());
    }

    /**
     * Changes the general settings. A theme or feel left empty keeps the one in force.
     */
    public ApplicationSettings update(ApplicationSettings request) {
        String mailLocale = request.defaultMailLocale();
        boolean changesMailLocale = mailLocale != null && !mailLocale.isBlank();
        if (changesMailLocale && !availableMailLocales().contains(mailLocale)) {
            throw SystemRefusal.NO_MAIL_WRITTEN_IN_THAT_LANGUAGE.raise(mailLocale);
        }
        ThemeFeel feel = request.instanceDefaultFeel() == null ? null : feelOf(request.instanceDefaultFeel());
        var theming = conf.main().theming();
        var before = Look.of(theming);
        var after = new Look(
                request.instanceDefaultTheme() == null ? before.theme() : request.instanceDefaultTheme(),
                feel == null ? before.feel() : feel,
                request.instanceLockFeel());
        changes.apply(() -> after.applyTo(theming), () -> before.applyTo(theming));
        settings.setBoolean(STATION_REGISTRATION_ENABLED, request.stationRegistrationEnabled());
        settings.setBoolean(FORCE_PRIDE_FLAG, request.forcePrideFlag());
        if (changesMailLocale) {
            settings.set(ApplicationSettingRepository.DEFAULT_MAIL_LOCALE, mailLocale);
        }
        return settings();
    }

    private static ThemeFeel feelOf(String name) {
        try {
            return ThemeFeel.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw SystemRefusal.THEME_FEEL_UNKNOWN.raise(name);
        }
    }

    private boolean forcePrideFlag() {
        return settings.getBoolean(FORCE_PRIDE_FLAG, false);
    }

    /**
     * The languages this instance can actually write a mail in, one per directory of mail
     * templates. Offering anything else would let an administrator pick a language that silently
     * falls back to English on the first mail sent.
     */
    List<String> availableMailLocales() {
        File[] directories = templateRoot.toFile().listFiles(File::isDirectory);
        if (directories == null) return List.of("en");
        return Arrays.stream(directories).map(File::getName).sorted().toList();
    }

    private record Look(String theme, ThemeFeel feel, boolean lockFeel) {
        static Look of(Theming theming) {
            return new Look(theming.defaultTheme(), theming.defaultFeel(), theming.lockFeel());
        }

        void applyTo(Theming theming) {
            theming.defaultTheme(theme);
            theming.defaultFeel(feel);
            theming.lockFeel(lockFeel);
        }
    }

    /**
     * @param defaultMailLocale    the language system mails use for accounts with no station to
     *                             take one from
     * @param availableMailLocales the languages this instance holds mail templates for; read-only,
     *                             so the client can offer exactly what will work
     */
    public record ApplicationSettings(
            boolean stationRegistrationEnabled,
            String instanceDefaultTheme,
            String instanceDefaultFeel,
            boolean instanceLockFeel,
            boolean forcePrideFlag,
            String defaultMailLocale,
            List<String> availableMailLocales) {}

    public record PublicTheme(String defaultTheme, String defaultFeel, boolean lockFeel, boolean forcePrideFlag) {}
}
