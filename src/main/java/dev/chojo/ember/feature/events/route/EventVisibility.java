/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.feature.events.route.EventOwnership.requireOwnedEvent;

/**
 * The guard in front of everything that reads an event.
 *
 * <p>Owning the event is not enough to read it: an event can be hidden from most of its own
 * station, and its id is a counter anybody can step through. Whoever may write events is let
 * through whatever the event says about who may see it, since an editor still has to open an
 * appointment they restricted to one group. A guardian sees what the members they look after see,
 * the same as in the event list.
 */
@Singleton
public class EventVisibility {

    private final EventCrudService crudService;
    private final EventRestrictionService restrictionService;
    private final StationMemberService stationMemberService;

    @Inject
    public EventVisibility(
            EventCrudService crudService,
            EventRestrictionService restrictionService,
            StationMemberService stationMemberService) {
        this.crudService = crudService;
        this.restrictionService = restrictionService;
        this.stationMemberService = stationMemberService;
    }

    /**
     * Loads an event of the caller's station and asserts they may see it.
     *
     * @param session the reader
     * @param eventId the event being read
     * @return the event
     */
    public StationEvent requireVisibleEvent(UserSession session, int eventId) {
        var event = requireOwnedEvent(crudService, eventId, session);
        if (!canSee(session, event)) throw Refusal.EVENT_NOT_YOURS_TO_SEE.raise();
        return event;
    }

    /**
     * Whether the reader may see an event their station owns.
     *
     * @param session the reader
     * @param event   an event of the reader's station
     */
    public boolean canSee(UserSession session, StationEvent event) {
        if (session.permissions().contains(StationPermission.EVENT_EDIT)) return true;
        var spokenFor = stationMemberService.findSpokenForIds(session);
        return restrictionService.canViewAny(event.id(), spokenFor, session.permissions());
    }
}
