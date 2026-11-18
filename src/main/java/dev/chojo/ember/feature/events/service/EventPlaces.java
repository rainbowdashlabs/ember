/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.Places;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.WhenFull;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueList;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueOwner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decides where somebody who says they are coming to an appointment date ends up, and keeps the
 * waiting list of that date.
 *
 * <p>An appointment that confirms its sign-ups leaves the choice to whoever runs it, so a "coming"
 * waits for a decision however many there are. Otherwise a sign-up is accepted while a place is free
 * and nobody waits for one; anybody else joins the end of the waiting list, or its place by fairness,
 * so nobody overtakes the people already waiting. Whoever runs the appointment may let somebody in
 * beyond the limit instead, but has to say so: a full date refuses them until they choose.
 *
 * <p>Callers hold the appointment's lock, so two sign-ups never take the same last place.
 */
@Singleton
public class EventPlaces {
    private final EventCapacity capacity;
    private final QueuePlacement placement;
    private final RegistrationQueueRepository queue;
    private final QueueAdvancer advancer;

    /**
     * Creates the decision.
     *
     * @param capacity  the places of a date
     * @param placement the waiting list order
     * @param queue     the waiting lists
     * @param advancer  the move-up from the waiting lists
     */
    @Inject
    public EventPlaces(
            EventCapacity capacity,
            QueuePlacement placement,
            RegistrationQueueRepository queue,
            QueueAdvancer advancer) {
        this.capacity = capacity;
        this.placement = placement;
        this.queue = queue;
        this.advancer = advancer;
    }

    /**
     * Refuses a waiting list on a date that has no participant limit: nobody would ever have to wait
     * there, and the next place falling free would let the whole list in at once.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     */
    public void requireLimit(int eventId, LocalDate eventDate) {
        if (capacity.of(eventId, eventDate).limit() == null) throw EventRefusal.QUEUE_WITHOUT_LIMIT.raise();
    }

    /**
     * Whether every place of a date is taken.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return true where no place is free
     */
    public boolean full(int eventId, LocalDate eventDate) {
        return capacity.of(eventId, eventDate).full();
    }

    /**
     * Fills the free places of a date from its waiting list. The caller holds the appointment's lock.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return the registrations of this station's members who moved up
     */
    public List<EventRegistration> fill(int eventId, LocalDate eventDate) {
        return advancer.fill(eventId, eventDate);
    }

    /**
     * Fills the free places of every waited-for date of an appointment from one day on. The caller
     * holds the appointment's lock.
     *
     * @param eventId the appointment
     * @param from    the first date looked at
     * @return the registrations of this station's members who moved up
     */
    public List<EventRegistration> fillFrom(int eventId, LocalDate from) {
        return advancer.fillFrom(eventId, from);
    }

    /**
     * Puts a member's registration at the front of the waiting list of its date, ahead of everybody
     * waiting, for a seat that was given up by mistake and taken in the meantime. The entry carries no
     * score, so on a list kept by fairness no newcomer is placed before it: it gets the next place that
     * falls free.
     *
     * @param event          the appointment
     * @param date           the date
     * @param registrationId the registration
     */
    public void enqueueFirst(StationEvent event, LocalDate date, int registrationId) {
        insertFirst(RegistrationQueueRepository.ofDate(event.id(), date), QueueOwner.local(registrationId));
    }

    /** Puts a sign-up ahead of everybody waiting, without a score so no newcomer is placed before it. */
    private void insertFirst(QueueList list, QueueOwner owner) {
        var entries = queue.entries(list);
        queue.insertAt(list, owner, entries.isEmpty() ? 1 : entries.getFirst().position(), null);
    }

