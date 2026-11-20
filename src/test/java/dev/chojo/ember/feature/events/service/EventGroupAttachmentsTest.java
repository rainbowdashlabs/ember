/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An event group hands its files over on every part, before the part's own: a part's page lists and
 * hands them out under the part, an internal one stays with the crew, and only the event group takes
 * one off.
 */
class EventGroupAttachmentsTest extends RepositoryTestBase {
    private static final Set<StationPermission> ROOM = Set.of(StationPermission.USER);
    private static final Set<StationPermission> CREW = Set.of(StationPermission.EVENT_INTERNAL);

    private static final EventAttachmentRepository repository = new EventAttachmentRepository();
    private static final List<Integer> groupIds = new ArrayList<>();
    private static final List<Integer> eventIds = new ArrayList<>();
    private static EventAttachmentService service;
    private static EventGroupService groups;
    private static Station station;
    private static int routeFile;
    private static int crewFile;
    private static int ownFile;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Group Files Station");
        var media = mock(MediaLibraryService.class);
        when(media.findFile(anyInt())).thenAnswer(invocation -> mediaFileRepo.findById(invocation.getArgument(0)));
        service = new EventAttachmentService(repository, media);
        groups = newEventGroupService();
        routeFile = file("anfahrt.pdf");
        crewFile = file("ausbilderplan.pdf");
        ownFile = file("tagesplan.pdf");
    }

    @AfterAll
    static void cleanup() {
        groupIds.forEach(id -> repository
                .findByOwner(AppointmentOwner.EVENT_GROUP, id)
                .forEach(found -> repository.detach(AppointmentOwner.EVENT_GROUP, found.id())));
        eventIds.forEach(id -> repository
                .findByOwner(AppointmentOwner.EVENT, id)
                .forEach(found -> repository.detach(AppointmentOwner.EVENT, found.id())));
        stationRepo.delete(station.id());
    }

    private static int file(String name) {
        return mediaFileRepo
                .create(null, station.id(), UUID.randomUUID().toString(), name, "application/pdf", 1024)
                .id();
    }

    private static EventGroup group() {
        var group = groups.create(
                station.id(),
                new EventGroupDetails("Jugendflamme Dateien", null, null, null, false, null),
                List.of(new EventGroupSetDraft(null, "Übung", 0)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        groupIds.add(group.id());
        return group;
    }

    private static StationEvent appointment() {
        var start = Instant.now().plus(Duration.ofDays(10));
        var event = eventRepo.create(
                station.id(),
                "Übungstag",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(4)),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        eventIds.add(event.id());
        return event;
    }

    private static StationEvent part(EventGroup group) {
        var event = appointment();
        groups.addPart(
                station.id(),
                group.id(),
                event.id(),
                groups.findSets(group.id()).getFirst().id());
        return event;
    }

    private static List<String> names(List<EventAttachment> attachments) {
        return attachments.stream().map(EventAttachment::fileName).toList();
    }

    @Test
    void aPartHandsOverTheGroupsFilesBeforeItsOwn() {
        var group = group();
        var event = part(group);
        service.attach(AppointmentOwner.EVENT_GROUP, group.id(), station.id(), routeFile, "Anfahrt", false);
        service.attach(AppointmentOwner.EVENT_GROUP, group.id(), station.id(), crewFile, null, true);
        service.attach(AppointmentOwner.EVENT, event.id(), station.id(), ownFile, null, false);

        var forTheRoom = service.listFor(event.id(), ROOM);
        assertEquals(List.of("anfahrt.pdf", "tagesplan.pdf"), names(forTheRoom));
        assertTrue(forTheRoom.getFirst().fromEventGroup());
        assertFalse(forTheRoom.get(1).fromEventGroup());
        assertEquals(event.id(), forTheRoom.getFirst().eventId(), "the event group's file is read as the part's");

        assertEquals(
                List.of("anfahrt.pdf", "ausbilderplan.pdf", "tagesplan.pdf"), names(service.listFor(event.id(), CREW)));
        assertEquals(List.of("anfahrt.pdf", "tagesplan.pdf"), names(service.listOpen(event.id())));
        assertEquals(2, service.listOpenFor(List.of(event.id())).get(event.id()).size());
    }

    @Test
    void aPartHandsOutTheGroupsFileButCannotTakeItOff() {
        var group = group();
        var event = part(group);
        int crew = service.attach(AppointmentOwner.EVENT_GROUP, group.id(), station.id(), crewFile, null, true)
                .id();

        assertTrue(service.findReadableOn(event.id(), crew, CREW).isPresent());
        assertTrue(service.findReadableOn(event.id(), crew, ROOM).isEmpty());
        assertTrue(service.find(AppointmentOwner.EVENT, crew).isEmpty(), "the part's own address does not reach it");
        assertFalse(service.detach(AppointmentOwner.EVENT, crew));
        assertEquals(
                1,
                service.listForEventGroup(group.id(), Set.of(StationPermission.EVENT_INTERNAL))
                        .size());

        assertTrue(service.detach(AppointmentOwner.EVENT_GROUP, crew));
        assertTrue(service.listFor(event.id(), CREW).isEmpty());
    }

    @Test
    void theGroupsFilesStayWithTheGroup() {
        var group = group();
        var other = group();
        int route = service.attach(AppointmentOwner.EVENT_GROUP, group.id(), station.id(), routeFile, null, false)
                .id();
        var loose = appointment();

        assertTrue(service.findOfEventGroup(other.id(), route).isEmpty());
        assertTrue(service.findReadableOfEventGroup(group.id(), route, ROOM).isPresent());
        assertTrue(service.listFor(loose.id(), CREW).isEmpty());

        assertTrue(service.update(AppointmentOwner.EVENT_GROUP, route, "Wegbeschreibung", true));
        assertTrue(service.findReadableOfEventGroup(group.id(), route, ROOM).isEmpty());
        assertEquals(
                "Wegbeschreibung",
                service.listForEventGroup(group.id(), CREW).getFirst().displayName());
    }

    @Test
    void theGroupsFilesKeepTheOrderTheyWereGivenAndIgnoreOthers() {
        var group = group();
        var other = group();
        int route = service.attach(AppointmentOwner.EVENT_GROUP, group.id(), station.id(), routeFile, null, false)
                .id();
        int crew = service.attach(AppointmentOwner.EVENT_GROUP, group.id(), station.id(), crewFile, null, false)
                .id();
        int foreign = service.attach(AppointmentOwner.EVENT_GROUP, other.id(), station.id(), ownFile, null, false)
                .id();

        service.reorder(AppointmentOwner.EVENT_GROUP, group.id(), List.of(crew, foreign, route));

        assertEquals(List.of("ausbilderplan.pdf", "anfahrt.pdf"), names(service.listForEventGroup(group.id(), CREW)));
        assertEquals(List.of("tagesplan.pdf"), names(service.listForEventGroup(other.id(), CREW)));
    }
}
