/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which place an appointment shows: the calendar takes the meeting point and falls back to the
 * venue, a document's venue takes the other place and falls back to the meeting point.
 */
class CalendarLocationTest {
    private static final EventQuestionSettings MEETING_POINT =
            EventQuestionSettings.parse("{\"calendarLocation\":true}");

    private static AppointmentField place(int id, String name, String value, EventQuestionSettings settings) {
        return new AppointmentField(id, 10, name, FieldType.LOCATION, settings, value, id, true, null, true);
    }

    private static final AppointmentField VENUE = place(1, "Ort", " Gerätehaus ", EventQuestionSettings.empty());
    private static final AppointmentField MEETING = place(2, "Treffpunkt", "Bahnhof", MEETING_POINT);

    /** With both places answered, the calendar shows the meeting point and a document the venue. */
    @Test
    void bothPlacesAnswered() {
        var fields = List.of(VENUE, MEETING);
        assertEquals("Bahnhof", AppointmentField.calendarLocation(fields));
        assertEquals("Gerätehaus", AppointmentField.venue(fields));
    }

    /** A meeting point left empty leaves the calendar the venue. */
    @Test
    void anEmptyMeetingPointFallsBackToTheVenue() {
        var fields = List.of(VENUE, place(2, "Treffpunkt", " ", MEETING_POINT));
        assertEquals("Gerätehaus", AppointmentField.calendarLocation(fields));
        assertEquals("Gerätehaus", AppointmentField.venue(fields));
    }

    /** The meeting point as the only place answered is the venue too. */
    @Test
    void onlyTheMeetingPointAnswered() {
        var fields = List.of(place(1, "Ort", "", EventQuestionSettings.empty()), MEETING);
        assertEquals("Bahnhof", AppointmentField.calendarLocation(fields));
        assertEquals("Bahnhof", AppointmentField.venue(fields));
    }

    /** Without a marked place, both take the first place answered, as before. */
    @Test
    void nothingMarked() {
        var fields = List.of(VENUE, place(2, "Treffpunkt", "Bahnhof", EventQuestionSettings.empty()));
        assertEquals("Gerätehaus", AppointmentField.calendarLocation(fields));
        assertEquals("Gerätehaus", AppointmentField.venue(fields));
    }

    /**
     * An appointment of an event group that marks a place of its own shows that one, not the meeting
     * point its group hands down; the group's places stay the venue.
     */
    @Test
    void anOwnMarkWinsOverTheGroups() {
        var inheritedVenue = new AppointmentField(
                AppointmentField.inheritedId(5),
                10,
                "Ort",
                FieldType.LOCATION,
                EventQuestionSettings.empty(),
                "Gerätehaus",
                0,
                true,
                null,
                true);
        var inheritedMeeting = new AppointmentField(
                AppointmentField.inheritedId(6),
                10,
                "Treffpunkt",
                FieldType.LOCATION,
                MEETING_POINT,
                "Bahnhof",
                1,
                true,
                null,
                true);
        var own = place(7, "Sammelplatz", "Schule", MEETING_POINT);
        var fields = List.of(inheritedVenue, inheritedMeeting, own);

        assertEquals("Schule", AppointmentField.calendarLocation(fields));
        assertEquals("Gerätehaus", AppointmentField.venue(fields));
    }

    /** The places beside the one an entry shows are the rest, in their order. */
    @Test
    void theOtherPlacesLeaveOutTheNamedOne() {
        assertEquals(List.of(VENUE), AppointmentField.otherPlaces(List.of(VENUE, MEETING), "Bahnhof"));
        assertEquals(List.of(VENUE, MEETING), AppointmentField.otherPlaces(List.of(VENUE, MEETING), null));
    }

    /** An appointment without a place answered shows none. */
    @Test
    void noPlaceAtAll() {
        var text = new AppointmentField(
                3, 10, "Thema", FieldType.TEXT, EventQuestionSettings.empty(), "Übung", 0, true, null, true);
        assertNull(AppointmentField.calendarLocation(List.of(text)));
        assertNull(AppointmentField.venue(List.of()));
    }

    /** Being the calendar's location stays with a place question only, and two such questions are counted. */
    @Test
    void onlyAPlaceIsTheCalendarsLocation() {
        assertTrue(MEETING_POINT.organisers(FieldType.LOCATION).calendarLocation());
        assertFalse(MEETING_POINT.organisers(FieldType.TEXT).calendarLocation());
        assertFalse(MEETING_POINT.registrants().calendarLocation());
        assertTrue(EventQuestionSettings.claimMoreThanOneCalendarLocation(List.of(MEETING_POINT, MEETING_POINT)));
        assertFalse(EventQuestionSettings.claimMoreThanOneCalendarLocation(
                List.of(MEETING_POINT, EventQuestionSettings.empty())));
    }
}