    /**
     * The status somebody saying they are coming gets.
     *
     * @param event     the appointment
     * @param date      the date
     * @param byManager whether whoever runs the appointment lets somebody else in
     * @param choice    what they chose for a full date, or null; ignored for anybody else
     * @return PENDING, ACCEPTED or QUEUED
     */
    public RegistrationStatus statusForComing(
            StationEvent event, LocalDate date, boolean byManager, @Nullable WhenFull choice) {
        if (event.requiresConfirmation()) return RegistrationStatus.PENDING;
        Places places = capacity.of(event.id(), date);
        if (!places.full() && places.queued() == 0) return RegistrationStatus.ACCEPTED;
        return whenFull(places, byManager, choice);
    }

    /**
     * Whether whoever runs the appointment may accept somebody on a date, and refuses when every place
     * is taken and they did not say to go beyond the limit.
     *
     * @param event     the appointment
     * @param date      the date
     * @param overLimit whether they accept beyond the limit
     */
    public void requireRoomToAccept(StationEvent event, LocalDate date, boolean overLimit) {
        Places places = capacity.of(event.id(), date);
        if (places.full() && !overLimit) throw full(places);
    }

    private static RegistrationStatus whenFull(Places places, boolean byManager, @Nullable WhenFull choice) {
        if (!byManager) return RegistrationStatus.QUEUED;
        if (choice == null) throw full(places);
        return choice == WhenFull.OVER_LIMIT ? RegistrationStatus.ACCEPTED : RegistrationStatus.QUEUED;
    }

    private static RefusalResponse full(Places places) {
        return EventRefusal.REGISTRATION_FULL.raise(
                RefusalDetail.count(Objects.requireNonNullElse(places.limit(), places.accepted())));
    }

    /**
     * Puts a member's registration on the waiting list of its date, in the order the appointment keeps.
     * A registration already on it keeps its place.
     *
     * @param event          the appointment
     * @param date           the date
     * @param registrationId the registration
     * @param memberId       the member, whose history decides a list kept by fairness
     */
    public void enqueue(StationEvent event, LocalDate date, int registrationId, int memberId) {
        placement.place(
                RegistrationQueueRepository.ofDate(event.id(), date),
                queue.orderOfEvent(event.id()),
                QueueOwner.local(registrationId),
                placement.scoreOf(event.stationId(), event.categoryId(), memberId));
    }

    /**
     * Moves a member's registration to another place in line on the waiting list of its date. Moving by
     * hand wins over the order the list keeps; whoever comes later is placed by that order around it.
     *
     * @param eventId        the appointment
     * @param date           the date
     * @param owner          the sign-up, a member's or a partner's member's, which waits on the list
     * @param rank           the place in line it takes, 1 moving up next; beyond the end it goes last
     */
    public void moveTo(int eventId, LocalDate date, QueueOwner owner, int rank) {
        placement.moveTo(
                RegistrationQueueRepository.ofDate(eventId, date), owner, rank, EventRefusal.REGISTRATION_NOT_QUEUED);
    }

    /**
     * Puts a partner station's member on the waiting list of a date, as a newcomer by fairness since
     * this station keeps no history of them; or at its front, for a seat given up by mistake.
     *
     * @param event                    the appointment
     * @param date                     the date
     * @param federationRegistrationId the partner's registration
     * @param first                    whether it goes ahead of everybody waiting
     */
    public void enqueuePartner(StationEvent event, LocalDate date, int federationRegistrationId, boolean first) {
        var list = RegistrationQueueRepository.ofDate(event.id(), date);
        var owner = QueueOwner.ofPartner(federationRegistrationId);
        if (first) {
            insertFirst(list, owner);
            return;
        }
        placement.place(
                list,
                queue.orderOfEvent(event.id()),
                owner,
                placement.scoreOf(event.stationId(), event.categoryId(), null));
    }

    /**
     * The places in line of the given registrations that wait on a waiting list.
     *
     * @param registrationIds the registrations
     * @return the place in line by registration, only for those waiting
     */
    public Map<Integer, Integer> ranksOf(Collection<Integer> registrationIds) {
        return queue.ranksOf(registrationIds);
    }
}
