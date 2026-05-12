/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Database;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.entity.AttendanceEntry;
import dev.chojo.ember.entity.CheckResult;
import dev.chojo.ember.entity.ExchangeStatus;
import dev.chojo.ember.entity.ProfileFieldScope;
import dev.chojo.ember.entity.StationMember;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.AttendanceRepository;
import dev.chojo.ember.repository.EventRepository;
import dev.chojo.ember.repository.ExchangeRepository;
import dev.chojo.ember.repository.InventoryCheckRepository;
import dev.chojo.ember.repository.InventoryRepository;
import dev.chojo.ember.repository.MemberGroupRepository;
import dev.chojo.ember.repository.NewsRepository;
import dev.chojo.ember.repository.ProcurementRepository;
import dev.chojo.ember.repository.ProfileFieldRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import dev.chojo.ember.repository.StationRepository;
import dev.chojo.ember.repository.UserTagRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

@Singleton
public class DemoService {
    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    private static final String PASSWORD = "demo";

    private final Demo demoConfig;
    private final Database databaseConfig;
    private final DataSource dataSource;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final EventRepository eventRepository;
    private final AttendanceRepository attendanceRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryCheckRepository inventoryCheckRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final NewsRepository newsRepository;
    private final ExchangeRepository exchangeRepository;
    private final ProcurementRepository procurementRepository;
    private final UserTagRepository userTagRepository;
    private final PasswordHasher passwordHasher;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public DemoService(
            Demo demoConfig,
            Database databaseConfig,
            DataSource dataSource,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            EventRepository eventRepository,
            AttendanceRepository attendanceRepository,
            InventoryRepository inventoryRepository,
            InventoryCheckRepository inventoryCheckRepository,
            ProfileFieldRepository profileFieldRepository,
            NewsRepository newsRepository,
            ExchangeRepository exchangeRepository,
            ProcurementRepository procurementRepository,
            UserTagRepository userTagRepository,
            PasswordHasher passwordHasher) {
        this.demoConfig = demoConfig;
        this.databaseConfig = databaseConfig;
        this.dataSource = dataSource;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.eventRepository = eventRepository;
        this.attendanceRepository = attendanceRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryCheckRepository = inventoryCheckRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.newsRepository = newsRepository;
        this.exchangeRepository = exchangeRepository;
        this.procurementRepository = procurementRepository;
        this.userTagRepository = userTagRepository;
        this.passwordHasher = passwordHasher;
    }

    public boolean isEnabled() {
        return demoConfig.enabled() || demoConfig.dev();
    }

    public boolean isDemo() {
        return demoConfig.enabled();
    }

    public boolean isDev() {
        return demoConfig.dev();
    }

    public void initialize() {
        if (demoConfig.dev()) {
            log.info("Dev mode enabled. Seeding database once...");
            resetAndSeed();
            return;
        }
        if (!demoConfig.enabled()) return;
        log.info("Demo mode enabled. Reset interval: {} hours", demoConfig.resetIntervalHours());
        resetAndSeed();
        scheduler.scheduleAtFixedRate(
                this::resetAndSeed, demoConfig.resetIntervalHours(), demoConfig.resetIntervalHours(), TimeUnit.HOURS);
    }

    public void resetAndSeed() {
        log.info("Demo: Wiping and re-seeding database...");
        try {
            wipeDatabase();
            seedData();
            log.info("Demo: Database seeded successfully");
        } catch (Exception e) {
            log.error("Demo: Failed to seed database", e);
        }
    }

    private void wipeDatabase() {
        String schema = databaseConfig.schema();
        Query.query("DROP SCHEMA IF EXISTS " + schema + " CASCADE;").single().delete();
        Query.query("CREATE SCHEMA " + schema + ";").single().insert();
        try {
            SqlUpdater.builder(dataSource, PostgreSql.get())
                    .setReplacements(new QueryReplacement("ember_schema", schema))
                    .setSchemas(schema)
                    .execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to re-run migrations after wipe", e);
        }
    }

