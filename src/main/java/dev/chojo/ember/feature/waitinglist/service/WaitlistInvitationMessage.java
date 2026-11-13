/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.mail.entity.WaitlistInvitationDetails;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryGuardian;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What an invitation to come and look says, and writing it to whoever can be written to: the
 * invitation itself, other dates replacing it, the start of the trial and being taken off the list.
 * All four go to the same people, so all four are written here.
 *
 * <p>The page behind the entry link shows the same thing, so both ask this rather than each working
 * the appointment out for itself. An answer given without knowing the occasion is not an answer worth
 * collecting, and "the date does not suit" in particular is about a date that has to be on screen.
 *
 * <p><b>Where "wo" comes from.</b> An appointment has no place of its own. A station that wants one
 * on the invitation says so in one of two ways it already has, and this reads them in that order: a
 * field of type location on the appointment, which is how a one-off meeting point is named, and
 * otherwise the station's postal address, which is an opt-in of its own. When neither is there the
 * invitation says nothing about where, rather than guessing at an address the station never
 * published.
 */
@Singleton
public class WaitlistInvitationMessage {

    private final EventRepository eventRepository;
    private final EventFieldRepository eventFieldRepository;
    private final EmailService emailService;

    @Inject
    public WaitlistInvitationMessage(
            EventRepository eventRepository, EventFieldRepository eventFieldRepository, EmailService emailService) {
        this.eventRepository = eventRepository;
        this.eventFieldRepository = eventFieldRepository;
        this.emailService = emailService;
    }

    /**
     * Sends the invitation to everybody on the entry who left an address.
     *
     * @param entry      the entry being invited
     * @param guardians  whoever answers for them, written to in preference to the entry itself
     * @param station    the station doing the inviting, which decides the language and the zone
     * @param invitation the appointment they are asked to, or {@code null} for an invitation naming
     *                   none
     */
    public void send(
            WaitingListEntry entry,
            List<WaitingListEntryGuardian> guardians,
            Station station,
            WaitingListInvitation invitation) {
        var details = describe(station, invitation);
        String language = StationFormat.languageOf(station);
        for (var recipient : recipients(entry, guardians)) {
            emailService.sendWaitlistInvitationEmail(
                    recipient.email(),
                    recipient.name(),
                    entry.accessToken(),
                    station.name(),
                    language,
                    station.id(),
                    details);
        }
    }

    /**
     * Sends the dates that replace the ones offered before to everybody on the entry who left an
     * address.
     *
     * @param invitation the new dates and the station's note
     */
    public void sendOtherDates(
            WaitingListEntry entry,
            List<WaitingListEntryGuardian> guardians,
            Station station,
            WaitingListInvitation invitation) {
        var details = describe(station, invitation);
        String language = StationFormat.languageOf(station);
        for (var recipient : recipients(entry, guardians)) {
            emailService.sendWaitlistOtherDatesEmail(
                    recipient.email(),
                    recipient.name(),
                    entry.accessToken(),
                    station.name(),
                    language,
                    station.id(),
                    details);
        }
    }

    /**
     * Tells everybody on the entry who left an address that the trial has begun.
     *
     * @param message what the station wrote to them, empty for nothing
     */
    public void sendTrialStarted(
            WaitingListEntry entry, List<WaitingListEntryGuardian> guardians, Station station, String message) {
        String appointment = pickedAppointment(station, entry);
        String language = StationFormat.languageOf(station);
        for (var recipient : recipients(entry, guardians)) {
            emailService.sendWaitlistTrialStartedEmail(
                    recipient.email(),
                    recipient.name(),
                    entry.accessToken(),
                    station.name(),
                    language,
                    station.id(),
                    appointment,
                    message);
        }
    }

    /**
     * Tells everybody on the entry who left an address that the station has taken them off the list.
     *
     * <p>Read before the entry is deleted and sent after, since the addresses go with the entry.
     *
     * @param recipients  the addresses as they were before the entry went
     * @param message     what the station wrote to them, empty for nothing
     * @param duringTrial whether a running trial ended with it, which the mail says in its own words
     */
    public void sendRemoved(List<Recipient> recipients, Station station, String message, boolean duringTrial) {
        String language = StationFormat.languageOf(station);
        for (var recipient : recipients) {
            emailService.sendWaitlistRemovedEmail(
                    recipient.email(), recipient.name(), station.name(), language, station.id(), message, duringTrial);
        }
    }

