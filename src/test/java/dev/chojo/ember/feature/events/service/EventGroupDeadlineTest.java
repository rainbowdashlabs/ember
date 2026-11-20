/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An event group with a relative deadline closes the span before the start of its first part, and its
 * parts carry that instant: when the first part moves or goes, the close moves with it and the notice
 * about a passed deadline is due again.
 */
class EventGroupDeadlineTest extends EventGroupServiceTestBase {

    private static final RegistrationDeadline TWO_DAYS =
            RegistrationDeadline.relative(2 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT);

    private EventGroup relativeGroup(String name) {
        return create(
                new EventGroupDetails(name, null, null, TWO_DAYS, false, null),
                List.of(new EventGroupSetDraft(null, "Tage", 1)));
    }

    private void addPart(EventGroup eventGroup, StationEvent part) {
        service.addPart(
                station.id(),
                eventGroup.id(),
                part.id(),
                set(eventGroup, "Tage").id());
    }

    private Instant closesAt(EventGroup eventGroup) {
        return service.findById(eventGroup.id()).orElseThrow().closesAt();
    }

    private RegistrationDeadline deadlineOf(StationEvent part) {
        return eventRepo.findById(part.id()).orElseThrow().registrationDeadline();
    }

    /** Whether the notice that the deadline passed counts for the close as it stands. */
    private boolean deadlineNotified(EventGroup eventGroup) {
        return query("""
                        SELECT deadline_notified_for IS NOT DISTINCT FROM event_group_closes_at(id) AS counts
                        FROM event_group
                        WHERE id = :id;""")
                .single(call().bind("id", eventGroup.id()))
                .map(row -> row.getBoolean("counts"))
                .first()
                .orElseThrow();
    }

    /** How many closing warnings count for the close as it stands. */
    private int closingWarnings(EventGroup eventGroup) {
        return query("""
                        SELECT count(*) AS n
                        FROM event_group_deadline_reminder_sent
                        WHERE event_group_id = :id
                          AND closes_at = event_group_closes_at(:id);""")
                .single(call().bind("id", eventGroup.id()))
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow();
    }

    private void notifiedAndWarned(EventGroup eventGroup) {
        var closes = Objects.requireNonNull(closesAt(eventGroup));
        eventGroupRepo.markDeadlineNotified(eventGroup.id(), closes);
        eventGroupRepo.markClosingWarned(eventGroup.id(), 3, closes);
        assertTrue(deadlineNotified(eventGroup));
        assertEquals(1, closingWarnings(eventGroup));
    }

