/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.feature.equipment.entity.ClaimOrigin;
import dev.chojo.ember.feature.equipment.entity.EquipmentAvailability;
import dev.chojo.ember.feature.equipment.entity.EquipmentClaim;
import dev.chojo.ember.feature.equipment.entity.EquipmentHandover;
import dev.chojo.ember.feature.equipment.entity.EquipmentNeed;
import dev.chojo.ember.feature.equipment.entity.NeedCoverage;
import dev.chojo.ember.feature.equipment.repository.EquipmentAvailabilityRepository;
import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.inventory.service.LineTargetService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What an appointment needs, written once for the series and answered per evening.
 *
 * <p>Writing a line takes the same permission as editing the appointment, because a need is part of
 * planning the evening rather than a second kind of stock: what it says about the gear is a note,
 * and nothing here reserves, holds or moves anything.
 */
@Singleton
public class EquipmentNeedService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentNeedService.class);

    /** A lead or trail beyond this is refused, because it would make every availability walk unbounded. */
    private static final int MAX_LEAD_TRAIL_MINUTES = EquipmentAvailabilityService.MAX_LEAD_TRAIL_DAYS * 24 * 60;

    private final EquipmentNeedRepository needRepository;
    private final EquipmentAvailabilityRepository availabilityRepository;
    private final EquipmentAvailabilityService availabilityService;
    private final EventCrudService eventService;
    private final LineTargetService lineTargets;

    @Inject
    public EquipmentNeedService(
            EquipmentNeedRepository needRepository,
            EquipmentAvailabilityRepository availabilityRepository,
            EquipmentAvailabilityService availabilityService,
            EventCrudService eventService,
            LineTargetService lineTargets) {
        this.needRepository = needRepository;
        this.availabilityRepository = availabilityRepository;
        this.availabilityService = availabilityService;
        this.eventService = eventService;
        this.lineTargets = lineTargets;
    }

    /**
     * Finds a line by its ID.
     *
     * @param id the line ID
     * @return the line, or empty if not found
     */
    public Optional<EquipmentNeed> findById(int id) {
        return needRepository.findById(id);
    }

    /**
     * Every line of one appointment.
     *
     * @param eventId the appointment
     * @return the lines, in their own order
     */
    public List<EquipmentNeed> findByEvent(int eventId) {
        return needRepository.findByEvent(eventId);
    }

    /**
     * Writes a line onto an appointment.
     *
     * @param eventId      the appointment
     * @param stationId    the station it belongs to
     * @param eventDate    the one evening the line speaks for, or {@code null} for the whole series
     * @param target       what the line asks for
     * @param quantity     how many pieces
     * @param leadMinutes  how long before the appointment the gear goes
     * @param trailMinutes how long after it the gear is back
     * @return the created line
     * @throws IllegalArgumentException if the gear belongs to another station or the numbers do not hold
     */
    public EquipmentNeed add(
            int eventId,
            int stationId,
            LocalDate eventDate,
            LineTarget target,
            int quantity,
            int leadMinutes,
            int trailMinutes) {
        int wanted = target.namesItem() ? 1 : quantity;
        if (wanted < 1) throw new IllegalArgumentException("A line asks for at least one piece");
        requireLeadTrail(leadMinutes, trailMinutes);
        lineTargets.requireOwnedBy(target, stationId, "An appointment can only ask for its own station's gear");
        if (needRepository.findByEvent(eventId).stream()
                .anyMatch(line -> line.target().equals(target) && Objects.equals(line.eventDate(), eventDate))) {
            throw new IllegalArgumentException("The appointment already asks for this");
        }
        var need = needRepository.create(
                eventId,
                eventDate,
                target.itemId(),
                target.artId(),
                target.inventoryId(),
                wanted,
                leadMinutes,
                trailMinutes);
        log.info("Appointment {} now needs {} of {}", eventId, wanted, target);
        return need;
    }

    /**
     * Rewrites what a line asks for and when the gear is away.
     *
     * @param id           the line ID
     * @param quantity     the new count
     * @param leadMinutes  the new lead
     * @param trailMinutes the new trail
     * @return {@code true} if a row changed
     * @throws IllegalArgumentException if the numbers do not hold or the line names a single piece
     */
    public boolean update(int id, int quantity, int leadMinutes, int trailMinutes) {
        var need =
                needRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("The line does not exist"));
        if (need.target().namesItem() && quantity != 1) {
            throw new IllegalArgumentException("A line naming one piece always asks for that one piece");
        }
        if (quantity < 1) throw new IllegalArgumentException("A line asks for at least one piece");
        requireLeadTrail(leadMinutes, trailMinutes);
        return needRepository.update(id, quantity, leadMinutes, trailMinutes);
    }

    /**
     * Rewrites the order of an appointment's lines.
     *
     * @param eventId    the appointment
     * @param orderedIds the line IDs in their new order
     */
    public void reorder(int eventId, List<Integer> orderedIds) {
        needRepository.reorder(eventId, orderedIds);
    }

    /**
     * Deletes a line.
     *
     * @param id the line ID
     * @return {@code true} if a row went
     */
    public boolean delete(int id) {
        return needRepository.delete(id);
    }

    /**
     * Records that a piece went out for one evening, which is where a loose claim becomes a firm one.
     *
     * @param needId    the line
     * @param eventDate the evening
     * @param itemId    the piece
     * @param handedBy  the member recording it, or {@code null}
     * @return the recorded handover
     * @throws IllegalArgumentException if the line does not exist or the piece is not the station's
     */
    public EquipmentHandover handOver(int needId, LocalDate eventDate, int itemId, Integer handedBy) {
        var need = needRepository
                .findById(needId)
                .orElseThrow(() -> new IllegalArgumentException("The line does not exist"));
        var event = eventService
                .findById(need.eventId())
                .orElseThrow(() -> new IllegalArgumentException("The appointment does not exist"));
        lineTargets.requireOwnedBy(
                LineTarget.item(itemId), event.stationId(), "A station only hands over its own gear");
        Instant from = EquipmentOccurrenceWindows.startOf(event, eventDate).minus(need.lead());
        Instant to = EquipmentOccurrenceWindows.endOf(event, eventDate).plus(need.trail());
        return needRepository.recordHandover(needId, eventDate, itemId, from, to, handedBy);
    }

    /**
     * Marks a handed-over piece as back.
     *
     * @param handoverId the handover
     * @param eventId    the appointment it has to belong to
     * @return {@code true} if a row changed
     */
    public boolean handBack(int handoverId, int eventId) {
        return needRepository.markReturned(handoverId, eventId);
    }

    /**
     * The pieces that went out for one evening.
     *
     * @param eventId the appointment
     * @param date    the evening
     * @return the handovers
     */
    public List<EquipmentHandover> handovers(int eventId, LocalDate date) {
        return needRepository.findHandovers(eventId, date);
    }

    /**
     * What one evening of an appointment needs, and how much of it is answered.
     *
     * <p>The line is the question and where the gear comes from is part of the answer: the station's
     * own free stock, what a partner has already sent, and what has been asked for and not arrived.
     * An over-claim is reported beside the line with the appointments involved rather than being
     * prevented, because two people planning the same weekend both writing down what they need is
     * planning, and refusing the second one does not remove the conflict.
     *
     * @param eventId the appointment
     * @param date    the evening
     * @return one answer per line that holds for that evening
     */
    public List<NeedCoverage> coverage(int eventId, LocalDate date) {
        StationEvent event = eventService
                .findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("The appointment does not exist"));
        var lines = EquipmentAvailabilityService.needsForDate(needRepository.findByEvent(eventId), date);
        var coverage = new ArrayList<NeedCoverage>();
        for (var need : lines) {
            Instant from = EquipmentOccurrenceWindows.startOf(event, date).minus(need.lead());
            Instant to = EquipmentOccurrenceWindows.endOf(event, date).plus(need.trail());
            EquipmentAvailability availability =
                    availabilityService.availability(event.stationId(), need.target(), from, to, need.id());
            int borrowed = availabilityRepository.borrowedAgainstNeed(need.id());
            int outstanding = Math.max(0, availabilityRepository.outstandingAgainstNeed(need.id()) - borrowed);
            int own = Math.min(need.quantity(), Math.max(0, availability.free()));
            boolean overClaimed = availability.claimed() + need.quantity() > availability.stock();
            List<EquipmentClaim> overClaim = overClaimed
                    ? availability.claims().stream()
                            .filter(claim -> claim.origin() == ClaimOrigin.OWN_NEED)
                            .toList()
                    : List.of();
            coverage.add(new NeedCoverage(
                    need,
                    availability.target().label(),
                    from,
                    to,
                    own,
                    borrowed,
                    outstanding,
                    availability.stock(),
                    availability.claimed(),
                    overClaim));
        }
        return coverage;
    }

    private static void requireLeadTrail(int leadMinutes, int trailMinutes) {
        if (leadMinutes < 0 || trailMinutes < 0) {
            throw new IllegalArgumentException("A lead and a trail are never negative");
        }
        if (leadMinutes > MAX_LEAD_TRAIL_MINUTES || trailMinutes > MAX_LEAD_TRAIL_MINUTES) {
            throw new IllegalArgumentException("A lead and a trail reach at most sixty days");
        }
    }
}
