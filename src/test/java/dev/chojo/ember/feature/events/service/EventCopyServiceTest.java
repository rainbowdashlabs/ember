/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.equipment.entity.EquipmentNeed;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.events.repository.EventCopyRepository;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.service.GeneratorTestBase;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.EventGroupFixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a copied appointment takes over on the server, table by table: the files it hands over, the
 * documents it asks to bring and the equipment it needs on every date. And which originals may be
 * copied at all.
 */
class EventCopyServiceTest extends GeneratorTestBase {
    private static final Instant START = Instant.parse("2027-04-06T17:00:00Z");

    private static EventCopyService service;
    private static EventAttachmentRepository attachments;
    private static EventRequirementRepository requirements;
    private static Station station;
    private static Station otherStation;
    private static Wiring wiring;
    private static StationMember author;
    private static final List<Map.Entry<AppointmentOwner, Integer>> FILE_HOLDERS = new ArrayList<>();

    @BeforeAll
    static void setup() {
        service = new EventCopyService(eventRepo, new EventCopyRepository());
        attachments = new EventAttachmentRepository();
        requirements = new EventRequirementRepository();
        station = stationRepo.create("EventCopyStation");
        otherStation = stationRepo.create("EventCopyOtherStation");
        wiring = wire(station);
        author = stationMemberRepo.create(
                station.id(),
                accountRepo
                        .create("event-copy-author@test.com", "Nora", "Fülling")
                        .id());
    }

    /**
     * Takes the files off every template and event group the tests attached them to. Those links keep
     * the library's files from being deleted with the station.
     */
    @AfterAll
    static void cleanup() {
        FILE_HOLDERS.forEach(holder -> attachments
                .findByOwner(holder.getKey(), holder.getValue())
                .forEach(attachment -> attachments.detach(holder.getKey(), attachment.id())));
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
    }

