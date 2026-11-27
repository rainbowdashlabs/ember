/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventGroupSettings;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventGroupPartFactory;
import dev.chojo.ember.feature.events.service.EventGroupQuestionService;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.events.service.EventGroupSettingsService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Jugendflamme event group for the youth fire brigade, meant for the young people and the team: three
 * practice days over two weekends of which two count, and a test on the last day that everybody
 * attends. The T-shirt size is asked once for the whole event group.
 *
 * <p>The Jugendflamme carries a colour of its own; the Grundlehrgang shows the one picked by its id.
 *
 * <p>The sign-ups show every state an event group page knows: a few young people with a valid choice, one
 * with all four days, one still waiting for the team to confirm, and one the team signed up with a
 * single practice day, who shows as short.
 *
 * <p>The Jugendflamme also shows what an event group hands its dates: a "Betreuer" field the team puts
 * itself into, with one supervisor entered for the whole weekend, one who misses the test and one who
 * comes on Sunday only; and the practice attendance sheet, the practice category and reminders, which
 * every date follows because none sets its own.
 */
@Singleton
public class DemoEventGroupSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoEventGroupSeeder.class);
    private static final String[] SIZES = {"M", "S", "L", "M", "XL", "S", "M"};

    /** The name of the Jugendflamme's field the team puts itself into. */
    static final String SUPERVISORS = "Betreuer";

    private final EventGroupService eventGroups;
    private final EventGroupRegistrationService signUps;
    private final EventGroupQuestionService questions;
    private final EventGroupPartFactory partFactory;
    private final EventGroupSettingsService settings;
    private final EventFieldService fields;
    private final DemoClock clock;

    @Inject
    public DemoEventGroupSeeder(
            EventGroupService eventGroups,
            EventGroupRegistrationService signUps,
            EventGroupQuestionService questions,
            EventGroupPartFactory partFactory,
            EventGroupSettingsService settings,
            EventFieldService fields,
            DemoClock clock) {
        this.settings = settings;
        this.fields = fields;
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
        var days = clock.of(station.station());
        var jugendflamme = seedJugendflamme(
                days,
                station.stationId(),
                members.fortgeschritten(),
                members.betreuer(),
                members.groupBetreuer().id());
        handDown(station, jugendflamme.id());
        seedSupervisors(jugendflamme, members.groupBetreuer().id(), members.betreuer());
        seedGrundlehrgang(
                days,
                station.stationId(),
                members.anfaenger(),
                station.events().evUebung().categoryId());
        seedErsteHilfe(days, station.stationId(), members.fortgeschritten());
    }

    /** What the first aid course is called, the event group with two alternative dates. */
    static final String ERSTE_HILFE = "Erste-Hilfe-Kurs";

    /** How many places each date of the first aid course has. */
    static final int ERSTE_HILFE_PLACES = 3;

    /** The name of the first aid course's date that is full. */
    static final String ERSTE_HILFE_FULL = "Kurs Samstag";

    /** The name of the first aid course's date that still has places. */
    static final String ERSTE_HILFE_FREE = "Kurs Sonntag";

    /**
     * A first aid course held twice, on a Saturday and on the Sunday after, of which every sign-up
     * takes exactly one: two alternatives, each with three places. The Saturday is full and one more
     * member waits for it on the waiting list, while the Sunday still has places, so the demo shows a
     * full alternative and the free one beside it. The members sign up the way members do, so the
     * waiting sign-up is on the list in order.
     *
     * @param days      today and the hours of a day as the station has them
     * @param stationId the station
     * @param members   the members who sign up
     */
    private void seedErsteHilfe(DemoStationDays days, int stationId, List<StationMember> members) {
        LocalDate saturday = days.today().plusWeeks(5).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        var eventGroup = eventGroups.create(
                stationId,
                new EventGroupDetails(
                        ERSTE_HILFE,
                        "Der Kurs findet zweimal statt. Jede Anmeldung wählt einen der beiden Tage.",
                        null,
                        RegistrationDeadline.none(),
                        false,
                        "#00c507",
                        null,
                        null,
                        ERSTE_HILFE_PLACES,
                        null),
                List.of(new EventGroupSetDraft(null, "Kurstag", 1, 1)),
                youthAndTeam(),
                youthAndTeam(),
                new EventGroupContent(null, List.of()));
        int courseDay = eventGroups.findSets(eventGroup.id()).getFirst().id();
        int full = part(days, stationId, eventGroup.id(), courseDay, ERSTE_HILFE_FULL, saturday, 9, 8, null);
        int free =
                part(days, stationId, eventGroup.id(), courseDay, ERSTE_HILFE_FREE, saturday.plusDays(1), 9, 8, null);
        int onSaturday = Math.min(ERSTE_HILFE_PLACES + 1, members.size());
        for (int i = 0; i < onSaturday; i++) {
            signUps.register(stationId, eventGroup.id(), members.get(i).id(), Set.of(full), Map.of(), null, false);
        }
        if (members.size() > onSaturday) {
            signUps.register(
                    stationId, eventGroup.id(), members.get(onSaturday).id(), Set.of(free), Map.of(), null, false);
        }
        log.info("Demo: Created the {} event group with a full and a free alternative", ERSTE_HILFE);
    }

    /**
     * The basic training of the newcomers: eight Thursday evenings, of which six have to be attended.
     * Most of the beginners signed up for all eight, some for six. The evenings are filed as practice,
     * as the repeating appointment they replace was.
     */
    private void seedGrundlehrgang(
            DemoStationDays days, int stationId, List<StationMember> beginners, @Nullable Integer practice) {
        LocalDate firstThursday = days.today().plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
        var eventGroup = eventGroups.create(
                stationId,
                new EventGroupDetails(
                        "Grundlehrgang",
                        "Acht Abende Grundausbildung für die Neuen. Sechs davon müssen besucht werden.",
                        null,
                        RegistrationDeadline.fixed(days.at(firstThursday, 17, 0)),
                        false,
                        null),
                List.of(new EventGroupSetDraft(null, "Ausbildungsabende", 6)),
                youthAndTeam(),
                youthAndTeam(),
                new EventGroupContent(
                        null,
                        List.of(new EventFieldDraft(
                                null,
                                "Ort",
                                FieldType.LOCATION,
                                EventQuestionSettings.empty(),
                                "Gerätehaus Musterstadt",
                                true,
                                null,
                                true))));
        int evenings = eventGroups.findSets(eventGroup.id()).getFirst().id();
        var parts = new ArrayList<Integer>();
        for (int week = 0; week < 8; week++) {
            parts.add(part(
                    days,
                    stationId,
                    eventGroup.id(),
                    evenings,
                    "Grundlehrgang " + (week + 1),
                    firstThursday.plusWeeks(week),
                    18,
                    2,
                    practice));
        }
        for (int i = 0; i < beginners.size() && i < 6; i++) {
            var chosen = i % 3 == 2 ? Set.copyOf(parts.subList(0, 6)) : Set.copyOf(parts);
            signUps.register(stationId, eventGroup.id(), beginners.get(i).id(), chosen, Map.of(), null, false);
        }
        log.info("Demo: Created the Grundlehrgang event group with eight evenings");
    }

    private static RestrictionAudience youthAndTeam() {
        return new RestrictionAudience(
                List.of(DemoEventSeeder.JUGENDFEUERWEHR), List.of(), List.of(), List.of(), RestrictionMode.OR);
    }

    /**
     * The practice attendance sheet, the practice category and two reminders, set on the Jugendflamme
     * and followed by every one of its dates.
     */
    private void handDown(DemoStationContext station, int eventGroupId) {
        var events = station.events();
        settings.save(
                station.stationId(),
                eventGroupId,
                new EventGroupSettings(
                        events.templateUebung().id(), events.evUebung().categoryId(), List.of(7, 1), List.of()));
    }

    /**
     * Puts the team into the "Betreuer" field the way they would themselves: the first supervisor for
     * the whole weekend, the third for the whole weekend but not the test, and the second for the
     * Sunday practice only.
     */
    private void seedSupervisors(Jugendflamme jugendflamme, int teamGroupId, List<StationMember> team) {
        if (team.size() < 3) return;
        int eventGroupId = jugendflamme.id();
        int field = eventGroups.findFields(eventGroupId).stream()
                .filter(found -> found.name().equals(SUPERVISORS))
                .map(AppointmentField::id)
                .findFirst()
                .orElseThrow();
        int sunday = jugendflamme.sundayPractice();
        int exam = jugendflamme.exam();
        fields.toggleEventGroupSelfRegistration(eventGroupId, field, team.get(0).id(), true);
        fields.toggleEventGroupSelfRegistration(eventGroupId, field, team.get(2).id(), true);
        fields.toggleSelfRegistration(
                exam, AppointmentField.inheritedId(field), team.get(2).id(), null, true);
        fields.toggleSelfRegistration(
                sunday, AppointmentField.inheritedId(field), team.get(1).id(), null, true);
        log.info("Demo: Put the team into the Betreuer field of event group {} (group {})", eventGroupId, teamGroupId);
    }

    /**
     * The Jugendflamme as the team is put into it: the event group and the two dates one of them is
     * entered for or skips.
     *
     * @param id             the event group
     * @param sundayPractice the Sunday practice
     * @param exam           the test on the last day
     */
    private record Jugendflamme(int id, int sundayPractice, int exam) {}

    private Jugendflamme seedJugendflamme(
            DemoStationDays days, int stationId, List<StationMember> youth, List<StationMember> team, int teamGroupId) {
        LocalDate saturday = days.today().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        var youthAndTeam = youthAndTeam();
        var eventGroup = eventGroups.create(
                stationId,
                new EventGroupDetails(
                        "Jugendflamme Stufe 1",
                        "Die erste Stufe der Jugendflamme. Von den drei Übungstagen müssen zwei besucht werden,"
                                + " die Prüfung ist Pflicht.",
                        12,
                        RegistrationDeadline.relative(7 * 1440, RegistrationDeadline.DeadlineAnchor.MIDNIGHT),
                        true,
                        "#8b5cf6"),
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
                                        SUPERVISORS,
                                        FieldType.MEMBER_LIST_OF_GROUP,
                                        supervisorField(teamGroupId),
                                        "",
                                        true,
                                        null,
                                        false),
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
        int saturdayPractice = part(days, stationId, eventGroup.id(), practice, "Übung Samstag", saturday, 9, 6, null);
        int sundayPractice =
                part(days, stationId, eventGroup.id(), practice, "Übung Sonntag", saturday.plusDays(1), 10, 6, null);
        int nextSaturdayPractice =
                part(days, stationId, eventGroup.id(), practice, "Übung Samstag", saturday.plusWeeks(1), 9, 6, null);
        int exam = part(
                days,
                stationId,
                eventGroup.id(),
                test,
                "Prüfung",
                saturday.plusWeeks(1).plusDays(1),
                10,
                6,
                null);

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
        return new Jugendflamme(eventGroup.id(), sundayPractice, exam);
    }

    private int part(
            DemoStationDays days,
            int stationId,
            int eventGroupId,
            int setId,
            String name,
            LocalDate day,
            int from,
            int hours,
            @Nullable Integer categoryId) {
        return partFactory
                .create(
                        stationId,
                        eventGroupId,
                        setId,
                        name,
                        null,
                        days.at(day, from, 0),
                        days.at(day, from + hours, 0),
                        categoryId)
                .eventId();
    }

    /** Members of the team group, who put themselves in. */
    private static EventQuestionSettings supervisorField(int teamGroupId) {
        return new EventQuestionSettings(
                null, teamGroupId, null, null, null, true, false, false, null, null, null, false, null);
    }

    /** A size chosen from the usual ones, starting from M, that has to be answered and shows what it is for. */
    private static EventQuestionSettings sizeQuestion() {
        return new EventQuestionSettings(
                List.of("XS", "S", "M", "L", "XL"),
                null,
                null,
                null,
                null,
                false,
                false,
                true,
                "M",
                null,
                null,
                false,
                "Für das T-Shirt, das alle Teilnehmenden zur Abnahme bekommen.");
    }

    private static int setId(List<EventGroupSet> sets, String name) {
        return sets.stream()
                .filter(set -> set.name().equals(name))
                .map(EventGroupSet::id)
                .findFirst()
                .orElseThrow();
    }
}
