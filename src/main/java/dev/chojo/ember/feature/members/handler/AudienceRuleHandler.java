/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.StationAudienceChanged;
import dev.chojo.ember.feature.members.service.MemberRuleService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/** Who belongs to a station or its groups and tags changed, so its rules run again once the change has landed. */
@Singleton
public class AudienceRuleHandler implements DomainEventHandler<StationAudienceChanged> {
    private final Provider<MemberRuleService> rules;

    @Inject
    public AudienceRuleHandler(Provider<MemberRuleService> rules) {
        this.rules = rules;
    }

    @Override
    public Class<StationAudienceChanged> eventType() {
        return StationAudienceChanged.class;
    }

    @Override
    public void handle(StationAudienceChanged event) {
        if (event.stationId() > 0) Transactions.afterCommit(() -> rules.get().stationChanged(event.stationId()));
    }
}
