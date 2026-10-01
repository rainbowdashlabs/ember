/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventDateCancellation;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventRecurrence;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.http.Context;
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
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class PublicEventRoutes implements Routes {
    private final EventCrudService crudService;
    private final EventCategoryService categoryService;
    private final EventFieldService eventFieldService;
    private final OccurrenceCalendar occurrenceCalendar;
    private final StationService stationService;

    @Inject
    public PublicEventRoutes(
            EventCrudService crudService,
            EventCategoryService categoryService,
            EventFieldService eventFieldService,
            OccurrenceCalendar occurrenceCalendar,
            StationService stationService) {
        this.crudService = crudService;
        this.categoryService = categoryService;
        this.eventFieldService = eventFieldService;
        this.occurrenceCalendar = occurrenceCalendar;
        this.stationService = stationService;
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
        var station = stationService.findByUid(uid).orElseThrow(Refusal.STATION_NOT_HERE_BEHIND_PUBLIC_CALENDAR::raise);
        if (!station.publicCalendarEnabled()) {
            throw Refusal.PUBLIC_CALENDAR_SWITCHED_OFF.raise();
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
        Boolean isPublic = event.isPublic();
        if (isPublic != null) return isPublic;
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
                publicEvents.stream().map(StationEvent::id).toList(), occurrenceCalendar.datesInView(publicEvents));
        var publicUids = crudService.findPublicUidsByIds(
                data.station().id(), publicEvents.stream().map(StationEvent::id).toList());
        var stationCalendar = occurrenceCalendar.forStation(data.station().id());

        ctx.json(publicEvents.stream()
                .map(e -> toPublicResponse(
                        e, data.categoryMap(), overviewFields, publicUids.get(e.id()), stationCalendar))
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
        var event = crudService.findById(id).orElseThrow(Refusal.PUBLIC_EVENT_NOT_HERE::raise);
        if (event.stationId() != station.id()) throw Refusal.PUBLIC_EVENT_NOT_HERE.raise();

        var categoryMap = categoryMap(station.id());

        if (!isEventPublic(event, categoryMap)) throw Refusal.PUBLIC_EVENT_NOT_HERE.raise();

        var fields = eventFieldService
                .findByEvent(id, occurrenceCalendar.dateInView(event).orElse(null))
                .stream()
                .filter(AppointmentField::isPublic)
                .map(EventField::of)
                .toList();

        var stationCalendar = occurrenceCalendar.forStation(station.id());
        ctx.json(new PublicEventDetail(
                event.id(),
                event.name(),
                event.description(),
                event.eventType(),
                event.dayOfWeek(),
                event.startTime(),
                event.endTime(),
                event.categoryId() != null ? categoryMap.getOrDefault(event.categoryId(), null) : null,
                fields,
                stationCalendar.cancelledAltogether(event),
                cancelledDatesAhead(event, stationCalendar)));
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

        var stationCalendar = occurrenceCalendar.forStation(data.station().id());
        for (var event : data.publicEvents()) {
            buildVEvent(event, data.categoryMap(), stationCalendar).ifPresent(calendar::add);
            for (var cancellation : stationCalendar.cancelledDates(event)) {
                EventRecurrence.cancelledDateOf(event, cancellation.eventDate(), event.name())
                        .ifPresent(calendar::add);
            }
        }

        ctx.contentType("text/calendar; charset=utf-8");
        ctx.header("Content-Disposition", "attachment; filename=\"calendar.ics\"");
        ctx.result(calendar.toString());
    }

    private Optional<VEvent> buildVEvent(
            StationEvent event, Map<Integer, EventCategory> categoryMap, StationCalendar stationCalendar) {
        var entry = EventRecurrence.entryOf(event, event.name(), stationCalendar);
        if (entry.isEmpty()) return Optional.empty();
        var vevent = entry.get();
        vevent.add(new Uid(EventRecurrence.uidOf(event)));
        if (stationCalendar.cancelledAltogether(event)) {
            vevent.add(ImmutableStatus.VEVENT_CANCELLED);
        }

        String description = event.description();
        if (description != null && !description.isBlank()) {
            vevent.add(new Description(description));
        }
        if (event.categoryId() != null) {
            var cat = categoryMap.get(event.categoryId());
            if (cat != null) {
                vevent.add(new Categories(cat.name()));
            }
        }
        return Optional.of(vevent);
    }

    /** The dates of a series from today on that were called off one by one, earliest first. */
    private static List<LocalDate> cancelledDatesAhead(StationEvent event, StationCalendar stationCalendar) {
        LocalDate today = stationCalendar.today();
        return stationCalendar.cancelledDates(event).stream()
                .map(EventDateCancellation::eventDate)
                .filter(date -> !date.isBefore(today))
                .toList();
    }

    private PublicEventResponse toPublicResponse(
            StationEvent e,
            Map<Integer, EventCategory> categoryMap,
            Map<Integer, List<AppointmentField>> overviewFields,
            UUID publicUid,
            StationCalendar stationCalendar) {
        String categoryName = null;
        if (e.categoryId() != null) {
            var cat = categoryMap.get(e.categoryId());
            if (cat != null) categoryName = cat.name();
        }
        var fields = overviewFields.getOrDefault(e.id(), List.of()).stream()
                .filter(AppointmentField::isPublic)
                .map(EventField::of)
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
                fields,
                stationCalendar.cancelledAltogether(e),
                cancelledDatesAhead(e, stationCalendar));
    }

    /**
     * The addressed station, its category lookup, and its publicly visible events.
     */
    private record PublicEventData(
            Station station, Map<Integer, EventCategory> categoryMap, List<StationEvent> publicEvents) {}

    /**
     * An appointment as the public list shows it.
     *
     * @param cancelled      whether it is off as a whole: a series called off, or a one-time
     *                       appointment whose date was
     * @param cancelledDates the dates of a series from today on that were called off one by one
     */
    public record PublicEventResponse(
            int id,
            UUID publicUid,
            String name,
            @Nullable String description,
            StationEvent.EventType eventType,
            @Nullable Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            @Nullable Integer categoryId,
            @Nullable String categoryName,
            List<EventField> publicFields,
            boolean cancelled,
            List<LocalDate> cancelledDates) {}

    /**
     * An appointment as its public page shows it.
     *
     * @param cancelled      whether it is off as a whole: a series called off, or a one-time
     *                       appointment whose date was
     * @param cancelledDates the dates of a series from today on that were called off one by one
     */
    public record PublicEventDetail(
            int id,
            String name,
            @Nullable String description,
            StationEvent.EventType eventType,
            @Nullable Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            @Nullable EventCategory category,
            List<EventField> publicFields,
            boolean cancelled,
            List<LocalDate> cancelledDates) {}
}
