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
import dev.chojo.ember.feature.equipment.repository.EquipmentAvailabilityRepository;
import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.EventBreakService;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.inventory.entity.ResolvedTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one definition of free, which everything asks.
 *
 * <p>Free over a window means the stock, minus every claim whose window overlaps it, whatever the
 * claim's origin: the station's own appointments, the loans it has agreed to, and the periods it has
 * set aside. One function, asked by the needs panel and by the lending path alike. Two separate
 * calculations that merely checked each other at the last moment would be two definitions of free,
 * they would drift, and the conflict would surface when somebody says yes rather than when they plan.
 *
 * <p>Nothing is locked and nothing is refused. Two appointments may both need the last trailer on the
 * same weekend; the answer then reports the over-claim and names the appointments involved, because
 * planning is writing down rather than taking, and a tool that refuses to record a conflict does not
 * remove the conflict, it hides it until the Saturday.
 */
@Singleton
public class EquipmentAvailabilityService {

    /**
     * How far outside the queried window an evening may lie and still reach into it, which bounds the
     * walk over the recurrence rule. A lead or trail beyond this is refused when the line is written.
     */
    static final int MAX_LEAD_TRAIL_DAYS = 60;

    private final EquipmentAvailabilityRepository availabilityRepository;
    private final EquipmentNeedRepository needRepository;
    private final EventRepository eventRepository;
    private final EventBreakService breakService;

    @Inject
    public EquipmentAvailabilityService(
            EquipmentAvailabilityRepository availabilityRepository,
            EquipmentNeedRepository needRepository,
            EventRepository eventRepository,
            EventBreakService breakService) {
        this.availabilityRepository = availabilityRepository;
        this.needRepository = needRepository;
        this.eventRepository = eventRepository;
        this.breakService = breakService;
    }

    /**
     * Fills in the levels above a target and reads its name.
     *
     * @param target what to resolve
     * @return the resolved target, or empty when it points at something that no longer exists
     */
    public Optional<ResolvedTarget> resolve(LineTarget target) {
        return availabilityRepository.resolve(target);
    }

    /**
     * What a station has free of one thing over one window.
     *
     * @param stationId the station asking
     * @param target    what is being asked about
     * @param from      the first moment of the window
     * @param to        the last moment of the window
     * @return the stock and everything already holding some of it
     */
    public EquipmentAvailability availability(int stationId, LineTarget target, Instant from, Instant to) {
        return availability(stationId, target, from, to, null);
    }

    /**
     * What a station has free of one thing over one window, leaving one line's own claim out.
     *
     * <p>A line asking what it can still find must not count itself as competition, which is the only
     * reason this exists beside {@link #availability(int, LineTarget, Instant, Instant)}.
     *
     * @param stationId   the station asking
     * @param target      what is being asked about
     * @param from        the first moment of the window
     * @param to          the last moment of the window
     * @param ignoreNeedId the line not to count, or {@code null} to count every one
     * @return the stock and everything already holding some of it
     */
    public EquipmentAvailability availability(
            int stationId, LineTarget target, Instant from, Instant to, Integer ignoreNeedId) {
        ResolvedTarget question = availabilityRepository
                .resolve(target)
                .orElseThrow(() -> new IllegalArgumentException("The equipment does not exist"));
        int stock = availabilityRepository.stockOf(stationId, target);
        List<EquipmentClaim> claims = claims(stationId, from, to, ignoreNeedId).stream()
                .filter(claim -> claim.touches(question, from, to))
                .map(claim -> sizeAgainst(claim, stock))
                .toList();
        return new EquipmentAvailability(question, from, to, stock, claims);
    }

    /**
     * The pieces of one target nobody has set aside by name over a window.
     *
     * <p>Loose claims name no pieces, so they cannot take one out of this list; they take a count out
     * of {@link EquipmentAvailability#free()} instead. What is dropped here is what a piece-level
     * claim points at: a piece already handed over for another evening, a piece set aside for a
     * partner, and a piece a block names.
     *
     * @param stationId the station asking
     * @param target    what is being asked about
     * @param from      the first moment of the window
     * @param to        the last moment of the window
     * @return the piece IDs, in a stable order
     */
    public List<Integer> freePieces(int stationId, LineTarget target, Instant from, Instant to) {
        var spoken = claims(stationId, from, to, null).stream()
                .filter(claim -> claim.from().isBefore(to) && claim.to().isAfter(from))
                .map(EquipmentClaim::target)
                .filter(claimed -> claimed != null && claimed.itemId() != null)
                .map(ResolvedTarget::itemId)
                .toList();
        return availabilityRepository.piecesOf(stationId, target).stream()
                .filter(id -> !spoken.contains(id))
                .toList();
    }

