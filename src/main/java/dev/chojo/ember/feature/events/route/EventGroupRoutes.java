/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.EventGroupPartState;
import dev.chojo.ember.feature.events.entity.EventGroupPartSummary;
import dev.chojo.ember.feature.events.entity.EventGroupRegistration;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.route.EventRegistrationRoutes.AwaitingMember;
import dev.chojo.ember.feature.events.route.EventRegistrationRoutes.EventRegistrationFieldValue;
import dev.chojo.ember.feature.events.route.EventStructureRoutes.EventFieldEntry;
import dev.chojo.ember.feature.events.service.EventGroupCancellationService;
import dev.chojo.ember.feature.events.service.EventGroupPartFactory;
import dev.chojo.ember.feature.events.service.EventGroupQuestionService;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService.EventGroupStanding;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    private final GuardianPolicy guardianPolicy;
    private final MemberIdentityFactory identities;
    private final EventVisibility visibility;
    private final EventGroupCancellationService cancellation;
    private final EventGroupPartFactory partFactory;
    private final EventGroupQuestionService questions;

    @Inject
    public EventGroupRoutes(
            EventGroupService eventGroups,
            EventGroupRegistrationService signUps,
            GuardianPolicy guardianPolicy,
            MemberIdentityFactory identities,
            EventVisibility visibility,
            EventGroupCancellationService cancellation,
            EventGroupPartFactory partFactory,
            EventGroupQuestionService questions) {
        this.eventGroups = eventGroups;
        this.signUps = signUps;
        this.guardianPolicy = guardianPolicy;
        this.identities = identities;
        this.visibility = visibility;
        this.cancellation = cancellation;
        this.partFactory = partFactory;
        this.questions = questions;
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
                prefix + EVENT_GROUP + "/sign-up/answers",
                this::changeAnswers,
                StationPermission.USER,
                StationModule.EVENTS);
        routes.post(prefix + EVENT_GROUP + "/sign-up", this::signUp, StationPermission.USER, StationModule.EVENTS);
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
    }

    @OpenApi(
            path = "/api/v1/events/groups",
            methods = HttpMethod.GET,
            summary = "List the event groups meant for the reader",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupSummary[].class)))
    private void list(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(eventGroups.findByStation(session.stationId()).stream()
                .filter(eventGroup -> mayOpen(session, eventGroup.id()))
                .map(eventGroup -> EventGroupSummary.of(
                        eventGroup, eventGroups.findParts(eventGroup.id()).size(), signUps.holding(eventGroup.id())))
                .toList());
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
        var eventGroup = eventGroups.create(
                session.stationId(),
                request.details(),
                Objects.requireNonNullElse(request.sets(), List.of()),
                audienceOr(request.registration()),
                audienceOr(request.view()),
                new EventGroupContent(draftsOf(request.questions()), fieldDraftsOf(request.fields())));
        ctx.status(HttpStatus.CREATED).json(detailOf(session, eventGroup));
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
        ctx.json(detailOf(session, visibleEventGroup(ctx, session)));
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
        ctx.json(detailOf(session, eventGroup));
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
        ctx.json(detailOf(session, eventGroups.requireEventGroup(session.stationId(), eventGroupId)));
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
        ctx.json(detailOf(session, eventGroups.requireEventGroup(session.stationId(), eventGroupId)));
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
        ctx.json(detailOf(session, eventGroups.requireEventGroup(session.stationId(), eventGroupId)));
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
        ctx.status(HttpStatus.CREATED)
                .json(eventGroups.addPart(
                        session.stationId(), pathInt(ctx, "eventGroupId"), request.eventId(), request.setId()));
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
        String name = request.name();
        Instant start = request.startTime();
        Instant end = request.endTime();
        if (name == null || name.isBlank()) throw EventRefusal.EVENT_GROUP_NAME_MISSING.raise();
        if (start == null || end == null) throw EventRefusal.EVENT_GROUP_PART_NEEDS_TIMES.raise();
        ctx.status(HttpStatus.CREATED)
                .json(partFactory.create(
                        session.stationId(),
                        pathInt(ctx, "eventGroupId"),
                        request.setId(),
                        name.strip(),
                        request.description(),
                        start,
                        end));
    }

    @OpenApi(
            path = "/api/v1/events/groups/awaiting",
            methods = HttpMethod.GET,
            summary = "Event groups still waiting on an answer from the reader or anyone they answer for",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupAwaiting[].class)))
    private void listAwaiting(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var household = guardianPolicy.household(session.user());
        var permissions = session.user().permissions();
        var awaiting = new ArrayList<EventGroupAwaiting>();
        for (var eventGroup : eventGroups.findOpenWithDeadline(session.stationId())) {
            var answered = signUps.findByEventGroup(eventGroup.id()).stream()
                    .map(EventGroupRegistration::memberId)
                    .collect(Collectors.toSet());
            var owing = household.stream()
                    .filter(memberId -> !answered.contains(memberId))
                    .filter(memberId -> eventGroups.canSignUp(eventGroup.id(), memberId, permissions))
                    .map(memberId -> new AwaitingMember(
                            memberId,
                            Objects.requireNonNullElse(
                                    identities
                                            .local(session.stationId(), memberId)
                                            .name(),
                                    "")))
                    .toList();
            if (!owing.isEmpty()) {
                awaiting.add(new EventGroupAwaiting(
                        eventGroup.id(), eventGroup.name(), eventGroup.registrationDeadline(), owing));
            }
        }
        ctx.json(awaiting);
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
        eventGroups.removePart(session.stationId(), pathInt(ctx, "eventGroupId"), pathInt(ctx, "eventId"));
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
        boolean override = overrides(session, memberId);
        requireMayJoin(session, eventGroup.id(), memberId, override);
        signUps.register(
                session.stationId(),
                eventGroup.id(),
                memberId,
                partsOf(request),
                EventRegistrationRoutes.answersOf(request.answers()),
                createdBy(session, memberId),
                override);
        ctx.status(HttpStatus.CREATED).json(signUpOf(session, eventGroup.id(), memberId));
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
        boolean override = overrides(session, memberId);
        requireMayJoin(session, eventGroup.id(), memberId, override);
        signUps.changeParts(
                session.stationId(),
                eventGroup.id(),
                memberId,
                partsOf(request),
                createdBy(session, memberId),
                override);
        ctx.json(signUpOf(session, eventGroup.id(), memberId));
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
                readsAnswersOf(session, memberId),
                overrides(session, memberId));
        ctx.json(signUpOf(session, eventGroup.id(), memberId));
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
        ctx.json(signUpOf(session, eventGroup.id(), memberId));
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
        var eventGroup = eventGroups.requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"));
        var standing = signUps.findByEventGroup(eventGroup.id()).stream()
                .filter(signUp -> signUp.active() || signUp.status() == RegistrationStatus.DENIED)
                .map(EventGroupRegistration::memberId)
                .toList();
        ctx.json(signUpsOf(
                session,
                questions.findByEventGroup(eventGroup.id()),
                signUps.standings(eventGroup.id(), standing),
                entry -> true));
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
        signUps.decide(session.stationId(), eventGroupId, memberId, request.status());
        ctx.json(signUpOf(session, eventGroupId, memberId));
    }

    private EventGroup visibleEventGroup(Context ctx, StationSession session) {
        var eventGroup = eventGroups.requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"));
        if (!mayOpen(session, eventGroup.id())) throw EventRefusal.EVENT_GROUP_NOT_HERE.raise();
        return eventGroup;
    }

    /** Whether the reader may open an event group: it is meant for them or somebody they look after. */
    private boolean mayOpen(StationSession session, int eventGroupId) {
        return seesEverything(session)
                || eventGroups.canSeeAny(
                        eventGroupId,
                        guardianPolicy.household(session.user()),
                        session.user().permissions());
    }

    private static boolean seesEverything(StationSession session) {
        return session.hasPermission(StationPermission.EVENT_EDIT)
                || session.hasPermission(StationPermission.EVENT_MANAGER)
                || session.hasPermission(StationPermission.EVENT_REGISTRATION);
    }

    private void requireMayJoin(StationSession session, int eventGroupId, int memberId, boolean override) {
        if (!override
                && !eventGroups.canSignUp(eventGroupId, memberId, session.user().permissions())) {
            throw EventRefusal.MEMBER_NOT_INVITED_TO_EVENT.raise();
        }
    }

    private int targetMember(StationSession session, @Nullable Integer requested) {
        if (requested == null || requested == session.member().id())
            return session.member().id();
        if (!guardianPolicy.mayActFor(session.user(), requested)
                && !session.hasPermission(StationPermission.EVENT_MANAGER)) {
            throw EventRefusal.MEMBER_NOT_YOURS_TO_REGISTER.raise();
        }
        if (!eventGroups.isMemberHere(session.stationId(), requested))
            throw EventRefusal.EVENT_GROUP_MEMBER_NOT_HERE.raise();
        return requested;
    }

    private static boolean overrides(StationSession session, int memberId) {
        return memberId != session.member().id() && session.hasPermission(StationPermission.EVENT_MANAGER);
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

    private EventGroupDetail detailOf(StationSession session, EventGroup eventGroup) {
        var permissions = session.user().permissions();
        var asked = questions.findByEventGroup(eventGroup.id());
        var household = guardianPolicy.household(session.user()).stream()
                .filter(memberId -> eventGroups.canSee(eventGroup.id(), memberId, permissions))
                .toList();
        return new EventGroupDetail(
                EventGroupSummary.of(
                        eventGroup, eventGroups.findParts(eventGroup.id()).size(), signUps.holding(eventGroup.id())),
                eventGroups.findSets(eventGroup.id()),
                signUps.partStates(eventGroup.id()),
                eventGroups.registrationAudience(eventGroup.id()),
                eventGroups.viewAudience(eventGroup.id()),
                asked,
                eventGroups.findFields(eventGroup.id()),
                signUpsOf(
                        session,
                        asked,
                        signUps.standings(eventGroup.id(), household),
                        entry -> eventGroups.canSignUp(eventGroup.id(), entry.memberId(), permissions)));
    }

    private EventGroupSignUp signUpOf(StationSession session, int eventGroupId, int memberId) {
        var permissions = session.user().permissions();
        return signUpsOf(
                        session,
                        questions.findByEventGroup(eventGroupId),
                        signUps.standings(eventGroupId, List.of(memberId)),
                        entry -> eventGroups.canSignUp(eventGroupId, entry.memberId(), permissions))
                .getFirst();
    }

    /**
     * Turns standings into what a response shows, reading the answers of all of them at once. Answers
     * kept for organisers are left out unless the reader runs events or the answers are about them or
     * somebody they look after. Whether a required answer is still owed is told either way.
     */
    private List<EventGroupSignUp> signUpsOf(
            StationSession session,
            List<EventRegistrationField> asked,
            List<EventGroupStanding> entries,
            Predicate<EventGroupStanding> mayRegister) {
        var answers = questions.answersBySignUp(entries.stream()
                .map(EventGroupStanding::signUp)
                .filter(Objects::nonNull)
                .map(EventGroupRegistration::id)
                .toList());
        var required = EventGroupQuestionService.requiredIds(asked);
        var hidden = asked.stream()
                .filter(field -> field.config().managersOnly())
                .map(EventRegistrationField::id)
                .collect(Collectors.toSet());
        return entries.stream()
                .map(entry -> {
                    var signUp = entry.signUp();
                    boolean readsHidden = readsAnswersOf(session, entry.memberId());
                    var stored = signUp == null
                            ? List.<RegistrationFieldValue>of()
                            : answers.getOrDefault(signUp.id(), List.of());
                    var given = stored.stream()
                            .filter(value -> readsHidden || !hidden.contains(value.fieldId()))
                            .map(value -> new EventRegistrationFieldValue(value.fieldId(), value.value()))
                            .toList();
                    return new EventGroupSignUp(
                            entry.memberId(),
                            identities.local(session.stationId(), entry.memberId()),
                            signUp == null ? null : signUp.status(),
                            signUp == null ? null : signUp.registeredAt(),
                            entry.counted().stream().sorted().toList(),
                            entry.sets(),
                            given,
                            signUp != null
                                    && EventRegistrationFieldService.owesAnswer(signUp.status(), required, stored),
                            mayRegister.test(entry));
                })
                .toList();
    }

    private boolean readsAnswersOf(StationSession session, int memberId) {
        return session.hasPermission(StationPermission.EVENT_EDIT)
                || memberId == session.member().id()
                || guardianPolicy.mayActFor(session.user(), memberId);
    }

    /**
     * An event group as lists show it.
     *
     * @param id                   the event group
     * @param name                 its name
     * @param description          what it is about, as Markdown
     * @param registrationLimit    how many people it takes, or null
     * @param registrationDeadline when sign-ups close, or null
     * @param requiresConfirmation whether a manager confirms each sign-up
     * @param cancelled            whether it was called off
     * @param cancelReason         why, where it was
     * @param partCount            how many parts it has
     * @param holding              how many sign-ups hold or wait for a place
     */
    public record EventGroupSummary(
            int id,
            String name,
            @Nullable String description,
            @Nullable Integer registrationLimit,
            @Nullable Instant registrationDeadline,
            boolean requiresConfirmation,
            boolean cancelled,
            @Nullable String cancelReason,
            int partCount,
            int holding) {
        public static EventGroupSummary of(EventGroup eventGroup, int partCount, int holding) {
            return new EventGroupSummary(
                    eventGroup.id(),
                    eventGroup.name(),
                    eventGroup.description(),
                    eventGroup.registrationLimit(),
                    eventGroup.registrationDeadline(),
                    eventGroup.requiresConfirmation(),
                    eventGroup.cancelled(),
                    eventGroup.cancelReason(),
                    partCount,
                    holding);
        }
    }

    /**
     * An event group with everything its page shows.
     *
     * @param eventGroup      the event group
     * @param sets         its sets in order
     * @param parts        its parts in date order
     * @param registration who may sign up
     * @param view         who may see it
     * @param questions    what it asks of everybody signing up, in order
     * @param fields       what every appointment of it shows, such as its place, in order
     * @param household    the sign-up of the reader and of everybody they look after who may see it
     */
    public record EventGroupDetail(
            EventGroupSummary eventGroup,
            List<EventGroupSet> sets,
            List<EventGroupPartState> parts,
            RestrictionAudience registration,
            RestrictionAudience view,
            List<EventRegistrationField> questions,
            List<AppointmentField> fields,
            List<EventGroupSignUp> household) {}

    /**
     * One member's standing in an event group.
     *
     * @param memberId     the member's id, which sign-ups and decisions for them name
     * @param member       the member
     * @param status       the sign-up's status, or null where the member is not signed up
     * @param registeredAt when they signed up, or null
     * @param parts        the parts that count for the rules, in id order
     * @param sets         how those parts stand against every set
     * @param answers      the answers given to the event group's questions, those kept for organisers only
     *                     where the reader may read them
     * @param answersMissing whether a standing sign-up still owes an answer to a required question
     * @param mayRegister  whether the member is one of the people the event group takes
     */
    public record EventGroupSignUp(
            int memberId,
            MemberIdentity member,
            @Nullable RegistrationStatus status,
            @Nullable Instant registeredAt,
            List<Integer> parts,
            List<SetResult> sets,
            List<EventRegistrationFieldValue> answers,
            boolean answersMissing,
            boolean mayRegister) {}

    /**
     * A new event group.
     *
     * @param details      what it holds
     * @param sets         its sets in order, at least one
     * @param registration who may sign up, or null for everybody who may see it
     * @param view         who may see it, or null for everybody
     * @param questions    what everybody signing up is asked, or null for nothing
     * @param fields       what every appointment shows, or null for nothing
     */
    public record EventGroupCreateRequest(
            EventGroupDetails details,
            @Nullable List<EventGroupSetDraft> sets,
            @Nullable RestrictionAudience registration,
            @Nullable RestrictionAudience view,
            @Nullable List<RegistrationFieldDefinition> questions,
            @Nullable List<EventFieldEntry> fields) {}

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
     * A sign-up or a change of parts.
     *
     * @param memberId who signs up, or null for the reader
     * @param parts    the chosen parts
     * @param answers  the answers to the event group's questions, read on a new sign-up only
     */
    public record EventGroupSignUpRequest(
            @Nullable Integer memberId,
            @Nullable List<Integer> parts,
            @Nullable List<EventRegistrationFieldValue> answers) {}

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

    /**
     * An event group still waiting on an answer, and everybody in the household who owes one.
     *
     * @param eventGroupId             the event group
     * @param name                 its name
     * @param registrationDeadline when sign-ups close
     * @param members              who still owes an answer
     */
    public record EventGroupAwaiting(
            int eventGroupId, String name, @Nullable Instant registrationDeadline, List<AwaitingMember> members) {}

    /** @param reason why the event group is called off, as told to the people signed up, or null */
    public record EventGroupCancelRequest(@Nullable String reason) {}

    /** @param status {@code ACCEPTED} or {@code DENIED} */
    public record EventGroupDecisionRequest(RegistrationStatus status) {}
}
