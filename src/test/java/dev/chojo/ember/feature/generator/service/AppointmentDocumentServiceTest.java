/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.entity.RequirementStatus;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService.ParticipantDocuments;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The documents appointments ask participants to bring: which templates can be asked for, the list an
 * appointment takes from its template, and the copies a participant or their guardian gets, filled with
 * the appointment's values, filed and logged, and refused to everybody else.
 */
class AppointmentDocumentServiceTest extends GeneratorTestBase {
    private static final Instant START = Instant.parse("2026-09-27T07:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-09-27");
    private static final String CONSENT = """
            {{member.fullName}} nimmt an {{event.name}} teil.

            Beginn {{event.start}}, Ende {{event.end}}, Treffpunkt {{event.location}}.""";

    private static Wiring wiring;
    private static EventRequirementService requirements;
    private static AppointmentDocumentService appointments;
    private static StationMember manager;
    private static StationMember lena;
    private static StationMember guardian;
    private static StationMember max;
    private static StationEvent marathon;
    private static StationEvent camp;

    @BeforeAll
    static void setup() {
        wiring = wire("Termin Wache");
        manager = wiring.member("appt-manager@test.com", "Nora", "Fülling");
        lena = wiring.member("appt-lena@test.com", "Lena", "Schmidt");
        guardian = wiring.member("appt-guardian@test.com", "Anna", "Schmidt");
        max = wiring.member("appt-max@test.com", "Max", "Weiß");
        stationMemberRepo.addManager(guardian.id(), lena.id());

        marathon = event("Berlin-Marathon");
        camp = event("Zeltlager");
        eventFieldRepo.create(
                marathon.id(),
                "Treffpunkt",
                FieldType.LOCATION,
                EventQuestionSettings.empty(),
                "Brandenburger Tor",
                0,
                false,
                null,
                false);
        eventRegistrationRepo.create(marathon.id(), lena.id(), DAY);
        eventRegistrationRepo.create(camp.id(), max.id(), DAY);

        var repository = new EventRequirementRepository();
        requirements = new EventRequirementService(repository, wiring.templates());
        appointments = new AppointmentDocumentService(
                repository,
                wiring.templates(),
                wiring.generator(),
                wiring.generation(),
                new GuardianPolicy(stationMemberRepo),
                eventRegistrationRepo,
                eventFieldRepo,
                memberNameResolver);
    }

