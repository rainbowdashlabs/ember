/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * When the notifications a station has gathered are mailed out.
 *
 * <p>A station says the times of day it wants to be written to rather than how long its notifications
 * should be gathered for. Seven in the morning means seven in the morning: a length of time says only
 * "no more often than", and leaves the arrival drifting with whenever the server was last restarted.
 * Asking for hourly mail is the same thing said twenty-four times, so there is one rule here and not
 * two.
 *
 * <p>Nothing about this is a decision about who is written to. It answers one question, for a station
 * or a cluster that has something waiting: has one of the moments it asked for gone by since the last
 * time it was written to.
 */
public final class NotificationSchedule {

    private NotificationSchedule() {}

    /**
     * Whether a mail is due.
     *
     * <p>Due where one of the times asked for has gone by since whichever is later of the last mail
     * and the oldest thing waiting to go in it, and where the operator's own floor has passed as well.
     * A station that has never been written to is anchored to its oldest waiting notification instead,
     * so it is first written to at a time it asked for rather than the moment something arrived.
     *
     * <p>Where a station has asked for nothing, the operator's number is the whole rule and the mail
     * goes out on the next look, which is exactly what every station did before any of this existed.
     * Measuring that case from the oldest notification instead would push each mail a little later
     * than the last, since every batch would start its own hour from whenever it happened to begin.
     *
     * @param sendTimes  the times of day this station asked for, empty where it asked for none
     * @param lastSent   when it was last written to, null where it never has been
     * @param oldestWaiting when the oldest notification waiting for it arrived
     * @param zone       the station's own clock, which is what its times are read on
     * @param floor      the shortest gap the operator allows between two mails
     * @param now        the moment being judged
     * @return true where a mail should go out now
     */
    public static boolean isDue(
            List<LocalTime> sendTimes,
            Instant lastSent,
            Instant oldestWaiting,
            ZoneId zone,
            Duration floor,
            Instant now) {
        if (lastSent != null && Duration.between(lastSent, now).compareTo(floor) < 0) return false;
        if (sendTimes == null || sendTimes.isEmpty()) return true;

        Instant passed = latestPassed(sendTimes, zone, now);
        return passed != null && passed.isAfter(anchor(lastSent, oldestWaiting));
    }

    /**
     * What a send time is measured against.
     *
     * <p>The later of the two: measuring only from the last mail would send the moment a station's
     * first notification arrives, and measuring only from the notification would send again for
     * everything that arrived before the mail that already carried it.
     */
    private static Instant anchor(Instant lastSent, Instant oldestWaiting) {
        if (lastSent == null) return oldestWaiting;
        if (oldestWaiting == null) return lastSent;
        return lastSent.isAfter(oldestWaiting) ? lastSent : oldestWaiting;
    }

    /**
     * The most recent of the asked-for times that has already gone by, or null where none has today
     * or yesterday.
     *
     * <p>Yesterday is looked at as well, because a station asking only for seven in the morning is
     * still owed that mail at one in the following morning, and looking only at today would say no
     * time has passed and hold its notifications until seven came round again.
     */
    private static Instant latestPassed(List<LocalTime> sendTimes, ZoneId zone, Instant now) {
        ZonedDateTime here = now.atZone(zone);
        Instant best = null;
        for (LocalTime time : sendTimes) {
            if (time == null) continue;
            for (int dayOffset = 0; dayOffset >= -1; dayOffset--) {
                Instant candidate = here.toLocalDate()
                        .plusDays(dayOffset)
                        .atTime(time)
                        .atZone(zone)
                        .toInstant();
                if (candidate.isAfter(now)) continue;
                if (best == null || candidate.isAfter(best)) best = candidate;
                break;
            }
        }
        return best;
    }

    /** The times a station asking for hourly mail holds, which is the same rule said twenty-four times. */
    public static List<LocalTime> everyHour() {
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(hour -> LocalTime.of(hour, 0))
                .toList();
    }
}
