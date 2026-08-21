/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.core.configuration.DatabaseConfig;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.postgresql.mapper.PostgresqlMapper;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import dev.chojo.ember.TestContainers;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.board.repository.BoardRepository;
import dev.chojo.ember.feature.board.repository.BoardTicketRepository;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import dev.chojo.ember.feature.checklist.repository.ChecklistRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterApplicationRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.service.ClusterGovernanceService;
import dev.chojo.ember.feature.cluster.service.ClusterMemberService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.comment.repository.EventCommentRepository;
import dev.chojo.ember.feature.comment.repository.NoteRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryBlocklistRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPingRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.events.repository.EventCategoryRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventFieldDefaultRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventReminderRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.EventBreakService;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldDefaultService;
import dev.chojo.ember.feature.events.service.EventOccurrenceService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventReminderService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.feed.repository.FeedMetricsRepository;
import dev.chojo.ember.feature.feed.repository.FeedTokenRepository;
import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.insights.repository.PageHitRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryContainerKindRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryContainerRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryFieldDefinitionRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ItemMovementRepository;
import dev.chojo.ember.feature.inventory.repository.MovementFlowRepository;
import dev.chojo.ember.feature.inventory.repository.ProcurementRepository;
import dev.chojo.ember.feature.inventory.service.ClusterItemReleaseService;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.inventory.service.ItemCustodyService;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.inventory.service.MovementFlowService;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.lostandfound.repository.LostAndFoundRepository;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.RegistrationCodeRepository;
import dev.chojo.ember.feature.members.repository.SavedFilterRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserSettingsRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.news.repository.NewsRepository;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.notifications.repository.NotificationSettingsRepository;
import dev.chojo.ember.feature.page.repository.PageRepository;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import dev.chojo.ember.feature.protocol.repository.TestProtocolRepository;
import dev.chojo.ember.feature.quiz.repository.AiProviderRepository;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import dev.chojo.ember.feature.restriction.repository.RestrictionRepository;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.station.repository.StationApplicationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StorageBackendAuditRepository;
import dev.chojo.ember.feature.storage.repository.StorageQuotaPresetRepository;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import dev.chojo.ember.feature.traffic.repository.StationTrafficRepository;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.waitinglist.repository.WaitingListRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

@Tag("database")
public abstract class RepositoryTestBase {
    private static final AtomicInteger SCHEMA_COUNTER = new AtomicInteger(0);

    /**
     * A single PostgreSQL container per JVM (Gradle test fork), started lazily on the first test
     * class's {@link #setupDatabase()} and shared by every repository test class in that fork; each
     * class isolates its data in its own schema. Sharing one container - instead of letting the
     * {@code @Testcontainers} lifecycle start and stop one per test class - removes the container
     * start/stop churn under parallel forks that let rootless Docker occasionally hand two
     * concurrently-starting containers the same host port. {@code withStartupAttempts} self-heals
     * the rare remaining collision, and the container is reaped when the fork's JVM exits.
     *
     * <p>Startup is deliberately kept out of a static initialiser: a transient Docker failure there
     * would poison this class for the whole fork ({@code NoClassDefFoundError} on every later
     * class). From {@code @BeforeAll} a failure fails only the current class and the next one
     * retries the start.
     */
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("ember_test")
            .withUsername("test")
            .withPassword("test")
            .withStartupAttempts(8);

    protected static AccountRepository accountRepo;
    protected static StationRepository stationRepo;
    protected static StationMemberRepository stationMemberRepo;
    protected static AttendanceRepository attendanceRepo;
    protected static InventoryRepository inventoryRepo;
    protected static ClusterRepository clusterRepo;

    protected static ClusterApplicationRepository clusterApplicationRepo;

    /** Shared, because its dependency list grows with every step and no test cares about it. */
    protected static ClusterService clusterService;

    protected static ClusterGovernanceService clusterGovernanceService;

    protected static ClusterMemberService clusterMemberService;

    protected static ItemCustodyService itemCustodyService;
    protected static MovementFlowRepository movementFlowRepo;
    protected static ItemMovementRepository itemMovementRepo;
    protected static MovementFlowService movementFlowService;
    protected static ItemMovementService itemMovementService;

