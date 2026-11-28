/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventGroupPartnerSignUpStatusChanged;
import dev.chojo.ember.event.events.EventGroupRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.PlacesKind;
import dev.chojo.ember.feature.events.entity.QueueEntry;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.WhenFull;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.PartnerPlacesRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueList;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueOwner;
import dev.chojo.ember.feature.events.service.EventGroupSignUpRules.Standing;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The waiting list of an event group: who gets in, who waits, and who moves up.
 *
 * <p>A sign-up waits as a whole with the parts it chose; it is never split into parts accepted and
 * parts waiting. It gets in at once only where the event group has a place left within its limit on
 * people and every chosen part that still takes place has a free place; otherwise it joins the list,
 * by sign-up time or by fairness score as the event group keeps it. An event group that confirms its
 * sign-ups leaves that to whoever decides.
 *
 * <p>Moving up walks the list in order and takes every entry whose chosen parts all have room again,
 * the people limit too. An entry waiting for a part that is still full is passed over, because it
 * cannot move up anyway; nobody who waits for the place that fell free is passed over. An entry whose
 * chosen parts include one already past moves up no more, and a called-off event group moves nobody.
 * Whoever moves up is accepted at once, members of partner stations alike. A partner's member whose
 * station has filled the places it was given is passed over and keeps its place in line, so it moves up
 * once one of its station's places falls free.
 *
 * <p>Callers hold the event group's lock and its parts' locks.
 */
@Singleton
public class EventGroupQueue {
    private static final Logger log = LoggerFactory.getLogger(EventGroupQueue.class);

    private final EventGroupSignUpRules rules;
    private final EventGroupRegistrationRepository signUps;
    private final EventGroupPartnerRepository partners;
    private final PartnerPlacesRepository partnerPlaces;
    private final EventRegistrationRepository registrations;
    private final RegistrationQueueRepository queue;
    private final QueuePlacement placement;
    private final EventRegistrationService registrationService;
    private final DomainEventBus eventBus;
    private final MemberNameResolver names;
    private final Clock clock;

    /**
     * Creates the waiting list.
     *
     * @param rules               the sign-up rules, for room and standing
     * @param signUps             the sign-ups of this station's members
     * @param partners            the sign-ups of partners' members
     * @param partnerPlaces       the places each partner was given
     * @param registrations       the registrations of the parts
     * @param queue               the waiting lists
     * @param placement           the order of the waiting lists
     * @param registrationService the registrations, for whoever keeps a record of the dates
     * @param eventBus            where whoever moved up is announced
     * @param names               the names announcements carry
     */
    @Inject
    public EventGroupQueue(
            EventGroupSignUpRules rules,
            EventGroupRegistrationRepository signUps,
            EventGroupPartnerRepository partners,
            PartnerPlacesRepository partnerPlaces,
            EventRegistrationRepository registrations,
            RegistrationQueueRepository queue,
            QueuePlacement placement,
            EventRegistrationService registrationService,
            DomainEventBus eventBus,
            MemberNameResolver names) {
        this(
                rules,
                signUps,
                partners,
                partnerPlaces,
                registrations,
                queue,
                placement,
                registrationService,
                eventBus,
                names,
                Clock.systemUTC());
    }

    /**
     * Creates the waiting list on a given clock.
     *
     * @param rules               the sign-up rules, for room and standing
     * @param signUps             the sign-ups of this station's members
     * @param partners            the sign-ups of partners' members
     * @param partnerPlaces       the places each partner was given
     * @param registrations       the registrations of the parts
     * @param queue               the waiting lists
     * @param placement           the order of the waiting lists
     * @param registrationService the registrations, for whoever keeps a record of the dates
     * @param eventBus            where whoever moved up is announced
     * @param names               the names announcements carry
     * @param clock               the current time, for a start at short notice
     */
    public EventGroupQueue(
            EventGroupSignUpRules rules,
            EventGroupRegistrationRepository signUps,
            EventGroupPartnerRepository partners,
            PartnerPlacesRepository partnerPlaces,
            EventRegistrationRepository registrations,
            RegistrationQueueRepository queue,
            QueuePlacement placement,
            EventRegistrationService registrationService,
            DomainEventBus eventBus,
            MemberNameResolver names,
            Clock clock) {
        this.clock = clock;
        this.rules = rules;
        this.signUps = signUps;
        this.partners = partners;
        this.partnerPlaces = partnerPlaces;
        this.registrations = registrations;
        this.queue = queue;
        this.placement = placement;
        this.registrationService = registrationService;
        this.eventBus = eventBus;
        this.names = names;
    }