    private void seedData() {
        String hash = passwordHasher.hash(PASSWORD);
        var rng = new Random(42); // Deterministic for reproducibility

        // -- Admin --
        var admin = accountRepository.create("admin@ember.local", "Admin", "Demo", true);
        accountRepository.createCredential(admin.id(), hash);
        accountRepository.addAccountRole(admin.id(), "ADMIN");

        // -- Station --
        var station = stationRepository.create("Jugendfeuerwehr Musterstadt");
        stationRepository.updateTimezone(station.id(), "Europe/Berlin");
        stationRepository.updateLocale(station.id(), "de-DE");

        var adminMember = stationMemberRepository.create(station.id(), admin.id());
        var managerRole = stationMemberRepository.findRoleByName(Roles.MANAGER).orElseThrow();
        var loginRole = stationMemberRepository.findRoleByName(Roles.LOGIN).orElseThrow();
        var memberRole = stationMemberRepository.findRoleByName(Roles.MEMBER).orElseThrow();
        var teamRole = stationMemberRepository.findRoleByName(Roles.TEAM).orElseThrow();
        var memberManagerRole =
                stationMemberRepository.findRoleByName(Roles.MEMBER_MANAGER).orElseThrow();
        var attendanceMgmt = stationMemberRepository
                .findRoleByName(Roles.ATTENDENCE_MANAGEMENT)
                .orElseThrow();
        var eventMgmt =
                stationMemberRepository.findRoleByName(Roles.EVENT_MANAGEMENT).orElseThrow();
        var memberMgmt =
                stationMemberRepository.findRoleByName(Roles.MEMBER_MANAGEMENT).orElseThrow();

        stationMemberRepository.addRole(adminMember.id(), managerRole.id());
        stationMemberRepository.addRole(adminMember.id(), loginRole.id());

        // -- Groups --
        var groupBetreuer = memberGroupRepository.create(station.id(), "Betreuer");
        var groupEltern = memberGroupRepository.create(station.id(), "Eltern");
        var groupAnfaenger = memberGroupRepository.create(station.id(), "Anfänger");
        var groupFortgeschritten = memberGroupRepository.create(station.id(), "Fortgeschritten");

        // -- Profile fields: TEAM scope (Betreuer) --
        var fieldJuleica =
                profileFieldRepository.create(station.id(), "Juleica", "boolean", "{}", 0, ProfileFieldScope.TEAM);
        var fieldJuleicaAblauf = profileFieldRepository.create(
                station.id(), "Juleica Ablaufdatum", "date", "{}", 1, ProfileFieldScope.TEAM);
        var fieldFuehrerschein =
                profileFieldRepository.create(station.id(), "Führerschein", "boolean", "{}", 2, ProfileFieldScope.TEAM);
        var fieldFuehrerscheinAblauf = profileFieldRepository.create(
                station.id(), "Führerschein Ablaufdatum", "date", "{}", 3, ProfileFieldScope.TEAM);

        // -- Profile fields: MEMBER_MANAGER scope (Eltern) --
        var fieldTelefon = profileFieldRepository.create(
                station.id(), "Telefonnummer", "text", "{}", 0, ProfileFieldScope.MEMBER_MANAGER);
        var fieldNewsletter = profileFieldRepository.create(
                station.id(),
                "Newsletter per Mail",
                "boolean",
                "{\"defaultValue\":true}",
                1,
                ProfileFieldScope.MEMBER_MANAGER);

        // -- Profile fields: MEMBER scope (kids) --
        var fieldAllergien =
                profileFieldRepository.create(station.id(), "Allergien", "text", "{}", 0, ProfileFieldScope.MEMBER);
        var fieldLeistungsspange = profileFieldRepository.create(
                station.id(), "Leistungsspange", "boolean", "{}", 1, ProfileFieldScope.MEMBER);
        var fieldLeistungsspangeDatum = profileFieldRepository.create(
                station.id(), "Leistungsspange Datum", "date", "{}", 2, ProfileFieldScope.MEMBER);
        var fieldJF1 = profileFieldRepository.create(
                station.id(), "Jugendflamme 1", "boolean", "{}", 3, ProfileFieldScope.MEMBER);
        var fieldJF1Datum = profileFieldRepository.create(
                station.id(), "Jugendflamme 1 Datum", "date", "{}", 4, ProfileFieldScope.MEMBER);
        var fieldJF2 = profileFieldRepository.create(
                station.id(), "Jugendflamme 2", "boolean", "{}", 5, ProfileFieldScope.MEMBER);
        var fieldJF2Datum = profileFieldRepository.create(
                station.id(), "Jugendflamme 2 Datum", "date", "{}", 6, ProfileFieldScope.MEMBER);
        var fieldJF3 = profileFieldRepository.create(
                station.id(), "Jugendflamme 3", "boolean", "{}", 7, ProfileFieldScope.MEMBER);
        var fieldJF3Datum = profileFieldRepository.create(
                station.id(), "Jugendflamme 3 Datum", "date", "{}", 8, ProfileFieldScope.MEMBER);

        // -- Users --
        // Betreuer (team role, in Betreuer group)
        record DemoUser(String firstName, String lastName) {}
        var betreuer = List.of(
                new DemoUser("Max", "Mustermann"),
                new DemoUser("Anna", "Schmidt"),
                new DemoUser("Thomas", "Müller"),
                new DemoUser("Lisa", "Weber"),
                new DemoUser("Michael", "Wagner"));

        // Eltern (member_manager role, in Eltern group)
        var eltern = List.of(
                new DemoUser("Hans", "Berger"),
                new DemoUser("Petra", "Frank"),
                new DemoUser("Klaus", "Friedrich"),
                new DemoUser("Monika", "Roth"),
                new DemoUser("Jürgen", "Beck"),
                new DemoUser("Ursula", "Lorenz"),
                new DemoUser("Werner", "Baumann"),
                new DemoUser("Ingrid", "Franke"),
                new DemoUser("Helmut", "Albrecht"),
                new DemoUser("Gerda", "Simon"));

        // Anfänger kids (member role, in Anfänger group)
        var anfaenger = List.of(
                new DemoUser("Tim", "Schulze"),
                new DemoUser("Lena", "Maier"),
                new DemoUser("Lukas", "Köhler"),
                new DemoUser("Sophie", "Lehmann"),
                new DemoUser("Felix", "König"),
                new DemoUser("Emma", "Huber"),
                new DemoUser("Jonas", "Kaiser"),
                new DemoUser("Marie", "Fuchs"),
                new DemoUser("Niklas", "Peters"),
                new DemoUser("Lea", "Lang"),
                new DemoUser("Paul", "Scholz"),
                new DemoUser("Hannah", "Möller"),
                new DemoUser("Leon", "Weiß"));

        // Fortgeschritten kids (member role, in Fortgeschritten group)
        var fortgeschritten = List.of(
                new DemoUser("Mia", "Jung"),
                new DemoUser("Ben", "Hahn"),
                new DemoUser("Laura", "Koch"),
                new DemoUser("Markus", "Bauer"),
                new DemoUser("Nina", "Richter"),
                new DemoUser("Christian", "Wolf"),
                new DemoUser("Sandra", "Schröder"),
                new DemoUser("Tobias", "Neumann"),
                new DemoUser("Katharina", "Schwarz"),
                new DemoUser("Andreas", "Zimmermann"),
                new DemoUser("Melanie", "Braun"),
                new DemoUser("Patrick", "Krüger"));

        var betreuerMembers = new ArrayList<StationMember>();
        var elternMembers = new ArrayList<StationMember>();
        var anfaengerMembers = new ArrayList<StationMember>();
        var fortgeschrittenMembers = new ArrayList<StationMember>();

        // Create Betreuer
        for (var u : betreuer) {
            var m = createUser(u.firstName(), u.lastName(), hash, station.id(), loginRole.id(), memberRole.id());
            stationMemberRepository.addRole(m.id(), teamRole.id());
            stationMemberRepository.addRole(m.id(), attendanceMgmt.id());
            stationMemberRepository.addRole(m.id(), eventMgmt.id());
            stationMemberRepository.addRole(m.id(), memberMgmt.id());
            memberGroupRepository.addMember(groupBetreuer.id(), m.id());
            betreuerMembers.add(m);

            // Profile data
            boolean hasJuleica = rng.nextBoolean();
            profileFieldRepository.setValue(m.id(), fieldJuleica.id(), Boolean.toString(hasJuleica));
            if (hasJuleica) {
                profileFieldRepository.setValue(
                        m.id(),
                        fieldJuleicaAblauf.id(),
                        jsonStr(LocalDate.now().plusMonths(rng.nextInt(24)).toString()));
            }
            profileFieldRepository.setValue(m.id(), fieldFuehrerschein.id(), "true");
            profileFieldRepository.setValue(
                    m.id(),
                    fieldFuehrerscheinAblauf.id(),
                    jsonStr(LocalDate.now().plusYears(rng.nextInt(5) + 1).toString()));
        }

        // Create Eltern
        for (var u : eltern) {
            var m = createUser(u.firstName(), u.lastName(), hash, station.id(), loginRole.id(), memberRole.id());
            stationMemberRepository.addRole(m.id(), memberManagerRole.id());
            memberGroupRepository.addMember(groupEltern.id(), m.id());
            elternMembers.add(m);

            // Profile data
            profileFieldRepository.setValue(
                    m.id(), fieldTelefon.id(), jsonStr("0151 " + (10000000 + rng.nextInt(90000000))));
            profileFieldRepository.setValue(m.id(), fieldNewsletter.id(), Boolean.toString(rng.nextBoolean()));
        }

        // Create Anfänger
        for (var u : anfaenger) {
            var m = createUser(u.firstName(), u.lastName(), hash, station.id(), loginRole.id(), memberRole.id());
            memberGroupRepository.addMember(groupAnfaenger.id(), m.id());
            anfaengerMembers.add(m);

            // Some have Jugendflamme 1
            if (rng.nextInt(3) == 0) {
                profileFieldRepository.setValue(m.id(), fieldJF1.id(), "true");
                profileFieldRepository.setValue(
                        m.id(),
                        fieldJF1Datum.id(),
                        jsonStr(LocalDate.now().minusMonths(rng.nextInt(12)).toString()));
            }
            if (rng.nextBoolean()) {
                profileFieldRepository.setValue(m.id(), fieldAllergien.id(), jsonStr(randomAllergy(rng)));
            }
        }

        // Create Fortgeschritten
        for (var u : fortgeschritten) {
            var m = createUser(u.firstName(), u.lastName(), hash, station.id(), loginRole.id(), memberRole.id());
            memberGroupRepository.addMember(groupFortgeschritten.id(), m.id());
            fortgeschrittenMembers.add(m);

            // Most have JF1, some JF2, few JF3
            profileFieldRepository.setValue(m.id(), fieldJF1.id(), "true");
            profileFieldRepository.setValue(
                    m.id(),
                    fieldJF1Datum.id(),
                    jsonStr(LocalDate.now().minusMonths(rng.nextInt(24) + 6).toString()));
            if (rng.nextInt(3) != 0) {
                profileFieldRepository.setValue(m.id(), fieldJF2.id(), "true");
                profileFieldRepository.setValue(
                        m.id(),
                        fieldJF2Datum.id(),
                        jsonStr(LocalDate.now().minusMonths(rng.nextInt(12)).toString()));
            }
            if (rng.nextInt(5) == 0) {
                profileFieldRepository.setValue(m.id(), fieldJF3.id(), "true");
                profileFieldRepository.setValue(
                        m.id(),
                        fieldJF3Datum.id(),
                        jsonStr(LocalDate.now().minusMonths(rng.nextInt(6)).toString()));
                profileFieldRepository.setValue(m.id(), fieldLeistungsspange.id(), "true");
                profileFieldRepository.setValue(
                        m.id(),
                        fieldLeistungsspangeDatum.id(),
                        jsonStr(LocalDate.now().minusMonths(rng.nextInt(3)).toString()));
            }
            if (rng.nextBoolean()) {
                profileFieldRepository.setValue(m.id(), fieldAllergien.id(), jsonStr(randomAllergy(rng)));
            }
        }

        // -- Manager assignments: each Eltern manages 2-3 kids --
        var allKids = new ArrayList<>(anfaengerMembers);
        allKids.addAll(fortgeschrittenMembers);
        int kidIdx = 0;
        for (var elternMember : elternMembers) {
            int count = 2 + rng.nextInt(2); // 2 or 3 kids
            for (int i = 0; i < count && kidIdx < allKids.size(); i++, kidIdx++) {
                stationMemberRepository.addManager(
                        elternMember.id(), allKids.get(kidIdx).id());
            }
        }
        // Some Betreuer also manage remaining kids
        for (int i = 0; kidIdx < allKids.size(); kidIdx++, i++) {
            stationMemberRepository.addManager(
                    betreuerMembers.get(i % betreuerMembers.size()).id(),
                    allKids.get(kidIdx).id());
        }

        // -- Attendance templates --
        var templateAnfaenger = attendanceRepository.createTemplate(station.id(), "Übung Anfänger");
        attendanceRepository.setTemplateGroups(
                templateAnfaenger.id(), List.of(new AttendanceRepository.TemplateGroup(groupAnfaenger.id(), 0)));
        attendanceRepository.createTemplateField(
                templateAnfaenger.id(), "Thema", "string", "{\"defaultValue\":\"Grundausbildung\"}", 0);

        var templateFort = attendanceRepository.createTemplate(station.id(), "Übung Fortgeschritten");
        attendanceRepository.setTemplateGroups(
                templateFort.id(), List.of(new AttendanceRepository.TemplateGroup(groupFortgeschritten.id(), 0)));
        attendanceRepository.createTemplateField(templateFort.id(), "Thema", "string", "{}", 0);

        var templateGesamt = attendanceRepository.createTemplate(station.id(), "Gesamtübung");
        attendanceRepository.setTemplateGroups(
                templateGesamt.id(),
                List.of(
                        new AttendanceRepository.TemplateGroup(groupAnfaenger.id(), 0),
                        new AttendanceRepository.TemplateGroup(groupFortgeschritten.id(), 1)));

        // -- Events --
        Instant monStart = LocalDate.now().atTime(17, 30).toInstant(ZoneOffset.UTC);
        Instant monEnd = LocalDate.now().atTime(19, 0).toInstant(ZoneOffset.UTC);
        Instant wedStart = LocalDate.now().atTime(18, 0).toInstant(ZoneOffset.UTC);
        Instant wedEnd = LocalDate.now().atTime(19, 30).toInstant(ZoneOffset.UTC);
        Instant satStart = LocalDate.now().atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant satEnd = LocalDate.now().atTime(13, 0).toInstant(ZoneOffset.UTC);

        eventRepository.create(
                station.id(),
                "Übung Anfänger",
                "Grundausbildung für Anfänger",
                "RECURRING",
                1,
                monStart,
                monEnd,
                templateAnfaenger.id(),
                false,
                null,
                false,
                null);
        eventRepository.create(
                station.id(),
                "Übung Fortgeschritten",
                "Training für Fortgeschrittene",
                "RECURRING",
                3,
                wedStart,
                wedEnd,
                templateFort.id(),
                false,
                null,
                false,
                null);
        eventRepository.create(
                station.id(),
                "Gesamtübung",
                "Gemeinsame Übung aller Gruppen",
                "RECURRING",
                6,
                satStart,
                satEnd,
                templateGesamt.id(),
                false,
                null,
                false,
                null);

        // -- Past attendance sessions (full year + current year so far) --
        seedAttendanceSessions(
                rng,
                templateAnfaenger,
                templateFort,
                templateGesamt,
                anfaengerMembers,
                fortgeschrittenMembers,
                betreuerMembers);

        // -- Inventory --
        seedInventory(station.id(), memberRole.id(), rng, anfaengerMembers, fortgeschrittenMembers);

        // -- Inventory checks (done by Betreuer) --
        seedInventoryChecks(station.id(), rng, betreuerMembers, anfaengerMembers, fortgeschrittenMembers);

        // -- Event categories --
        var catUebung = eventRepository.createCategory(station.id(), "Übungen", 0);
        var catVeranstaltung = eventRepository.createCategory(station.id(), "Veranstaltungen", 1);
        var catWettbewerb = eventRepository.createCategory(station.id(), "Wettbewerbe", 2);

        // Update existing recurring events with category
        // (events were created above without categories, but we can't easily update — create new ones with categories
        // instead)

        // -- Registration-required events --
        Instant nextMonth =
                LocalDate.now().plusMonths(1).withDayOfMonth(15).atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant nextMonthEnd =
                LocalDate.now().plusMonths(1).withDayOfMonth(15).atTime(16, 0).toInstant(ZoneOffset.UTC);
        Instant deadline =
                LocalDate.now().plusMonths(1).withDayOfMonth(10).atTime(23, 59).toInstant(ZoneOffset.UTC);

        var tagDerOffenenTuer = eventRepository.create(
                station.id(),
                "Tag der offenen Tür",
                "Öffentlichkeitsarbeit: Vorführungen und Mitmach-Aktionen",
                "ONE_TIME",
                null,
                nextMonth,
                nextMonthEnd,
                null,
                true,
                deadline,
                true,
                catVeranstaltung.id());

        Instant oeffentlichkeit = LocalDate.now().plusWeeks(3).atTime(14, 0).toInstant(ZoneOffset.UTC);
        Instant oeffentlichkeitEnd = LocalDate.now().plusWeeks(3).atTime(17, 0).toInstant(ZoneOffset.UTC);
        Instant oeffentlichkeitDeadline =
                LocalDate.now().plusWeeks(2).atTime(23, 59).toInstant(ZoneOffset.UTC);

        var stadtfest = eventRepository.create(
                station.id(),
                "Stadtfest Musterstadt",
                "Stand der Jugendfeuerwehr beim Stadtfest",
                "ONE_TIME",
                null,
                oeffentlichkeit,
                oeffentlichkeitEnd,
                null,
                true,
                oeffentlichkeitDeadline,
                false,
                catVeranstaltung.id());

        Instant wettbewerb =
                LocalDate.now().plusMonths(2).withDayOfMonth(20).atTime(8, 0).toInstant(ZoneOffset.UTC);
        Instant wettbewerbEnd =
                LocalDate.now().plusMonths(2).withDayOfMonth(20).atTime(17, 0).toInstant(ZoneOffset.UTC);
        Instant wettbewerbDeadline =
                LocalDate.now().plusMonths(2).withDayOfMonth(1).atTime(23, 59).toInstant(ZoneOffset.UTC);

        var kreisWettbewerb = eventRepository.create(
                station.id(),
                "Kreiswettbewerb",
                "Jährlicher Kreiswettbewerb der Jugendfeuerwehren",
                "ONE_TIME",
                null,
                wettbewerb,
                wettbewerbEnd,
                null,
                true,
                wettbewerbDeadline,
                true,
                catWettbewerb.id());

        // Add some registrations
        LocalDate tagDate = LocalDate.now().plusMonths(1).withDayOfMonth(15);
        LocalDate stadtfestDate = LocalDate.now().plusWeeks(3);
        for (int i = 0; i < 8 && i < fortgeschrittenMembers.size(); i++) {
            eventRepository.createRegistration(
                    tagDerOffenenTuer.id(), fortgeschrittenMembers.get(i).id(), tagDate, "ACCEPTED");
        }
        for (int i = 0; i < 5 && i < anfaengerMembers.size(); i++) {
            eventRepository.createRegistration(
                    stadtfest.id(), anfaengerMembers.get(i).id(), stadtfestDate, "ACCEPTED");
        }
        for (int i = 0; i < 3 && i < fortgeschrittenMembers.size(); i++) {
            eventRepository.createRegistration(
                    stadtfest.id(), fortgeschrittenMembers.get(i).id(), stadtfestDate, "ACCEPTED");
        }
        // Some pending registrations for Kreiswettbewerb
        LocalDate kwDate = LocalDate.now().plusMonths(2).withDayOfMonth(20);
        for (int i = 0; i < 6 && i < fortgeschrittenMembers.size(); i++) {
            eventRepository.createRegistration(
                    kreisWettbewerb.id(), fortgeschrittenMembers.get(i).id(), kwDate, "PENDING");
        }

        // -- News --
        var news1 = newsRepository.create(
                station.id(),
                "Willkommen bei der Jugendfeuerwehr!",
                "Herzlich willkommen auf unserer neuen Plattform! Hier findet ihr alle wichtigen Informationen rund um unsere **Jugendfeuerwehr**.\n\n## Was ist neu?\n\n- Übersicht über Termine und Anwesenheit\n- Inventarverwaltung für Ausrüstung\n- Profilverwaltung für alle Mitglieder\n\nBei Fragen wendet euch bitte an eure Betreuer.",
                "<h1>Willkommen bei der Jugendfeuerwehr!</h1><p>Herzlich willkommen auf unserer neuen Plattform! Hier findet ihr alle wichtigen Informationen rund um unsere <strong>Jugendfeuerwehr</strong>.</p><h2>Was ist neu?</h2><ul><li>Übersicht über Termine und Anwesenheit</li><li>Inventarverwaltung für Ausrüstung</li><li>Profilverwaltung für alle Mitglieder</li></ul><p>Bei Fragen wendet euch bitte an eure Betreuer.</p>",
                adminMember.id());
        var news2 = newsRepository.create(
                station.id(),
                "Kreiswettbewerb: Anmeldung geöffnet",
                "Die Anmeldung zum **Kreiswettbewerb** am 20. des übernächsten Monats ist jetzt geöffnet!\n\nBitte meldet euch über die Terminseite an. Die Plätze sind begrenzt.\n\n*Teilnehmen dürfen alle Fortgeschrittenen.*",
                "<p>Die Anmeldung zum <strong>Kreiswettbewerb</strong> am 20. des übernächsten Monats ist jetzt geöffnet!</p><p>Bitte meldet euch über die Terminseite an. Die Plätze sind begrenzt.</p><p><em>Teilnehmen dürfen alle Fortgeschrittenen.</em></p>",
                betreuerMembers.getFirst().id());

        // Comments on news
        var comment1 = newsRepository.createComment(
                news1.id(), null, elternMembers.get(0).id(), "Super, endlich eine moderne Plattform!");
        newsRepository.createComment(
                news1.id(), comment1.id(), betreuerMembers.get(0).id(), "Danke! Bei Fragen einfach melden.");
        newsRepository.createComment(
                news1.id(), null, elternMembers.get(1).id(), "Kann man hier auch Abwesenheiten eintragen?");
        newsRepository.createComment(
                news2.id(), null, fortgeschrittenMembers.get(0).id(), "Ich bin dabei! 💪");
        var comment2 = newsRepository.createComment(
                news2.id(), null, fortgeschrittenMembers.get(1).id(), "Wie viele Plätze gibt es?");
        newsRepository.createComment(
                news2.id(), comment2.id(), betreuerMembers.get(0).id(), "Wir haben 8 Plätze. Bitte schnell anmelden!");

        var news3 = newsRepository.create(
                station.id(),
                "Neue Ausrüstung eingetroffen",
                "Die bestellten **Helme und Handschuhe** sind eingetroffen! Die Verteilung findet bei der nächsten Übung statt.\n\nBitte prüft eure Größen im Inventar und meldet euch bei Unstimmigkeiten.",
                "<p>Die bestellten <strong>Helme und Handschuhe</strong> sind eingetroffen! Die Verteilung findet bei der nächsten Übung statt.</p><p>Bitte prüft eure Größen im Inventar und meldet euch bei Unstimmigkeiten.</p>",
                betreuerMembers.get(1).id());

        newsRepository.create(
                station.id(),
                "Sommerferien: Übungspause",
                "Während der **Sommerferien** finden keine regulären Übungen statt. Der Übungsbetrieb startet wieder am ersten Montag nach den Ferien.\n\nWir wünschen allen schöne Ferien! ☀️",
                "<p>Während der <strong>Sommerferien</strong> finden keine regulären Übungen statt. Der Übungsbetrieb startet wieder am ersten Montag nach den Ferien.</p><p>Wir wünschen allen schöne Ferien! ☀️</p>",
                adminMember.id());

        newsRepository.createComment(
                news3.id(), null, elternMembers.get(2).id(), "Werden die alten Helme eingesammelt?");
        newsRepository.createComment(
                news3.id(), null, betreuerMembers.get(1).id(), "Ja, bitte zur nächsten Übung mitbringen.");

        // -- User Tags --
        var tagWettkampf = userTagRepository.create(station.id(), "Wettkampfgruppe");
        var tagErsthelfer = userTagRepository.create(station.id(), "Ersthelfer");
        // Add some Fortgeschritten to Wettkampfgruppe
        for (int i = 0; i < 6 && i < fortgeschrittenMembers.size(); i++) {
            userTagRepository.addMember(
                    tagWettkampf.id(), fortgeschrittenMembers.get(i).id());
        }
        // Add some Betreuer as Ersthelfer
        for (int i = 0; i < 3 && i < betreuerMembers.size(); i++) {
            userTagRepository.addMember(
                    tagErsthelfer.id(), betreuerMembers.get(i).id());
        }

        // -- Equipment Exchange Requests --
        // A kid requesting a helmet exchange
        var helmItems =
                inventoryRepository.findItemsByMember(anfaengerMembers.get(0).id());
        if (!helmItems.isEmpty()) {
            var helmExchange = exchangeRepository.create(
                    station.id(),
                    anfaengerMembers.get(0).id(),
                    helmItems.getFirst().id(),
                    helmItems.getFirst().inventoryId(),
                    null,
                    "Helm ist zu klein geworden");
            // Manager progresses it
            exchangeRepository.updateStatus(helmExchange.id(), ExchangeStatus.RECEIVED);
            exchangeRepository.createLog(
                    helmExchange.id(),
                    ExchangeStatus.ANNOUNCED,
                    ExchangeStatus.RECEIVED,
                    betreuerMembers.get(0).id(),
                    "Neuer Helm bestellt");
        }
        // Another kid requesting blouson exchange
        var blousonItems = inventoryRepository.findItemsByMember(
                fortgeschrittenMembers.get(0).id());
        if (blousonItems.size() > 1) {
            exchangeRepository.create(
                    station.id(),
                    fortgeschrittenMembers.get(0).id(),
                    blousonItems.get(1).id(),
                    blousonItems.get(1).inventoryId(),
                    null,
                    "Blouson hat einen Riss");
        }

        // -- Equipment Procurement --
        var inventories = inventoryRepository.findByStation(station.id());
        if (!inventories.isEmpty()) {
            // Need a new pair of gloves for a kid
            var handschuheInv = inventories.stream()
                    .filter(i -> "Handschuhe".equals(i.name()))
                    .findFirst();
            if (handschuheInv.isPresent()) {
                var sizes = inventoryRepository.findSizes(handschuheInv.get().id());
                procurementRepository.create(
                        station.id(),
                        handschuheInv.get().id(),
                        anfaengerMembers.get(2).id(),
                        sizes.isEmpty() ? null : sizes.get(2 % sizes.size()).id(),
                        "Handschuhe verloren");
            }
        }

        int totalUsers = 1 + betreuer.size() + eltern.size() + anfaenger.size() + fortgeschritten.size();
        log.info("Demo: Created {} user accounts (password: '{}')", totalUsers, PASSWORD);
        log.info("Demo: Admin login: admin@ember.local / {}", PASSWORD);
    }

