/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.PartnerRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.Places;
import dev.chojo.ember.feature.events.entity.PlacesKind;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.SharedEvent;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The host side of shared appointments: what this station serves its partners about the appointments
 * it shares with them, and the registrations their members hold here.
 *
 * <p>What a partner may see and do is decided in the serving functions registered in
 * {@link #serveOn}. A partner on another instance reaches them through the {@code /remote} routes, a
 * partner on this instance through the local transport, and both are held to the share targets and
 * the rows of the station holding the appointment. The same registrations are decided here by whoever
 * runs the appointment: accepted, turned down, put on the waiting list or moved within it.
 */
@Singleton
public class EventPartnerRegistrationService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(EventPartnerRegistrationService.class);

    private final EventFederationRepository federationRepository;
    private final EventShareService shares;
    private final PartnerPlacesService places;
    private final EventCrudService crudService;
    private final EventAttachmentService attachmentService;
    private final EventFieldService fieldService;
    private final OccurrenceCalendar occurrenceCalendar;
    private final MediaLibraryService media;
    private final Api apiConfig;
    private final EventGroupRepository eventGroupRepository;
    private final EventRegistrationService registrationService;
    private final PartnerQuestionService partnerQuestions;
    private final DomainEventBus eventBus;

    @Inject
    public EventPartnerRegistrationService(
            EventFederationRepository federationRepository,
            EventShareService shares,
            PartnerPlacesService places,
            EventCrudService crudService,
            EventAttachmentService attachmentService,
            EventFieldService fieldService,
            OccurrenceCalendar occurrenceCalendar,
            MediaLibraryService media,
            Api apiConfig,
            EventGroupRepository eventGroupRepository,
            EventRegistrationService registrationService,
            PartnerQuestionService partnerQuestions,
            DomainEventBus eventBus) {
        this.federationRepository = federationRepository;
        this.shares = shares;
        this.places = places;
        this.crudService = crudService;
        this.attachmentService = attachmentService;
        this.fieldService = fieldService;
        this.occurrenceCalendar = occurrenceCalendar;
        this.media = media;
        this.apiConfig = apiConfig;
        this.eventGroupRepository = eventGroupRepository;
        this.registrationService = registrationService;
        this.partnerQuestions = partnerQuestions;
        this.eventBus = eventBus;
    }

    /**
     * Registers the serving functions of the appointments this station shares, apart from their
     * comments, which {@link EventCommentFederation} serves.
     */
    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(RemoteEventRoutes.LIST_EVENTS, (partner, params, body) -> serveEvents(partner));
        endpoints.serve(
                RemoteEventRoutes.GET_EVENT, (partner, params, body) -> serveEvent(partner, params.integer("id")));
        endpoints.serve(
                RemoteEventRoutes.LIST_ATTACHMENTS,
                (partner, params, body) -> serveAttachments(partner, params.integer("eventId")));
        endpoints.serve(
                RemoteEventRoutes.GET_ATTACHMENT_CONTENT,
                (partner, params, body) ->
                        serveAttachmentContent(partner, params.integer("eventId"), params.integer("attachmentId")));
        endpoints.<RemoteEventRoutes.RemoteRegistrationRequest, EventFederationRegistration>serve(
                RemoteEventRoutes.REGISTER,
                (partner, params, body) -> serveRegistration(partner, params.integer("id"), body));
        endpoints.<RemoteEventRoutes.RemoteRegistrationRequest, Void>serve(
                RemoteEventRoutes.WITHDRAW, (partner, params, body) -> {
                    serveWithdrawal(partner, params.integer("id"), body);
                    return null;
                });
        endpoints.<RemoteEventRoutes.RemoteRegistrationRequest, Void>serve(
                RemoteEventRoutes.UNDO_WITHDRAWAL, (partner, params, body) -> {
                    serveUndoWithdrawal(partner, params.integer("id"), body);
                    return null;
                });
        endpoints.<RemoteEventRoutes.RemoteRegistrationRequest, RemoteEventRoutes.RemoteConfirmedOwn>serve(
                RemoteEventRoutes.CONFIRM_OWN, (partner, params, body) -> {
                    int eventId = params.integer("id");
                    serveConfirmOwn(partner, eventId, body);
                    return confirmedOwn(partner, eventId, body);
                });
        endpoints.serve(
                RemoteEventRoutes.LIST_REGISTRATIONS,
                (partner, params, body) -> serveRegistrations(partner, params.integer("id")));
        endpoints.serve(
                RemoteEventRoutes.LIST_MEMBER_REGISTRATIONS,
                (partner, params, body) -> serveMemberRegistrations(partner, params.uuid("memberUid")));
    }

    /**
     * Registers a federated member for an event occurrence.
     *
     * <p>The status is the event's to decide, exactly as it is for a member of this station: an event
     * that asks for no confirmation accepts at once, and one that asks holds the registration until
     * somebody answers. Without this the row took the column default and every visitor waited for a
     * confirmation on events that ask nobody for one, which nobody at the host was ever prompted to
     * give.
     *
     * <p>A partner with places of its own is the other reason somebody has to choose. Accepting at
     * once there would hand out more places than the partner was given, since nothing would ever
     * count them: the arrangement is itself the reason to pick, whatever the appointment asks of this
     * station's own members.
     *
     * <p>The door is asked here rather than at the route, because two stations that happen to sit on
     * one instance never go through the remote one. Asking it in the one place both paths share is
     * what keeps a visitor's answer the same wherever their station is kept.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param eventDate      the event occurrence date
     * @return the created registration
     */
    public EventFederationRegistration registerFederated(
            int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        return registerFederated(eventId, partnerId, remoteMemberId, eventDate, null);
    }

    /**
     * Registers a federated member for an event occurrence with their answers to its questions.
     *
     * <p>The answers are checked before anything is written, as for this station's own members, and
     * stored with a registration this creates. A member who already holds or waits for a place keeps
     * the answers they gave. A partner that sends no answers is held to the questions all the same, see
     * {@link PartnerQuestionService}.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param eventDate      the event occurrence date
     * @param answers        the answers as sent, or null where the partner sent none
     * @return the created registration
     */
    public EventFederationRegistration registerFederated(
            int eventId,
            int partnerId,
            UUID remoteMemberId,
            LocalDate eventDate,
            @Nullable List<EventRegistrationFieldValue> answers) {
        var event =
                crudService.findById(eventId).orElseThrow(EventRefusal.EVENT_NOT_HERE_FOR_PARTNER_REGISTRATION::raise);
        registrationService.requireOwnSignUp(eventId);
        requireOpenForRegistration(event, eventDate);
        var resolved = partnerQuestions.resolve(eventId, answers);
        boolean somebodyChooses = event.requiresConfirmation()
                || !places.arrangement(PlacesKind.APPOINTMENT, eventId, partnerId)
                        .decidedByHost();
        var moved = new ArrayList<EventRegistration>();
        var registration = registrationService.underSignUpLock(eventId, () -> {
            var standing = federationRepository
                    .findRegistration(eventId, partnerId, remoteMemberId, eventDate)
                    .filter(existing -> existing.status() == RegistrationStatus.QUEUED
                            || existing.status() == RegistrationStatus.ACCEPTED);
            if (standing.isPresent()) return standing.get();
            var decided = somebodyChooses
                    ? RegistrationStatus.PENDING
                    : registrationService.statusForVisitor(event, eventDate);
            var created =
                    federationRepository.createRegistration(eventId, partnerId, remoteMemberId, eventDate, decided);
            partnerQuestions.store(created.id(), resolved);
            if (decided == RegistrationStatus.QUEUED) {
                registrationService.enqueueVisitor(event, eventDate, created.id(), false);
                moved.addAll(registrationService.fillLocked(eventId, eventDate));
                return federationRepository.findRegistrationById(created.id()).orElse(created);
            }
            return created;
        });
        registrationService.tellMovedUp(moved);
        var status = registration.status();
        log.info(
                "Registered federated member for event {} from partner {} on {} as {}",
                eventId,
                partnerId,
                eventDate,
                status);
        return registration;
    }

    /**
     * The questions a member of this station answers before they are on a list, asked of a visitor
     * too.
     *
     * <p>Being shared with is what makes somebody eligible from another station, and that is checked
     * before this. Everything else the local door asks applies just as much to a visitor: an event
     * that takes no registrations has no list to join, a date the appointment does not fall on, or
     * that was called off on its own or with its whole series, is not one to join, and a deadline that
     * has passed has passed for everybody. Without these the host's list filled up with people its own
     * door would have turned away.
     *
     * <p>The date is asked the way the local door asks it, and only asked: what a partner names is
     * still what the registration is filed under, so the wire format stays as it was.
     *
     * <p>There is no equivalent of the eligibility check. Restrictions are written in terms of this
     * station's members and groups, and a visitor is in none of them; the host said who may come when
     * it chose whom to share with.
     */
    private void requireOpenForRegistration(StationEvent event, LocalDate eventDate) {
        if (!event.requiresRegistration()) {
            throw EventRefusal.EVENT_TAKES_NO_PARTNER_REGISTRATIONS.raise();
        }
        occurrenceCalendar.dateToAnswerFor(event, eventDate);
        if (occurrenceCalendar.registrationClosed(event, eventDate)) {
            throw EventRefusal.REGISTRATION_CLOSED_TO_PARTNER.raise();
        }
    }

    /**
     * The registrations a partner's member holds here, whatever partner sent them.
     *
     * @param remoteMemberId the member
     * @return every registration filed under that member
     */
    public List<EventFederationRegistration> findRegistrationsByRemoteMember(UUID remoteMemberId) {
        return federationRepository.findRegistrationsByRemoteMember(remoteMemberId);
    }

    /**
     * Updates the status of a federated registration.
     *
     * @param id     the registration ID
     * @param status the new status
     * @return true if a row was updated
     */
    public boolean updateRegistrationStatus(int id, RegistrationStatus status) {
        var found = federationRepository.findRegistrationById(id).orElse(null);
        if (found == null) {
            log.warn("Cannot update federated registration status: registration {} not found", id);
            return false;
        }
        var before = registrationService.underSignUpLock(found.eventId(), () -> {
            var current = federationRepository.findRegistrationById(id).orElse(found);
            return federationRepository.updateRegistrationStatus(id, status) ? current : null;
        });
        if (before != null) {
            log.info("Updated federated registration {} status to {}", id, status);
            if (before.status() == RegistrationStatus.ACCEPTED && status != RegistrationStatus.ACCEPTED) {
                registrationService.moveUp(before.eventId(), before.eventDate());
            }
            return true;
        }
        log.warn("Cannot update federated registration status: registration {} not found", id);
        return false;
    }

    /**
     * Confirms or turns down a partner's member, which is this station's to do unless it handed the
     * decision to the partner.
     *
     * <p>Where a partner decides its own, this station keeps the list but not the pen: taking the
     * decision back is a change to the arrangement rather than something done one member at a time
     * behind the partner's back. Accepting also spends one of that partner's places, so a host
     * confirming somebody cannot put the partner over the number it was given.
     *
     * @param id        the partner's registration, already checked to be on one of the station's appointments
     * @param status    ACCEPTED or DENIED
     * @param overLimit whether an acceptance may go beyond the appointment's limit
     */
    public void decide(int id, RegistrationStatus status, boolean overLimit) {
        if (status != RegistrationStatus.ACCEPTED && status != RegistrationStatus.DENIED) {
            throw EventRefusal.REGISTRATION_DECISION_UNKNOWN.raise();
        }
        var registration =
                findRegistrationById(id).orElseThrow(EventRefusal.PARTNER_REGISTRATION_NOT_HERE_ON_DECISION::raise);
        registrationService.requireOwnSignUp(registration.eventId());
        places.requireHostDecides(PlacesKind.APPOINTMENT, registration.eventId(), registration.partnerId());
        if (status == RegistrationStatus.DENIED) {
            if (updateRegistrationStatus(id, status)) tellPartner(registration.eventId(), id, status, null);
            return;
        }
        if (!acceptWithinBudget(
                id, registration.eventId(), registration.partnerId(), registration.eventDate(), overLimit)) {
            throw EventRefusal.NO_PLACES_LEFT_FOR_THIS_PARTNER.raise();
        }
        tellPartner(registration.eventId(), id, status, null);
    }

    /** Announces a decision about a partner's member once it is kept, so the partner is notified. */
    private void tellPartner(
            int eventId, int registrationId, RegistrationStatus status, @Nullable Integer queuePosition) {
        var changedAt = Instant.now();
        crudService
                .findById(eventId)
                .ifPresent(
                        event -> Transactions.afterCommit(() -> eventBus.publish(new PartnerRegistrationStatusChanged(
                                event.stationId(), registrationId, status, queuePosition, false, changedAt))));
    }

    /**
     * Puts partners' members waiting for this station's decision onto the waiting lists of their dates,
     * in sign-up order, placed by the order each list keeps. Nothing changes unless every one of them
     * waits for this station's decision on a date with a participant limit.
     *
     * @param ids the partners' registrations
     */
    public void moveToQueue(Collection<Integer> ids) {
        Transactions.run(() -> {
            var found = ids.stream()
                    .distinct()
                    .map(id -> federationRepository
                            .findRegistrationById(id)
                            .orElseThrow(EventRefusal.PARTNER_REGISTRATION_NOT_HERE_ON_DECISION::raise))
                    .toList();
            found.stream()
                    .map(EventFederationRegistration::eventId)
                    .distinct()
                    .sorted()
                    .forEach(eventId -> registrationService.underSignUpLock(eventId, () -> null));
            var current = found.stream()
                    .map(registration -> federationRepository
                            .findRegistrationById(registration.id())
                            .orElse(registration))
                    .sorted(Comparator.comparing(EventFederationRegistration::createdAt)
                            .thenComparing(EventFederationRegistration::id))
                    .toList();
            for (var registration : current) {
                registrationService.requireOwnSignUp(registration.eventId());
                if (registration.status() != RegistrationStatus.PENDING) {
                    throw EventRefusal.REGISTRATION_NOT_PENDING_FOR_QUEUE.raise();
                }
                places.requireHostDecides(PlacesKind.APPOINTMENT, registration.eventId(), registration.partnerId());
                registrationService.requireLimit(registration.eventId(), registration.eventDate());
            }
            for (var registration : current) {
                federationRepository.updateRegistrationStatus(registration.id(), RegistrationStatus.QUEUED);
                var event = crudService.findById(registration.eventId()).orElseThrow();
                registrationService.enqueueVisitor(event, registration.eventDate(), registration.id(), false);
            }
            var ranks = registrationService.partnerQueueRanksOf(
                    current.stream().map(EventFederationRegistration::id).toList());
            for (var registration : current) {
                tellPartner(
                        registration.eventId(),
                        registration.id(),
                        RegistrationStatus.QUEUED,
                        ranks.get(registration.id()));
            }
        });
    }

    /**
     * Moves a partner's member to another place in line on the waiting list of their date.
     *
     * @param id   the partner's registration, which waits on the list
     * @param rank the place in line it takes, 1 moving up next; beyond the end it goes last
     */
    public void moveInQueue(int id, int rank) {
        var found = federationRepository
                .findRegistrationById(id)
                .orElseThrow(EventRefusal.PARTNER_REGISTRATION_NOT_HERE_ON_DECISION::raise);
        registrationService.underSignUpLock(found.eventId(), () -> {
            registrationService.requireOwnSignUp(found.eventId());
            var current = federationRepository.findRegistrationById(id).orElse(found);
            if (current.status() != RegistrationStatus.QUEUED) throw EventRefusal.REGISTRATION_NOT_QUEUED.raise();
            registrationService.moveVisitorInQueue(current.eventId(), current.eventDate(), id, rank);
            return null;
        });
    }

    /**
     * Finds a federated registration by its ID.
     *
     * @param id the registration ID
     * @return the registration, if found
     */
    public Optional<EventFederationRegistration> findRegistrationById(int id) {
        return federationRepository.findRegistrationById(id);
    }

    /**
     * Finds all federated registrations for an event on a specific date.
     *
     * @param eventId   the event ID
     * @param eventDate the event occurrence date
     * @return the list of registrations
     */
    public List<EventFederationRegistration> findRegistrations(int eventId, @Nullable LocalDate eventDate) {
        return federationRepository.findRegistrations(eventId, eventDate);
    }

    /**
     * Finds all federated registrations by partner.
     *
     * @param partnerId the federation partner ID
     * @return the list of registrations
     */
    public List<EventFederationRegistration> findRegistrationsByPartner(int partnerId) {
        return federationRepository.findRegistrationsByPartner(partnerId);
    }

    /**
     * Withdraws a federated registration by its composite key.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param eventDate      the event occurrence date
     * @return true if a registration was deleted
     */
    public boolean withdrawRegistration(int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        var heldBefore = registrationService.underSignUpLock(eventId, () -> {
            var before = federationRepository.findRegistration(eventId, partnerId, remoteMemberId, eventDate);
            if (!federationRepository.withdrawRegistration(eventId, partnerId, remoteMemberId, eventDate)) return null;
            return before.map(EventFederationRegistration::status).orElse(RegistrationStatus.WITHDRAWN);
        });
        if (heldBefore != null) {
            log.info(
                    "Withdrew federated registration for event {} from partner {} on {}",
                    eventId,
                    partnerId,
                    eventDate);
            if (heldBefore == RegistrationStatus.ACCEPTED) registrationService.moveUp(eventId, eventDate);
            return true;
        }
        log.warn(
                "Cannot withdraw federated registration: no registration for event {} from partner {} on {}",
                eventId,
                partnerId,
                eventDate);
        return false;
    }

    /** One partner's member on one date. */
    public Optional<EventFederationRegistration> findRegistration(
            int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        return federationRepository.findRegistration(eventId, partnerId, remoteMemberId, eventDate);
    }

    /**
     * Flags a partner's standing registration of a member for a date: the partner reported a signed
     * agreement the appointment asked for withdrawn.
     *
     * @return whether a standing registration was flagged
     */
    public boolean flagAgreementWithdrawn(
            int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate, Instant at) {
        return federationRepository.flagAgreementWithdrawn(eventId, partnerId, remoteMemberId, eventDate, at);
    }

    /**
     * Takes the flag of a withdrawn agreement off a partner's registration of a member for a date.
     *
     * @return whether a flag was taken off
     */
    public boolean clearAgreementWithdrawn(int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        return federationRepository.clearAgreementWithdrawn(eventId, partnerId, remoteMemberId, eventDate);
    }

    /**
     * How many of a partner's places are taken on one date, and how many it may take.
     *
     * @return the places filled, and the budget, which is empty where there is no cap
     */
    public PartnerPlaceCount countPartnerPlaces(int eventId, int partnerId, LocalDate eventDate) {
        var arranged = places.arrangement(PlacesKind.APPOINTMENT, eventId, partnerId);
        return new PartnerPlaceCount(
                federationRepository.countAcceptedForPartner(eventId, partnerId, eventDate),
                arranged.slotBudget(),
                arranged.partnerConfirms());
    }

    /**
     * Gives a partner's member one of that partner's places.
     *
     * <p>The budget is counted and spent in one statement, so two people at the partner confirming at
     * the same moment cannot both take the last place. Whoever asked, the count is this station's,
     * because the rows are.
     *
     * @return true where a place was granted, false where the budget was already spent
     */
    public boolean acceptWithinBudget(int registrationId, int eventId, int partnerId, LocalDate eventDate) {
        return acceptWithinBudget(registrationId, eventId, partnerId, eventDate, false);
    }

    /**
     * Gives a partner's member one of that partner's places, refusing on a full date unless whoever
     * decides goes beyond the limit.
     *
     * @param registrationId the partner's registration
     * @param eventId        the appointment
     * @param partnerId      the partner
     * @param eventDate      the date
     * @param overLimit      whether the acceptance may go beyond the appointment's limit
     * @return true where a place was granted, false where the partner's budget was already spent
     */
    public boolean acceptWithinBudget(
            int registrationId, int eventId, int partnerId, LocalDate eventDate, boolean overLimit) {
        boolean granted = registrationService.underSignUpLock(eventId, () -> {
            boolean held = federationRepository
                    .findRegistrationById(registrationId)
                    .filter(found -> found.status() == RegistrationStatus.ACCEPTED)
                    .isPresent();
            if (!held) {
                crudService
                        .findById(eventId)
                        .ifPresent(event -> registrationService.requireRoomToAccept(event, eventDate, overLimit));
            }
            places.lock(PlacesKind.APPOINTMENT, eventId, partnerId);
            return federationRepository.spendPlace(registrationId, eventId, partnerId, eventDate);
        });
        if (!granted) {
            log.info("Partner {} has no places left on event {} for {}", partnerId, eventId, eventDate);
        }
        return granted;
    }

    /**
     * What a partner has and may have on one date.
     *
     * @param taken            how many places are filled
     * @param budget           how many it may fill, or {@code null} for no cap
     * @param decidedByPartner whether the partner decides rather than this station
     */
    public record PartnerPlaceCount(int taken, @Nullable Integer budget, boolean decidedByPartner) {}

    /**
     * Puts a partner's member back on the list, for as long as their withdrawal can be taken back.
     *
     * <p>Two instances keep two clocks, and a window either could measure is a window neither agrees
     * on. The row lives here, so this station's stamp decides and the partner only asks. A partner
     * whose clock is minutes out gets this station's answer either way, and its member sees what
     * this station answered.
     *
     * @return true where the place was restored, false where the window had closed
     */
    public boolean undoWithdrawal(int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        boolean restored = registrationService.underSignUpLock(eventId, () -> {
            boolean seatTaken = federationRepository
                            .findPreviousStatus(eventId, partnerId, remoteMemberId, eventDate)
                            .filter(RegistrationStatus.ACCEPTED::equals)
                            .isPresent()
                    && registrationService.full(eventId, eventDate);
            if (!federationRepository.restoreRegistration(
                    eventId, partnerId, remoteMemberId, eventDate, EventRegistrationService.UNDO_WINDOW)) {
                return false;
            }
            var back = federationRepository
                    .findRegistration(eventId, partnerId, remoteMemberId, eventDate)
                    .orElseThrow();
            var event = crudService.findById(eventId).orElseThrow();
            if (seatTaken) {
                federationRepository.updateRegistrationStatus(back.id(), RegistrationStatus.QUEUED);
                registrationService.enqueueVisitor(event, eventDate, back.id(), true);
            } else if (back.status() == RegistrationStatus.QUEUED) {
                registrationService.enqueueVisitor(event, eventDate, back.id(), false);
            }
            return true;
        });
        if (restored) {
            registrationService.moveUp(eventId, eventDate);
            log.info(
                    "Took back the withdrawal of a federated registration for event {} from partner {} on {}",
                    eventId,
                    partnerId,
                    eventDate);
            return true;
        }
        log.info(
                "A federated withdrawal for event {} from partner {} on {} can no longer be taken back",
                eventId,
                partnerId,
                eventDate);
        return false;
    }

    /**
     * The appointments this station shares with a partner.
     *
     * @param partner the partnership the request arrived on
     * @return each shared appointment as a partner may see it
     */
    public List<SharedEvent> serveEvents(ServingPartner partner) {
        var eventGroups = eventGroupRepository.findPartSummaries(partner.servingStationId());
        return shares.sharedWith(partner).stream()
                .map(crudService::findById)
                .flatMap(Optional::stream)
                .map(event -> SharedEvent.of(event).withEventGroup(eventGroups.get(event.id())))
                .toList();
    }

    /**
     * One shared appointment with the questions it asks openly, the places it set aside for the
     * partner, where it set any aside, the registration questions a partner's member answers, and how
     * the places of its date in view stand.
     *
     * @param partner the partnership the request arrived on
     * @param eventId the appointment
     * @return the appointment as the partner may see it
     */
    public RemoteEventRoutes.RemoteEventDetail serveEvent(ServingPartner partner, int eventId) {
        shares.requireSharedWith(partner, eventId);
        var event = crudService.findById(eventId).orElseThrow(EventRefusal.SHARED_EVENT_NOT_HERE::raise);
        var dateInView = occurrenceCalendar.dateInView(event);
        var fields = fieldService.findByEvent(eventId, dateInView.orElse(null)).stream()
                .filter(AppointmentField::isPublic)
                .map(EventField::of)
                .toList();
        var arranged = places.arrangement(PlacesKind.APPOINTMENT, eventId, partner.partnerId());
        return new RemoteEventRoutes.RemoteEventDetail(
                SharedEvent.of(event)
                        .withEventGroup(eventGroupRepository
                                .findPartSummaries(partner.servingStationId())
                                .get(eventId)),
                fields,
                arranged.partnerConfirms() ? new RemoteEventRoutes.RemotePlaces(arranged.slotBudget(), true) : null,
                partnerQuestions.askedOfPartners(eventId).stream()
                        .map(RemoteEventRoutes.RemoteQuestion::of)
                        .toList(),
                dateInView.map(date -> datePlaces(eventId, date)).orElse(null));
    }

    private RemoteEventRoutes.RemoteDatePlaces datePlaces(int eventId, LocalDate date) {
        Places counted = registrationService.placesOf(eventId, date);
        return new RemoteEventRoutes.RemoteDatePlaces(date, counted.limit(), counted.accepted(), counted.queued());
    }

    /**
     * The open files of a shared appointment.
     *
     * @param partner the partnership the request arrived on
     * @param eventId the appointment
     * @return the files it hands over
     */
    public List<RemoteEventRoutes.RemoteAttachment> serveAttachments(ServingPartner partner, int eventId) {
        shares.requireSharedWith(partner, eventId);
        return attachmentService.listOpen(eventId).stream()
                .map(RemoteEventRoutes.RemoteAttachment::of)
                .toList();
    }

    /**
     * One open file of a shared appointment, encoded into the answer.
     *
     * <p>The same two questions are asked here as at home: is this appointment shared with the
     * partner asking, and is the file one it hands out at all. A file kept back is answered as absent
     * rather than refused, so asking for one by id reveals no more than asking for a file that is gone.
     *
     * @param partner      the partnership the request arrived on
     * @param eventId      the appointment
     * @param attachmentId the file
     * @return the file with its bytes
     */
    public RemoteEventRoutes.RemoteAttachmentContent serveAttachmentContent(
            ServingPartner partner, int eventId, int attachmentId) {
        shares.requireSharedWith(partner, eventId);
        var attachment = attachmentService
                .findOn(eventId, attachmentId)
                .filter(found -> !found.internal())
                .orElseThrow(EventRefusal.SHARED_EVENT_FILE_NOT_HERE::raise);
        EventAttachmentService.requireSizeToTravel(attachment.fileSize(), apiConfig.maxUploadSizeBytes());
        var event = crudService.findById(eventId).orElseThrow(EventRefusal.EVENT_NOT_HERE_BEHIND_SHARED_FILE::raise);
        var file = media.read(event.stationId(), attachment.contentHash())
                .orElseThrow(EventRefusal.SHARED_EVENT_FILE_CONTENT_NOT_HERE::raise);
        return new RemoteEventRoutes.RemoteAttachmentContent(
                attachment.id(),
                attachment.displayName(),
                attachment.fileName(),
                file.contentType(),
                Base64.getEncoder().encodeToString(file.data()));
    }

    /**
     * A partner's member signing up for a shared appointment, filed under this station's record of
     * the partner.
     */
    private EventFederationRegistration serveRegistration(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteRegistrationRequest request) {
        shares.requireSharedWith(partner, eventId);
        return registerFederated(
                eventId, partner.partnerId(), request.remoteMemberId(), request.eventDate(), request.answers());
    }

    private void serveWithdrawal(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteRegistrationRequest request) {
        shares.requireSharedWith(partner, eventId);
        registrationService.requireOwnSignUp(eventId);
        withdrawRegistration(eventId, partner.partnerId(), request.remoteMemberId(), request.eventDate());
    }

    /**
     * A partner asking for one of its members to be put back after a withdrawal.
     *
     * <p>Whether it is still possible is this station's to answer, because this station holds the
     * row and the clock that measures the window. A refusal here is not a failure: it means the
     * few minutes have passed, and the partner notifies its member of it.
     */
    private void serveUndoWithdrawal(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteRegistrationRequest request) {
        shares.requireSharedWith(partner, eventId);
        registrationService.requireOwnSignUp(eventId);
        if (!undoWithdrawal(eventId, partner.partnerId(), request.remoteMemberId(), request.eventDate())) {
            throw EventRefusal.PARTNER_WITHDRAWAL_NO_LONGER_UNDONE.raise();
        }
    }

    /**
     * A partner confirming one of its own members, where this station handed it that decision. Within
     * the partner's places but on a date every place of which is taken, the member goes onto the
     * waiting list and moves up like anybody else.
     *
     * <p>Two refusals, and they mean different things. Without an arrangement the partner is asking for
     * something it was never given, which is forbidden. With one, but with its places already filled,
     * the answer is that there is no room, which is an ordinary thing to hear and not a fault.
     */
    private void serveConfirmOwn(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteRegistrationRequest request) {
        shares.requireSharedWith(partner, eventId);
        registrationService.requireOwnSignUp(eventId);
        places.requirePartnerDecides(PlacesKind.APPOINTMENT, eventId, partner.partnerId());
        var registration = findRegistration(eventId, partner.partnerId(), request.remoteMemberId(), request.eventDate())
                .orElseThrow(EventRefusal.PARTNER_REGISTRATION_NOT_HERE::raise);
        boolean queued = registrationService.underSignUpLock(eventId, () -> {
            if (federationRepository
                    .findRegistrationById(registration.id())
                    .filter(found -> found.status() == RegistrationStatus.QUEUED)
                    .isPresent()) {
                return true;
            }
            int taken = federationRepository.countAcceptedForPartner(eventId, partner.partnerId(), request.eventDate());
            int waiting = federationRepository.countQueuedForPartner(eventId, partner.partnerId(), request.eventDate());
            if (!places.arrangement(PlacesKind.APPOINTMENT, eventId, partner.partnerId())
                    .admits(taken, waiting)) {
                throw EventRefusal.NO_PLACES_LEFT_FOR_PARTNER.raise();
            }
            var current =
                    federationRepository.findRegistrationById(registration.id()).orElse(registration);
            if (current.status() == RegistrationStatus.ACCEPTED
                    || !registrationService.full(eventId, request.eventDate())) {
                return false;
            }
            federationRepository.updateRegistrationStatus(registration.id(), RegistrationStatus.QUEUED);
            var event = crudService.findById(eventId).orElseThrow();
            registrationService.enqueueVisitor(event, request.eventDate(), registration.id(), false);
            return true;
        });
        if (queued) return;
        if (!acceptWithinBudget(registration.id(), eventId, partner.partnerId(), request.eventDate())) {
            throw EventRefusal.NO_PLACES_LEFT_FOR_PARTNER.raise();
        }
    }

    /** Where a partner's member stands after their confirmation, with the appointment's name. */
    private RemoteEventRoutes.RemoteConfirmedOwn confirmedOwn(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteRegistrationRequest request) {
        var status = findRegistration(eventId, partner.partnerId(), request.remoteMemberId(), request.eventDate())
                .map(EventFederationRegistration::status)
                .orElseThrow(EventRefusal.PARTNER_REGISTRATION_NOT_HERE::raise);
        var event = crudService.findById(eventId).orElseThrow(EventRefusal.SHARED_EVENT_NOT_HERE::raise);
        return new RemoteEventRoutes.RemoteConfirmedOwn(status, event.name());
    }

    /**
     * Who from this partner is coming, which is not the same as who has a row.
     *
     * <p>A withdrawal keeps its row, so the refusals are filtered out here rather than sent. A partner
     * reading this list treats a row as somebody coming, and an older one has never heard of a
     * withdrawn status at all.
     */
    private List<EventFederationRegistration> serveRegistrations(ServingPartner partner, int eventId) {
        shares.requireSharedWith(partner, eventId);
        return findRegistrationsByPartner(partner.partnerId()).stream()
                .filter(r -> r.eventId() == eventId)
                .filter(EventFederationRegistration::isStanding)
                .toList();
    }

    /**
     * The appointments of this station one of the partner's members stands on.
     *
     * @param partner   the partnership the request arrived on
     * @param memberUid the partner's member
     * @return the standing registrations of that member here
     */
    public List<RemoteEventRoutes.RemoteMemberRegistration> serveMemberRegistrations(
            ServingPartner partner, UUID memberUid) {
        return findRegistrationsByRemoteMember(memberUid).stream()
                .filter(r -> r.partnerId() == partner.partnerId())
                .filter(EventFederationRegistration::isStanding)
                .map(r -> new RemoteEventRoutes.RemoteMemberRegistration(
                        r.eventId(),
                        r.remoteMemberId().toString(),
                        r.eventDate().toString(),
                        r.status(),
                        r.partnerId()))
                .toList();
    }
}
