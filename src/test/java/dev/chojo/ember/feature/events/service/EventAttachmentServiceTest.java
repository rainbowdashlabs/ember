/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who is handed which of an event's files.
 *
 * <p>The whole point of the internal mark is that it is answered the same way twice: a file left out
 * of the list is refused when its own address is asked for. Both are checked here, because a screen
 * that filters and a door that does not is the shape this feature must not have.
 */
class EventAttachmentServiceTest extends RepositoryTestBase {

    private static final Set<StationPermission> ROOM = Set.of(StationPermission.USER);
    private static final Set<StationPermission> CREW = Set.of(StationPermission.EVENT_INTERNAL);

    private static EventAttachmentService service;
    private static EventAttachmentRepository repository;
    private static Station station;
    private static StationEvent event;
    private static int openFileId;
    private static int internalFileId;

    @BeforeAll
    static void setup() {
        repository = new EventAttachmentRepository();
        var media = mock(MediaLibraryService.class);
        station = stationRepo.create("EventFilesStation");
        event = eventRepo.create(
                station.id(),
                "Dienstabend",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2026-05-12T18:00:00Z"),
                Instant.parse("2026-05-12T20:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        openFileId = mediaFileRepo
                .create(null, station.id(), UUID.randomUUID().toString(), "laufzettel.pdf", "application/pdf", 2048)
                .id();
        internalFileId = mediaFileRepo
                .create(null, station.id(), UUID.randomUUID().toString(), "einsatzplan.pdf", "application/pdf", 4096)
                .id();

        when(media.findFile(anyInt())).thenAnswer(invocation -> mediaFileRepo.findById(invocation.getArgument(0)));
        service = new EventAttachmentService(repository, media);
    }

    @AfterAll
    static void cleanup() {
        repository.findByEvent(event.id()).forEach(attachment -> repository.detach(attachment.id()));
        stationRepo.delete(station.id());
    }

    @BeforeEach
    void emptyTheEvent() {
        repository.findByEvent(event.id()).forEach(attachment -> repository.detach(attachment.id()));
    }

    private int attachOpen() {
        return service.attach(event.id(), station.id(), openFileId, "Laufzettel", false)
                .id();
    }

    private int attachInternal() {
        return service.attach(event.id(), station.id(), internalFileId, null, true)
                .id();
    }

    @Test
    void aFileIsForEverybodyWhoMaySeeTheEventUnlessItIsMarkedInternal() {
        attachOpen();
        attachInternal();

        var forTheRoom = service.listFor(event.id(), ROOM);
        assertEquals(1, forTheRoom.size(), "the room is handed the open file and nothing else");
        assertEquals("Laufzettel", forTheRoom.getFirst().displayName());

        var forTheCrew = service.listFor(event.id(), CREW);
        assertEquals(2, forTheCrew.size(), "whoever reads the internal side is handed both");
    }

    /** A file left out of the list is refused at the door, which is the same question asked twice. */
    @Test
    void anInternalFileIsRefusedToWhoeverTheListKeepsItFrom() {
        int internal = attachInternal();

        assertTrue(service.findReadable(internal, CREW).isPresent());
        assertEquals(Optional.empty(), service.findReadable(internal, ROOM), "asking by id says no more than the list");
    }

    /** What leaves the station carries the open files and never what the event keeps back. */
    @Test
    void onlyTheOpenFilesAreWhatLeavesTheStation() {
        attachOpen();
        attachInternal();

        var travelling = service.listOpen(event.id());

        assertEquals(1, travelling.size());
        assertFalse(travelling.getFirst().internal());
    }

    @Test
    void aFileOfAnotherStationCannotBeAttached() {
        var elsewhere = stationRepo.create("EventFilesOtherStation");
        int theirFile = mediaFileRepo
                .create(null, elsewhere.id(), UUID.randomUUID().toString(), "fremd.pdf", "application/pdf", 512)
                .id();

        assertThrows(
                BadRequestResponse.class,
                () -> service.attach(event.id(), station.id(), theirFile, null, false),
                "an attachment is a reference into this station's own library");

        stationRepo.delete(elsewhere.id());
    }

    /** Whether a file is kept back is written after the fact, which is how a slip is put right. */
    @Test
    void aFileCanBeOpenedUpOrKeptBackAfterwards() {
        int attachment = attachOpen();

        assertTrue(service.update(attachment, "Laufzettel", true));
        assertEquals(0, service.listFor(event.id(), ROOM).size(), "it is no longer the room's");

        assertTrue(service.update(attachment, "Laufzettel", false));
        assertEquals(1, service.listFor(event.id(), ROOM).size());
    }

    @Test
    void theOrderIsTheOneWhoeverWroteTheEventPutThemIn() {
        int first = attachOpen();
        int second = service.attach(event.id(), station.id(), internalFileId, "Zweitens", false)
                .id();

        service.reorder(event.id(), List.of(second, first));

        assertEquals(
                List.of(second, first),
                service.listFor(event.id(), ROOM).stream().map(a -> a.id()).toList());
    }

    /** Whoever edits the event is handed everything, without a permission being asked about. */
    @Test
    void theEditorIsHandedEveryFileOfTheEvent() {
        attachOpen();
        attachInternal();

        assertEquals(2, service.listAll(event.id()).size());
    }

    /**
     * A listing asks once for every event it draws.
     *
     * <p>Asked event by event, a calendar of fifty appointments asks fifty times, which is what the
     * one-question form is for. An event with nothing attached is absent from the answer rather
     * than present and empty, and an empty question is answered without asking at all.
     */
    @Test
    void severalEventsAreAskedAboutAtOnce() {
        attachOpen();
        attachInternal();

        var byEvent = service.listOpenFor(List.of(event.id()));

        assertEquals(1, byEvent.size());
        assertEquals(1, byEvent.get(event.id()).size(), "only the open file travels");
        assertTrue(service.listOpenFor(List.of()).isEmpty(), "nothing asked for, nothing asked");
    }

    @Test
    void aDetachedFileIsGoneFromTheEventAndNowhereToBeFound() {
        int attachment = attachOpen();

        assertTrue(service.detach(attachment));
        assertEquals(Optional.empty(), service.find(attachment));
        assertFalse(service.detach(attachment), "detaching what is already gone changes nothing");
    }

    /** A blank label is no label: the file name is what a reader is shown then. */
    @Test
    void aFileWithoutALabelIsCalledByItsFileName() {
        int attachment = service.attach(event.id(), station.id(), openFileId, "   ", false)
                .id();

        var stored = service.find(attachment).orElseThrow();
        assertEquals("laufzettel.pdf", stored.displayName());
    }

    /** Whoever may write an event may read what it keeps back, without being granted anything twice. */
    @Test
    void writingAnEventCarriesReadingWhatItKeepsBack() {
        var editor = StationPermission.expand(Set.of(StationPermission.EVENT_EDIT));

        assertTrue(editor.contains(StationPermission.EVENT_INTERNAL));
        assertTrue(EventAttachmentService.readsInternal(editor));
        assertFalse(EventAttachmentService.readsInternal(StationPermission.expand(Set.of(StationPermission.USER))));
    }
}