    /**
     * The status a new sign-up or a sign-up coming back gets.
     *
     * @param eventGroup the event group
     * @param chosen     the parts chosen
     * @param parts      the event group's parts, by id
     * @param standing   which parts are called off or past
     * @param override   whether whoever runs events signs up
     * @param choice     what whoever runs events chose for a choice that does not fit, or null
     * @return PENDING where the event group confirms its sign-ups, ACCEPTED where it fits or whoever runs
     *     events goes beyond the limit, else QUEUED
     */
    RegistrationStatus statusFor(
            EventGroup eventGroup,
            Set<Integer> chosen,
            Map<Integer, StationEvent> parts,
            Standing standing,
            boolean override,
            @Nullable WhenFull choice) {
        if (eventGroup.details().requiresConfirmation()) return RegistrationStatus.PENDING;
        if (rules.fits(eventGroup, chosen, parts, standing)) return RegistrationStatus.ACCEPTED;
        if (!override) return RegistrationStatus.QUEUED;
        if (choice == null) throw EventRefusal.REGISTRATION_FULL.raise();
        return choice == WhenFull.OVER_LIMIT ? RegistrationStatus.ACCEPTED : RegistrationStatus.QUEUED;
    }

    /**
     * Refuses to accept a choice that does not fit unless whoever decides goes beyond the limit.
     *
     * @param eventGroup the event group
     * @param chosen     the parts chosen
     * @param parts      the event group's parts, by id
     * @param standing   which parts are called off or past
     * @param overLimit  whether whoever decides goes beyond the limit
     */
    void requireRoomToAccept(
            EventGroup eventGroup,
            Set<Integer> chosen,
            Map<Integer, StationEvent> parts,
            Standing standing,
            boolean overLimit) {
        if (!overLimit && !rules.fits(eventGroup, chosen, parts, standing)) {
            throw EventRefusal.REGISTRATION_FULL.raise();
        }
    }

    /**
     * Refuses a waiting list on an event group that limits neither its people nor any part's places:
     * nobody would ever have to wait, and the next trigger would let the whole list in at once.
     *
     * @param eventGroup the event group
     * @param parts      its parts, by id
     */
    void requireLimit(EventGroup eventGroup, Map<Integer, StationEvent> parts) {
        if (eventGroup.details().registrationLimit() != null) return;
        if (parts.values().stream().anyMatch(part -> rules.placesOf(part).limit() != null)) return;
        throw EventRefusal.QUEUE_WITHOUT_LIMIT.raise();
    }

    /**
     * Puts a sign-up on the event group's waiting list, in the order it keeps. A sign-up already on it
     * keeps its place.
     *
     * @param eventGroup the event group
     * @param owner      the sign-up
     * @param memberId   this station's member, whose history decides a list kept by fairness, or null
     *                   for a partner's member
     */
    void enqueue(EventGroup eventGroup, QueueOwner owner, @Nullable Integer memberId) {
        placement.place(
                RegistrationQueueRepository.ofEventGroup(eventGroup.id()),
                eventGroup.details().order(),
                owner,
                placement.scoreOf(eventGroup.stationId(), queue.categoryOfEventGroup(eventGroup.id()), memberId));
    }

    /**
     * The places in line on an event group's waiting list, by the sign-up waiting there.
     *
     * @param eventGroupId the event group
     * @return the place in line, 1 moving up next, of every sign-up on the list
     */
    public Map<QueueOwner, Integer> ranks(int eventGroupId) {
        return queue.entries(RegistrationQueueRepository.ofEventGroup(eventGroupId)).stream()
                .collect(Collectors.toMap(EventGroupQueue::ownerOf, QueueEntry::rank));
    }

    /** Whose entry on the waiting list this is: a member's sign-up or a partner's. */
    private static QueueOwner ownerOf(QueueEntry entry) {
        Integer localId = entry.localId();
        return localId != null
                ? QueueOwner.local(localId)
                : QueueOwner.ofPartner(Objects.requireNonNull(entry.partnerId()));
    }

    /**
     * Moves a sign-up to another place in line, as whoever runs the event group decides.
     *
     * @param eventGroup the event group
     * @param owner      the sign-up, which waits on the list
     * @param rank       the place in line it takes, 1 moving up next; beyond the end it goes last
     */
    void moveTo(EventGroup eventGroup, QueueOwner owner, int rank) {
        placement.moveTo(
                RegistrationQueueRepository.ofEventGroup(eventGroup.id()),
                owner,
                rank,
                EventRefusal.EVENT_GROUP_NOT_QUEUED);
    }

