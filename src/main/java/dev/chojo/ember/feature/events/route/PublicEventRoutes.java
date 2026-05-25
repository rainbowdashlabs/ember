/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
public class PublicEventRoutes implements Routes {
    private final EventService eventService;
    private final EventFieldService eventFieldService;
    private final StationRepository stationRepository;

    @Inject
    public PublicEventRoutes(
            EventService eventService, EventFieldService eventFieldService, StationRepository stationRepository) {
        this.eventService = eventService;
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
        String uidParam = ctx.pathParam("stationUid");
        UUID uid;
        try {
            uid = UUID.fromString(uidParam);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Invalid station ID");
        }
        var station = stationRepository.findByUid(uid).orElseThrow(NotFoundResponse::new);
        if (!station.publicCalendarEnabled()) {
            throw new NotFoundResponse();
        }
        return station;
    }

    private boolean isEventPublic(StationEvent event, Map<Integer, EventCategory> categoryMap) {
        // Tri-state: true = force public, false = force hidden, null = inherit from category
        if (event.isPublic() != null) return event.isPublic();
        if (event.categoryId() != null) {
            var cat = categoryMap.get(event.categoryId());
            return cat != null && cat.isPublic();
        }
        return false;
    }

    private void listPublicEvents(Context ctx) {
        var station = resolveStation(ctx);
        var categories = eventService.findCategoriesByStation(station.id());
        var categoryMap = new java.util.HashMap<Integer, EventCategory>();
        for (var cat : categories) categoryMap.put(cat.id(), cat);

        var allEvents = eventService.findByStation(station.id());
        var publicEvents = allEvents.stream()
                .filter(e -> isEventPublic(e, categoryMap))
                .sorted((a, b) -> {
                    var sa = a.startTime() != null ? a.startTime().toString() : "";
                    var sb = b.startTime() != null ? b.startTime().toString() : "";
                    return sa.compareTo(sb);
                })
                .toList();

        var overviewFields = eventFieldService.findOverviewFieldsByEvents(
                publicEvents.stream().map(StationEvent::id).toList());

        ctx.json(publicEvents.stream()
                .map(e -> toPublicResponse(e, categoryMap, overviewFields))
                .toList());
    }

    private void getPublicEvent(Context ctx) {
        var station = resolveStation(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != station.id()) throw new NotFoundResponse();

        var categories = eventService.findCategoriesByStation(station.id());
        var categoryMap = new java.util.HashMap<Integer, EventCategory>();
        for (var cat : categories) categoryMap.put(cat.id(), cat);

        if (!isEventPublic(event, categoryMap)) throw new NotFoundResponse();

        var fields = eventFieldService.findByEvent(id).stream()
                .filter(EventField::isPublic)
                .toList();

        ctx.json(new PublicEventDetail(
                event.id(),
                event.name(),
                event.description(),
                event.eventType() != null ? event.eventType().name() : null,
                event.dayOfWeek(),
                event.startTime(),
                event.endTime(),
                event.categoryId() != null ? categoryMap.getOrDefault(event.categoryId(), null) : null,
                fields));
    }

    private void listPublicCategories(Context ctx) {
        var station = resolveStation(ctx);
        var categories = eventService.findCategoriesByStation(station.id()).stream()
                .filter(EventCategory::isPublic)
                .toList();
        ctx.json(categories);
    }

    private void icalFeed(Context ctx) {
        var station = resolveStation(ctx);
        var categories = eventService.findCategoriesByStation(station.id());
        var categoryMap = new java.util.HashMap<Integer, EventCategory>();
        for (var cat : categories) categoryMap.put(cat.id(), cat);

        var allEvents = eventService.findByStation(station.id());
        var publicEvents =
                allEvents.stream().filter(e -> isEventPublic(e, categoryMap)).toList();

        var calendar = new Calendar();
        calendar.add(new ProdId("-//Ember//Public Calendar//DE"));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        calendar.add(new XProperty("X-WR-CALNAME", station.name()));

        for (var event : publicEvents) {
            var vevent = buildVEvent(event, categoryMap);
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
        if (event.isRecurring() && event.dayOfWeek() != null) {
            String[] days = {"", "MO", "TU", "WE", "TH", "FR", "SA", "SU"};
            String day = days[event.dayOfWeek()];
            String rrule =
                    switch (event.eventType()) {
                        case RECURRING -> "FREQ=WEEKLY;BYDAY=" + day;
                        case MONTHLY_FIRST -> "FREQ=MONTHLY;BYDAY=1" + day;
                        case QUARTERLY -> "FREQ=MONTHLY;INTERVAL=3;BYDAY=1" + day;
                        case YEARLY -> "FREQ=YEARLY";
                        default -> null;
                    };
            if (rrule != null) {
                vevent.add(new RRule<>(rrule));
            }
        }
        return vevent;
    }

    private PublicEventResponse toPublicResponse(
            StationEvent e, Map<Integer, EventCategory> categoryMap, Map<Integer, List<EventField>> overviewFields) {
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
                e.name(),
                e.description(),
                e.eventType() != null ? e.eventType().name() : null,
                e.dayOfWeek(),
                e.startTime(),
                e.endTime(),
                categoryName,
                fields);
    }

    public record PublicEventResponse(
            int id,
            String name,
            String description,
            String eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            String categoryName,
            List<EventField> publicFields) {}

    public record PublicEventDetail(
            int id,
            String name,
            String description,
            String eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            EventCategory category,
            List<EventField> publicFields) {}
}
