/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.UUID;

/**
 * An event as a content block shows it to a member of its own station.
 *
 * <p>A block names its event by the public id, because the same block is read on the public blog
 * and on partner stations. Those readers resolve it through the public event list. A member of the
 * owning station resolves it here, which also reaches the events the station keeps to itself, as
 * long as the reader may see them. Anything else answers 404, so a block cannot be used to learn
 * whether a hidden event exists.
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
        routes.get(prefix + "/events/embed/{uid}", this::get, StationPermission.USER);
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
        var session = UserSession.from(ctx);
        var uid = parseUid(ctx.pathParam("uid"));
        var event = crudService
                .findByPublicUid(session.stationId(), uid)
                .filter(found -> visibility.canSee(session, found))
                .orElseThrow(NotFoundResponse::new);
        ctx.json(EmbeddedEvent.of(event, categoryName(event)));
    }

    private static UUID parseUid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new NotFoundResponse();
        }
    }

    private String categoryName(StationEvent event) {
        if (event.categoryId() == null) return null;
        return categoryService
                .findById(event.categoryId())
                .map(EventCategory::name)
                .orElse(null);
    }

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
            String description,
            Instant startTime,
            Instant endTime,
            boolean cancelled,
            String categoryName) {

        static EmbeddedEvent of(StationEvent event, String categoryName) {
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
