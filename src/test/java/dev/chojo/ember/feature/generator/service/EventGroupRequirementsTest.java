/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventRequirementsChanged;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * An event group asks every part for its documents: a part lists the event group's first and its own
 * after them, cannot take one of the event group's off, and a change to the event group's list is told
 * for every part.
 */
class EventGroupRequirementsTest extends GeneratorTestBase {
    private static final Instant START = Instant.now().plus(Duration.ofDays(20));

    private static Wiring wiring;
    private static StationMember manager;
    private static EventGroupService groups;

    private DomainEventBus bus;
    private EventRequirementService requirements;

    @BeforeAll
    static void setup() {
        wiring = wire("Terminreihe Dokumente");
        manager = wiring.member("group-docs@test.com", "Nora", "Fülling");
        groups = newEventGroupService();
    }

    @BeforeEach
    void wireService() {
        bus = mock(DomainEventBus.class);
        requirements = new EventRequirementService(
                new EventRequirementRepository(),
                wiring.templates(),
                new MemberNeutralTemplates(wiring.templates()),
                new EventFederationRepository(),
                bus,
                new EventGroupRepository(),
                new EventGroupPartnerRepository());
    }

    private static int forAppointments(String name) {
        var request = letter(name, "{{member.fullName}}")
                .forAppointments(true)
                .hidden(true)
                .build();
        return wiring.templates().create(wiring.owner(), request, manager.id()).id();
    }

    private static EventGroup group(String name) {
        return groups.create(
                wiring.station().id(),
                new EventGroupDetails(name, null, null, null, false, null),
                List.of(new EventGroupSetDraft(null, "Übung", 0)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
    }

    private static StationEvent appointment(String name) {
        return eventRepo.create(
                wiring.station().id(),
                name,
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                START,
                START.plus(Duration.ofHours(4)),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
    }

    private static StationEvent part(EventGroup group, String name) {
        var event = appointment(name);
        groups.addPart(
                wiring.station().id(),
                group.id(),
                event.id(),
                groups.findSets(group.id()).getFirst().id());
        return event;
    }

    private static List<Integer> ids(List<RequiredTemplate> required) {
        return required.stream().map(RequiredTemplate::templateId).toList();
    }

    @Test
    void aPartAsksForTheGroupsDocumentsBeforeItsOwn() {
        var group = group("Jugendflamme Dokumente");
        var event = part(group, "Übungstag");
        int consent = forAppointments("Einverständnis");
        int photo = forAppointments("Fotoerlaubnis");
        requirements.setForEventGroup(wiring.owner(), group.id(), List.of(consent));
        requirements.setForEvent(wiring.owner(), event.id(), List.of(photo));

        var effective = requirements.forEvent(event.id());
        assertEquals(List.of(consent, photo), ids(effective));
        assertTrue(effective.get(0).fromEventGroup());
        assertTrue(!effective.get(1).fromEventGroup());
        assertEquals(List.of(consent), ids(requirements.forEventGroup(group.id())));
    }

    /** A service whose partner check refuses every document, so whether a check runs at all shows. */
    private EventRequirementService refusingPartners() {
        var neutral = mock(MemberNeutralTemplates.class);
        doThrow(DocumentRefusal.PARTNER_AGREEMENT_NAMES_A_PERSON.raise())
                .when(neutral)
                .requireSignableByPartners(any());
        return new EventRequirementService(
                new EventRequirementRepository(),
                wiring.templates(),
                neutral,
                new EventFederationRepository(),
                bus,
                new EventGroupRepository(),
                new EventGroupPartnerRepository());
    }

    @Test
    void aGroupWithoutPartsIsCheckedForItsOwnDocumentsBeforeItIsShared() {
        var group = group("Jugendflamme ohne Termine");
        requirements.setForEventGroup(wiring.owner(), group.id(), List.of(forAppointments("Einverständnis geteilt")));

        assertThrows(RefusalResponse.class, () -> refusingPartners().requireEventGroupShareable(group.id()));
        var bare = group("Jugendflamme ohne Dokumente");
        assertDoesNotThrow(() -> refusingPartners().requireEventGroupShareable(bare.id()));
    }

    @Test
    void anAppointmentJoiningASharedGroupIsCheckedForItsOwnDocuments() {
        var group = group("Jugendflamme geteilt");
        var event = appointment("Zusatztag");
        requirements.setForEvent(wiring.owner(), event.id(), List.of(forAppointments("Fotoerlaubnis geteilt")));

        assertDoesNotThrow(() -> refusingPartners().requireJoinable(group.id(), event.id()));
        new EventGroupPartnerRepository().setShare(group.id(), ShareScope.ALL_PARTNERS, List.of());
        assertThrows(RefusalResponse.class, () -> refusingPartners().requireJoinable(group.id(), event.id()));
    }

    @Test
    void aPartCannotTakeTheGroupsDocumentOff() {
        var group = group("Jugendflamme Abgabe");
        var event = part(group, "Prüfungstag");
        int consent = forAppointments("Einverständnis Abgabe");
        int health = forAppointments("Gesundheitsbogen");
        requirements.setForEventGroup(wiring.owner(), group.id(), List.of(consent));

        var after = requirements.setForEvent(wiring.owner(), event.id(), List.of(health));

        assertEquals(List.of(consent, health), ids(after));
        var again = requirements.setForEvent(wiring.owner(), event.id(), List.of(consent, health));
        assertEquals(List.of(consent, health), ids(again), "the event group's document is not stored twice");
    }

    @Test
    void changingTheGroupsListIsToldForEveryPart() {
        var group = group("Jugendflamme Änderung");
        var first = part(group, "Tag eins");
        var second = part(group, "Tag zwei");
        int consent = forAppointments("Einverständnis Änderung");

        requirements.setForEventGroup(wiring.owner(), group.id(), List.of(consent));

        verify(bus)
                .publish(new EventRequirementsChanged(wiring.station().id(), first.id(), List.of(consent), List.of()));
        verify(bus)
                .publish(new EventRequirementsChanged(wiring.station().id(), second.id(), List.of(consent), List.of()));

        requirements.setForEventGroup(wiring.owner(), group.id(), List.of());
        verify(bus)
                .publish(new EventRequirementsChanged(wiring.station().id(), first.id(), List.of(), List.of(consent)));
        assertTrue(requirements.forEvent(first.id()).isEmpty());
    }

    @Test
    void anAppointmentOfItsOwnKeepsItsList() {
        group("Jugendflamme Allein");
        var event = appointment("Dienstabend");
        int consent = forAppointments("Einverständnis Allein");

        requirements.setForEvent(wiring.owner(), event.id(), List.of(consent));

        var effective = requirements.forEvent(event.id());
        assertEquals(List.of(consent), ids(effective));
        assertTrue(!effective.getFirst().fromEventGroup());
    }
}
