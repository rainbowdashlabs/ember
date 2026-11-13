/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventGroupPartState;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.route.EventGroupRoutes.EventGroupSummary;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService.PartnerStanding;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What a station serves its partners about the event groups it shares with them: an event group with the
 * standing of the partner's members, and their sign-ups.
 *
 * <p>A partner that has never heard of these endpoints never calls them. Its members still see the
 * parts as shared appointments, and are told there that they sign up through the event group.
 */
@Singleton
public class RemoteEventGroupRoutes implements Routes {

    /** A shared event group with the standing of the members the partner asks about. */
    public static final FederationEndpoint GET_EVENT_GROUP = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/events/groups/{id}",
            RemoteEventGroupQuery.class,
            RemoteEventGroupDetail.class);

    /** One of the partner's members signing up for a shared event group. */
    public static final FederationEndpoint SIGN_UP = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/events/groups/{id}/sign-up",
            RemoteEventGroupSignUpRequest.class,
            PartnerStanding.class);

    /** One of the partner's members changing the parts they chose. */
    public static final FederationEndpoint CHANGE_PARTS = FederationEndpoint.put(
            FederationSurface.EVENT_SHARE,
            "/remote/events/groups/{id}/sign-up",
            RemoteEventGroupSignUpRequest.class,
            PartnerStanding.class);

    /** One of the partner's members withdrawing from a shared event group. */
    public static final FederationEndpoint WITHDRAW = FederationEndpoint.delete(
            FederationSurface.EVENT_SHARE,
            "/remote/events/groups/{id}/sign-up",
            RemoteEventGroupMember.class,
            Void.class);

    /**
     * The station holding an event group telling a partner that the event group was called off, naming the
     * partner's members who held or waited for a place.
     */
    public static final FederationEndpoint EVENT_GROUP_CANCELLED_WEBHOOK = FederationEndpoint.post(
                    FederationSurface.EVENT_SHARE,
                    "/remote/webhook/event-group-cancelled",
                    RemoteEventGroupCancelled.class,
                    Void.class)
            .keepingInStep();

    public static final List<FederationEndpoint> CONTRACT =
            List.of(GET_EVENT_GROUP, SIGN_UP, CHANGE_PARTS, WITHDRAW, EVENT_GROUP_CANCELLED_WEBHOOK);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteEventGroupRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(GET_EVENT_GROUP)
                .serveCreated(SIGN_UP)
                .serve(CHANGE_PARTS)
                .serve(WITHDRAW)
                .serve(EVENT_GROUP_CANCELLED_WEBHOOK));
    }

    /** @param memberUids the partner's members whose standing is asked about */
    public record RemoteEventGroupQuery(List<UUID> memberUids) {}

    /**
     * A shared event group as a partner is shown it.
     *
     * @param eventGroup   the event group
     * @param sets      its sets in order
     * @param parts     its parts in date order
     * @param standings where each member asked about stands
     * @param fields    the fields it shows readers outside the station; a partner that has never
     *                  heard of them ignores them
     */
    public record RemoteEventGroupDetail(
            EventGroupSummary eventGroup,
            List<EventGroupSet> sets,
            List<EventGroupPartState> parts,
            List<PartnerStanding> standings,
            @Nullable List<AppointmentField> fields) {}

    /**
     * A partner's member signing up, or changing the parts they chose.
     *
     * @param memberUid the member, by the id their own station gave them
     * @param parts     the chosen parts
     */
    public record RemoteEventGroupSignUpRequest(UUID memberUid, List<Integer> parts) {}

    /** @param memberUid the member, by the id their own station gave them */
    public record RemoteEventGroupMember(UUID memberUid) {}

    /**
     * A shared event group that was called off.
     *
     * @param eventGroupId   the event group at the station holding it
     * @param eventGroupName its name
     * @param reason     why, as told to the people signed up, or null
     * @param memberUids the partner's members who held or waited for a place
     */
    public record RemoteEventGroupCancelled(
            int eventGroupId,
            String eventGroupName,
            @Nullable String reason,
            List<UUID> memberUids) {}
}
