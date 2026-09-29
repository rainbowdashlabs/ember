/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventEmbedRoutesTest {

    private static final int STATION_ID = 7;
    private static final UUID EVENT_UID = UUID.fromString("7d7c1d5e-4e3a-4f9b-9d0e-2a4f6c1b8e11");

    private EventCrudService crudService;
    private EventCategoryService categoryService;
    private EventVisibility visibility;
    private EventEmbedRoutes routes;
    private UserSession session;
    private StationEvent event;

    @BeforeEach
    void setup() {
        crudService = mock(EventCrudService.class);
        categoryService = mock(EventCategoryService.class);
        visibility = mock(EventVisibility.class);
        routes = new EventEmbedRoutes(crudService, categoryService, visibility);
        session = mock(UserSession.class);
        when(session.stationId()).thenReturn(STATION_ID);
        event = mock(StationEvent.class);
        when(event.id()).thenReturn(42);
        when(event.name()).thenReturn("Summer camp");
        when(event.description()).thenReturn("Tents and a lake");
        when(event.startTime()).thenReturn(Instant.parse("2027-07-01T08:00:00Z"));
        when(event.endTime()).thenReturn(Instant.parse("2027-07-05T16:00:00Z"));
        when(event.categoryId()).thenReturn(3);
    }

    private Context asking(String uid) {
        Context ctx = mock(Context.class);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(session);
        when(ctx.pathParam("uid")).thenReturn(uid);
        return ctx;
    }

    private void ask(Context ctx) throws Exception {
        Method handler = EventEmbedRoutes.class.getDeclaredMethod("get", Context.class);
        handler.setAccessible(true);
        try {
            handler.invoke(routes, ctx);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void aVisibleEventIsAnsweredWithItsCategory() throws Exception {
        when(crudService.findByPublicUid(STATION_ID, EVENT_UID)).thenReturn(Optional.of(event));
        when(visibility.canSee(session, event)).thenReturn(true);
        when(categoryService.findById(3))
                .thenReturn(Optional.of(new EventCategory(3, STATION_ID, "Camps", 0, null, false, null)));
        var ctx = asking(EVENT_UID.toString());

        ask(ctx);

        var answer = ArgumentCaptor.forClass(Object.class);
        verify(ctx).json(answer.capture());
        var embedded = assertInstanceOf(EventEmbedRoutes.EmbeddedEvent.class, answer.getValue());
        assertEquals(42, embedded.id());
        assertEquals("Summer camp", embedded.name());
        assertEquals("Camps", embedded.categoryName());
    }

    @Test
    void anEventWithoutCategoryIsAnsweredWithoutOne() throws Exception {
        when(event.categoryId()).thenReturn(null);
        when(crudService.findByPublicUid(STATION_ID, EVENT_UID)).thenReturn(Optional.of(event));
        when(visibility.canSee(session, event)).thenReturn(true);
        var ctx = asking(EVENT_UID.toString());

        ask(ctx);

        var answer = ArgumentCaptor.forClass(Object.class);
        verify(ctx).json(answer.capture());
        assertNull(((EventEmbedRoutes.EmbeddedEvent) answer.getValue()).categoryName());
    }

    @Test
    void aHiddenEventIsNotFound() {
        when(crudService.findByPublicUid(STATION_ID, EVENT_UID)).thenReturn(Optional.of(event));
        when(visibility.canSee(session, event)).thenReturn(false);

        assertThrows(NotFoundResponse.class, () -> ask(asking(EVENT_UID.toString())));
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertThrows(NotFoundResponse.class, () -> ask(asking(EVENT_UID.toString())));
    }

    @SuppressWarnings("unchecked")
    private Context askingForTheReferenceOf(int eventId) {
        Context ctx = mock(Context.class);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(session);
        Validator<Integer> id = mock(Validator.class);
        when(id.get()).thenReturn(eventId);
        when(ctx.pathParamAsClass("id", Integer.class)).thenReturn(id);
        return ctx;
    }

    private void askForTheReference(Context ctx) throws Exception {
        Method handler = EventEmbedRoutes.class.getDeclaredMethod("reference", Context.class);
        handler.setAccessible(true);
        try {
            handler.invoke(routes, ctx);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void anAuthorIsHandedThePublicIdOfAnEventTheySee() throws Exception {
        when(event.stationId()).thenReturn(STATION_ID);
        when(visibility.requireVisibleEvent(session, 42)).thenReturn(event);
        when(crudService.findPublicUidsByIds(STATION_ID, List.of(42))).thenReturn(Map.of(42, EVENT_UID));
        var ctx = askingForTheReferenceOf(42);

        askForTheReference(ctx);

        verify(ctx).json(new EventEmbedRoutes.EmbedReference(EVENT_UID));
    }

    @Test
    void anEventWithoutPublicIdHasNoReference() {
        when(event.stationId()).thenReturn(STATION_ID);
        when(visibility.requireVisibleEvent(session, 42)).thenReturn(event);
        when(crudService.findPublicUidsByIds(STATION_ID, List.of(42))).thenReturn(Map.of());

        assertThrows(NotFoundResponse.class, () -> askForTheReference(askingForTheReferenceOf(42)));
    }

    @Test
    void aMalformedIdIsNotFound() {
        assertThrows(NotFoundResponse.class, () -> ask(asking("not-a-uuid")));
    }
}
