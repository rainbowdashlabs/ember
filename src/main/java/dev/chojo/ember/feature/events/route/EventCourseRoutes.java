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
import dev.chojo.ember.feature.events.entity.CoursePartSummary;
import dev.chojo.ember.feature.events.entity.EventCourse;
import dev.chojo.ember.feature.events.entity.EventCourseDetails;
import dev.chojo.ember.feature.events.entity.EventCoursePart;
import dev.chojo.ember.feature.events.entity.EventCoursePartState;
import dev.chojo.ember.feature.events.entity.EventCourseRegistration;
import dev.chojo.ember.feature.events.entity.EventCourseSet;
import dev.chojo.ember.feature.events.entity.EventCourseSetDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.service.CourseRules.SetResult;
import dev.chojo.ember.feature.events.service.EventCourseRegistrationService;
import dev.chojo.ember.feature.events.service.EventCourseRegistrationService.CourseStanding;
import dev.chojo.ember.feature.events.service.EventCourseService;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Courses: appointments that belong together and are signed up for once.
 *
 * <p>Members list and open the courses meant for them and sign up, change their parts or withdraw,
 * for themselves or for the people they look after. Whoever edits appointments shapes courses: their
 * details, sets, parts and audiences. Whoever handles registrations sees every sign-up and accepts or
 * turns them down; all three see every course. A course is open to a reader where it is meant for
 * them or for somebody they look after.
 *
 * <p>Whoever runs events signing somebody else up passes the deadline, the limit, parts already past
 * and the rules. Signing themselves up, they keep to the rules like every member: a course is a
 * promise to everybody in it, and the one making the promise is no exception. A member may withdraw
 * a sign-up they or somebody they look after holds even where the course is no longer meant for them.
 */
@Singleton
public class EventCourseRoutes implements Routes {
    private static final String COURSE = "/events/courses/{courseId}";

    private final EventCourseService courses;
    private final EventCourseRegistrationService signUps;
    private final GuardianPolicy guardianPolicy;
    private final MemberIdentityFactory identities;
    private final EventVisibility visibility;

