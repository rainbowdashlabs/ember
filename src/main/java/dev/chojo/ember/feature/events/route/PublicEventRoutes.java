/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventRecurrence;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
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
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class PublicEventRoutes implements Routes {
    private final EventCrudService crudService;
    private final EventCategoryService categoryService;
    private final EventFieldService eventFieldService;
    private final StationRepository stationRepository;

    @Inject
    public PublicEventRoutes(
            EventCrudService crudService,
            EventCategoryService categoryService,
            EventFieldService eventFieldService,
            StationRepository stationRepository) {
        this.crudService = crudService;
        this.categoryService = categoryService;
        this.eventFieldService = eventFieldService;
        this.stationRepository = stationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/public/events/{stationUid}";
        routes.get(base, this::listPublicEvents);
        routes.get(base + "/categories", this::listPublicCategories);
        routes.get(base + "/{id}", this::getPublicEvent);
        routes.get(base + "/feed/ical", this::icalFeed);
    }

    private Station resolveStation(Context ctx) {
        UUID uid = pathUuid(ctx, "stationUid");
        var station = stationRepository.findByUid(uid).orElseThrow(NotFoundResponse::new);
        if (!station.publicCalendarEnabled()) {
            throw new NotFoundResponse();
        }
        return station;
    }

    private Map<Integer, EventCategory> categoryMap(int stationId) {
        var map = new HashMap<Integer, EventCategory>();
        for (var cat : categoryService.findByStation(stationId)) {
            map.put(cat.id(), cat);
        }
        return map;
    }

    /**
     * Resolves the addressed station and its publicly visible events along with the category lookup.
     */
    private PublicEventData loadPublicEvents(Context ctx) {
        var station = resolveStation(ctx);
        var categoryMap = categoryMap(station.id());
        var publicEvents = crudService.findByStation(station.id()).stream()
                .filter(e -> isEventPublic(e, categoryMap))
                .toList();
        return new PublicEventData(station, categoryMap, publicEvents);
    }

    /**
     * Whether an event belongs on the station's public page, which anybody on the internet can read.
     *
     * <p>An event that not even every member may know about never does, whatever the flag says. The
     * flag is a tri-state whose middle value inherits from the category, so without this an event
     * dropped into a public category would be published by a setting nobody made for it.
     */
    private boolean isEventPublic(StationEvent event, Map<Integer, EventCategory> categoryMap) {
        if (event.restricted()) return false;
        // Tri-state: true = force public, false = force hidden, null = inherit from category
        if (event.isPublic() != null) return event.isPublic();
        if (event.categoryId() != null) {
            var cat = categoryMap.get(event.categoryId());
            return cat != null && cat.isPublic();
        }
        return false;
    }

    @OpenApi(
            path = "/api/v1/public/events/{stationUid}",
            methods = HttpMethod.GET,
            summary = "List all public events for a station",
            tags = {"Public Events"},
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicEventResponse[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listPublicEvents(Context ctx) {
        var data = loadPublicEvents(ctx);
        var publicEvents = data.publicEvents().stream()
                .sorted((a, b) -> {
                    var sa = a.startTime() != null ? a.startTime().toString() : "";
                    var sb = b.startTime() != null ? b.startTime().toString() : "";
                    return sa.compareTo(sb);
                })
                .toList();

        var overviewFields = eventFieldService.findOverviewFieldsByEvents(
                publicEvents.stream().map(StationEvent::id).toList());
        var publicUids = crudService.findPublicUidsByIds(
                data.station().id(), publicEvents.stream().map(StationEvent::id).toList());

        ctx.json(publicEvents.stream()
                .map(e -> toPublicResponse(e, data.categoryMap(), overviewFields, publicUids.get(e.id())))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/public/events/{stationUid}/{id}",
            methods = HttpMethod.GET,
            summary = "Get a single public event by ID",
            tags = {"Public Events"},
            pathParams = {
                @OpenApiParam(name = "stationUid", type = String.class, required = true),
                @OpenApiParam(name = "id", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicEventDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getPublicEvent(Context ctx) {
        var station = resolveStation(ctx);
        int id = pathInt(ctx, "id");
        var event = crudService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != station.id()) throw new NotFoundResponse();

        var categoryMap = categoryMap(station.id());

        if (!isEventPublic(event, categoryMap)) throw new NotFoundResponse();

        var fields = eventFieldService.findByEvent(id).stream()
                .filter(EventField::isPublic)
                .toList();

        ctx.json(new PublicEventDetail(
                event.id(),
                event.name(),
                event.description(),
                event.eventType(),
                event.dayOfWeek(),
                event.startTime(),
                event.endTime(),
                event.categoryId() != null ? categoryMap.getOrDefault(event.categoryId(), null) : null,
                fields));
    }

    @OpenApi(
            path = "/api/v1/public/events/{stationUid}/categories",
            methods = HttpMethod.GET,
            summary = "List public event categories for a station",
            tags = {"Public Events"},
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventCategory[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listPublicCategories(Context ctx) {
        var station = resolveStation(ctx);
        var categories = categoryService.findByStation(station.id()).stream()
                .filter(EventCategory::isPublic)
                .toList();
        ctx.json(categories);
    }

    @OpenApi(
            path = "/api/v1/public/events/{stationUid}/feed/ical",
            methods = HttpMethod.GET,
            summary = "Get an iCal feed of public events for a station",
            tags = {"Public Events"},
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "iCal calendar file. Content-Disposition: attachment; filename=\"calendar.ics\"",
                        content = @OpenApiContent(type = "text/calendar")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void icalFeed(Context ctx) {
        var data = loadPublicEvents(ctx);

        var calendar = new Calendar();
        calendar.add(new ProdId("-//Ember//Public Calendar//DE"));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        calendar.add(new XProperty("X-WR-CALNAME", data.station().name()));

        for (var event : data.publicEvents()) {
            var vevent = buildVEvent(event, data.categoryMap());
            calendar.add(vevent);
        }

        ctx.contentType("text/calendar; charset=utf-8");
        ctx.header("Content-Disposition", "attachment; filename=\"calendar.ics\"");
        ctx.result(calendar.toString());
    }

    private VEvent buildVEvent(StationEvent event, Map<Integer, EventCategory> categoryMap) {
        var start = event.startTime() != null ? event.startTime() : Instant.now();
        var end = event.endTime() != null ? event.endTime() : start;
        var vevent = new VEvent(start, end, event.name());
        vevent.add(new Uid("event-" + event.id() + "@ember"));

        if (event.description() != null && !event.description().isBlank()) {
            vevent.add(new Description(event.description()));
        }
        if (event.categoryId() != null) {
            var cat = categoryMap.get(event.categoryId());
            if (cat != null) {
                vevent.add(new Categories(cat.name()));
            }
        }
        String rrule = EventRecurrence.rule(event);
        if (rrule != null) {
            vevent.add(new RRule<>(rrule));
        }
        return vevent;
    }

    private PublicEventResponse toPublicResponse(
            StationEvent e,
            Map<Integer, EventCategory> categoryMap,
            Map<Integer, List<EventField>> overviewFields,
            UUID publicUid) {
        String categoryName = null;
        if (e.categoryId() != null) {
            var cat = categoryMap.get(e.categoryId());
            if (cat != null) categoryName = cat.name();
        }
        var fields = overviewFields.getOrDefault(e.id(), List.of()).stream()
                .filter(EventField::isPublic)
                .toList();
        return new PublicEventResponse(
                e.id(),
                publicUid,
                e.name(),
                e.description(),
                e.eventType(),
                e.dayOfWeek(),
                e.startTime(),
                e.endTime(),
                e.categoryId(),
                categoryName,
                fields);
    }

    /**
     * The addressed station, its category lookup, and its publicly visible events.
     */
    private record PublicEventData(
            Station station, Map<Integer, EventCategory> categoryMap, List<StationEvent> publicEvents) {}

    public record PublicEventResponse(
            int id,
            UUID publicUid,
            String name,
            String description,
            StationEvent.EventType eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            Integer categoryId,
            String categoryName,
            List<EventField> publicFields) {}

    public record PublicEventDetail(
            int id,
            String name,
            String description,
            StationEvent.EventType eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            EventCategory category,
            List<EventField> publicFields) {}
}