    /**
     * Everything holding some of a station's stock over a window, from all three origins.
     *
     * @param stationId    the station
     * @param from         the first moment of the window
     * @param to           the last moment of the window
     * @param ignoreNeedId the line not to count, or {@code null} to count every one
     * @return the claims
     */
    public List<EquipmentClaim> claims(int stationId, Instant from, Instant to, Integer ignoreNeedId) {
        var resolved = new HashMap<LineTarget, ResolvedTarget>();
        var claims = new ArrayList<EquipmentClaim>();
        claims.addAll(ownClaims(stationId, from, to, ignoreNeedId, resolved));
        claims.addAll(loanClaims(stationId, from, to, resolved));
        claims.addAll(blockClaims(stationId, from, to, resolved));
        return claims;
    }

    /**
     * The evenings one appointment produces inside a window, widened by the longest lead and trail its
     * lines carry so that an evening just outside can still reach in.
     *
     * @param event  the appointment
     * @param breaks the periods the station does not meet in
     * @param from   the first moment of the window
     * @param to     the last moment of the window
     * @param slack  the widest lead or trail to allow for
     * @return the evenings, in order
     */
    public static List<LocalDate> occurrencesIn(
            StationEvent event, List<EventBreak> breaks, Instant from, Instant to, int slack) {
        var dates = new ArrayList<LocalDate>();
        LocalDate first = from.atZone(ZoneOffset.UTC).toLocalDate().minusDays(slack + 1L);
        LocalDate last = to.atZone(ZoneOffset.UTC).toLocalDate().plusDays(slack + 1L);
        if (!event.isRecurring()) {
            LocalDate single = EquipmentOccurrenceWindows.singleDateOf(event);
            if (single != null && !single.isBefore(first) && !single.isAfter(last)) dates.add(single);
            return dates;
        }
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (EventBreak.coversAny(breaks, date)) continue;
            if (event.occursOn(date)) dates.add(date);
        }
        return dates;
    }

    /**
     * What one evening of an appointment needs, which is the standing list with that evening's own
     * lines folded in.
     *
     * <p>A line written for the evening is added; where it names the same thing as a standing line it
     * takes its place, which is how one Dienst a year asks for twenty jackets instead of fourteen
     * without touching the other forty-nine.
     *
     * @param needs every line of the appointment
     * @param date  the evening
     * @return the lines that hold for that evening
     */
    public static List<EquipmentNeed> needsForDate(List<EquipmentNeed> needs, LocalDate date) {
        var byTarget = new LinkedHashMap<LineTarget, EquipmentNeed>();
        for (var need : needs) {
            if (need.forWholeSeries()) byTarget.put(need.target(), need);
        }
        for (var need : needs) {
            if (date.equals(need.eventDate())) byTarget.put(need.target(), need);
        }
        return List.copyOf(byTarget.values());
    }

    private List<EquipmentClaim> ownClaims(
            int stationId, Instant from, Instant to, Integer ignoreNeedId, Map<LineTarget, ResolvedTarget> resolved) {
        var needsByEvent = new HashMap<Integer, List<EquipmentNeed>>();
        for (var need : needRepository.findByStation(stationId)) {
            if (ignoreNeedId != null && ignoreNeedId == need.id()) continue;
            needsByEvent
                    .computeIfAbsent(need.eventId(), key -> new ArrayList<>())
                    .add(need);
        }
        if (needsByEvent.isEmpty()) return List.of();

        var breaks = breakService.findByStation(stationId);
        var firm = firmCounts(stationId, from, to);
        var claims = new ArrayList<EquipmentClaim>();

        for (var event : eventRepository.findByStation(stationId)) {
            var needs = needsByEvent.get(event.id());
            if (needs == null || event.cancelled()) continue;
            int slack = slackOf(needs);
            for (LocalDate date : occurrencesIn(event, breaks, from, to, slack)) {
                for (var need : needsForDate(needs, date)) {
                    Instant start =
                            EquipmentOccurrenceWindows.startOf(event, date).minus(need.lead());
                    Instant end = EquipmentOccurrenceWindows.endOf(event, date).plus(need.trail());
                    if (!start.isBefore(to) || !end.isAfter(from)) continue;
                    int handed = firm.getOrDefault(new HandoverKey(need.id(), date), List.of())
                            .size();
                    int loose = Math.max(0, need.quantity() - handed);
                    if (loose > 0) {
                        claims.add(new EquipmentClaim(
                                ClaimOrigin.OWN_NEED,
                                resolvedOrNull(need.target(), resolved),
                                event.name(),
                                event.id(),
                                date,
                                loose,
                                start,
                                end,
                                false));
                    }
                    for (var handover :
                            firm.getOrDefault(new HandoverKey(need.id(), date), List.<EquipmentHandover>of())) {
                        claims.add(new EquipmentClaim(
                                ClaimOrigin.OWN_NEED,
                                resolvedOrNull(LineTarget.item(handover.itemId()), resolved),
                                event.name(),
                                event.id(),
                                date,
                                1,
                                handover.claimFrom(),
                                handover.claimTo(),
                                true));
                    }
                }
            }
        }
        return claims;
    }

    private Map<HandoverKey, List<EquipmentHandover>> firmCounts(int stationId, Instant from, Instant to) {
        var byKey = new HashMap<HandoverKey, List<EquipmentHandover>>();
        for (var handover : needRepository.findOpenHandovers(stationId, from, to)) {
            byKey.computeIfAbsent(new HandoverKey(handover.needId(), handover.eventDate()), key -> new ArrayList<>())
                    .add(handover);
        }
        return byKey;
    }

    private List<EquipmentClaim> loanClaims(
            int stationId, Instant from, Instant to, Map<LineTarget, ResolvedTarget> resolved) {
        LocalDate dayFrom = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate dayTo = to.atZone(ZoneOffset.UTC).toLocalDate();
        var claims = new ArrayList<EquipmentClaim>();
        for (var loan : availabilityRepository.loanClaims(stationId, dayFrom, dayTo)) {
            LineTarget target = loan.assignedItemId() != null
                    ? LineTarget.item(loan.assignedItemId())
                    : targetOf(loan.itemId(), loan.artId(), loan.inventoryId());
            if (target == null) continue;
            claims.add(new EquipmentClaim(
                    ClaimOrigin.LOAN,
                    resolvedOrNull(target, resolved),
                    loan.partnerName(),
                    null,
                    null,
                    loan.assignedItemId() != null ? 1 : loan.quantity(),
                    dayStart(loan.dateFrom()),
                    dayEnd(loan.dateTo()),
                    loan.assignedItemId() != null));
        }
        return claims;
    }

    private List<EquipmentClaim> blockClaims(
            int stationId, Instant from, Instant to, Map<LineTarget, ResolvedTarget> resolved) {
        LocalDate dayFrom = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate dayTo = to.atZone(ZoneOffset.UTC).toLocalDate();
        var claims = new ArrayList<EquipmentClaim>();
        for (var block : availabilityRepository.blockClaims(stationId, dayFrom, dayTo)) {
            LineTarget target = targetOf(block.itemId(), null, block.inventoryId());
            int quantity = target == null ? 0 : availabilityRepository.stockOf(stationId, target);
            claims.add(new EquipmentClaim(
                    ClaimOrigin.BLOCK,
                    target == null ? null : resolvedOrNull(target, resolved),
                    block.reason(),
                    null,
                    null,
                    quantity,
                    dayStart(block.blockFrom()),
                    dayEnd(block.blockTo()),
                    block.itemId() != null));
        }
        return claims;
    }

    /**
     * A block takes everything it covers, however much that is, which is what setting a period aside
     * means. Its count therefore follows the question rather than being written down.
     */
    private ResolvedTarget resolvedOrNull(LineTarget target, Map<LineTarget, ResolvedTarget> resolved) {
        return resolved.computeIfAbsent(
                target, key -> availabilityRepository.resolve(key).orElse(null));
    }

    private static EquipmentClaim sizeAgainst(EquipmentClaim claim, int stock) {
        if (claim.origin() != ClaimOrigin.BLOCK || claim.firm()) return claim;
        return new EquipmentClaim(
                claim.origin(),
                claim.target(),
                claim.label(),
                claim.eventId(),
                claim.eventDate(),
                stock,
                claim.from(),
                claim.to(),
                claim.firm());
    }

    private static LineTarget targetOf(Integer itemId, Integer artId, Integer inventoryId) {
        if (itemId != null) return LineTarget.item(itemId);
        if (artId != null) return LineTarget.art(artId);
        if (inventoryId != null) return LineTarget.inventory(inventoryId);
        return null;
    }

    private static int slackOf(List<EquipmentNeed> needs) {
        int minutes = needs.stream()
                .mapToInt(need -> Math.max(need.leadMinutes(), need.trailMinutes()))
                .max()
                .orElse(0);
        return Math.min(MAX_LEAD_TRAIL_DAYS, minutes / (24 * 60) + 1);
    }

    /**
     * A day-grained window read as the whole day, which is what a day means. That is the one place
     * where the two granularities meet: the blocks and the lending requests are days, and a claim is
     * instants, so a day becomes the day it stands for rather than a moment inside it.
     */
    private static Instant dayStart(LocalDate date) {
        return date == null ? Instant.EPOCH : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * The end of a day-grained window. A request with no return date reaches forward without an end,
     * which is what an open end means: the piece is not offered to a second borrower until it is known
     * to be back.
     */
    private static Instant dayEnd(LocalDate date) {
        return date == null
                ? Instant.MAX
                : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private record HandoverKey(int needId, LocalDate date) {}
}
