/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.StationAudienceChanged;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Somebody joined or left a group or tag, which can change the color of their name and the tag
 * beside it, so what is kept beside names is dropped.
 * It happens once the change has committed, so no read in between keeps the old look again. The
 * resolver is fetched when needed, because it reaches the event bus itself through the group service.
 */
@Singleton
public class AudienceDisplayHandler implements DomainEventHandler<StationAudienceChanged> {
    private final Provider<MemberNameResolver> names;

    @Inject
    public AudienceDisplayHandler(Provider<MemberNameResolver> names) {
        this.names = names;
    }

    @Override
    public Class<StationAudienceChanged> eventType() {
        return StationAudienceChanged.class;
    }

    @Override
    public void handle(StationAudienceChanged event) {
        Transactions.afterCommit(() -> names.get().forgetDisplays());
    }
}