    /**
     * Moves everybody up whose choice fits now, first in line first, and notifies this station's members
     * who moved up once the change is kept.
     *
     * @param eventGroup the event group, locked with its parts
     * @param parts      its parts, by id
     * @param standing   which parts are called off or past
     * @return this station's members who moved up
     */
    List<Integer> fill(EventGroup eventGroup, Map<Integer, StationEvent> parts, Standing standing) {
        if (eventGroup.cancelled()) return List.of();
        var list = RegistrationQueueRepository.ofEventGroup(eventGroup.id());
        var moved = new ArrayList<Integer>();
        var atShortNotice = new HashSet<Integer>();
        Integer limit = eventGroup.details().registrationLimit();
        for (var entry : queue.entries(list)) {
            if (limit != null && rules.holding(eventGroup.id()) >= limit) break;
            Integer local = entry.localId();
            Integer partner = entry.partnerId();
            if (local != null) {
                moveUpLocal(eventGroup, list, local, parts, standing).ifPresent(chosen -> {
                    moved.add(chosen.memberId());
                    if (chosen.shortNotice()) atShortNotice.add(chosen.memberId());
                });
            } else if (partner != null) {
                moveUpPartner(eventGroup, list, partner, parts, standing);
            }
        }
        if (!moved.isEmpty()) log.info("Moved {} up in event group {}", moved.size(), eventGroup.id());
        for (int memberId : moved) {
            Transactions.afterCommit(() -> eventBus.publish(new EventGroupRegistrationStatusChanged(
                    eventGroup.stationId(),
                    eventGroup.id(),
                    eventGroup.details().name(),
                    memberId,
                    names.called(memberId),
                    RegistrationStatus.ACCEPTED,
                    null,
                    true,
                    atShortNotice.contains(memberId),
                    false)));
        }
        return moved;
    }

    /** A member who moved up, and whether their first part still to come starts in less than a day. */
    private record MovedUp(int memberId, boolean shortNotice) {}

    private Optional<MovedUp> moveUpLocal(
            EventGroup eventGroup, QueueList list, int signUpId, Map<Integer, StationEvent> parts, Standing standing) {
        var signUp = signUps.findById(signUpId).orElse(null);
        if (signUp == null || signUp.status() != RegistrationStatus.QUEUED) {
            queue.remove(list, QueueOwner.local(signUpId));
            return Optional.empty();
        }
        var tied = registrations.findLinkedToEventGroup(signUpId);
        var chosen = tied.stream().map(EventRegistration::eventId).collect(Collectors.toSet());
        if (!movesUp(eventGroup, chosen, parts, standing)) return Optional.empty();
        signUps.updateStatus(signUpId, RegistrationStatus.ACCEPTED);
        var written = new ArrayList<EventRegistration>();
        for (var registration : tied) {
            if (registration.status() == RegistrationStatus.QUEUED) {
                written.add(registrationService.restateForEventGroup(registration, RegistrationStatus.ACCEPTED));
            }
        }
        Transactions.afterCommit(() -> registrationService.recordedAll(written));
        boolean shortNotice = chosen.stream()
                .map(parts::get)
                .filter(Objects::nonNull)
                .map(StationEvent::startTime)
                .filter(start -> start.isAfter(clock.instant()))
                .min(Comparator.naturalOrder())
                .filter(start -> QueueAdvancer.withinADay(clock, start))
                .isPresent();
        return Optional.of(new MovedUp(signUp.memberId(), shortNotice));
    }

    private void moveUpPartner(
            EventGroup eventGroup, QueueList list, int signUpId, Map<Integer, StationEvent> parts, Standing standing) {
        var signUp = partners.findById(signUpId).orElse(null);
        if (signUp == null || signUp.status() != RegistrationStatus.QUEUED) {
            queue.remove(list, QueueOwner.ofPartner(signUpId));
            return;
        }
        var tied = partners.findLinked(signUpId);
        var chosen = tied.stream().map(EventFederationRegistration::eventId).collect(Collectors.toSet());
        if (!withinPartnerPlaces(eventGroup, signUp.partnerId())) return;
        if (!movesUp(eventGroup, chosen, parts, standing)) return;
        partners.updateStatus(signUpId, RegistrationStatus.ACCEPTED);
        for (var registration : tied) {
            if (registration.status() == RegistrationStatus.QUEUED) {
                partners.restate(registration.id(), RegistrationStatus.ACCEPTED);
            }
        }
        var changedAt = clock.instant();
        Transactions.afterCommit(() -> eventBus.publish(new EventGroupPartnerSignUpStatusChanged(
                eventGroup.stationId(), signUpId, RegistrationStatus.ACCEPTED, null, true, changedAt)));
    }

    /** Whether a partner still has a place of the ones it was given, counted per sign-up. */
    private boolean withinPartnerPlaces(EventGroup eventGroup, int partnerId) {
        return partnerPlaces
                .find(PlacesKind.EVENT_GROUP, eventGroup.id(), partnerId)
                .admits(partners.countForPartner(eventGroup.id(), partnerId, RegistrationStatus.ACCEPTED), 0);
    }

    /** Whether a waiting choice moves up now: it fits, and none of its parts is already past. */
    private boolean movesUp(
            EventGroup eventGroup, Set<Integer> chosen, Map<Integer, StationEvent> parts, Standing standing) {
        if (chosen.stream().anyMatch(standing::tookPlace)) return false;
        return rules.fits(eventGroup, chosen, parts, standing);
    }
}
