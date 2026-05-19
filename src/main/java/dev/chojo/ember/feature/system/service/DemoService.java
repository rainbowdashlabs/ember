/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Database;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.inventory.repository.ExchangeRepository;
import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.news.repository.NewsRepository;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.inventory.repository.ProcurementRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final NotificationRepository notificationRepository;
    private final ExchangeRepository exchangeRepository;
    private final ProcurementRepository procurementRepository;
    private final UserTagRepository userTagRepository;
    private final FormRepository formRepository;
    private final ProfileFieldChangeRepository profileFieldChangeRepository;
    private final EventFieldRepository eventFieldRepository;
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
            NotificationRepository notificationRepository,
            ExchangeRepository exchangeRepository,
            ProcurementRepository procurementRepository,
            UserTagRepository userTagRepository,
            FormRepository formRepository,
            ProfileFieldChangeRepository profileFieldChangeRepository,
            EventFieldRepository eventFieldRepository,
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
        this.notificationRepository = notificationRepository;
        this.exchangeRepository = exchangeRepository;
        this.procurementRepository = procurementRepository;
        this.userTagRepository = userTagRepository;
        this.formRepository = formRepository;
        this.profileFieldChangeRepository = profileFieldChangeRepository;
        this.eventFieldRepository = eventFieldRepository;
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
        try {
            var logoBytes =
                    java.nio.file.Files.readAllBytes(java.nio.file.Path.of("templates", "graphics", "logo.png"));
            stationRepository.updateLogo(station.id(), logoBytes, "image/png");
        } catch (java.io.IOException e) {
            log.warn("Demo: Could not load logo.png", e);
        }

        var adminMember = stationMemberRepository.create(station.id(), admin.id());
        var managerRole = stationMemberRepository.findRoleByName(Roles.MANAGER).orElseThrow();
        var loginRole = stationMemberRepository.findRoleByName(Roles.LOGIN).orElseThrow();
        var memberRole = stationMemberRepository.findRoleByName(Roles.MEMBER).orElseThrow();
        var teamRole = stationMemberRepository.findRoleByName(Roles.TEAM).orElseThrow();
        var memberManagerRole =
                stationMemberRepository.findRoleByName(Roles.GUARDIAN).orElseThrow();
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

        // -- Profile fields: GUARDIAN scope (Eltern) --
        var fieldTelefon = profileFieldRepository.create(
                station.id(),
                "Mobilnummer",
                "text",
                "{\"overview\":true,\"required\":true}",
                0,
                ProfileFieldScope.GUARDIAN);
        var fieldFestnetz =
                profileFieldRepository.create(station.id(), "Festnetz", "text", "{}", 1, ProfileFieldScope.GUARDIAN);
        var fieldNewsletter = profileFieldRepository.create(
                station.id(),
                "Newsletter per Mail",
                "boolean",
                "{\"defaultValue\":true}",
                2,
                ProfileFieldScope.GUARDIAN);

        // -- Profile fields: MEMBER scope (kids) --
        var fieldPersonalnummer = profileFieldRepository.create(
                station.id(),
                "Personalnummer",
                "text",
                "{\"readonly\":true,\"overview\":true}",
                0,
                ProfileFieldScope.MEMBER);
        var fieldGeschlecht = profileFieldRepository.create(
                station.id(),
                "Geschlecht",
                "select",
                "{\"readonly\":true,\"overview\":true,\"options\":[\"männlich\",\"weiblich\",\"divers\"]}",
                1,
                ProfileFieldScope.MEMBER);
        var fieldGeburtstag = profileFieldRepository.create(
                station.id(),
                "Geburtstag",
                "date",
                "{\"required\":true,\"overview\":true}",
                2,
                ProfileFieldScope.MEMBER);
        var fieldAllergien = profileFieldRepository.create(
                station.id(),
                "Allergien",
                "text",
                "{\"overview\":true,\"notifyOnChange\":true}",
                3,
                ProfileFieldScope.MEMBER);
        var fieldLeistungsspange = profileFieldRepository.create(
                station.id(), "Leistungsspange", "boolean", "{\"readonly\":true}", 4, ProfileFieldScope.MEMBER);
        var fieldLeistungsspangeDatum = profileFieldRepository.create(
                station.id(), "Leistungsspange Datum", "date", "{\"readonly\":true}", 5, ProfileFieldScope.MEMBER);
        var fieldJF1 = profileFieldRepository.create(
                station.id(), "Jugendflamme 1", "boolean", "{\"readonly\":true}", 6, ProfileFieldScope.MEMBER);
        var fieldJF1Datum = profileFieldRepository.create(
                station.id(), "Jugendflamme 1 Datum", "date", "{\"readonly\":true}", 7, ProfileFieldScope.MEMBER);
        var fieldJF2 = profileFieldRepository.create(
                station.id(), "Jugendflamme 2", "boolean", "{\"readonly\":true}", 8, ProfileFieldScope.MEMBER);
        var fieldJF2Datum = profileFieldRepository.create(
                station.id(), "Jugendflamme 2 Datum", "date", "{\"readonly\":true}", 9, ProfileFieldScope.MEMBER);
        var fieldJF3 = profileFieldRepository.create(
                station.id(), "Jugendflamme 3", "boolean", "{\"readonly\":true}", 10, ProfileFieldScope.MEMBER);
        var fieldJF3Datum = profileFieldRepository.create(
                station.id(), "Jugendflamme 3 Datum", "date", "{\"readonly\":true}", 11, ProfileFieldScope.MEMBER);

        // -- Users --
        // Betreuer (team role, in Betreuer group)
        record DemoUser(String firstName, String lastName) {}
        var betreuer = List.of(
                new DemoUser("Max", "Mustermann"),
                new DemoUser("Anna", "Schmidt"),
                new DemoUser("Thomas", "Müller"),
                new DemoUser("Lisa", "Weber"),
                new DemoUser("Michael", "Wagner"));

        // Families: parent + kids sharing the same last name
        record Family(
                String parentFirstName,
                String lastName,
                List<String> anfaengerKids,
                List<String> fortgeschrittenKids) {}
        var families = List.of(
                new Family("Hans", "Berger", List.of("Tim", "Lena"), List.of("Mia")),
                new Family("Petra", "Frank", List.of("Lukas"), List.of("Ben", "Laura")),
                new Family("Klaus", "Schulze", List.of("Sophie", "Felix"), List.of()),
                new Family("Monika", "Lehmann", List.of("Emma"), List.of("Markus")),
                new Family("Jürgen", "König", List.of("Jonas", "Marie"), List.of("Nina")),
                new Family("Ursula", "Huber", List.of("Niklas"), List.of("Christian", "Sandra")),
                new Family("Werner", "Kaiser", List.of("Lea", "Paul"), List.of()),
                new Family("Ingrid", "Peters", List.of("Hannah"), List.of("Tobias", "Katharina")),
                new Family("Helmut", "Lang", List.of("Leon"), List.of("Andreas")),
                new Family("Gerda", "Scholz", List.of(), List.of("Melanie", "Patrick")));

        // Build user lists from families
        var eltern = new ArrayList<DemoUser>();
        var anfaenger = new ArrayList<DemoUser>();
        var fortgeschritten = new ArrayList<DemoUser>();
        // Track which kids belong to which parent index for manager assignment
        // Indices are into allKids = anfaengerMembers ++ fortgeschrittenMembers
        var familyKidIndices = new ArrayList<List<Integer>>(); // per family: indices into allKids
        int anfaengerCounter = 0;
        int fortgeschrittenCounter = 0;
        int totalAnfaenger =
                families.stream().mapToInt(f -> f.anfaengerKids().size()).sum();
        for (var family : families) {
            eltern.add(new DemoUser(family.parentFirstName(), family.lastName()));
            var kidIndices = new ArrayList<Integer>();
            for (var kidName : family.anfaengerKids()) {
                anfaenger.add(new DemoUser(kidName, family.lastName()));
                kidIndices.add(anfaengerCounter++);
            }
            for (var kidName : family.fortgeschrittenKids()) {
                fortgeschritten.add(new DemoUser(kidName, family.lastName()));
                kidIndices.add(totalAnfaenger + fortgeschrittenCounter++);
            }
            familyKidIndices.add(kidIndices);
        }

        var betreuerMembers = new ArrayList<StationMember>();
        var elternMembers = new ArrayList<StationMember>();
        var anfaengerMembers = new ArrayList<StationMember>();
        var fortgeschrittenMembers = new ArrayList<StationMember>();

        // Create Betreuer (TEAM — not MEMBER)
        for (var u : betreuer) {
            var m = createUser(u.firstName(), u.lastName(), hash, station.id(), loginRole.id(), teamRole.id());
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

        // Create Eltern (member managers)
        boolean firstEltern = true;
        for (var u : eltern) {
            var m = createUser(u.firstName(), u.lastName(), hash, station.id(), loginRole.id(), memberRole.id());
            stationMemberRepository.addRole(m.id(), memberManagerRole.id());
            memberGroupRepository.addMember(groupEltern.id(), m.id());
            elternMembers.add(m);

            // Profile data — skip Mobilnummer for first member manager (incomplete profile)
            if (!firstEltern) {
                profileFieldRepository.setValue(
                        m.id(), fieldTelefon.id(), jsonStr("0151 " + (10000000 + rng.nextInt(90000000))));
            }
            firstEltern = false;
            profileFieldRepository.setValue(
                    m.id(), fieldFestnetz.id(), jsonStr("0208 " + (1000000 + rng.nextInt(9000000))));
            profileFieldRepository.setValue(m.id(), fieldNewsletter.id(), Boolean.toString(rng.nextBoolean()));
            // Geburtstag (required MEMBER field — Eltern also have MEMBER role)
            profileFieldRepository.setValue(
                    m.id(),
                    fieldGeburtstag.id(),
                    jsonStr(LocalDate.now()
                            .minusYears(30 + rng.nextInt(20))
                            .minusDays(rng.nextInt(365))
                            .toString()));
        }

        // Create Anfänger
        int personalNr = 100000 + rng.nextInt(900000);
        boolean firstAnfaenger = true;
        for (var u : anfaenger) {
            var m = createUser(u.firstName(), u.lastName(), hash, station.id(), loginRole.id(), memberRole.id());
            memberGroupRepository.addMember(groupAnfaenger.id(), m.id());
            anfaengerMembers.add(m);

            // Personalnummer
            profileFieldRepository.setValue(m.id(), fieldPersonalnummer.id(), jsonStr(String.valueOf(personalNr++)));

            // Geburtstag — skip first Anfänger (incomplete profile)
            if (!firstAnfaenger) {
                profileFieldRepository.setValue(
                        m.id(),
                        fieldGeburtstag.id(),
                        jsonStr(LocalDate.now()
                                .minusYears(10 + rng.nextInt(6))
                                .minusDays(rng.nextInt(365))
                                .toString()));
            }
            firstAnfaenger = false;

            // Geschlecht
            profileFieldRepository.setValue(
                    m.id(), fieldGeschlecht.id(), jsonStr(rng.nextBoolean() ? "männlich" : "weiblich"));

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

            // Personalnummer
            profileFieldRepository.setValue(m.id(), fieldPersonalnummer.id(), jsonStr(String.valueOf(personalNr++)));

            // Geburtstag
            profileFieldRepository.setValue(
                    m.id(),
                    fieldGeburtstag.id(),
                    jsonStr(LocalDate.now()
                            .minusYears(12 + rng.nextInt(6))
                            .minusDays(rng.nextInt(365))
                            .toString()));

            // Geschlecht
            profileFieldRepository.setValue(
                    m.id(), fieldGeschlecht.id(), jsonStr(rng.nextBoolean() ? "männlich" : "weiblich"));

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

        // -- Former members --
        var formerMember1 = createUser("Max", "Altmann", hash, station.id(), loginRole.id(), memberRole.id());
        memberGroupRepository.addMember(groupAnfaenger.id(), formerMember1.id());
        stationMemberRepository.setFormer(formerMember1.id(), true);

        var formerMember2 = createUser("Lisa", "Wegner", hash, station.id(), loginRole.id(), memberRole.id());
        memberGroupRepository.addMember(groupFortgeschritten.id(), formerMember2.id());
        stationMemberRepository.setFormer(formerMember2.id(), true);

        var formerMember3 = createUser("Tom", "Richter", hash, station.id(), loginRole.id(), memberRole.id());
        stationMemberRepository.addRole(formerMember3.id(), teamRole.id());
        stationMemberRepository.setFormer(formerMember3.id(), true);

        // -- Profile field changes (unacknowledged) --
        if (anfaengerMembers.size() >= 3) {
            // Simulate phone number changes
            profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    anfaengerMembers.get(0).id(),
                    "\"0151 12345678\"",
                    "\"0171 98765432\"",
                    anfaengerMembers.get(0).id(),
                    true);
            profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    anfaengerMembers.get(1).id(),
                    "\"0152 11223344\"",
                    "\"0163 55667788\"",
                    anfaengerMembers.get(1).id(),
                    true);
            // Simulate allergy field change
            profileFieldChangeRepository.create(
                    fieldAllergien.id(),
                    anfaengerMembers.get(2).id(),
                    "\"Keine\"",
                    "\"Nussallergie\"",
                    anfaengerMembers.get(2).id(),
                    true);
        }

        // -- Past profile field changes (acknowledged) --
        if (anfaengerMembers.size() >= 5 && betreuerMembers.size() >= 1) {
            int bId = betreuerMembers.get(0).id();
            // Phone number changes
            var c1 = profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    anfaengerMembers.get(3).id(),
                    "\"0155 33344455\"",
                    "\"0177 11122233\"",
                    anfaengerMembers.get(3).id(),
                    true);
            profileFieldChangeRepository.acknowledge(c1.id(), bId, null);
            var c2 = profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    anfaengerMembers.get(4).id(),
                    "\"0160 99988877\"",
                    "\"0172 44455566\"",
                    anfaengerMembers.get(4).id(),
                    true);
            profileFieldChangeRepository.acknowledge(c2.id(), bId, "Nummer geprüft");
            // Allergy changes
            var c3 = profileFieldChangeRepository.create(
                    fieldAllergien.id(),
                    anfaengerMembers.get(3).id(),
                    "\"Keine\"",
                    "\"Laktoseintoleranz\"",
                    betreuerMembers.get(0).id(),
                    true);
            profileFieldChangeRepository.acknowledge(c3.id(), bId, null);
            var c4 = profileFieldChangeRepository.create(
                    fieldAllergien.id(),
                    anfaengerMembers.get(4).id(),
                    "\"Heuschnupfen\"",
                    "\"Heuschnupfen, Hausstaub\"",
                    anfaengerMembers.get(4).id(),
                    true);
            profileFieldChangeRepository.acknowledge(c4.id(), bId, "Mit Eltern abgestimmt");
            // Birthday correction
            var c5 = profileFieldChangeRepository.create(
                    fieldGeburtstag.id(),
                    anfaengerMembers.get(3).id(),
                    "\"2014-05-10\"",
                    "\"2014-05-11\"",
                    betreuerMembers.get(0).id(),
                    false);
            // Non-acknowledged changes that don't require ack
            profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    fortgeschrittenMembers.get(0).id(),
                    "\"0151 77766655\"",
                    "\"0176 88899900\"",
                    fortgeschrittenMembers.get(0).id(),
                    false);
        }

        // -- Manager assignments: each Eltern manages their own kids (same last name) --
        var allKids = new ArrayList<>(anfaengerMembers);
        allKids.addAll(fortgeschrittenMembers);
        for (int fi = 0; fi < families.size(); fi++) {
            var elternMember = elternMembers.get(fi);
            for (int kidIndex : familyKidIndices.get(fi)) {
                if (kidIndex < allKids.size()) {
                    stationMemberRepository.addManager(
                            elternMember.id(), allKids.get(kidIndex).id());
                }
            }
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

        // -- Event categories --
        var catUebung = eventRepository.createCategory(station.id(), "Übungen", 0);
        var catVeranstaltung = eventRepository.createCategory(station.id(), "Veranstaltungen", 1);
        var catWettbewerb = eventRepository.createCategory(station.id(), "Wettbewerbe", 2);

        // -- Events --
        Instant monStart = LocalDate.now().atTime(17, 30).toInstant(ZoneOffset.UTC);
        Instant monEnd = LocalDate.now().atTime(19, 0).toInstant(ZoneOffset.UTC);
        Instant wedStart = LocalDate.now().atTime(18, 0).toInstant(ZoneOffset.UTC);
        Instant wedEnd = LocalDate.now().atTime(19, 30).toInstant(ZoneOffset.UTC);
        Instant satStart = LocalDate.now().atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant satEnd = LocalDate.now().atTime(13, 0).toInstant(ZoneOffset.UTC);

        var evAnfaenger = eventRepository.create(
                station.id(),
                "Übung Anfänger",
                "Grundausbildung für Anfänger",
                StationEvent.EventType.RECURRING,
                1,
                monStart,
                monEnd,
                templateAnfaenger.id(),
                false,
                null,
                false,
                catUebung.id());
        var evFort = eventRepository.create(
                station.id(),
                "Übung Fortgeschritten",
                "Training für Fortgeschrittene",
                StationEvent.EventType.RECURRING,
                3,
                wedStart,
                wedEnd,
                templateFort.id(),
                false,
                null,
                false,
                catUebung.id());
        var evGesamt = eventRepository.create(
                station.id(),
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
                catUebung.id());

        // Monthly: first Saturday = Elternabend
        eventRepository.create(
                station.id(),
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
                catVeranstaltung.id());

        // Quarterly: first Saturday = Dienstbesprechung
        eventRepository.create(
                station.id(),
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
                catVeranstaltung.id());

        // -- Past attendance sessions (full year + current year so far) --
        // Yearly: Jahreshauptversammlung on Sep 20
        Instant jhvStart =
                LocalDate.now().withMonth(9).withDayOfMonth(20).atTime(18, 0).toInstant(ZoneOffset.UTC);
        Instant jhvEnd =
                LocalDate.now().withMonth(9).withDayOfMonth(20).atTime(21, 0).toInstant(ZoneOffset.UTC);
        eventRepository.create(
                station.id(),
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
                catVeranstaltung.id());

        seedAttendanceSessions(
                rng,
                templateAnfaenger,
                templateFort,
                templateGesamt,
                evAnfaenger,
                evFort,
                evGesamt,
                anfaengerMembers,
                fortgeschrittenMembers,
                betreuerMembers);

        // -- Inventory --
        seedInventory(
                station.id(),
                rng,
                anfaengerMembers,
                fortgeschrittenMembers,
                groupAnfaenger.id(),
                groupFortgeschritten.id());

        // -- Inventory checks (done by Betreuer) --
        seedInventoryChecks(station.id(), rng, betreuerMembers, anfaengerMembers, fortgeschrittenMembers);

        // One-time event for today (ensures there's always an event today)
        Instant todayEventStart = LocalDate.now().atTime(16, 0).toInstant(ZoneOffset.UTC);
        Instant todayEventEnd = LocalDate.now().atTime(18, 0).toInstant(ZoneOffset.UTC);
        var templateTheorie = attendanceRepository.createTemplate(station.id(), "Theorieabend");
        attendanceRepository.setTemplateGroups(
                templateTheorie.id(),
                List.of(
                        new AttendanceRepository.TemplateGroup(groupAnfaenger.id(), 0),
                        new AttendanceRepository.TemplateGroup(groupFortgeschritten.id(), 1)));
        var theorieabend = eventRepository.create(
                station.id(),
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
                catUebung.id());
        LocalDate todayDate = LocalDate.now();
        for (int i = 0; i < 5 && i < anfaengerMembers.size(); i++) {
            eventRepository.createRegistration(
                    theorieabend.id(),
                    anfaengerMembers.get(i).id(),
                    todayDate,
                    EventRegistration.RegistrationStatus.DECLINED,
                    null);
        }

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
                StationEvent.EventType.ONE_TIME,
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
                StationEvent.EventType.ONE_TIME,
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
                StationEvent.EventType.ONE_TIME,
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
                    tagDerOffenenTuer.id(),
                    fortgeschrittenMembers.get(i).id(),
                    tagDate,
                    EventRegistration.RegistrationStatus.ACCEPTED,
                    null);
        }
        for (int i = 0; i < 5 && i < anfaengerMembers.size(); i++) {
            eventRepository.createRegistration(
                    stadtfest.id(),
                    anfaengerMembers.get(i).id(),
                    stadtfestDate,
                    EventRegistration.RegistrationStatus.ACCEPTED,
                    null);
        }
        for (int i = 0; i < 3 && i < fortgeschrittenMembers.size(); i++) {
            eventRepository.createRegistration(
                    stadtfest.id(),
                    fortgeschrittenMembers.get(i).id(),
                    stadtfestDate,
                    EventRegistration.RegistrationStatus.ACCEPTED,
                    null);
        }
        // Some pending registrations for Kreiswettbewerb
        LocalDate kwDate = LocalDate.now().plusMonths(2).withDayOfMonth(20);
        for (int i = 0; i < 6 && i < fortgeschrittenMembers.size(); i++) {
            eventRepository.createRegistration(
                    kreisWettbewerb.id(),
                    fortgeschrittenMembers.get(i).id(),
                    kwDate,
                    EventRegistration.RegistrationStatus.PENDING,
                    null);
        }

        // Declined registrations for Stadtfest
        for (int i = 5; i < 8 && i < anfaengerMembers.size(); i++) {
            eventRepository.createRegistration(
                    stadtfest.id(),
                    anfaengerMembers.get(i).id(),
                    stadtfestDate,
                    EventRegistration.RegistrationStatus.DECLINED,
                    null);
        }
        // Declined registrations for Kreiswettbewerb
        for (int i = 6; i < 9 && i < fortgeschrittenMembers.size(); i++) {
            eventRepository.createRegistration(
                    kreisWettbewerb.id(),
                    fortgeschrittenMembers.get(i).id(),
                    kwDate,
                    EventRegistration.RegistrationStatus.DECLINED,
                    null);
        }
        // Denied registration for Tag der offenen Tür
        if (anfaengerMembers.size() > 9) {
            eventRepository.createRegistration(
                    tagDerOffenenTuer.id(),
                    anfaengerMembers.get(9).id(),
                    tagDate,
                    EventRegistration.RegistrationStatus.DENIED,
                    null);
        }

        // -- Event Fields --
        // Per-event fields
        eventFieldRepository.create(tagDerOffenenTuer.id(), "Ort", "Feuerwehrhaus Musterstadt", 0);
        eventFieldRepository.create(tagDerOffenenTuer.id(), "Treffpunkt", "Haupteingang", 1);
        eventFieldRepository.create(tagDerOffenenTuer.id(), "Hinweis", "Dienstkleidung tragen", 2);
        eventFieldRepository.create(stadtfest.id(), "Ort", "Marktplatz Musterstadt", 0);
        eventFieldRepository.create(stadtfest.id(), "Treffpunkt", "Stand der Jugendfeuerwehr", 1);
        eventFieldRepository.create(kreisWettbewerb.id(), "Ort", "Sportplatz Nachbarstadt", 0);
        eventFieldRepository.create(kreisWettbewerb.id(), "Hinweis", "Wettkampfkleidung und Ausrüstung mitbringen", 1);
        // Recurring event fields
        eventFieldRepository.create(evAnfaenger.id(), "Ort", "Feuerwehrhaus Musterstadt", 0);
        eventFieldRepository.create(evAnfaenger.id(), "Hinweis", "Sportkleidung mitbringen", 1);
        eventFieldRepository.create(evFort.id(), "Ort", "Feuerwehrhaus Musterstadt", 0);
        eventFieldRepository.create(evFort.id(), "Hinweis", "Schutzausrüstung wird gestellt", 1);
        eventFieldRepository.create(evGesamt.id(), "Ort", "Feuerwehrhaus Musterstadt", 0);
        eventFieldRepository.create(evGesamt.id(), "Treffpunkt", "Fahrzeughalle", 1);
        eventFieldRepository.create(theorieabend.id(), "Ort", "Schulungsraum Feuerwehrhaus", 0);
        eventFieldRepository.create(theorieabend.id(), "Hinweis", "Schreibzeug mitbringen", 1);

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
                news1.id(), comment1.id(), betreuerMembers.getFirst().id(), "Danke! Bei Fragen einfach melden.");
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

        // -- Equipment Exchange Requests (~80% of members) --
        var allKidsForExchange = new ArrayList<>(anfaengerMembers);
        allKidsForExchange.addAll(fortgeschrittenMembers);
        var exchangeReasons = List.of(
                "Zu klein geworden",
                "Beschädigt",
                "Verschlissen",
                "Falsche Größe erhalten",
                "Verloren und brauche Ersatz",
                "Riss im Material",
                "Reißverschluss defekt");
        var exchangeStatuses = List.of(
                ExchangeStatus.ANNOUNCED,
                ExchangeStatus.ANNOUNCED,
                ExchangeStatus.RECEIVED,
                ExchangeStatus.ANNOUNCED,
                ExchangeStatus.RECEIVED);
        int exchangeCount = 0;
        for (var kid : allKidsForExchange) {
            if (rng.nextInt(5) == 0) continue; // ~80% get an exchange
            var memberItems = inventoryRepository.findItemsByMember(kid.id());
            if (memberItems.isEmpty()) continue;
            var item = memberItems.get(rng.nextInt(memberItems.size()));
            var reason = exchangeReasons.get(rng.nextInt(exchangeReasons.size()));
            // Determine new size (sometimes same, sometimes different)
            Integer newSizeId = item.sizeId();
            if (item.sizeId() != null && rng.nextBoolean()) {
                var sizes = inventoryRepository.findSizes(item.inventoryId());
                if (!sizes.isEmpty()) {
                    newSizeId = sizes.get(rng.nextInt(sizes.size())).id();
                }
            }
            var exchange = exchangeRepository.create(
                    station.id(), kid.id(), item.id(), item.inventoryId(), item.sizeId(), newSizeId, reason, null);
            // Progress some exchanges
            var targetStatus = exchangeStatuses.get(rng.nextInt(exchangeStatuses.size()));
            if (targetStatus != ExchangeStatus.ANNOUNCED) {
                exchangeRepository.updateStatus(exchange.id(), ExchangeStatus.RECEIVED);
                exchangeRepository.createLog(
                        exchange.id(),
                        ExchangeStatus.ANNOUNCED,
                        ExchangeStatus.RECEIVED,
                        betreuerMembers.get(rng.nextInt(betreuerMembers.size())).id(),
                        "In Bearbeitung");
            }
            exchangeCount++;
        }
        log.info("Demo: Created {} exchange requests", exchangeCount);

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

        // Procurement for members missing Sporttasche
        var sporttascheInv =
                inventories.stream().filter(i -> "Sporttasche".equals(i.name())).findFirst();
        if (sporttascheInv.isPresent()) {
            var allKidsForProcurement = new ArrayList<>(anfaengerMembers);
            allKidsForProcurement.addAll(fortgeschrittenMembers);
            for (var kid : allKidsForProcurement) {
                var items = inventoryRepository.findItemsByMember(kid.id());
                boolean hasSporttasche = items.stream()
                        .anyMatch(i -> i.inventoryId() == sporttascheInv.get().id());
                if (!hasSporttasche) {
                    procurementRepository.create(
                            station.id(), sporttascheInv.get().id(), kid.id(), null, "Sporttasche fehlt");
                }
            }
        }

        // -- Forms --
        seedForms(
                station.id(),
                adminMember,
                anfaengerMembers,
                fortgeschrittenMembers,
                memberRole.id(),
                memberManagerRole.id(),
                groupAnfaenger.id(),
                tagWettkampf.id(),
                rng);

        // -- Demo sessions (fake past sessions to show in settings) --
        var demoUserAgents = List.of(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
                "Mozilla/5.0 (X11; Linux x86_64; rv:150.0) Gecko/20100101 Firefox/150.0");
        var sessionExpiry = Instant.now().plus(java.time.Duration.ofHours(24));
        for (int i = 0; i < demoUserAgents.size(); i++) {
            var accountId = admin.id();
            var token = java.util.UUID.randomUUID().toString();
            accountRepository.createSession(accountId, token, sessionExpiry, demoUserAgents.get(i), null);
        }

        // -- Notifications (demo data so users see them on the dashboard) --
        seedNotifications(
                station.id(), adminMember, betreuerMembers, elternMembers, anfaengerMembers, fortgeschrittenMembers);

        int totalUsers = 1 + betreuer.size() + eltern.size() + anfaenger.size() + fortgeschritten.size();
        log.info("Demo: Created {} user accounts (password: '{}')", totalUsers, PASSWORD);
        log.info("Demo: Admin login: admin@ember.local / {}", PASSWORD);
    }

    private void seedForms(
            int stationId,
            StationMember admin,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten,
            int memberRoleId,
            int memberManagerRoleId,
            int anfaengerGroupId,
            int wettkampfTagId,
            Random rng) {
        // Form 1: Satisfaction survey (OPEN, with responses)
        var survey = formRepository.create(
                stationId,
                "Zufriedenheitsumfrage",
                "Wie gefällt dir unsere Jugendfeuerwehr?",
                false,
                true,
                null,
                null,
                admin.id());
        formRepository.updateStatus(survey.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                survey.id(),
                0,
                FormQuestion.QuestionType.RATING,
                "Wie zufrieden bist du insgesamt?",
                "1 = sehr unzufrieden, 5 = sehr zufrieden",
                true,
                false,
                "{\"scale\":5,\"icon\":\"STAR\"}");
        formRepository.createQuestion(
                survey.id(),
                1,
                FormQuestion.QuestionType.CHOICE,
                "Was gefällt dir am besten?",
                "",
                false,
                true,
                "{\"multiSelect\":true,\"dropdown\":false,\"allowOther\":true,\"options\":[\"Übungen\",\"Gemeinschaft\",\"Ausflüge\",\"Wettbewerbe\"],\"multiLimitType\":\"NONE\"}");
        formRepository.createQuestion(
                survey.id(),
                2,
                FormQuestion.QuestionType.TEXT,
                "Hast du Verbesserungsvorschläge?",
                "",
                false,
                false,
                "{\"longAnswer\":true}");

        // Add some responses
        var surveyQuestions = formRepository.findQuestions(survey.id());
        var respondents = new ArrayList<StationMember>();
        respondents.addAll(anfaenger.subList(0, Math.min(5, anfaenger.size())));
        respondents.addAll(fortgeschritten.subList(0, Math.min(4, fortgeschritten.size())));
        String[] suggestions = {"Mehr Ausflüge!", "Öfter draußen üben", "Alles super!", "", "Neue Geräte wären toll"};
        for (int i = 0; i < respondents.size(); i++) {
            var member = respondents.get(i);
            var response = formRepository.createResponse(survey.id(), member.id(), member.id());
            int rating = 3 + rng.nextInt(3);
            formRepository.upsertAnswer(response.id(), surveyQuestions.get(0).id(), "{\"rating\":" + rating + "}");
            int[] selected = rng.nextInt(2) == 0 ? new int[] {0, 2} : new int[] {1, 3};
            formRepository.upsertAnswer(
                    response.id(),
                    surveyQuestions.get(1).id(),
                    "{\"selected\":[" + selected[0] + "," + selected[1] + "],\"other\":\"\"}");
            formRepository.upsertAnswer(
                    response.id(),
                    surveyQuestions.get(2).id(),
                    "{\"text\":\"" + suggestions[i % suggestions.length] + "\"}");
        }

        // Add remaining types to survey: DATE, RANKING, LIKERT
        formRepository.createQuestion(
                survey.id(),
                3,
                FormQuestion.QuestionType.DATE,
                "Wann bist du der Jugendfeuerwehr beigetreten?",
                "",
                false,
                false,
                "{}");
        formRepository.createQuestion(
                survey.id(),
                4,
                FormQuestion.QuestionType.RANKING,
                "Ordne die Aktivitäten nach Beliebtheit",
                "",
                false,
                true,
                "{\"options\":[\"Übungen\",\"Wettbewerbe\",\"Ausflüge\",\"Theorie\"]}");
        formRepository.createQuestion(
                survey.id(),
                5,
                FormQuestion.QuestionType.LIKERT,
                "Wie bewertest du die folgenden Bereiche?",
                "",
                false,
                false,
                "{\"statements\":[\"Ausrüstung\",\"Betreuung\",\"Abwechslung\"],\"scaleMin\":1,\"scaleMax\":5,\"scaleLabels\":[]}");

        // Re-fetch questions after adding more
        surveyQuestions = formRepository.findQuestions(survey.id());
        // Add responses for the new question types
        for (StationMember member : respondents) {
            var existingResponse =
                    formRepository.findResponse(survey.id(), member.id()).orElseThrow();
            formRepository.upsertAnswer(
                    existingResponse.id(),
                    surveyQuestions.get(3).id(),
                    "{\"date\":\"202" + (2 + rng.nextInt(4)) + "-0" + (1 + rng.nextInt(9)) + "-15\"}");
            int[] rankOrder = {rng.nextInt(4), (1 + rng.nextInt(3)) % 4, (2 + rng.nextInt(2)) % 4, 3 - rng.nextInt(2)};
            formRepository.upsertAnswer(
                    existingResponse.id(),
                    surveyQuestions.get(4).id(),
                    "{\"order\":[" + rankOrder[0] + "," + rankOrder[1] + "," + rankOrder[2] + "," + rankOrder[3]
                            + "]}");
            formRepository.upsertAnswer(
                    existingResponse.id(),
                    surveyQuestions.get(5).id(),
                    "{\"ratings\":{\"0\":" + (3 + rng.nextInt(3)) + ",\"1\":" + (3 + rng.nextInt(3)) + ",\"2\":"
                            + (2 + rng.nextInt(4)) + "}}");
        }

        // Form 2: CLOSED comprehensive form with ALL types + responses
        var feedback = formRepository.create(
                stationId,
                "Feedback Übungsabend",
                "Rückmeldung zum letzten Übungsabend",
                false,
                true,
                null,
                null,
                admin.id());
        formRepository.updateStatus(feedback.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                feedback.id(),
                0,
                FormQuestion.QuestionType.CHOICE,
                "Würdest du wieder teilnehmen?",
                "",
                true,
                false,
                "{\"multiSelect\":false,\"dropdown\":false,\"allowOther\":false,\"options\":[\"Ja\",\"Vielleicht\",\"Nein\"],\"multiLimitType\":\"NONE\"}");
        formRepository.createQuestion(
                feedback.id(),
                1,
                FormQuestion.QuestionType.TEXT,
                "Was hat dir besonders gefallen?",
                "",
                false,
                false,
                "{\"longAnswer\":true}");
        formRepository.createQuestion(
                feedback.id(),
                2,
                FormQuestion.QuestionType.RATING,
                "Gesamtbewertung",
                "1 = schlecht, 10 = super",
                true,
                false,
                "{\"scale\":10,\"icon\":\"HEART\"}");
        formRepository.createQuestion(
                feedback.id(),
                3,
                FormQuestion.QuestionType.DATE,
                "An welchem Datum warst du dabei?",
                "",
                false,
                false,
                "{}");
        formRepository.createQuestion(
                feedback.id(),
                4,
                FormQuestion.QuestionType.RANKING,
                "Was war am wichtigsten?",
                "",
                false,
                true,
                "{\"options\":[\"Teamwork\",\"Technik\",\"Fitness\",\"Spaß\"]}");
        formRepository.createQuestion(
                feedback.id(),
                5,
                FormQuestion.QuestionType.LIKERT,
                "Bewerte die folgenden Aspekte",
                "",
                true,
                false,
                "{\"statements\":[\"Organisation\",\"Lerninhalte\",\"Spaßfaktor\",\"Zeitdauer\"],\"scaleMin\":1,\"scaleMax\":5,\"scaleLabels\":[]}");

        var feedbackQuestions = formRepository.findQuestions(feedback.id());
        String[] feedbackTexts = {
            "Tolle Übung!", "Mehr davon!", "War ok", "Super organisiert", "Könnte besser sein", "Hat Spaß gemacht"
        };
        for (int i = 0; i < Math.min(8, anfaenger.size()); i++) {
            var member = anfaenger.get(i);
            var response = formRepository.createResponse(feedback.id(), member.id(), member.id());
            int choiceIdx = rng.nextInt(3);
            formRepository.upsertAnswer(
                    response.id(), feedbackQuestions.get(0).id(), "{\"selected\":[" + choiceIdx + "],\"other\":\"\"}");
            formRepository.upsertAnswer(
                    response.id(),
                    feedbackQuestions.get(1).id(),
                    "{\"text\":\"" + feedbackTexts[i % feedbackTexts.length] + "\"}");
            formRepository.upsertAnswer(
                    response.id(), feedbackQuestions.get(2).id(), "{\"rating\":" + (5 + rng.nextInt(6)) + "}");
            formRepository.upsertAnswer(response.id(), feedbackQuestions.get(3).id(), "{\"date\":\"2026-05-10\"}");
            int[] order = {rng.nextInt(4), (1 + rng.nextInt(3)) % 4, 2, 3};
            formRepository.upsertAnswer(
                    response.id(),
                    feedbackQuestions.get(4).id(),
                    "{\"order\":[" + order[0] + "," + order[1] + "," + order[2] + "," + order[3] + "]}");
            formRepository.upsertAnswer(
                    response.id(),
                    feedbackQuestions.get(5).id(),
                    "{\"ratings\":{\"0\":" + (3 + rng.nextInt(3)) + ",\"1\":" + (2 + rng.nextInt(4)) + ",\"2\":"
                            + (4 + rng.nextInt(2)) + ",\"3\":" + (2 + rng.nextInt(3)) + "}}");
        }
        formRepository.updateStatus(feedback.id(), Form.FormStatus.CLOSED);

        // Form 3: Member-only form (restricted to MEMBER role)
        var memberOnly = formRepository.create(
                stationId,
                "Persönliche Einschätzung",
                "Nur für Mitglieder — Verwalter können dieses Formular für ihre verwalteten Mitglieder ausfüllen.",
                false,
                true,
                null,
                null,
                admin.id());
        formRepository.updateStatus(memberOnly.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                memberOnly.id(),
                0,
                FormQuestion.QuestionType.RATING,
                "Wie wohl fühlst du dich in der Gruppe?",
                "1 = gar nicht, 5 = sehr wohl",
                true,
                false,
                "{\"scale\":5,\"icon\":\"STAR\"}");
        formRepository.createQuestion(
                memberOnly.id(),
                1,
                FormQuestion.QuestionType.TEXT,
                "Was wünschst du dir für die nächsten Monate?",
                "",
                false,
                false,
                "{\"longAnswer\":true}");
        formRepository.createQuestion(
                memberOnly.id(),
                2,
                FormQuestion.QuestionType.CHOICE,
                "Möchtest du an einem Wettbewerb teilnehmen?",
                "",
                true,
                false,
                "{\"multiSelect\":false,\"dropdown\":false,\"allowOther\":false,\"options\":[\"Ja, unbedingt!\",\"Vielleicht\",\"Nein, lieber nicht\"],\"multiLimitType\":\"NONE\"}");
        formRepository.setRoleRestrictions(memberOnly.id(), List.of(memberRoleId));

        // Form 4: For MEMBER + GUARDIAN (both can fill for themselves)
        var bothRoles = formRepository.create(
                stationId,
                "Terminplanung Herbstfest",
                "Für Mitglieder und Verwalter — bitte gebt eure Verfügbarkeit an.",
                false,
                true,
                null,
                null,
                admin.id());
        formRepository.updateStatus(bothRoles.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                bothRoles.id(),
                0,
                FormQuestion.QuestionType.DATE,
                "An welchem Wochenende passt es dir am besten?",
                "",
                true,
                false,
                "{}");
        formRepository.createQuestion(
                bothRoles.id(),
                1,
                FormQuestion.QuestionType.CHOICE,
                "Kannst du beim Aufbau helfen?",
                "",
                false,
                false,
                "{\"multiSelect\":false,\"dropdown\":false,\"allowOther\":false,\"options\":[\"Ja\",\"Nein\",\"Vielleicht\"],\"multiLimitType\":\"NONE\"}");
        formRepository.setRoleRestrictions(bothRoles.id(), List.of(memberRoleId, memberManagerRoleId));

        // Form 5: Restricted to Wettkampfgruppe tag only
        var wettkampfForm = formRepository.create(
                stationId,
                "Wettkampf-Vorbereitung",
                "Nur für Mitglieder der Wettkampfgruppe.",
                false,
                true,
                null,
                null,
                admin.id());
        formRepository.updateStatus(wettkampfForm.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                wettkampfForm.id(),
                0,
                FormQuestion.QuestionType.RATING,
                "Wie fit fühlst du dich für den Wettkampf?",
                "1 = gar nicht, 5 = top vorbereitet",
                true,
                false,
                "{\"scale\":5,\"icon\":\"STAR\"}");
        formRepository.createQuestion(
                wettkampfForm.id(),
                1,
                FormQuestion.QuestionType.CHOICE,
                "Welche Disziplin möchtest du übernehmen?",
                "",
                true,
                false,
                "{\"multiSelect\":true,\"dropdown\":false,\"allowOther\":true,\"options\":[\"Löschangriff\",\"Staffellauf\",\"Knotenkunde\",\"Erste Hilfe\"],\"multiLimitType\":\"AT_MOST\",\"multiLimit\":2}");
        formRepository.setTagRestrictions(wettkampfForm.id(), List.of(wettkampfTagId));

        // Form 6: Restricted to Anfänger group only
        var anfaengerForm = formRepository.create(
                stationId,
                "Anfänger-Feedback",
                "Nur für die Anfänger-Gruppe — wie läuft es bei euch?",
                false,
                true,
                null,
                null,
                admin.id());
        formRepository.updateStatus(anfaengerForm.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                anfaengerForm.id(),
                0,
                FormQuestion.QuestionType.LIKERT,
                "Bewerte deine bisherige Erfahrung",
                "",
                true,
                false,
                "{\"statements\":[\"Ich verstehe die Übungen\",\"Ich fühle mich willkommen\",\"Ich lerne viel Neues\"],\"scaleMin\":1,\"scaleMax\":5,\"scaleLabels\":[]}");
        formRepository.createQuestion(
                anfaengerForm.id(),
                1,
                FormQuestion.QuestionType.TEXT,
                "Was können wir für dich verbessern?",
                "",
                false,
                false,
                "{\"longAnswer\":true}");
        formRepository.setGroupRestrictions(anfaengerForm.id(), List.of(anfaengerGroupId));

        log.info(
                "Demo: Created 6 forms (open all types, closed all types, member-only, member+manager, tag-restricted, group-restricted)");
    }

    private void seedNotifications(
            int stationId,
            StationMember admin,
            List<StationMember> betreuer,
            List<StationMember> eltern,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten) {
        // News notification for all members
        for (var m : betreuer) {
            notificationRepository.create(
                    m.id(),
                    NotificationType.NEW_NEWS,
                    NotificationData.of(
                            "notification.newNews",
                            Map.of(
                                    "title", "Neue Ausrüstung eingetroffen",
                                    "author", "Anna Schmidt",
                                    "preview", "Die bestellten Helme und Handschuhe sind eingetroffen..."),
                            new NotificationData.NotificationLink("news-list")));
        }

        // Comment notifications for Betreuer (news author gets comment notification)
        notificationRepository.create(
                betreuer.get(1).id(),
                NotificationType.NEWS_COMMENT,
                NotificationData.of(
                        "notification.newsComment",
                        Map.of(
                                "newsTitle",
                                "Neue Ausrüstung eingetroffen",
                                "author",
                                "Klaus Schulze",
                                "preview",
                                "Werden die alten Helme eingesammelt?"),
                        new NotificationData.NotificationLink("news-list")));

        // Exchange request notification for Betreuer (INVENTORY_MANAGEMENT)
        for (var m : betreuer) {
            notificationRepository.create(
                    m.id(),
                    NotificationType.EXCHANGE_NEW_REQUEST,
                    NotificationData.of(
                            "notification.exchangeNewRequest",
                            Map.of(
                                    "memberName",
                                    "Tim Berger",
                                    "inventoryName",
                                    "Blouson",
                                    "reason",
                                    "Zu klein geworden"),
                            new NotificationData.NotificationLink("inventory-exchanges")));
        }

        // Event registration status for some kids
        for (int i = 0; i < 3 && i < fortgeschritten.size(); i++) {
            notificationRepository.create(
                    fortgeschritten.get(i).id(),
                    NotificationType.EVENT_REGISTRATION_STATUS,
                    NotificationData.of(
                            "notification.eventRegistrationStatus",
                            Map.of("eventName", "Tag der offenen Tür", "status", "ACCEPTED"),
                            new NotificationData.NotificationLink("events-registrations")));
        }

        // New event notification for some members
        for (int i = 0; i < 5 && i < anfaenger.size(); i++) {
            notificationRepository.create(
                    anfaenger.get(i).id(),
                    NotificationType.NEW_EVENT,
                    NotificationData.of(
                            "notification.newEvent",
                            Map.of(
                                    "title",
                                    "Stadtfest Musterstadt",
                                    "eventDescription",
                                    "Stand der Jugendfeuerwehr beim Stadtfest"),
                            new NotificationData.NotificationLink("events-upcoming")));
        }

        // Group membership notification for some Eltern
        for (int i = 0; i < 3 && i < eltern.size(); i++) {
            notificationRepository.create(
                    eltern.get(i).id(),
                    NotificationType.MEMBER_ADDED_TO_GROUP,
                    NotificationData.of(
                            "notification.memberAddedToGroup",
                            Map.of("groupName", "Eltern"),
                            new NotificationData.NotificationLink("dashboard-overview")));
        }

        // Procurement notification for a kid
        notificationRepository.create(
                anfaenger.get(2).id(),
                NotificationType.PROCUREMENT_REQUESTED,
                NotificationData.of(
                        "notification.procurementRequested",
                        Map.of("inventoryName", "Handschuhe"),
                        new NotificationData.NotificationLink("dashboard-overview")));

        // Profile change notification for Betreuer
        notificationRepository.create(
                betreuer.getFirst().id(),
                NotificationType.PROFILE_FIELD_CHANGED,
                NotificationData.of(
                        "notification.profileFieldChanged",
                        Map.of("memberName", "Lukas Frank", "fieldName", "Allergien"),
                        new NotificationData.NotificationLink(
                                "members-detail", Map.of("id", anfaenger.get(0).id()))));

        log.info("Demo: Created sample notifications for dashboard");
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
            AttendanceTemplate templateAnfaenger,
            AttendanceTemplate templateFort,
            AttendanceTemplate templateGesamt,
            StationEvent evAnfaenger,
            StationEvent evFort,
            StationEvent evGesamt,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten,
            List<StationMember> betreuer) {
        var teamForAnfaenger = betreuer.subList(0, Math.min(2, betreuer.size()));
        var teamForFort = betreuer.subList(Math.min(1, betreuer.size()), Math.min(3, betreuer.size()));
        var teamForGesamt = betreuer.subList(0, Math.min(3, betreuer.size()));

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(14).withDayOfMonth(1);
        int sessionCount = 0;

        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            int weekOfYear = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            if (weekOfYear >= 28 && weekOfYear <= 33) continue; // summer break

            int dow = date.getDayOfWeek().getValue();
            boolean isToday = date.equals(today);

            if (dow == 1) { // Monday: Anfänger
                log.info("Demo: Creating attendance session for week {}", weekOfYear);
                Instant start = date.atTime(17, 30).toInstant(ZoneOffset.UTC);
                Instant end = date.atTime(19, 0).toInstant(ZoneOffset.UTC);
                var sess = attendanceRepository.createSession(
                        templateAnfaenger.id(), start, end, evAnfaenger.id(), "Übung Anfänger KW" + weekOfYear);
                if (!isToday) {
                    for (var m : anfaenger) {
                        var status = rng.nextInt(10) < 8
                                ? AttendanceEntry.AttendanceStatus.PRESENT
                                : AttendanceEntry.AttendanceStatus.ABSENT;
                        attendanceRepository.createEntry(
                                sess.id(), m.id(), status, AttendanceEntry.EntrySource.EXPECTED);
                    }
                    for (var m : teamForAnfaenger) {
                        attendanceRepository.createEntry(
                                sess.id(),
                                m.id(),
                                AttendanceEntry.AttendanceStatus.PRESENT,
                                AttendanceEntry.EntrySource.EXTRA);
                    }
                }
                sessionCount++;
            }

            if (dow == 3) { // Wednesday: Fortgeschritten
                log.info("Demo: Creating attendance session for week {}", weekOfYear);
                Instant start = date.atTime(18, 0).toInstant(ZoneOffset.UTC);
                Instant end = date.atTime(19, 30).toInstant(ZoneOffset.UTC);
                var sess = attendanceRepository.createSession(
                        templateFort.id(), start, end, evFort.id(), "Übung Fortgeschritten KW" + weekOfYear);
                if (!isToday) {
                    for (var m : fortgeschritten) {
                        var status = rng.nextInt(10) < 7
                                ? AttendanceEntry.AttendanceStatus.PRESENT
                                : AttendanceEntry.AttendanceStatus.ABSENT;
                        attendanceRepository.createEntry(
                                sess.id(), m.id(), status, AttendanceEntry.EntrySource.EXPECTED);
                    }
                    for (var m : teamForFort) {
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
                log.info("Demo: Creating attendance session for week {}", weekOfYear);
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
                    for (var m : anfaenger) {
                        var status = rng.nextInt(10) < 7
                                ? AttendanceEntry.AttendanceStatus.PRESENT
                                : AttendanceEntry.AttendanceStatus.ABSENT;
                        attendanceRepository.createEntry(
                                sess.id(), m.id(), status, AttendanceEntry.EntrySource.EXPECTED);
                    }
                    for (var m : fortgeschritten) {
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
        for (StationMember allKid : allKids) {
            if (rng.nextInt(3) != 0) continue; // ~1/3 of kids have been checked
            var kid = allKid;
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
                if (result == CheckResult.LOST) {
                    inventoryRepository.markLost(item.id());
                }
            }
            checkedCount++;
        }
        log.info("Demo: Created inventory checks for {} members", checkedCount);
    }

    private void seedInventory(
            int stationId,
            Random rng,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten,
            int anfaengerGroupId,
            int fortgeschrittenGroupId) {
        // Sizes reference from existing data
        var kleidungSizes = List.of("140", "146", "152", "158", "164", "170", "176", "182");
        var parkaSizes = List.of("XXXXS", "XXXS", "XXS", "XS", "S", "M", "L");
        var handschuhSizes = List.of("4", "5", "6", "7", "8", "9", "10");
        var stiefelSizes = List.of("34", "35", "36", "37", "38", "39", "40", "41", "42", "43");
        var tshirtSizes = List.of("128", "140", "152", "164", "176");

        // Create inventories
        var helm = inventoryRepository.create(stationId, "Helm", InventoryType.MIXED, false);

        var blouson = inventoryRepository.create(stationId, "Blouson", InventoryType.EXTERNAL, true);
        for (int i = 0; i < kleidungSizes.size(); i++)
            inventoryRepository.createSize(blouson.id(), kleidungSizes.get(i), i, "");

        var parka = inventoryRepository.create(stationId, "Parka", InventoryType.EXTERNAL, true);
        for (int i = 0; i < parkaSizes.size(); i++)
            inventoryRepository.createSize(parka.id(), parkaSizes.get(i), i, "");

        var latzhose = inventoryRepository.create(stationId, "Latzhose", InventoryType.EXTERNAL, true);
        for (int i = 0; i < kleidungSizes.size(); i++)
            inventoryRepository.createSize(latzhose.id(), kleidungSizes.get(i), i, "");

        var handschuhe = inventoryRepository.create(stationId, "Handschuhe", InventoryType.MIXED, true);
        for (int i = 0; i < handschuhSizes.size(); i++)
            inventoryRepository.createSize(handschuhe.id(), handschuhSizes.get(i), i, "");

        var stiefel = inventoryRepository.create(stationId, "Stiefel", InventoryType.INTERNAL, true);
        for (int i = 0; i < stiefelSizes.size(); i++)
            inventoryRepository.createSize(stiefel.id(), stiefelSizes.get(i), i, "");

        var sporttasche = inventoryRepository.create(stationId, "Sporttasche", InventoryType.INTERNAL, false);

        var tshirt = inventoryRepository.create(stationId, "T-Shirt", InventoryType.INTERNAL, true);
        for (int i = 0; i < tshirtSizes.size(); i++)
            inventoryRepository.createSize(tshirt.id(), tshirtSizes.get(i), i, "");

        // Requirements: Anfänger and Fortgeschritten members each need 1 of each (2 T-shirts)
        for (int groupId : List.of(anfaengerGroupId, fortgeschrittenGroupId)) {
            inventoryRepository.createRequirement(helm.id(), 0, groupId, 1);
            inventoryRepository.createRequirement(blouson.id(), 0, groupId, 1);
            inventoryRepository.createRequirement(parka.id(), 0, groupId, 1);
            inventoryRepository.createRequirement(latzhose.id(), 0, groupId, 1);
            inventoryRepository.createRequirement(handschuhe.id(), 0, groupId, 1);
            inventoryRepository.createRequirement(stiefel.id(), 0, groupId, 1);
            inventoryRepository.createRequirement(sporttasche.id(), 0, groupId, 1);
            inventoryRepository.createRequirement(tshirt.id(), 0, groupId, 2);
        }

        // Create items and assign to members
        var allKids = new ArrayList<>(anfaenger);
        allKids.addAll(fortgeschritten);

        int itemCounter = 1;
        var blousonSizeList = inventoryRepository.findSizes(blouson.id());
        var parkaSizeList = inventoryRepository.findSizes(parka.id());
        var latzhoseSizeList = inventoryRepository.findSizes(latzhose.id());
        var handschuheSizeList = inventoryRepository.findSizes(handschuhe.id());
        var stiefelSizeList = inventoryRepository.findSizes(stiefel.id());
        var tshirtSizeList = inventoryRepository.findSizes(tshirt.id());

        for (var member : allKids) {
            int idx = allKids.indexOf(member);

            // Helm (MIXED, no size) — station-provided = INTERNAL
            var helmItem = inventoryRepository.createItem(
                    helm.id(),
                    "H-" + String.format("%03d", itemCounter++),
                    "Helm",
                    null,
                    null,
                    InventoryItem.ItemSource.INTERNAL);
            inventoryRepository.assignItem(helmItem.id(), member.id());

            // Blouson (EXTERNAL)
            var blousonItem = inventoryRepository.createItem(
                    blouson.id(),
                    "BL-" + String.format("%03d", itemCounter++),
                    "Blouson",
                    blousonSizeList.get(idx % blousonSizeList.size()).id(),
                    null,
                    InventoryItem.ItemSource.EXTERNAL);
            inventoryRepository.assignItem(blousonItem.id(), member.id());

            // Parka (EXTERNAL)
            var parkaItem = inventoryRepository.createItem(
                    parka.id(),
                    "PA-" + String.format("%03d", itemCounter++),
                    "Parka",
                    parkaSizeList.get(idx % parkaSizeList.size()).id(),
                    null,
                    InventoryItem.ItemSource.EXTERNAL);
            inventoryRepository.assignItem(parkaItem.id(), member.id());

            // Latzhose (EXTERNAL)
            var latzItem = inventoryRepository.createItem(
                    latzhose.id(),
                    "LH-" + String.format("%03d", itemCounter++),
                    "Latzhose",
                    latzhoseSizeList.get(idx % latzhoseSizeList.size()).id(),
                    null,
                    InventoryItem.ItemSource.EXTERNAL);
            inventoryRepository.assignItem(latzItem.id(), member.id());

            // Handschuhe (MIXED) — station-provided = INTERNAL
            var handschuhItem = inventoryRepository.createItem(
                    handschuhe.id(),
                    "HS-" + String.format("%03d", itemCounter++),
                    "Handschuhe",
                    handschuheSizeList.get(idx % handschuheSizeList.size()).id(),
                    null,
                    InventoryItem.ItemSource.INTERNAL);
            inventoryRepository.assignItem(handschuhItem.id(), member.id());

            // Stiefel (INTERNAL)
            var stiefelItem = inventoryRepository.createItem(
                    stiefel.id(),
                    "ST-" + String.format("%03d", itemCounter++),
                    "Stiefel",
                    stiefelSizeList.get(idx % stiefelSizeList.size()).id(),
                    null,
                    InventoryItem.ItemSource.INTERNAL);
            inventoryRepository.assignItem(stiefelItem.id(), member.id());

            // T-Shirt (INTERNAL, 2 per member)
            for (int t = 0; t < 2; t++) {
                var tshirtItem = inventoryRepository.createItem(
                        tshirt.id(),
                        "TS-" + String.format("%03d", itemCounter++),
                        "T-Shirt",
                        tshirtSizeList.get(idx % tshirtSizeList.size()).id(),
                        null,
                        InventoryItem.ItemSource.INTERNAL);
                inventoryRepository.assignItem(tshirtItem.id(), member.id());
            }

            // Sporttasche (INTERNAL, ~70% get one, rest need procurement)
            if (rng.nextInt(10) < 7) {
                var tasche = inventoryRepository.createItem(
                        sporttasche.id(),
                        "SP-" + String.format("%03d", itemCounter++),
                        "Sporttasche",
                        null,
                        null,
                        InventoryItem.ItemSource.INTERNAL);
                inventoryRepository.assignItem(tasche.id(), member.id());
            }
        }

        // Add some unassigned spare items (INTERNAL)
        for (int i = 0; i < 5; i++) {
            inventoryRepository.createItem(
                    helm.id(),
                    "H-" + String.format("%03d", itemCounter++),
                    "Helm Ersatz",
                    null,
                    null,
                    InventoryItem.ItemSource.INTERNAL);
        }
        for (int i = 0; i < 3; i++) {
            inventoryRepository.createItem(
                    sporttasche.id(),
                    "SP-" + String.format("%03d", itemCounter++),
                    "Sporttasche Ersatz",
                    null,
                    null,
                    InventoryItem.ItemSource.INTERNAL);
        }

        // Add one personally owned Handschuh per size (MIXED → EXTERNAL = personally owned)
        var handschuhSizeListOwned = inventoryRepository.findSizes(handschuhe.id());
        for (var size : handschuhSizeListOwned) {
            var kid = allKids.get(rng.nextInt(allKids.size()));
            var ownedGlove = inventoryRepository.createItem(
                    handschuhe.id(),
                    "HS-" + String.format("%03d", itemCounter++),
                    "Handschuhe (eigen) " + size.label(),
                    size.id(),
                    "{\"owned\":true}",
                    InventoryItem.ItemSource.EXTERNAL);
            inventoryRepository.assignItem(ownedGlove.id(), kid.id());
        }

        // Generate item assignment history for internal items
        // For ~40% of items, create a history of 1-3 previous owners
        var internalInventoryIds = List.of(helm.id(), stiefel.id(), sporttasche.id(), handschuhe.id());
        var allInternalItems = new ArrayList<InventoryItem>();
        for (int invId : internalInventoryIds) {
            allInternalItems.addAll(inventoryRepository.findItems(invId));
        }

        int historyCount = 0;
        for (var item : allInternalItems) {
            if (item.assignedTo() == null) continue;
            if (rng.nextInt(10) < 6) continue; // skip 60%

            int prevOwnerCount = 1 + rng.nextInt(3);
            Instant cursor = Instant.now().minus(Duration.ofDays(365 + rng.nextInt(730)));

            for (int h = 0; h < prevOwnerCount; h++) {
                var prevOwner = allKids.get(rng.nextInt(allKids.size()));
                var prevAccount =
                        accountRepository.findById(prevOwner.accountId()).orElse(null);
                String prevName = prevAccount != null
                        ? (prevAccount.firstName() + " " + prevAccount.lastName()).trim()
                        : "#" + prevOwner.id();

                Instant givenOut = cursor;
                cursor = cursor.plus(Duration.ofDays(30 + rng.nextInt(180)));
                Instant returned = cursor;
                cursor = cursor.plus(Duration.ofDays(1 + rng.nextInt(14)));

                inventoryRepository.createHistoryWithDates(item.id(), prevOwner.id(), prevName, givenOut, returned);
                historyCount++;
            }

            // Current owner — given out after last return, no return date
            var currentAccount = accountRepository
                    .findById(allKids.stream()
                            .filter(m -> m.id() == item.assignedTo())
                            .findFirst()
                            .map(StationMember::accountId)
                            .orElse(0))
                    .orElse(null);
            String currentName = currentAccount != null
                    ? (currentAccount.firstName() + " " + currentAccount.lastName()).trim()
                    : "#" + item.assignedTo();
            inventoryRepository.createHistoryWithDates(item.id(), item.assignedTo(), currentName, cursor, null);
            historyCount++;
        }

        log.info("Demo: Created {} inventory items with {} history entries", itemCounter - 1, historyCount);
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
