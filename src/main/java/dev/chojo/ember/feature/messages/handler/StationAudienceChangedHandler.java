/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.StationAudienceChanged;
import dev.chojo.ember.feature.messages.service.StationChatMembership;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Plans a reconciliation of the station chats a change may concern, once the transaction the change
 * was made in has committed. The stations of an account are looked up at that point too, so they
 * include what the transaction wrote. The reconciliation itself runs a moment later.
 */
@Singleton
public class StationAudienceChangedHandler implements DomainEventHandler<StationAudienceChanged> {
    private final StationChatMembership membership;

    @Inject
    public StationAudienceChangedHandler(StationChatMembership membership) {
        this.membership = membership;
    }

    @Override
    public Class<StationAudienceChanged> eventType() {
        return StationAudienceChanged.class;
    }

    @Override
    public void handle(StationAudienceChanged event) {
        Transactions.afterCommit(() -> {
            Integer accountId = event.accountId();
            if (accountId != null) membership.soonForAccount(accountId);
            if (event.stationId() != 0) membership.soon(event.stationId());
        });
    }
}
