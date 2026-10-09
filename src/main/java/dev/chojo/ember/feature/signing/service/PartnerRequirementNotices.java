/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.signing.entity.PartnerRequirementChange;
import dev.chojo.ember.feature.signing.entity.RemoteRegisteredMember;
import dev.chojo.ember.feature.signing.entity.RemoteRequirementChange;
import dev.chojo.ember.feature.signing.entity.RemoteRequirementChangeAnswer;
import dev.chojo.ember.feature.signing.repository.PartnerAgreementRepository;
import dev.chojo.ember.feature.signing.repository.PartnerRequirementChangeRepository;
import dev.chojo.ember.feature.signing.route.RemoteSigningRoutes;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.Executor;

/**
 * Tells the partner stations of a shared appointment that the documents it asks for changed, at the station
 * holding the appointment, so each partner asks its members already registered for a document added and lets
 * the open requests of one taken off go ({@link PartnerSignatures} at the partner).
 *
 * <p>Told are the partners with a member holding a place on a date still ahead, today and later in this
 * station's zone, and those that took a document of such a date on. Every change for a partner is gathered
 * until it took the notice: the documents taken off, without those added back since. The notice is a
 * federation request signed with this station's federation key, and names the partner's members holding a
 * place on a date still ahead as they stand when it is sent. Where the partner let a request go, the record that
 * it took the document on is forgotten here; what came back signed stays.
 *
 * <p>The notice is sent at once, outside the change that caused it. A partner that cannot be reached is told
 * again by a sweep, later after every failure in a row like the signed copies that travel the other way
 * ({@link PartnerDeliveries}), and given up after {@link PartnerDeliveries#MAX_ATTEMPTS} failures until the
 * next change. A partner the appointment is no longer shared with is not told.
 */
@Singleton
public class PartnerRequirementNotices implements TaskSource {
    private static final Duration START_DELAY = Duration.ofMinutes(4);
    private static final Duration INTERVAL = Duration.ofMinutes(5);
    private static final int SWEEP_LIMIT = 50;
    private static final Logger log = LoggerFactory.getLogger(PartnerRequirementNotices.class);

    private final PartnerRequirementChangeRepository changes;
    private final PartnerAgreementRepository agreements;
    private final EventFederationService federation;
    private final FederationRepository partners;
    private final StationRepository stations;
    private final FederationTransport transport;
    private final Executor executor;
    private final Clock clock;

    @Inject
    public PartnerRequirementNotices(
            PartnerRequirementChangeRepository changes,
            PartnerAgreementRepository agreements,
            EventFederationService federation,
            FederationRepository partners,
            StationRepository stations,
            FederationTransport transport,
            TaskScheduler scheduler) {
        this(changes, agreements, federation, partners, stations, transport, scheduler.executor(), Clock.systemUTC());
    }

