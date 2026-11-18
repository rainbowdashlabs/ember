/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupAwaiting;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupDetail;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.EventGroupPartSummary;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventGroupSignUp;
import dev.chojo.ember.feature.events.entity.EventGroupSummary;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.WhenFull;
import dev.chojo.ember.feature.events.route.EventStructureRoutes.EventFieldEntry;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventGroupCancellationService;
import dev.chojo.ember.feature.events.service.EventGroupGuards;
import dev.chojo.ember.feature.events.service.EventGroupPartFactory;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.events.service.EventGroupViewService;
import dev.chojo.ember.feature.events.service.EventSignUpGuards;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Event groups: appointments that belong together and are signed up for once.
 *
 * <p>Members list and open the event groups meant for them and sign up, change their parts or withdraw,
 * for themselves or for the people they look after. Whoever edits appointments shapes event groups: their
 * details, sets, parts and audiences. Whoever handles registrations sees every sign-up and accepts or
 * turns them down; all three see every event group. An event group is open to a reader where it is meant for
 * them or for somebody they look after.
 *
 * <p>Whoever runs events signing somebody else up passes the deadline, the limit, parts already past
 * and the rules. Signing themselves up, they keep to the rules like every member: an event group is a
 * promise to everybody in it, and the one making the promise is no exception. A member may withdraw
 * a sign-up they or somebody they look after holds even where the event group is no longer meant for them.
 */
@Singleton
public class EventGroupRoutes implements Routes {
    private static final String EVENT_GROUP = "/events/groups/{eventGroupId}";

    private final EventGroupService eventGroups;
    private final EventGroupRegistrationService signUps;
    private final EventVisibility visibility;
    private final EventGroupCancellationService cancellation;
    private final EventGroupPartFactory partFactory;
    private final EventGroupGuards guards;
    private final EventFieldService fields;
    private final EventRequirementService requirements;
    private final EventSignUpGuards signUpGuards;
    private final EventGroupViewService view;

