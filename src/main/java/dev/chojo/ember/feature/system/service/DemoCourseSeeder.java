/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.events.entity.EventCourseDetails;
import dev.chojo.ember.feature.events.entity.EventCourseSet;
import dev.chojo.ember.feature.events.entity.EventCourseSetDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.service.CourseQuestionService;
import dev.chojo.ember.feature.events.service.EventCoursePartFactory;
import dev.chojo.ember.feature.events.service.EventCourseRegistrationService;
import dev.chojo.ember.feature.events.service.EventCourseService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Jugendflamme course for the youth fire brigade, meant for the young people and the team: three
 * practice days over two weekends of which two count, and a test on the last day that everybody
 * attends. The T-shirt size is asked once for the whole course.
 *
 * <p>The sign-ups show every state a course page knows: a few young people with a valid choice, one
 * with all four days, one still waiting for the team to confirm, and one the team signed up with a
 * single practice day, who shows as short.
 */
@Singleton
public class DemoCourseSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoCourseSeeder.class);
    private static final String[] SIZES = {"M", "S", "L", "M", "XL", "S", "M"};

    private final EventCourseService courses;
    private final EventCourseRegistrationService signUps;
    private final CourseQuestionService questions;
    private final EventCoursePartFactory partFactory;
    private final DemoClock clock;

    @Inject
    public DemoCourseSeeder(
            EventCourseService courses,
            EventCourseRegistrationService signUps,
            CourseQuestionService questions,
            EventCoursePartFactory partFactory,
            DemoClock clock) {
        this.courses = courses;
        this.signUps = signUps;
        this.questions = questions;
        this.partFactory = partFactory;
        this.clock = clock;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var members = station.members();
        seedJugendflamme(
                clock.of(station.station()), station.stationId(), members.fortgeschritten(), members.betreuer());
    }

    private void seedJugendflamme(
            DemoStationDays days, int stationId, List<StationMember> youth, List<StationMember> team) {
        LocalDate saturday = days.today().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        var youthAndTeam = new RestrictionAudience(
                List.of(DemoEventSeeder.JUGENDFEUERWEHR), List.of(), List.of(), List.of(), RestrictionMode.OR);
        var course = courses.create(
                stationId,
                new EventCourseDetails(
                        "Jugendflamme Stufe 1",
                        "Die erste Stufe der Jugendflamme. Von den drei Übungstagen müssen zwei besucht werden,"
                                + " die Prüfung ist Pflicht. Treffpunkt ist jeweils das Gerätehaus.",
                        12,
                        days.at(saturday.minusDays(7), 23, 59),
                        true),
                List.of(new EventCourseSetDraft(null, "Übung", 2), new EventCourseSetDraft(null, "Prüfung", 1)),
                youthAndTeam,
                youthAndTeam,
                List.of(new RegistrationFieldDraft("T-Shirt-Größe", FieldType.CHOICE, sizeQuestion(), true)));
        var sets = courses.findSets(course.id());
        int practice = setId(sets, "Übung");
        int test = setId(sets, "Prüfung");
        int saturdayPractice = part(days, stationId, course.id(), practice, "Übung Samstag", saturday, 9);
        int sundayPractice = part(days, stationId, course.id(), practice, "Übung Sonntag", saturday.plusDays(1), 10);
        int nextSaturdayPractice =
                part(days, stationId, course.id(), practice, "Übung Samstag", saturday.plusWeeks(1), 9);
        int exam = part(
                days,
                stationId,
                course.id(),
                test,
                "Prüfung",
                saturday.plusWeeks(1).plusDays(1),
                10);

        int sizeField = questions.findByCourse(course.id()).stream()
                .map(EventRegistrationField::id)
                .findFirst()
                .orElseThrow();
        var choices = List.of(
                Set.of(saturdayPractice, sundayPractice, exam),
                Set.of(sundayPractice, nextSaturdayPractice, exam),
                Set.of(saturdayPractice, nextSaturdayPractice, exam),
                Set.of(saturdayPractice, sundayPractice, exam),
                Set.of(saturdayPractice, sundayPractice, nextSaturdayPractice, exam),
                Set.of(sundayPractice, nextSaturdayPractice, exam));
        int signedUp = Math.min(choices.size(), youth.size());
        for (int i = 0; i < signedUp; i++) {
            signUps.register(
                    stationId,
                    course.id(),
                    youth.get(i).id(),
                    choices.get(i),
                    Map.of(sizeField, SIZES[i]),
                    null,
                    false);
        }
        if (youth.size() > signedUp && !team.isEmpty()) {
            signUps.register(
                    stationId,
                    course.id(),
                    youth.get(signedUp).id(),
                    Set.of(saturdayPractice, exam),
                    Map.of(sizeField, SIZES[signedUp]),
                    team.getFirst().id(),
                    true);
            signUps.decide(stationId, course.id(), youth.get(signedUp).id(), RegistrationStatus.ACCEPTED);
        }
        for (int i = 0; i < signedUp - 1; i++) {
            signUps.decide(stationId, course.id(), youth.get(i).id(), RegistrationStatus.ACCEPTED);
        }
        log.info("Demo: Created the Jugendflamme course with four parts and {} sign-ups", signedUp + 1);
    }

    private int part(
            DemoStationDays days, int stationId, int courseId, int setId, String name, LocalDate day, int from) {
        return partFactory
                .create(stationId, courseId, setId, name, null, days.at(day, from, 0), days.at(day, from + 6, 0))
                .eventId();
    }

    /** A size chosen from the usual ones, starting from M, that has to be answered. */
    private static EventQuestionSettings sizeQuestion() {
        return new EventQuestionSettings(
                List.of("XS", "S", "M", "L", "XL"), null, null, null, null, false, false, true, "M", null, null, false);
    }

    private static int setId(List<EventCourseSet> sets, String name) {
        return sets.stream()
                .filter(set -> set.name().equals(name))
                .map(EventCourseSet::id)
                .findFirst()
                .orElseThrow();
    }
}
