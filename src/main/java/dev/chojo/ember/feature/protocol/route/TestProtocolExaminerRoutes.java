/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.protocol.service.TestProtocolExaminerService;
import dev.chojo.ember.feature.protocol.service.TestProtocolExaminerService.GradingScope;
import dev.chojo.ember.feature.protocol.service.TestProtocolExaminerService.SectionExaminers;
import dev.chojo.ember.feature.protocol.service.TestProtocolGuards;
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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Planning the examiners of a test run, and telling a tester what of a run is theirs to grade.
 *
 * <p>Registered ahead of {@link TestProtocolRoutes}, whose {@code /protocols/{id}} would otherwise
 * take the candidate list's fixed path for a protocol's number.
 */
@Singleton
public class TestProtocolExaminerRoutes implements Routes {
    private final TestProtocolExaminerService examiners;
    private final TestProtocolGuards guards;
    private final MemberNameResolver names;

    @Inject
    public TestProtocolExaminerRoutes(
            TestProtocolExaminerService examiners, TestProtocolGuards guards, MemberNameResolver names) {
        this.examiners = examiners;
        this.guards = guards;
        this.names = names;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/protocols/examiner-candidates", this::listCandidates, StationPermission.PROTOCOL_CREATE);
        routes.get(prefix + "/protocols/runs/{id}/examiners", this::listExaminers, StationPermission.PROTOCOL_TESTER);
        routes.put(prefix + "/protocols/runs/{id}/examiners", this::planExaminers, StationPermission.PROTOCOL_CREATE);
        routes.get(
                prefix + "/protocols/runs/{id}/examiners/previous",
                this::previousExaminers,
                StationPermission.PROTOCOL_CREATE);
        routes.get(
                prefix + "/protocols/runs/{id}/grading-scope", this::gradingScope, StationPermission.PROTOCOL_TESTER);
    }

    @OpenApi(
            path = "/api/v1/protocols/examiner-candidates",
            methods = HttpMethod.GET,
            summary = "List the members who may be named examiners: everybody allowed to grade protocols",
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ExaminerCandidate[].class)))
    private void listCandidates(Context ctx) {
        ctx.json(examiners.candidates(StationSession.from(ctx).stationId()).stream()
                .map(member -> new ExaminerCandidate(member.id(), nameOf(member)))
                .sorted(Comparator.comparing(ExaminerCandidate::name, String.CASE_INSENSITIVE_ORDER))
                .toList());
    }

    /**
     * What the menu calls a candidate. A member with an account keeps their name on the account, not
     * on the membership, so the membership's own name is only the last resort: read alone it left
     * every such member without a name, and the menu with nothing to show.
     */
    private String nameOf(StationMember member) {
        return Objects.requireNonNullElse(names.identified(member.id()), member.displayName());
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}/examiners",
            methods = HttpMethod.GET,
            summary = "List the examiners of each section of a test run",
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RunExaminers.class)))
    private void listExaminers(Context ctx) {
        var run =
                guards.requireRun(ctx, ctx.pathParamAsClass("id", Integer.class).get());
        ctx.json(new RunExaminers(examiners.examinersOf(run.id())));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}/examiners",
            methods = HttpMethod.PUT,
            summary = "Replace the examiners of a test run",
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RunExaminers.class)),
            responses = @OpenApiResponse(status = "204"))
    private void planExaminers(Context ctx) {
        var run =
                guards.requireRun(ctx, ctx.pathParamAsClass("id", Integer.class).get());
        examiners.plan(run, ctx.bodyAsClass(RunExaminers.class).sections());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}/examiners/previous",
            methods = HttpMethod.GET,
            summary = "The examiners of the last planned run of the same protocol, as a starting point",
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RunExaminers.class)))
    private void previousExaminers(Context ctx) {
        var run =
                guards.requireRun(ctx, ctx.pathParamAsClass("id", Integer.class).get());
        ctx.json(new RunExaminers(examiners.previousPlan(run)));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}/grading-scope",
            methods = HttpMethod.GET,
            summary = "What the caller may grade in a test run",
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = GradingScope.class)))
    private void gradingScope(Context ctx) {
        var run =
                guards.requireRun(ctx, ctx.pathParamAsClass("id", Integer.class).get());
        ctx.json(examiners.scopeOf(run, StationSession.from(ctx).member().id()));
    }

    /**
     * The examiners of a run, section by section.
     *
     * @param sections one entry per section with examiners of its own
     */
    public record RunExaminers(List<SectionExaminers> sections) {
        public RunExaminers {
            sections = List.copyOf(Objects.requireNonNullElse(sections, List.of()));
        }
    }

    /**
     * A member who may be named an examiner.
     *
     * @param memberId the station member
     * @param name     what the station calls them
     */
    public record ExaminerCandidate(int memberId, String name) {}
}
