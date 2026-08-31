/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.mail.entity.WaitlistInvitationDetails;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryGuardian;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes the invitation to come and look, and works out what it can say about the evening.
 *
 * <p><b>Where "wo" comes from.</b> An appointment has no place of its own. A station that wants one
 * on the invitation says so in one of two ways it already has, and this reads them in that order: a
 * field of type location on the appointment, which is how a one-off meeting point is named, and
 * otherwise the station's postal address, which is an opt-in of its own. When neither is there the
 * mail says nothing about where, rather than guessing at an address the station never published.
 */
@Singleton
public class WaitlistInvitationMailer {

    private final EventRepository eventRepository;
    private final EventFieldRepository eventFieldRepository;
    private final EmailService emailService;

    @Inject
    public WaitlistInvitationMailer(
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
     * @param invitation the evening they are asked to, or {@code null} for an invitation naming none
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
     * What the mail can say about the evening, all of it already written out for a reader.
     *
     * <p>An invitation whose appointment has since been deleted describes nothing rather than
     * failing: the person still has to be told they are invited.
     */
    private WaitlistInvitationDetails describe(Station station, WaitingListInvitation invitation) {
        if (invitation == null) return WaitlistInvitationDetails.NONE;
        var event = eventRepository.findById(invitation.eventId()).orElse(null);
        if (event == null) return WaitlistInvitationDetails.NONE;

        Locale locale = StationFormat.localeOf(station);
        ZoneId zone = StationFormat.timezoneOf(station);
        var dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        var timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale);

        return new WaitlistInvitationDetails(
                event.name(),
                dateFormat.format(invitation.date()),
                span(event, zone, timeFormat),
                invitation.arrivalTime() == null ? "" : timeFormat.format(invitation.arrivalTime()),
                location(event, station));
    }

    /** When the appointment itself runs, in the zone the station keeps its times in. */
    private static String span(StationEvent event, ZoneId zone, DateTimeFormatter format) {
        return format.format(LocalTime.from(event.startTime().atZone(zone)))
                + " - "
                + format.format(LocalTime.from(event.endTime().atZone(zone)));
    }

    private String location(StationEvent event, Station station) {
        return eventFieldRepository.findByEvent(event.id()).stream()
                .filter(field -> field.fieldType() == EventFieldType.LOCATION)
                .map(EventField::value)
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
     * Whoever the invitation goes to: every guardian who left an address, and the entry's own
     * address only when no guardian did.
     */
    private static List<Recipient> recipients(WaitingListEntry entry, List<WaitingListEntryGuardian> guardians) {
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

    private record Recipient(String email, String name) {}
}