    private void move(StationEvent part, Instant start) {
        newEventServices(new DomainEventBus(Set.of()))
                .crud()
                .update(
                        part.id(),
                        part.name(),
                        part.description(),
                        part.eventType(),
                        part.dayOfWeek(),
                        start,
                        start.plus(Duration.ofHours(8)),
                        null,
                        true,
                        RegistrationDeadline.none(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null)
                .orElseThrow();
    }

    @Test
    void aRelativeDeadlineClosesTheSpanBeforeTheFirstPart() {
        var eventGroup = relativeGroup("Relativ");
        assertNull(closesAt(eventGroup), "without a part there is nothing to count from");

        var later = oneTime(30);
        var first = oneTime(20);
        addPart(eventGroup, later);
        addPart(eventGroup, first);

        var closes = first.startTime().minus(Duration.ofDays(2));
        assertEquals(closes, closesAt(eventGroup));
        assertEquals(RegistrationDeadline.fixed(closes), deadlineOf(first));
        assertEquals(RegistrationDeadline.fixed(closes), deadlineOf(later));
        assertEquals(
                TWO_DAYS,
                service.findById(eventGroup.id()).orElseThrow().details().registrationDeadline());
    }

    @Test
    void movingTheFirstPartMovesTheCloseSoTheNoticeCountsNoMore() {
        var eventGroup = relativeGroup("Verschoben");
        var first = oneTime(22);
        var second = oneTime(32);
        addPart(eventGroup, first);
        addPart(eventGroup, second);
        notifiedAndWarned(eventGroup);

        var moved = first.startTime().plus(Duration.ofDays(20));
        newEventServices(new DomainEventBus(Set.of()))
                .crud()
                .update(
                        first.id(),
                        first.name(),
                        first.description(),
                        first.eventType(),
                        first.dayOfWeek(),
                        moved,
                        moved.plus(Duration.ofHours(8)),
                        null,
                        true,
                        RegistrationDeadline.none(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null)
                .orElseThrow();

        var closes = second.startTime().minus(Duration.ofDays(2));
        assertEquals(closes, closesAt(eventGroup), "the other part is first now");
        assertEquals(RegistrationDeadline.fixed(closes), deadlineOf(first));
        assertEquals(RegistrationDeadline.fixed(closes), deadlineOf(second));
        assertFalse(deadlineNotified(eventGroup));
        assertEquals(0, closingWarnings(eventGroup));
    }

    /**
     * The sweep warns again once the first part moves, though nothing armed the warning again: the
     * warning recorded was about the close before.
     */
    @Test
    void aGroupWarnedAboutIsWarnedAgainOnceItsFirstPartMoves() {
        var eventGroup = relativeGroup("Wieder gewarnt");
        var first = oneTime(4);
        addPart(eventGroup, first);
        addPart(eventGroup, oneTime(10));
        var closes = Objects.requireNonNull(closesAt(eventGroup));
        assertTrue(closingIn(eventGroup));
        eventGroupRepo.markClosingWarned(eventGroup.id(), 3, closes);
        assertFalse(closingIn(eventGroup), "warned for this close");

        move(first, first.startTime().plus(Duration.ofHours(6)));

        assertEquals(closes.plus(Duration.ofHours(6)), closesAt(eventGroup));
        assertTrue(closingIn(eventGroup), "the warning was about the close before");
    }

    private boolean closingIn(EventGroup eventGroup) {
        return eventGroupRepo.findClosingIn(3).stream().anyMatch(closing -> closing.eventGroupId() == eventGroup.id());
    }

    @Test
    void editingAPartWithoutMovingTheCloseKeepsTheNotice() {
        var eventGroup = relativeGroup("Unverändert");
        var first = oneTime(24);
        var second = oneTime(34);
        addPart(eventGroup, first);
        addPart(eventGroup, second);
        notifiedAndWarned(eventGroup);

        newEventServices(new DomainEventBus(Set.of()))
                .crud()
                .update(
                        second.id(),
                        "Anders benannt",
                        second.description(),
                        second.eventType(),
                        second.dayOfWeek(),
                        second.startTime(),
                        second.endTime(),
                        null,
                        true,
                        RegistrationDeadline.none(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null)
                .orElseThrow();

        assertTrue(deadlineNotified(eventGroup));
        assertEquals(1, closingWarnings(eventGroup));
    }

    @Test
    void deletingTheFirstPartMovesTheCloseSoTheNoticeCountsNoMore() {
        var eventGroup = relativeGroup("Gelöscht");
        var first = oneTime(23);
        var second = oneTime(33);
        addPart(eventGroup, first);
        addPart(eventGroup, second);
        notifiedAndWarned(eventGroup);

        assertTrue(newEventServices(new DomainEventBus(Set.of())).crud().delete(first.id()));

        var closes = second.startTime().minus(Duration.ofDays(2));
        assertEquals(closes, closesAt(eventGroup));
        assertEquals(RegistrationDeadline.fixed(closes), deadlineOf(second));
        assertFalse(deadlineNotified(eventGroup));
        assertEquals(0, closingWarnings(eventGroup));
    }

    @Test
    void removingTheFirstPartMovesTheClose() {
        var eventGroup = relativeGroup("Herausgenommen");
        var first = oneTime(25);
        var second = oneTime(35);
        addPart(eventGroup, first);
        addPart(eventGroup, second);
        var closedWith = Objects.requireNonNull(closesAt(eventGroup));

        service.removePart(station.id(), eventGroup.id(), first.id());

        var closes = second.startTime().minus(Duration.ofDays(2));
        assertEquals(closes, closesAt(eventGroup));
        assertEquals(RegistrationDeadline.fixed(closes), deadlineOf(second));
        assertEquals(
                RegistrationDeadline.fixed(closedWith),
                deadlineOf(first),
                "the part that left keeps the close it had, as it keeps its places");
    }

    @Test
    void partsOfADeletedGroupKeepItsClose() {
        var eventGroup = relativeGroup("Aufgelöst");
        var first = oneTime(26);
        var second = oneTime(36);
        addPart(eventGroup, first);
        addPart(eventGroup, second);
        var closes = Objects.requireNonNull(closesAt(eventGroup));

        service.delete(station.id(), eventGroup.id());

        assertEquals(RegistrationDeadline.fixed(closes), deadlineOf(first));
        assertEquals(RegistrationDeadline.fixed(closes), deadlineOf(second));
    }

    @Test
    void aPartStoresNoDeadlineOfItsOwnAndReadsTheGroupsClose() {
        var eventGroup = relativeGroup("Gespeichert");
        var part = oneTime(29);
        addPart(eventGroup, part);

        var stored = query("""
                        SELECT registration_deadline_kind
                        FROM station_event
                        WHERE id = :id;""")
                .single(call().bind("id", part.id()))
                .map(row -> row.getString("registration_deadline_kind"))
                .first()
                .orElseThrow();
        assertEquals("NONE", stored, "the part keeps no copy");
        assertEquals(RegistrationDeadline.fixed(part.startTime().minus(Duration.ofDays(2))), deadlineOf(part));
    }

    @Test
    void changingTheRuleReachesEveryPart() {
        var eventGroup = relativeGroup("Umgestellt");
        var part = oneTime(26);
        addPart(eventGroup, part);

        var fixed = Instant.now().plus(Duration.ofDays(5)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        service.update(
                station.id(),
                eventGroup.id(),
                new EventGroupDetails("Umgestellt", null, null, RegistrationDeadline.fixed(fixed), false, null));
        assertEquals(fixed, closesAt(eventGroup));
        assertEquals(RegistrationDeadline.fixed(fixed), deadlineOf(part));

        service.update(
                station.id(),
                eventGroup.id(),
                new EventGroupDetails("Umgestellt", null, null, RegistrationDeadline.none(), false, null));
        assertNull(closesAt(eventGroup));
        assertEquals(RegistrationDeadline.none(), deadlineOf(part));
    }

    @Test
    void changingTheRuleOfAGroupWithPartsLeavesTheNoticeAndWarningsBehind() {
        var eventGroup = relativeGroup("Neue Frist");
        var part = oneTime(27);
        addPart(eventGroup, part);
        notifiedAndWarned(eventGroup);

        service.update(
                station.id(),
                eventGroup.id(),
                new EventGroupDetails(
                        "Neue Frist",
                        null,
                        null,
                        RegistrationDeadline.relative(3 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT),
                        false,
                        null));

        assertEquals(part.startTime().minus(Duration.ofDays(3)), closesAt(eventGroup));
        assertFalse(deadlineNotified(eventGroup));
        assertEquals(0, closingWarnings(eventGroup));
    }

    @Test
    void savingAGroupWithoutMovingItsCloseKeepsTheNotice() {
        var eventGroup = relativeGroup("Gleiche Frist");
        addPart(eventGroup, oneTime(28));
        notifiedAndWarned(eventGroup);

        service.update(
                station.id(),
                eventGroup.id(),
                new EventGroupDetails("Anders benannt", null, null, TWO_DAYS, false, null));

        assertTrue(deadlineNotified(eventGroup));
        assertEquals(1, closingWarnings(eventGroup));
    }

    @Test
    void aRuleThatCannotBePlacedIsRefused() {
        var eventGroup = relativeGroup("Ungültig");

        assertRefused(
                EventRefusal.REGISTRATION_DEADLINE_INVALID,
                () -> service.update(
                        station.id(),
                        eventGroup.id(),
                        new EventGroupDetails(
                                "Ungültig",
                                null,
                                null,
                                RegistrationDeadline.relative(-60, RegistrationDeadline.DeadlineAnchor.EXACT),
                                false,
                                null)));
        assertEquals(
                TWO_DAYS,
                service.findById(eventGroup.id()).orElseThrow().details().registrationDeadline());
    }

    @Test
    void anAppointmentWithARuleThatCannotBePlacedIsRefused() {
        var start = Instant.now().plus(Duration.ofDays(40));

        assertRefused(EventRefusal.REGISTRATION_DEADLINE_INVALID, () -> newEventServices(new DomainEventBus(Set.of()))
                .crud()
                .create(
                        station.id(),
                        "Ungültig",
                        null,
                        StationEvent.EventType.ONE_TIME,
                        null,
                        start,
                        start.plus(Duration.ofHours(2)),
                        null,
                        true,
                        new RegistrationDeadline(RegistrationDeadline.DeadlineKind.FIXED, null, null, null),
                        false,
                        null,
                        null,
                        null,
                        null));
    }
}
