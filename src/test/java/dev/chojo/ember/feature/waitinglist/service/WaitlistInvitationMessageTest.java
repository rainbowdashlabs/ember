/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.mail.entity.WaitlistInvitationDetails;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * What the invitation mail can say about the evening, and who it goes to.
 */
class WaitlistInvitationMessageTest extends RepositoryTestBase {

    private static EmailService emailService;
    private static WaitlistInvitationMessage message;
    private static Station station;
    private int listId;

    @BeforeAll
    static void setup() {
        emailService = mock(EmailService.class);
        message = new WaitlistInvitationMessage(eventRepo, eventFieldRepo, emailService);
        var created = stationRepo.create("InvitationStation");
        stationRepo.updateTimezone(created.id(), "UTC");
        station = stationRepo.findById(created.id()).orElseThrow();
    }

    @BeforeEach
    void freshList() {
        reset(emailService);
        listId = waitingListRepo
                .create(station.id(), "List " + UUID.randomUUID(), "", null, 180, null, null, 5, false, null, null)
                .id();
    }

    private WaitingListEntry entry(String firstname, String email) {
        return waitingListRepo.createEntry(
                listId,
                firstname,
                "Muster",
                "Elternteil",
                email,
                UUID.randomUUID().toString(),
                "",
                null);
    }

    private StationEvent appointment(String name) {
        return eventRepo.create(
                station.id(),
                name,
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                LocalDate.of(2026, 5, 12).atTime(18, 0).toInstant(ZoneOffset.UTC),
                LocalDate.of(2026, 5, 12).atTime(20, 0).toInstant(ZoneOffset.UTC),
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

    private WaitlistInvitationDetails captureDetails() {
        var captor = ArgumentCaptor.forClass(WaitlistInvitationDetails.class);
        verify(emailService)
                .sendWaitlistInvitationEmail(
                        anyString(), anyString(), anyString(), anyString(), anyString(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void theMailCarriesTheEveningItIsAbout() {
        var event = appointment("Dienstabend");
        var invited = entry("Kind", "kind@test.com");

        message.send(
                invited,
                List.of(),
                station,
                new WaitingListInvitation(event.id(), LocalDate.of(2026, 5, 12), LocalTime.of(17, 45)));

        var details = captureDetails();
        assertEquals("Dienstabend", details.appointmentName());
        assertTrue(details.date().contains("2026"), "the date is written out for a reader");
        assertEquals("18:00 - 20:00", details.time(), "the appointment's own hours are in the mail");
        assertEquals("17:45", details.arrivalTime(), "and the time they were asked to be there");
    }

    /** The hours are printed in the zone the station keeps its times in, not in the stored one. */
    @Test
    void theHoursAreWrittenInTheStationsOwnZone() {
        var elsewhere = stationRepo.create("BerlinStation");
        stationRepo.updateTimezone(elsewhere.id(), "Europe/Berlin");
        var reloaded = stationRepo.findById(elsewhere.id()).orElseThrow();
        var event = appointment("Dienstabend");
        var invited = entry("Kind", "kind@test.com");

        message.send(
                invited, List.of(), reloaded, new WaitingListInvitation(event.id(), LocalDate.of(2026, 5, 12), null));

        assertEquals("20:00 - 22:00", captureDetails().time());
    }

    @Test
    void anInvitationWithoutAnEveningSaysNothingAboutOne() {
        var invited = entry("Kind", "kind@test.com");

        message.send(invited, List.of(), station, null);

        var details = captureDetails();
        assertEquals("", details.appointmentName());
        assertEquals("", details.date());
    }

    /** An appointment deleted between the invitation and the mail is not a reason to stay silent. */
    @Test
    void anInvitationNamingAGoneAppointmentStillGoesOut() {
        var invited = entry("Kind", "kind@test.com");

        message.send(invited, List.of(), station, new WaitingListInvitation(-1, LocalDate.of(2026, 5, 12), null));

        assertEquals("", captureDetails().appointmentName());
    }

    @Test
    void theAppointmentsOwnLocationIsWhereItSays() {
        var event = appointment("Ortstermin");
        eventFieldRepo.create(
                event.id(),
                "Treffpunkt",
                EventFieldType.LOCATION,
                EventFieldConfig.empty(),
                "Am Hof 3",
                0,
                false,
                null,
                false);
        var invited = entry("Kind", "kind@test.com");

        message.send(invited, List.of(), station, new WaitingListInvitation(event.id(), LocalDate.now(), null));

        assertEquals("Am Hof 3", captureDetails().location());
    }

    /** Without a meeting point on the appointment, the station's published address answers "wo". */
    @Test
    void theStationsAddressAnswersWhenTheAppointmentDoesNot() {
        var addressed = stationRepo.create("AddressedStation");
        stationRepo.updateLocation(addressed.id(), "Feuerwache 1", "12345", "Musterstadt", "DE", null, null);
        var reloaded = stationRepo.findById(addressed.id()).orElseThrow();
        var event = appointment("Ohne Ort");
        var invited = entry("Kind", "kind@test.com");

        message.send(invited, List.of(), reloaded, new WaitingListInvitation(event.id(), LocalDate.now(), null));

        assertEquals("Feuerwache 1, 12345 Musterstadt", captureDetails().location());
    }

    @Test
    void aStationWithNoAddressLeavesTheQuestionOpen() {
        var event = appointment("Ohne alles");
        var invited = entry("Kind", "kind@test.com");

        message.send(invited, List.of(), station, new WaitingListInvitation(event.id(), LocalDate.now(), null));

        assertEquals("", captureDetails().location());
    }

    @Test
    void everyGuardianWithAnAddressIsWrittenTo() {
        var invited = entry("Kind", "kind@test.com");
        waitingListRepo.createGuardian(invited.id(), "Mutter", "Muster", "mutter@test.com", "", 0);
        waitingListRepo.createGuardian(invited.id(), "Vater", "Muster", "vater@test.com", "", 1);
        waitingListRepo.createGuardian(invited.id(), "Oma", "Muster", "", "", 2);

        message.send(invited, waitingListRepo.findGuardiansByEntry(invited.id()), station, null);

        verify(emailService, times(2))
                .sendWaitlistInvitationEmail(
                        anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
        verify(emailService)
                .sendWaitlistInvitationEmail(
                        eq("mutter@test.com"), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    /** The entry's own address is the fallback, not a second copy beside the guardians. */
    @Test
    void theEntrysOwnAddressIsUsedWhenNoGuardianLeftOne() {
        var invited = entry("Kind", "kind@test.com");

        message.send(invited, List.of(), station, null);

        verify(emailService)
                .sendWaitlistInvitationEmail(
                        eq("kind@test.com"), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void nobodyToWriteToMeansNoMail() {
        var invited = entry("Kind", "");

        message.send(invited, List.of(), station, null);

        verifyNoInteractions(emailService);
    }

    /** A guardian who left no name is written to under the name of the person on the list. */
    @Test
    void aNamelessGuardianIsAddressedByTheApplicantsName() {
        var invited = entry("Kind", "kind@test.com");
        waitingListRepo.createGuardian(invited.id(), "", "", "still@test.com", "", 0);

        message.send(invited, waitingListRepo.findGuardiansByEntry(invited.id()), station, null);

        verify(emailService)
                .sendWaitlistInvitationEmail(
                        eq("still@test.com"), eq("Kind Muster"), anyString(), anyString(), anyString(), any(), any());
    }
}