    /** Builds the service with its own executor and clock, so a test sends on the calling thread. */
    PartnerRequirementNotices(
            PartnerRequirementChangeRepository changes,
            PartnerAgreementRepository agreements,
            EventFederationService federation,
            FederationRepository partners,
            StationRepository stations,
            FederationTransport transport,
            Executor executor,
            Clock clock) {
        this.changes = changes;
        this.agreements = agreements;
        this.federation = federation;
        this.partners = partners;
        this.stations = stations;
        this.transport = transport;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "partner-requirement-notices", Schedule.fixedDelay(START_DELAY, INTERVAL), this::sweep));
    }

    /**
     * Notes a change of an appointment's documents for every partner concerned and tells them in the
     * background.
     *
     * @param stationId the station holding the appointment
     * @param eventId   the appointment
     * @param added     the templates it asks for now and did not before
     * @param removed   the templates it asked for before and no longer does
     * @return how many partners are to be told
     */
    public int changed(int stationId, int eventId, Collection<Integer> added, Collection<Integer> removed) {
        var concerned = concernedPartners(eventId, today(stationId));
        for (int partnerId : concerned) changes.note(stationId, eventId, partnerId, removed, added);
        if (!concerned.isEmpty())
            executor.execute(() -> changes.forEvent(eventId).forEach(this::deliver));
        return concerned.size();
    }

    /**
     * Tells what is due and has not reached its partner yet.
     *
     * @return how many notices reached their partner
     */
    int sweep() {
        int reached = 0;
        for (var change : changes.due(clock.instant(), PartnerDeliveries.MAX_ATTEMPTS, SWEEP_LIMIT)) {
            if (deliver(change)) reached++;
        }
        return reached;
    }

    /**
     * Tells the partner of a change, and forgets the records of documents it let go.
     *
     * @return whether the partner took it
     */
    boolean deliver(PartnerRequirementChange change) {
        var partner = activePartner(change.partnerId());
        if (partner.isEmpty()) return failed(change, "the partnership is not active");
        if (!sharedWith(partner.get(), change.eventId())) {
            changes.drop(change.id());
            return false;
        }
        try {
            var answer = transport.send(
                    partner.get(),
                    RemoteSigningRoutes.REQUIREMENTS_CHANGED.at(change.eventId()),
                    noticeOf(change, partner.get()),
                    RemoteRequirementChangeAnswer.class);
            forgetReleased(change.eventId(), partner.get(), answer);
            changes.delivered(change);
            log.info(
                    "Partner {} took the change of the documents of appointment {}",
                    change.partnerId(),
                    change.eventId());
            return true;
        } catch (RuntimeException e) {
            return failed(change, String.valueOf(e.getMessage()));
        }
    }

    private boolean failed(PartnerRequirementChange change, String why) {
        var retry = clock.instant().plus(PartnerDeliveries.delayAfter(change.deliveryAttempts()));
        changes.failed(change.id(), retry);
        log.warn(
                "Partner {} did not take the change of the documents of appointment {} (try {}), next at {}: {}",
                change.partnerId(),
                change.eventId(),
                change.deliveryAttempts() + 1,
                retry,
                why);
        return false;
    }

    private RemoteRequirementChange noticeOf(PartnerRequirementChange change, FederationPartner partner) {
        var today = today(partner.stationId());
        var registered = federation.findRegistrations(change.eventId(), null).stream()
                .filter(registration -> registration.partnerId() == partner.id())
                .filter(EventFederationRegistration::isStanding)
                .filter(registration -> !registration.eventDate().isBefore(today))
                .map(registration ->
                        new RemoteRegisteredMember(registration.remoteMemberId(), registration.eventDate()))
                .toList();
        return new RemoteRequirementChange(change.removedTemplateIds(), registered);
    }

    private void forgetReleased(int eventId, FederationPartner partner, RemoteRequirementChangeAnswer answer) {
        for (var released : answer.released()) {
            agreements.release(
                    eventId,
                    released.eventDate(),
                    released.templateId(),
                    partner.partnerStationId(),
                    released.memberUid());
        }
    }

    private TreeSet<Integer> concernedPartners(int eventId, LocalDate from) {
        var concerned = new TreeSet<>(agreements.partnersFrom(eventId, from));
        federation.findRegistrations(eventId, null).stream()
                .filter(EventFederationRegistration::isStanding)
                .filter(registration -> !registration.eventDate().isBefore(from))
                .map(EventFederationRegistration::partnerId)
                .forEach(concerned::add);
        return concerned;
    }

    private boolean sharedWith(FederationPartner partner, int eventId) {
        return federation.findSharedEventIds(partner.id(), partner.stationId()).contains(eventId);
    }

    private Optional<FederationPartner> activePartner(int partnerId) {
        return partners.findPartnerById(partnerId).filter(partner -> partner.status() == FederationStatus.ACTIVE);
    }

    private LocalDate today(int stationId) {
        var zone = StationFormat.timezoneOf(stations.findById(stationId).orElse(null));
        return LocalDate.now(clock.withZone(zone));
    }
}
