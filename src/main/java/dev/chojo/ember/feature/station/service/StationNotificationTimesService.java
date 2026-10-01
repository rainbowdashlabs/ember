/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.notifications.repository.NotificationScheduleRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * When a station's gathered notifications go out.
 *
 * <p>Empty times mean the station has asked for nothing and the operator's own number decides, which
 * is what every station did before it could choose. The floor is sent along so the screen can say
 * plainly where a station is asking for more often than the installation allows, rather than letting
 * it believe it got what it asked for.
 */
@Singleton
public class StationNotificationTimesService {
    private final NotificationScheduleRepository schedules;
    private final Mailing mailing;

    @Inject
    public StationNotificationTimesService(NotificationScheduleRepository schedules, Mailing mailing) {
        this.schedules = schedules;
        this.mailing = mailing;
    }

    public NotificationSchedulePayload times(int stationId) {
        var schedule = schedules.findDigestGroups(List.of(stationId), List.of()).stream()
                .findFirst()
                .orElseThrow(StationRefusal.NOTIFICATION_TIMES_NOT_SET::raise);
        return new NotificationSchedulePayload(
                schedule.sendTimes().stream().map(LocalTime::toString).toList(),
                mailing.notificationDigestIntervalMinutes());
    }

    /**
     * Sets the times, or gives them back so the operator's number decides again.
     *
     * <p>A time nobody could mean is refused rather than quietly dropped: a station that typed one
     * and was answered with success would believe it had asked for something it had not.
     *
     * @param sendTimes the times of day as the screen sent them, or null for none
     */
    public void update(int stationId, List<String> sendTimes) {
        var times = new ArrayList<LocalTime>();
        for (String raw : sendTimes == null ? List.<String>of() : sendTimes) {
            if (raw == null) throw StationRefusal.NOTIFICATION_TIME_NOT_A_TIME.raise();
            try {
                times.add(LocalTime.parse(raw));
            } catch (DateTimeParseException e) {
                throw StationRefusal.NOTIFICATION_TIME_NOT_A_TIME.raise();
            }
        }
        schedules.setStationSendTimes(stationId, times);
    }

    /**
     * @param sendTimes    the times of day, empty where the operator's number decides
     * @param floorMinutes the shortest gap between two mails the installation allows
     */
    public record NotificationSchedulePayload(List<String> sendTimes, int floorMinutes) {}
}
