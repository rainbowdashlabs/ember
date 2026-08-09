/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import io.javalin.http.NotFoundResponse;

/**
 * The station-ownership guard shared by the event route classes.
 */
final class EventOwnership {

    private EventOwnership() {}

    /**
     * Loads an event and asserts it belongs to the caller's station. Answers 404 both when
     * the event is absent and when it is owned by another station, so an event id from one
     * station cannot be used to probe or act on another station's event.
     */
    static StationEvent requireOwnedEvent(EventCrudService crudService, int eventId, UserSession session) {
        var event = crudService.findById(eventId).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, event.stationId());
        return event;
    }
}
