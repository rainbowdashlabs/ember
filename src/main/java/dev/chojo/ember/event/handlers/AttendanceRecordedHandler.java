/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.AttendanceRecorded;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Counts an evening somebody turned up to towards their trial period.
 *
 * <p>The waiting list shows a count against a threshold, and until this existed nothing ever raised
 * it, so the measure of a trial period stood at zero however often somebody came.
 *
 * <p>The service is asked for rather than held: it publishes events of its own, so it needs the bus,
 * and the bus needs every handler. Looking it up when the event arrives is what keeps that from
 * being a circle nothing can be built out of.
 */
@Singleton
public class AttendanceRecordedHandler implements DomainEventHandler<AttendanceRecorded> {
    private final Provider<WaitingListService> waitingListService;

    @Inject
    public AttendanceRecordedHandler(Provider<WaitingListService> waitingListService) {
        this.waitingListService = waitingListService;
    }

    @Override
    public Class<AttendanceRecorded> eventType() {
        return AttendanceRecorded.class;
    }

    @Override
    public void handle(AttendanceRecorded event) {
        waitingListService.get().recordTrialAttendance(event.memberId());
    }
}
