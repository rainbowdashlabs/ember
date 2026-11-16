/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.events.service.EventGroupPartDeparture;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Hands the documents an event group asks for over to another part when a part leaves the event group
 * ({@link AppointmentSignatures#partLeaving}).
 *
 * <p>The signatures are reached through a provider: building them needs the appointment services, which
 * need this.
 */
@Singleton
public class EventGroupDocumentHandover implements EventGroupPartDeparture {
    private final Provider<AppointmentSignatures> signatures;

    @Inject
    public EventGroupDocumentHandover(Provider<AppointmentSignatures> signatures) {
        this.signatures = signatures;
    }

    @Override
    public Runnable leaving(int eventGroupId, int eventId) {
        return signatures.get().partLeaving(eventGroupId, eventId);
    }
}
