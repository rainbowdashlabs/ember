/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.events.entity.StationEvent;

/**
 * The station-ownership guard shared by the event route classes and by everything else that hangs
 * off an event, such as its comments.
 */
public final class EventOwnership {

    private EventOwnership() {}

    /**
     * Loads an event and asserts it belongs to the caller's station. Answers 404 both when
     * the event is absent and when it is owned by another station, so an event id from one
     * station cannot be used to probe or act on another station's event.
     */
    public static StationEvent requireOwnedEvent(EventCrudService crudService, int eventId, StationSession session) {
        var event = crudService.findById(eventId).orElseThrow(Refusal.EVENT_NOT_HERE::raise);
        RouteSupport.requireSameStation(session.user(), event.stationId());
        return event;
    }
}
