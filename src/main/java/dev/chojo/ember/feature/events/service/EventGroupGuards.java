/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Who may open an event group of their station: whoever edits appointments, runs them or handles
 * their registrations sees every event group, and everybody else the event groups meant for them or
 * for somebody they look after.
 *
 * <p>An event group kept from the reader reads as absent, so its id says nothing about whether it
 * exists.
 */
@Singleton
public class EventGroupGuards {
    private final EventGroupService eventGroups;
    private final GuardianPolicy guardianPolicy;

    @Inject
    public EventGroupGuards(EventGroupService eventGroups, GuardianPolicy guardianPolicy) {
        this.eventGroups = eventGroups;
        this.guardianPolicy = guardianPolicy;
    }

    /**
     * Whether the reader may open an event group of their station.
     *
     * @param session      the reader
     * @param eventGroupId an event group of the reader's station
     * @return true where it is meant for them or somebody they look after, or they run events
     */
    public boolean mayOpen(StationSession session, int eventGroupId) {
        return seesEverything(session)
                || eventGroups.canSeeAny(
                        eventGroupId,
                        guardianPolicy.household(session.user()),
                        session.user().permissions());
    }

    /**
     * Loads an event group of the reader's station and asserts they may open it.
     *
     * @param session      the reader
     * @param eventGroupId the event group
     * @return the event group
     */
    public EventGroup requireOpen(StationSession session, int eventGroupId) {
        var eventGroup = eventGroups.requireEventGroup(session.stationId(), eventGroupId);
        if (!mayOpen(session, eventGroup.id())) throw EventRefusal.EVENT_GROUP_NOT_HERE.raise();
        return eventGroup;
    }

    /**
     * Refuses a sign-up for a member the event group is not meant for, unless whoever runs events
     * signs them up.
     *
     * @param session      the reader
     * @param eventGroupId the event group
     * @param memberId     the member signed up
     * @param override     whether whoever runs events signs somebody else up
     */
    public void requireMayJoin(StationSession session, int eventGroupId, int memberId, boolean override) {
        if (!override
                && !eventGroups.canSignUp(eventGroupId, memberId, session.user().permissions())) {
            throw EventRefusal.MEMBER_NOT_INVITED_TO_EVENT.raise();
        }
    }

    private static boolean seesEverything(StationSession session) {
        return session.hasPermission(StationPermission.EVENT_EDIT)
                || session.hasPermission(StationPermission.EVENT_MANAGER)
                || session.hasPermission(StationPermission.EVENT_REGISTRATION);
    }
}
