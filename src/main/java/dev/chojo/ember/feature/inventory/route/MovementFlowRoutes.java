/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlow;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.service.MovementFlowService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for the flows a station's movements walk, and for the bindings that say which flow applies
 * to which owner and purpose.
 *
 * <p>Configuring flows is a manager's job, so the whole surface sits behind one permission. Which
 * flow a movement then walks is nobody's choice at the time: it is resolved once and pinned.
 */
@Singleton
public class MovementFlowRoutes implements Routes {
    private final MovementFlowService flowService;

    @Inject
    public MovementFlowRoutes(MovementFlowService flowService) {
        this.flowService = flowService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/movement-flows", this::list, StationPermission.INVENTORY_MANAGER);
        routes.post(prefix + "/movement-flows", this::createFlow, StationPermission.INVENTORY_MANAGER);
        routes.put(prefix + "/movement-flows/{id}", this::renameFlow, StationPermission.INVENTORY_MANAGER);
        routes.delete(prefix + "/movement-flows/{id}", this::archiveFlow, StationPermission.INVENTORY_MANAGER);
        routes.post(prefix + "/movement-flows/{id}/steps", this::addStep, StationPermission.INVENTORY_MANAGER);
        routes.put(prefix + "/movement-flow-steps/{id}", this::updateStep, StationPermission.INVENTORY_MANAGER);
        routes.delete(prefix + "/movement-flow-steps/{id}", this::archiveStep, StationPermission.INVENTORY_MANAGER);
        routes.get(prefix + "/movement-flow-bindings", this::listBindings, StationPermission.INVENTORY_MANAGER);
        routes.put(prefix + "/movement-flow-bindings", this::bind, StationPermission.INVENTORY_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/movement-flows",
            methods = HttpMethod.GET,
            summary = "List the station's flows with their steps",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlowResponse[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(flowService.findFlows(session.stationId()).stream()
                .map(this::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/movement-flows",
            methods = HttpMethod.POST,
            summary = "Create a flow",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FlowRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = FlowResponse.class)))
    private void createFlow(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(FlowRequest.class);
        if (request.purpose() == null) throw new BadRequestResponse("purpose is required");
        var flow = flowService.createFlow(session.stationId(), request.name(), request.purpose());
        ctx.status(HttpStatus.CREATED).json(toResponse(flow));
    }

    @OpenApi(
            path = "/api/v1/movement-flows/{id}",
            methods = HttpMethod.PUT,
            summary = "Rename a flow",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FlowRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlowResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void renameFlow(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = requireOwnFlow(pathInt(ctx, "id"), session);
        var request = ctx.bodyAsClass(FlowRequest.class);
        if (!flowService.renameFlow(id, request.name())) throw new NotFoundResponse();
        ctx.json(toResponse(flowService.findFlow(id).orElseThrow(NotFoundResponse::new)));
    }

    @OpenApi(
            path = "/api/v1/movement-flows/{id}",
            methods = HttpMethod.DELETE,
            summary = "Retire a flow, which keeps it readable for the movements that walked it",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void archiveFlow(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = requireOwnFlow(pathInt(ctx, "id"), session);
        if (!flowService.archiveFlow(id)) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/movement-flows/{id}/steps",
            methods = HttpMethod.POST,
            summary = "Add a step to the end of a flow",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StepRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = StepResponse.class)))
    private void addStep(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int flowId = requireOwnFlow(pathInt(ctx, "id"), session);
        var request = ctx.bodyAsClass(StepRequest.class);
        requireStepFields(request);
        var step = flowService.addStep(
                flowId,
                request.label(),
                request.actor(),
                request.subject(),
                request.custodyAfter(),
                request.picksItem());
        ctx.status(HttpStatus.CREATED).json(toStep(step));
    }

    @OpenApi(
            path = "/api/v1/movement-flow-steps/{id}",
            methods = HttpMethod.PUT,
            summary = "Change what a step says",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StepRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void updateStep(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int stepId = pathInt(ctx, "id");
        requireOwnStep(stepId, session);
        var request = ctx.bodyAsClass(StepRequest.class);
        requireStepFields(request);
        if (!flowService.updateStep(
                stepId,
                request.label(),
                request.actor(),
                request.subject(),
                request.custodyAfter(),
                request.picksItem())) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/movement-flow-steps/{id}",
            methods = HttpMethod.DELETE,
            summary = "Retire a step, which keeps it readable for the movements that passed it",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void archiveStep(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int stepId = pathInt(ctx, "id");
        requireOwnStep(stepId, session);
        if (!flowService.archiveStep(stepId)) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/movement-flow-bindings",
            methods = HttpMethod.GET,
            summary = "List which flow applies to which owner and purpose",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BindingResponse[].class)))
    private void listBindings(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(flowService.findBindings(session.stationId()).stream()
                .map(b -> new BindingResponse(b.inventoryId(), b.ownerKind(), b.purpose(), b.flowId()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/movement-flow-bindings",
            methods = HttpMethod.PUT,
            summary = "Point a binding at a flow",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BindingRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void bind(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(BindingRequest.class);
        if (request.ownerKind() == null || request.purpose() == null) {
            throw new BadRequestResponse("ownerKind and purpose are required");
        }
        flowService.bind(
                session.stationId(), request.inventoryId(), request.ownerKind(), request.purpose(), request.flowId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void requireStepFields(StepRequest request) {
        if (request.actor() == null || request.subject() == null || request.custodyAfter() == null) {
            throw new BadRequestResponse("actor, subject and custodyAfter are required");
        }
    }

    /**
     * Answers 404 both when the flow is absent and when it belongs to somebody else, so the two stay
     * indistinguishable from outside.
     */
    private int requireOwnFlow(int flowId, UserSession session) {
        MovementFlow flow = flowService.findFlow(flowId).orElseThrow(NotFoundResponse::new);
        if (flow.stationId() == null || !flow.stationId().equals(session.stationId())) {
            throw new NotFoundResponse();
        }
        return flowId;
    }

    private void requireOwnStep(int stepId, UserSession session) {
        MovementFlowStep step = flowService.findStep(stepId).orElseThrow(NotFoundResponse::new);
        requireOwnFlow(step.flowId(), session);
    }

    private FlowResponse toResponse(MovementFlow flow) {
        return new FlowResponse(
                flow.id(),
                flow.name(),
                flow.purpose(),
                flow.archived(),
                flow.clusterId() != null,
                flowService.findAllSteps(flow.id()).stream().map(this::toStep).toList());
    }

    private StepResponse toStep(MovementFlowStep step) {
        return new StepResponse(
                step.id(),
                step.position(),
                step.label(),
                step.actor(),
                step.subject(),
                step.custodyAfter(),
                step.picksItem(),
                step.archived());
    }

    public record FlowRequest(String name, MovementPurpose purpose) {}

    public record StepRequest(
            String label, StepActor actor, StepSubject subject, ItemCustody custodyAfter, boolean picksItem) {}

    public record BindingRequest(Integer inventoryId, ItemOwner ownerKind, MovementPurpose purpose, int flowId) {}

    /**
     * @param ownedByCluster whether the flow belongs to the body above the station rather than to
     *                       the station, in which case it is shown and named but not edited here
     */
    public record FlowResponse(
            int id,
            String name,
            MovementPurpose purpose,
            boolean archived,
            boolean ownedByCluster,
            List<StepResponse> steps) {}

    public record StepResponse(
            int id,
            int position,
            String label,
            StepActor actor,
            StepSubject subject,
            ItemCustody custodyAfter,
            boolean picksItem,
            boolean archived) {}

    public record BindingResponse(Integer inventoryId, ItemOwner ownerKind, MovementPurpose purpose, int flowId) {}
}
