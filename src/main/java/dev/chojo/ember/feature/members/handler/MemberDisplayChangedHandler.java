/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MemberDisplayChanged;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * A group or tag of the station changed how it looks, so the color and tag kept beside names are
 * dropped.
 * It happens once the change has committed, so no read in between keeps the old look again. The
 * resolver is fetched when needed, because it reaches the event bus itself through the group service.
 */
@Singleton
public class MemberDisplayChangedHandler implements DomainEventHandler<MemberDisplayChanged> {
    private final Provider<MemberNameResolver> names;

    @Inject
    public MemberDisplayChangedHandler(Provider<MemberNameResolver> names) {
        this.names = names;
    }

    @Override
    public Class<MemberDisplayChanged> eventType() {
        return MemberDisplayChanged.class;
    }

    @Override
    public void handle(MemberDisplayChanged event) {
        Transactions.afterCommit(() -> names.get().forgetDisplays());
    }
}
