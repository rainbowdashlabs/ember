/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Changelog;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Tells the managers of every station that their instance has been updated, and what that brought.
 *
 * <p>An update is something that happens to people who did not ask for it and often did not do it:
 * the evening after, a screen is different and nobody said why. One entry in the news, written by
 * the instance itself, is what turns that into something that was announced.
 *
 * <p>It is said once per version, not once per start, so the version that was announced is written
 * down. What that mark makes possible is the whole of the behaviour worth stating:
 *
 * <ul>
 *   <li>The same version again is a restart, and a restart is not an update.</li>
 *   <li>No mark at all is a fresh installation, which has not been updated either. It is written
 *       down in silence, so that a new instance does not open with the history of releases it was
 *       born with.</li>
 *   <li>An older version is a deliberate step back by an operator, who does not need every station
 *       told about it.</li>
 *   <li>A version the changelog does not name is a development build. An entry with an empty body
 *       says less than nothing.</li>
 * </ul>
 */
@Singleton
public class ChangelogAnnouncer {
    private static final Logger log = LoggerFactory.getLogger(ChangelogAnnouncer.class);

    /** The version the last entry was written for, or absent where none ever was. */
    public static final String ANNOUNCED_VERSION_KEY = "changelog_announced_version";

    private final Changelog config;
    private final ChangelogService changelog;
    private final NewsService newsService;
    private final ApplicationSettingRepository settings;
    private final String currentVersion;

    @Inject
    public ChangelogAnnouncer(
            Changelog config,
            ChangelogService changelog,
            NewsService newsService,
            ApplicationSettingRepository settings,
            UpdateCheckService updateCheckService) {
        this(
                config,
                changelog,
                newsService,
                settings,
                updateCheckService.status().currentVersion());
    }

    /**
     * Lets a test say what this instance is running, which is otherwise baked into the jar.
     *
     * @param currentVersion the version this instance runs
     */
    ChangelogAnnouncer(
            Changelog config,
            ChangelogService changelog,
            NewsService newsService,
            ApplicationSettingRepository settings,
            String currentVersion) {
        this.config = config;
        this.changelog = changelog;
        this.newsService = newsService;
        this.settings = settings;
        this.currentVersion = ChangelogService.normalise(currentVersion);
    }

    /**
     * Says what this start has to say, if anything, and writes down where the instance now stands.
     *
     * @return the entry that was written, or empty where there was nothing to say
     */
    public Optional<String> announce() {
        if (!config.announceUpdates()) return Optional.empty();
        if (currentVersion.isEmpty()) return Optional.empty();

        var announced = settings.get(ANNOUNCED_VERSION_KEY).map(ChangelogService::normalise);
        if (announced.filter(version -> !version.isEmpty()).isEmpty()) {
            markAnnounced();
            log.info("First start at {}; nothing to announce", currentVersion);
            return Optional.empty();
        }
        if (ChangelogService.compareVersions(currentVersion, announced.get()) <= 0) {
            markAnnounced();
            return Optional.empty();
        }

        var entry = changelog.forVersion("de", currentVersion);
        if (entry.isEmpty()) {
            markAnnounced();
            log.info("Updated to {}, which the changelog does not name; nothing announced", currentVersion);
            return Optional.empty();
        }

        newsService.createSystem(
                "Ember " + currentVersion, body(entry.get().body()), List.of(StationUserType.MANAGER), true, true);
        markAnnounced();
        log.info("Announced the update to {} in the news of every station", currentVersion);
        return Optional.of(currentVersion);
    }

    /**
     * What the entry says: the version's own notes, and the way to everything before them.
     */
    private String body(String notes) {
        return notes + "\n\n[Das vollständige Änderungsprotokoll](/patch-notes)";
    }

    private void markAnnounced() {
        settings.set(ANNOUNCED_VERSION_KEY, currentVersion);
    }
}
