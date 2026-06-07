/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Seeder for demo attendance session data spanning 14 months of history.
 */
@Singleton
public class DemoAttendanceSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoAttendanceSeeder.class);

    private final AttendanceRepository attendanceRepository;

    @Inject
    public DemoAttendanceSeeder(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public void seedAttendanceSessions(
            Random rng,
            AttendanceTemplate templateUebung,
            AttendanceTemplate templateGesamt,
            StationEvent evUebung,
            StationEvent evGesamt,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten,
            List<StationMember> betreuer) {
        var teamForUebung = betreuer.subList(0, Math.min(2, betreuer.size()));
        var teamForGesamt = betreuer.subList(0, Math.min(3, betreuer.size()));

        var teilnehmer = new ArrayList<>(anfaenger);
        teilnehmer.addAll(fortgeschritten);

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(14).withDayOfMonth(1);
        int sessionCount = 0;

        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            int weekOfYear = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            if (weekOfYear >= 28 && weekOfYear <= 33) continue; // summer break

            int dow = date.getDayOfWeek().getValue();
            boolean isToday = date.equals(today);

            if (dow == 1) { // Monday: Übung
                Instant start = date.atTime(17, 30).toInstant(ZoneOffset.UTC);
                Instant end = date.atTime(19, 0).toInstant(ZoneOffset.UTC);
                var sess = attendanceRepository.createSession(
                        templateUebung.id(), start, end, evUebung.id(), "Übung KW" + weekOfYear);
                if (!isToday) {
                    for (var m : teilnehmer) {
                        var status = rng.nextInt(10) < 8
                                ? AttendanceEntry.AttendanceStatus.PRESENT
                                : AttendanceEntry.AttendanceStatus.ABSENT;
                        attendanceRepository.createEntry(
                                sess.id(), m.id(), status, AttendanceEntry.EntrySource.EXPECTED);
                    }
                    for (var m : teamForUebung) {
                        attendanceRepository.createEntry(
                                sess.id(),
                                m.id(),
                                AttendanceEntry.AttendanceStatus.PRESENT,
                                AttendanceEntry.EntrySource.EXTRA);
                    }
                }
                sessionCount++;
            }

            if (dow == 6 && date.getDayOfMonth() <= 7) { // 1st Saturday: Gesamtübung
                Instant start = date.atTime(10, 0).toInstant(ZoneOffset.UTC);
                Instant end = date.atTime(13, 0).toInstant(ZoneOffset.UTC);
                var sess = attendanceRepository.createSession(
                        templateGesamt.id(),
                        start,
                        end,
                        evGesamt.id(),
                        "Gesamtübung "
                                + date.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN)
                                + " " + date.getYear());
                if (!isToday) {
                    for (var m : teilnehmer) {
                        var status = rng.nextInt(10) < 7
                                ? AttendanceEntry.AttendanceStatus.PRESENT
                                : AttendanceEntry.AttendanceStatus.ABSENT;
                        attendanceRepository.createEntry(
                                sess.id(), m.id(), status, AttendanceEntry.EntrySource.EXPECTED);
                    }
                    for (var m : teamForGesamt) {
                        attendanceRepository.createEntry(
                                sess.id(),
                                m.id(),
                                AttendanceEntry.AttendanceStatus.PRESENT,
                                AttendanceEntry.EntrySource.EXTRA);
                    }
                }
                sessionCount++;
            }
        }
        log.info("Demo: Created {} attendance sessions spanning 14 months", sessionCount);
    }
}