    private static StationEvent event(String name) {
        return eventRepo.create(
                wiring.station().id(),
                name,
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                START,
                START.plus(Duration.ofHours(8)),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    private static int forAppointments(String name, String body) {
        var request = letter(name, body).forAppointments(true).hidden(true).build();
        return wiring.templates().create(wiring.owner(), request, manager.id()).id();
    }

    private static int plain(String name) {
        return wiring.templates()
                .create(wiring.owner(), letter(name, "{{member.fullName}}").build(), manager.id())
                .id();
    }

    @Test
    void aGuardianGetsTheirChildsCopyFilledWithTheAppointment() {
        int consent = forAppointments("Einverständnis", CONSENT);
        requirements.setForEvent(wiring.owner(), marathon.id(), List.of(consent));

        var before = appointments.documentsToBring(as(guardian), marathon, DAY);
        assertEquals(1, before.size(), "only the child takes part");
        var forLena = before.getFirst();
        assertEquals(lena.id(), forLena.memberId());
        assertEquals(
                RequirementStatus.NOT_GENERATED, forLena.documents().getFirst().status());

        var generated = appointments.generate(as(guardian), marathon, DAY, consent, lena.id());

        String text = wiring.textOf(generated.documentId());
        assertTrue(text.contains("Lena Schmidt nimmt an Berlin-Marathon teil."), text);
        assertTrue(text.contains("27.09.2026"), text);
        assertTrue(text.contains("Treffpunkt Brandenburger Tor"), text);
        var document = memberDocumentRepo.findById(generated.documentId()).orElseThrow();
        assertFalse(document.hidden(), "a copy to bring is the participant's, whatever the template says");
        assertEquals(guardian.id(), document.uploadedBy());
        var entry = wiring.log().findById(generated.generationId()).orElseThrow();
        assertEquals(marathon.id(), entry.eventId());
        assertEquals(DAY, entry.eventDate());
        assertFalse(entry.selfService());

        var after = appointments
                .documentsToBring(as(guardian), marathon, DAY)
                .getFirst()
                .documents()
                .getFirst();
        assertEquals(RequirementStatus.GENERATED, after.status());
        assertEquals(generated.documentId(), after.documentId());
        assertNotNull(after.generatedAt());
        assertFalse(after.outdated());

        wiring.templates()
                .update(
                        wiring.owner(),
                        consent,
                        letter("Einverständnis", CONSENT + "\n\nNeu.")
                                .forAppointments(true)
                                .build(),
                        manager.id());
        var changed = appointments
                .documentsToBring(as(guardian), marathon, DAY)
                .getFirst()
                .documents()
                .getFirst();
        assertTrue(changed.outdated(), "the template changed since the copy was generated");
    }

    @Test
    void aParticipantGetsTheirOwnCopy() {
        int form = forAppointments("Teilnahme", "{{member.fullName}} am {{event.start}}");
        requirements.setForEvent(wiring.owner(), marathon.id(), List.of(form));

        List<ParticipantDocuments> own = appointments.documentsToBring(as(lena), marathon, DAY);
        assertEquals(
                List.of(lena.id()),
                own.stream().map(ParticipantDocuments::memberId).toList());

        var generated = appointments.generate(as(lena), marathon, DAY, form, lena.id());
        assertEquals(
                lena.id(),
                memberDocumentRepo
                        .findById(generated.documentId())
                        .orElseThrow()
                        .uploadedBy());
    }

    @Test
    void nobodyElseGetsACopy() {
        int form = forAppointments("Nur Teilnehmende", "{{member.fullName}}");
        requirements.setForEvent(wiring.owner(), marathon.id(), List.of(form));
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(form));

        assertTrue(appointments.documentsToBring(as(max), marathon, DAY).isEmpty());
        refused(
                DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS,
                () -> appointments.generate(as(max), marathon, DAY, form, max.id()));
        refused(
                DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS,
                () -> appointments.generate(as(max), marathon, DAY, form, lena.id()));
        refused(
                DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS,
                () -> appointments.generate(as(guardian), marathon, DAY.plusDays(1), form, lena.id()));
        refused(
                DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_REQUIRED,
                () -> appointments.generate(as(lena), marathon, DAY, plain("Nicht verlangt"), lena.id()));
    }

    @Test
    void anAppointmentAskingForNothingListsNothing() {
        var quiet = event("Ohne Dokumente");
        eventRegistrationRepo.create(quiet.id(), lena.id(), DAY);

        assertTrue(appointments.documentsToBring(as(lena), quiet, DAY).isEmpty());
    }

    @Test
    void anAppointmentStartsWithTheDocumentsOfItsTemplate() {
        int form = forAppointments("Aus der Vorlage", "{{event.name}}");
        var eventTemplates = new EventTemplateRepository();
        int eventTemplate = eventTemplates.create(wiring.station().id(), "Lauf").id();
        requirements.setForEventTemplate(wiring.owner(), eventTemplate, List.of(form));
        var created = event("Aus Vorlage gemacht");

        assertEquals(1, eventTemplates.copyDocumentRequirements(eventTemplate, created.id()));

        assertEquals(
                List.of(form),
                requirements.forEvent(created.id()).stream()
                        .map(RequiredTemplate::templateId)
                        .toList());
        assertEquals(
                List.of(form),
                requirements.forEventTemplate(eventTemplate).stream()
                        .map(RequiredTemplate::templateId)
                        .toList());
    }

    @Test
    void onlyTemplatesForAppointmentsCanBeAskedFor() {
        int form = forAppointments("Angeboten", "{{member.fullName}}");
        int other = plain("Nicht für Termine");
        var event = event("Prüfung");

        assertTrue(requirements.offered(wiring.owner()).stream().anyMatch(t -> t.templateId() == form));
        assertFalse(requirements.offered(wiring.owner()).stream().anyMatch(t -> t.templateId() == other));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NOT_FOR_APPOINTMENTS,
                () -> requirements.setForEvent(wiring.owner(), event.id(), List.of(other)));
        var tooMany = new ArrayList<Integer>();
        for (int index = 0; index <= EventRequirementService.MAX_REQUIREMENTS; index++) tooMany.add(-index - 1);
        refused(
                DocumentRefusal.DOCUMENT_REQUIREMENTS_TOO_MANY,
                () -> requirements.setForEvent(wiring.owner(), event.id(), tooMany));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE,
                () -> requirements.setForEvent(wiring.owner(), event.id(), List.of(-1)));

        requirements.setForEvent(wiring.owner(), event.id(), List.of(form, form));
        wiring.templates().setArchived(wiring.owner(), form, true, manager.id());
        var kept = requirements.setForEvent(wiring.owner(), event.id(), List.of(form));
        assertTrue(kept.getFirst().archived(), "an archived template asked for before may stay");
        requirements.setForEvent(wiring.owner(), event.id(), List.of());
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED,
                () -> requirements.setForEvent(wiring.owner(), event.id(), List.of(form)));
    }

    @Test
    void aTemplateForAppointmentsIsLegalAndStaysOneWhileRequired() {
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_CALLED_NAME_IN_LEGAL,
                () -> forAppointments("Rufname", "{{member.calledName}}"));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_APPOINTMENT_VALUES_OUTSIDE, () -> wiring.templates()
                .create(wiring.owner(), letter("Ohne Termin", "{{event.name}}").build(), manager.id()));

        int form = forAppointments("Pflicht", "{{member.fullName}}");
        assertTrue(wiring.templates().detail(wiring.owner(), form).legal());
        requirements.setForEvent(wiring.owner(), event("Pflichttermin").id(), List.of(form));

        refused(DocumentRefusal.DOCUMENT_TEMPLATE_REQUIRED_BY_APPOINTMENTS, () -> wiring.templates()
                .update(
                        wiring.owner(),
                        form,
                        letter("Pflicht", "{{member.fullName}}").build(),
                        manager.id()));
    }
}
