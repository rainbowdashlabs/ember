/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.format.DateTimeFormatter;

/**
 * How a repeating appointment is written down for a calendar.
 *
 * <p>One place rather than one per feed: the station's own calendar file and the public one said the
 * same thing twice, so an appointment that runs out said it in the one that had been remembered and
 * repeated for ever in the other.
 */
public final class EventRecurrence {

    private static final String[] DAYS = {"", "MO", "TU", "WE", "TH", "FR", "SA", "SU"};
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("yyyyMMdd");

    private EventRecurrence() {}

    /**
     * The repetition rule of an appointment, in the wording a calendar reads.
     *
     * @param event the appointment
     * @return the rule, or null where the appointment does not repeat on a rule a calendar can state
     */
    public static @Nullable String rule(StationEvent event) {
        if (!event.isRecurring() || event.dayOfWeek() == null) return null;
        String day = DAYS[event.dayOfWeek()];
        String rule =
                switch (event.eventType()) {
                    case RECURRING -> "FREQ=WEEKLY;BYDAY=" + day;
                    case MONTHLY_FIRST -> "FREQ=MONTHLY;BYDAY=1" + day;
                    case QUARTERLY -> "FREQ=MONTHLY;INTERVAL=3;BYDAY=1" + day;
                    case YEARLY -> "FREQ=YEARLY";
                    default -> null;
                };
        if (rule == null) return null;
        return event.lastDate()
                .map(last -> rule + ";UNTIL=" + last.format(UNTIL) + "T235959Z")
                .orElse(rule);
    }
}
