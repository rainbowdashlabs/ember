/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.service.EventGroupPartFactory;
import dev.chojo.ember.feature.events.service.EventGroupQuestionService;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService;
import dev.chojo.ember.feature.events.service.EventGroupService;
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
 * A Jugendflamme event group for the youth fire brigade, meant for the young people and the team: three
 * practice days over two weekends of which two count, and a test on the last day that everybody
 * attends. The T-shirt size is asked once for the whole event group.
 *
 * <p>The sign-ups show every state an event group page knows: a few young people with a valid choice, one
 * with all four days, one still waiting for the team to confirm, and one the team signed up with a
 * single practice day, who shows as short.
 */
@Singleton
public class DemoEventGroupSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoEventGroupSeeder.class);
    private static final String[] SIZES = {"M", "S", "L", "M", "XL", "S", "M"};

    private final EventGroupService eventGroups;
    private final EventGroupRegistrationService signUps;
    private final EventGroupQuestionService questions;
    private final EventGroupPartFactory partFactory;
    private final DemoClock clock;

    @Inject
    public DemoEventGroupSeeder(
            EventGroupService eventGroups,
            EventGroupRegistrationService signUps,
            EventGroupQuestionService questions,
            EventGroupPartFactory partFactory,
            DemoClock clock) {
        this.eventGroups = eventGroups;
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
        var eventGroup = eventGroups.create(
                stationId,
                new EventGroupDetails(
                        "Jugendflamme Stufe 1",
                        "Die erste Stufe der Jugendflamme. Von den drei Übungstagen müssen zwei besucht werden,"
                                + " die Prüfung ist Pflicht.",
                        12,
                        days.at(saturday.minusDays(7), 23, 59),
                        true),
                List.of(new EventGroupSetDraft(null, "Übung", 2), new EventGroupSetDraft(null, "Prüfung", 1)),
                youthAndTeam,
                youthAndTeam,
                new EventGroupContent(
                        List.of(new RegistrationFieldDraft("T-Shirt-Größe", FieldType.CHOICE, sizeQuestion(), true)),
                        List.of(
                                new EventFieldDraft(
                                        null,
                                        "Ort",
                                        FieldType.LOCATION,
                                        EventQuestionSettings.empty(),
                                        "Gerätehaus Musterstadt",
                                        true,
                                        null,
                                        true),
                                new EventFieldDraft(
                                        null,
                                        "Mitbringen",
                                        FieldType.TEXT,
                                        EventQuestionSettings.empty(),
                                        "Einsatzkleidung, Helm, Handschuhe",
                                        false,
                                        null,
                                        false))));
        var sets = eventGroups.findSets(eventGroup.id());
        int practice = setId(sets, "Übung");
        int test = setId(sets, "Prüfung");
        int saturdayPractice = part(days, stationId, eventGroup.id(), practice, "Übung Samstag", saturday, 9);
        int sundayPractice =
                part(days, stationId, eventGroup.id(), practice, "Übung Sonntag", saturday.plusDays(1), 10);
        int nextSaturdayPractice =
                part(days, stationId, eventGroup.id(), practice, "Übung Samstag", saturday.plusWeeks(1), 9);
        int exam = part(
                days,
                stationId,
                eventGroup.id(),
                test,
                "Prüfung",
                saturday.plusWeeks(1).plusDays(1),
                10);

        int sizeField = questions.findByEventGroup(eventGroup.id()).stream()
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
                    eventGroup.id(),
                    youth.get(i).id(),
                    choices.get(i),
                    Map.of(sizeField, SIZES[i]),
                    null,
                    false);
        }
        if (youth.size() > signedUp && !team.isEmpty()) {
            signUps.register(
                    stationId,
                    eventGroup.id(),
                    youth.get(signedUp).id(),
                    Set.of(saturdayPractice, exam),
                    Map.of(sizeField, SIZES[signedUp]),
                    team.getFirst().id(),
                    true);
            signUps.decide(stationId, eventGroup.id(), youth.get(signedUp).id(), RegistrationStatus.ACCEPTED);
        }
        for (int i = 0; i < signedUp - 1; i++) {
            signUps.decide(stationId, eventGroup.id(), youth.get(i).id(), RegistrationStatus.ACCEPTED);
        }
        log.info("Demo: Created the Jugendflamme event group with four parts and {} sign-ups", signedUp + 1);
    }

    private int part(
            DemoStationDays days, int stationId, int eventGroupId, int setId, String name, LocalDate day, int from) {
        return partFactory
                .create(stationId, eventGroupId, setId, name, null, days.at(day, from, 0), days.at(day, from + 6, 0))
                .eventId();
    }

    /** A size chosen from the usual ones, starting from M, that has to be answered. */
    private static EventQuestionSettings sizeQuestion() {
        return new EventQuestionSettings(
                List.of("XS", "S", "M", "L", "XL"), null, null, null, null, false, false, true, "M", null, null, false);
    }

    private static int setId(List<EventGroupSet> sets, String name) {
        return sets.stream()
                .filter(set -> set.name().equals(name))
                .map(EventGroupSet::id)
                .findFirst()
                .orElseThrow();
    }
}
