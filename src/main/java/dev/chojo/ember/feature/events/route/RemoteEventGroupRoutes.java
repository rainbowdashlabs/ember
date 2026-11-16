/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupPartState;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSummary;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemoteCommentDeleteRequest;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemoteCommentUpdateRequest;
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
 * standing of the partner's members, their sign-ups, and the comments on it.
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

    /** The comments on a shared event group. */
    public static final FederationEndpoint LIST_COMMENTS = FederationEndpoint.getList(
            FederationSurface.EVENT_SHARE, "/remote/events/groups/{id}/comments", CommentResponse.class);

    /** One of the partner's members commenting on a shared event group. */
    public static final FederationEndpoint CREATE_COMMENT = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/events/groups/{id}/comments",
            RemoteEventRoutes.RemoteCommentRequest.class,
            CommentResponse.class);

    /** One of the partner's members changing their comment on a shared event group. */
    public static final FederationEndpoint UPDATE_COMMENT = FederationEndpoint.put(
            FederationSurface.EVENT_SHARE,
            "/remote/events/groups/{id}/comments/{commentId}",
            RemoteCommentUpdateRequest.class,
            CommentResponse.class);

    /** One of the partner's members removing their comment on a shared event group. */
    public static final FederationEndpoint DELETE_COMMENT = FederationEndpoint.delete(
            FederationSurface.EVENT_SHARE,
            "/remote/events/groups/{id}/comments/{commentId}",
            RemoteCommentDeleteRequest.class,
            Void.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(
            GET_EVENT_GROUP,
            SIGN_UP,
            CHANGE_PARTS,
            WITHDRAW,
            EVENT_GROUP_CANCELLED_WEBHOOK,
            LIST_COMMENTS,
            CREATE_COMMENT,
            UPDATE_COMMENT,
            DELETE_COMMENT);

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
                .serve(EVENT_GROUP_CANCELLED_WEBHOOK)
                .serve(LIST_COMMENTS)
                .serveCreated(CREATE_COMMENT)
                .serve(UPDATE_COMMENT)
                .serve(DELETE_COMMENT));
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
            RemoteEventGroupSummary eventGroup,
            List<EventGroupSet> sets,
            List<EventGroupPartState> parts,
            List<PartnerStanding> standings,
            @Nullable List<AppointmentField> fields) {}

    /**
     * A shared event group as its partner is told about it.
     *
     * @param id           the event group at the station holding it
     * @param details      what a manager set on it: name, description, the sign-up, its colour and minimum
     * @param cancelled    whether it was called off
     * @param cancelReason why, where it was
     * @param partCount    how many parts it has
     * @param holding      how many sign-ups hold or wait for a place, of every station
     */
    public record RemoteEventGroupSummary(
            int id,
            EventGroupDetails details,
            boolean cancelled,
            @Nullable String cancelReason,
            int partCount,
            int holding) {
        /**
         * The event group as the station holding it tells a partner about it.
         *
         * @param summary the event group as its own lists show it
         * @return what the partner is told
         */
        public static RemoteEventGroupSummary of(EventGroupSummary summary) {
            return new RemoteEventGroupSummary(
                    summary.id(),
                    summary.details(),
                    summary.cancelled(),
                    summary.cancelReason(),
                    summary.partCount(),
                    summary.holding());
        }

        /**
         * The event group as the partner's own pages show it.
         *
         * @return the summary
         */
        public EventGroupSummary toSummary() {
            return new EventGroupSummary(id, details, cancelled, cancelReason, partCount, holding);
        }
    }

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
