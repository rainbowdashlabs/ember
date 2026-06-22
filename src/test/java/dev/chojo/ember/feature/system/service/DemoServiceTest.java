/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Database;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ProcurementService;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.news.repository.NewsFederationRepository;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.page.repository.PageFileMetaRepository;
import dev.chojo.ember.feature.page.service.PageFileStorageService;
import dev.chojo.ember.feature.page.service.PageImageVariantService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
        var federationHttpClient = new FederationHttpClient(
                signingService, stationRepo, new RemoteUrlValidator(new Federation(), new Demo()));

        var eventService = new EventService(eventRepo, restrictionRepo, noOpBus);
        var newsService = new NewsService(newsRepo, restrictionRepo, noOpBus, stationMemberRepo, accountRepo);
        var inventoryService = new InventoryService(inventoryRepo);
        var exchangeService = new ExchangeService(exchangeRepo, inventoryRepo, inventoryService, noOpBus);
        var procurementService = new ProcurementService(procurementRepo, inventoryService, inventoryRepo, noOpBus);
        var eventTemplateService = new EventTemplateService(eventTemplateRepo);
        var feedTokenService = new FeedTokenService(feedTokenRepo);

        var memberSvc = new StationMemberService(stationMemberRepo, stationRepo, accountRepo, mock(AuthService.class));
        var commentService = new CommentService(eventCommentRepo, noOpBus, memberSvc, stationRepo);
        var kbStorageConfig = new Storage();
        var kbFileStorage = new KbFileStorageService(kbStorageConfig);
        var kbService = new KnowledgeBaseService(
                knowledgeBaseRepo,
                stationRepo,
                kbFileStorage,
                federationService,
                federationRepo,
                federationHttpClient,
                kbCommentRepo,
                eventFederationRepo,
                memberIdentityFactory,
                noOpBus,
                memberSvc,
                new PresentationCompressor(kbStorageConfig),
                new PdfCompressor(kbStorageConfig));
        var quizService = new QuizService(
                quizCatalogRepo,
                quizTestRepo,
                restrictionRepo,
                federationService,
                federationRepo,
                federationHttpClient,
                stationRepo);
        var protocolService = new TestProtocolService(
                testProtocolRepo, federationService, federationRepo, federationHttpClient, stationRepo);
        var imageService = new ImageService();
        var authService = mock(AuthService.class);
        var stationService =
                new StationService(stationRepo, stationMemberRepo, accountRepo, authService, federationService);

        var groupService =
                new MemberGroupService(memberGroupRepo, stationMemberRepo, userTagRepo, new DomainEventBus(Set.of()));
        var tagService = new UserTagService(userTagRepo, memberGroupRepo);
        var memberNameResolver = new MemberNameResolver(
                new StationMemberService(stationMemberRepo, stationRepo, accountRepo, mock(AuthService.class)),
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
                eventService,
                commentService,
                eventCommentRepo,
                memberNameResolver);
        var newsFederationService = new NewsFederationService(
                newsFederationRepo,
                federationService,
                federationRepo,
                federationHttpClient,
                stationRepo,
                newsService,
                stationMemberRepo,
                accountRepo,
                eventFederationRepo,
                memberNameResolver);
        var lendingService = new LendingService(
                lendingRepo, federationHttpClient, federationService, stationRepo, inventoryRepo, noOpBus);
        var federatedBoardService = new FederatedBoardService(federatedBoardRepo);

        // Services consumed by DemoService for the post-seed notification showcase (read-only
        // lookups for one entity of each type). We mock NotificationService since the demo's
        // read paths don't depend on its behavior and constructing a real one would drag in
        // EmailService + Mailing config that aren't relevant here.
        var notificationServiceMock = mock(NotificationService.class);
        var lostAndFoundService = new LostAndFoundService(lostAndFoundRepo, notificationServiceMock);
        var boardService = new BoardService(boardRepo, memberSvc, groupService, tagService);
        var boardTicketService = new BoardTicketService(
                boardTicketRepo, boardRepo, noOpBus, memberSvc, memberIdentityFactory, memberNameResolver);
        var procedureService = new ProcedureService(procedureRepo, noOpBus);

        // -- Seeders --
        var memberSeeder = new DemoMemberSeeder(
                accountRepo, stationMemberRepo, memberGroupRepo, profileFieldRepo, profileFieldChangeRepo, userTagRepo);
        var eventSeeder =
                new DemoEventSeeder(eventRepo, eventFieldRepo, attendanceRepo, eventService, eventTemplateService);
        var attendanceSeeder = new DemoAttendanceSeeder(attendanceRepo);
        var inventorySeeder = new DemoInventorySeeder(inventoryRepo, inventoryCheckRepo, accountRepo);
        var formSeeder = new DemoFormSeeder(formRepo, restrictionRepo);
        var notificationSeeder = new DemoNotificationSeeder(notificationRepo);
        var waitingListSeeder = new DemoWaitingListSeeder(
                waitingListRepo, memberGroupRepo, stationMemberRepo, attendanceRepo, accountRepo);
        var quizSeeder = new DemoQuizSeeder(quizCatalogRepo, quizTestRepo, quizService, imageService);
        var kbSeeder = new DemoKnowledgeBaseSeeder(kbService, knowledgeBaseRepo);
        var protocolSeeder = new DemoProtocolSeeder(testProtocolRepo);
        var mediaSeeder = new DemoMediaSeeder(imageService, stationService, accountRepo);
        var federationSeeder = new DemoFederationSeeder(
                stationRepo,
                federationService,
                kbService,
                quizService,
                protocolService,
                eventService,
                eventFederationService,
                eventFederationRepo,
                accountRepo,
                stationMemberRepo,
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
        var demoStorage = new PageFileStorageService(stationRepo);
        var pageSeeder = new DemoPageSeeder(
                new PageService(
                        pageRepo,
                        new PageFileMetaRepository(),
                        demoStorage,
                        new PageImageVariantService(demoStorage, demoStorageConfig),
                        new StorageQuotaService(storageUsageRepo, demoStorageConfig, noOpBus),
                        stationMemberRepo,
                        new ImageService()),
                formRepo,
                quizCatalogRepo);
        var newsSeeder = new DemoNewsSeeder(newsService, stationMemberRepo);
        var lostAndFoundSeederLocal = new DemoLostAndFoundSeeder(lostAndFoundService);

        // -- DemoService --
        demoService = new DemoService(
                demoConfig,
                databaseConfig,
                dataSource,
                accountRepo,
                stationRepo,
                stationMemberRepo,
                inventoryRepo,
                exchangeRepo,
                passwordHasher,
                memberSeeder,
                eventSeeder,
                formSeeder,
                notificationSeeder,
                attendanceSeeder,
                inventorySeeder,
                waitingListSeeder,
                quizSeeder,
                mediaSeeder,
                kbSeeder,
                protocolSeeder,
                federationSeeder,
                lendingSeeder,
                boardSeeder,
                procedureSeeder,
                pageSeeder,
                newsSeeder,
                lostAndFoundSeederLocal,
                applicationSettingRepo,
                exchangeService,
                procurementService,
                feedTokenService,
                boardService,
                boardTicketService,
                procedureService,
                lendingService);
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
}
