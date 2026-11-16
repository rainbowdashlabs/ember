/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.chojo.ember.repository.EventGroupFixtures.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who an event group is meant for and who may sign up for it: its two audiences, how they reach every part
 * and the appointments that join it, and who they let sign up.
 */
class EventGroupServiceAudiencesTest extends EventGroupServiceTestBase {
    @Test
    void theEventGroupsAudiencesReachEveryPartAndEveryPartAddedLater() {
        var eventGroup = jugendflamme();
        var first = oneTime(28);
        service.addPart(
                station.id(),
                eventGroup.id(),
                first.id(),
                set(eventGroup, "Übung").id());

        service.setAudiences(station.id(), eventGroup.id(), youth(), youth());
        var later = oneTime(29);
        service.addPart(
                station.id(),
                eventGroup.id(),
                later.id(),
                set(eventGroup, "Übung").id());

        for (var part : List.of(first, later)) {
            assertEquals(
                    List.of(StationUserType.MEMBER),
                    restrictionService
                            .audienceOf(RestrictionType.EVENT, part.id())
                            .userTypes());
            assertEquals(
                    List.of(StationUserType.MEMBER),
                    restrictionService
                            .audienceOf(RestrictionType.EVENT_VIEW, part.id())
                            .userTypes());
        }
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.viewAudience(eventGroup.id()).userTypes());
    }

    @Test
    void anEventGroupIsCreatedWithItsAudiencesAndATrimmedName() {
        var eventGroup = service.create(
                station.id(),
                details("  Jugendflamme 2  ", false),
                List.of(new EventGroupSetDraft(null, "Teile", 1)),
                youth(),
                youth(),
                null);

        assertEquals("Jugendflamme 2", eventGroup.details().name());
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.registrationAudience(eventGroup.id()).userTypes());
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.viewAudience(eventGroup.id()).userTypes());
    }

    @Test
    void anAudienceNamingSomebodyOfAnotherStationIsRefused() {
        var eventGroup = jugendflamme();
        var stranger =
                new RestrictionAudience(List.of(), List.of(), List.of(), List.of(outsider.id()), RestrictionMode.AND);

        assertRefused(
                EventRefusal.EVENT_GROUP_AUDIENCE_NOT_OF_STATION,
                () -> service.setAudiences(station.id(), eventGroup.id(), stranger, RestrictionAudience.empty()));
        assertRefused(
                EventRefusal.EVENT_GROUP_AUDIENCE_NOT_OF_STATION,
                () -> service.create(
                        station.id(),
                        details("Kurs", false),
                        List.of(new EventGroupSetDraft(null, "Teile", 1)),
                        RestrictionAudience.empty(),
                        stranger,
                        null));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> service.setAudiences(
                        otherStation.id(), eventGroup.id(), RestrictionAudience.empty(), RestrictionAudience.empty()));
    }

    @Test
    void anAppointmentMeantForOtherPeopleThanTheEventGroupIsRefusedAndOneForTheSamePeopleTaken() {
        var eventGroup = jugendflamme();
        int practice = set(eventGroup, "Übung").id();
        var forYouth = oneTime(31);
        restrictionService.setAudience(RestrictionType.EVENT_VIEW, forYouth.id(), youth());

        assertRefused(
                EventRefusal.EVENT_GROUP_PART_AUDIENCE_DIFFERS,
                () -> service.addPart(station.id(), eventGroup.id(), forYouth.id(), practice));
        assertFalse(restrictionService
                .audienceOf(RestrictionType.EVENT_VIEW, forYouth.id())
                .namesNobody());

        service.setAudiences(station.id(), eventGroup.id(), RestrictionAudience.empty(), youth());
        service.addPart(station.id(), eventGroup.id(), forYouth.id(), practice);
        assertEquals(
                List.of(StationUserType.MEMBER),
                restrictionService
                        .audienceOf(RestrictionType.EVENT_VIEW, forYouth.id())
                        .userTypes());
    }

    @Test
    void aRemovedPartKeepsTheSignUpAndAudienceItHadAsAPart() {
        var eventGroup = jugendflamme();
        service.setAudiences(station.id(), eventGroup.id(), youth(), youth());
        var sunday = oneTime(34);
        service.addPart(
                station.id(),
                eventGroup.id(),
                sunday.id(),
                set(eventGroup, "Übung").id());

        service.removePart(station.id(), eventGroup.id(), sunday.id());

        var stored = eventRepo.findById(sunday.id()).orElseThrow();
        assertTrue(stored.requiresRegistration());
        assertTrue(stored.requiresConfirmation());
        assertEquals(
                List.of(StationUserType.MEMBER),
                restrictionService
                        .audienceOf(RestrictionType.EVENT_VIEW, sunday.id())
                        .userTypes());
    }

    @Test
    void whoMaySignUpFollowsBothAudiencesAndLetsAManagerThrough() {
        var joinOnlyAnna = jugendflamme();
        var seenOnlyByAnna = jugendflamme();
        var both = jugendflamme();
        var onlyAnna =
                new RestrictionAudience(List.of(), List.of(), List.of(), List.of(anna.id()), RestrictionMode.AND);
        service.setAudiences(station.id(), joinOnlyAnna.id(), onlyAnna, RestrictionAudience.empty());
        service.setAudiences(station.id(), seenOnlyByAnna.id(), RestrictionAudience.empty(), onlyAnna);
        service.setAudiences(station.id(), both.id(), onlyAnna, onlyAnna);
        var bertAccount = accountRepo.create("bert@eventGroup.test", "Bert", "EventGroup");
        accounts.add(bertAccount.id());
        var bert = stationMemberRepo.create(station.id(), bertAccount.id());
        var groups = List.of(joinOnlyAnna.id(), seenOnlyByAnna.id(), both.id());

        var asMember = service.whoMaySignUp(groups, List.of(anna.id(), bert.id(), -1), Set.of());
        var asManager = service.whoMaySignUp(
                groups, List.of(anna.id(), bert.id()), Set.of(RestrictionType.EVENT_GROUP.managerPermission()));

        for (int eventGroupId : groups) {
            assertEquals(List.of(anna.id()), asMember.get(eventGroupId));
            assertEquals(List.of(anna.id(), bert.id()), asManager.get(eventGroupId));
        }
    }
}
