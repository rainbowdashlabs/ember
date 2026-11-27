/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.PartnerRegistrationStatusChanged;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemoteRegistrationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Notifies a partner station that this station decided about one of its members on a date of a shared
 * appointment, or that the member moved up from the waiting list. The partner notifies its member.
 *
 * <p>The instant sent is the one taken as the change was made. It runs once the change is kept: a partner on this instance takes the notice in at once, and one
 * elsewhere is sent it with the usual retries. A notice that does not arrive is caught up by the next
 * read of the appointment at the partner.
 */
@Singleton
public class PartnerRegistrationStatusHandler implements DomainEventHandler<PartnerRegistrationStatusChanged> {
    private final EventFederationRepository registrations;
    private final EventRepository events;
    private final FederationRepository partners;
    private final Provider<FederationTransport> transport;

    /**
     * @param transport read when a notice is sent, since the services it serves reach the event bus this
     *                  handler is registered on
     */
    @Inject
    public PartnerRegistrationStatusHandler(
            EventFederationRepository registrations,
            EventRepository events,
            FederationRepository partners,
            Provider<FederationTransport> transport) {
        this.registrations = registrations;
        this.events = events;
        this.partners = partners;
        this.transport = transport;
    }

    @Override
    public Class<PartnerRegistrationStatusChanged> eventType() {
        return PartnerRegistrationStatusChanged.class;
    }

    @Override
    public void handle(PartnerRegistrationStatusChanged changed) {
        var registration =
                registrations.findRegistrationById(changed.registrationId()).orElse(null);
        if (registration == null) return;
        var event = events.findById(registration.eventId()).orElse(null);
        var partner = partners.findPartnerById(registration.partnerId()).orElse(null);
        if (event == null || partner == null) return;
        transport
                .get()
                .notify(
                        partner,
                        RemoteEventRoutes.REGISTRATION_STATUS_WEBHOOK.at(),
                        new RemoteRegistrationStatus(
                                event.id(),
                                event.name(),
                                registration.remoteMemberId(),
                                registration.eventDate(),
                                changed.status(),
                                changed.queuePosition(),
                                changed.movedUp(),
                                changed.changedAt()));
    }
}