    protected static ClusterItemReleaseService clusterItemReleaseService;
    protected static ExchangeService exchangeService;
    protected static MemberGroupRepository memberGroupRepo;
    protected static ProfileFieldRepository profileFieldRepo;
    protected static RegistrationCodeRepository registrationCodeRepo;
    protected static EventRepository eventRepo;
    protected static EventBreakRepository eventBreakRepo;
    protected static EventCategoryRepository eventCategoryRepo;
    protected static EventFieldDefaultRepository eventFieldDefaultRepo;
    protected static EventRegistrationRepository eventRegistrationRepo;
    protected static EventReminderRepository eventReminderRepo;
    protected static SavedFilterRepository savedFilterRepo;
    protected static InventoryCheckRepository inventoryCheckRepo;
    protected static EventFieldRepository eventFieldRepo;
    protected static FormRepository formRepo;
    protected static ProcurementRepository procurementRepo;
    protected static InventoryContainerRepository containerRepo;
    protected static InventoryContainerKindRepository containerKindRepo;
    protected static InventoryFieldDefinitionRepository fieldDefinitionRepo;
    protected static LostAndFoundRepository lostAndFoundRepo;
    protected static EmailQueueRepository emailQueueRepo;
    protected static ProfileFieldChangeRepository profileFieldChangeRepo;
    protected static UserSettingsRepository userSettingsRepo;
    protected static UserTagRepository userTagRepo;
    protected static NewsRepository newsRepo;
    protected static NotificationRepository notificationRepo;
    protected static NotificationSettingsRepository notificationSettingsRepo;
    protected static StationApplicationRepository stationApplicationRepo;
    protected static StationMailProviderRepository stationMailProviderRepo;
    protected static WaitingListRepository waitingListRepo;
    protected static EventCommentRepository eventCommentRepo;
    protected static NoteRepository noteRepo;
    protected static FeedTokenRepository feedTokenRepo;
    protected static FeedMetricsRepository feedMetricsRepo;
    protected static QuizCatalogRepository quizCatalogRepo;
    protected static QuizTestRepository quizTestRepo;
    protected static AiProviderRepository aiProviderRepo;
    protected static TestProtocolRepository testProtocolRepo;
    protected static KnowledgeBaseRepository knowledgeBaseRepo;
    protected static RestrictionRepository restrictionRepo;
    protected static RestrictionService restrictionService;
    protected static ApplicationSettingRepository applicationSettingRepo;
    protected static ProblemReportRepository problemReportRepo;
    protected static BoardRepository boardRepo;
    protected static BoardTicketRepository boardTicketRepo;
    protected static FederatedBoardRepository federatedBoardRepo;
    protected static ChecklistRepository checklistRepo;
    protected static ProcedureRepository procedureRepo;
    protected static PageRepository pageRepo;
    protected static StorageUsageRepository storageUsageRepo;
    protected static StorageQuotaPresetRepository storagePresetRepo;
    protected static StorageBackendAuditRepository storageBackendAuditRepo;
    protected static DiscoveryPeerRepository discoveryPeerRepo;
    protected static DiscoveryPingRepository discoveryPingRepo;
    protected static DiscoveryStationCacheRepository discoveryStationCacheRepo;
    protected static DiscoveryBlocklistRepository discoveryBlocklistRepo;
    protected static StationTrafficRepository stationTrafficRepo;
    protected static PageHitRepository pageHitRepo;
    protected static TwoFactorRepository twoFactorRepo;
    protected static MemberIdentityFactory memberIdentityFactory;
    protected static MemberNameResolver memberNameResolver;
    protected static MemberLookupService memberLookupService;
    protected static DataSource dataSource;
    protected static String schemaName;

