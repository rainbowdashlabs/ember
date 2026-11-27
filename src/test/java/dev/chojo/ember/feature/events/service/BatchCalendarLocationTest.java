/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.BatchFieldEntry;
import dev.chojo.ember.feature.events.entity.BatchRequest;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Appointments created together whose fields name two calendar locations are not created at all. */
class BatchCalendarLocationTest {
    @Test
    void twoCalendarLocationsCreateNothing() {
        var crud = mock(EventCrudService.class);
        var service = new BatchEventService(
                crud,
                mock(EventRestrictionService.class),
                mock(EventFieldService.class),
                mock(EventBreakRepository.class),
                mock(StationRepository.class),
                mock(DomainEventBus.class),
                mock(EventTemplateService.class),
                mock(EventReminderService.class),
                mock(EventFieldDefaultService.class));
        var meetingPoint = EventQuestionSettings.parse("{\"calendarLocation\":true}");
        var request = new BatchRequest(
                "Adventszauber",
                null,
                null,
                null,
                List.of(
                        new BatchFieldEntry("Ort", FieldType.LOCATION, meetingPoint, true, null),
                        new BatchFieldEntry("Treffpunkt", FieldType.LOCATION, meetingPoint, true, null)),
                List.of(),
                false,
                false,
                null,
                RestrictionSelection.empty(),
                RestrictionSelection.empty());

        var refused = assertThrows(RefusalResponse.class, () -> service.createBatch(1, request));

        assertEquals(EventRefusal.BATCH_TWO_CALENDAR_LOCATIONS, refused.refusal());
        verifyNoInteractions(crud);
    }
}
