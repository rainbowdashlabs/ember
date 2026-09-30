/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.events.FormPublished;
import dev.chojo.ember.event.events.NewsCreated;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Announcements of new restricted entries reach the members who may open them, and nobody else.
 */
class RestrictedAnnouncementTest extends RepositoryTestBase {
    private static final List<Account> accounts = new ArrayList<>();

    private static Notifier notifications;
    private static Station station;
    private static MemberGroup group;
    private static UserTag tag;
    private static StationMember named;
    private static StationMember inGroup;
    private static StationMember inGroupWithTag;
    private static StationMember outsider;
    private static StationMember pollManager;

    @BeforeAll
    static void setup() {
        notifications = newNotifier();

        station = stationRepo.create("Restricted Announcement Station");
        group = memberGroupRepo.create(station.id(), "Announced Group");
        tag = userTagRepo.create(station.id(), "Announced Tag");
        named = createMember("announce-named@test.com");
        inGroup = createMember("announce-group@test.com");
        inGroupWithTag = createMember("announce-group-tag@test.com");
        outsider = createMember("announce-outsider@test.com");
        pollManager = createMember("announce-manager@test.com");
        memberGroupRepo.addMember(group.id(), inGroup.id());
        memberGroupRepo.addMember(group.id(), inGroupWithTag.id());
        userTagRepo.addMember(tag.id(), inGroupWithTag.id());
        var permission = stationMemberRepo
                .findPermissionByName(StationPermission.POLL_MANAGER)
                .orElseThrow();
        stationMemberRepo.grantPermission(pollManager.id(), permission.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    @Test
    void aFormForNamedMembersAndAGroupWithATagIsAnnouncedOnlyToThoseWhoMayOpenIt() {
        var form = formRepo.create(
                station.id(), "Named and group", "x", false, true, false, null, null, named.id(), FormPurpose.INTERNAL);
        restrictionService.setRestrictions(
                RestrictionType.FORM,
                form.id(),
                new RestrictionSelection(
                        List.of(), List.of(group.id()), List.of(tag.id()), List.of(named.id()), RestrictionMode.AND));
        formRepo.updateRestrictionMode(form.id(), RestrictionMode.AND);

        new FormPublishedHandler(notifications, restrictionService)
                .handle(new FormPublished(station.id(), form.id(), form.title()));

        assertEquals(1, count(named, NotificationType.NEW_FORM), "the named member");
        assertEquals(1, count(inGroupWithTag, NotificationType.NEW_FORM), "in the group and carrying the tag");
        assertEquals(1, count(pollManager, NotificationType.NEW_FORM), "the manager");
        assertEquals(0, count(inGroup, NotificationType.NEW_FORM), "in the group without the tag");
        assertEquals(0, count(outsider, NotificationType.NEW_FORM), "neither named nor in the group");
    }

    @Test
    void aBlogEntryForNamedMembersOnlyIsAnnouncedOnlyToThem() {
        var author = stationMemberRepo.resolveIdentity(named.id());
        var news = newsRepo.create(station.id(), "Named only", "x", "<p>x</p>", author);
        restrictionService.setRestrictions(
                RestrictionType.NEWS,
                news.id(),
                new RestrictionSelection(List.of(), List.of(), List.of(), List.of(named.id()), RestrictionMode.AND));

        new NewsCreatedHandler(notifications, restrictionService)
                .handle(new NewsCreated(station.id(), news.id(), "Named only", "Author", "x"));

        assertEquals(1, count(named, NotificationType.NEW_NEWS));
        assertEquals(0, count(inGroup, NotificationType.NEW_NEWS));
        assertEquals(0, count(outsider, NotificationType.NEW_NEWS));
    }

    private static long count(StationMember member, NotificationType type) {
        return notificationRepo.findAll(member.id()).stream()
                .filter(notification -> notification.type() == type)
                .count();
    }

    private static StationMember createMember(String email) {
        var account = accountRepo.create(email, "Announce", "Member");
        accounts.add(account);
        return stationMemberRepo.create(station.id(), account.id());
    }
}
