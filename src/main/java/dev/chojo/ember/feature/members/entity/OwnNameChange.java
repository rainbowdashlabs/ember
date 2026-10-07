/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.time.Instant;

/**
 * The name a member asked for and that still waits, as they see it on their own profile.
 *
 * @param firstName   the first name asked for
 * @param lastName    the last name asked for
 * @param requestedAt when it was asked for
 */
public record OwnNameChange(String firstName, String lastName, Instant requestedAt) {

    /**
     * The open request as its member sees it.
     *
     * @param request the open request
     * @return what the member is shown
     */
    public static OwnNameChange of(NameChangeRequest request) {
        return new OwnNameChange(request.firstName(), request.lastName(), request.requestedAt());
    }
}
