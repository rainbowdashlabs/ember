/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.CancellationNotice;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventRecurrence;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Builds RFC-5545 {@link VEvent}s for the personal iCal feed.
 *
 * <p>The renderer enriches each event with category, recurrence type, registration
 * deadline/limit/status, per-managed-member registration breakdown, a link back to the
 * web UI, and the location of the event (extracted from the first non-empty
 * {@link FieldType#LOCATION} field).
 *
 * <p>It is also responsible for the personal visibility rules - see
 * {@link #isVisibleForFeed(StationEvent, Context)}.
 */
@Singleton
public class IcalEventRenderer {
    private final EventFieldService eventFieldService;
    private final NotificationText notificationText;

    @Inject
    public IcalEventRenderer(EventFieldService eventFieldService, NotificationText notificationText) {
        this.eventFieldService = eventFieldService;
        this.notificationText = notificationText;
    }

    /**
     * Unicode marker prefixed to status labels so the registration state remains visible in
     * monochrome clients and for users with colour-blindness.
     */
    private static String withSymbol(String label, @Nullable RegistrationStatus status) {
        if (status == null) return label;
        return switch (status) {
            case ACCEPTED -> "✓ " + label;
            case DENIED, DECLINED -> "✗ " + label;
            case PENDING -> "… " + label;
            case WITHDRAWN -> "↶ " + label;
        };
    }

    /**
     * Whether a status means the member will not be there: turned down, declined, or a confirmed
     * place taken back again.
     */
    private static boolean notAttending(RegistrationStatus status) {
        return status == RegistrationStatus.DECLINED
                || status == RegistrationStatus.DENIED
                || status == RegistrationStatus.WITHDRAWN;
    }

    /**
     * Whether this appointment belongs in the reader's own calendar.
     *
     * <ul>
     *   <li>Non-guardian: hide if their own answer means they are not going.</li>
     *   <li>Guardian with no own registration: hide only if every managed member with a
     *       registration is not going.</li>
     *   <li>Owner + guardian: hide only if neither the owner nor any managed member is going.</li>
     *   <li>Where an appointment asks to be signed up for and the closing date has passed,
     *       only a place actually taken keeps it: an answer still outstanding at that point is
     *       an absence, and a calendar that keeps showing it sends somebody to a drill they are
     *       not on the list for. Until the closing date an outstanding answer keeps it, which is
     *       what the reminder is for.</li>
     *   <li>Cancelled events are kept (with the cancel marker) so calendars stay accurate.</li>
     * </ul>
     */
    public boolean isVisibleForFeed(StationEvent event, Context ctx) {
        var ownStatus = ctx.ownerStatusByEvent().get(event.id());
        var managed = ctx.managedStatusByEvent().getOrDefault(event.id(), List.of());

        boolean ownDeclined = notAttending(ownStatus);

        boolean allManagedRefused = !managed.isEmpty() && managed.stream().allMatch(r -> notAttending(r.status()));

        if (ownStatus != null && managed.isEmpty()) {
            if (ownDeclined) return false;
        } else if (ownStatus == null && !managed.isEmpty()) {
            if (allManagedRefused) return false;
        } else if (ownStatus != null) {
            if (ownDeclined && allManagedRefused) return false;
            if (ownDeclined && managed.isEmpty()) return false;
        }

        Instant deadline = event.registrationDeadline();
        if (!event.requiresRegistration() || deadline == null || !deadline.isBefore(Instant.now())) {
            return true;
        }

        return ownStatus == RegistrationStatus.ACCEPTED
                || managed.stream().anyMatch(r -> r.status() == RegistrationStatus.ACCEPTED);
    }

    /**
     * Builds the entries for the given event, applying registration metadata, location, and the
     * localised description body. Honours the verbose/images flags from the context.
     *
     * <p>An appointment called off as a whole is one entry marked cancelled. A series with single
     * dates called off is its own entry followed by one override per such date, each marked
     * cancelled, so a calendar crosses out those dates and keeps the others.
     *
     * <p>A compact entry carries just the event's text and a link, so the source can still be opened.
     *
     * @return the entries, none for a series that falls on no date at all
     */
    public List<VEvent> render(StationEvent event, Context ctx) {
        var altogether = ctx.calendar().noticeAltogether(event);
        String summary = altogether.isPresent() ? cancelledPrefix(ctx.locale()) + event.name() : event.name();
        var entry = EventRecurrence.entryOf(event, summary, ctx.calendar());
        if (entry.isEmpty()) return List.of();
        var vevent = entry.get();
        vevent.add(new Uid(EventRecurrence.uidOf(event)));

        if (event.categoryId() != null) {
            var cat = ctx.categoryMap().get(event.categoryId());
            if (cat != null) vevent.add(new Categories(cat.name()));
        }

        if (altogether.isPresent()) {
            vevent.add(ImmutableStatus.VEVENT_CANCELLED);
        }

        var fields = eventFieldService.findByEvent(
                event.id(), ctx.calendar().dateInView(event).orElse(null));
        var location = firstLocation(fields);
        if (location != null) {
            vevent.add(new Location(location));
        }

        String deepLink = ctx.baseUrl() + "/station/events/" + event.id() + "?station="
                + ctx.station().uid();
        vevent.add(new Url(URI.create(deepLink)));

        if (ctx.verbose()) {
            String description = buildDescription(event, fields, deepLink, altogether, ctx);
            if (!description.isBlank()) vevent.add(new Description(description));
        } else {
            String text = event.description();
            String description = (text != null ? text.trim() + "\n\n" : "") + deepLink;
            vevent.add(new Description(description.stripTrailing()));
        }

        var entries = new ArrayList<VEvent>();
        entries.add(vevent);
        entries.addAll(cancelledDates(event, ctx));
        return entries;
    }

    /** The overrides for the dates of a series called off one by one, each saying why. */
    private List<VEvent> cancelledDates(StationEvent event, Context ctx) {
        String summary = cancelledPrefix(ctx.locale()) + event.name();
        var overrides = new ArrayList<VEvent>();
        for (var cancellation : ctx.calendar().cancelledDates(event)) {
            EventRecurrence.cancelledDateOf(event, cancellation.eventDate(), summary)
                    .ifPresent(override -> {
                        override.add(new Description(cancelledText(ctx.locale(), cancellation.notice())));
                        overrides.add(override);
                    });
        }
        return overrides;
    }

    /**
     * The verbose description. Location fields are left out, since they already sit on the location
     * property; member fields show the names the reader knows rather than internal ids; and a trailing
     * link opens the source in clients that show no URL.
     */
    private String buildDescription(
            StationEvent event,
            List<AppointmentField> fields,
            String deepLink,
            Optional<CancellationNotice> altogether,
            Context ctx) {
        var sb = new StringBuilder();

        String description = event.description();
        if (description != null && !description.isBlank()) {
            sb.append(description.trim()).append("\n\n");
        }

        if (event.categoryId() != null) {
            var cat = ctx.categoryMap().get(event.categoryId());
            if (cat != null) appendLine(sb, ctx.locale(), "label.category", cat.name());
        }

        String typeLabel = notificationText.resolveLocalized(
                ctx.locale(), "ical", "eventType." + event.eventType().name(), null);
        appendLine(sb, ctx.locale(), "label.eventType", typeLabel);

        for (var field : fields) {
            if (field.fieldType() == FieldType.LOCATION) continue;
            if (field.value() == null || field.value().isBlank()) continue;
            String value = eventFieldService.displayValue(field);
            if (value.isBlank()) continue;
            sb.append(field.name()).append(": ").append(value).append("\n");
        }

        altogether.ifPresent(
                notice -> sb.append(cancelledText(ctx.locale(), notice)).append("\n"));

        if (event.requiresRegistration()) {
            sb.append(notificationText.resolveLocalized(ctx.locale(), "ical", "registrationRequired", null))
                    .append("\n");
            Instant registrationDeadline = event.registrationDeadline();
            if (registrationDeadline != null) {
                appendLine(sb, ctx.locale(), "label.deadline", formatInstant(registrationDeadline, ctx));
            }
            Integer registrationLimit = event.registrationLimit();
            if (registrationLimit != null) {
                appendLine(sb, ctx.locale(), "label.limit", registrationLimit.toString());
            }
            var status = ctx.ownerStatusByEvent().get(event.id());
            String statusLabel = notificationText.resolveLocalized(
                    ctx.locale(), "ical", "status." + (status != null ? status.name() : "NONE"), null);
            appendLine(sb, ctx.locale(), "label.status", withSymbol(statusLabel, status));

            var managed = ctx.managedStatusByEvent().getOrDefault(event.id(), List.of());
            int acceptedCount = 0;
            for (var m : managed) {
                String mStatusLabel = notificationText.resolveLocalized(
                        ctx.locale(), "ical", "status." + m.status().name(), null);
                sb.append(m.memberName())
                        .append(": ")
                        .append(withSymbol(mStatusLabel, m.status()))
                        .append("\n");
                if (m.status() == RegistrationStatus.ACCEPTED) acceptedCount++;
            }
            if (acceptedCount > 0) {
                String acceptedLabel = notificationText.resolveLocalized(ctx.locale(), "ical", "label.accepted", null);
                String limit = registrationLimit != null ? registrationLimit.toString() : "∞";
                sb.append(acceptedLabel)
                        .append(": ")
                        .append(acceptedCount)
                        .append(" / ")
                        .append(limit)
                        .append("\n");
            }
        }

        String linkLabel = notificationText.resolveLocalized(ctx.locale(), "ical", "label.link", null);
        sb.append("\n").append(linkLabel).append(": ").append(deepLink);

        return sb.toString().stripTrailing();
    }

    /** Why something is off, in the reader's language: the check's own sentence, or the manager's reason. */
    private String cancelledText(String locale, CancellationNotice notice) {
        if (notice.cause() == CancellationCause.THRESHOLD) {
            return notificationText.resolveLocalized(locale, "ical", "cancelledTooFewRegistrations", null);
        }
        String reason = notice.reason();
        if (reason != null && !reason.isBlank()) {
            return notificationText.resolveLocalized(locale, "ical", "cancelledWithReason", Map.of("reason", reason));
        }
        return notificationText.resolveLocalized(locale, "ical", "cancelled", null);
    }

    private String cancelledPrefix(String locale) {
        return notificationText.resolveLocalized(locale, "ical", "summary.cancelledPrefix", null) + " ";
    }

    private @Nullable String firstLocation(List<AppointmentField> fields) {
        for (var field : fields) {
            if (field.fieldType() == FieldType.LOCATION
                    && field.value() != null
                    && !field.value().isBlank()) {
                return field.value().trim();
            }
        }
        return null;
    }

    private void appendLine(StringBuilder sb, String locale, String labelKey, String value) {
        String label = notificationText.resolveLocalized(locale, "ical", labelKey, null);
        sb.append(label).append(": ").append(value).append("\n");
    }

    private String formatInstant(Instant instant, Context ctx) {
        ZoneId zone = StationFormat.timezoneOf(ctx.station());
        var fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag(ctx.locale()))
                .withZone(zone);
        return fmt.format(instant) + " (" + zone.getId() + ")";
    }

    /**
     * The data the renderer needs for every event in a single feed render.
     *
     * @param station               the station that owns the events
     * @param locale                the resolved feed locale ({@code de}/{@code en})
     * @param baseUrl               the public base URL of the deployment, used in
     *                              {@code URL} and the trailing link in {@code DESCRIPTION}
     * @param verbose               when {@code false} only the headline + link are rendered
     * @param categoryMap           event category lookup
     * @param ownerStatusByEvent    the feed owner's registration status per event id
     * @param managedStatusByEvent  list of managed-member registrations per event id (name +
     *                              status), in display-name order
     * @param calendar              the station's calendar, which places each series and names the
     *                              dates its breaks take out
     */
    public record Context(
            Station station,
            String locale,
            String baseUrl,
            boolean verbose,
            Map<Integer, EventCategory> categoryMap,
            Map<Integer, RegistrationStatus> ownerStatusByEvent,
            Map<Integer, List<ManagedRegistration>> managedStatusByEvent,
            StationCalendar calendar) {}

    /**
     * Registration of a managed member (e.g. a guardian's child) for the event.
     */
    public record ManagedRegistration(String memberName, RegistrationStatus status) {}
}
