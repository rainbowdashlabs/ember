/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds RFC-5545 {@link VEvent}s for the personal iCal feed.
 *
 * <p>The renderer enriches each event with category, recurrence type, registration
 * deadline/limit/status, per-managed-member registration breakdown, a link back to the
 * web UI, and the location of the event (extracted from the first non-empty
 * {@link EventFieldType#LOCATION} field).
 *
 * <p>It is also responsible for the personal visibility rules — see
 * {@link #isVisibleForFeed(StationEvent, Context)}.
 */
@Singleton
public class IcalEventRenderer {
    private final EventFieldService eventFieldService;
    private final NotificationService notificationService;

    @Inject
    public IcalEventRenderer(EventFieldService eventFieldService, NotificationService notificationService) {
        this.eventFieldService = eventFieldService;
        this.notificationService = notificationService;
    }

    /**
     * The data the renderer needs for every event in a single feed render.
     *
     * @param station                the station that owns the events
     * @param locale                 the resolved feed locale ({@code de}/{@code en})
     * @param baseUrl                the public base URL of the deployment, used in
     *                               {@code URL} and the trailing link in {@code DESCRIPTION}
     * @param verbose                when {@code false} only the headline + link are rendered
     * @param categoryMap            event category lookup
     * @param ownerStatusByEvent     the feed owner's registration status per event id
     * @param ownerRegisteredEvents  event ids the owner is registered for (any status)
     * @param managedStatusByEvent   list of managed-member registrations per event id (name +
     *                               status), in display-name order
     */
    public record Context(
            Station station,
            String locale,
            String baseUrl,
            boolean verbose,
            Map<Integer, EventCategory> categoryMap,
            Map<Integer, RegistrationStatus> ownerStatusByEvent,
            java.util.Set<Integer> ownerRegisteredEvents,
            Map<Integer, List<ManagedRegistration>> managedStatusByEvent) {}

    /**
     * Registration of a managed member (e.g. a guardian's child) for the event.
     */
    public record ManagedRegistration(String memberName, RegistrationStatus status) {}

    /**
     * Personal visibility decision per the feed spec. Hides an event when every relevant
     * registration is {@code DECLINED} / {@code DENIED}; keeps it otherwise.
     *
     * <ul>
     *   <li>Non-guardian: hide if own status is {@code DECLINED}/{@code DENIED}.</li>
     *   <li>Guardian with no own registration: hide only if every managed member with a
     *       registration is {@code DECLINED}/{@code DENIED}.</li>
     *   <li>Owner + guardian: hide only if both the owner and every managed member are
     *       {@code DECLINED}/{@code DENIED}.</li>
     *   <li>Registration-required + deadline expired + nobody registered: hide.</li>
     *   <li>Cancelled events are kept (with the cancel marker) so calendars stay accurate.</li>
     * </ul>
     */
    public boolean isVisibleForFeed(StationEvent event, Context ctx) {
        var ownStatus = ctx.ownerStatusByEvent().get(event.id());
        var managed = ctx.managedStatusByEvent().getOrDefault(event.id(), List.of());

        boolean ownDeclined = ownStatus == RegistrationStatus.DECLINED || ownStatus == RegistrationStatus.DENIED;
        boolean ownActive = ownStatus == RegistrationStatus.ACCEPTED || ownStatus == RegistrationStatus.PENDING;

        boolean anyManagedActive = managed.stream()
                .anyMatch(r -> r.status() == RegistrationStatus.ACCEPTED || r.status() == RegistrationStatus.PENDING);
        boolean allManagedRefused = !managed.isEmpty()
                && managed.stream()
                        .allMatch(r ->
                                r.status() == RegistrationStatus.DECLINED || r.status() == RegistrationStatus.DENIED);

        if (ownStatus != null && managed.isEmpty()) {
            if (ownDeclined) return false;
        } else if (ownStatus == null && !managed.isEmpty()) {
            if (allManagedRefused) return false;
        } else if (ownStatus != null) {
            if (ownDeclined && allManagedRefused) return false;
            if (ownDeclined && managed.isEmpty()) return false;
        }

        // Registration required + deadline expired + nobody (owner or managed) registered
        if (event.requiresRegistration()
                && event.registrationDeadline() != null
                && event.registrationDeadline().isBefore(Instant.now())
                && !ctx.ownerRegisteredEvents().contains(event.id())
                && !anyManagedActive) {
            return false;
        }

        return true;
    }

    /**
     * Builds the {@link VEvent} for the given event, applying registration metadata, location,
     * and the localised description body. Honours the verbose/images flags from the context.
     */
    public VEvent render(StationEvent event, Context ctx) {
        var start = event.startTime() != null ? event.startTime() : Instant.now();
        var end = event.endTime() != null ? event.endTime() : start;

        String summary = event.cancelled() ? cancelledPrefix(ctx.locale()) + event.name() : event.name();
        var vevent = new VEvent(start, end, summary);
        vevent.add(new Uid("event-" + event.id() + "@ember"));

        if (event.categoryId() != null) {
            var cat = ctx.categoryMap().get(event.categoryId());
            if (cat != null) vevent.add(new Categories(cat.name()));
        }

        if (event.cancelled()) {
            vevent.add(ImmutableStatus.VEVENT_CANCELLED);
        }

        // Load the event's custom fields once and split into a location candidate + the rest.
        var fields = eventFieldService.findByEvent(event.id());
        var location = firstLocation(fields);
        if (location != null) {
            vevent.add(new Location(location));
        }

        String deepLink = ctx.baseUrl() + "/station/events/" + event.id();
        vevent.add(new Url(URI.create(deepLink)));

        if (ctx.verbose()) {
            String description = buildDescription(event, fields, deepLink, ctx);
            if (!description.isBlank()) vevent.add(new Description(description));
        } else {
            // Compact mode: just the description plus a link so users can still open the source.
            String description =
                    (event.description() != null ? event.description().trim() + "\n\n" : "") + deepLink;
            vevent.add(new Description(description.stripTrailing()));
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
            if (rrule != null) vevent.add(new RRule<>(rrule));
        }
        return vevent;
    }

    // -- description body --

    private String buildDescription(StationEvent event, List<EventField> fields, String deepLink, Context ctx) {
        var sb = new StringBuilder();

        if (event.description() != null && !event.description().isBlank()) {
            sb.append(event.description().trim()).append("\n\n");
        }

        if (event.categoryId() != null) {
            var cat = ctx.categoryMap().get(event.categoryId());
            if (cat != null) appendLine(sb, ctx.locale(), "label.category", cat.name());
        }

        String typeLabel = notificationService.resolveLocalized(
                ctx.locale(), "ical", "eventType." + event.eventType().name(), null);
        appendLine(sb, ctx.locale(), "label.eventType", typeLabel);

        // Render custom field values (skip LOCATION — already on the LOCATION property).
        for (var field : fields) {
            if (field.fieldType() == EventFieldType.LOCATION) continue;
            if (field.value() == null || field.value().isBlank()) continue;
            sb.append(field.name()).append(": ").append(field.value().trim()).append("\n");
        }

        if (event.cancelled()) {
            String cancelled =
                    event.cancelReason() != null && !event.cancelReason().isBlank()
                            ? notificationService.resolveLocalized(
                                    ctx.locale(), "ical", "cancelledWithReason", Map.of("reason", event.cancelReason()))
                            : notificationService.resolveLocalized(ctx.locale(), "ical", "cancelled", null);
            sb.append(cancelled).append("\n");
        }

        if (event.requiresRegistration()) {
            sb.append(notificationService.resolveLocalized(ctx.locale(), "ical", "registrationRequired", null))
                    .append("\n");
            if (event.registrationDeadline() != null) {
                appendLine(sb, ctx.locale(), "label.deadline", formatInstant(event.registrationDeadline(), ctx));
            }
            if (event.registrationLimit() != null) {
                appendLine(
                        sb,
                        ctx.locale(),
                        "label.limit",
                        event.registrationLimit().toString());
            }
            var status = ctx.ownerStatusByEvent().get(event.id());
            String statusLabel = notificationService.resolveLocalized(
                    ctx.locale(), "ical", "status." + (status != null ? status.name() : "NONE"), null);
            appendLine(sb, ctx.locale(), "label.status", withSymbol(statusLabel, status));

            // Per-managed-member status, one line each, in stable order.
            var managed = ctx.managedStatusByEvent().getOrDefault(event.id(), List.of());
            int acceptedCount = 0;
            for (var m : managed) {
                String mStatusLabel = notificationService.resolveLocalized(
                        ctx.locale(), "ical", "status." + m.status().name(), null);
                sb.append(m.memberName())
                        .append(": ")
                        .append(withSymbol(mStatusLabel, m.status()))
                        .append("\n");
                if (m.status() == RegistrationStatus.ACCEPTED) acceptedCount++;
            }
            if (acceptedCount > 0) {
                String acceptedLabel =
                        notificationService.resolveLocalized(ctx.locale(), "ical", "label.accepted", null);
                String limit = event.registrationLimit() != null
                        ? event.registrationLimit().toString()
                        : "\u221E";
                sb.append(acceptedLabel)
                        .append(": ")
                        .append(acceptedCount)
                        .append(" / ")
                        .append(limit)
                        .append("\n");
            }
        }

        // Trailing web link so users can open the source even when URL isn't shown by the client.
        String linkLabel = notificationService.resolveLocalized(ctx.locale(), "ical", "label.link", null);
        sb.append("\n").append(linkLabel).append(": ").append(deepLink);

        return sb.toString().stripTrailing();
    }

    // -- helpers --

    private String cancelledPrefix(String locale) {
        return notificationService.resolveLocalized(locale, "ical", "summary.cancelledPrefix", null) + " ";
    }

    private String firstLocation(List<EventField> fields) {
        for (var field : fields) {
            if (field.fieldType() == EventFieldType.LOCATION
                    && field.value() != null
                    && !field.value().isBlank()) {
                return field.value().trim();
            }
        }
        return null;
    }

    private void appendLine(StringBuilder sb, String locale, String labelKey, String value) {
        String label = notificationService.resolveLocalized(locale, "ical", labelKey, null);
        sb.append(label).append(": ").append(value).append("\n");
    }

    private String formatInstant(Instant instant, Context ctx) {
        ZoneId zone = resolveZone(ctx.station());
        var fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag(ctx.locale()))
                .withZone(zone);
        return fmt.format(instant) + " (" + zone.getId() + ")";
    }

    private static ZoneId resolveZone(Station station) {
        if (station.timezone() != null && !station.timezone().isBlank()) {
            try {
                return ZoneId.of(station.timezone());
            } catch (Exception ignored) {
                // fall through to system default
            }
        }
        return ZoneId.systemDefault();
    }

    /**
     * Unicode marker prefixed to status labels so the registration state remains visible in
     * monochrome clients and for users with colour-blindness.
     */
    private static String withSymbol(String label, RegistrationStatus status) {
        if (status == null) return label;
        return switch (status) {
            case ACCEPTED -> "\u2713 " + label;
            case DENIED, DECLINED -> "\u2717 " + label;
            case PENDING -> "\u2026 " + label;
            case WITHDRAWN -> "\u21B6 " + label;
        };
    }

    /**
     * Helper for routes assembling the registration map. Keeps the renderer the single source
     * of truth for which statuses are considered active.
     */
    public static Map<Integer, RegistrationStatus> buildOwnerStatusMap(
            java.util.Collection<dev.chojo.ember.feature.events.entity.EventRegistration> ownerRegistrations) {
        var map = new LinkedHashMap<Integer, RegistrationStatus>();
        for (var r : ownerRegistrations) map.put(r.eventId(), r.status());
        return map;
    }
}
