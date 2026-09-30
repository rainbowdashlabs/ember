/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.service.GuardianPolicy;

import java.util.List;

/** Whose view restrictions narrow the appointments a reader is shown, shared by the event listings. */
final class EventVisibility {

    private EventVisibility() {}

    /**
     * The members whose view the listings are narrowed to: nobody for somebody who runs the
     * appointments, who sees them all, and otherwise the reader and everybody they look after.
     *
     * @param session        the reader
     * @param guardianPolicy who the reader looks after
     * @return those members, null where nothing narrows the listing, and a member nobody is where the
     *     reader is no member of the station at all
     */
    static List<Integer> memberIdsSeenBy(UserSession session, GuardianPolicy guardianPolicy) {
        if (session.hasPermission(StationPermission.EVENT_MANAGER)) {
            return null;
        }
        if (session.member() == null) {
            return List.of(-1);
        }
        return guardianPolicy.household(session);
    }
}
