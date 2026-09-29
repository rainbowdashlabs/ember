/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lists of restricted items decide who manages an item type by the same rule as the check on a
 * single item: everything a member holds, whether it comes from their user type, the station's
 * grants for that type, a direct grant or a group, together with what those rights include.
 *
 * <p>Every item here is restricted to one named member, so only a manager of the type sees it
 * without being named.
 */
class ManagerListVisibilityTest extends RepositoryTestBase {
    private static final List<Account> accounts = new ArrayList<>();

    private static Station station;
    private static EventCrudService events;
    private static StationMember named;
    private static StationMember managerByType;
    private static StationMember administratorByGroup;
    private static StationMember managerByStationTypeGrant;
    private static StationMember plain;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("ManagerListVisibilityStation");
        events = newEventServices(new DomainEventBus(Set.of())).crud();

        named = createMember("manager-list-named@test.com");
        plain = createMember("manager-list-plain@test.com");

        managerByType = createMember("manager-list-type@test.com");
        stationMemberRepo.setUserType(managerByType.id(), StationUserType.MANAGER);

        administratorByGroup = createMember("manager-list-group@test.com");
        var group = memberGroupRepo.create(station.id(), "Administrators");
        memberGroupRepo.addMember(group.id(), administratorByGroup.id());
        memberGroupRepo.addGroupPermission(group.id(), permissionId(StationPermission.STATION_ADMINISTRATOR));

        managerByStationTypeGrant = createMember("manager-list-team@test.com");
        stationMemberRepo.setUserType(managerByStationTypeGrant.id(), StationUserType.TEAM);
        stationMemberRepo.setUserTypePermissions(
                station.id(),
                StationUserType.TEAM,
                List.of(
                        permissionId(StationPermission.NEWS_MANAGER),
                        permissionId(StationPermission.EVENT_MANAGER),
                        permissionId(StationPermission.POLL_MANAGER),
                        permissionId(StationPermission.TEST_MANAGER)));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    @Test
    void managersSeeRestrictedNewsInTheListAndByIdAndPlainMembersDoNot() {
        var news = newsRepo.create(
                station.id(), "Named only", "x", "<p>x</p>", stationMemberRepo.resolveIdentity(named.id()));
        restrictionService.setRestrictions(RestrictionType.NEWS, news.id(), namedOnly());

        for (var member : managers()) {
            boolean manager = manages(RestrictionType.NEWS, member);
            assertTrue(manager, "resolved as a news manager");
            assertTrue(newsRepo.isVisibleForMember(news.id(), member.id(), manager));
            assertTrue(newsRepo.findVisibleForMember(station.id(), member.id(), manager, 0, 50).stream()
                    .anyMatch(entry -> entry.id() == news.id()));
        }
        assertFalse(manages(RestrictionType.NEWS, plain));
        assertFalse(newsRepo.isVisibleForMember(news.id(), plain.id(), false));
        assertFalse(newsRepo.findVisibleForMember(station.id(), plain.id(), false, 0, 50).stream()
                .anyMatch(entry -> entry.id() == news.id()));
    }

    @Test
    void theUnseenListOfANewsEntryCountsItsManagers() {
        var news = newsRepo.create(
                station.id(), "Named only, views", "x", "<p>x</p>", stationMemberRepo.resolveIdentity(named.id()));
        restrictionService.setRestrictions(RestrictionType.NEWS, news.id(), namedOnly());
        var managerIds =
                stationMemberRepo.findMembersWithPermission(station.id(), StationPermission.NEWS_MANAGER).stream()
                        .map(StationMember::id)
                        .toList();

        var unseen = newsRepo.findUnseenViewers(news.id(), station.id(), managerIds).stream()
                .map(viewer -> viewer.member().memberUid())
                .toList();

        for (var member : managers()) {
            assertTrue(unseen.contains(member.uid()));
        }
        assertTrue(unseen.contains(named.uid()));
        assertFalse(unseen.contains(plain.uid()));
    }

