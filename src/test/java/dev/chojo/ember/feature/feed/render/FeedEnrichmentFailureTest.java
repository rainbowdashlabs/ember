/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A lookup that fails while an entry is enriched adds nothing and never fails the entry: the rows the
 * notification carries itself are all that is shown.
 */
class FeedEnrichmentFailureTest {

    private static final String NO_DETAILS = "<dl";

    private NotificationFeedRenderer renderer;

    @BeforeEach
    void setup() {
        var notificationService = mock(NotificationText.class);
        when(notificationService.resolveLocalized(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        var failure = new IllegalStateException("the database went away");
        var crudService = mock(EventCrudService.class);
        when(crudService.findById(anyInt())).thenThrow(failure);
        var lostAndFound = mock(LostAndFoundService.class);
        when(lostAndFound.findById(anyInt())).thenThrow(failure);
        var lending = mock(LendingService.class);
        when(lending.findRequest(anyInt())).thenThrow(failure);
        var storage = mock(StorageQuotaService.class);
        when(storage.getUsage(anyInt())).thenThrow(failure);
        var inventory = mock(InventoryService.class);
        when(inventory.findById(anyInt())).thenThrow(failure);
        var board = mock(BoardTicketService.class);
        when(board.findById(anyInt())).thenThrow(failure);
        var procedure = mock(ProcedureService.class);
        when(procedure.findItems(anyInt())).thenThrow(failure);
        renderer = new NotificationFeedRenderer(
                notificationService,
                mock(StationRepository.class),
                FeedContributors.all(
                        crudService,
                        mock(EventFieldService.class),
                        mock(OccurrenceCalendar.class),
                        lostAndFound,
                        lending,
                        storage,
                        inventory,
                        board,
                        procedure));
    }

    private String rows(NotificationType type, NotificationParams params, Map<String, Object> routeParams) {
        var data = NotificationData.of(params, new NotificationData.NotificationLink("detail", routeParams));
        var notification = new Notification(1, 1, null, type, data, Instant.EPOCH, null);
        var ctx = new NotificationFeedRenderer.RenderContext("en", "https://ember.example.com", null, true, true, null);
        var html = renderer.render(notification, ctx).getContents().getFirst().getValue();
        int start = html.indexOf(NO_DETAILS);
        return start < 0 ? "" : html.substring(start, html.indexOf("</dl>") + 5);
    }

    private static int rowCount(String rows) {
        return rows.isEmpty() ? 0 : rows.split("<dt").length - 1;
    }

    @Test
    void anAppointmentThatCannotBeReadAddsNothing() {
        assertEquals(
                1,
                rowCount(rows(
                        NotificationType.EVENT_CANCELLED,
                        new NotificationParams.EventCancelled("Probe", null, null, null),
                        Map.of("id", 1))));
    }

    @Test
    void aFoundItemThatCannotBeReadAddsNothing() {
        assertEquals(
                1,
                rowCount(rows(
                        NotificationType.LOST_AND_FOUND_NEW,
                        new NotificationParams.LostAndFoundNew("Jacke"),
                        Map.of("id", 1))));
    }

    @Test
    void aLendingThatCannotBeReadAddsNothing() {
        assertEquals(
                1,
                rowCount(rows(
                        NotificationType.LENDING_NEW_MESSAGE,
                        new NotificationParams.LendingNewMessage("FF Süd", null),
                        Map.of("id", 1))));
    }

    @Test
    void storageThatCannotBeReadAddsNothing() {
        assertEquals(
                1,
                rowCount(rows(
                        NotificationType.STORAGE_WARNING,
                        new NotificationParams.StorageWarning(90, null, null),
                        Map.of("stationId", 1))));
    }

    @Test
    void anInventoryThatCannotBeReadAddsNothing() {
        assertEquals(
                1,
                rowCount(rows(
                        NotificationType.PROCUREMENT_REQUESTED,
                        new NotificationParams.ProcurementRequested("Schlauch"),
                        Map.of("id", 1))));
    }

    @Test
    void aTicketThatCannotBeReadAddsNothing() {
        assertEquals(
                1,
                rowCount(rows(
                        NotificationType.BOARD_TICKET_UPDATE,
                        new NotificationParams.BoardTicketUpdate("Vorstand", null, null),
                        Map.of("ticketId", 1))));
    }

    @Test
    void aProcedureThatCannotBeReadAddsNothing() {
        assertEquals(
                1,
                rowCount(rows(
                        NotificationType.PROCEDURE_RESOLVED,
                        new NotificationParams.ProcedureResolvedParams("Check"),
                        Map.of("id", 1))));
    }

    @Test
    void aLinkIdThatIsNoNumberNamesNothing() {
        assertEquals(
                1,
                rowCount(rows(
                        NotificationType.PROCEDURE_RESOLVED,
                        new NotificationParams.ProcedureResolvedParams("Check"),
                        Map.of("id", "not-a-number"))));
    }
}