    private static StationEvent appointment(Station owner, String name) {
        return eventRepo.create(
                owner.id(),
                name,
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                START,
                START.plusSeconds(7200),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    private static int file(String name) {
        return mediaFileRepo
                .create(null, station.id(), UUID.randomUUID().toString(), name, "application/pdf", 1024)
                .id();
    }

    private static int documentTemplate(String name) {
        return wiring.templates()
                .create(
                        wiring.owner(),
                        letter(name, "{{member.fullName}}")
                                .forAppointments(true)
                                .build(),
                        author.id())
                .id();
    }

    @Test
    void theStationsOwnAppointmentMayBeCopied() {
        var source = appointment(station, "Dienstabend");

        var found = service.requireSource(station.id(), source.id(), event -> true);

        assertEquals(source.id(), found.id());
    }

    @Test
    void anotherStationsAppointmentIsRefused() {
        var foreign = appointment(otherStation, "Fremder Dienst");

        var refusal = assertThrows(
                RefusalResponse.class, () -> service.requireSource(station.id(), foreign.id(), event -> true));
        assertSame(EventRefusal.EVENT_TO_COPY_NOT_HERE, refusal.refusal());
    }

    @Test
    void anAppointmentHiddenFromTheCallerIsRefusedInTheSameWords() {
        var hidden = appointment(station, "Verborgener Dienst");

        var refusal = assertThrows(
                RefusalResponse.class, () -> service.requireSource(station.id(), hidden.id(), event -> false));
        assertSame(EventRefusal.EVENT_TO_COPY_NOT_HERE, refusal.refusal());
    }

    @Test
    void anAppointmentThatIsGoneIsRefusedInTheSameWords() {
        var gone = appointment(station, "Gelöschter Dienst");
        eventRepo.delete(gone.id());

        var refusal = assertThrows(
                RefusalResponse.class, () -> service.requireSource(station.id(), gone.id(), event -> true));
        assertSame(EventRefusal.EVENT_TO_COPY_NOT_HERE, refusal.refusal());
    }

    /**
     * The files are linked in the original's order and with its labels, and the original keeps them. A
     * file the original shows from its event group stays with the event group.
     */
    @Test
    void theCopyTakesTheOriginalsFiles() {
        var eventGroups = newEventGroupService();
        var eventGroup = eventGroups.create(
                station.id(),
                new EventGroupDetails("Lehrgang mit Dateien", null, null, null, false, null),
                List.of(new EventGroupSetDraft(null, "Tage", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        var source = EventGroupFixtures.part(eventGroup, 3);
        var copy = appointment(station, "Übung mit Dateien");
        int plan = file("plan.pdf");
        int sheet = file("laufzettel.pdf");
        attachments.attach(AppointmentOwner.EVENT, source.id(), plan, "Einsatzplan", true);
        attachments.attach(AppointmentOwner.EVENT, source.id(), sheet, null, false);
        attachments.attach(AppointmentOwner.EVENT_GROUP, eventGroup.id(), file("lehrgang.pdf"), null, false);
        FILE_HOLDERS.add(Map.entry(AppointmentOwner.EVENT_GROUP, eventGroup.id()));
        attachments.attach(
                AppointmentOwner.EVENT, appointment(station, "Andere Übung").id(), file("x.pdf"), null, false);

        service.copyInto(source, copy.id());

        var taken = attachments.findByOwner(AppointmentOwner.EVENT, copy.id());
        assertEquals(
                List.of(plan, sheet),
                taken.stream().map(EventAttachment::fileId).toList());
        assertEquals("Einsatzplan", taken.getFirst().label());
        assertEquals(
                List.of(true, false),
                taken.stream().map(EventAttachment::internal).toList());
        assertEquals(
                2, attachments.findByOwner(AppointmentOwner.EVENT, source.id()).size());
    }

    /**
     * A template applied to the copy links its files first. A file the original shares with the template
     * is linked once, and the original's other files come after the template's.
     */
    @Test
    void aFileTheCopyAlreadyTookFromATemplateIsLinkedOnce() {
        var templates = new EventTemplateRepository();
        var template = templates.create(station.id(), "Vorlage mit Dateien " + UUID.randomUUID());
        var source = appointment(station, "Übung mit Vorlage");
        var copy = appointment(station, "Übung mit Vorlage");
        int shared = file("gemeinsam.pdf");
        int own = file("eigen.pdf");
        attachments.attach(AppointmentOwner.TEMPLATE, template.id(), shared, null, false);
        FILE_HOLDERS.add(Map.entry(AppointmentOwner.TEMPLATE, template.id()));
        attachments.attach(AppointmentOwner.EVENT, source.id(), own, null, false);
        attachments.attach(AppointmentOwner.EVENT, source.id(), shared, null, false);
        templates.copyAttachments(template.id(), copy.id());

        int linked = new EventCopyRepository().copyAttachments(source.id(), copy.id());

        assertEquals(1, linked);
        assertEquals(
                List.of(shared, own),
                attachments.findByOwner(AppointmentOwner.EVENT, copy.id()).stream()
                        .map(EventAttachment::fileId)
                        .toList());
    }

    @Test
    void theCopyAsksForTheSameDocumentsInTheSameOrder() {
        var source = appointment(station, "Zeltlager");
        var copy = appointment(station, "Zeltlager");
        int consent = documentTemplate("Einverständnis Kopie");
        int photos = documentTemplate("Fotoerlaubnis Kopie");
        requirements.replaceForEvent(source.id(), List.of(photos, consent));

        service.copyInto(source, copy.id());

        assertEquals(
                List.of(photos, consent),
                requirements.forEvent(copy.id()).stream()
                        .map(RequiredTemplate::templateId)
                        .toList());
    }

    /** A line for every date goes with the copy; a line written for one date of the original stays behind. */
    @Test
    void theCopyNeedsTheEquipmentTheOriginalNeedsOnEveryDate() {
        var source = appointment(station, "Ausbildung mit Material");
        var copy = appointment(station, "Ausbildung mit Material");
        var inventory = inventoryRepo.create(station.id(), "EventCopyFunk", InventoryType.INTERNAL, false, false);
        var radios = artRepo.create(inventory.id(), "EventCopyHandfunk", "", 0);
        var trailers = artRepo.create(inventory.id(), "EventCopyAnhänger", "", 0);
        equipmentNeedRepo.create(source.id(), null, null, radios.id(), null, 4, 60, 30);
        equipmentNeedRepo.create(source.id(), LocalDate.parse("2027-04-06"), null, trailers.id(), null, 1, 0, 0);

        service.copyInto(source, copy.id());

        var taken = equipmentNeedRepo.findByEvent(copy.id());
        assertEquals(1, taken.size());
        EquipmentNeed line = taken.getFirst();
        assertEquals(radios.id(), line.artId());
        assertNull(line.eventDate());
        assertEquals(4, line.quantity());
        assertEquals(60, line.leadMinutes());
        assertEquals(30, line.trailMinutes());
        assertEquals(2, equipmentNeedRepo.findByEvent(source.id()).size(), "the original keeps its lines");
    }

    @Test
    void anOriginalWithNothingToHandOverLeavesTheCopyEmpty() {
        var source = appointment(station, "Leerer Dienst");
        var copy = appointment(station, "Leerer Dienst");

        service.copyInto(source, copy.id());

        assertTrue(attachments.findByOwner(AppointmentOwner.EVENT, copy.id()).isEmpty());
        assertTrue(requirements.forEvent(copy.id()).isEmpty());
        assertTrue(equipmentNeedRepo.findByEvent(copy.id()).isEmpty());
    }
}
