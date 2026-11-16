/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Whom a reader may sign up, change or withdraw for appointments and event groups, here or at a
 * partner: themselves, somebody in their care, or any member of the station where they run its
 * appointments.
 */
@Singleton
public class EventSignUpGuards {
    private final GuardianPolicy guardianPolicy;

    /**
     * Creates the guards.
     *
     * @param guardianPolicy who looks after whom
     */
    @Inject
    public EventSignUpGuards(GuardianPolicy guardianPolicy) {
        this.guardianPolicy = guardianPolicy;
    }

    /**
     * The member a sign-up call acts for, refusing one the reader may not answer for.
     *
     * @param session   the reader
     * @param requested the member named in the call, or null for the reader
     * @return the member to act for
     */
    public int requireTarget(StationSession session, @Nullable Integer requested) {
        if (requested == null || requested == session.member().id())
            return session.member().id();
        if (!guardianPolicy.mayActFor(session.user(), requested)
                && !session.hasPermission(StationPermission.EVENT_MANAGER)) {
            throw EventRefusal.MEMBER_NOT_YOURS_TO_REGISTER.raise();
        }
        return requested;
    }
}
