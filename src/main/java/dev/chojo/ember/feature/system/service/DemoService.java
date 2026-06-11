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
import dev.chojo.ember.api.roles.InstanceUserType;
import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.api.roles.StationUserType;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Database;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.repository.ExchangeRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.inventory.service.ProcurementService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private final InventoryRepository inventoryRepository;
    private final ExchangeRepository exchangeRepository;
    private final StationRepository stationRepository;
    private final PasswordHasher passwordHasher;
    private final DemoMemberSeeder memberSeeder;
    private final DemoEventSeeder eventSeeder;
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
    private final DemoBoardSeeder boardSeeder;
    private final DemoProcedureSeeder procedureSeeder;
    private final DemoPageSeeder pageSeeder;
    private final DemoNewsSeeder newsSeeder;
    private final ApplicationSettingRepository applicationSettingRepository;
    private final ExchangeService exchangeService;
    private final ProcurementService procurementService;
    private final FeedTokenService feedTokenService;
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
            InventoryRepository inventoryRepository,
            ExchangeRepository exchangeRepository,
            PasswordHasher passwordHasher,
            DemoMemberSeeder memberSeeder,
            DemoEventSeeder eventSeeder,
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
            DemoBoardSeeder boardSeeder,
            DemoProcedureSeeder procedureSeeder,
            DemoPageSeeder pageSeeder,
            DemoNewsSeeder newsSeeder,
            ApplicationSettingRepository applicationSettingRepository,
            ExchangeService exchangeService,
            ProcurementService procurementService,
            FeedTokenService feedTokenService) {
        this.demoConfig = demoConfig;
        this.databaseConfig = databaseConfig;
        this.dataSource = dataSource;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.inventoryRepository = inventoryRepository;
        this.exchangeRepository = exchangeRepository;
        this.passwordHasher = passwordHasher;
        this.memberSeeder = memberSeeder;
        this.eventSeeder = eventSeeder;
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
        this.boardSeeder = boardSeeder;
        this.procedureSeeder = procedureSeeder;
        this.pageSeeder = pageSeeder;
        this.newsSeeder = newsSeeder;
        this.applicationSettingRepository = applicationSettingRepository;
        this.exchangeService = exchangeService;
        this.procurementService = procurementService;
        this.feedTokenService = feedTokenService;
    }

    public boolean isEnabled() {
        return demoConfig.enabled() || demoConfig.dev();
    }

    public void initialize() {
        if (demoConfig.dev()) {
            if (schemaUnchanged()) {
                log.info("Dev mode: schema unchanged, skipping seed.");
                return;
            }
            log.info("Dev mode: schema changed, re-seeding database...");
            resetAndSeed();
            writeSchemaHash();
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

    private static final Path SCHEMA_HASH_FILE = Path.of(".demo-schema-hash");

    private boolean schemaUnchanged() {
        try {
            if (!Files.exists(SCHEMA_HASH_FILE)) return false;
            var stored = Files.readString(SCHEMA_HASH_FILE).strip();
            return stored.equals(computeSchemaHash());
        } catch (Exception e) {
            log.warn("Could not read schema hash, will re-seed", e);
            return false;
        }
    }

    private void writeSchemaHash() {
        try {
            Files.writeString(SCHEMA_HASH_FILE, computeSchemaHash());
        } catch (Exception e) {
            log.warn("Could not write schema hash file", e);
        }
    }

    private String computeSchemaHash() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            // Hash the version file
            try (InputStream is = getClass().getResourceAsStream("/database/version")) {
                if (is != null) digest.update(is.readAllBytes());
            }
            // Hash all patch files in order
            for (int i = 1; ; i++) {
                try (InputStream is = getClass().getResourceAsStream("/database/postgresql/1/patch_" + i + ".sql")) {
                    if (is == null) break;
                    digest.update(is.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Failed to compute schema hash", e);
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
        accountRepository.setInstanceUserType(admin.id(), InstanceUserType.ADMINISTRATOR);

        // -- Station --
        var station = stationRepository.create(
                "Jugendfeuerwehr Musterstadt", UUID.fromString("00000000-0000-4000-a000-000000000001"));
        stationRepository.updateTimezone(station.id(), "Europe/Berlin");
        stationRepository.updateLocale(station.id(), "de-DE");
        stationRepository.updatePublicSlug(station.id(), "jugendfeuerwehr-musterstadt");
        stationRepository.updatePublicCalendarEnabled(station.id(), true);
        stationRepository.updatePublicPagesEnabled(station.id(), true);
        stationRepository.updatePublicWaitlistEnabled(station.id(), true);
        stationRepository.updatePublicBlogEnabled(station.id(), true);
        stationRepository.updatePublicKbMode(station.id(), PublicKbMode.ALLOW_ALL);
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
        var managerRole = stationMemberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        var loginRole = stationMemberRepository
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow();

        stationMemberRepository.setUserType(adminMember.id(), StationUserType.MANAGER);
        stationMemberRepository.grantPermission(adminMember.id(), managerRole.id());
        stationMemberRepository.grantPermission(adminMember.id(), loginRole.id());
        stationRepository.setOwner(station.id(), adminMember.id());

        // -- Members (must run first, all other seeders depend on member data) --
        var members = memberSeeder.seed(station.id(), adminMember, hash, rng);

        // -- Events (must run before parallel section, attendance/notifications depend on event data) --
        var events = eventSeeder.seed(
                station.id(),
                members.groupAnfaenger().id(),
                members.groupFortgeschritten().id(),
                members.anfaenger(),
                members.fortgeschritten());

        // -- News (must run before parallel section, notifications depend on news ID) --
        var news = newsSeeder.seed(
                station.id(), adminMember.id(), members.betreuer(), members.eltern(), members.fortgeschritten());

        // -- Parallel module seeding --
        log.info("Demo: Starting parallel module seeding...");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<CompletableFuture<?>>();

            // Attendance sessions
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        attendanceSeeder.seedAttendanceSessions(
                                new Random(42_001),
                                events.templateUebung(),
                                events.templateGesamt(),
                                events.evUebung(),
                                events.evGesamt(),
                                members.anfaenger(),
                                members.fortgeschritten(),
                                members.betreuer());
                        log.info("Demo: Created attendance sessions");
                    },
                    executor));

            // Inventory -> Checks -> Exchanges -> Procurement
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        var rngInv = new Random(42_002);
                        inventorySeeder.seedInventory(
                                station.id(),
                                rngInv,
                                members.anfaenger(),
                                members.fortgeschritten(),
                                members.groupAnfaenger().id(),
                                members.groupFortgeschritten().id());
                        inventorySeeder.seedInventoryChecks(
                                station.id(),
                                rngInv,
                                members.betreuer(),
                                members.anfaenger(),
                                members.fortgeschritten());
                        seedExchanges(
                                station.id(),
                                rngInv,
                                members.anfaenger(),
                                members.fortgeschritten(),
                                members.betreuer());
                        seedProcurements(station.id(), members.anfaenger(), members.fortgeschritten());
                    },
                    executor));

            // Forms
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        formSeeder.seedForms(
                                station.id(),
                                adminMember,
                                members.anfaenger(),
                                members.fortgeschritten(),
                                StationUserType.MEMBER.name(),
                                StationUserType.GUARDIAN.name(),
                                members.groupAnfaenger().id(),
                                members.tagWettkampf().id(),
                                new Random(42_003));
                        log.info("Demo: Created forms");
                    },
                    executor));

            // Demo sessions
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        var demoUserAgents = List.of(
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
                                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15",
                                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36",
                                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
                                "Mozilla/5.0 (X11; Linux x86_64; rv:150.0) Gecko/20100101 Firefox/150.0");
                        var sessionExpiry = Instant.now().plus(Duration.ofHours(24));
                        for (String demoUserAgent : demoUserAgents) {
                            var token = UUID.randomUUID().toString();
                            accountRepository.createSession(admin.id(), token, sessionExpiry, demoUserAgent, null);
                        }
                        log.info("Demo: Created previous sessions");
                    },
                    executor));

            // Notifications
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        notificationSeeder.seedNotifications(
                                station.id(),
                                adminMember,
                                members.betreuer(),
                                members.eltern(),
                                members.anfaenger(),
                                members.fortgeschritten(),
                                events.tagDerOffenenTuerId(),
                                events.stadtfestId(),
                                news.firstNewsId());
                        log.info("Demo: Created Notifications");
                    },
                    executor));

            // Waiting List
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        waitingListSeeder.seedWaitingList(
                                station.id(), members.groupAnfaenger().id());
                        log.info("Demo: Created Waiting list");
                    },
                    executor));

            // Quiz
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        var quizTestTakers = new ArrayList<Integer>();
                        for (var m : members.anfaenger()) quizTestTakers.add(m.id());
                        for (var m : members.fortgeschritten()) quizTestTakers.add(m.id());
                        quizSeeder.seedQuiz(station.id(), adminMember.id(), quizTestTakers);
                        log.info("Demo: Created Quiz entries");
                    },
                    executor));

            // Knowledge Base
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        kbSeeder.seed(station.id(), adminMember.id());
                        log.info("Demo: Created Knowledge Base content");
                    },
                    executor));

            // Public Pages
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        pageSeeder.seed(station.id(), adminMember.id());
                        log.info("Demo: Created Public Pages");
                    },
                    executor));

            // Protocols
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        var protocolTestees = new ArrayList<Integer>();
                        for (var m : members.anfaenger()) protocolTestees.add(m.id());
                        protocolSeeder.seed(station.id(), adminMember.id(), protocolTestees);
                        log.info("Demo: Created Test Protocol data");
                    },
                    executor));

            // Procedures
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        procedureSeeder.seed(
                                station.id(), adminMember, members.betreuer(), members.anfaenger(), new Random(42_020));
                        log.info("Demo: Created Procedure data");
                    },
                    executor));

            // Media / Profile Pictures
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        mediaSeeder.seedProfilePictures(
                                station.id(),
                                adminMember,
                                members.betreuer(),
                                members.eltern(),
                                members.anfaenger(),
                                members.fortgeschritten());
                        log.info("Demo: Created profile pictures");
                    },
                    executor));

            // Federation -> Lending -> Boards (shared)
            var federationFuture = CompletableFuture.supplyAsync(
                    () -> federationSeeder.seed(station.id(), adminMember.id()), executor);
            tasks.add(federationFuture.thenAcceptAsync(
                    federationResult -> {
                        log.info("Demo: Created federation data");
                        lendingSeeder.seed(
                                station.id(),
                                federationResult.partnerStationId(),
                                adminMember.id(),
                                federationResult.partnerMemberId());
                        log.info("Demo: Created lending data");
                        boardSeeder.seedSharedBoard(
                                station.id(),
                                federationResult.partnerStationId(),
                                adminMember,
                                members.betreuer(),
                                "TEAM",
                                "MEMBER",
                                new Random(42_005));
                        log.info("Demo: Created shared board data");
                    },
                    executor));

            // Boards (regular -- no federation dependency)
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        boardSeeder.seed(
                                station.id(), adminMember, members.betreuer(), "TEAM", "MEMBER", new Random(42_004));
                        log.info("Demo: Created board data");
                    },
                    executor));

            // Event Templates
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        eventSeeder.seedTemplates(station.id());
                        log.info("Demo: Created event templates");
                    },
                    executor));

            // Feed Token + Settings + Public KB
            tasks.add(CompletableFuture.runAsync(
                    () -> {
                        feedTokenService.getOrCreate(adminMember.id());
                        stationRepository.updatePublicKbMode(station.id(), PublicKbMode.ALLOW_ALL);
                        applicationSettingRepository.setBoolean("station_registration_enabled", false);
                    },
                    executor));

            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        }

        log.info("Demo: Created all user accounts (password: '{}')", PASSWORD);
        log.info("Demo: Admin login: admin@ember.local / {}", PASSWORD);
    }

    private void seedExchanges(
            int stationId,
            Random rng,
            List<StationMember> anfaengerMembers,
            List<StationMember> fortgeschrittenMembers,
            List<StationMember> betreuerMembers) {
        var allKids = new ArrayList<>(anfaengerMembers);
        allKids.addAll(fortgeschrittenMembers);
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
        for (var kid : allKids) {
            if (rng.nextInt(5) == 0) continue;
            var memberItems = inventoryRepository.findItemsByMember(kid.id());
            if (memberItems.isEmpty()) continue;
            var item = memberItems.get(rng.nextInt(memberItems.size()));
            var reason = exchangeReasons.get(rng.nextInt(exchangeReasons.size()));
            Integer newSizeId = item.sizeId();
            var sizes = item.sizeId() != null
                    ? inventoryRepository.findSizes(item.inventoryId())
                    : List.<InventorySize>of();
            int currentIdx = -1;
            for (int si = 0; si < sizes.size(); si++) {
                if (sizes.get(si).id() == item.sizeId()) {
                    currentIdx = si;
                    break;
                }
            }
            switch (reason) {
                case "Zu klein geworden" -> {
                    if (currentIdx >= 0 && currentIdx < sizes.size() - 1) {
                        newSizeId = sizes.get(currentIdx + 1).id();
                    } else {
                        reason = "Beschädigt";
                    }
                }
                case "Beschädigt",
                        "Verschlissen",
                        "Riss im Material",
                        "Reißverschluss defekt",
                        "Verloren und brauche Ersatz" -> {}
                case "Falsche Größe erhalten" -> {
                    if (sizes.size() > 1 && currentIdx >= 0) {
                        int offset = rng.nextBoolean() && currentIdx > 0 ? -1 : 1;
                        int newIdx = Math.clamp(currentIdx + offset, 0, sizes.size() - 1);
                        if (newIdx == currentIdx) {
                            newIdx = currentIdx > 0 ? currentIdx - 1 : currentIdx + 1;
                        }
                        newSizeId = sizes.get(newIdx).id();
                    }
                }
                default -> {}
            }
            var exchange = exchangeService.create(
                    stationId,
                    kid.id(),
                    "Demo User",
                    item.id(),
                    item.inventoryId(),
                    item.sizeId(),
                    newSizeId,
                    reason,
                    null);
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
    }

    private void seedProcurements(
            int stationId, List<StationMember> anfaengerMembers, List<StationMember> fortgeschrittenMembers) {
        var inventories = inventoryRepository.findByStation(stationId);
        if (!inventories.isEmpty()) {
            var handschuheInv = inventories.stream()
                    .filter(i -> "Handschuhe".equals(i.name()))
                    .findFirst();
            if (handschuheInv.isPresent()) {
                var sizes = inventoryRepository.findSizes(handschuheInv.get().id());
                procurementService.create(
                        stationId,
                        handschuheInv.get().id(),
                        anfaengerMembers.get(2).id(),
                        sizes.isEmpty() ? null : sizes.get(2 % sizes.size()).id(),
                        "Handschuhe verloren");
            }
        }
        var sporttascheInv =
                inventories.stream().filter(i -> "Sporttasche".equals(i.name())).findFirst();
        if (sporttascheInv.isPresent()) {
            var allKids = new ArrayList<>(anfaengerMembers);
            allKids.addAll(fortgeschrittenMembers);
            for (var kid : allKids) {
                var items = inventoryRepository.findItemsByMember(kid.id());
                boolean hasSporttasche = items.stream()
                        .anyMatch(i -> i.inventoryId() == sporttascheInv.get().id());
                if (!hasSporttasche) {
                    procurementService.create(
                            stationId, sporttascheInv.get().id(), kid.id(), null, "Sporttasche fehlt");
                }
            }
        }
        log.info("Demo: Created procurements");
    }
}
