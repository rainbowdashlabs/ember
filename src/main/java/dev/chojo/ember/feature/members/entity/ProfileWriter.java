/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * Who saves profile answers, which decides which locks they pass.
 *
 * <p>A question can be locked twice. Its own lock says only the member management writes the answer,
 * and the station's member management passes it the way the association's does. An association's
 * question can also be kept from the station altogether, a lock only the association passes.
 *
 * @param management  whether they write as the member management, which passes a question's own lock
 * @param owningAssociation whether they are the association, which passes the lock it keeps against the station
 */
public record ProfileWriter(boolean management, boolean owningAssociation) {

    /**
     * Somebody at the member's station.
     *
     * @param management whether they write as the station's member management
     * @return the writer
     */
    public static ProfileWriter station(boolean management) {
        return new ProfileWriter(management, false);
    }

    /**
     * The association the member's station belongs to, through somebody who manages its members.
     *
     * @return the writer
     */
    public static ProfileWriter association() {
        return new ProfileWriter(true, true);
    }
}