    @BeforeAll
    static void setupDatabase() throws Exception {
        TestContainers.startExclusively(PG);
        String SCHEMA = "ember_t" + SCHEMA_COUNTER.incrementAndGet();
        DatabaseConfig dbConfig = new DatabaseConfig() {
            @Override
            public String host() {
                return PG.getHost();
            }

            @Override
            public String port() {
                return String.valueOf(PG.getFirstMappedPort());
            }

            @Override
            public String user() {
                return PG.getUsername();
            }

            @Override
            public String password() {
                return PG.getPassword();
            }

            @Override
            public String database() {
                return PG.getDatabaseName();
            }
        };

        dataSource = DataSourceCreator.create(PostgreSql.get())
                .configure(config ->
                        config.withConfig(dbConfig).currentSchema(SCHEMA).applicationName("EmberTest"))
                .create()
                .withMaximumPoolSize(5)
                .build();
        schemaName = SCHEMA;

        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setReplacements(new QueryReplacement("ember_schema", SCHEMA))
                .setSchemas(SCHEMA)
                .execute();

        var config = QueryConfiguration.builder(dataSource)
                .setThrowExceptions(true)
                .setRowMapperRegistry(new RowMapperRegistry().register(PostgresqlMapper.getDefaultMapper()))
                .build();
        QueryConfiguration.setDefault(config);
        accountRepo = new AccountRepository(TokenHasher.forTesting("repository-test-pepper"));
        stationRepo = new StationRepository();
        stationMemberRepo = new StationMemberRepository();
        memberLookupService = new MemberLookupService(stationMemberRepo, stationRepo);
        attendanceRepo = new AttendanceRepository();
        inventoryRepo = new InventoryRepository();
        clusterRepo = new ClusterRepository();
        clusterApplicationRepo = new ClusterApplicationRepository();
        itemCustodyService = new ItemCustodyService(inventoryRepo);
        movementFlowRepo = new MovementFlowRepository();
        itemMovementRepo = new ItemMovementRepository();
        movementFlowService = new MovementFlowService(movementFlowRepo, itemMovementRepo);
        itemMovementService = new ItemMovementService(
                itemMovementRepo, movementFlowService, inventoryRepo, itemCustodyService, new DomainEventBus(Set.of()));
        clusterItemReleaseService =
                new ClusterItemReleaseService(inventoryRepo, itemCustodyService, itemMovementService);
        clusterGovernanceService = new ClusterGovernanceService(
                clusterRepo,
                stationRepo,
                new ClusterStorageConfigRepository(),
                new StorageBackendResolver(new LocalStorageBackend()),
                new DomainEventBus(Set.of()));
        clusterService = new ClusterService(
                clusterRepo,
                stationRepo,
                clusterItemReleaseService,
                new FederationService(new FederationRepository(), stationRepo, new Api()),
                clusterGovernanceService,
                new DomainEventBus(Set.of()));
        clusterMemberService = new ClusterMemberService(clusterRepo, clusterService, new DomainEventBus(Set.of()));
        exchangeService = new ExchangeService(itemMovementService, inventoryRepo);
        memberGroupRepo = new MemberGroupRepository();
        profileFieldRepo = new ProfileFieldRepository();
        registrationCodeRepo = new RegistrationCodeRepository();
        eventRepo = new EventRepository();
        eventBreakRepo = new EventBreakRepository();
        eventCategoryRepo = new EventCategoryRepository();
        eventFieldDefaultRepo = new EventFieldDefaultRepository();
        eventRegistrationRepo = new EventRegistrationRepository();
        eventReminderRepo = new EventReminderRepository();
        savedFilterRepo = new SavedFilterRepository();
        inventoryCheckRepo = new InventoryCheckRepository();
        eventFieldRepo = new EventFieldRepository();
        formRepo = new FormRepository();
        procurementRepo = new ProcurementRepository();
        containerRepo = new InventoryContainerRepository();
        containerKindRepo = new InventoryContainerKindRepository();
        fieldDefinitionRepo = new InventoryFieldDefinitionRepository();
        lostAndFoundRepo = new LostAndFoundRepository();
        emailQueueRepo = new EmailQueueRepository();
        profileFieldChangeRepo = new ProfileFieldChangeRepository();
        userSettingsRepo = new UserSettingsRepository();
        userTagRepo = new UserTagRepository();
        newsRepo = new NewsRepository();
        notificationRepo = new NotificationRepository();
        notificationSettingsRepo = new NotificationSettingsRepository();
        stationApplicationRepo = new StationApplicationRepository();
        stationMailProviderRepo = new StationMailProviderRepository();
        waitingListRepo = new WaitingListRepository();
        eventCommentRepo = new EventCommentRepository();
        noteRepo = new NoteRepository();
        feedTokenRepo = new FeedTokenRepository();
        feedMetricsRepo = new FeedMetricsRepository();
        quizCatalogRepo = new QuizCatalogRepository();
        quizTestRepo = new QuizTestRepository();
        aiProviderRepo = new AiProviderRepository();
        testProtocolRepo = new TestProtocolRepository();
        knowledgeBaseRepo = new KnowledgeBaseRepository();
        restrictionRepo = new RestrictionRepository();
        restrictionService = new RestrictionService(restrictionRepo, stationMemberRepo, memberGroupRepo, userTagRepo);
        applicationSettingRepo = new ApplicationSettingRepository();
        problemReportRepo = new ProblemReportRepository();
        boardRepo = new BoardRepository();
        boardTicketRepo = new BoardTicketRepository();
        federatedBoardRepo = new FederatedBoardRepository();
        checklistRepo = new ChecklistRepository();
        procedureRepo = new ProcedureRepository();
        pageRepo = new PageRepository();
        storageUsageRepo = new StorageUsageRepository();
        storagePresetRepo = new StorageQuotaPresetRepository();
        storageBackendAuditRepo = new StorageBackendAuditRepository();
        discoveryPeerRepo = new DiscoveryPeerRepository();
        discoveryPingRepo = new DiscoveryPingRepository();
        discoveryStationCacheRepo = new DiscoveryStationCacheRepository();
        discoveryBlocklistRepo = new DiscoveryBlocklistRepository();
        stationTrafficRepo = new StationTrafficRepository();
        pageHitRepo = new PageHitRepository();
        twoFactorRepo = new TwoFactorRepository();
        var eventFedRepo = new EventFederationRepository();
        var fedRepo = new FederationRepository();
        var memberSvc = newStationMemberService(accountRepo, null);
        var groupSvc =
                new MemberGroupService(memberGroupRepo, stationMemberRepo, userTagRepo, new DomainEventBus(Set.of()));
        var tagSvc = new UserTagService(userTagRepo, memberGroupRepo);
        memberNameResolver =
                new MemberNameResolver(memberSvc, accountRepo, eventFedRepo, fedRepo, stationRepo, groupSvc, tagSvc);
        memberIdentityFactory = new MemberIdentityFactory(stationRepo, memberLookupService, memberNameResolver);
    }

