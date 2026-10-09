/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventAnswerRecorded;
import dev.chojo.ember.feature.signing.service.AppointmentSignatures;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Carries an answer to an appointment over to the signatures its documents to bring ask for: a place taken
 * asks for them, a place given up withdraws what is still open. Runs in the thread that wrote the answer, so
 * the signatures are asked for by the time the registration is answered.
 *
 * <p>The signatures are reached through a provider: building them needs the template services, which need
 * the event bus, which in turn needs this handler.
 */
@Singleton
public class RegistrationSignaturesHandler implements DomainEventHandler<EventAnswerRecorded> {
    private final Provider<AppointmentSignatures> signatures;

    @Inject
    public RegistrationSignaturesHandler(Provider<AppointmentSignatures> signatures) {
        this.signatures = signatures;
    }

    @Override
    public Class<EventAnswerRecorded> eventType() {
        return EventAnswerRecorded.class;
    }

    @Override
    public void handle(EventAnswerRecorded event) {
        switch (event.status()) {
            case PENDING, ACCEPTED ->
                signatures.get().askOnRegistration(event.eventId(), event.eventDate(), event.memberId());
            case DENIED, DECLINED, WITHDRAWN ->
                signatures.get().withdrawOnLeaving(event.eventId(), event.eventDate(), event.memberId());
        }
    }
}
