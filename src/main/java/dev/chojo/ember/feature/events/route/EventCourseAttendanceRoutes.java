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
import dev.chojo.ember.feature.events.service.CourseAttendanceService;
import dev.chojo.ember.feature.events.service.CourseAttendanceService.AttendanceRow;
import dev.chojo.ember.feature.events.service.CourseRules.SetResult;
import dev.chojo.ember.feature.events.service.EventCourseService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.util.CsvWriter;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * How the participants of a course attended its parts, for whoever handles registrations: on the
 * course page and as a spreadsheet.
 */
@Singleton
public class EventCourseAttendanceRoutes implements Routes {
    private static final String COURSE = "/events/courses/{courseId}";

    private final CourseAttendanceService attendance;
    private final EventCourseService courses;
    private final MemberIdentityFactory identities;
    private final MemberNameResolver names;
    private final StationService stations;

    @Inject
    public EventCourseAttendanceRoutes(
            CourseAttendanceService attendance,
            EventCourseService courses,
            MemberIdentityFactory identities,
            MemberNameResolver names,
            StationService stations) {
        this.attendance = attendance;
        this.courses = courses;
        this.identities = identities;
        this.names = names;
        this.stations = stations;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + COURSE + "/attendance",
                this::summary,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
        routes.get(
                prefix + COURSE + "/attendance/export.csv",
                this::exportCsv,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/attendance",
            methods = HttpMethod.GET,
            summary = "How the accepted participants of a course attended its parts",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseAttendance[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void summary(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(attendance.summary(session.stationId(), pathInt(ctx, "courseId")).stream()
                .map(row -> new CourseAttendance(
                        row.memberId(),
                        identities.local(session.stationId(), row.memberId()),
                        row.sets(),
                        row.complete()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/courses/{courseId}/attendance/export.csv",
            methods = HttpMethod.GET,
            summary = "The attendance of a course's participants as a spreadsheet",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "courseId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "separator"),
            responses = @OpenApiResponse(status = "200"))
    private void exportCsv(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var course = courses.requireCourse(session.stationId(), pathInt(ctx, "courseId"));
        var station = stations.findById(session.stationId())
                .orElseThrow(EventRefusal.STATION_NOT_HERE_FOR_REGISTRATION_CSV::raise);
        String language = StationFormat.languageOf(station);
        var rows = attendance.summary(session.stationId(), course.id());
        var headers = new ArrayList<String>();
        headers.add(DocumentWord.MEMBER.in(language));
        courses.findSets(course.id()).forEach(set -> headers.add(set.name() + " (" + set.minimum() + ")"));
        headers.add(DocumentWord.COMPLETE.in(language));
        var lines = rows.stream().map(row -> lineOf(row, language)).toList();
        ctx.contentType("text/csv");
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(
                        SafeContentDisposition.Disposition.ATTACHMENT,
                        DocumentName.of(
                                "csv", DocumentWord.COURSE_ATTENDANCE.in(language), DocumentName.part(course.name()))));
        ctx.result(CsvWriter.write(headers, lines, CsvWriter.Separator.of(ctx.queryParam("separator"))));
    }

    private List<String> lineOf(AttendanceRow row, String language) {
        var line = new ArrayList<String>();
        line.add(names.called(row.memberId()));
        for (SetResult set : row.sets()) {
            line.add(set.chosen() + "/" + set.required());
        }
        line.add((row.complete() ? DocumentWord.YES : DocumentWord.NO).in(language));
        return line;
    }

    /**
     * One participant's attendance as the course page shows it.
     *
     * @param memberId the participant
     * @param member   the participant as they are shown
     * @param sets     how the parts they attended meet every set
     * @param complete whether every set is met
     */
    public record CourseAttendance(int memberId, MemberIdentity member, List<SetResult> sets, boolean complete) {}
}
