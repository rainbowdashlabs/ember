/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import io.javalin.http.Context;
import io.javalin.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The fairness ranking of an event, and the category it is ranked within.
 *
 * <p>An event does not have to be in a category, and the ranking then covers everything the
 * station has done. The route said so already, by falling back to the event's own category, but
 * the fallback stood beside a plain int, which unboxes it: an event in no category answered 500
 * to the screen a manager opens before deciding who gets a place.
 */
class EventRegistrationStatsRouteTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_ID = 9;

    private EventCrudService crudService;
    private EventRegistrationService registrationService;
    private EventRegistrationRoutes routes;

    private static StationEvent eventInCategory(Integer categoryId) {
        return new StationEvent(
                EVENT_ID,
                STATION_ID,
                "Übungsabend",
                "Beschreibung",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                true,
                null,
                true,
                categoryId,
                RestrictionMode.OR,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null);
    }

    private static UserSession session() {
        return new UserSession(
                new Account(1, null, "manager@test.com", null, "Mara", "Nager", true, null, "Mara Nager", null, null),
                1,
                STATION_ID,
                null,
                null,
                Set.of(),
                Set.of(),
                null);
    }

    @BeforeEach
    void setup() {
        crudService = mock(EventCrudService.class);
        registrationService = mock(EventRegistrationService.class);
        when(registrationService.findStatsByEvent(anyInt(), any(), anyInt())).thenReturn(List.of());
        routes = new EventRegistrationRoutes(
                crudService,
                registrationService,
                mock(EventRestrictionService.class),
                mock(MemberNameResolver.class),
                mock(StationMemberService.class),
                mock(StationMemberRepository.class),
                mock(AccountRepository.class),
                mock(AttendanceService.class),
                mock(MemberIdentityFactory.class),
                mock(EventRegistrationFieldService.class));
    }

    @SuppressWarnings("unchecked")
    private Context asking(String categoryParam) {
        Context ctx = mock(Context.class);
        Validator<Integer> eventIdParam = mock(Validator.class);
        when(eventIdParam.get()).thenReturn(EVENT_ID);
        when(ctx.pathParamAsClass("eventId", Integer.class)).thenReturn(eventIdParam);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(session());
        when(ctx.queryParam("categoryId")).thenReturn(categoryParam);
        when(ctx.queryParam("months")).thenReturn(null);
        return ctx;
    }

    private void askForTheRanking(Context ctx) throws Exception {
        Method handler = EventRegistrationRoutes.class.getDeclaredMethod("getRegistrationStats", Context.class);
        handler.setAccessible(true);
        handler.invoke(routes, ctx);
    }

    @Test
    void anEventInNoCategoryIsRankedAcrossAllOfThem() throws Exception {
        when(crudService.findById(EVENT_ID)).thenReturn(Optional.of(eventInCategory(null)));

        askForTheRanking(asking(null));

        verify(registrationService).findStatsByEvent(eq(EVENT_ID), isNull(), eq(12));
    }

    @Test
    void anEventInACategoryIsRankedWithinIt() throws Exception {
        when(crudService.findById(EVENT_ID)).thenReturn(Optional.of(eventInCategory(4)));

        askForTheRanking(asking(null));

        verify(registrationService).findStatsByEvent(EVENT_ID, 4, 12);
    }

    /**
     * The screen may ask about a category other than the event's own, which is how a manager
     * compares one against another. What is asked for wins over what the event says.
     */
    @Test
    void theCategoryAskedForWinsOverTheEventsOwn() throws Exception {
        when(crudService.findById(EVENT_ID)).thenReturn(Optional.of(eventInCategory(4)));

        askForTheRanking(asking("7"));

        verify(registrationService).findStatsByEvent(EVENT_ID, 7, 12);
    }
}
