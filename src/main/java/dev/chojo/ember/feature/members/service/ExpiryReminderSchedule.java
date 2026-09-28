/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.members.entity.ExpirySettings;
import dev.chojo.ember.feature.members.entity.SentExpiryReminder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which reminder one expiry date owes on one day.
 *
 * <p>A reminder is due once its day has come and nothing is recorded for it. Its days are the ones
 * the field names before the date, and the first day after the last valid one, which always gets a
 * reminder. Where the field repeats, a further reminder is due the set number of days after the last
 * reminder about the passed date actually went out, so a late one does not bring the next one forward.
 *
 * <p>At most one reminder goes out per sweep: the nearest one owed, with every earlier one owed
 * recorded as done beside it. That catches up after a sweep was missed without sending a stack, and a
 * day added to the field later reaches everybody already inside it.
 */
public final class ExpiryReminderSchedule {
    private ExpiryReminderSchedule() {}

    /**
     * A reminder owed.
     *
     * @param remindOn the day of the reminder that goes out
     * @param done     every day owed, the one going out among them, to be recorded as done
     */
    public record Due(LocalDate remindOn, List<LocalDate> done) {}

    /**
     * The reminder one date owes today, if any.
     *
     * @param expiresOn the last valid day
     * @param settings  the field's settings
     * @param today     the day it is, on the station's clock
     * @param sent      the reminders already done with for this member, field and date
     * @param zone      the station's clock, which says on which day a reminder went out
     * @return the reminder owed, or nothing where none is
     */
    public static Optional<Due> due(
            LocalDate expiresOn,
            ExpirySettings settings,
            LocalDate today,
            Collection<SentExpiryReminder> sent,
            ZoneId zone) {
        Set<LocalDate> recorded =
                sent.stream().map(SentExpiryReminder::reminderDate).collect(Collectors.toSet());
        var owed = new ArrayList<LocalDate>();
        for (int daysBefore : settings.reminderDays()) {
            owe(owed, expiresOn.minusDays(daysBefore), today, recorded);
        }
        owe(owed, expiresOn.plusDays(1), today, recorded);
        if (settings.repeatEveryDays() != null) {
            lastAfter(expiresOn, sent, zone)
                    .ifPresent(last -> owe(owed, last.plusDays(settings.repeatEveryDays()), today, recorded));
        }
        return owed.stream()
                .max(Comparator.naturalOrder())
                .map(nearest ->
                        new Due(nearest, owed.stream().distinct().sorted().toList()));
    }

    private static void owe(List<LocalDate> owed, LocalDate day, LocalDate today, Set<LocalDate> recorded) {
        if (!day.isAfter(today) && !recorded.contains(day)) owed.add(day);
    }

    /** The day the last reminder about the passed date went out, on the station's clock. */
    private static Optional<LocalDate> lastAfter(
            LocalDate expiresOn, Collection<SentExpiryReminder> sent, ZoneId zone) {
        return sent.stream()
                .filter(reminder -> reminder.reminderDate().isAfter(expiresOn))
                .map(reminder -> reminder.sentAt().atZone(zone).toLocalDate())
                .max(Comparator.naturalOrder());
    }
}
