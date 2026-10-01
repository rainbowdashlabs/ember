/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.attendance.entity.TemplateGroup;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.AppointmentTemplateFieldDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCategoryRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds demo event data: categories, recurring/one-time events, registrations,
 * event fields, attendance templates, and event templates.
 */
@Singleton
public class DemoEventSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoEventSeeder.class);

    private final EventCategoryRepository categoryRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventFieldRepository eventFieldRepository;
    private final AttendanceRepository attendanceRepository;
    private final EventCrudService crudService;
    private final EventTemplateService eventTemplateService;
    private final EventRestrictionService restrictionService;
    private final EventRegistrationFieldService registrationFieldService;
    private final DemoClock clock;

    @Inject
    public DemoEventSeeder(
            EventCategoryRepository categoryRepository,
            EventRegistrationRepository registrationRepository,
            EventFieldRepository eventFieldRepository,
            AttendanceRepository attendanceRepository,
            EventCrudService crudService,
            EventTemplateService eventTemplateService,
            EventRestrictionService restrictionService,
            EventRegistrationFieldService registrationFieldService,
            DemoClock clock) {
        this.categoryRepository = categoryRepository;
        this.registrationRepository = registrationRepository;
        this.eventFieldRepository = eventFieldRepository;
        this.attendanceRepository = attendanceRepository;
        this.crudService = crudService;
        this.eventTemplateService = eventTemplateService;
        this.restrictionService = restrictionService;
        this.registrationFieldService = registrationFieldService;
        this.clock = clock;
    }

    @Override
    public int order() {
        return EVENTS;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var members = station.members();
        station.events(seed(
                clock.of(station.station()),
                station.stationId(),
                members.groupAnfaenger().id(),
                members.groupFortgeschritten().id(),
                members.anfaenger(),
                members.fortgeschritten()));
        seedTemplates(station.stationId());
    }

    /**
     * Who the drills of the youth fire brigade are for: the young people, the team running them and
     * whoever manages the station. A guardian belongs to the appointment put on for guardians, not to
     * the Saturday drill, and a demo that restricts nothing never shows that the setting exists.
     */
    private static final StationUserType[] JUGENDFEUERWEHR = {
        StationUserType.MEMBER, StationUserType.TEAM, StationUserType.MANAGER
    };

    /**
     * Who runs the station: the ones whose own meetings nobody else attends, and mostly need not know
     * about either.
     */
    private static final StationUserType[] BETREUER = {StationUserType.TEAM, StationUserType.MANAGER};

    /**
     * Narrows who may sign up for one appointment. Everybody else keeps seeing it in the calendar and
     * simply cannot answer it, which is the common case and the one a demo should mostly show.
     *
     * @param eventId   the appointment
     * @param userTypes who may sign up for it
     */
    private void restrictToUserTypes(int eventId, StationUserType... userTypes) {
        restrictionService.setRestrictions(eventId, audienceOf(userTypes));
    }

    /**
     * Narrows who may know one appointment exists at all. For everybody else it is absent from the
     * calendar, the search and every notification, and it is the only kind that carries the lock.
     *
     * <p>The demo needs one, or nothing in it ever shows that the setting is there.
     *
     * @param eventId   the appointment
     * @param userTypes who may see it
     */
    private void hideFromEveryoneBut(int eventId, StationUserType... userTypes) {
        restrictionService.setViewRestrictions(eventId, audienceOf(userTypes));
    }

    private static RestrictionSelection audienceOf(StationUserType... userTypes) {
        return new RestrictionSelection(List.of(userTypes), List.of(), List.of(), List.of(), RestrictionMode.OR);
    }

    /**
     * Seeds the station's appointments, each dated on the station's own clock.
     *
     * @param days today and the hours of a day as the station has them
     */
    public SeedResult seed(
            DemoStationDays days,
            int stationId,
            int groupAnfaengerId,
            int groupFortgeschrittenId,
            List<StationMember> anfaengerMembers,
            List<StationMember> fortgeschrittenMembers) {

        // -- Attendance templates --
        var templateUebung = attendanceRepository.createTemplate(stationId, "Übung");
        attendanceRepository.setTemplateGroups(
                templateUebung.id(),
                List.of(new TemplateGroup(groupAnfaengerId, 0), new TemplateGroup(groupFortgeschrittenId, 1)));
        attendanceRepository.createTemplateField(
                templateUebung.id(),
                "Thema",
                FieldType.TEXT,
                AttendanceFieldConfig.parse("{\"defaultValue\":\"Grundausbildung\"}"),
                0);

        var templateGesamt = attendanceRepository.createTemplate(stationId, "Gesamtübung");
        attendanceRepository.setTemplateGroups(
                templateGesamt.id(),
                List.of(new TemplateGroup(groupAnfaengerId, 0), new TemplateGroup(groupFortgeschrittenId, 1)));

        // -- Event categories --
        var catUebung = categoryRepository.create(stationId, "Übungen", 0, "#ff6421");
        var catVeranstaltung = categoryRepository.create(stationId, "Veranstaltungen", 1, "#73ceff");
        var catWettbewerb = categoryRepository.create(stationId, "Wettbewerbe", 2, "#ffdd1b");
        // Make Veranstaltungen public (all events in this category visible on public calendar)
        categoryRepository.update(
                catVeranstaltung.id(),
                catVeranstaltung.name(),
                catVeranstaltung.position(),
                null,
                true,
                catVeranstaltung.color());

        // -- Events --
        LocalDate today = days.today();
        Instant monStart = days.at(today, 17, 30);
        Instant monEnd = days.at(today, 19, 0);
        Instant satStart = days.at(today, 10, 0);
        Instant satEnd = days.at(today, 13, 0);

        var evUebung = crudService.create(
                stationId,
                "Übung",
                "Wöchentliche Übung für alle Gruppen",
                StationEvent.EventType.RECURRING,
                1,
                monStart,
                monEnd,
                templateUebung.id(),
                false,
                null,
                false,
                catUebung.id(),
                null,
                null,
                null,
                null);
        var evGesamt = crudService.create(
                stationId,
                "Gesamtübung",
                "Gemeinsame Übung aller Gruppen",
                StationEvent.EventType.RECURRING,
                6,
                satStart,
                satEnd,
                templateGesamt.id(),
                false,
                null,
                false,
                catUebung.id(),
                null,
                null,
                null,
                null);

        LocalDate firstThursday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
        var grundlehrgang = crudService.create(
                stationId,
                "Grundlehrgang",
                "Acht Abende Grundausbildung für die Neuen",
                StationEvent.EventType.RECURRING,
                DayOfWeek.THURSDAY.getValue(),
                days.at(firstThursday, 18, 0),
                days.at(firstThursday, 20, 0),
                null,
                false,
                null,
                false,
                catUebung.id(),
                null,
                null,
                null,
                null);
        crudService.setRepeatEnd(grundlehrgang.id(), null, 8);

        // Monthly: first Saturday = Elternabend
        var elternabend = crudService.create(
                stationId,
                "Elternabend",
                "Monatliches Treffen mit den Eltern",
                StationEvent.EventType.MONTHLY_FIRST,
                6,
                satStart,
                satEnd,
                null,
                true,
                null,
                false,
                catVeranstaltung.id(),
                null,
                null,
                null,
                null);
        restrictToUserTypes(elternabend.id(), StationUserType.GUARDIAN);
        restrictToUserTypes(evUebung.id(), JUGENDFEUERWEHR);
        restrictToUserTypes(evGesamt.id(), JUGENDFEUERWEHR);
        restrictToUserTypes(grundlehrgang.id(), JUGENDFEUERWEHR);

        var dienstbesprechung = crudService.create(
                stationId,
                "Dienstbesprechung",
                "Vierteljährliche Besprechung aller Betreuer",
                StationEvent.EventType.QUARTERLY,
                6,
                satStart,
                satEnd,
                null,
                false,
                null,
                false,
                catVeranstaltung.id(),
                null,
                null,
                null,
                null);
        restrictToUserTypes(dienstbesprechung.id(), BETREUER);
        hideFromEveryoneBut(dienstbesprechung.id(), BETREUER);

        // Yearly: Jahreshauptversammlung on Sep 20
        LocalDate jhvDate = today.withMonth(9).withDayOfMonth(20);
        Instant jhvStart = days.at(jhvDate, 18, 0);
        Instant jhvEnd = days.at(jhvDate, 21, 0);
        crudService.create(
                stationId,
                "Jahreshauptversammlung",
                "Jährliche Versammlung mit Berichten und Wahlen",
                StationEvent.EventType.YEARLY,
                null,
                jhvStart,
                jhvEnd,
                null,
                true,
                null,
                false,
                catVeranstaltung.id(),
                null,
                null,
                null,
                null);

        // One-time event for today (ensures there's always an event today)
        Instant todayEventStart = days.at(today, 16, 0);
        Instant todayEventEnd = days.at(today, 18, 0);
        var templateTheorie = attendanceRepository.createTemplate(stationId, "Theorieabend");
        attendanceRepository.setTemplateGroups(
                templateTheorie.id(),
                List.of(new TemplateGroup(groupAnfaengerId, 0), new TemplateGroup(groupFortgeschrittenId, 1)));
        var theorieabend = crudService.create(
                stationId,
                "Theorieabend",
                "Theoretische Grundlagen und Fahrzeugkunde",
                StationEvent.EventType.ONE_TIME,
                null,
                todayEventStart,
                todayEventEnd,
                templateTheorie.id(),
                true,
                null,
                false,
                catUebung.id(),
                null,
                null,
                null,
                null);
        restrictToUserTypes(theorieabend.id(), JUGENDFEUERWEHR);
        for (int i = 0; i < 5 && i < anfaengerMembers.size(); i++) {
            registrationRepository.create(
                    theorieabend.id(), anfaengerMembers.get(i).id(), today, RegistrationStatus.DECLINED, null);
        }

        // -- Registration-required events --
        LocalDate tagDate = today.plusMonths(1).withDayOfMonth(15);
        Instant nextMonth = days.at(tagDate, 10, 0);
        Instant nextMonthEnd = days.at(tagDate, 16, 0);
        Instant deadline = days.at(today.plusMonths(1).withDayOfMonth(10), 23, 59);

        var tagDerOffenenTuer = crudService.create(
                stationId,
                "Tag der offenen Tür",
                "Öffentlichkeitsarbeit: Vorführungen und Mitmach-Aktionen",
                StationEvent.EventType.ONE_TIME,
                null,
                nextMonth,
                nextMonthEnd,
                null,
                true,
                deadline,
                true,
                catVeranstaltung.id(),
                null,
                null,
                null,
                null);

        LocalDate stadtfestDate = today.plusWeeks(3);
        Instant oeffentlichkeit = days.at(stadtfestDate, 14, 0);
        Instant oeffentlichkeitEnd = days.at(stadtfestDate, 17, 0);
        Instant oeffentlichkeitDeadline = days.at(today.plusWeeks(2), 23, 59);

        var stadtfest = crudService.create(
                stationId,
                "Stadtfest Musterstadt",
                "Stand der Jugendfeuerwehr beim Stadtfest",
                StationEvent.EventType.ONE_TIME,
                null,
                oeffentlichkeit,
                oeffentlichkeitEnd,
                null,
                true,
                oeffentlichkeitDeadline,
                false,
                catVeranstaltung.id(),
                null,
                null,
                null,
                null);

        LocalDate kwDate = today.plusMonths(2).withDayOfMonth(20);
        Instant wettbewerb = days.at(kwDate, 8, 0);
        Instant wettbewerbEnd = days.at(kwDate, 17, 0);
        Instant wettbewerbDeadline = days.at(today.plusMonths(2).withDayOfMonth(1), 23, 59);

        var kreisWettbewerb = crudService.create(
                stationId,
                "Kreiswettbewerb",
                "Jährlicher Kreiswettbewerb der Jugendfeuerwehren",
                StationEvent.EventType.ONE_TIME,
                null,
                wettbewerb,
                wettbewerbEnd,
                null,
                true,
                wettbewerbDeadline,
                true,
                catWettbewerb.id(),
                null,
                null,
                null,
                null);
        restrictToUserTypes(kreisWettbewerb.id(), JUGENDFEUERWEHR);

        LocalDate zeltlagerStart = today.plusMonths(3).withDayOfMonth(11);
        var zeltlager = crudService.create(
                stationId,
                "Zeltlager",
                "Eine Woche Kreiszeltlager mit Übungen, Spielen und Lagerfeuer",
                StationEvent.EventType.ONE_TIME,
                null,
                days.at(zeltlagerStart, 8, 0),
                days.at(zeltlagerStart.plusDays(6), 16, 0),
                null,
                true,
                days.at(zeltlagerStart.minusWeeks(3), 23, 59),
                false,
                catVeranstaltung.id(),
                null,
                null,
                null,
                null);
        restrictToUserTypes(zeltlager.id(), JUGENDFEUERWEHR);

        // Add some registrations
        for (int i = 0; i < 8 && i < fortgeschrittenMembers.size(); i++) {
            registrationRepository.create(
                    tagDerOffenenTuer.id(),
                    fortgeschrittenMembers.get(i).id(),
                    tagDate,
                    RegistrationStatus.ACCEPTED,
                    null);
        }
        for (int i = 0; i < 5 && i < anfaengerMembers.size(); i++) {
            registrationRepository.create(
                    stadtfest.id(), anfaengerMembers.get(i).id(), stadtfestDate, RegistrationStatus.ACCEPTED, null);
        }
        for (int i = 0; i < 3 && i < fortgeschrittenMembers.size(); i++) {
            registrationRepository.create(
                    stadtfest.id(),
                    fortgeschrittenMembers.get(i).id(),
                    stadtfestDate,
                    RegistrationStatus.ACCEPTED,
                    null);
        }
        // Some pending registrations for Kreiswettbewerb
        for (int i = 0; i < 6 && i < fortgeschrittenMembers.size(); i++) {
            registrationRepository.create(
                    kreisWettbewerb.id(), fortgeschrittenMembers.get(i).id(), kwDate, RegistrationStatus.PENDING, null);
        }

        // Declined registrations for Stadtfest
        for (int i = 5; i < 8 && i < anfaengerMembers.size(); i++) {
            registrationRepository.create(
                    stadtfest.id(), anfaengerMembers.get(i).id(), stadtfestDate, RegistrationStatus.DECLINED, null);
        }
        // Declined registrations for Kreiswettbewerb
        for (int i = 6; i < 9 && i < fortgeschrittenMembers.size(); i++) {
            registrationRepository.create(
                    kreisWettbewerb.id(),
                    fortgeschrittenMembers.get(i).id(),
                    kwDate,
                    RegistrationStatus.DECLINED,
                    null);
        }
        // Denied registration for Tag der offenen Tuer
        if (anfaengerMembers.size() > 9) {
            registrationRepository.create(
                    tagDerOffenenTuer.id(), anfaengerMembers.get(9).id(), tagDate, RegistrationStatus.DENIED, null);
        }

        seedMarathon(days, stationId, catVeranstaltung.id(), fortgeschrittenMembers, anfaengerMembers);

        // -- Oeffentlichkeitsarbeit events --
        var catOeffentlichkeit = categoryRepository.create(stationId, "Öffentlichkeitsarbeit", 3, "#00c507");
        categoryRepository.update(
                catOeffentlichkeit.id(),
                catOeffentlichkeit.name(),
                catOeffentlichkeit.position(),
                null,
                true,
                catOeffentlichkeit.color());
        var allMembers = new ArrayList<StationMember>();
        allMembers.addAll(anfaengerMembers);
        allMembers.addAll(fortgeschrittenMembers);

        // Past events (completed)
        String[] oeNames = {
            "Feuerwehrfest Sommerfest",
            "Brandschutztag Grundschule",
            "Infostand Stadtfest",
            "Laternenumzug St. Martin",
            "Weihnachtsmarkt Standdienst"
        };
        String[] oeOrte = {
            "Feuerwehrgerätehaus",
            "Grundschule am Park",
            "Marktplatz Musterstadt",
            "Treffpunkt Rathaus",
            "Weihnachtsmarkt Innenstadt"
        };
        int[] oeMemberCounts = {15, 12, 14, 16, 18};

        for (int e = 0; e < oeNames.length; e++) {
            LocalDate eventDate = today.minusWeeks(oeNames.length - e);
            Instant oeStart = days.at(eventDate, 10, 0);
            Instant oeEnd = days.at(eventDate, 16, 0);
            var oeEvent = crudService.create(
                    stationId,
                    oeNames[e],
                    "Öffentlichkeitsarbeit der Jugendfeuerwehr",
                    StationEvent.EventType.ONE_TIME,
                    null,
                    oeStart,
                    oeEnd,
                    null,
                    true,
                    null,
                    true,
                    catOeffentlichkeit.id(),
                    null,
                    null,
                    null,
                    null);
            eventFieldRepository.create(
                    oeEvent.id(),
                    "Ort",
                    FieldType.LOCATION,
                    EventQuestionSettings.empty(),
                    oeOrte[e],
                    0,
                    true,
                    null,
                    true);
            eventFieldRepository.create(
                    oeEvent.id(),
                    "Treffpunkt",
                    FieldType.TEXT,
                    EventQuestionSettings.empty(),
                    "Feuerwehrgerätehaus",
                    1,
                    true,
                    null,
                    true);
            // Create registrations with rotation: offset accepted members per event for variance
            int count = Math.min(oeMemberCounts[e], allMembers.size());
            int acceptOffset = e * 3; // shift which members get accepted each event
            for (int i = 0; i < count; i++) {
                int rotatedIdx = (i + acceptOffset) % allMembers.size();
                var status = i < 6 ? RegistrationStatus.ACCEPTED : RegistrationStatus.DENIED;
                registrationRepository.create(
                        oeEvent.id(), allMembers.get(rotatedIdx).id(), eventDate, status, null);
            }
        }

        // One open event with pending (unconfirmed) registrations
        LocalDate openDate = today.plusWeeks(1);
        Instant openStart = days.at(openDate, 9, 0);
        Instant openEnd = days.at(openDate, 15, 0);
        Instant openDeadline = days.at(today.plusDays(3), 23, 59);
        var oeOpen = crudService.create(
                stationId,
                "Blaulichtmeile Bürgerfest",
                "Öffentlichkeitsarbeit - Anmeldung offen",
                StationEvent.EventType.ONE_TIME,
                null,
                openStart,
                openEnd,
                null,
                true,
                openDeadline,
                true,
                catOeffentlichkeit.id(),
                null,
                null,
                null,
                null);
        eventFieldRepository.create(
                oeOpen.id(),
                "Ort",
                FieldType.LOCATION,
                EventQuestionSettings.empty(),
                "Rathausplatz Musterstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                oeOpen.id(),
                "Treffpunkt",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Feuerwehrgerätehaus 08:30",
                1,
                true,
                null,
                true);
        eventFieldRepository.create(
                oeOpen.id(),
                "Hinweis",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Dienstkleidung und Ausrüstung mitbringen",
                2,
                false,
                null,
                false);
        // 14 registrations: 6 accepted, 8 pending (not yet confirmed)
        int openCount = Math.min(14, allMembers.size());
        for (int i = 0; i < openCount; i++) {
            var status = i < 6 ? RegistrationStatus.ACCEPTED : RegistrationStatus.PENDING;
            registrationRepository.create(oeOpen.id(), allMembers.get(i).id(), openDate, status, null);
        }

        // -- Event Fields --
        // Per-event fields
        eventFieldRepository.create(
                tagDerOffenenTuer.id(),
                "Ort",
                FieldType.LOCATION,
                EventQuestionSettings.empty(),
                "Feuerwehrhaus Musterstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                tagDerOffenenTuer.id(),
                "Treffpunkt",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Haupteingang",
                1,
                true,
                null,
                true);
        eventFieldRepository.create(
                tagDerOffenenTuer.id(),
                "Hinweis",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Dienstkleidung tragen",
                2,
                false,
                null,
                false);
        eventFieldRepository.create(
                stadtfest.id(),
                "Ort",
                FieldType.LOCATION,
                EventQuestionSettings.empty(),
                "Marktplatz Musterstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                stadtfest.id(),
                "Treffpunkt",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Stand der Jugendfeuerwehr",
                1,
                true,
                null,
                true);
        eventFieldRepository.create(
                kreisWettbewerb.id(),
                "Ort",
                FieldType.LOCATION,
                EventQuestionSettings.empty(),
                "Sportplatz Nachbarstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                kreisWettbewerb.id(),
                "Hinweis",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Wettkampfkleidung und Ausrüstung mitbringen",
                1,
                false,
                null,
                false);
        // Recurring event fields
        eventFieldRepository.create(
                evUebung.id(),
                "Ort",
                FieldType.LOCATION,
                EventQuestionSettings.empty(),
                "Feuerwehrhaus Musterstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                evUebung.id(),
                "Hinweis",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Sportkleidung mitbringen",
                1,
                false,
                null,
                false);
        eventFieldRepository.create(
                evGesamt.id(),
                "Ort",
                FieldType.LOCATION,
                EventQuestionSettings.empty(),
                "Feuerwehrhaus Musterstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                evGesamt.id(),
                "Treffpunkt",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Fahrzeughalle",
                1,
                true,
                null,
                true);
        eventFieldRepository.create(
                theorieabend.id(),
                "Ort",
                FieldType.LOCATION,
                EventQuestionSettings.empty(),
                "Schulungsraum Feuerwehrhaus",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                theorieabend.id(),
                "Hinweis",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Schreibzeug mitbringen",
                1,
                false,
                null,
                false);

        log.info("Demo: Created events, categories, attendance templates, and event fields");
        return new SeedResult(
                templateUebung, templateGesamt, evUebung, evGesamt, tagDerOffenenTuer.id(), stadtfest.id());
    }

    /**
     * Seeds event templates (independent of main event data, can run in parallel).
     */
    public void seedTemplates(int stationId) {
        var tplStandard = eventTemplateService.create(stationId, "Standard-Übung");
        eventTemplateService.update(
                tplStandard.id(),
                "Standard-Übung",
                "Übungsabend",
                null,
                null,
                StationEvent.EventType.RECURRING,
                false,
                null,
                false,
                null,
                null,
                null);
        eventTemplateService.replaceFields(
                tplStandard.id(),
                List.of(
                        new AppointmentTemplateFieldDraft(
                                "Ort",
                                FieldType.LOCATION,
                                EventQuestionSettings.empty(),
                                0,
                                true,
                                true,
                                null,
                                "Gerätehaus"),
                        new AppointmentTemplateFieldDraft(
                                "Treffpunkt",
                                FieldType.TEXT,
                                EventQuestionSettings.empty(),
                                1,
                                true,
                                true,
                                null,
                                "Fahrzeughalle")));
        var tplWettbewerb = eventTemplateService.create(stationId, "Wettbewerb");
        eventTemplateService.update(
                tplWettbewerb.id(),
                "Wettbewerb",
                null,
                null,
                null,
                StationEvent.EventType.ONE_TIME,
                true,
                null,
                true,
                null,
                null,
                null);
        eventTemplateService.replaceFields(
                tplWettbewerb.id(),
                List.of(
                        new AppointmentTemplateFieldDraft(
                                "Ort", FieldType.LOCATION, EventQuestionSettings.empty(), 0, true, true, null, null),
                        new AppointmentTemplateFieldDraft(
                                "Thema", FieldType.TEXT, EventQuestionSettings.empty(), 1, true, false, null, null)));
        log.info("Demo: Created event templates");
    }

    /**
     * Seeds the charity marathon: an event open to the team and to members alike, which asks
     * everyone registering for their shirt size and how many guests they bring.
     *
     * <p>It is the demo of registration questions, so the answers are seeded too - a registration
     * list with empty answers would not show what the feature does.
     */
    private void seedMarathon(
            DemoStationDays days,
            int stationId,
            int categoryId,
            List<StationMember> teamMembers,
            List<StationMember> members) {
        LocalDate raceDay = days.today().plusMonths(1).withDayOfMonth(8);
        Instant start = days.at(raceDay, 9, 0);
        Instant end = days.at(raceDay, 15, 0);
        Instant deadline = days.at(raceDay.minusWeeks(2), 23, 59);

        var marathon = crudService.create(
                stationId,
                "Benefiz-Marathon",
                "Staffellauf für den guten Zweck. Anmeldung mit Shirtgröße, das Shirt gibt es am Renntag.",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                true,
                deadline,
                false,
                categoryId,
                null,
                null,
                null,
                null);

        restrictionService.setRestrictions(
                marathon.id(),
                new RestrictionSelection(
                        List.of(StationUserType.TEAM, StationUserType.MEMBER),
                        List.of(),
                        List.of(),
                        List.of(),
                        RestrictionMode.OR));

        registrationFieldService.replaceFields(
                marathon.id(),
                List.of(
                        new RegistrationFieldDraft(
                                "Shirtgröße",
                                FieldType.CHOICE,
                                new EventQuestionSettings(
                                        List.of("XS", "S", "M", "L", "XL", "XXL"),
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
                                        false),
                                true),
                        new RegistrationFieldDraft(
                                "Begleitpersonen",
                                FieldType.NUMBER,
                                new EventQuestionSettings(
                                        null, null, null, null, null, false, false, false, "0", 0, 5, false),
                                true),
                        new RegistrationFieldDraft(
                                "Anmerkungen", FieldType.LONG_TEXT, EventQuestionSettings.empty(), false),
                        new RegistrationFieldDraft(
                                "Startnummer",
                                FieldType.TEXT,
                                new EventQuestionSettings(
                                        null, null, null, null, null, false, false, false, null, null, null, true),
                                true)));

        var fields = registrationFieldService.findByEvent(marathon.id());
        int sizeFieldId = fieldId(fields, "Shirtgröße");
        int guestFieldId = fieldId(fields, "Begleitpersonen");
        int noteFieldId = fieldId(fields, "Anmerkungen");

        String[] sizes = {"S", "M", "L", "M", "XL", "S", "M", "L", "XXL", "M"};
        int[] guests = {0, 2, 1, 0, 3, 1, 0, 2, 0, 1};
        String[] notes = {
            "Laufe die erste Etappe.", "", "Bringe Kuchen mit.", "", "Komme mit der ganzen Familie.",
        };

        var registrants = new ArrayList<StationMember>();
        for (int i = 0; i < 6 && i < teamMembers.size(); i++) registrants.add(teamMembers.get(i));
        for (int i = 0; i < 4 && i < members.size(); i++) registrants.add(members.get(i));

        for (int i = 0; i < registrants.size(); i++) {
            var registration = registrationRepository.create(
                    marathon.id(), registrants.get(i).id(), raceDay, RegistrationStatus.ACCEPTED, null);
            registrationFieldService.persistAnswers(
                    registration.id(),
                    answers(
                            sizeFieldId,
                            sizes[i % sizes.length],
                            guestFieldId,
                            String.valueOf(guests[i % guests.length]),
                            noteFieldId,
                            i < notes.length ? notes[i] : ""));
        }
        log.info("Demo: Created marathon event with registration questions");
    }

    private static int fieldId(List<EventRegistrationField> fields, String name) {
        return fields.stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .map(EventRegistrationField::id)
                .orElseThrow();
    }

    /**
     * Builds an answer map, skipping the blank entries so a seeded registration looks like one a
     * member filled in rather than one with empty strings stored.
     */
    private static Map<Integer, String> answers(
            int sizeFieldId, String size, int guestFieldId, String guests, int noteFieldId, String note) {
        var values = new LinkedHashMap<Integer, String>();
        values.put(sizeFieldId, size);
        values.put(guestFieldId, guests);
        if (!note.isBlank()) values.put(noteFieldId, note);
        return values;
    }

    /**
     * Result of event seeding, containing references needed by attendance seeder and notification seeder.
     */
    public record SeedResult(
            AttendanceTemplate templateUebung,
            AttendanceTemplate templateGesamt,
            StationEvent evUebung,
            StationEvent evGesamt,
            int tagDerOffenenTuerId,
            int stadtfestId) {}
}
