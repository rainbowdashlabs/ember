/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.equipment.entity.EquipmentChoices;
import dev.chojo.ember.feature.equipment.entity.EquipmentChoices.ArtChoice;
import dev.chojo.ember.feature.equipment.entity.EquipmentChoices.InventoryChoice;
import dev.chojo.ember.feature.equipment.entity.EquipmentChoices.ItemChoice;
import dev.chojo.ember.feature.equipment.service.EquipmentChoiceService;
import dev.chojo.ember.feature.equipment.service.EquipmentNeedService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a line of an appointment can ask for, over HTTP: whoever may edit the appointment reads it
 * without the right to read the inventory, and only for an appointment of their own station.
 */
class EquipmentNeedRoutesTest {
    private static final EquipmentChoices CHOICES = new EquipmentChoices(
            List.of(new InventoryChoice(1, "Handfunkgeräte", InventoryType.INTERNAL, false, null, null)),
            List.of(new ArtChoice(7, 1, "Funkgerät blau", null, null)),
            List.of(new ItemChoice(
                    21, 1, 7, "Funkgerät 01", "FG-01", null, ItemOwner.STATION, ItemCustody.WITH_OWNER)));

    private EquipmentChoiceService choices;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        choices = mock(EquipmentChoiceService.class);
        var events = mock(EventCrudService.class);
        var visibility = mock(EventVisibility.class);
        when(events.findById(5)).thenReturn(Optional.of(event(5, 3)));
        when(events.findById(6)).thenReturn(Optional.of(event(6, 4)));
        when(visibility.canSee(any(), any())).thenReturn(true);
        when(choices.choicesFor(3)).thenReturn(CHOICES);
        harness = RouteHarness.serving(
                new EquipmentNeedRoutes(mock(EquipmentNeedService.class), choices, events, visibility));
    }

    private static StationEvent event(int id, int stationId) {
        return new StationEvent(
                id,
                stationId,
                "Berlin-Marathon",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                true,
                null,
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
                null,
                null);
    }

    @Test
    void anEditorWithoutInventoryRightsReadsTheChoices() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.EVENT_EDIT));
            var answer = client.get(PREFIX + "/events/5/equipment/choices", editor);
            assertEquals(200, answer.code());
            var body = json(answer);
            assertEquals(
                    "Handfunkgeräte",
                    body.path("inventories").path(0).path("name").asString());
            assertEquals(
                    "Funkgerät blau", body.path("arts").path(0).path("name").asString());
            var item = body.path("items").path(0);
            assertEquals("FG-01", item.path("internalId").asString());
            assertFalse(item.has("metadata"), "nothing beyond what a picker shows");
            assertFalse(item.has("assignedTo"), "nothing about who holds a piece");
        });
    }

    @Test
    void theChoicesNeedTheRightToEditTheAppointment() {
        harness.run((server, client) -> {
            var reader = harness.as(
                    TestSessions.member(3, StationPermission.EVENT_INTERNAL, StationPermission.INVENTORY_READ));
            assertEquals(
                    403,
                    client.get(PREFIX + "/events/5/equipment/choices", reader).code());
        });

        verify(choices, never()).choicesFor(anyInt());
    }

    @Test
    void anotherStationsAppointmentOffersNoChoices() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.EVENT_EDIT));
            assertEquals(
                    GeneralRefusal.NOT_YOURS_TO_OPEN,
                    refusalOf(client.get(PREFIX + "/events/6/equipment/choices", editor)));
        });

        verify(choices, never()).choicesFor(anyInt());
    }
}
