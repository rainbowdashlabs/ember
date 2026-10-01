/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * An event as a content block in a news or wiki article shows it to a member of its own station.
 *
 * <p>A block names its event by the public id, because the same block is read on the public blog
 * and on partner stations. Those readers resolve it through the public event list. A member of the
 * owning station resolves it here, which also reaches the events the station keeps to itself, as
 * long as every member may see them: the block shows the same thing to every reader of the article,
 * so an event kept to part of the station is not shown to anybody, not even to those who may see it.
 * Anything else answers 404, so a block cannot be used to learn whether a hidden event exists.
 */
@Singleton
public class EventEmbedRoutes implements Routes {

    private final EventCrudService crudService;
    private final EventCategoryService categoryService;
    private final EventVisibility visibility;

    @Inject
    public EventEmbedRoutes(
            EventCrudService crudService, EventCategoryService categoryService, EventVisibility visibility) {
        this.crudService = crudService;
        this.categoryService = categoryService;
        this.visibility = visibility;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/embed/{uid}", this::get, StationPermission.LOGIN);
        routes.get(
                prefix + "/events/{id}/embed-reference",
                this::reference,
                StationPermission.NEWS_EDIT,
                StationPermission.PAGE_EDIT);
    }

    /**
     * The public id a block names an event by, for an author placing a block about an event they are
     * looking at. Only for an event the author may see and that is kept to nobody in particular: a
     * block shows its event to every reader, so one kept to part of the station has no reference to
     * give, and the announcement is written without an event block.
     */
    @OpenApi(
            path = "/api/v1/events/{id}/embed-reference",
            methods = HttpMethod.GET,
            summary = "The public id an event block names this event by",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EmbedReference.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reference(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        if (event.restricted()) throw Refusal.EVENT_BLOCK_REFERENCE_NOT_HERE.raise();
        var uid = crudService
                .findPublicUidsByIds(event.stationId(), List.of(event.id()))
                .get(event.id());
        if (uid == null) throw Refusal.EVENT_BLOCK_REFERENCE_NOT_HERE.raise();
        ctx.json(new EmbedReference(uid));
    }

    @OpenApi(
            path = "/api/v1/events/embed/{uid}",
            methods = HttpMethod.GET,
            summary = "An event of the reader's station as a content block shows it",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "uid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EmbeddedEvent.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        var session = StationSession.from(ctx);
        var uid = pathUuid(ctx, "uid");
        var event = crudService
                .findOpenByUid(session.stationId(), BlockAudience.MEMBERS, uid)
                .orElseThrow(Refusal.EVENT_BLOCK_APPOINTMENT_NOT_HERE::raise);
        ctx.json(EmbeddedEvent.of(event, categoryName(event)));
    }

    private @Nullable String categoryName(StationEvent event) {
        Integer categoryId = event.categoryId();
        if (categoryId == null) return null;
        return categoryService.findById(categoryId).map(EventCategory::name).orElse(null);
    }

    /**
     * How an event block names an event.
     *
     * @param eventUid the event's public id
     */
    public record EmbedReference(UUID eventUid) {}

    /**
     * What an event block shows of an event.
     *
     * @param id           the event, for the link to its page
     * @param name         its name
     * @param description  its description
     * @param startTime    when it starts
     * @param endTime      when it ends
     * @param cancelled    whether it has been called off
     * @param categoryName the category it is filed under, or null
     */
    public record EmbeddedEvent(
            int id,
            String name,
            @Nullable String description,
            Instant startTime,
            Instant endTime,
            boolean cancelled,
            @Nullable String categoryName) {

        static EmbeddedEvent of(StationEvent event, @Nullable String categoryName) {
            return new EmbeddedEvent(
                    event.id(),
                    event.name(),
                    event.description(),
                    event.startTime(),
                    event.endTime(),
                    event.cancelled(),
                    categoryName);
        }
    }
}
