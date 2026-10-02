/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventAnswerRecorded;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Carries an answer to an appointment onto the attendance sheet already open for its date, so a
 * no given after the sheet was opened stands on it without anybody filling it in again.
 */
@Singleton
public class EventAnswerRecordedHandler implements DomainEventHandler<EventAnswerRecorded> {
    private final AttendanceService attendanceService;

    @Inject
    public EventAnswerRecordedHandler(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Override
    public Class<EventAnswerRecorded> eventType() {
        return EventAnswerRecorded.class;
    }

    @Override
    public void handle(EventAnswerRecorded event) {
        attendanceService.takeAnswer(event.eventId(), event.eventDate(), event.memberId());
    }
}
