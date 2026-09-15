/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Changelog;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an instance says for itself when it comes up on a version it was not on before.
 *
 * <p>The mark it leaves is the whole of the mechanism: said once per version rather than once per
 * start, silent on a fresh installation, silent on a step back, and silent about a version the
 * changelog does not name.
 */
class ChangelogAnnouncerTest extends RepositoryTestBase {

    private static NewsService newsService;
    private static ChangelogService changelog;
    private static String knownVersion;
    private static Station station;
    private static Account managerAccount;
    private static Account memberAccount;
    private static StationMember manager;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        newsService = new NewsService(
                newsRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                restrictionService,
                new DomainEventBus(Set.of()),
                stationMemberRepo,
                memberLookupService,
                accountRepo);
        changelog = new ChangelogService();
        knownVersion = changelog.all("de").getFirst().version();

        station = stationRepo.create("ChangelogStation");
        managerAccount = accountRepo.create("changelog-manager@test.com", "Change", "Manager");
        memberAccount = accountRepo.create("changelog-member@test.com", "Change", "Member");
        manager = stationMemberRepo.create(station.id(), managerAccount.id());
        member = stationMemberRepo.create(station.id(), memberAccount.id());
        stationMemberRepo.setUserType(manager.id(), StationUserType.MANAGER);
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
    }

    @AfterAll
    static void cleanup() {
        clearSystemEntries();
        stationRepo.delete(station.id());
        accountRepo.delete(managerAccount.id());
        accountRepo.delete(memberAccount.id());
    }

    @BeforeEach
    void forgetWhatWasAnnounced() {
        applicationSettingRepo.set(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY, "");
        clearSystemEntries();
    }

    /**
     * An announcer on a version of this test's choosing, since what a build says it is comes from
     * the jar. Switched off, the operator's setting is the only thing that differs.
     */
    private ChangelogAnnouncer announcerOn(String version, boolean enabled) {
        var config = enabled
                ? new Changelog()
                : new Changelog() {
                    @Override
                    public boolean announceUpdates() {
                        return false;
                    }
                };
        return new ChangelogAnnouncer(config, changelog, newsService, applicationSettingRepo, version);
    }

    private static void clearSystemEntries() {
        newsService.findSystem(0, 100).stream().map(News::id).forEach(newsRepo::delete);
    }

    private long systemEntriesNaming(String version) {
        return newsService.findSystem(0, 100).stream()
                .filter(entry -> entry.title().contains(version))
                .count();
    }

    /** A fresh installation has not been updated, and would otherwise open with its whole history. */
    @Test
    void aFirstStartWritesNothingAndRemembersWhereItStands() {
        var announcer = announcerOn(knownVersion, true);

        assertEquals(Optional.empty(), announcer.announce());
        assertEquals(Optional.of(knownVersion), applicationSettingRepo.get(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY));
        assertEquals(0, systemEntriesNaming(knownVersion));
    }

    /** The point of the mark: said once per version, however often the process comes up. */
    @Test
    void anUpdateIsAnnouncedOnceAndNotAgainOnTheNextStart() {
        applicationSettingRepo.set(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY, "0.0.1");
        var announcer = announcerOn(knownVersion, true);

        assertEquals(Optional.of(knownVersion), announcer.announce());
        assertEquals(1, systemEntriesNaming(knownVersion));

        assertEquals(Optional.empty(), announcer.announce(), "a restart is not an update");
        assertEquals(1, systemEntriesNaming(knownVersion));
    }

    /** It is addressed to the people who run the stations and to nobody else. */
    @Test
    void theEntryReachesAManagerAndNotAnOrdinaryMember() {
        applicationSettingRepo.set(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY, "0.0.1");
        announcerOn(knownVersion, true).announce();

        var entry = newsService.findSystem(0, 100).stream()
                .filter(news -> news.title().contains(knownVersion))
                .findFirst()
                .orElseThrow();

        assertTrue(
                restrictionService.checkRestriction(RestrictionType.NEWS, entry.id(), manager.id(), Set.of()),
                "whoever runs the station is told");
        assertFalse(
                restrictionService.checkRestriction(RestrictionType.NEWS, entry.id(), member.id(), Set.of()),
                "and nobody else is");
    }

    @Test
    void theEntryCarriesTheVersionsOwnNotesAndTheWayToTheRest() {
        applicationSettingRepo.set(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY, "0.0.1");
        announcerOn(knownVersion, true).announce();

        var entry = newsService.findSystem(0, 100).stream()
                .filter(news -> news.title().contains(knownVersion))
                .findFirst()
                .orElseThrow();

        String notes = changelog.forVersion("de", knownVersion).orElseThrow().body();
        assertTrue(entry.contentMarkdown().startsWith(notes), "the body is that version's own notes");
        assertTrue(entry.contentMarkdown().contains("/patch-notes"), "and the way to everything before them");
    }

    /** A step back is an operator's deliberate act and needs no telling. */
    @Test
    void aStepBackIsWrittenDownInSilence() {
        applicationSettingRepo.set(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY, "999.0.0");
        var announcer = announcerOn(knownVersion, true);

        assertEquals(Optional.empty(), announcer.announce());
        assertEquals(0, systemEntriesNaming(knownVersion));
        assertEquals(
                Optional.of(knownVersion),
                applicationSettingRepo.get(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY),
                "where the instance now stands is written down either way");
    }

    /** A development build has no section, and an entry with an empty body says less than nothing. */
    @Test
    void aVersionTheChangelogDoesNotNameIsNotAnnounced() {
        applicationSettingRepo.set(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY, "0.0.1");
        var announcer = announcerOn("999.999.999", true);

        assertEquals(Optional.empty(), announcer.announce());
        assertEquals(0, systemEntriesNaming("999.999.999"));
        assertEquals(Optional.of("999.999.999"), applicationSettingRepo.get(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY));
    }

    @Test
    void anOperatorWhoSwitchesItOffIsNotWrittenTo() {
        applicationSettingRepo.set(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY, "0.0.1");

        assertEquals(Optional.empty(), announcerOn(knownVersion, false).announce());
        assertEquals(0, systemEntriesNaming(knownVersion));
        assertEquals(
                Optional.of("0.0.1"),
                applicationSettingRepo.get(ChangelogAnnouncer.ANNOUNCED_VERSION_KEY),
                "switched off, it does not even move the mark");
        assertFalse(knownVersion.isEmpty());
    }
}
