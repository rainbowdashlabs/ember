/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.api.auth.StationPermission;

import java.util.Set;

/**
 * Who saves profile answers, which decides which locks they pass.
 *
 * <p>A question can be locked twice. Its own lock means only that the member management writes the answer,
 * and the station's member management passes it the way the association's does. An association's
 * question can also be kept from the station altogether, a lock only the association passes.
 *
 * <p>Passing a question's own lock lets the management change answers a group's rule reads, which moves people in
 * or out of that group. Where the group grants permissions, the writer has to hold them, the same as for moving
 * people by hand, so the station's management brings the permissions it holds.
 *
 * @param management        whether they write as the member management, which passes a question's own lock
 * @param owningAssociation whether they are the association, which passes the lock it keeps against the station
 * @param held              the station permissions of the writer, expanded, empty for the association
 */
public record ProfileWriter(boolean management, boolean owningAssociation, Set<StationPermission> held) {

    /**
     * Somebody at the member's station.
     *
     * @param management whether they write as the station's member management
     * @return the writer
     */
    public static ProfileWriter station(boolean management) {
        return new ProfileWriter(management, false, Set.of());
    }

    /**
     * Somebody at the member's station, holding these permissions.
     *
     * @param management whether they write as the station's member management
     * @param held       their station permissions, expanded
     * @return the writer
     */
    public static ProfileWriter station(boolean management, Set<StationPermission> held) {
        return new ProfileWriter(management, false, Set.copyOf(held));
    }

    /**
     * The association the member's station belongs to, through somebody who manages its members.
     *
     * @return the writer
     */
    public static ProfileWriter association() {
        return new ProfileWriter(true, true, Set.of());
    }
}
