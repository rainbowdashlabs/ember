/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.LocalRouteServer;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.attendance.service.AttendanceTemplateGuards;
import dev.chojo.ember.feature.content.BlockReferenceTestBase;
import dev.chojo.ember.feature.equipment.service.EquipmentReleaseService;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventExportService;
import dev.chojo.ember.feature.events.service.EventFieldRegistrationService;
import dev.chojo.ember.feature.events.service.EventOccurrenceService;
import dev.chojo.ember.feature.events.service.EventReminderService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * An event block in a news or wiki article reads its appointment, its picker searches for one, and
 * an author announcing an appointment is handed its reference, over HTTP. Whoever asks, only what
 * every member may see is reached: an appointment kept to a group, a user type or a tag answers the
 * same 404 as one that does not exist, even to somebody who edits appointments and so may open it.
 */
class EventBlockRoutesTest extends BlockReferenceTestBase {
    private static LocalRouteServer server;

    @BeforeAll
    static void setupClass() {
        var crudService = new EventCrudService(
                eventRepo,
                new DomainEventBus(Set.of()),
                mock(EquipmentReleaseService.class),
                restrictionService,
                mock(AttendanceTemplateGuards.class));
        var visibility =
                new EventVisibility(crudService, mock(EventRestrictionService.class), mock(GuardianPolicy.class));
        var embedRoutes = new EventEmbedRoutes(crudService, new EventCategoryService(eventCategoryRepo), visibility);
        var eventRoutes = new EventRoutes(
                crudService,
                mock(EventOccurrenceService.class),
                mock(EventRestrictionService.class),
                mock(EventReminderService.class),
                mock(BatchEventService.class),
                mock(GuardianPolicy.class),
                mock(EventExportService.class),
                mock(EventTemplateService.class),
                mock(EventFieldRegistrationService.class),
                mock(OccurrenceCalendar.class),
                visibility);
        var editor = new UserSession(
                account,
                1,
                station.id(),
                station.uid(),
                member,
                Set.of(StationPermission.NEWS_EDIT, StationPermission.EVENT_EDIT),
                Set.of(),
                null);
        server = LocalRouteServer.serving(stationRepo, clusterRepo, editor, embedRoutes, eventRoutes);
    }

    @AfterAll
    static void cleanupClass() {
        server.close();
    }

    @Test
    void anArticleDrawsEveryAppointmentEveryMemberMaySee() throws Exception {
        for (var event : List.of(publicEvent, internalEvent)) {
            var answer = server.get("/events/embed/%s".formatted(uidOf(event)));
            assertEquals(200, answer.statusCode(), event.name());
            assertEquals(event.name(), LocalRouteServer.json(answer).get("name").asString());
        }
    }

    @Test
    void anArticleDrawsNothingElseEvenForAnEditor() throws Exception {
        for (var withheld : eventsNobodyMayName().entrySet()) {
            var answer = server.get("/events/embed/%s".formatted(uidOf(withheld.getValue())));
            assertEquals(404, answer.statusCode(), withheld.getKey());
            assertFalse(answer.body().contains(withheld.getValue().name()), withheld.getKey());
        }
        assertEquals(
                404, server.get("/events/embed/%s".formatted(UUID.randomUUID())).statusCode());
    }

    @Test
    void thePickerOffersAPageThePublicAppointmentOnly() throws Exception {
        assertEquals(List.of(publicEvent.name()), offered("PUBLIC"));
    }

    @Test
    void thePickerOffersAnArticleEveryAppointmentEveryMemberMaySeeWhoeverPicks() throws Exception {
        var offered = offered("MEMBERS");

        assertTrue(offered.containsAll(List.of(publicEvent.name(), internalEvent.name())), offered.toString());
        for (var withheld : eventsNobodyMayName().values()) {
            assertFalse(offered.contains(withheld.name()), withheld.name());
        }
    }

    @Test
    void anAnnouncementIsHandedAReferenceOnlyForAnAppointmentEveryMemberMaySee() throws Exception {
        var internal = server.get("/events/%d/embed-reference".formatted(internalEvent.id()));
        assertEquals(200, internal.statusCode(), internal.body());
        assertEquals(
                uidOf(internalEvent).toString(),
                LocalRouteServer.json(internal).get("eventUid").asString());

        for (var restricted : List.of(groupEvent, typeEvent, tagEvent)) {
            assertEquals(
                    404,
                    server.get("/events/%d/embed-reference".formatted(restricted.id()))
                            .statusCode(),
                    restricted.name());
        }
    }

    private static List<String> offered(String scope) throws Exception {
        var answer = server.get("/events/search?scope=%s&mode=ALL&limit=20&q=%s".formatted(scope, "bung"));
        assertEquals(200, answer.statusCode(), answer.body());
        var names = new ArrayList<String>();
        LocalRouteServer.json(answer)
                .forEach(found -> names.add(found.get("name").asString()));
        return names;
    }
}
