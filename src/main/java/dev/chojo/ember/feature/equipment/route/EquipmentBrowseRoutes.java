/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.equipment.repository.EquipmentRecommendationRepository;
import dev.chojo.ember.feature.equipment.service.EquipmentBrowseService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;

/**
 * The browser behind borrowing: what goes with a thing, and what a collected list would still find.
 *
 * <p>Both take {@code INVENTORY_LENDING_REQUEST}, which is what the station already asks of somebody
 * who may borrow from a partner. Nothing here writes anything.
 */
@Singleton
public class EquipmentBrowseRoutes implements Routes {

    private final EquipmentBrowseService browseService;

    @Inject
    public EquipmentBrowseRoutes(EquipmentBrowseService browseService) {
        this.browseService = browseService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/equipment/recommendations",
                this::recommendations,
                StationPermission.INVENTORY_READ,
                StationPermission.INVENTORY_LENDING_REQUEST);
        routes.post(prefix + "/equipment/collected", this::recheck, StationPermission.INVENTORY_LENDING_REQUEST);
    }

    @OpenApi(
            path = "/api/v1/equipment/recommendations",
            methods = HttpMethod.GET,
            summary = "What goes with a piece that has just been picked",
            description =
                    "Everything carrying a word the picked piece carries, across the inventories, then the other pieces filed beside it. Words win where both apply.",
            tags = {"Inventory"},
            queryParams = @OpenApiParam(name = "itemId", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = EquipmentRecommendationRepository.Recommendation[].class)))
    private void recommendations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String raw = ctx.queryParam("itemId");
        if (raw == null || raw.isBlank()) throw new BadRequestResponse("A piece is required");
        int itemId;
        try {
            itemId = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("A piece is required");
        }
        ctx.json(browseService.recommendationsFor(session.stationId(), itemId));
    }

    @OpenApi(
            path = "/api/v1/equipment/collected",
            methods = HttpMethod.POST,
            summary = "Count a collected list again before it is sent",
            description =
                    "Answers per line what it would still find and whether that is fewer than it asked for, and says how many requests the list will turn into. Nothing is held while a list is assembled, so this is the honest step at the end.",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RecheckRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RecheckResponse.class)))
    private void recheck(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(RecheckRequest.class);
        if (body.from() == null) throw new BadRequestResponse("A window is required");
        List<EquipmentBrowseService.CollectedLine> lines = body.lines() == null ? List.of() : body.lines();
        LocalDate to = body.to() == null ? body.from() : body.to();
        ctx.json(new RecheckResponse(
                browseService.recheck(session.stationId(), body.from(), to, lines),
                browseService.stationsInvolved(lines)));
    }

    public record RecheckRequest(LocalDate from, LocalDate to, List<EquipmentBrowseService.CollectedLine> lines) {}

    /**
     * @param stationsInvolved the stations the list will be sent to, one request each
     */
    public record RecheckResponse(List<EquipmentBrowseService.LineCheck> lines, List<Integer> stationsInvolved) {}
}
