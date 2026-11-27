/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventGroupPartnerSignUpStatusChanged;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupSignUpStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Notifies a partner station that this station decided about one of its members' event group sign-ups, or
 * that the sign-up moved up from the waiting list. The partner notifies its member.
 *
 * <p>The instant sent is the one taken as the change was made. It runs once the change is kept: a partner on this instance takes the notice in at once, and one
 * elsewhere is sent it with the usual retries. A notice that does not arrive is caught up by the next
 * read of the event group at the partner.
 */
@Singleton
public class EventGroupPartnerSignUpStatusHandler implements DomainEventHandler<EventGroupPartnerSignUpStatusChanged> {
    private final EventGroupPartnerRepository signUps;
    private final EventGroupRepository eventGroups;
    private final FederationRepository partners;
    private final Provider<FederationTransport> transport;

    /**
     * @param transport read when a notice is sent, since the services it serves reach the event bus this
     *                  handler is registered on
     */
    @Inject
    public EventGroupPartnerSignUpStatusHandler(
            EventGroupPartnerRepository signUps,
            EventGroupRepository eventGroups,
            FederationRepository partners,
            Provider<FederationTransport> transport) {
        this.signUps = signUps;
        this.eventGroups = eventGroups;
        this.partners = partners;
        this.transport = transport;
    }

    @Override
    public Class<EventGroupPartnerSignUpStatusChanged> eventType() {
        return EventGroupPartnerSignUpStatusChanged.class;
    }

    @Override
    public void handle(EventGroupPartnerSignUpStatusChanged changed) {
        var signUp = signUps.findById(changed.signUpId()).orElse(null);
        if (signUp == null) return;
        var eventGroup = eventGroups.findById(signUp.eventGroupId()).orElse(null);
        var partner = partners.findPartnerById(signUp.partnerId()).orElse(null);
        if (eventGroup == null || partner == null) return;
        transport
                .get()
                .notify(
                        partner,
                        RemoteEventGroupRoutes.EVENT_GROUP_SIGN_UP_STATUS_WEBHOOK.at(),
                        new RemoteEventGroupSignUpStatus(
                                eventGroup.id(),
                                eventGroup.details().name(),
                                signUp.remoteMemberId(),
                                changed.status(),
                                changed.queuePosition(),
                                changed.movedUp(),
                                changed.changedAt()));
    }
}
