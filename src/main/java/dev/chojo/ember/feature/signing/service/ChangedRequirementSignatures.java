/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Carries a change of the documents an appointment asks for over to the participants already registered,
 * on the dates still ahead: today and later in the station's zone, each date of a series on its own. Past
 * dates stay as they were.
 *
 * <p>A document added asks every member holding a place, pending or accepted, for its signatures, exactly as
 * registering does ({@link AppointmentSignatures#askForAdded}): the copy is generated, everybody asked is
 * told, reminded, and finds it among their open tasks, and the issuer's field signs itself where it can.
 * Nothing is asked again where a copy already has its signatures asked for or signed, or its signed paper
 * copy was confirmed. An appointment that takes no registrations offers its agreements on its page, so
 * nobody is asked there.
 *
 * <p>A document taken off withdraws what is still open on its copies; signed fields stay, reminders stop and
 * the requests already out are taken back.
 *
 * <p>A participant who cannot be asked is logged and left out; the change to the appointment stands.
 */
@Singleton
public class ChangedRequirementSignatures {
    private static final Logger log = LoggerFactory.getLogger(ChangedRequirementSignatures.class);

    private final EventRepository events;
    private final EventRegistrationRepository registrations;
    private final StationRepository stations;
    private final AppointmentSignatures signatures;
    private final SignatureRequestRepository requests;
    private final SignatureRequestService requestService;
    private final Clock clock;

    @Inject
    public ChangedRequirementSignatures(
            EventRepository events,
            EventRegistrationRepository registrations,
            StationRepository stations,
            AppointmentSignatures signatures,
            SignatureRequestRepository requests,
            SignatureRequestService requestService) {
        this(events, registrations, stations, signatures, requests, requestService, Clock.systemUTC());
    }

    /** Builds the service with its own clock, so a test decides which dates are still ahead. */
    ChangedRequirementSignatures(
            EventRepository events,
            EventRegistrationRepository registrations,
            StationRepository stations,
            AppointmentSignatures signatures,
            SignatureRequestRepository requests,
            SignatureRequestService requestService,
            Clock clock) {
        this.events = events;
        this.registrations = registrations;
        this.stations = stations;
        this.signatures = signatures;
        this.requests = requests;
        this.requestService = requestService;
        this.clock = clock;
    }

    /**
     * Asks the members holding a place on a date still ahead for the signatures of documents added to the
     * appointment.
     *
     * @param stationId   the station of the appointment
     * @param eventId     the appointment
     * @param templateIds the documents added
     * @return how many documents were asked for
     */
    public int added(int stationId, int eventId, Collection<Integer> templateIds) {
        var event = events.findById(eventId).orElse(null);
        if (event == null || !event.requiresRegistration() || templateIds.isEmpty()) return 0;
        // TODO: members of partner stations registered before a document is added are not asked for it.
        int asked = 0;
        for (var date : registrations.findDatesFrom(eventId, today(stationId))) {
            for (int memberId : registrations.findRegisteredMemberIds(eventId, date)) {
                asked += askForAdded(eventId, date, memberId, templateIds);
            }
        }
        log.info("Asked for {} added document(s) of appointment {}", asked, eventId);
        return asked;
    }

    /**
     * Withdraws what is still open on the copies of documents taken off the appointment, on the dates still
     * ahead.
     *
     * @param stationId   the station of the appointment
     * @param eventId     the appointment
     * @param templateIds the documents taken off
     * @return how many requests were withdrawn
     */
    public int removed(int stationId, int eventId, Collection<Integer> templateIds) {
        if (templateIds.isEmpty()) return 0;
        int withdrawn = 0;
        for (var open : requests.openForDocumentsFrom(eventId, templateIds, today(stationId))) {
            if (withdraw(eventId, open.request().id())) withdrawn++;
        }
        log.info("Withdrew {} request(s) of documents taken off appointment {}", withdrawn, eventId);
        return withdrawn;
    }

    private int askForAdded(int eventId, LocalDate date, int memberId, Collection<Integer> templateIds) {
        try {
            return signatures.askForAdded(eventId, date, memberId, templateIds);
        } catch (RuntimeException e) {
            log.warn(
                    "Could not ask member {} for the documents added to appointment {} on {}",
                    memberId,
                    eventId,
                    date,
                    e);
            return 0;
        }
    }

    private boolean withdraw(int eventId, int requestId) {
        try {
            return requestService.withdrawUnasked(requestId);
        } catch (RuntimeException e) {
            log.warn("Could not withdraw request {} of a document taken off appointment {}", requestId, eventId, e);
            return false;
        }
    }

    private LocalDate today(int stationId) {
        var zone = StationFormat.timezoneOf(stations.findById(stationId).orElse(null));
        return LocalDate.now(clock.withZone(zone));
    }
}