    @Test
    void managersSeeRestrictedFormsAndPlainMembersDoNot() {
        var form = formRepo.create(
                station.id(), "Named only", "x", false, true, false, null, null, named.id(), FormPurpose.INTERNAL);
        restrictionService.setRestrictions(RestrictionType.FORM, form.id(), namedOnly());

        for (var member : managers()) {
            assertTrue(
                    formRepo
                            .findByStationForMember(station.id(), member.id(), manages(RestrictionType.FORM, member))
                            .stream()
                            .anyMatch(listed -> listed.id() == form.id()));
        }
        assertFalse(formRepo.findByStationForMember(station.id(), plain.id(), false).stream()
                .anyMatch(listed -> listed.id() == form.id()));
    }

    @Test
    void managersSeeRestrictedTestsAndPlainMembersDoNot() {
        var test = quizTestRepo.create(station.id(), "Named only", "x", 30, false, false, named.id());
        restrictionService.setRestrictions(RestrictionType.QUIZ_TEST, test.id(), namedOnly());

        for (var member : managers()) {
            assertTrue(
                    quizTestRepo
                            .findByStationForMember(
                                    station.id(), member.id(), manages(RestrictionType.QUIZ_TEST, member))
                            .stream()
                            .anyMatch(listed -> listed.id() == test.id()));
        }
        assertFalse(quizTestRepo.findByStationForMember(station.id(), plain.id(), false).stream()
                .anyMatch(listed -> listed.id() == test.id()));
    }

    @Test
    void managersSeeEventsHiddenFromOthersAndPlainMembersDoNot() {
        var event = hiddenEvent();

        for (var member : managers()) {
            assertTrue(listed(events.findByStationForMember(station.id(), member.id()), event));
            assertTrue(listed(events.findFilteredForMembers(station.id(), List.of(member.id()), null, null), event));
        }
        assertFalse(listed(events.findByStationForMember(station.id(), plain.id()), event));
        assertFalse(listed(events.findFilteredForMembers(station.id(), List.of(plain.id()), null, null), event));
        assertTrue(listed(events.findByStationForMember(station.id(), named.id()), event));
    }

    /**
     * The station-wide search for holders of a permission and the resolver that answers for one
     * member agree on every member and every permission, including the guardian right that follows
     * from looking after somebody.
     */
    @Test
    void theSearchForHoldersAgreesWithTheResolverOnEveryPermission() {
        var guardian = createMember("manager-list-guardian@test.com");
        var cared = createMember("manager-list-cared@test.com");
        stationMemberRepo.addManager(guardian.id(), cared.id());
        var members = new ArrayList<>(managers());
        members.addAll(List.of(named, plain, guardian, cared));

        for (var permission : StationPermission.values()) {
            var found = stationMemberRepo.findMembersWithPermission(station.id(), permission).stream()
                    .map(StationMember::id)
                    .toList();
            for (var member : members) {
                boolean holds = memberPermissionResolver.resolve(member.id()).contains(permission);
                assertTrue(
                        holds == found.contains(member.id()),
                        "%s for %s: resolver %s, search %s"
                                .formatted(permission, member.id(), holds, found.contains(member.id())));
            }
        }
    }

    private static StationEvent hiddenEvent() {
        var start = Instant.now().plus(30, ChronoUnit.DAYS);
        var event = events.create(
                station.id(),
                "Named only",
                "x",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        restrictionService.setRestrictions(RestrictionType.EVENT_VIEW, event.id(), namedOnly());
        return event;
    }

    private static boolean listed(List<StationEvent> list, StationEvent event) {
        return list.stream().anyMatch(listed -> listed.id() == event.id());
    }

    private static List<StationMember> managers() {
        return List.of(managerByType, administratorByGroup, managerByStationTypeGrant);
    }

    private static boolean manages(RestrictionType type, StationMember member) {
        return restrictionService.manages(type, member.id());
    }

    private static RestrictionSelection namedOnly() {
        return new RestrictionSelection(List.of(), List.of(), List.of(), List.of(named.id()), RestrictionMode.AND);
    }

    private static int permissionId(StationPermission permission) {
        return stationMemberRepo.findPermissionByName(permission).orElseThrow().id();
    }

    private static StationMember createMember(String email) {
        var account = accountRepo.create(email, "Manager", "List");
        accounts.add(account);
        return stationMemberRepo.create(station.id(), account.id());
    }
}