    /**
     * The date a coming answer picked, in one line, or empty when the entry picked none.
     */
    private String pickedAppointment(Station station, WaitingListEntry entry) {
        var invitation = entry.invitation();
        var answer = entry.answer();
        Integer position = answer == null ? null : answer.position();
        if (invitation == null || position == null) return "";
        return invitation
                .option(position)
                .map(picked -> summary(station, picked))
                .orElse("");
    }

    /**
     * One offered date in one line, as the station and the family are told it, or empty when its
     * appointment is gone.
     */
    public String summary(Station station, WaitingListInvitation.OfferedDate date) {
        return describe(station, new WaitingListInvitation("", List.of(date))).appointments().stream()
                .findFirst()
                .map(WaitlistInvitationDetails.Appointment::summary)
                .orElse("");
    }

    /**
     * What the invitation can say about the dates it offers, all of it already written out for a
     * reader.
     *
     * <p>A date whose appointment has since been deleted is left out rather than failing the whole
     * invitation: the person still has to be told they are invited, and to the dates that remain.
     */
    public WaitlistInvitationDetails describe(Station station, @Nullable WaitingListInvitation invitation) {
        if (invitation == null) return WaitlistInvitationDetails.NONE;

        Locale locale = StationFormat.localeOf(station);
        ZoneId zone = StationFormat.timezoneOf(station);
        var dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        var timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale);

        var appointments = new ArrayList<WaitlistInvitationDetails.Appointment>();
        for (var option : invitation.options()) {
            var event = eventRepository.findById(option.eventId()).orElse(null);
            if (event == null) continue;
            appointments.add(new WaitlistInvitationDetails.Appointment(
                    option.position(),
                    event.name(),
                    dateFormat.format(option.date()),
                    span(event, zone, timeFormat),
                    option.arrivalTime() == null ? "" : timeFormat.format(option.arrivalTime()),
                    location(event, station, option.date())));
        }
        return new WaitlistInvitationDetails(appointments, invitation.message());
    }

    /** When the appointment itself runs, in the zone the station keeps its times in. */
    private static String span(StationEvent event, ZoneId zone, DateTimeFormatter format) {
        return format.format(LocalTime.from(event.startTime().atZone(zone)))
                + " - "
                + format.format(LocalTime.from(event.endTime().atZone(zone)));
    }

    private String location(StationEvent event, Station station, LocalDate date) {
        return eventFieldRepository.findShownOn(event.id(), date).stream()
                .filter(field -> field.fieldType() == FieldType.LOCATION)
                .map(AppointmentField::value)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst()
                .orElseGet(() -> postalAddress(station));
    }

    /** The station's own address, which only exists once the station has published one. */
    private static String postalAddress(Station station) {
        var parts = new ArrayList<String>();
        if (station.addressLine() != null && !station.addressLine().isBlank()) {
            parts.add(station.addressLine().trim());
        }
        String town = joinBlankSafe(station.postalCode(), station.city());
        if (!town.isBlank()) parts.add(town);
        return String.join(", ", parts);
    }

    private static String joinBlankSafe(String first, String second) {
        return ((first == null ? "" : first.trim()) + " " + (second == null ? "" : second.trim())).trim();
    }

    /**
     * The addresses the invitation would be written to, so the person sending it can see them first.
     */
    public static List<String> recipientAddresses(WaitingListEntry entry, List<WaitingListEntryGuardian> guardians) {
        return recipients(entry, guardians).stream().map(Recipient::email).toList();
    }

    /**
     * Whoever the invitation goes to: every guardian who left an address, and the entry's own
     * address only when no guardian did.
     */
    public static List<Recipient> recipients(WaitingListEntry entry, List<WaitingListEntryGuardian> guardians) {
        var found = guardians.stream()
                .filter(guardian ->
                        guardian.email() != null && !guardian.email().isBlank())
                .map(guardian -> new Recipient(
                        guardian.email(), guardian.firstname().isBlank() ? entry.fullName() : guardian.fullName()))
                .toList();
        if (!found.isEmpty()) return found;
        if (entry.email() == null || entry.email().isBlank()) return List.of();
        return List.of(
                new Recipient(entry.email(), entry.parentName().isBlank() ? entry.fullName() : entry.parentName()));
    }

    /**
     * One person a message about the entry is written to.
     *
     * @param email where it goes
     * @param name  who it greets
     */
    public record Recipient(String email, String name) {}
}
