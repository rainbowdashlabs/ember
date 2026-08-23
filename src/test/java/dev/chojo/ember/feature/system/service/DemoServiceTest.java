/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Database;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.board.service.BoardAttachmentService;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.checklist.repository.ChecklistRepository;
import dev.chojo.ember.feature.checklist.service.ChecklistService;
import dev.chojo.ember.feature.cluster.service.ClusterApplicationService;
import dev.chojo.ember.feature.cluster.service.ClusterAutoShareService;
import dev.chojo.ember.feature.cluster.service.ClusterContentService;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.FederationContractRefreshService;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryFieldDefinitionService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ProcurementService;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbCommentService;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.knowledgebase.service.KbLinkMetadataService;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfExportService;
import dev.chojo.ember.feature.knowledgebase.service.KbPresentationService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.knowledgebase.service.TextCompressionPolicy;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.media.MediaTestSupport;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.MediaReferenceRegistry;
import dev.chojo.ember.feature.media.service.MediaStorageService;
import dev.chojo.ember.feature.media.service.MediaVariantService;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.news.repository.NewsAttachmentRepository;
import dev.chojo.ember.feature.news.repository.NewsFederationRepository;
import dev.chojo.ember.feature.news.service.NewsAttachmentService;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.quiz.service.QuizAnswerGrader;
import dev.chojo.ember.feature.quiz.service.QuizAttemptService;
import dev.chojo.ember.feature.quiz.service.QuizCatalogService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionImageService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionSelector;
import dev.chojo.ember.feature.quiz.service.QuizQuestionService;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.quiz.service.QuizTestService;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoServiceTest extends RepositoryTestBase {

    private static DemoService demoService;

    @BeforeAll
    static void setup() {
        var noOpBus = new DomainEventBus(Set.of());
        var passwordHasher = new PasswordHasher();
        var demoConfig = new Demo();
        var apiConfig = new Api();
        var databaseConfig = new Database();

        // -- Repositories not in RepositoryTestBase --
        var federationRepo = new FederationRepository();
        var eventFederationRepo = new EventFederationRepository();
        var eventTemplateRepo = new EventTemplateRepository();
        var kbCommentRepo = new KbCommentRepository();
        var newsFederationRepo = new NewsFederationRepository();
        var lendingRepo = new LendingRepository();

        // -- Services --
        var federationService = new FederationService(federationRepo, stationRepo, apiConfig);
        var signingService = new FederationSigningService();
        var contractRefreshRef = new AtomicReference<FederationContractRefreshService>();
        var federationHttpClient = new FederationHttpClient(
                signingService,
                stationRepo,
                new RemoteUrlValidator(new Federation(), new Demo()),
                contractRefreshRef::get);
        contractRefreshRef.set(new FederationContractRefreshService(federationRepo, stationRepo, federationHttpClient));
        var federationFanout = new FederationFanout();
        var federationEntityResolver = new FederationEntityResolver(federationRepo, stationRepo, federationHttpClient);

        var eventServices = newEventServices(noOpBus);
        var newsService = new NewsService(
                newsRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                restrictionService,
                noOpBus,
                stationMemberRepo,
                memberLookupService,
                accountRepo);
        var inventoryService = new InventoryService(inventoryRepo, itemCustodyService, clusterRepo);
        var exchangeService = new ExchangeService(itemMovementService, inventoryRepo);
        var procurementService = new ProcurementService(
                procurementRepo, inventoryService, inventoryRepo, clusterRepo, itemCustodyService, noOpBus);
        var eventTemplateService = new EventTemplateService(eventTemplateRepo);
        var feedTokenService = new FeedTokenService(feedTokenRepo);

        var memberSvc = newStationMemberService(accountRepo, mock(AuthService.class));
        var commentService = new CommentService(eventCommentRepo, noOpBus, memberSvc, stationRepo);
        var kbStorageConfig = new Storage();
        var kbBackend = new LocalStorageBackend();
        var kbResolver = new StorageBackendResolver(kbBackend);
        var kbStorageSvc = new StorageService(kbResolver, kbBackend);
        var kbCompression = new TextCompressionPolicy(kbStorageConfig);
        var kbFileStorage = new KbFileStorageService(kbStorageSvc, stationRepo, kbBackend, kbCompression);
        var kbSearchService = new KbSearchService(knowledgeBaseRepo, stationRepo);
        var kbContentService = new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                kbFileStorage,
                kbSearchService);
        var kbCommentService =
                new KbCommentService(knowledgeBaseRepo, kbCommentRepo, memberIdentityFactory, memberSvc, noOpBus);
        var kbService = new KnowledgeBaseService(
                knowledgeBaseRepo,
                kbFileStorage,
                kbContentService,
                new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo),
                new KbPresentationService(knowledgeBaseRepo, kbFileStorage, kbContentService),
                new KbLinkMetadataService(),
                new PresentationCompressor(kbStorageConfig),
                new PdfCompressor(kbStorageConfig),
                new ClusterAutoShareService(clusterRepo, new FederationRepository()));
        var kbFederationService = new KnowledgeBaseFederationService(
                kbService,
                kbContentService,
                kbSearchService,
                federationService,
                federationRepo,
                federationHttpClient,
                stationRepo,
                kbCommentRepo,
                eventFederationRepo,
                memberNameResolver,
                federationFanout,
                federationEntityResolver,
                mock(KbPdfExportService.class));
        var quizQuestionService = new QuizQuestionService(quizCatalogRepo);
        var quizService = new QuizService(
                new QuizCatalogService(quizCatalogRepo),
                quizQuestionService,
                new QuizTestService(quizTestRepo, new QuizQuestionSelector(quizCatalogRepo, quizTestRepo)),
                new QuizAttemptService(quizTestRepo, quizQuestionService, new QuizAnswerGrader()));
        var protocolService = new TestProtocolService(
                testProtocolRepo,
                federationService,
                federationRepo,
                federationHttpClient,
                stationRepo,
                federationFanout,
                federationEntityResolver);
        var imageVariantStorage = new StorageService(new StorageBackendResolver(kbBackend), kbBackend);
        var imageVariantWriter = new ImageVariantService(imageVariantStorage);
        var avatarService = new AvatarService(imageVariantWriter);
        var quizImageService = new QuizQuestionImageService(imageVariantWriter, stationRepo);
        var authService = mock(AuthService.class);
        var stationService = new StationService(
                stationRepo,
                stationMemberRepo,
                accountRepo,
                federationService,
                new StationMemberInviteService(stationMemberRepo, memberGroupRepo, accountRepo, authService),
                clusterRepo);

        var groupService =
                new MemberGroupService(memberGroupRepo, stationMemberRepo, userTagRepo, new DomainEventBus(Set.of()));
        var tagService = new UserTagService(userTagRepo, memberGroupRepo);
        var memberNameResolver = new MemberNameResolver(
                newStationMemberService(accountRepo, mock(AuthService.class)),
                accountRepo,
                eventFederationRepo,
                federationRepo,
                stationRepo,
                groupService,
                tagService);
        var eventFederationService = new EventFederationService(
                eventFederationRepo,
                federationService,
                federationHttpClient,
                federationRepo,
                stationRepo,
                eventServices.crud(),
                commentService,
                eventCommentRepo,
                memberNameResolver,
                federationFanout,
                federationEntityResolver);
        var newsFederationService = new NewsFederationService(
                newsFederationRepo,
                federationService,
                federationRepo,
                federationHttpClient,
                stationRepo,
                newsService,
                new NewsAttachmentService(
                        new NewsAttachmentRepository(),
                        MediaTestSupport.library(
                                stationRepo, contentContainerRepo, mediaFileRepo, mediaMetaRepo, storageUsageRepo),
                        stationRepo,
                        new Api()),
                eventFederationRepo,
                memberNameResolver,
                federationFanout,
                federationEntityResolver);
        var lendingService = new LendingService(
                lendingRepo,
                federationHttpClient,
                federationService,
                stationRepo,
                inventoryRepo,
                itemCustodyService,
                noOpBus);
        var federatedBoardService = new FederatedBoardService(federatedBoardRepo);

        // Services consumed by DemoService for the post-seed notification showcase (read-only
        // lookups for one entity of each type). We mock NotificationService since the demo's
        // read paths don't depend on its behavior and constructing a real one would drag in
        // EmailService + Mailing config that aren't relevant here.
        var notificationServiceMock = mock(NotificationService.class);
        var lostAndFoundService = new LostAndFoundService(lostAndFoundRepo, notificationServiceMock);
        var boardService = new BoardService(boardRepo, memberSvc, groupService, tagService);
        var boardAttachmentSvc = new BoardAttachmentService(kbStorageSvc, stationRepo, kbBackend);
        var boardTicketService = new BoardTicketService(
                boardTicketRepo,
                boardRepo,
                noOpBus,
                memberSvc,
                memberIdentityFactory,
                memberNameResolver,
                boardAttachmentSvc);
        var procedureService = new ProcedureService(procedureRepo, noOpBus);

        // -- Seeders --
        var memberSeeder = new DemoMemberSeeder(
                accountRepo,
                stationMemberRepo,
                memberLookupService,
                memberGroupRepo,
                profileFieldRepo,
                profileFieldChangeRepo,
                userTagRepo,
                stationRepo);
        var eventSeeder = new DemoEventSeeder(
                eventCategoryRepo,
                eventRegistrationRepo,
                eventFieldRepo,
                attendanceRepo,
                eventServices.crud(),
                eventTemplateService,
                eventServices.restriction(),
                new EventRegistrationFieldService(new EventRegistrationFieldRepository()));
        var attendanceSeeder = new DemoAttendanceSeeder(attendanceRepo);
        var containerSvc =
                new InventoryContainerService(containerRepo, containerKindRepo, inventoryRepo, itemCustodyService);
        var fieldDefSvc = new InventoryFieldDefinitionService(fieldDefinitionRepo);
        var inventorySeeder = new DemoInventorySeeder(
                inventoryRepo,
                inventoryCheckRepo,
                accountRepo,
                containerSvc,
                fieldDefSvc,
                exchangeService,
                procurementService,
                itemCustodyService);
        var clusterSeeder = new DemoClusterSeeder(
                accountRepo,
                passwordHasher,
                clusterService,
                clusterMemberService,
                clusterInventoryService,
                clusterProfileFieldService,
                new ClusterContentService(clusterRepo, stationRepo, stationMemberRepo, kbService),
                new ClusterApplicationService(
                        clusterApplicationRepo, clusterRepo, stationRepo, clusterService, noOpBus),
                clusterStorageQuotaService,
                stationRepo,
                inventoryRepo,
                fieldDefSvc,
                itemCustodyService,
                itemMovementService,
                movementFlowService,
                newsService,
                eventServices.crud(),
                eventRegistrationRepo,
                notificationRepo);
        var formSeeder = new DemoFormSeeder(formRepo, restrictionService);
        var notificationSeeder = new DemoNotificationSeeder(
                notificationRepo, inventoryRepo, boardService, boardTicketService, procedureService, lendingService);
        var waitingListSeeder = new DemoWaitingListSeeder(
                waitingListRepo, memberGroupRepo, stationMemberRepo, attendanceRepo, accountRepo);
        var quizSeeder = new DemoQuizSeeder(quizCatalogRepo, quizTestRepo, quizService, quizImageService);
        var kbSeeder = new DemoKnowledgeBaseSeeder(kbService, kbContentService, knowledgeBaseRepo);
        var protocolSeeder = new DemoProtocolSeeder(testProtocolRepo);
        var mediaSeeder = new DemoMediaSeeder(
                avatarService,
                stationService,
                mock(dev.chojo.ember.feature.station.service.StationLogoService.class),
                accountRepo);
        var federationSeeder = new DemoFederationSeeder(
                stationRepo,
                federationService,
                kbService,
                kbCommentService,
                kbFederationService,
                quizService,
                protocolService,
                eventServices.crud(),
                eventServices.category(),
                eventFederationService,
                eventFederationRepo,
                accountRepo,
                stationMemberRepo,
                memberLookupService,
                passwordHasher,
                newsService,
                newsFederationService,
                commentService,
                memberIdentityFactory,
                demoConfig,
                apiConfig);
        var lendingSeeder = new DemoLendingSeeder(lendingService, inventoryRepo);
        var boardSeeder = new DemoBoardSeeder(
                boardRepo, boardTicketRepo, federatedBoardService, federationService, memberIdentityFactory);
        var procedureSeeder = new DemoProcedureSeeder(procedureRepo);
        var demoStorageConfig = new Storage();
        var demoBackend = new LocalStorageBackend();
        var demoResolver = new StorageBackendResolver(demoBackend);
        var demoStorageSvc = new StorageService(demoResolver, demoBackend);
        var demoStorage = new MediaStorageService(demoStorageSvc, stationRepo, demoBackend);
        var demoMediaLibrary = new MediaLibraryService(
                mediaFileRepo,
                mediaMetaRepo,
                demoStorage,
                new MediaVariantService(demoStorage, demoStorageConfig),
                new MediaReferenceRegistry(contentContainerRepo),
                new StorageQuotaService(storageUsageRepo, demoStorageConfig, noOpBus));
        var pageSeeder = new DemoPageSeeder(
                new PageService(
                        pageRepo,
                        new ContentBlockService(contentContainerRepo),
                        demoMediaLibrary,
                        stationMemberRepo,
                        avatarService),
                demoMediaLibrary,
                formRepo,
                quizCatalogRepo);
        var newsSeeder = new DemoNewsSeeder(newsService, stationMemberRepo);
        var lostAndFoundSeederLocal = new DemoLostAndFoundSeeder(lostAndFoundService);
        var checklistService =
                new ChecklistService(new ChecklistRepository(), stationMemberRepo, memberGroupRepo, userTagRepo);
        var checklistSeederLocal = new DemoChecklistSeeder(checklistService);
        var stationSeeder = new DemoStationSeeder(accountRepo, stationRepo);
        var mirrorStationSeeder = new DemoMirrorStationSeeder(
                stationRepo,
                stationMemberRepo,
                memberLookupService,
                accountRepo,
                federationService,
                demoConfig,
                apiConfig);
        var sessionSeeder = new DemoSessionSeeder(accountRepo);
        var settingsSeeder = new DemoSettingsSeeder(feedTokenService, stationRepo, applicationSettingRepo);
        var setupSeeder = new DemoSetupSeeder(stationRepo, accountRepo, stationMemberRepo);
        // A demo instance is what lets the TOTP service run without a configured encryption key,
        // which is the same reason the seeder only ever runs on one.
        var demoInstance = mock(Demo.class);
        when(demoInstance.dev()).thenReturn(true);
        var twoFactorSeeder = new DemoTwoFactorSeeder(
                new TwoFactorRepository(), new TotpService(new TwoFactorSettings(), demoInstance));

        // -- DemoService --
        demoService = new DemoService(
                demoConfig,
                databaseConfig,
                dataSource,
                passwordHasher,
                Set.of(
                        stationSeeder,
                        memberSeeder,
                        mirrorStationSeeder,
                        eventSeeder,
                        newsSeeder,
                        lostAndFoundSeederLocal,
                        attendanceSeeder,
                        inventorySeeder,
                        clusterSeeder,
                        formSeeder,
                        sessionSeeder,
                        waitingListSeeder,
                        quizSeeder,
                        kbSeeder,
                        protocolSeeder,
                        procedureSeeder,
                        mediaSeeder,
                        federationSeeder,
                        settingsSeeder,
                        checklistSeederLocal,
                        boardSeeder,
                        pageSeeder,
                        lendingSeeder,
                        notificationSeeder,
                        setupSeeder,
                        twoFactorSeeder),
                stationRepo,
                clusterRepo);
    }

    @Test
    @Order(1)
    void seedDataWithoutErrors() {
        demoService.resetAndSeed();
    }

    @Test
    @Order(2)
    void verifyAdminAccountCreated() {
        var admin = accountRepo.findByEmail("admin@ember.local");
        assertTrue(admin.isPresent(), "Admin account admin@ember.local should exist");
    }

    @Test
    @Order(3)
    void verifyStationCreated() {
        var stations = stationRepo.findAll();
        assertNotNull(stations);
        assertFalse(stations.isEmpty(), "At least one station should exist");
        assertTrue(
                stations.stream().anyMatch(s -> "Jugendfeuerwehr Musterstadt".equals(s.name())),
                "Station 'Jugendfeuerwehr Musterstadt' should exist");
    }

    @Test
    @Order(4)
    void verifyMembersCreated() {
        var stations = stationRepo.findAll();
        var station = stations.stream()
                .filter(s -> "Jugendfeuerwehr Musterstadt".equals(s.name()))
                .findFirst()
                .orElseThrow();
        var members = stationMemberRepo.findByStation(station.id());
        assertTrue(members.size() >= 10, "At least 10 members should exist, found: " + members.size());
    }

    @Test
    @Order(5)
    void verifyPartnerStationCreated() {
        var stations = stationRepo.findAll();
        assertTrue(
                stations.stream().anyMatch(s -> "JF Partnerwache".equals(s.name())),
                "Partner station 'JF Partnerwache' should exist");
    }

    /**
     * The cluster seeder skips a lot of itself when the pieces it builds on are missing, which is right at
     * run time and useless in a test: a silent skip and a working seeder look identical from outside. These
     * assertions name the things that only exist if it ran the whole way through.
     */
    @Test
    void verifyClusterSeeded() {
        var cluster = clusterRepo.findAll().stream()
                .filter(c -> "Kreisverband Musterstadt".equals(c.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The demo cluster should exist"));

        assertTrue(cluster.usesInventory(), "The demo cluster should keep gear of its own");
        // The station the demo is about, the neighbouring one, and the one the cluster made itself. The
        // federation partner and the mirror are the two that stay outside.
        assertEquals(3, clusterRepo.findStationIds(cluster.id()).size(), "Three stations should be in the cluster");
        assertFalse(
                clusterApplicationRepo.findByCluster(cluster.id()).isEmpty(),
                "A station should be waiting to join the cluster");
        assertEquals(
                2,
                clusterProfileFieldRepo.findByCluster(cluster.id()).size(),
                "The cluster should ask two questions of its members");

        // The room the cluster hands out, in all four places a station can get its numbers from: the pool,
        // the defaults, the two tiers, and the grants. A storage screen with none of them shows nothing.
        var room = clusterStorageQuotaService.findOverview(cluster.id());
        assertEquals(100L * 1024 * 1024 * 1024, room.poolBytes(), "The instance should have granted a pool");
        assertNotNull(room.defaults().quotaBytes(), "The cluster should say what a station it granted nothing gets");
        assertEquals(2, room.presets().size(), "The cluster should keep two tiers");
        assertEquals(
                41L * 1024 * 1024 * 1024,
                room.handedOut(),
                "Its own store and two of its stations should have been granted room");
        assertTrue(
                room.stations().stream().anyMatch(s -> s.ownStore() && s.granted().totalBytes() != null),
                "The cluster's own store should be granted room like any other station");

        var gear = inventoryRepo.findItemsOwnedByCluster(cluster.id());
        for (String code : List.of("KV-0001", "KV-0002", "KV-0003", "KV-0004", "KV-0005", "KV-0006")) {
            assertTrue(
                    gear.stream().anyMatch(item -> code.equals(item.internalId())),
                    "The cluster should own the piece of gear " + code);
        }
        assertTrue(
                gear.size() > 6,
                "The gear the demo station already kept for the body above it should have found its owner "
                        + "when the station joined");
        for (ItemCustody custody : List.of(
                ItemCustody.WITH_OWNER, ItemCustody.AT_STATION, ItemCustody.WITH_MEMBER, ItemCustody.IN_TRANSIT)) {
            assertTrue(
                    gear.stream().anyMatch(item -> item.custody() == custody),
                    "The cluster's gear should show a piece that is " + custody);
        }

        var admin = clusterRepo.findMembers(cluster.id()).stream()
                .filter(m -> m.userType() == ClusterUserType.CLUSTER_ADMIN)
                .findFirst()
                .orElseThrow(() -> new AssertionError("The cluster should have an administrator"));
        assertEquals(
                3,
                notificationRepo.findAllForClusterMember(admin.id()).size(),
                "The administrator should have been told about the cluster's own business");
    }
}
