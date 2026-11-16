/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The consistency decisions of sharing an appointment with partners: a part of an event group is shared with
 * its event group and never configured on its own.
 */
@Singleton
public class EventSharingGuards {
    private final EventCrudService crudService;

    @Inject
    public EventSharingGuards(EventCrudService crudService) {
        this.crudService = crudService;
    }

    /**
     * Refuses changing how an appointment is shared where it is a part of an event group, whose share it
     * follows.
     *
     * @param eventId the appointment
     */
    public void requireSharedOnItsOwn(int eventId) {
        if (crudService.isEventGroupPart(eventId)) throw EventRefusal.EVENT_GROUP_PART_SHARED_WITH_EVENT_GROUP.raise();
    }
}