    private StationMember createUser(
            String firstName, String lastName, String hash, int stationId, int loginRoleId, int memberRoleId) {
        String email = firstName.toLowerCase() + "@" + lastName.toLowerCase() + ".local";
        var account = accountRepository.create(email, firstName, lastName, true);
        accountRepository.createCredential(account.id(), hash);
        var member = stationMemberRepository.create(stationId, account.id());
        stationMemberRepository.addRole(member.id(), loginRoleId);
        stationMemberRepository.addRole(member.id(), memberRoleId);
        return member;
    }

    private void seedAttendanceSessions(
            Random rng,
            dev.chojo.ember.entity.AttendanceTemplate templateAnfaenger,
            dev.chojo.ember.entity.AttendanceTemplate templateFort,
            dev.chojo.ember.entity.AttendanceTemplate templateGesamt,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten,
            List<StationMember> betreuer) {
        var teamForAnfaenger = betreuer.subList(0, Math.min(2, betreuer.size()));
        var teamForFort = betreuer.subList(Math.min(1, betreuer.size()), Math.min(3, betreuer.size()));
        var teamForGesamt = betreuer.subList(0, Math.min(3, betreuer.size()));

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(14).withDayOfMonth(1);
        int sessionCount = 0;

        for (LocalDate date = startDate; date.isBefore(today); date = date.plusDays(1)) {
            int weekOfYear = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            if (weekOfYear >= 28 && weekOfYear <= 33) continue; // summer break

            int dow = date.getDayOfWeek().getValue();

            if (dow == 1) { // Monday: Anfänger
                Instant start = date.atTime(17, 30).toInstant(ZoneOffset.UTC);
                Instant end = date.atTime(19, 0).toInstant(ZoneOffset.UTC);
                var sess = attendanceRepository.createSession(
                        templateAnfaenger.id(), start, end, null, "Übung Anfänger KW" + weekOfYear);
                for (var m : anfaenger) {
                    var status = rng.nextInt(10) < 8
                            ? AttendanceEntry.AttendanceStatus.PRESENT
                            : AttendanceEntry.AttendanceStatus.ABSENT;
                    attendanceRepository.createEntry(sess.id(), m.id(), status, AttendanceEntry.EntrySource.EXPECTED);
                }
                for (var m : teamForAnfaenger) {
                    attendanceRepository.createEntry(
                            sess.id(),
                            m.id(),
                            AttendanceEntry.AttendanceStatus.PRESENT,
                            AttendanceEntry.EntrySource.EXTRA);
                }
                sessionCount++;
            }

            if (dow == 3) { // Wednesday: Fortgeschritten
                Instant start = date.atTime(18, 0).toInstant(ZoneOffset.UTC);
                Instant end = date.atTime(19, 30).toInstant(ZoneOffset.UTC);
                var sess = attendanceRepository.createSession(
                        templateFort.id(), start, end, null, "Übung Fortgeschritten KW" + weekOfYear);
                for (var m : fortgeschritten) {
                    var status = rng.nextInt(10) < 7
                            ? AttendanceEntry.AttendanceStatus.PRESENT
                            : AttendanceEntry.AttendanceStatus.ABSENT;
                    attendanceRepository.createEntry(sess.id(), m.id(), status, AttendanceEntry.EntrySource.EXPECTED);
                }
                for (var m : teamForFort) {
                    attendanceRepository.createEntry(
                            sess.id(),
                            m.id(),
                            AttendanceEntry.AttendanceStatus.PRESENT,
                            AttendanceEntry.EntrySource.EXTRA);
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
                        null,
                        "Gesamtübung "
                                + date.getMonth()
                                        .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.GERMAN)
                                + " " + date.getYear());
                for (var m : anfaenger) {
                    var status = rng.nextInt(10) < 7
                            ? AttendanceEntry.AttendanceStatus.PRESENT
                            : AttendanceEntry.AttendanceStatus.ABSENT;
                    attendanceRepository.createEntry(sess.id(), m.id(), status, AttendanceEntry.EntrySource.EXPECTED);
                }
                for (var m : fortgeschritten) {
                    var status = rng.nextInt(10) < 7
                            ? AttendanceEntry.AttendanceStatus.PRESENT
                            : AttendanceEntry.AttendanceStatus.ABSENT;
                    attendanceRepository.createEntry(sess.id(), m.id(), status, AttendanceEntry.EntrySource.EXPECTED);
                }
                for (var m : teamForGesamt) {
                    attendanceRepository.createEntry(
                            sess.id(),
                            m.id(),
                            AttendanceEntry.AttendanceStatus.PRESENT,
                            AttendanceEntry.EntrySource.EXTRA);
                }
                sessionCount++;
            }
        }
        log.info("Demo: Created {} attendance sessions spanning 14 months", sessionCount);
    }

    private void seedInventoryChecks(
            int stationId,
            Random rng,
            List<StationMember> betreuer,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten) {
        var allKids = new ArrayList<>(anfaenger);
        allKids.addAll(fortgeschritten);

        // Some Betreuer checked some kids
        int checkedCount = 0;
        for (int i = 0; i < allKids.size(); i++) {
            if (rng.nextInt(3) != 0) continue; // ~1/3 of kids have been checked
            var kid = allKids.get(i);
            var checker = betreuer.get(rng.nextInt(betreuer.size()));
            var check = inventoryCheckRepository.createCheck(stationId, kid.id(), checker.id());

            // Check all items assigned to this kid
            var items = inventoryRepository.findItemsByMember(kid.id());
            for (var item : items) {
                CheckResult result;
                int roll = rng.nextInt(20);
                if (roll == 0) {
                    result = CheckResult.LOST;
                } else if (roll < 3) {
                    result = CheckResult.NOT_IN_POSSESSION;
                } else {
                    result = CheckResult.CONFIRMED;
                }
                String note = result == CheckResult.LOST ? "Seit letzter Übung vermisst" : "";
                inventoryCheckRepository.createCheckItem(check.id(), item.id(), item.inventoryId(), result, note);
            }
            checkedCount++;
        }
        log.info("Demo: Created inventory checks for {} members", checkedCount);
    }

    private void seedInventory(
            int stationId,
            int memberRoleId,
            Random rng,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten) {
        // Sizes reference from existing data
        var helmSizes = List.of("XS", "S", "M", "L");
        var kleidungSizes = List.of("140", "146", "152", "158", "164", "170", "176", "182");
        var parkaSizes = List.of("XXXXS", "XXXS", "XXS", "XS", "S", "M", "L");
        var handschuhSizes = List.of("4", "5", "6", "7", "8", "9", "10");
        var stiefelSizes = List.of("34", "35", "36", "37", "38", "39", "40", "41", "42", "43");

        // Create inventories
        var helm = inventoryRepository.create(stationId, "Helm", "external", true);
        for (int i = 0; i < helmSizes.size(); i++) inventoryRepository.createSize(helm.id(), helmSizes.get(i), i, "");

        var blouson = inventoryRepository.create(stationId, "Blouson", "external", true);
        for (int i = 0; i < kleidungSizes.size(); i++)
            inventoryRepository.createSize(blouson.id(), kleidungSizes.get(i), i, "");

        var parka = inventoryRepository.create(stationId, "Parka", "external", true);
        for (int i = 0; i < parkaSizes.size(); i++)
            inventoryRepository.createSize(parka.id(), parkaSizes.get(i), i, "");

        var latzhose = inventoryRepository.create(stationId, "Latzhose", "external", true);
        for (int i = 0; i < kleidungSizes.size(); i++)
            inventoryRepository.createSize(latzhose.id(), kleidungSizes.get(i), i, "");

        var handschuhe = inventoryRepository.create(stationId, "Handschuhe", "external", true);
        for (int i = 0; i < handschuhSizes.size(); i++)
            inventoryRepository.createSize(handschuhe.id(), handschuhSizes.get(i), i, "");

        var stiefel = inventoryRepository.create(stationId, "Stiefel", "internal", true);
        for (int i = 0; i < stiefelSizes.size(); i++)
            inventoryRepository.createSize(stiefel.id(), stiefelSizes.get(i), i, "");

        var sporttasche = inventoryRepository.create(stationId, "Sporttasche", "internal", false);

        // Requirements: every member needs 1 of each
        inventoryRepository.createRequirement(helm.id(), memberRoleId, 0, 1);
        inventoryRepository.createRequirement(blouson.id(), memberRoleId, 0, 1);
        inventoryRepository.createRequirement(parka.id(), memberRoleId, 0, 1);
        inventoryRepository.createRequirement(latzhose.id(), memberRoleId, 0, 1);
        inventoryRepository.createRequirement(handschuhe.id(), memberRoleId, 0, 1);
        inventoryRepository.createRequirement(stiefel.id(), memberRoleId, 0, 1);
        inventoryRepository.createRequirement(sporttasche.id(), memberRoleId, 0, 1);

        // Create items and assign to members
        var allKids = new ArrayList<>(anfaenger);
        allKids.addAll(fortgeschritten);

        int itemCounter = 1;
        for (var member : allKids) {
            // Assign random sizes based on member index for determinism
            int idx = allKids.indexOf(member);
            var helmSizeList = inventoryRepository.findSizes(helm.id());
            var blousonSizeList = inventoryRepository.findSizes(blouson.id());
            var parkaSizeList = inventoryRepository.findSizes(parka.id());
            var latzhoseSizeList = inventoryRepository.findSizes(latzhose.id());
            var handschuheSizeList = inventoryRepository.findSizes(handschuhe.id());
            var stiefelSizeList = inventoryRepository.findSizes(stiefel.id());

            // Helm
            var helmItem = inventoryRepository.createItem(
                    helm.id(),
                    "H-" + String.format("%03d", itemCounter++),
                    "Helm " + helmSizes.get(idx % helmSizes.size()),
                    helmSizeList.get(idx % helmSizeList.size()).id(),
                    null);
            inventoryRepository.assignItem(helmItem.id(), member.id());

            // Blouson
            var blousonItem = inventoryRepository.createItem(
                    blouson.id(),
                    "BL-" + String.format("%03d", itemCounter++),
                    "Blouson " + kleidungSizes.get(idx % kleidungSizes.size()),
                    blousonSizeList.get(idx % blousonSizeList.size()).id(),
                    null);
            inventoryRepository.assignItem(blousonItem.id(), member.id());

            // Parka
            var parkaItem = inventoryRepository.createItem(
                    parka.id(),
                    "PA-" + String.format("%03d", itemCounter++),
                    "Parka " + parkaSizes.get(idx % parkaSizes.size()),
                    parkaSizeList.get(idx % parkaSizeList.size()).id(),
                    null);
            inventoryRepository.assignItem(parkaItem.id(), member.id());

            // Latzhose
            var latzItem = inventoryRepository.createItem(
                    latzhose.id(),
                    "LH-" + String.format("%03d", itemCounter++),
                    "Latzhose " + kleidungSizes.get(idx % kleidungSizes.size()),
                    latzhoseSizeList.get(idx % latzhoseSizeList.size()).id(),
                    null);
            inventoryRepository.assignItem(latzItem.id(), member.id());

            // Handschuhe
            var handschuhItem = inventoryRepository.createItem(
                    handschuhe.id(),
                    "HS-" + String.format("%03d", itemCounter++),
                    "Handschuhe " + handschuhSizes.get(idx % handschuhSizes.size()),
                    handschuheSizeList.get(idx % handschuheSizeList.size()).id(),
                    null);
            inventoryRepository.assignItem(handschuhItem.id(), member.id());

            // Stiefel
            var stiefelItem = inventoryRepository.createItem(
                    stiefel.id(),
                    "ST-" + String.format("%03d", itemCounter++),
                    "Stiefel " + stiefelSizes.get(idx % stiefelSizes.size()),
                    stiefelSizeList.get(idx % stiefelSizeList.size()).id(),
                    null);
            inventoryRepository.assignItem(stiefelItem.id(), member.id());

            // Sporttasche (no size)
            var tasche = inventoryRepository.createItem(
                    sporttasche.id(), "SP-" + String.format("%03d", itemCounter++), "Sporttasche", null, null);
            inventoryRepository.assignItem(tasche.id(), member.id());
        }

        // Add some unassigned spare items
        for (int i = 0; i < 5; i++) {
            var helmSizeList = inventoryRepository.findSizes(helm.id());
            inventoryRepository.createItem(
                    helm.id(),
                    "H-" + String.format("%03d", itemCounter++),
                    "Helm Ersatz",
                    helmSizeList.get(rng.nextInt(helmSizeList.size())).id(),
                    null);
        }
        for (int i = 0; i < 3; i++) {
            inventoryRepository.createItem(
                    sporttasche.id(), "SP-" + String.format("%03d", itemCounter++), "Sporttasche Ersatz", null, null);
        }

        log.info("Demo: Created {} inventory items across 7 inventories", itemCounter - 1);
    }

    private String jsonStr(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String randomAllergy(Random rng) {
        var allergies = List.of(
                "Nussallergie",
                "Laktoseintoleranz",
                "Glutenunverträglichkeit",
                "Pollenallergie",
                "Tierhaarallergie",
                "Keine");
        return allergies.get(rng.nextInt(allergies.size()));
    }
}
