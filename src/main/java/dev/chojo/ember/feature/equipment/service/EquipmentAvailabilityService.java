/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.feature.equipment.entity.ClaimOrigin;
import dev.chojo.ember.feature.equipment.entity.ClaimSnapshot;
import dev.chojo.ember.feature.equipment.entity.EquipmentAvailability;
import dev.chojo.ember.feature.equipment.entity.EquipmentClaim;
import dev.chojo.ember.feature.equipment.entity.EquipmentHandover;
import dev.chojo.ember.feature.equipment.entity.EquipmentNeed;
import dev.chojo.ember.feature.equipment.repository.EquipmentAvailabilityRepository;
import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.events.service.OccurrenceWindows;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.inventory.entity.ResolvedTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

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
     * How far outside the queried window an occurrence may lie and still reach into it, which bounds the
     * walk over the recurrence rule. A lead or trail beyond this is refused when the line is written.
     */
    static final int MAX_LEAD_TRAIL_DAYS = 60;

    private final EquipmentAvailabilityRepository availabilityRepository;
    private final EquipmentNeedRepository needRepository;
    private final EventRepository eventRepository;
    private final OccurrenceCalendar occurrenceCalendar;

    @Inject
    public EquipmentAvailabilityService(
            EquipmentAvailabilityRepository availabilityRepository,
            EquipmentNeedRepository needRepository,
            EventRepository eventRepository,
            OccurrenceCalendar occurrenceCalendar) {
        this.availabilityRepository = availabilityRepository;
        this.needRepository = needRepository;
        this.eventRepository = eventRepository;
        this.occurrenceCalendar = occurrenceCalendar;
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
            int stationId, LineTarget target, Instant from, Instant to, @Nullable Integer ignoreNeedId) {
        return availability(stationId, target, from, to, ignoreNeedId, null);
    }

    /**
     * What a station has free of one thing over one window, leaving one line and one lending request
     * out.
     *
     * <p>The request is left out for the same reason a line is: what fills an approved request must
     * not read that request's own promise as gear somebody else has taken, and by the time it is
     * filled the request is approved and therefore already counts as a loan.
     *
     * @param stationId       the station asking
     * @param target          what is being asked about
     * @param from            the first moment of the window
     * @param to              the last moment of the window
     * @param ignoreNeedId    the line not to count, or {@code null} to count every one
     * @param ignoreRequestId the lending request not to count, or {@code null} to count every one
     * @return the stock and everything already holding some of it
     */
    public EquipmentAvailability availability(
            int stationId,
            LineTarget target,
            Instant from,
            Instant to,
            @Nullable Integer ignoreNeedId,
            @Nullable Integer ignoreRequestId) {
        ResolvedTarget question = availabilityRepository
                .resolve(target)
                .orElseThrow(() -> new IllegalArgumentException("The equipment does not exist"));
        int stock = availabilityRepository.stockOf(stationId, target);
        List<EquipmentClaim> claims = claims(stationId, from, to, ignoreNeedId, ignoreRequestId).stream()
                .filter(claim -> claim.touches(question, from, to))
                .map(claim -> sizeAgainst(claim, stock))
                .toList();
        return new EquipmentAvailability(question, from, to, stock, claims);
    }

    /**
     * Every claim on a station's stock over a span, read once, for a caller that counts many windows or
     * many things inside it.
     *
     * @param stationId the station
     * @param from      the first moment of the span
     * @param to        the last moment of the span
     * @return the claims
     */
    public ClaimSnapshot snapshot(int stationId, Instant from, Instant to) {
        return snapshot(stationId, from, to, null);
    }

    /**
     * The same, leaving one lending request out, for that request asking what it can still find.
     *
     * @param ignoreRequestId the lending request not to count, or {@code null} to count every one
     */
    public ClaimSnapshot snapshot(int stationId, Instant from, Instant to, @Nullable Integer ignoreRequestId) {
        return new ClaimSnapshot(claims(stationId, from, to, null, ignoreRequestId));
    }

    /**
     * Whether a block takes something over a window: one on the station, its inventory or, for a piece,
     * the piece itself.
     *
     * @param stationId the station
     * @param target    what is asked about
     * @param from      the first moment of the window
     * @param to        the last moment of the window
     * @return {@code true} when a block covers it, or when it no longer exists
     */
    public boolean blocked(int stationId, LineTarget target, Instant from, Instant to) {
        return availabilityRepository
                .resolve(target)
                .map(resolved -> snapshot(stationId, from, to).blocked(resolved, from, to))
                .orElse(true);
    }

    /**
     * One thing to ask a snapshot about, resolved with its stock.
     *
     * @param stationId the station
     * @param target    what is asked about
     * @return the question, or empty where the thing no longer exists
     */
    public Optional<ClaimSnapshot.Question> question(int stationId, LineTarget target) {
        return availabilityRepository
                .resolve(target)
                .map(resolved -> new ClaimSnapshot.Question(
                        target, resolved, availabilityRepository.stockOf(stationId, target)));
    }

    /**
     * The pieces of one target nobody has set aside by name over a window.
     *
     * <p>Loose claims name no pieces, so they cannot take one out of this list; they take a count out
     * of {@link EquipmentAvailability#free()} instead. What is dropped here is what a piece-level
     * claim points at: a piece already handed over for another date, a piece set aside for a
     * partner, and a piece a block names. A block on the whole target, its inventory or the station
     * leaves nothing.
     *
     * @param stationId the station asking
     * @param target    what is being asked about
     * @param from      the first moment of the window
     * @param to        the last moment of the window
     * @return the piece IDs, in a stable order
     */
    public List<Integer> freePieces(int stationId, LineTarget target, Instant from, Instant to) {
        return freePieces(stationId, target, from, to, null);
    }

    /**
     * The same, leaving one lending request out, which is that request filling itself: the pieces its own
     * lines name are not taken from it by its own claim.
     *
     * @param ignoreRequestId the lending request not to count, or {@code null} to count every one
     */
    public List<Integer> freePieces(
            int stationId, LineTarget target, Instant from, Instant to, @Nullable Integer ignoreRequestId) {
        var snapshot = snapshot(stationId, from, to, ignoreRequestId);
        var resolved = availabilityRepository.resolve(target).orElse(null);
        if (resolved == null || snapshot.blocked(resolved, from, to)) return List.of();
        var spoken = snapshot.named(from, to);
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
    public List<EquipmentClaim> claims(int stationId, Instant from, Instant to, @Nullable Integer ignoreNeedId) {
        return claims(stationId, from, to, ignoreNeedId, null);
    }

    /**
     * Everything holding some of a station's stock over a window, from all three origins, leaving one
     * line and one lending request out.
     *
     * @param stationId       the station
     * @param from            the first moment of the window
     * @param to              the last moment of the window
     * @param ignoreNeedId    the line not to count, or {@code null} to count every one
     * @param ignoreRequestId the lending request not to count, or {@code null} to count every one
     * @return the claims
     */
    public List<EquipmentClaim> claims(
            int stationId,
            Instant from,
            Instant to,
            @Nullable Integer ignoreNeedId,
            @Nullable Integer ignoreRequestId) {
        var resolved = new HashMap<LineTarget, ResolvedTarget>();
        var claims = new ArrayList<EquipmentClaim>();
        claims.addAll(ownClaims(stationId, from, to, ignoreNeedId, resolved));
        claims.addAll(loanClaims(stationId, from, to, ignoreRequestId, resolved));
        claims.addAll(blockClaims(stationId, from, to, resolved));
        return claims;
    }

    /**
     * The occurrences one appointment produces inside a window, widened by the longest lead and trail
     * its lines carry so that an occurrence just outside can still reach in.
     *
     * @param event  the appointment
     * @param calendar the station's calendar, breaks included
     * @param from   the first moment of the window
     * @param to     the last moment of the window
     * @param slack  the widest lead or trail to allow for
     * @return the dates, in order
     */
    public static List<LocalDate> occurrencesIn(
            StationEvent event, StationCalendar calendar, Instant from, Instant to, int slack) {
        LocalDate first = from.atZone(ZoneOffset.UTC).toLocalDate().minusDays(slack + 1L);
        LocalDate last = to.atZone(ZoneOffset.UTC).toLocalDate().plusDays(slack + 1L);
        if (event.isRecurring()) return calendar.between(event, first, last);
        LocalDate single = OccurrenceWindows.singleDateOf(event, calendar.zone());
        if (single != null && !single.isBefore(first) && !single.isAfter(last)) return List.of(single);
        return List.of();
    }

    /**
     * What one date of an appointment needs, which is the standing list with that date's own lines
     * folded in.
     *
     * <p>A line written for the date is added; where it names the same thing as a standing line it
     * takes its place, which is how one Dienst a year asks for twenty jackets instead of fourteen
     * without touching the other forty-nine.
     *
     * @param needs every line of the appointment
     * @param date  the date
     * @return the lines that hold for that date
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
            int stationId,
            Instant from,
            Instant to,
            @Nullable Integer ignoreNeedId,
            Map<LineTarget, ResolvedTarget> resolved) {
        var needsByEvent = new HashMap<Integer, List<EquipmentNeed>>();
        for (var need : needRepository.findByStation(stationId)) {
            if (ignoreNeedId != null && ignoreNeedId == need.id()) continue;
            needsByEvent
                    .computeIfAbsent(need.eventId(), key -> new ArrayList<>())
                    .add(need);
        }
        if (needsByEvent.isEmpty()) return List.of();

        var calendar = occurrenceCalendar.forStation(stationId);
        var firm = firmCounts(stationId, from, to);
        var claims = new ArrayList<EquipmentClaim>();

        for (var event : eventRepository.findByStation(stationId)) {
            var needs = needsByEvent.get(event.id());
            if (needs == null || event.cancelled()) continue;
            int slack = slackOf(needs);
            for (LocalDate date : occurrencesIn(event, calendar, from, to, slack)) {
                if (calendar.isCancelled(event, date)) continue;
                for (var need : needsForDate(needs, date)) {
                    Instant start = OccurrenceWindows.startOf(event, date, calendar.zone())
                            .minus(need.lead());
                    Instant end = OccurrenceWindows.endOf(event, date, calendar.zone())
                            .plus(need.trail());
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
            int stationId,
            Instant from,
            Instant to,
            @Nullable Integer ignoreRequestId,
            Map<LineTarget, ResolvedTarget> resolved) {
        LocalDate dayFrom = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate dayTo = to.atZone(ZoneOffset.UTC).toLocalDate();
        var claims = new ArrayList<EquipmentClaim>();
        for (var loan : availabilityRepository.loanClaims(stationId, dayFrom, dayTo, ignoreRequestId)) {
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
    private @Nullable ResolvedTarget resolvedOrNull(LineTarget target, Map<LineTarget, ResolvedTarget> resolved) {
        return resolved.computeIfAbsent(
                target, key -> availabilityRepository.resolve(key).orElse(null));
    }

    private static EquipmentClaim sizeAgainst(EquipmentClaim claim, int stock) {
        return ClaimSnapshot.sized(claim, stock);
    }

    private static @Nullable LineTarget targetOf(
            @Nullable Integer itemId, @Nullable Integer artId, @Nullable Integer inventoryId) {
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
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** The end of a day-grained window: the start of the day after its last day. */
    private static Instant dayEnd(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private record HandoverKey(int needId, LocalDate date) {}
}
