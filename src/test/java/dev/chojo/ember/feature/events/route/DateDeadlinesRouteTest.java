/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EventDateCancellation;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.events.repository.EventCopyRepository;
import dev.chojo.ember.feature.events.repository.EventDateCancellationRepository;
import dev.chojo.ember.feature.events.repository.EventDetachedDateRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventChangeNotifier;
import dev.chojo.ember.feature.events.service.EventChangePreviewService;
import dev.chojo.ember.feature.events.service.EventCopyService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventEditScopeService;
import dev.chojo.ember.feature.events.service.EventExportService;
import dev.chojo.ember.feature.events.service.EventFieldRegistrationService;
import dev.chojo.ember.feature.events.service.EventOccurrenceService;
import dev.chojo.ember.feature.events.service.EventReminderService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A screen showing one date of an appointment reads when its sign-up closes and when its minimum is
 * checked from the server, placed on the station's clock.
 */
class DateDeadlinesRouteTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_ID = 9;
    private static final LocalDate FIRST = LocalDate.of(2030, 5, 1);

    private static StationEvent weekly(RegistrationDeadline deadline, Integer thresholdDays) {
        var start = FIRST.atTime(18, 0).toInstant(ZoneOffset.UTC);
        return new StationEvent(
                EVENT_ID,
                STATION_ID,
                "Übung",
                null,
                StationEvent.EventType.RECURRING,
                FIRST.getDayOfWeek().getValue(),
                start,
                start.plusSeconds(7200),
                null,
                true,
                deadline,
                false,
                null,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                thresholdDays == null ? null : 2,
                thresholdDays,
                null,
                null);
    }

    private static StationEvent oneTime(RegistrationDeadline deadline) {
        var start = FIRST.atTime(18, 0).toInstant(ZoneOffset.UTC);
        return new StationEvent(
                EVENT_ID,
                STATION_ID,
                "Fest",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(7200),
                null,
                true,
                deadline,
                false,
                null,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static StationEvent withoutMinimum(StationEvent event) {
        return new StationEvent(
                event.id(),
                event.stationId(),
                event.name(),
                event.description(),
                event.eventType(),
                event.dayOfWeek(),
                event.startTime(),
                event.endTime(),
                event.templateId(),
                event.requiresRegistration(),
                event.registrationDeadline(),
                event.requiresConfirmation(),
                event.categoryId(),
                event.restrictionMode(),
                event.viewRestrictionMode(),
                event.restricted(),
                event.isPublic(),
                event.registrationLimit(),
                event.cancelled(),
                event.cancelledAt(),
                event.cancelReason(),
                null,
                event.thresholdDays(),
                event.repeatUntil(),
                event.repeatCount(),
                event.queueOrder());
    }

    private static Response answer(StationEvent event, String query, Set<LocalDate> calledOff) {
        var crud = mock(EventCrudService.class);
        when(crud.findById(EVENT_ID)).thenReturn(Optional.of(event));
        var cancellations = mock(EventDateCancellationRepository.class);
        when(cancellations.findActiveByStation(STATION_ID))
                .thenReturn(calledOff.stream()
                        .map(date -> new EventDateCancellation(
                                EVENT_ID, date, CancellationCause.MANUAL, null, Instant.now(), null, null))
                        .toList());
        var calendar = new OccurrenceCalendar(
                mock(EventRepository.class),
                mock(EventBreakRepository.class),
                cancellations,
                mock(EventDetachedDateRepository.class),
                mock(StationRepository.class));
        var restrictions = mock(EventRestrictionService.class);
        var routes = new EventRoutes(
                crud,
                mock(EventOccurrenceService.class),
                restrictions,
                mock(EventReminderService.class),
                mock(BatchEventService.class),
                mock(GuardianPolicy.class),
                mock(EventExportService.class),
                mock(EventTemplateService.class),
                mock(EventFieldRegistrationService.class),
                calendar,
                new EventVisibility(crud, restrictions, mock(GuardianPolicy.class)),
                mock(EventEditScopeService.class),
                mock(EventChangeNotifier.class),
                mock(EventChangePreviewService.class),
                new EventCopyService(mock(EventRepository.class), mock(EventCopyRepository.class)));
        var harness = RouteHarness.serving(routes);
        return harness.request(client -> client.get(
                PREFIX + "/events/%d/date-deadlines%s".formatted(EVENT_ID, query),
                harness.as(TestSessions.member(STATION_ID, StationPermission.USER, StationPermission.EVENT_EDIT))));
    }

    private static EventRoutes.DateDeadlines read(Response answer) {
        return RouteHarness.read(answer, EventRoutes.DateDeadlines.class);
    }

    private static DateDeadlinesResponse ask(StationEvent event, String query) {
        var read = read(answer(event, query, Set.of()));
        return new DateDeadlinesResponse(read.date(), read.registrationClosesAt(), read.minimumCheckedAt());
    }

    private record DateDeadlinesResponse(LocalDate date, Instant closes, Instant minimum) {}

    @Test
    void aDateOfASeriesReadsItsOwnCloseAndMinimum() {
        var event = weekly(RegistrationDeadline.relative(48 * 60, RegistrationDeadline.DeadlineAnchor.EXACT), 3);
        var date = FIRST.plusWeeks(2);

        var read = ask(event, "?date=" + date);

        assertEquals(date, read.date());
        assertEquals(date.atTime(18, 0).toInstant(ZoneOffset.UTC).minusSeconds(48 * 3600), read.closes());
        assertEquals(date.minusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC), read.minimum());
    }

    @Test
    void withoutADateTheDateInViewIsRead() {
        var event = weekly(RegistrationDeadline.none(), null);

        var read = ask(event, "");

        assertEquals(FIRST, read.date(), "the series has not begun, so its first date is in view");
        assertNull(read.closes());
        assertNull(read.minimum());
    }

    @Test
    void aDayTheSeriesDoesNotFallOnIsRefused() {
        var event = weekly(RegistrationDeadline.relative(48 * 60, RegistrationDeadline.DeadlineAnchor.EXACT), 3);

        assertEquals(
                EventRefusal.REGISTRATION_DAY_NOT_AN_OCCURRENCE,
                refusalOf(answer(event, "?date=" + FIRST.plusDays(1), Set.of())));
        assertEquals(
                EventRefusal.REGISTRATION_DAY_NOT_AN_OCCURRENCE,
                refusalOf(answer(event, "?date=" + FIRST.minusWeeks(1), Set.of())),
                "a day before its first date");
    }

    @Test
    void aCalledOffDateIsStillAnswered() {
        var event = weekly(RegistrationDeadline.relative(48 * 60, RegistrationDeadline.DeadlineAnchor.EXACT), 3);
        var date = FIRST.plusWeeks(1);

        var read = read(answer(event, "?date=" + date, Set.of(date)));

        assertEquals(date, read.date());
        assertEquals(date.atTime(18, 0).toInstant(ZoneOffset.UTC).minusSeconds(48 * 3600), read.registrationClosesAt());
    }

    @Test
    void aOneTimeAppointmentAnswersForItsOwnDate() {
        var event = oneTime(RegistrationDeadline.relative(24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT));

        var read = read(answer(event, "?date=" + FIRST.plusDays(3), Set.of()));

        assertEquals(FIRST, read.date());
        assertEquals(
                FIRST.atTime(18, 0).toInstant(ZoneOffset.UTC).minusSeconds(24 * 3600), read.registrationClosesAt());
    }

    @Test
    void theMinimumIsOnlyReadWithAMinimumSet() {
        var event = withoutMinimum(weekly(RegistrationDeadline.none(), 3));

        assertNull(read(answer(event, "?date=" + FIRST, Set.of())).minimumCheckedAt());
    }
}
