/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventGroupSummary;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupDetail;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupMember;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupQuery;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupSignUpRequest;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupSummary;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemotePlaces;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService.PartnerStanding;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Serves partners the event groups this station shares with them. Every request is held to the event groups
 * shared with the partner it arrived on, so a partner cannot reach another event group by its id. The one
 * exception is a member withdrawing who still holds a place: they can give it back after the share
 * ended.
 */
@Singleton
public class EventGroupPartnerServer implements FederationServer {
    private final EventGroupPartnerService partners;
    private final EventGroupService eventGroups;
    private final EventGroupRegistrationService signUps;

    @Inject
    public EventGroupPartnerServer(
            EventGroupPartnerService partners, EventGroupService eventGroups, EventGroupRegistrationService signUps) {
        this.partners = partners;
        this.eventGroups = eventGroups;
        this.signUps = signUps;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.<RemoteEventGroupQuery, RemoteEventGroupDetail>serve(
                RemoteEventGroupRoutes.GET_EVENT_GROUP,
                (partner, params, body) -> serveEventGroup(partner, params.integer("id"), body));
        endpoints.<RemoteEventGroupSignUpRequest, PartnerStanding>serve(
                RemoteEventGroupRoutes.SIGN_UP, (partner, params, body) -> {
                    int eventGroupId = partners.requireSharedWith(partner, params.integer("id"));
                    return partners.register(
                            partner.servingStationId(),
                            eventGroupId,
                            partner.partnerId(),
                            body.memberUid(),
                            partsOf(body));
                });
        endpoints.<RemoteEventGroupSignUpRequest, PartnerStanding>serve(
                RemoteEventGroupRoutes.CHANGE_PARTS, (partner, params, body) -> {
                    int eventGroupId = partners.requireSharedWith(partner, params.integer("id"));
                    return partners.changeParts(
                            partner.servingStationId(),
                            eventGroupId,
                            partner.partnerId(),
                            body.memberUid(),
                            partsOf(body));
                });
        endpoints.<RemoteEventGroupMember, PartnerStanding>serve(
                RemoteEventGroupRoutes.CONFIRM_OWN, (partner, params, body) -> {
                    int eventGroupId = partners.requireSharedWith(partner, params.integer("id"));
                    return partners.confirmOwn(
                            partner.servingStationId(), eventGroupId, partner.partnerId(), body.memberUid());
                });
        endpoints.<RemoteEventGroupMember, Void>serve(RemoteEventGroupRoutes.WITHDRAW, (partner, params, body) -> {
            int eventGroupId = params.integer("id");
            if (!partners.holdsPlace(eventGroupId, partner.partnerId(), body.memberUid()))
                partners.requireSharedWith(partner, eventGroupId);
            partners.withdraw(partner.servingStationId(), eventGroupId, partner.partnerId(), body.memberUid());
            return null;
        });
    }

    private RemoteEventGroupDetail serveEventGroup(
            ServingPartner partner, int eventGroupId, RemoteEventGroupQuery query) {
        partners.requireSharedWith(partner, eventGroupId);
        var eventGroup = eventGroups.requireEventGroup(partner.servingStationId(), eventGroupId);
        var asked = Objects.requireNonNullElse(query.memberUids(), List.<UUID>of());
        return new RemoteEventGroupDetail(
                RemoteEventGroupSummary.of(EventGroupSummary.of(eventGroup, eventGroups.tally(eventGroupId))),
                eventGroups.findSets(eventGroupId),
                signUps.partStates(eventGroupId),
                partners.standings(eventGroupId, partner.partnerId(), asked),
                eventGroups.findFields(eventGroupId).stream()
                        .filter(AppointmentField::isPublic)
                        .toList(),
                placesFor(eventGroupId, partner));
    }

    /** The places the partner asking was given, told only to a partner that decides its own sign-ups. */
    private @Nullable RemotePlaces placesFor(int eventGroupId, ServingPartner partner) {
        var arranged = partners.arrangementWith(eventGroupId, partner.partnerId());
        return arranged.decidedByHost() ? null : new RemotePlaces(arranged.slotBudget(), true);
    }

    private static Set<Integer> partsOf(RemoteEventGroupSignUpRequest request) {
        return Set.copyOf(Objects.requireNonNullElse(request.parts(), List.of()));
    }
}
