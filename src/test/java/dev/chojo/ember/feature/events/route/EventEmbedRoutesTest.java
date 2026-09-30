/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An event as a block shows it to a member of its own station, and the public id an author names
 * it by. Anything the reader may not see answers exactly like something that is not there.
 */
class EventEmbedRoutesTest {

    private static final int STATION_ID = 7;
    private static final int EVENT_ID = 42;
    private static final UUID EVENT_UID = UUID.fromString("7d7c1d5e-4e3a-4f9b-9d0e-2a4f6c1b8e11");

    private EventCrudService crudService;
    private EventCategoryService categoryService;
    private EventVisibility visibility;
    private RouteHarness harness;
    private StationEvent event;

    @BeforeEach
    void setup() {
        crudService = mock(EventCrudService.class);
        categoryService = mock(EventCategoryService.class);
        visibility = mock(EventVisibility.class);
        harness = RouteHarness.serving(new EventEmbedRoutes(crudService, categoryService, visibility));
        event = mock(StationEvent.class);
        when(event.id()).thenReturn(EVENT_ID);
        when(event.stationId()).thenReturn(STATION_ID);
        when(event.name()).thenReturn("Summer camp");
        when(event.description()).thenReturn("Tents and a lake");
        when(event.startTime()).thenReturn(Instant.parse("2027-07-01T08:00:00Z"));
        when(event.endTime()).thenReturn(Instant.parse("2027-07-05T16:00:00Z"));
        when(event.categoryId()).thenReturn(3);
    }

    private static UserSession session(StationPermission... permissions) {
        return new UserSession(
                new Account(1, null, "author@test.com", null, "Ada", "Author", true, null, "Ada Author", null, null),
                1,
                STATION_ID,
                null,
                null,
                Set.of(permissions),
                Set.of(),
                null);
    }

    private Response askForTheEvent(String uid) {
        return harness.request(client ->
                client.get(RouteHarness.PREFIX + "/events/embed/" + uid, harness.as(session(StationPermission.USER))));
    }

    private Response askForTheReference() {
        return harness.request(client -> client.get(
                RouteHarness.PREFIX + "/events/%d/embed-reference".formatted(EVENT_ID),
                harness.as(session(StationPermission.USER, StationPermission.NEWS_EDIT))));
    }

    @Test
    void aVisibleEventIsAnsweredWithItsCategory() {
        when(crudService.findByPublicUid(STATION_ID, EVENT_UID)).thenReturn(Optional.of(event));
        when(visibility.canSee(any(), eq(event))).thenReturn(true);
        when(categoryService.findById(3))
                .thenReturn(Optional.of(new EventCategory(3, STATION_ID, "Camps", 0, null, false, null)));

        var embedded = RouteHarness.read(askForTheEvent(EVENT_UID.toString()), EventEmbedRoutes.EmbeddedEvent.class);

        assertEquals(EVENT_ID, embedded.id());
        assertEquals("Summer camp", embedded.name());
        assertEquals("Camps", embedded.categoryName());
    }

    @Test
    void anEventWithoutCategoryIsAnsweredWithoutOne() {
        when(event.categoryId()).thenReturn(null);
        when(crudService.findByPublicUid(STATION_ID, EVENT_UID)).thenReturn(Optional.of(event));
        when(visibility.canSee(any(), eq(event))).thenReturn(true);

        var answer = RouteHarness.json(askForTheEvent(EVENT_UID.toString()));

        assertTrue(answer.path("categoryName").isMissingNode()
                || answer.path("categoryName").isNull());
    }

    @Test
    void aHiddenEventIsNotFound() {
        when(crudService.findByPublicUid(STATION_ID, EVENT_UID)).thenReturn(Optional.of(event));
        when(visibility.canSee(any(), eq(event))).thenReturn(false);

        assertEquals(
                Refusal.EVENT_BLOCK_APPOINTMENT_NOT_HERE, RouteHarness.refusalOf(askForTheEvent(EVENT_UID.toString())));
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertEquals(
                Refusal.EVENT_BLOCK_APPOINTMENT_NOT_HERE, RouteHarness.refusalOf(askForTheEvent(EVENT_UID.toString())));
    }

    @Test
    void aMalformedIdIsNotFound() {
        assertEquals(Refusal.ADDRESS_NOT_AN_IDENTIFIER, RouteHarness.refusalOf(askForTheEvent("not-a-uuid")));
    }

    @Test
    void anAuthorIsHandedThePublicIdOfAnEventTheySee() {
        when(visibility.requireVisibleEvent(any(), eq(EVENT_ID))).thenReturn(event);
        when(crudService.findPublicUidsByIds(STATION_ID, List.of(EVENT_ID))).thenReturn(Map.of(EVENT_ID, EVENT_UID));

        var reference = RouteHarness.read(askForTheReference(), EventEmbedRoutes.EmbedReference.class);

        assertEquals(EVENT_UID, reference.eventUid());
    }

    @Test
    void anEventWithoutPublicIdHasNoReference() {
        when(visibility.requireVisibleEvent(any(), eq(EVENT_ID))).thenReturn(event);
        when(crudService.findPublicUidsByIds(STATION_ID, List.of(EVENT_ID))).thenReturn(Map.of());

        assertEquals(Refusal.EVENT_BLOCK_REFERENCE_NOT_HERE, RouteHarness.refusalOf(askForTheReference()));
    }

    @Test
    void aReaderWithoutTheRightToWriteNewsOrPagesIsRefusedTheReference() {
        var answer = harness.request(client -> client.get(
                RouteHarness.PREFIX + "/events/%d/embed-reference".formatted(EVENT_ID),
                harness.as(session(StationPermission.USER))));

        assertEquals(403, answer.code(), answer.body().string());
    }
}
