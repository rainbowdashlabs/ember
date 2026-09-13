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
import dev.chojo.ember.feature.inventory.entity.FlowProblem;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlow;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.service.MovementFlowService;
import dev.chojo.ember.feature.inventory.service.MovementFlowService.ChosenLanding;
import dev.chojo.ember.feature.inventory.service.MovementTargeting;
import dev.chojo.ember.feature.members.entity.StationMember;
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
import java.util.Locale;

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
    private final MovementTargeting targeting;

    @Inject
    public MovementFlowRoutes(MovementFlowService flowService, MovementTargeting targeting) {
        this.flowService = flowService;
        this.targeting = targeting;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/movement-flows", this::list, StationPermission.INVENTORY_MANAGER);
        // Before the one that takes an id, or the literal would never be reached.
        routes.get(prefix + "/movement-flows/resolve", this::resolve, StationPermission.USER);
        routes.get(prefix + "/movement-flows/{id}", this::getFlow, StationPermission.INVENTORY_MANAGER);
        routes.post(prefix + "/movement-flows", this::createFlow, StationPermission.INVENTORY_MANAGER);
        routes.put(prefix + "/movement-flows/{id}", this::renameFlow, StationPermission.INVENTORY_MANAGER);
        routes.delete(prefix + "/movement-flows/{id}", this::archiveFlow, StationPermission.INVENTORY_MANAGER);
        routes.post(prefix + "/movement-flows/{id}/steps", this::addStep, StationPermission.INVENTORY_MANAGER);
        routes.put(prefix + "/movement-flows/{id}/step-order", this::reorderSteps, StationPermission.INVENTORY_MANAGER);
        routes.get(
                prefix + "/movement-flows/{id}/restore/plan", this::restorePlan, StationPermission.INVENTORY_MANAGER);
        routes.post(
                prefix + "/movement-flows/{id}/restore", this::restoreToPreset, StationPermission.INVENTORY_MANAGER);
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

    /**
     * Which chain a movement would walk, before anybody starts one.
     *
     * <p>What the wizard shows on its last step. Open to any member, because a member raising an
     * exchange of their own gear is shown their own chain: the answer names the steps of a movement they
     * are about to be part of, and nothing about any other.
     *
     * <p>A combination nothing is bound to answers {@code 404} with a named error rather than the
     * service's refusal, because the wizard has a screen for that case and a link to where a chain is
     * written.
     */
    @OpenApi(
            path = "/api/v1/movement-flows/resolve",
            methods = HttpMethod.GET,
            summary = "The chain a movement with these ends would walk",
            tags = {"Inventory"},
            queryParams = {
                @OpenApiParam(name = "purpose", type = MovementPurpose.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class),
                @OpenApiParam(name = "itemId", type = Integer.class),
                @OpenApiParam(name = "inventoryId", type = Integer.class)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlowPreview.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void resolve(Context ctx) {
        UserSession session = UserSession.from(ctx);
        MovementPurpose purpose = purposeOf(ctx.queryParam("purpose"));
        Integer memberId = optionalInt(ctx, "memberId");
        Integer itemId = optionalInt(ctx, "itemId");
        Integer inventoryId = optionalInt(ctx, "inventoryId");

        // Which end the one picked piece sits on follows from the purpose: what leaves on a return or an
        // exchange, what arrives on an issue, and neither on a request.
        boolean itemLeaves = purpose == MovementPurpose.RETURN || purpose == MovementPurpose.EXCHANGE;
        Integer outgoing = itemLeaves ? itemId : null;
        Integer incoming = itemLeaves ? null : itemId;

        MovementTargeting.Target target;
        try {
            target = targeting.resolve(session.stationId(), purpose, memberId, outgoing, incoming, inventoryId);
        } catch (BadRequestResponse refused) {
            throw new NotFoundResponse("NO_FLOW");
        }
        MovementFlow flow = flowService.findFlow(target.flowId()).orElseThrow(() -> new NotFoundResponse("NO_FLOW"));
        ctx.json(new FlowPreview(toResponse(flow), target.ownerKind(), target.party()));
    }

    /**
     * The purpose a query asks about, read by hand because an enum in a query string has no converter
     * of its own and asking for one answers a wrong spelling with a fault rather than with a refusal.
     */
    private MovementPurpose purposeOf(String written) {
        if (written == null || written.isBlank()) throw new BadRequestResponse("purpose is required");
        try {
            return MovementPurpose.valueOf(written.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("%s is not a purpose".formatted(written));
        }
    }

    private Integer optionalInt(Context ctx, String name) {
        String written = ctx.queryParam(name);
        if (written == null || written.isBlank()) return null;
        try {
            return Integer.valueOf(written.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("%s is not a number".formatted(name));
        }
    }

    @OpenApi(
            path = "/api/v1/movement-flows/{id}",
            methods = HttpMethod.GET,
            summary = "Read one flow with its steps",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlowResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getFlow(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(flowAsItStands(requireOwnFlow(pathInt(ctx, "id"), session)));
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
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlowResponse.class)))
    private void archiveFlow(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = requireOwnFlow(pathInt(ctx, "id"), session);
        if (!flowService.archiveFlow(id)) throw new NotFoundResponse();
        ctx.json(flowAsItStands(id));
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
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlowResponse.class)))
    private void updateStep(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int stepId = pathInt(ctx, "id");
        int flowId = requireOwnStep(stepId, session);
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
        ctx.json(flowAsItStands(flowId));
    }

    @OpenApi(
            path = "/api/v1/movement-flow-steps/{id}",
            methods = HttpMethod.DELETE,
            summary = "Retire a step, which keeps it readable for the movements that passed it",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlowResponse.class)))
    private void archiveStep(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int stepId = pathInt(ctx, "id");
        int flowId = requireOwnStep(stepId, session);
        if (!flowService.archiveStep(stepId)) throw new NotFoundResponse();
        ctx.json(flowAsItStands(flowId));
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
                .map(b -> new BindingResponse(b.inventoryId(), b.ownerKind(), b.purpose(), b.party(), b.flowId()))
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
                session.stationId(),
                request.inventoryId(),
                request.ownerKind(),
                request.purpose(),
                request.party() != null ? request.party() : MovementParty.STORE,
                request.flowId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Puts the steps of a chain in the order they are to be walked.
     *
     * <p>The whole order in one call rather than a move per step: two calls in flight would leave
     * the chain reading as something nobody wrote.
     */
    @OpenApi(
            path = "/api/v1/movement-flows/{id}/step-order",
            methods = HttpMethod.PUT,
            summary = "Put the steps of a flow in the order they are walked",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StepOrderRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlowResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reorderSteps(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int flowId = requireOwnFlow(pathInt(ctx, "id"), session);
        var request = ctx.bodyAsClass(StepOrderRequest.class);
        if (request.stepIds() == null || request.stepIds().isEmpty()) {
            throw new BadRequestResponse("Name the steps in the order they are to be walked");
        }
        flowService.reorderSteps(flowId, request.stepIds());
        ctx.json(flowAsItStands(flowId));
    }

    /**
     * Says what writing this chain again would do, without doing any of it.
     *
     * <p>What is asked before the restore is asked for. A chain written again takes the movements
     * standing on it with it, and where one of them lands is a question the preset answers only where
     * it says once what the old step said. The rest are for somebody to answer, and this is the page
     * they answer on.
     */
    @OpenApi(
            path = "/api/v1/movement-flows/{id}/restore/plan",
            methods = HttpMethod.GET,
            summary = "Report what writing a flow again from its preset would do",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        content = @OpenApiContent(from = MovementFlowService.RestorePlan.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void restorePlan(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(flowService.planRestore(requireOwnFlow(pathInt(ctx, "id"), session)));
    }

    /**
     * Writes a chain again as the preset for its combination says it goes.
     *
     * <p>The body says where the movements the preset does not answer for are to land, and a chain
     * whose movements it does answer for is written again with no body at all, which is what keeps
     * the one-click case one click.
     *
     * <p>Answers with the chain as it now stands, the way every other change to one does, so the page
     * shows what it was given back rather than having to ask again.
     */
    @OpenApi(
            path = "/api/v1/movement-flows/{id}/restore",
            methods = HttpMethod.POST,
            summary = "Write a flow again from the preset for the combination it is bound to",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RestoreRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlowResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void restoreToPreset(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int flowId = requireOwnFlow(pathInt(ctx, "id"), session);
        var request = ctx.body().isBlank() ? null : ctx.bodyAsClass(RestoreRequest.class);
        var mappings = request == null || request.mappings() == null ? List.<ChosenLanding>of() : request.mappings();
        flowService.restoreToPreset(
                flowId, session.memberOpt().map(StationMember::id).orElse(null), mappings);
        ctx.json(flowAsItStands(flowId));
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

    /** The chain a step belongs to, once it is established that the station may touch it. */
    private int requireOwnStep(int stepId, UserSession session) {
        MovementFlowStep step = flowService.findStep(stepId).orElseThrow(NotFoundResponse::new);
        return requireOwnFlow(step.flowId(), session);
    }

    /** The chain as it now stands, which is what every change to it answers with. */
    private FlowResponse flowAsItStands(int flowId) {
        return toResponse(flowService.findFlow(flowId).orElseThrow(NotFoundResponse::new));
    }

    private FlowResponse toResponse(MovementFlow flow) {
        return new FlowResponse(
                flow.id(),
                flow.name(),
                flow.purpose(),
                flow.archived(),
                flow.clusterId() != null,
                flowService.problemOf(flow.id()).orElse(null),
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

    public record BindingRequest(
            Integer inventoryId, ItemOwner ownerKind, MovementPurpose purpose, MovementParty party, int flowId) {}

    /**
     * @param stepIds every active step of the chain, in the order they are to be walked
     */
    public record StepOrderRequest(List<Integer> stepIds) {}

    /**
     * @param mappings where the movements standing on the chain are to land, each naming a step of
     *                 the preset by its place in it. A movement the preset answers for on its own
     *                 needs no entry, so this may be empty and the body left out altogether
     */
    public record RestoreRequest(List<ChosenLanding> mappings) {}

    /**
     * @param ownedByCluster whether the flow belongs to the body above the station rather than to
     *                       the station, in which case it is shown and named but not edited here
     * @param problem        what stops this chain from being walked, or null when nothing does. A
     *                       chain under construction says so here rather than only when somebody
     *                       tries to use it, and it is named rather than worded so the reader is
     *                       told in their own language
     */
    public record FlowResponse(
            int id,
            String name,
            MovementPurpose purpose,
            boolean archived,
            boolean ownedByCluster,
            FlowProblem problem,
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

    public record BindingResponse(
            Integer inventoryId, ItemOwner ownerKind, MovementPurpose purpose, MovementParty party, int flowId) {}

    /**
     * The chain a movement would walk, and what it would be about.
     *
     * @param flow      the chain with its steps, in the shape the settings page already reads
     * @param ownerKind whose gear it would be, which is what names the owner's column on the diagram
     * @param party     the end that is not the owner, a member or the station's store
     */
    public record FlowPreview(FlowResponse flow, ItemOwner ownerKind, MovementParty party) {}
}