    /**
     * Builds a {@link StationMemberService} over the shared repositories and lookup service, so no
     * test has to repeat the fixed half of its constructor argument list.
     */
    protected static StationMemberService newStationMemberService(
            AccountRepository accountRepository, AuthService authService) {
        return new StationMemberService(
                stationMemberRepo, stationRepo, accountRepository, authService, memberLookupService);
    }

    /**
     * The event domain's services, wired against this class's repositories. Tests take the one they
     * exercise instead of repeating the construction, which differs per service.
     */
    protected record EventServices(
            EventCrudService crud,
            EventOccurrenceService occurrence,
            EventCategoryService category,
            EventBreakService breaks,
            EventRestrictionService restriction,
            EventFieldDefaultService fieldDefault,
            EventRegistrationService registration,
            EventReminderService reminder) {}

    /**
     * Builds the event domain's services over the shared repositories.
     */
    protected static EventServices newEventServices(DomainEventBus eventBus) {
        var crudService = new EventCrudService(eventRepo, eventBus);
        var breakService = new EventBreakService(eventBreakRepo);
        return new EventServices(
                crudService,
                new EventOccurrenceService(crudService, breakService),
                new EventCategoryService(eventCategoryRepo),
                breakService,
                new EventRestrictionService(eventRepo, restrictionService),
                new EventFieldDefaultService(eventFieldDefaultRepo, eventRepo),
                new EventRegistrationService(eventRegistrationRepo, eventRepo, eventBus),
                new EventReminderService(eventReminderRepo));
    }

    /**
     * Closes this class's connection pool so its connections are returned to the shared container.
     * Without it, every test class would leak a pool against the one Postgres instance and exhaust
     * its connection limit once enough classes have run in a single fork.
     */
    @AfterAll
    static void teardownDatabase() throws Exception {
        if (dataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
