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
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.repository.ExchangeRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ProcurementRepository;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.news.repository.NewsRepository;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

/**
 * Service for managing the demo environment including database reset, demo data seeding
 * with sample members, groups, attendance, events, inventory, forms, and notifications.
 */
@Singleton
public class DemoService {
    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    private static final String PASSWORD = "demo";

    private final Demo demoConfig;
    private final Database databaseConfig;
    private final DataSource dataSource;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final EventRepository eventRepository;
    private final AttendanceRepository attendanceRepository;
    private final InventoryRepository inventoryRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final NewsRepository newsRepository;
    private final ExchangeRepository exchangeRepository;
    private final ProcurementRepository procurementRepository;
    private final UserTagRepository userTagRepository;
    private final ProfileFieldChangeRepository profileFieldChangeRepository;
    private final EventFieldRepository eventFieldRepository;
    private final StationRepository stationRepository;
    private final PasswordHasher passwordHasher;
    private final DemoFormSeeder formSeeder;
    private final DemoNotificationSeeder notificationSeeder;
    private final DemoAttendanceSeeder attendanceSeeder;
    private final DemoInventorySeeder inventorySeeder;
    private final DemoWaitingListSeeder waitingListSeeder;
    private final DemoQuizSeeder quizSeeder;
    private final DemoMediaSeeder mediaSeeder;
    private final DemoKnowledgeBaseSeeder kbSeeder;
    private final DemoProtocolSeeder protocolSeeder;
    private final DemoFederationSeeder federationSeeder;
    private final DemoLendingSeeder lendingSeeder;
    private final ApplicationSettingRepository applicationSettingRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile Instant lastActivity = Instant.now();
    private volatile boolean needsReset = false;

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
            ProfileFieldRepository profileFieldRepository,
            NewsRepository newsRepository,
            ExchangeRepository exchangeRepository,
            ProcurementRepository procurementRepository,
            UserTagRepository userTagRepository,
            ProfileFieldChangeRepository profileFieldChangeRepository,
            EventFieldRepository eventFieldRepository,
            PasswordHasher passwordHasher,
            DemoFormSeeder formSeeder,
            DemoNotificationSeeder notificationSeeder,
            DemoAttendanceSeeder attendanceSeeder,
            DemoInventorySeeder inventorySeeder,
            DemoWaitingListSeeder waitingListSeeder,
            DemoQuizSeeder quizSeeder,
            DemoMediaSeeder mediaSeeder,
            DemoKnowledgeBaseSeeder kbSeeder,
            DemoProtocolSeeder protocolSeeder,
            DemoFederationSeeder federationSeeder,
            DemoLendingSeeder lendingSeeder,
            ApplicationSettingRepository applicationSettingRepository) {
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
        this.profileFieldRepository = profileFieldRepository;
        this.newsRepository = newsRepository;
        this.exchangeRepository = exchangeRepository;
        this.procurementRepository = procurementRepository;
        this.userTagRepository = userTagRepository;
        this.profileFieldChangeRepository = profileFieldChangeRepository;
        this.eventFieldRepository = eventFieldRepository;
        this.passwordHasher = passwordHasher;
        this.formSeeder = formSeeder;
        this.notificationSeeder = notificationSeeder;
        this.attendanceSeeder = attendanceSeeder;
        this.inventorySeeder = inventorySeeder;
        this.waitingListSeeder = waitingListSeeder;
        this.quizSeeder = quizSeeder;
        this.mediaSeeder = mediaSeeder;
        this.kbSeeder = kbSeeder;
        this.protocolSeeder = protocolSeeder;
        this.federationSeeder = federationSeeder;
        this.lendingSeeder = lendingSeeder;
        this.applicationSettingRepository = applicationSettingRepository;
    }

    public boolean isEnabled() {
        return demoConfig.enabled() || demoConfig.dev();
    }

    public void initialize() {
        if (demoConfig.dev()) {
            log.info("Dev mode enabled. Seeding database once...");
            resetAndSeed();
            return;
        }
        if (!demoConfig.enabled()) return;
        log.info("Demo mode enabled. Idle reset after {} minutes of inactivity", demoConfig.idleResetMinutes());
        resetAndSeed();
        // Check every minute if idle timeout has been reached
        scheduler.scheduleAtFixedRate(this::checkIdleReset, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Records an authenticated request. Resets the idle timer and marks the data as dirty.
     * Called from the API access handler on every authenticated request.
     */
    public void recordActivity() {
        lastActivity = Instant.now();
        needsReset = true;
    }

    private void checkIdleReset() {
        if (!needsReset) return;
        var idleMinutes = Duration.between(lastActivity, Instant.now()).toMinutes();
        if (idleMinutes >= demoConfig.idleResetMinutes()) {
            log.info("Demo: {} minutes idle, resetting data...", idleMinutes);
            needsReset = false;
            resetAndSeed();
        }
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
        stationRepository.updatePublicCalendarEnabled(station.id(), true);
        stationRepository.updatePublicKbMode(station.id(), "ALLOW_ALL");
        stationRepository.updateDiscoverySettings(
                station.id(),
                DiscoveryVisibility.PUBLIC,
                "Jugendfeuerwehr Musterstadt — Übungen, Wettbewerbe und mehr",
                true);
        try {
            var logoBytes = Files.readAllBytes(Path.of("templates", "graphics", "logo.png"));
            stationRepository.updateLogo(station.id(), logoBytes, "image/png");
        } catch (IOException e) {
            log.warn("Demo: Could not load logo.png", e);
        }

        var adminMember = stationMemberRepository.create(station.id(), admin.id());
        var managerRole = stationMemberRepository.findRoleByName(Roles.MANAGER).orElseThrow();
        var loginRole = stationMemberRepository.findRoleByName(Roles.LOGIN).orElseThrow();
        var memberRole = stationMemberRepository.findRoleByName(Roles.MEMBER).orElseThrow();
        var teamRole = stationMemberRepository.findRoleByName(Roles.TEAM).orElseThrow();
        var memberManagerRole =
                stationMemberRepository.findRoleByName(Roles.GUARDIAN).orElseThrow();
        var attendanceMgmt =
                stationMemberRepository.findRoleByName(Roles.ATTENDANCE_MANAGER).orElseThrow();
        var eventMgmt =
                stationMemberRepository.findRoleByName(Roles.EVENT_MANAGER).orElseThrow();
        var memberMgmt =
                stationMemberRepository.findRoleByName(Roles.MEMBER_MANAGER).orElseThrow();

        stationMemberRepository.addRole(adminMember.id(), managerRole.id());
        stationMemberRepository.addRole(adminMember.id(), loginRole.id());
        stationRepository.setOwner(station.id(), adminMember.id());

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

        // Create Eltern (member managers — GUARDIAN role, not MEMBER)
        boolean firstEltern = true;
        for (var u : eltern) {
            var m = createGuardian(
                    u.firstName(), u.lastName(), hash, station.id(), loginRole.id(), memberManagerRole.id());
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
        if (anfaengerMembers.size() >= 5 && !betreuerMembers.isEmpty()) {
            int bId = betreuerMembers.getFirst().id();
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
                    betreuerMembers.getFirst().id(),
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
            profileFieldChangeRepository.create(
                    fieldGeburtstag.id(),
                    anfaengerMembers.get(3).id(),
                    "\"2014-05-10\"",
                    "\"2014-05-11\"",
                    betreuerMembers.getFirst().id(),
                    true);
            // Non-acknowledged changes that don't require ack
            profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    fortgeschrittenMembers.getFirst().id(),
                    "\"0151 77766655\"",
                    "\"0176 88899900\"",
                    fortgeschrittenMembers.getFirst().id(),
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
        // Make Veranstaltungen public (all events in this category visible on public calendar)
        eventRepository.updateCategory(
                catVeranstaltung.id(), catVeranstaltung.name(), catVeranstaltung.position(), null, true);

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

        attendanceSeeder.seedAttendanceSessions(
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
        inventorySeeder.seedInventory(
                station.id(),
                rng,
                anfaengerMembers,
                fortgeschrittenMembers,
                groupAnfaenger.id(),
                groupFortgeschritten.id());

        // -- Inventory checks (done by Betreuer) --
        inventorySeeder.seedInventoryChecks(
                station.id(), rng, betreuerMembers, anfaengerMembers, fortgeschrittenMembers);

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

        // -- Öffentlichkeitsarbeit events --
        var catOeffentlichkeit = eventRepository.createCategory(station.id(), "Öffentlichkeitsarbeit", 3);
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
            var oeEvent = eventRepository.create(
                    station.id(),
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
                    catOeffentlichkeit.id());
            eventFieldRepository.create(oeEvent.id(), "Ort", "string", "{}", oeOrte[e], 0, true, null, true);
            eventFieldRepository.create(
                    oeEvent.id(), "Treffpunkt", "string", "{}", "Feuerwehrgerätehaus", 1, true, null, true);
            // Create registrations with rotation: offset accepted members per event for variance
            int count = Math.min(oeMemberCounts[e], allMembers.size());
            int acceptOffset = e * 3; // shift which members get accepted each event
            for (int i = 0; i < count; i++) {
                int rotatedIdx = (i + acceptOffset) % allMembers.size();
                var status = i < 6
                        ? EventRegistration.RegistrationStatus.ACCEPTED
                        : EventRegistration.RegistrationStatus.DENIED;
                eventRepository.createRegistration(
                        oeEvent.id(), allMembers.get(rotatedIdx).id(), eventDate, status, null);
            }
        }

        // One open event with pending (unconfirmed) registrations
        LocalDate openDate = LocalDate.now().plusWeeks(1);
        Instant openStart = openDate.atTime(9, 0).toInstant(ZoneOffset.UTC);
        Instant openEnd = openDate.atTime(15, 0).toInstant(ZoneOffset.UTC);
        Instant openDeadline = LocalDate.now().plusDays(3).atTime(23, 59).toInstant(ZoneOffset.UTC);
        var oeOpen = eventRepository.create(
                station.id(),
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
                catOeffentlichkeit.id());
        eventFieldRepository.create(
                oeOpen.id(), "Ort", "string", "{}", "Rathausplatz Musterstadt", 0, true, null, true);
        eventFieldRepository.create(
                oeOpen.id(), "Treffpunkt", "string", "{}", "Feuerwehrgerätehaus 08:30", 1, true, null, true);
        eventFieldRepository.create(
                oeOpen.id(),
                "Hinweis",
                "string",
                "{}",
                "Dienstkleidung und Ausrüstung mitbringen",
                2,
                false,
                null,
                false);
        // 14 registrations: 6 accepted, 8 pending (not yet confirmed)
        int openCount = Math.min(14, allMembers.size());
        for (int i = 0; i < openCount; i++) {
            var status = i < 6
                    ? EventRegistration.RegistrationStatus.ACCEPTED
                    : EventRegistration.RegistrationStatus.PENDING;
            eventRepository.createRegistration(oeOpen.id(), allMembers.get(i).id(), openDate, status, null);
        }

        // -- Event Fields --
        // Per-event fields
        eventFieldRepository.create(
                tagDerOffenenTuer.id(), "Ort", "string", "{}", "Feuerwehrhaus Musterstadt", 0, true, null, true);
        eventFieldRepository.create(
                tagDerOffenenTuer.id(), "Treffpunkt", "string", "{}", "Haupteingang", 1, true, null, true);
        eventFieldRepository.create(
                tagDerOffenenTuer.id(), "Hinweis", "string", "{}", "Dienstkleidung tragen", 2, false, null, false);
        eventFieldRepository.create(
                stadtfest.id(), "Ort", "string", "{}", "Marktplatz Musterstadt", 0, true, null, true);
        eventFieldRepository.create(
                stadtfest.id(), "Treffpunkt", "string", "{}", "Stand der Jugendfeuerwehr", 1, true, null, true);
        eventFieldRepository.create(
                kreisWettbewerb.id(), "Ort", "string", "{}", "Sportplatz Nachbarstadt", 0, true, null, true);
        eventFieldRepository.create(
                kreisWettbewerb.id(),
                "Hinweis",
                "string",
                "{}",
                "Wettkampfkleidung und Ausrüstung mitbringen",
                1,
                false,
                null,
                false);
        // Recurring event fields
        eventFieldRepository.create(
                evAnfaenger.id(), "Ort", "string", "{}", "Feuerwehrhaus Musterstadt", 0, true, null, true);
        eventFieldRepository.create(
                evAnfaenger.id(), "Hinweis", "string", "{}", "Sportkleidung mitbringen", 1, false, null, false);
        eventFieldRepository.create(
                evFort.id(), "Ort", "string", "{}", "Feuerwehrhaus Musterstadt", 0, true, null, true);
        eventFieldRepository.create(
                evFort.id(), "Hinweis", "string", "{}", "Schutzausrüstung wird gestellt", 1, false, null, false);
        eventFieldRepository.create(
                evGesamt.id(), "Ort", "string", "{}", "Feuerwehrhaus Musterstadt", 0, true, null, true);
        eventFieldRepository.create(evGesamt.id(), "Treffpunkt", "string", "{}", "Fahrzeughalle", 1, true, null, true);
        eventFieldRepository.create(
                theorieabend.id(), "Ort", "string", "{}", "Schulungsraum Feuerwehrhaus", 0, true, null, true);
        eventFieldRepository.create(
                theorieabend.id(), "Hinweis", "string", "{}", "Schreibzeug mitbringen", 1, false, null, false);

        // -- News --
        var news1 = newsRepository.create(
                station.id(),
                "Willkommen bei der Jugendfeuerwehr!",
                """
                Herzlich willkommen auf unserer neuen Plattform! Hier findet ihr alle wichtigen Informationen rund um unsere **Jugendfeuerwehr**.

                ## Was ist neu?

                Wir haben viele neue Funktionen für euch:

                - **Terminübersicht** — Alle Übungen, Veranstaltungen und Wettbewerbe auf einen Blick
                - **Anwesenheitsverwaltung** — Schnelles Ein- und Auschecken bei Übungen
                - **Inventarverwaltung** — Eure Ausrüstung immer im Blick
                - **Wissensdatenbank** — Lernmaterial und Protokolle

                ## Erste Schritte

                1. Prüft euer **Profil** und ergänzt fehlende Daten
                2. Schaut euch die **kommenden Termine** an
                3. Meldet euch für den nächsten **Wettbewerb** an

                > **Tipp:** Bei Fragen könnt ihr jederzeit die Betreuer ansprechen oder die Hilfe-Seite nutzen.

                Wir freuen uns auf eine tolle Zeit! 🚒
                """,
                "<p>Herzlich willkommen auf unserer neuen Plattform! Hier findet ihr alle wichtigen Informationen rund um unsere <strong>Jugendfeuerwehr</strong>.</p><h2>Was ist neu?</h2><p>Wir haben viele neue Funktionen für euch:</p><ul><li><strong>Terminübersicht</strong> — Alle Übungen, Veranstaltungen und Wettbewerbe auf einen Blick</li><li><strong>Anwesenheitsverwaltung</strong> — Schnelles Ein- und Auschecken bei Übungen</li><li><strong>Inventarverwaltung</strong> — Eure Ausrüstung immer im Blick</li><li><strong>Wissensdatenbank</strong> — Lernmaterial und Protokolle</li></ul><h2>Erste Schritte</h2><ol><li>Prüft euer <strong>Profil</strong> und ergänzt fehlende Daten</li><li>Schaut euch die <strong>kommenden Termine</strong> an</li><li>Meldet euch für den nächsten <strong>Wettbewerb</strong> an</ol><blockquote><p><strong>Tipp:</strong> Bei Fragen könnt ihr jederzeit die Betreuer ansprechen oder die Hilfe-Seite nutzen.</p></blockquote><p>Wir freuen uns auf eine tolle Zeit! 🚒</p>",
                adminMember.id());
        var news2 = newsRepository.create(
                station.id(),
                "Kreiswettbewerb: Anmeldung geöffnet",
                """
                Die Anmeldung zum **Kreiswettbewerb** am 20. des übernächsten Monats ist jetzt geöffnet!

                ## Wichtige Infos

                | | Details |
                |---|---|
                | **Datum** | 20. des übernächsten Monats |
                | **Ort** | Sportplatz Nachbarstadt |
                | **Treffpunkt** | Feuerwehrgerätehaus, 07:30 Uhr |

                ### Was wird bewertet?

                - Löschangriff
                - Staffellauf
                - Knotenkunde
                - Erste Hilfe

                Bitte meldet euch **bis spätestens nächste Woche** über die Terminseite an. Die Plätze sind begrenzt.

                > *Teilnehmen dürfen alle Fortgeschrittenen.*
                """,
                "<p>Die Anmeldung zum <strong>Kreiswettbewerb</strong> am 20. des übernächsten Monats ist jetzt geöffnet!</p><h2>Wichtige Infos</h2><table><tr><td></td><td>Details</td></tr><tr><td><strong>Datum</strong></td><td>20. des übernächsten Monats</td></tr><tr><td><strong>Ort</strong></td><td>Sportplatz Nachbarstadt</td></tr><tr><td><strong>Treffpunkt</strong></td><td>Feuerwehrgerätehaus, 07:30 Uhr</td></tr></table><h3>Was wird bewertet?</h3><ul><li>Löschangriff</li><li>Staffellauf</li><li>Knotenkunde</li><li>Erste Hilfe</li></ul><p>Bitte meldet euch <strong>bis spätestens nächste Woche</strong> über die Terminseite an. Die Plätze sind begrenzt.</p><blockquote><p><em>Teilnehmen dürfen alle Fortgeschrittenen.</em></p></blockquote>",
                betreuerMembers.getFirst().id());

        // Comments on news
        var comment1 = newsRepository.createComment(
                news1.id(), null, elternMembers.get(0).id(), "Super, endlich eine moderne Plattform!");
        newsRepository.createComment(
                news1.id(), comment1.id(), betreuerMembers.getFirst().id(), "Danke! Bei Fragen einfach melden.");
        newsRepository.createComment(
                news1.id(), null, elternMembers.get(1).id(), "Kann man hier auch Abwesenheiten eintragen?");
        newsRepository.createComment(
                news2.id(), null, fortgeschrittenMembers.get(0).id(), "Ich bin dabei! \uD83D\uDCAA");
        var comment2 = newsRepository.createComment(
                news2.id(), null, fortgeschrittenMembers.get(1).id(), "Wie viele Plätze gibt es?");
        newsRepository.createComment(
                news2.id(), comment2.id(), betreuerMembers.get(0).id(), "Wir haben 8 Plätze. Bitte schnell anmelden!");

        var news3 = newsRepository.create(
                station.id(),
                "Neue Ausrüstung eingetroffen",
                """
                Die bestellten **Helme und Handschuhe** sind eingetroffen! 🎉

                ## Verteilung

                Die Verteilung findet bei der **nächsten Übung** statt. Bitte beachtet:

                1. Prüft eure **Größen im Inventar** vorab
                2. Meldet euch bei Unstimmigkeiten bei den Betreuern
                3. Bringt eure **alten Helme** zur Rückgabe mit

                > Die neuen Helme entsprechen der aktuellen **DIN EN 443** Norm und bieten verbesserten Schutz.
                """,
                "<p>Die bestellten <strong>Helme und Handschuhe</strong> sind eingetroffen! 🎉</p><h2>Verteilung</h2><p>Die Verteilung findet bei der <strong>nächsten Übung</strong> statt. Bitte beachtet:</p><ol><li>Prüft eure <strong>Größen im Inventar</strong> vorab</li><li>Meldet euch bei Unstimmigkeiten bei den Betreuern</li><li>Bringt eure <strong>alten Helme</strong> zur Rückgabe mit</li></ol><blockquote><p>Die neuen Helme entsprechen der aktuellen <strong>DIN EN 443</strong> Norm und bieten verbesserten Schutz.</p></blockquote>",
                betreuerMembers.get(1).id());

        newsRepository.create(
                station.id(),
                "Sommerferien: Übungspause",
                """
                Während der **Sommerferien** finden keine regulären Übungen statt.

                ## Zeitraum

                Der Übungsbetrieb **pausiert** während der gesamten Schulferien. Wir starten wieder am **ersten Montag nach den Ferien**.

                ### Trotzdem aktiv bleiben?

                - Das **Wissenscenter** bleibt verfügbar — nutzt die Zeit zum Lernen
                - Prüft eure **Ausrüstung** und meldet Mängel vorab
                - Die **Anmeldung** für den Herbst-Wettbewerb öffnet in den Ferien

                Wir wünschen allen **schöne und erholsame Ferien**! ☀️
                """,
                "<p>Während der <strong>Sommerferien</strong> finden keine regulären Übungen statt.</p><h2>Zeitraum</h2><p>Der Übungsbetrieb <strong>pausiert</strong> während der gesamten Schulferien. Wir starten wieder am <strong>ersten Montag nach den Ferien</strong>.</p><h3>Trotzdem aktiv bleiben?</h3><ul><li>Das <strong>Wissenscenter</strong> bleibt verfügbar — nutzt die Zeit zum Lernen</li><li>Prüft eure <strong>Ausrüstung</strong> und meldet Mängel vorab</li><li>Die <strong>Anmeldung</strong> für den Herbst-Wettbewerb öffnet in den Ferien</li></ul><p>Wir wünschen allen <strong>schöne und erholsame Ferien</strong>! ☀️</p>",
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
        log.info("Demo: Created procurements");

        // -- Forms --
        formSeeder.seedForms(
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
        var sessionExpiry = Instant.now().plus(Duration.ofHours(24));
        for (String demoUserAgent : demoUserAgents) {
            var accountId = admin.id();
            var token = UUID.randomUUID().toString();
            accountRepository.createSession(accountId, token, sessionExpiry, demoUserAgent, null);
        }
        log.info("Demo: Created previous sessions");

        // -- Notifications (demo data so users see them on the dashboard) --
        notificationSeeder.seedNotifications(
                station.id(),
                adminMember,
                betreuerMembers,
                elternMembers,
                anfaengerMembers,
                fortgeschrittenMembers,
                tagDerOffenenTuer.id(),
                stadtfest.id());
        log.info("Demo: Created Notifications");

        // -- Waiting List --
        waitingListSeeder.seedWaitingList(station.id(), groupAnfaenger.id(), memberRole.id());
        log.info("Demo: Created Waiting list");

        // -- Quiz --
        var quizTestTakers = new ArrayList<Integer>();
        for (var m : anfaengerMembers) quizTestTakers.add(m.id());
        for (var m : fortgeschrittenMembers) quizTestTakers.add(m.id());
        quizSeeder.seedQuiz(station.id(), adminMember.id(), quizTestTakers);
        log.info("Demo: Created Quiz entries");

        // -- Knowledge Base --
        kbSeeder.seed(station.id(), adminMember.id());
        log.info("Demo: Created Knowledge Base content");

        // -- Test Protocols --
        var protocolTestees = new ArrayList<Integer>();
        for (var m : anfaengerMembers) protocolTestees.add(m.id());
        protocolSeeder.seed(station.id(), adminMember.id(), protocolTestees);
        log.info("Demo: Created Test Protocol data");

        // -- Profile Pictures & Station Logo --
        mediaSeeder.seedProfilePictures(
                station.id(), adminMember, betreuerMembers, elternMembers, anfaengerMembers, fortgeschrittenMembers);
        log.info("Demo: Created profile pictures");

        // -- Federation --
        int partnerStationId = federationSeeder.seed(station.id(), adminMember.id());
        log.info("Demo: Created federation data");

        // -- Lending --
        lendingSeeder.seed(station.id(), partnerStationId, adminMember.id());
        log.info("Demo: Created lending data");

        // -- Public Knowledge Base --
        stationRepository.updatePublicKbMode(station.id(), "ALLOW_ALL");
        log.info("Demo: Enabled public knowledge base");

        // -- Settings --
        applicationSettingRepository.setBoolean("station_registration_enabled", false);
        log.info("Demo: Disabled station registration");

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

    private StationMember createGuardian(
            String firstName, String lastName, String hash, int stationId, int loginRoleId, int guardianRoleId) {
        String email = firstName.toLowerCase() + "@" + lastName.toLowerCase() + ".local";
        var account = accountRepository.create(email, firstName, lastName, true);
        accountRepository.createCredential(account.id(), hash);
        var member = stationMemberRepository.create(stationId, account.id());
        stationMemberRepository.addRole(member.id(), loginRoleId);
        stationMemberRepository.addRole(member.id(), guardianRoleId);
        return member;
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
