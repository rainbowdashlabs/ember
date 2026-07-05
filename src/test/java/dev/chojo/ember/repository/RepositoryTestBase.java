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
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.board.repository.BoardRepository;
import dev.chojo.ember.feature.board.repository.BoardTicketRepository;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import dev.chojo.ember.feature.checklist.repository.ChecklistRepository;
import dev.chojo.ember.feature.comment.repository.EventCommentRepository;
import dev.chojo.ember.feature.comment.repository.NoteRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryBlocklistRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPingRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.feed.repository.FeedMetricsRepository;
import dev.chojo.ember.feature.feed.repository.FeedTokenRepository;
import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.insights.repository.PageHitRepository;
import dev.chojo.ember.feature.inventory.repository.ExchangeRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryContainerKindRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryContainerRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryFieldDefinitionRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ProcurementRepository;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.lostandfound.repository.LostAndFoundRepository;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.RegistrationCodeRepository;
import dev.chojo.ember.feature.members.repository.SavedFilterRepository;
import dev.chojo.ember.feature.members.repository.StationMemberInviteRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserSettingsRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
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
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.station.repository.StationApplicationRepository;
import dev.chojo.ember.feature.station.repository.StationMailConfigRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
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
     * class isolates its data in its own schema. Sharing one container — instead of letting the
     * {@code @Testcontainers} lifecycle start and stop one per test class — removes the container
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
            .withStartupAttempts(4);

    protected static AccountRepository accountRepo;
    protected static StationRepository stationRepo;
    protected static StationMemberRepository stationMemberRepo;
    protected static AttendanceRepository attendanceRepo;
    protected static InventoryRepository inventoryRepo;
    protected static MemberGroupRepository memberGroupRepo;
    protected static ProfileFieldRepository profileFieldRepo;
    protected static RegistrationCodeRepository registrationCodeRepo;
    protected static EventRepository eventRepo;
    protected static SavedFilterRepository savedFilterRepo;
    protected static InventoryCheckRepository inventoryCheckRepo;
    protected static EventFieldRepository eventFieldRepo;
    protected static FormRepository formRepo;
    protected static ExchangeRepository exchangeRepo;
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
    protected static StationMailConfigRepository stationMailConfigRepo;
    protected static StationMemberInviteRepository stationMemberInviteRepo;
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
    protected static DataSource dataSource;
    protected static String schemaName;

    @BeforeAll
    static void setupDatabase() throws Exception {
        if (!PG.isRunning()) {
            PG.start();
        }
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
        stationMemberRepo = new StationMemberRepository(stationRepo);
        attendanceRepo = new AttendanceRepository();
        inventoryRepo = new InventoryRepository();
        memberGroupRepo = new MemberGroupRepository();
        profileFieldRepo = new ProfileFieldRepository();
        registrationCodeRepo = new RegistrationCodeRepository();
        eventRepo = new EventRepository();
        savedFilterRepo = new SavedFilterRepository();
        inventoryCheckRepo = new InventoryCheckRepository();
        eventFieldRepo = new EventFieldRepository();
        formRepo = new FormRepository();
        exchangeRepo = new ExchangeRepository();
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
        stationMailConfigRepo = new StationMailConfigRepository();
        stationMemberInviteRepo = new StationMemberInviteRepository();
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
        restrictionRepo = new RestrictionRepository(stationMemberRepo, memberGroupRepo, userTagRepo);
        applicationSettingRepo = new ApplicationSettingRepository();
        problemReportRepo = new ProblemReportRepository();
        boardRepo = new BoardRepository();
        boardTicketRepo = new BoardTicketRepository(stationMemberRepo, stationRepo);
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
        var memberSvc = new StationMemberService(stationMemberRepo, stationRepo, accountRepo, null);
        var groupSvc =
                new MemberGroupService(memberGroupRepo, stationMemberRepo, userTagRepo, new DomainEventBus(Set.of()));
        var tagSvc = new UserTagService(userTagRepo, memberGroupRepo);
        memberNameResolver =
                new MemberNameResolver(memberSvc, accountRepo, eventFedRepo, fedRepo, stationRepo, groupSvc, tagSvc);
        memberIdentityFactory = new MemberIdentityFactory(stationRepo, stationMemberRepo, memberNameResolver);
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