    @Inject
    public EventGroupRoutes(
            EventGroupService eventGroups,
            EventGroupRegistrationService signUps,
            EventVisibility visibility,
            EventGroupCancellationService cancellation,
            EventGroupPartFactory partFactory,
            EventGroupGuards guards,
            EventFieldService fields,
            EventRequirementService requirements,
            EventSignUpGuards signUpGuards,
            EventGroupViewService view) {
        this.signUpGuards = signUpGuards;
        this.view = view;
        this.eventGroups = eventGroups;
        this.signUps = signUps;
        this.visibility = visibility;
        this.cancellation = cancellation;
        this.partFactory = partFactory;
        this.guards = guards;
        this.fields = fields;
        this.requirements = requirements;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/groups", this::list, StationPermission.USER, StationModule.EVENTS);
        routes.get(
                prefix + "/events/groups/awaiting", this::listAwaiting, StationPermission.USER, StationModule.EVENTS);
        routes.get(
                prefix + "/events/{eventId}/group",
                this::eventGroupOfEvent,
                StationPermission.USER,
                StationModule.EVENTS);
        routes.post(prefix + "/events/groups", this::create, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.get(prefix + EVENT_GROUP, this::detail, StationPermission.USER, StationModule.EVENTS);
        routes.put(prefix + EVENT_GROUP, this::update, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.post(prefix + EVENT_GROUP + "/cancel", this::cancel, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.delete(prefix + EVENT_GROUP, this::delete, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.put(
                prefix + EVENT_GROUP + "/sets", this::replaceSets, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.put(
                prefix + EVENT_GROUP + "/audiences",
                this::setAudiences,
                StationPermission.EVENT_EDIT,
                StationModule.EVENTS);
        routes.post(prefix + EVENT_GROUP + "/parts", this::addPart, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.post(
                prefix + EVENT_GROUP + "/parts/new",
                this::createPart,
                StationPermission.EVENT_EDIT,
                StationModule.EVENTS);
        routes.put(
                prefix + EVENT_GROUP + "/parts/{eventId}",
                this::movePart,
                StationPermission.EVENT_EDIT,
                StationModule.EVENTS);
        routes.delete(
                prefix + EVENT_GROUP + "/parts/{eventId}",
                this::removePart,
                StationPermission.EVENT_EDIT,
                StationModule.EVENTS);
        routes.put(
                prefix + EVENT_GROUP + "/parts/{eventId}/required",
                this::setPartRequired,
                StationPermission.EVENT_EDIT,
                StationModule.EVENTS);
        routes.put(
                prefix + EVENT_GROUP + "/sign-up/answers",
                this::changeAnswers,
                StationPermission.USER,
                StationModule.EVENTS);
        routes.post(prefix + EVENT_GROUP + "/sign-up", this::signUp, StationPermission.USER, StationModule.EVENTS);
        routes.post(
                prefix + EVENT_GROUP + "/fields/{fieldId}/self-register",
                this::selfRegisterField,
                StationPermission.USER,
                StationModule.EVENTS);
        routes.put(prefix + EVENT_GROUP + "/sign-up", this::changeParts, StationPermission.USER, StationModule.EVENTS);
        routes.delete(prefix + EVENT_GROUP + "/sign-up", this::withdraw, StationPermission.USER, StationModule.EVENTS);
        routes.get(
                prefix + EVENT_GROUP + "/sign-ups",
                this::listSignUps,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
        routes.put(
                prefix + EVENT_GROUP + "/sign-ups/{memberId}/status",
                this::decide,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
        routes.post(
                prefix + EVENT_GROUP + "/sign-ups/queue",
                this::moveToQueue,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
        routes.put(
                prefix + EVENT_GROUP + "/sign-ups/{memberId}/queue-position",
                this::moveInQueue,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/events/groups",
            methods = HttpMethod.GET,
            summary = "List the event groups meant for the reader",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupSummary[].class)))
    private void list(Context ctx) {
        ctx.json(view.list(StationSession.from(ctx)));
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/group",
            methods = HttpMethod.GET,
            summary = "The event group an appointment is a part of, or nothing for an appointment of its own",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupPartSummary.class)),
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void eventGroupOfEvent(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "eventId"));
        var part = eventGroups.partSummary(session.stationId(), event.id());
        if (part == null) {
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }
        ctx.json(part);
    }

    @OpenApi(
            path = "/api/v1/events/groups",
            methods = HttpMethod.POST,
            summary = "Create an event group with its sets and audiences",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupCreateRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventGroupDetail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupCreateRequest.class);
        var eventGroup = eventGroups.createWith(
                session.stationId(),
                request.details(),
                Objects.requireNonNullElse(request.sets(), List.of()),
                audienceOr(request.registration()),
                audienceOr(request.view()),
                new EventGroupContent(draftsOf(request.questions()), fieldDraftsOf(request.fields())),
                request.appointmentId());
        ctx.status(HttpStatus.CREATED).json(view.detailOf(session, eventGroup));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}",
            methods = HttpMethod.GET,
            summary = "An event group with its sets, parts and the reader's sign-ups",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void detail(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(view.detailOf(session, visibleEventGroup(ctx, session)));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}",
            methods = HttpMethod.PUT,
            summary = "Change an event group's details and sign-up, and with them its sets and audiences where given",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupDetailsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupDetail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupDetailsRequest.class);
        var eventGroup = eventGroups.save(
                session.stationId(),
                pathInt(ctx, "eventGroupId"),
                request.details(),
                request.sets(),
                request.registration(),
                request.view(),
                new EventGroupContent(draftsOf(request.questions()), fieldDraftsOf(request.fields())));
        signUps.moveUp(session.stationId(), eventGroup.id());
        ctx.json(view.detailOf(session, eventGroup));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/fields/{fieldId}/self-register",
            methods = HttpMethod.POST,
            summary = "Toggle the reader in or out of a member field of an event group for the whole group",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AppointmentField.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void selfRegisterField(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var eventGroup = visibleEventGroup(ctx, session);
        ctx.json(fields.toggleEventGroupSelfRegistration(
                eventGroup.id(),
                pathInt(ctx, "fieldId"),
                session.member().id(),
                EventGroupViewService.runsEventGroups(session)));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}",
            methods = HttpMethod.DELETE,
            summary = "Delete an event group; its parts become appointments of their own",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        StationSession session = StationSession.from(ctx);
        eventGroups.delete(session.stationId(), pathInt(ctx, "eventGroupId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/cancel",
            methods = HttpMethod.POST,
            summary = "Call an event group off with every part still to come",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupCancelRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupDetail.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void cancel(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int eventGroupId = pathInt(ctx, "eventGroupId");
        var request = ctx.bodyAsClass(EventGroupCancelRequest.class);
        cancellation.cancel(
                session.stationId(),
                eventGroupId,
                request.reason(),
                session.member().id());
        ctx.json(view.detailOf(session, eventGroups.requireEventGroup(session.stationId(), eventGroupId)));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sets",
            methods = HttpMethod.PUT,
            summary = "Replace an event group's sets",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupSetsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupDetail.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void replaceSets(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int eventGroupId = pathInt(ctx, "eventGroupId");
        var request = ctx.bodyAsClass(EventGroupSetsRequest.class);
        eventGroups.replaceSets(
                session.stationId(), eventGroupId, Objects.requireNonNullElse(request.sets(), List.of()));
        ctx.json(view.detailOf(session, eventGroups.requireEventGroup(session.stationId(), eventGroupId)));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/audiences",
            methods = HttpMethod.PUT,
            summary = "Set who may see and who may sign up for an event group and every part",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupAudiences.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupDetail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setAudiences(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int eventGroupId = pathInt(ctx, "eventGroupId");
        var request = ctx.bodyAsClass(EventGroupAudiences.class);
        eventGroups.setAudiences(
                session.stationId(), eventGroupId, audienceOr(request.registration()), audienceOr(request.view()));
        ctx.json(view.detailOf(session, eventGroups.requireEventGroup(session.stationId(), eventGroupId)));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/parts",
            methods = HttpMethod.POST,
            summary = "Make a one-time appointment a part of an event group",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupPartRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventGroupPart.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void addPart(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupPartRequest.class);
        int eventGroupId = pathInt(ctx, "eventGroupId");
        eventGroups.requireEventGroup(session.stationId(), eventGroupId);
        requirements.requireJoinable(eventGroupId, request.eventId());
        var part = eventGroups.addPart(session.stationId(), eventGroupId, request.eventId(), request.setId());
        ctx.status(HttpStatus.CREATED).json(part);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/parts/new",
            methods = HttpMethod.POST,
            summary = "Create a new one-time appointment straight into an event group",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupPartCreateRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventGroupPart.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createPart(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupPartCreateRequest.class);
        var part = partFactory.create(
                session.stationId(),
                pathInt(ctx, "eventGroupId"),
                request.setId(),
                request.name(),
                request.description(),
                request.startTime(),
                request.endTime(),
                null);
        ctx.status(HttpStatus.CREATED).json(part);
    }

    @OpenApi(
            path = "/api/v1/events/groups/awaiting",
            methods = HttpMethod.GET,
            summary = "Event groups still waiting on an answer from the reader or anyone they answer for",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupAwaiting[].class)))
    private void listAwaiting(Context ctx) {
        ctx.json(view.awaiting(StationSession.from(ctx)));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/parts/{eventId}/required",
            methods = HttpMethod.PUT,
            summary = "Mark a part as required by its set, or take the mark off",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "eventId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupPartRequiredRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupPart.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setPartRequired(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupPartRequiredRequest.class);
        ctx.json(eventGroups.setRequired(
                session.stationId(), pathInt(ctx, "eventGroupId"), pathInt(ctx, "eventId"), request.required()));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/parts/{eventId}",
            methods = HttpMethod.PUT,
            summary = "Move a part into another set of its event group",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "eventId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupPartMoveRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupPart.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void movePart(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupPartMoveRequest.class);
        ctx.json(eventGroups.movePart(
                session.stationId(), pathInt(ctx, "eventGroupId"), pathInt(ctx, "eventId"), request.setId()));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/parts/{eventId}",
            methods = HttpMethod.DELETE,
            summary = "Make a part an appointment of its own again",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "eventId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void removePart(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        int eventGroupId = pathInt(ctx, "eventGroupId");
        eventGroups.removePart(session.stationId(), eventGroupId, eventId);
        signUps.moveUp(session.stationId(), eventGroupId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sign-up",
            methods = HttpMethod.POST,
            summary = "Sign the reader, or somebody they look after, up for an event group",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupSignUpRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventGroupSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void signUp(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupSignUpRequest.class);
        var eventGroup = visibleEventGroup(ctx, session);
        int memberId = targetMember(session, request.memberId());
        boolean override = EventGroupViewService.overrides(session, memberId);
        guards.requireMayJoin(session, eventGroup.id(), memberId, override);
        signUps.register(
                session.stationId(),
                eventGroup.id(),
                memberId,
                partsOf(request),
                EventRegistrationRoutes.answersOf(request.answers()),
                createdBy(session, memberId),
                override,
                override ? request.whenFull() : null);
        ctx.status(HttpStatus.CREATED).json(view.signUpOf(session, eventGroup.id(), memberId));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sign-up",
            methods = HttpMethod.PUT,
            summary = "Change the parts chosen with an event group sign-up",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupSignUpRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void changeParts(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupSignUpRequest.class);
        var eventGroup = visibleEventGroup(ctx, session);
        int memberId = targetMember(session, request.memberId());
        boolean override = EventGroupViewService.overrides(session, memberId);
        guards.requireMayJoin(session, eventGroup.id(), memberId, override);
        signUps.changeParts(
                session.stationId(),
                eventGroup.id(),
                memberId,
                partsOf(request),
                createdBy(session, memberId),
                override);
        ctx.json(view.signUpOf(session, eventGroup.id(), memberId));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sign-up/answers",
            methods = HttpMethod.PUT,
            summary = "Change the answers given with an event group sign-up",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupAnswersRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void changeAnswers(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupAnswersRequest.class);
        var eventGroup = visibleEventGroup(ctx, session);
        int memberId = targetMember(session, request.memberId());
        signUps.changeAnswers(
                session.stationId(),
                eventGroup.id(),
                memberId,
                EventRegistrationRoutes.answersOf(request.answers()),
                view.readsAnswersOf(session, memberId),
                EventGroupViewService.overrides(session, memberId));
        ctx.json(view.signUpOf(session, eventGroup.id(), memberId));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sign-up",
            methods = HttpMethod.DELETE,
            summary = "Withdraw an event group sign-up with every part chosen",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "memberId", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupSignUp.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void withdraw(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var eventGroup = eventGroups.requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"));
        var asked = ctx.queryParam("memberId");
        int memberId = targetMember(session, asked == null ? null : Integer.valueOf(asked));
        signUps.withdraw(session.stationId(), eventGroup.id(), memberId);
        ctx.json(view.signUpOf(session, eventGroup.id(), memberId));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sign-ups",
            methods = HttpMethod.GET,
            summary = "Every sign-up of an event group, with the parts chosen and the sets each falls short of",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupSignUp[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listSignUps(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(view.signUpsOf(
                session, eventGroups.requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"))));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sign-ups/{memberId}/status",
            methods = HttpMethod.PUT,
            summary = "Accept or turn down an event group sign-up and every part chosen with it",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupDecisionRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void decide(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int eventGroupId = pathInt(ctx, "eventGroupId");
        int memberId = pathInt(ctx, "memberId");
        var request = ctx.bodyAsClass(EventGroupDecisionRequest.class);
        signUps.decide(
                session.stationId(),
                eventGroupId,
                memberId,
                request.status(),
                Boolean.TRUE.equals(request.overLimit()));
        ctx.json(view.signUpOf(session, eventGroupId, memberId));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sign-ups/queue",
            methods = HttpMethod.POST,
            summary = "Put event group sign-ups waiting for a decision onto the event group's waiting list",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupQueueRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void moveToQueue(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupQueueRequest.class);
        signUps.moveToQueue(
                session.stationId(),
                pathInt(ctx, "eventGroupId"),
                Objects.requireNonNullElse(request.ids(), List.of()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sign-ups/{memberId}/queue-position",
            methods = HttpMethod.PUT,
            summary = "Move an event group sign-up to another place in line on the waiting list",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupQueuePositionRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void moveInQueue(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupQueuePositionRequest.class);
        if (request.position() == null) throw EventRefusal.QUEUE_POSITION_MISSING.raise();
        signUps.moveInQueue(
                session.stationId(), pathInt(ctx, "eventGroupId"), pathInt(ctx, "memberId"), request.position());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private EventGroup visibleEventGroup(Context ctx, StationSession session) {
        return guards.requireOpen(session, pathInt(ctx, "eventGroupId"));
    }

    private int targetMember(StationSession session, @Nullable Integer requested) {
        int memberId = signUpGuards.requireTarget(session, requested);
        if (memberId != session.member().id() && !eventGroups.isMemberHere(session.stationId(), memberId))
            throw EventRefusal.EVENT_GROUP_MEMBER_NOT_HERE.raise();
        return memberId;
    }

    private static @Nullable Integer createdBy(StationSession session, int memberId) {
        return memberId != session.member().id() ? session.member().id() : null;
    }

    private static Set<Integer> partsOf(EventGroupSignUpRequest request) {
        return Set.copyOf(Objects.requireNonNullElse(request.parts(), List.of()));
    }

    private static @Nullable List<RegistrationFieldDraft> draftsOf(@Nullable List<RegistrationFieldDefinition> fields) {
        return fields == null
                ? null
                : fields.stream().map(RegistrationFieldDefinition::toDraft).toList();
    }

    private static @Nullable List<EventFieldDraft> fieldDraftsOf(@Nullable List<EventFieldEntry> fields) {
        return fields == null
                ? null
                : fields.stream().map(EventFieldEntry::toDraft).toList();
    }

    private static RestrictionAudience audienceOr(@Nullable RestrictionAudience audience) {
        return audience == null ? RestrictionAudience.empty() : audience;
    }

    /**
     * A new event group.
     *
     * @param details       what it holds
     * @param sets          its sets in order, at least one
     * @param registration  who may sign up, or null for everybody who may see it
     * @param view          who may see it, or null for everybody
     * @param questions     what everybody signing up is asked, or null for nothing
     * @param fields        what every appointment shows, or null for nothing
     * @param appointmentId a one-time appointment that becomes the first part, in the first set, or
     *                      null; where it cannot join, nothing is created
     */
    public record EventGroupCreateRequest(
            EventGroupDetails details,
            @Nullable List<EventGroupSetDraft> sets,
            @Nullable RestrictionAudience registration,
            @Nullable RestrictionAudience view,
            @Nullable List<RegistrationFieldDefinition> questions,
            @Nullable List<EventFieldEntry> fields,
            @Nullable Integer appointmentId) {}

    /**
     * An event group saved from its editor. Sets and audiences left out stay as they are.
     *
     * @param details      what the event group holds from now on
     * @param sets         its sets in their new order, or null
     * @param registration who may sign up, or null
     * @param view         who may see it, or null
     * @param questions    what everybody signing up is asked from now on, or null
     * @param fields       what every appointment shows from now on, or null
     */
    public record EventGroupDetailsRequest(
            EventGroupDetails details,
            @Nullable List<EventGroupSetDraft> sets,
            @Nullable RestrictionAudience registration,
            @Nullable RestrictionAudience view,
            @Nullable List<RegistrationFieldDefinition> questions,
            @Nullable List<EventFieldEntry> fields) {}

    /** @param sets the sets in their new order */
    public record EventGroupSetsRequest(@Nullable List<EventGroupSetDraft> sets) {}

    /**
     * Who may sign up for an event group and who may see it.
     *
     * @param registration who may sign up, or null for everybody who may see it
     * @param view         who may see it, or null for everybody
     */
    public record EventGroupAudiences(
            @Nullable RestrictionAudience registration,
            @Nullable RestrictionAudience view) {}

    /**
     * An appointment to become a part.
     *
     * @param eventId the appointment
     * @param setId   the set it goes into
     */
    public record EventGroupPartRequest(int eventId, int setId) {}

    /** @param setId the set the part moves into */
    public record EventGroupPartMoveRequest(int setId) {}

    /**
     * Whether a part is to be required by its set.
     *
     * @param required true to require it, false to take the mark off
     */
    public record EventGroupPartRequiredRequest(boolean required) {}

    /**
     * A sign-up or a change of parts.
     *
     * @param memberId who signs up, or null for the reader
     * @param parts    the chosen parts
     * @param answers  the answers to the event group's questions, read on a new sign-up only
     * @param whenFull what whoever runs events chose for a choice that does not fit; null asks them first,
     *                 and is all a member signing up can send
     */
    public record EventGroupSignUpRequest(
            @Nullable Integer memberId,
            @Nullable List<Integer> parts,
            @Nullable List<EventRegistrationFieldValue> answers,
            @Nullable WhenFull whenFull) {}

    /**
     * New answers for a standing event group sign-up.
     *
     * @param memberId whose sign-up, or null for the reader
     * @param answers  the answers to the event group's questions
     */
    public record EventGroupAnswersRequest(
            @Nullable Integer memberId, @Nullable List<EventRegistrationFieldValue> answers) {}

    /**
     * A new appointment created straight into an event group.
     *
     * @param setId       the set it goes into
     * @param name        its name
     * @param description what it is about, as Markdown, or null
     * @param startTime   when it begins
     * @param endTime     when it ends
     */
    public record EventGroupPartCreateRequest(
            int setId,
            String name,
            @Nullable String description,
            @Nullable Instant startTime,
            @Nullable Instant endTime) {}

    /** @param reason why the event group is called off, as told to the people signed up, or null */
    public record EventGroupCancelRequest(@Nullable String reason) {}

    /**
     * A decision on an event group sign-up.
     *
     * @param status    {@code ACCEPTED} or {@code DENIED}
     * @param overLimit whether an acceptance may go beyond the limit; without it a choice that does not fit
     *                  is refused
     */
    public record EventGroupDecisionRequest(
            RegistrationStatus status, @Nullable Boolean overLimit) {}

    /**
     * Sign-ups to put onto an event group's waiting list.
     *
     * @param ids the members, or for partners' members the partner sign-ups, each waiting for a decision
     */
    public record EventGroupQueueRequest(@Nullable List<Integer> ids) {}

    /**
     * A place in line on an event group's waiting list.
     *
     * @param position the place, 1 moving up next; beyond the end goes last
     */
    public record EventGroupQueuePositionRequest(@Nullable Integer position) {}
}