    @Inject
    public EventCourseRoutes(
            EventCourseService courses,
            EventCourseRegistrationService signUps,
            GuardianPolicy guardianPolicy,
            MemberIdentityFactory identities,
            EventVisibility visibility) {
        this.courses = courses;
        this.signUps = signUps;
        this.guardianPolicy = guardianPolicy;
        this.identities = identities;
        this.visibility = visibility;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/courses", this::list, StationPermission.USER, StationModule.EVENTS);
        routes.get(
                prefix + "/events/{eventId}/course", this::courseOfEvent, StationPermission.USER, StationModule.EVENTS);
        routes.post(prefix + "/events/courses", this::create, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.get(prefix + COURSE, this::detail, StationPermission.USER, StationModule.EVENTS);
        routes.put(prefix + COURSE, this::update, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.delete(prefix + COURSE, this::delete, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.put(prefix + COURSE + "/sets", this::replaceSets, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.put(
                prefix + COURSE + "/audiences", this::setAudiences, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.post(prefix + COURSE + "/parts", this::addPart, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.put(
                prefix + COURSE + "/parts/{eventId}",
                this::movePart,
                StationPermission.EVENT_EDIT,
                StationModule.EVENTS);
        routes.delete(
                prefix + COURSE + "/parts/{eventId}",
                this::removePart,
                StationPermission.EVENT_EDIT,
                StationModule.EVENTS);
        routes.post(prefix + COURSE + "/sign-up", this::signUp, StationPermission.USER, StationModule.EVENTS);
        routes.put(prefix + COURSE + "/sign-up", this::changeParts, StationPermission.USER, StationModule.EVENTS);
        routes.delete(prefix + COURSE + "/sign-up", this::withdraw, StationPermission.USER, StationModule.EVENTS);
        routes.get(
                prefix + COURSE + "/sign-ups",
                this::listSignUps,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
        routes.put(
                prefix + COURSE + "/sign-ups/{memberId}/status",
                this::decide,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/events/courses",
            methods = HttpMethod.GET,
            summary = "List the courses meant for the reader",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseSummary[].class)))
    private void list(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(courses.findByStation(session.stationId()).stream()
                .filter(course -> mayOpen(session, course.id()))
                .map(course ->
                        CourseSummary.of(course, courses.findParts(course.id()).size(), signUps.holding(course.id())))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/course",
            methods = HttpMethod.GET,
            summary = "The course an appointment is a part of, or nothing for an appointment of its own",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CoursePartSummary.class)),
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void courseOfEvent(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "eventId"));
        var part = courses.partSummary(session.stationId(), event.id());
        if (part == null) {
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }
        ctx.json(part);
    }

    @OpenApi(
            path = "/api/v1/events/courses",
            methods = HttpMethod.POST,
            summary = "Create a course with its sets and audiences",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CourseCreateRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = CourseDetail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CourseCreateRequest.class);
        var course = courses.create(
                session.stationId(),
                request.details(),
                Objects.requireNonNullElse(request.sets(), List.of()),
                audienceOr(request.registration()),
                audienceOr(request.view()));
        ctx.status(HttpStatus.CREATED).json(detailOf(session, course));
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}",
            methods = HttpMethod.GET,
            summary = "A course with its sets, parts and the reader's sign-ups",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void detail(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(detailOf(session, visibleCourse(ctx, session)));
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}",
            methods = HttpMethod.PUT,
            summary = "Change a course's details and sign-up",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CourseDetailsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseDetail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CourseDetailsRequest.class);
        var course = courses.update(session.stationId(), pathInt(ctx, "courseId"), request.details());
        ctx.json(detailOf(session, course));
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a course; its parts become appointments of their own",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        StationSession session = StationSession.from(ctx);
        courses.delete(session.stationId(), pathInt(ctx, "courseId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/sets",
            methods = HttpMethod.PUT,
            summary = "Replace a course's sets",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CourseSetsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseDetail.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void replaceSets(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int courseId = pathInt(ctx, "courseId");
        var request = ctx.bodyAsClass(CourseSetsRequest.class);
        courses.replaceSets(session.stationId(), courseId, Objects.requireNonNullElse(request.sets(), List.of()));
        ctx.json(detailOf(session, courses.requireCourse(session.stationId(), courseId)));
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/audiences",
            methods = HttpMethod.PUT,
            summary = "Set who may see and who may sign up for a course and every part",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CourseAudiences.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseDetail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setAudiences(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int courseId = pathInt(ctx, "courseId");
        var request = ctx.bodyAsClass(CourseAudiences.class);
        courses.setAudiences(
                session.stationId(), courseId, audienceOr(request.registration()), audienceOr(request.view()));
        ctx.json(detailOf(session, courses.requireCourse(session.stationId(), courseId)));
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/parts",
            methods = HttpMethod.POST,
            summary = "Make a one-time appointment a part of a course",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CoursePartRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventCoursePart.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void addPart(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CoursePartRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(courses.addPart(
                        session.stationId(), pathInt(ctx, "courseId"), request.eventId(), request.setId()));
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/parts/{eventId}",
            methods = HttpMethod.PUT,
            summary = "Move a part into another set of its course",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "courseId", type = Integer.class, required = true),
                @OpenApiParam(name = "eventId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CoursePartMoveRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventCoursePart.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void movePart(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CoursePartMoveRequest.class);
        ctx.json(courses.movePart(
                session.stationId(), pathInt(ctx, "courseId"), pathInt(ctx, "eventId"), request.setId()));
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/parts/{eventId}",
            methods = HttpMethod.DELETE,
            summary = "Make a part an appointment of its own again",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "courseId", type = Integer.class, required = true),
                @OpenApiParam(name = "eventId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void removePart(Context ctx) {
        StationSession session = StationSession.from(ctx);
        courses.removePart(session.stationId(), pathInt(ctx, "courseId"), pathInt(ctx, "eventId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/sign-up",
            methods = HttpMethod.POST,
            summary = "Sign the reader, or somebody they look after, up for a course",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CourseSignUpRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = CourseSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void signUp(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CourseSignUpRequest.class);
        var course = visibleCourse(ctx, session);
        int memberId = targetMember(session, request.memberId());
        boolean override = overrides(session, memberId);
        requireMayJoin(session, course.id(), memberId, override);
        signUps.register(
                session.stationId(), course.id(), memberId, partsOf(request), createdBy(session, memberId), override);
        ctx.status(HttpStatus.CREATED).json(signUpOf(session, course.id(), memberId));
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/sign-up",
            methods = HttpMethod.PUT,
            summary = "Change the parts chosen with a course sign-up",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CourseSignUpRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void changeParts(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CourseSignUpRequest.class);
        var course = visibleCourse(ctx, session);
        int memberId = targetMember(session, request.memberId());
        boolean override = overrides(session, memberId);
        requireMayJoin(session, course.id(), memberId, override);
        signUps.changeParts(
                session.stationId(), course.id(), memberId, partsOf(request), createdBy(session, memberId), override);
        ctx.json(signUpOf(session, course.id(), memberId));
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/sign-up",
            methods = HttpMethod.DELETE,
            summary = "Withdraw a course sign-up with every part chosen",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "memberId", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseSignUp.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void withdraw(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var course = courses.requireCourse(session.stationId(), pathInt(ctx, "courseId"));
        var asked = ctx.queryParam("memberId");
        int memberId = targetMember(session, asked == null ? null : Integer.valueOf(asked));
        signUps.withdraw(session.stationId(), course.id(), memberId);
        ctx.json(signUpOf(session, course.id(), memberId));
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/sign-ups",
            methods = HttpMethod.GET,
            summary = "Every sign-up of a course, with the parts chosen and the sets each falls short of",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseSignUp[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listSignUps(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var course = courses.requireCourse(session.stationId(), pathInt(ctx, "courseId"));
        var standing = signUps.findByCourse(course.id()).stream()
                .filter(signUp -> signUp.active() || signUp.status() == RegistrationStatus.DENIED)
                .map(EventCourseRegistration::memberId)
                .toList();
        ctx.json(signUps.standings(course.id(), standing).stream()
                .map(entry -> signUpOf(session.stationId(), entry, true))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/sign-ups/{memberId}/status",
            methods = HttpMethod.PUT,
            summary = "Accept or turn down a course sign-up and every part chosen with it",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "courseId", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CourseDecisionRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void decide(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int courseId = pathInt(ctx, "courseId");
        int memberId = pathInt(ctx, "memberId");
        var request = ctx.bodyAsClass(CourseDecisionRequest.class);
        signUps.decide(session.stationId(), courseId, memberId, request.status());
        ctx.json(signUpOf(session, courseId, memberId));
    }

    private EventCourse visibleCourse(Context ctx, StationSession session) {
        var course = courses.requireCourse(session.stationId(), pathInt(ctx, "courseId"));
        if (!mayOpen(session, course.id())) throw EventRefusal.COURSE_NOT_HERE.raise();
        return course;
    }

    /** Whether the reader may open a course: it is meant for them or somebody they look after. */
    private boolean mayOpen(StationSession session, int courseId) {
        return seesEverything(session)
                || courses.canSeeAny(
                        courseId,
                        guardianPolicy.household(session.user()),
                        session.user().permissions());
    }

    private static boolean seesEverything(StationSession session) {
        return session.hasPermission(StationPermission.EVENT_EDIT)
                || session.hasPermission(StationPermission.EVENT_MANAGER)
                || session.hasPermission(StationPermission.EVENT_REGISTRATION);
    }

    private void requireMayJoin(StationSession session, int courseId, int memberId, boolean override) {
        if (!override && !courses.canSignUp(courseId, memberId, session.user().permissions())) {
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
        if (!courses.isMemberHere(session.stationId(), requested)) throw EventRefusal.COURSE_MEMBER_NOT_HERE.raise();
        return requested;
    }

    private static boolean overrides(StationSession session, int memberId) {
        return memberId != session.member().id() && session.hasPermission(StationPermission.EVENT_MANAGER);
    }

    private static @Nullable Integer createdBy(StationSession session, int memberId) {
        return memberId != session.member().id() ? session.member().id() : null;
    }

    private static Set<Integer> partsOf(CourseSignUpRequest request) {
        return Set.copyOf(Objects.requireNonNullElse(request.parts(), List.of()));
    }

    private static RestrictionAudience audienceOr(@Nullable RestrictionAudience audience) {
        return audience == null ? RestrictionAudience.empty() : audience;
    }

    private CourseDetail detailOf(StationSession session, EventCourse course) {
        var permissions = session.user().permissions();
        var household = guardianPolicy.household(session.user()).stream()
                .filter(memberId -> courses.canSee(course.id(), memberId, permissions))
                .toList();
        return new CourseDetail(
                CourseSummary.of(course, courses.findParts(course.id()).size(), signUps.holding(course.id())),
                courses.findSets(course.id()),
                signUps.partStates(course.id()),
                courses.registrationAudience(course.id()),
                courses.viewAudience(course.id()),
                signUps.standings(course.id(), household).stream()
                        .map(entry -> signUpOf(
                                session.stationId(),
                                entry,
                                courses.canSignUp(course.id(), entry.memberId(), permissions)))
                        .toList());
    }

    private CourseSignUp signUpOf(StationSession session, int courseId, int memberId) {
        var entry = signUps.standings(courseId, List.of(memberId)).getFirst();
        return signUpOf(
                session.stationId(),
                entry,
                courses.canSignUp(courseId, memberId, session.user().permissions()));
    }

    private CourseSignUp signUpOf(int stationId, CourseStanding entry, boolean mayRegister) {
        var signUp = entry.signUp();
        return new CourseSignUp(
                identities.local(stationId, entry.memberId()),
                signUp == null ? null : signUp.status(),
                signUp == null ? null : signUp.registeredAt(),
                entry.counted().stream().sorted().toList(),
                entry.sets(),
                mayRegister);
    }

    /**
     * A course as lists show it.
     *
     * @param id                   the course
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
    public record CourseSummary(
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
        static CourseSummary of(EventCourse course, int partCount, int holding) {
            return new CourseSummary(
                    course.id(),
                    course.name(),
                    course.description(),
                    course.registrationLimit(),
                    course.registrationDeadline(),
                    course.requiresConfirmation(),
                    course.cancelled(),
                    course.cancelReason(),
                    partCount,
                    holding);
        }
    }

    /**
     * A course with everything its page shows.
     *
     * @param course       the course
     * @param sets         its sets in order
     * @param parts        its parts in date order
     * @param registration who may sign up
     * @param view         who may see it
     * @param household    the sign-up of the reader and of everybody they look after who may see it
     */
    public record CourseDetail(
            CourseSummary course,
            List<EventCourseSet> sets,
            List<EventCoursePartState> parts,
            RestrictionAudience registration,
            RestrictionAudience view,
            List<CourseSignUp> household) {}

    /**
     * One member's standing in a course.
     *
     * @param member       the member
     * @param status       the sign-up's status, or null where the member is not signed up
     * @param registeredAt when they signed up, or null
     * @param parts        the parts that count for the rules, in id order
     * @param sets         how those parts stand against every set
     * @param mayRegister  whether the member is one of the people the course takes
     */
    public record CourseSignUp(
            MemberIdentity member,
            @Nullable RegistrationStatus status,
            @Nullable Instant registeredAt,
            List<Integer> parts,
            List<SetResult> sets,
            boolean mayRegister) {}

    /**
     * A new course.
     *
     * @param details      what it holds
     * @param sets         its sets in order, at least one
     * @param registration who may sign up, or null for everybody who may see it
     * @param view         who may see it, or null for everybody
     */
    public record CourseCreateRequest(
            EventCourseDetails details,
            @Nullable List<EventCourseSetDraft> sets,
            @Nullable RestrictionAudience registration,
            @Nullable RestrictionAudience view) {}

    /** @param details what the course holds from now on */
    public record CourseDetailsRequest(EventCourseDetails details) {}

    /** @param sets the sets in their new order */
    public record CourseSetsRequest(@Nullable List<EventCourseSetDraft> sets) {}

    /**
     * Who may sign up for a course and who may see it.
     *
     * @param registration who may sign up, or null for everybody who may see it
     * @param view         who may see it, or null for everybody
     */
    public record CourseAudiences(
            @Nullable RestrictionAudience registration,
            @Nullable RestrictionAudience view) {}

    /**
     * An appointment to become a part.
     *
     * @param eventId the appointment
     * @param setId   the set it goes into
     */
    public record CoursePartRequest(int eventId, int setId) {}

    /** @param setId the set the part moves into */
    public record CoursePartMoveRequest(int setId) {}

    /**
     * A sign-up or a change of parts.
     *
     * @param memberId who signs up, or null for the reader
     * @param parts    the chosen parts
     */
    public record CourseSignUpRequest(
            @Nullable Integer memberId, @Nullable List<Integer> parts) {}

    /** @param status {@code ACCEPTED} or {@code DENIED} */
    public record CourseDecisionRequest(RegistrationStatus status) {}
}
