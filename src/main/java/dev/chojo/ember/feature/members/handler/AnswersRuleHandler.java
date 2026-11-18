/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MemberAnswersChanged;
import dev.chojo.ember.feature.members.service.MemberRuleService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/** A member's profile answers changed, so the rules of their station run again once the change has landed. */
@Singleton
public class AnswersRuleHandler implements DomainEventHandler<MemberAnswersChanged> {
    private final Provider<MemberRuleService> rules;

    @Inject
    public AnswersRuleHandler(Provider<MemberRuleService> rules) {
        this.rules = rules;
    }

    @Override
    public Class<MemberAnswersChanged> eventType() {
        return MemberAnswersChanged.class;
    }

    @Override
    public void handle(MemberAnswersChanged event) {
        Transactions.afterCommit(() -> rules.get().stationChanged(event.stationId()));
    }
}
