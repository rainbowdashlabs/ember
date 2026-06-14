/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventTemplateFieldData;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds demo event data: categories, recurring/one-time events, registrations,
 * event fields, attendance templates, and event templates.
 */
@Singleton
public class DemoEventSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoEventSeeder.class);

    private final EventRepository eventRepository;
    private final EventFieldRepository eventFieldRepository;
    private final AttendanceRepository attendanceRepository;
    private final EventService eventService;
    private final EventTemplateService eventTemplateService;

    @Inject
    public DemoEventSeeder(
            EventRepository eventRepository,
            EventFieldRepository eventFieldRepository,
            AttendanceRepository attendanceRepository,
            EventService eventService,
            EventTemplateService eventTemplateService) {
        this.eventRepository = eventRepository;
        this.eventFieldRepository = eventFieldRepository;
        this.attendanceRepository = attendanceRepository;
        this.eventService = eventService;
        this.eventTemplateService = eventTemplateService;
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

    public SeedResult seed(
            int stationId,
            int groupAnfaengerId,
            int groupFortgeschrittenId,
            List<StationMember> anfaengerMembers,
            List<StationMember> fortgeschrittenMembers) {

        // -- Attendance templates --
        var templateUebung = attendanceRepository.createTemplate(stationId, "Übung");
        attendanceRepository.setTemplateGroups(
                templateUebung.id(),
                List.of(
                        new AttendanceRepository.TemplateGroup(groupAnfaengerId, 0),
                        new AttendanceRepository.TemplateGroup(groupFortgeschrittenId, 1)));
        attendanceRepository.createTemplateField(
                templateUebung.id(),
                "Thema",
                AttendanceFieldType.STRING,
                AttendanceFieldConfig.parse("{\"defaultValue\":\"Grundausbildung\"}"),
                0);

        var templateGesamt = attendanceRepository.createTemplate(stationId, "Gesamtübung");
        attendanceRepository.setTemplateGroups(
                templateGesamt.id(),
                List.of(
                        new AttendanceRepository.TemplateGroup(groupAnfaengerId, 0),
                        new AttendanceRepository.TemplateGroup(groupFortgeschrittenId, 1)));

        // -- Event categories --
        var catUebung = eventRepository.createCategory(stationId, "Übungen", 0);
        var catVeranstaltung = eventRepository.createCategory(stationId, "Veranstaltungen", 1);
        var catWettbewerb = eventRepository.createCategory(stationId, "Wettbewerbe", 2);
        // Make Veranstaltungen public (all events in this category visible on public calendar)
        eventRepository.updateCategory(
                catVeranstaltung.id(), catVeranstaltung.name(), catVeranstaltung.position(), null, true);

        // -- Events --
        Instant monStart = LocalDate.now().atTime(17, 30).toInstant(ZoneOffset.UTC);
        Instant monEnd = LocalDate.now().atTime(19, 0).toInstant(ZoneOffset.UTC);
        Instant satStart = LocalDate.now().atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant satEnd = LocalDate.now().atTime(13, 0).toInstant(ZoneOffset.UTC);

        var evUebung = eventService.create(
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
        var evGesamt = eventService.create(
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

        // Monthly: first Saturday = Elternabend
        eventService.create(
                stationId,
                "Elternabend",
                "Monatliches Treffen mit den Eltern",
                StationEvent.EventType.MONTHLY_FIRST,
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

        // Quarterly: first Saturday = Dienstbesprechung
        eventService.create(
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

        // Yearly: Jahreshauptversammlung on Sep 20
        Instant jhvStart =
                LocalDate.now().withMonth(9).withDayOfMonth(20).atTime(18, 0).toInstant(ZoneOffset.UTC);
        Instant jhvEnd =
                LocalDate.now().withMonth(9).withDayOfMonth(20).atTime(21, 0).toInstant(ZoneOffset.UTC);
        eventService.create(
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
        Instant todayEventStart = LocalDate.now().atTime(16, 0).toInstant(ZoneOffset.UTC);
        Instant todayEventEnd = LocalDate.now().atTime(18, 0).toInstant(ZoneOffset.UTC);
        var templateTheorie = attendanceRepository.createTemplate(stationId, "Theorieabend");
        attendanceRepository.setTemplateGroups(
                templateTheorie.id(),
                List.of(
                        new AttendanceRepository.TemplateGroup(groupAnfaengerId, 0),
                        new AttendanceRepository.TemplateGroup(groupFortgeschrittenId, 1)));
        var theorieabend = eventService.create(
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
        LocalDate todayDate = LocalDate.now();
        for (int i = 0; i < 5 && i < anfaengerMembers.size(); i++) {
            eventRepository.createRegistration(
                    theorieabend.id(), anfaengerMembers.get(i).id(), todayDate, RegistrationStatus.DECLINED, null);
        }

        // -- Registration-required events --
        Instant nextMonth =
                LocalDate.now().plusMonths(1).withDayOfMonth(15).atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant nextMonthEnd =
                LocalDate.now().plusMonths(1).withDayOfMonth(15).atTime(16, 0).toInstant(ZoneOffset.UTC);
        Instant deadline =
                LocalDate.now().plusMonths(1).withDayOfMonth(10).atTime(23, 59).toInstant(ZoneOffset.UTC);

        var tagDerOffenenTuer = eventService.create(
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

        Instant oeffentlichkeit = LocalDate.now().plusWeeks(3).atTime(14, 0).toInstant(ZoneOffset.UTC);
        Instant oeffentlichkeitEnd = LocalDate.now().plusWeeks(3).atTime(17, 0).toInstant(ZoneOffset.UTC);
        Instant oeffentlichkeitDeadline =
                LocalDate.now().plusWeeks(2).atTime(23, 59).toInstant(ZoneOffset.UTC);

        var stadtfest = eventService.create(
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

        Instant wettbewerb =
                LocalDate.now().plusMonths(2).withDayOfMonth(20).atTime(8, 0).toInstant(ZoneOffset.UTC);
        Instant wettbewerbEnd =
                LocalDate.now().plusMonths(2).withDayOfMonth(20).atTime(17, 0).toInstant(ZoneOffset.UTC);
        Instant wettbewerbDeadline =
                LocalDate.now().plusMonths(2).withDayOfMonth(1).atTime(23, 59).toInstant(ZoneOffset.UTC);

        var kreisWettbewerb = eventService.create(
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

        // Add some registrations
        LocalDate tagDate = LocalDate.now().plusMonths(1).withDayOfMonth(15);
        LocalDate stadtfestDate = LocalDate.now().plusWeeks(3);
        for (int i = 0; i < 8 && i < fortgeschrittenMembers.size(); i++) {
            eventRepository.createRegistration(
                    tagDerOffenenTuer.id(),
                    fortgeschrittenMembers.get(i).id(),
                    tagDate,
                    RegistrationStatus.ACCEPTED,
                    null);
        }
        for (int i = 0; i < 5 && i < anfaengerMembers.size(); i++) {
            eventRepository.createRegistration(
                    stadtfest.id(), anfaengerMembers.get(i).id(), stadtfestDate, RegistrationStatus.ACCEPTED, null);
        }
        for (int i = 0; i < 3 && i < fortgeschrittenMembers.size(); i++) {
            eventRepository.createRegistration(
                    stadtfest.id(),
                    fortgeschrittenMembers.get(i).id(),
                    stadtfestDate,
                    RegistrationStatus.ACCEPTED,
                    null);
        }
        // Some pending registrations for Kreiswettbewerb
        LocalDate kwDate = LocalDate.now().plusMonths(2).withDayOfMonth(20);
        for (int i = 0; i < 6 && i < fortgeschrittenMembers.size(); i++) {
            eventRepository.createRegistration(
                    kreisWettbewerb.id(), fortgeschrittenMembers.get(i).id(), kwDate, RegistrationStatus.PENDING, null);
        }

        // Declined registrations for Stadtfest
        for (int i = 5; i < 8 && i < anfaengerMembers.size(); i++) {
            eventRepository.createRegistration(
                    stadtfest.id(), anfaengerMembers.get(i).id(), stadtfestDate, RegistrationStatus.DECLINED, null);
        }
        // Declined registrations for Kreiswettbewerb
        for (int i = 6; i < 9 && i < fortgeschrittenMembers.size(); i++) {
            eventRepository.createRegistration(
                    kreisWettbewerb.id(),
                    fortgeschrittenMembers.get(i).id(),
                    kwDate,
                    RegistrationStatus.DECLINED,
                    null);
        }
        // Denied registration for Tag der offenen Tuer
        if (anfaengerMembers.size() > 9) {
            eventRepository.createRegistration(
                    tagDerOffenenTuer.id(), anfaengerMembers.get(9).id(), tagDate, RegistrationStatus.DENIED, null);
        }

        // -- Oeffentlichkeitsarbeit events --
        var catOeffentlichkeit = eventRepository.createCategory(stationId, "Öffentlichkeitsarbeit", 3);
        eventRepository.updateCategory(
                catOeffentlichkeit.id(), catOeffentlichkeit.name(), catOeffentlichkeit.position(), null, true);
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
            LocalDate eventDate = LocalDate.now().minusWeeks(oeNames.length - e);
            Instant oeStart = eventDate.atTime(10, 0).toInstant(ZoneOffset.UTC);
            Instant oeEnd = eventDate.atTime(16, 0).toInstant(ZoneOffset.UTC);
            var oeEvent = eventService.create(
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
                    EventFieldType.LOCATION,
                    EventFieldConfig.parse("{}"),
                    oeOrte[e],
                    0,
                    true,
                    null,
                    true);
            eventFieldRepository.create(
                    oeEvent.id(),
                    "Treffpunkt",
                    EventFieldType.STRING,
                    EventFieldConfig.parse("{}"),
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
                eventRepository.createRegistration(
                        oeEvent.id(), allMembers.get(rotatedIdx).id(), eventDate, status, null);
            }
        }

        // One open event with pending (unconfirmed) registrations
        LocalDate openDate = LocalDate.now().plusWeeks(1);
        Instant openStart = openDate.atTime(9, 0).toInstant(ZoneOffset.UTC);
        Instant openEnd = openDate.atTime(15, 0).toInstant(ZoneOffset.UTC);
        Instant openDeadline = LocalDate.now().plusDays(3).atTime(23, 59).toInstant(ZoneOffset.UTC);
        var oeOpen = eventService.create(
                stationId,
                "Blaulichtmeile Bürgerfest",
                "Öffentlichkeitsarbeit — Anmeldung offen",
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
                EventFieldType.LOCATION,
                EventFieldConfig.parse("{}"),
                "Rathausplatz Musterstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                oeOpen.id(),
                "Treffpunkt",
                EventFieldType.STRING,
                EventFieldConfig.parse("{}"),
                "Feuerwehrgerätehaus 08:30",
                1,
                true,
                null,
                true);
        eventFieldRepository.create(
                oeOpen.id(),
                "Hinweis",
                EventFieldType.STRING,
                EventFieldConfig.parse("{}"),
                "Dienstkleidung und Ausrüstung mitbringen",
                2,
                false,
                null,
                false);
        // 14 registrations: 6 accepted, 8 pending (not yet confirmed)
        int openCount = Math.min(14, allMembers.size());
        for (int i = 0; i < openCount; i++) {
            var status = i < 6 ? RegistrationStatus.ACCEPTED : RegistrationStatus.PENDING;
            eventRepository.createRegistration(oeOpen.id(), allMembers.get(i).id(), openDate, status, null);
        }

        // -- Event Fields --
        // Per-event fields
        eventFieldRepository.create(
                tagDerOffenenTuer.id(),
                "Ort",
                EventFieldType.LOCATION,
                EventFieldConfig.parse("{}"),
                "Feuerwehrhaus Musterstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                tagDerOffenenTuer.id(),
                "Treffpunkt",
                EventFieldType.STRING,
                EventFieldConfig.parse("{}"),
                "Haupteingang",
                1,
                true,
                null,
                true);
        eventFieldRepository.create(
                tagDerOffenenTuer.id(),
                "Hinweis",
                EventFieldType.STRING,
                EventFieldConfig.parse("{}"),
                "Dienstkleidung tragen",
                2,
                false,
                null,
                false);
        eventFieldRepository.create(
                stadtfest.id(),
                "Ort",
                EventFieldType.LOCATION,
                EventFieldConfig.parse("{}"),
                "Marktplatz Musterstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                stadtfest.id(),
                "Treffpunkt",
                EventFieldType.STRING,
                EventFieldConfig.parse("{}"),
                "Stand der Jugendfeuerwehr",
                1,
                true,
                null,
                true);
        eventFieldRepository.create(
                kreisWettbewerb.id(),
                "Ort",
                EventFieldType.LOCATION,
                EventFieldConfig.parse("{}"),
                "Sportplatz Nachbarstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                kreisWettbewerb.id(),
                "Hinweis",
                EventFieldType.STRING,
                EventFieldConfig.parse("{}"),
                "Wettkampfkleidung und Ausrüstung mitbringen",
                1,
                false,
                null,
                false);
        // Recurring event fields
        eventFieldRepository.create(
                evUebung.id(),
                "Ort",
                EventFieldType.LOCATION,
                EventFieldConfig.parse("{}"),
                "Feuerwehrhaus Musterstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                evUebung.id(),
                "Hinweis",
                EventFieldType.STRING,
                EventFieldConfig.parse("{}"),
                "Sportkleidung mitbringen",
                1,
                false,
                null,
                false);
        eventFieldRepository.create(
                evGesamt.id(),
                "Ort",
                EventFieldType.LOCATION,
                EventFieldConfig.parse("{}"),
                "Feuerwehrhaus Musterstadt",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                evGesamt.id(),
                "Treffpunkt",
                EventFieldType.STRING,
                EventFieldConfig.parse("{}"),
                "Fahrzeughalle",
                1,
                true,
                null,
                true);
        eventFieldRepository.create(
                theorieabend.id(),
                "Ort",
                EventFieldType.LOCATION,
                EventFieldConfig.parse("{}"),
                "Schulungsraum Feuerwehrhaus",
                0,
                true,
                null,
                true);
        eventFieldRepository.create(
                theorieabend.id(),
                "Hinweis",
                EventFieldType.STRING,
                EventFieldConfig.parse("{}"),
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
                        new EventTemplateFieldData(
                                "Ort", EventFieldType.LOCATION, EventFieldConfig.parse("{}"), 0, true, true, null),
                        new EventTemplateFieldData(
                                "Treffpunkt",
                                EventFieldType.STRING,
                                EventFieldConfig.parse("{}"),
                                1,
                                true,
                                true,
                                null)));
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
                        new EventTemplateFieldData(
                                "Ort", EventFieldType.LOCATION, EventFieldConfig.parse("{}"), 0, true, true, null),
                        new EventTemplateFieldData(
                                "Thema", EventFieldType.STRING, EventFieldConfig.parse("{}"), 1, true, false, null)));
        log.info("Demo: Created event templates");
    }
}
