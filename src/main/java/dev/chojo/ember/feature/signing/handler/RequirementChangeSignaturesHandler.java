/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventRequirementsChanged;
import dev.chojo.ember.feature.signing.service.ChangedRequirementSignatures;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Carries a change of the documents an appointment asks for over to the signatures of the participants already
 * registered: what was taken off withdraws what is still open, what was added is asked for. Runs in the thread
 * that saved the change.
 *
 * <p>The signatures are reached through a provider: building them needs the template services, which need the
 * event bus, which in turn needs this handler.
 */
@Singleton
public class RequirementChangeSignaturesHandler implements DomainEventHandler<EventRequirementsChanged> {
    private final Provider<ChangedRequirementSignatures> signatures;

    @Inject
    public RequirementChangeSignaturesHandler(Provider<ChangedRequirementSignatures> signatures) {
        this.signatures = signatures;
    }

    @Override
    public Class<EventRequirementsChanged> eventType() {
        return EventRequirementsChanged.class;
    }

    @Override
    public void handle(EventRequirementsChanged event) {
        signatures.get().removed(event.stationId(), event.eventId(), event.removed());
        signatures.get().added(event.stationId(), event.eventId(), event.added());
    }
}
