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
import dev.chojo.ember.feature.account.service.AccountInviteService;
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
import dev.chojo.ember.feature.content.service.CellDescriptions;
import dev.chojo.ember.feature.equipment.repository.EquipmentAvailabilityRepository;
import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.equipment.service.EquipmentAvailabilityService;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.events.service.EventAttachmentService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.InventoryShareRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.FederationContractRefreshService;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.InventoryShareService;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryFieldDefinitionService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ProcurementService;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.knowledgebase.service.KbLinkMetadataService;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfExportService;
import dev.chojo.ember.feature.knowledgebase.service.KbPresentationService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.knowledgebase.service.KbTrashService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.knowledgebase.service.TextCompressionPolicy;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundImageService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.media.MediaTestSupport;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.MediaReferenceRegistry;
import dev.chojo.ember.feature.media.service.MediaStorageService;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.news.repository.NewsAttachmentRepository;
import dev.chojo.ember.feature.news.repository.NewsFederationRepository;
import dev.chojo.ember.feature.news.service.NewsAttachmentService;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.page.entity.PageVisibility;
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
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoServiceTest extends RepositoryTestBase {

    private static final ZoneId STATION_ZONE = ZoneId.of("Europe/Berlin");

    /**
     * Half past midnight at the demo stations, when the server's UTC clock still reads yesterday. The
     * whole demo is seeded at this moment, because it is the one where asking the server for today
     * puts today's appointments on the wrong day.
     */
    private static final Instant JUST_AFTER_MIDNIGHT =
            LocalDate.now(STATION_ZONE).atTime(0, 30).atZone(STATION_ZONE).toInstant();

    private static DemoService demoService;

    /**
     * Wires the demo service by hand. The two-factor seeder's TOTP service is told it runs on a demo
     * instance, which is what lets it work without a configured encryption key and the same reason the
     * seeder only ever runs on one.
     */
    @BeforeAll
    static void setup() {
        var demoClock = new DemoClock(Clock.fixed(JUST_AFTER_MIDNIGHT, ZoneOffset.UTC));
        var noOpBus = new DomainEventBus(Set.of());
        var passwordHasher = new PasswordHasher();
        var demoConfig = new Demo();
        var apiConfig = new Api();
        var databaseConfig = new Database();

        var federationRepo = new FederationRepository();
        var eventFederationRepo = new EventFederationRepository();
        var eventTemplateRepo = new EventTemplateRepository();
        var newsFederationRepo = new NewsFederationRepository();
        var lendingRepo = new LendingRepository();

        var federationService = new FederationService(federationRepo, stationRepo, TestStationKeys.store(), apiConfig);
        var contractRefreshRef = new AtomicReference<FederationContractRefreshService>();
        var federationHttpClient = new FederationHttpClient(
                TestStationKeys.signer(),
                stationRepo,
                new OutboundHttp(new RemoteUrlValidator(new Federation(), new Demo())),
                contractRefreshRef::get);
        contractRefreshRef.set(
                new FederationContractRefreshService(federationRepo, federationHttpClient, new TaskScheduler()));
        var federationFanout = new FederationFanout(new TaskScheduler());
        var federationEntityResolver = new FederationEntityResolver(federationRepo);
        var federationTransport = mock(FederationTransport.class);

        var eventServices = newEventServices(noOpBus);
        var newsService = newNewsService(noOpBus);
        var inventoryService = new InventoryService(
                inventoryRepo,
                artRepo,
                fieldDefinitionService,
                itemCustodyService,
                clusterRepo,
                clusterStationGroupRepo);
        var procurementService = new ProcurementService(
                procurementRepo,
                inventoryService,
                inventoryRepo,
                clusterRepo,
                itemCustodyService,
                itemMovementService,
                stationMemberRepo,
                accountRepo,
                noOpBus);
        var eventTemplateService = new EventTemplateService(eventTemplateRepo, attendanceRepo, memberEligibility);
        var feedTokenService = new FeedTokenService(feedTokenRepo);

        var memberSvc = newStationMemberService(accountRepo, mock(AuthService.class));
        var commentService = newCommentService(noOpBus);
        var kbStorageConfig = new Storage();
        var kbBackend = localStorage();
        var kbResolver = new StorageBackendResolver(kbBackend);
        var kbStorageSvc = new StorageService(kbResolver, kbBackend);
        var kbCompression = new TextCompressionPolicy(kbStorageConfig);
        var kbFileStorage = new KbFileStorageService(kbStorageSvc, stationRepo, kbBackend, kbCompression);
        var kbSearchService = new KbSearchService(knowledgeBaseRepo, stationRepo);
        var kbContentService = new KbContentService(
                knowledgeBaseRepo, contentBlocks(), noCellDescriptions(), stationRepo, kbFileStorage, kbSearchService);
        var kbService = new KnowledgeBaseService(
                knowledgeBaseRepo,
                kbFileStorage,
                kbContentService,
                new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo),
                new KbPresentationService(knowledgeBaseRepo, kbFileStorage, kbContentService, new TaskScheduler()),
                new KbLinkMetadataService(new OutboundHttp(new RemoteUrlValidator(new Federation(), new Demo()))),
                new PresentationCompressor(kbStorageConfig),
                new PdfCompressor(kbStorageConfig),
                new ClusterAutoShareService(clusterRepo, new FederationRepository()));
        var kbTrashService = new KbTrashService(
                knowledgeBaseRepo,
                kbFileStorage,
                kbContentService,
                kbSearchService,
                new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo),
                new KbAuthorNameService(stationMemberRepo, accountRepo),
                pageRepo);
        var kbFederationService = new KnowledgeBaseFederationService(
                kbService,
                kbContentService,
                kbSearchService,
                federationService,
                federationRepo,
                federationTransport,
                stationRepo,
                commentService,
                eventFederationRepo,
                memberNameResolver,
                federationFanout,
                federationEntityResolver,
                mock(KbPdfExportService.class),
                new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo));
        var quizQuestionService = new QuizQuestionService(quizCatalogRepo);
        var quizService = new QuizService(
                new QuizCatalogService(quizCatalogRepo),
                quizQuestionService,
                new QuizTestService(
                        quizTestRepo, new QuizQuestionSelector(quizCatalogRepo, quizTestRepo), restrictionService),
                new QuizAttemptService(quizTestRepo, quizQuestionService, new QuizAnswerGrader()));
        var protocolService = new TestProtocolService(
                testProtocolRepo,
                federationService,
                federationRepo,
                stationRepo,
                federationFanout,
                federationEntityResolver,
                federationTransport);
        var imageVariantStorage = new StorageService(new StorageBackendResolver(kbBackend), kbBackend);
        var imageVariantWriter = new ImageVariants(imageVariantStorage);
        var avatarService = new AvatarService(imageVariantWriter);
        var quizImageService = new QuizQuestionImageService(imageVariantWriter, stationRepo);
        var authService = mock(AuthService.class);
        var stationService = new StationService(
                stationRepo,
                stationMemberRepo,
                accountRepo,
                federationService,
                new StationMemberInviteService(
                        stationMemberRepo, newGroupMemberships(), new AccountInviteService(accountRepo, authService)),
                clusterRepo);

        var groupService = new MemberGroupService(memberGroupRepo, stationMemberRepo, userTagRepo);
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
                federationTransport,
                federationRepo,
                stationRepo,
                eventServices.crud(),
                commentService,
                memberNameResolver,
                federationFanout,
                federationEntityResolver,
                new EventAttachmentService(new EventAttachmentRepository(), mock(MediaLibraryService.class)),
                new EventFieldService(
                        eventFieldRepo,
                        stationMemberRepo,
                        memberEligibility,
                        eventRepo,
                        attendanceRepo,
                        eventFieldRegistrationService),
                occurrenceCalendar,
                mock(MediaLibraryService.class),
                new Api());
        var newsFederationService = new NewsFederationService(
                newsFederationRepo,
                federationService,
                federationRepo,
                stationRepo,
                newsService,
                commentService,
                new NewsAttachmentService(
                        new NewsAttachmentRepository(),
                        MediaTestSupport.library(
                                localStorage(),
                                stationRepo,
                                contentContainerRepo,
                                mediaFileRepo,
                                mediaMetaRepo,
                                storageUsageRepo),
                        stationRepo,
                        new Api()),
                eventFederationRepo,
                memberNameResolver,
                federationFanout,
                federationEntityResolver,
                federationTransport);
        var lendingService = new LendingService(
                lendingRepo,
                federationTransport,
                federationService,
                federationFanout,
                stationRepo,
                inventoryRepo,
                clusterRepo,
                itemCustodyService,
                borrowedGearService,
                new InventoryShareService(new InventoryShareRepository(), federationService, inventoryRepo, artRepo),
                artRepo,
                lineTargetService,
                new EquipmentAvailabilityService(
                        new EquipmentAvailabilityRepository(),
                        new EquipmentNeedRepository(),
                        eventRepo,
                        occurrenceCalendar),
                noOpBus);
        var federatedBoardService = new FederatedBoardService(federatedBoardRepo);

        var lostAndFoundService =
                new LostAndFoundService(lostAndFoundRepo, mock(Notifier.class), mock(LostAndFoundImageService.class));
        var boardService = new BoardService(boardRepo, memberSvc, groupService, tagService);
        var boardAttachmentSvc = new BoardAttachmentService(kbStorageSvc, stationRepo, kbBackend);
        var boardTicketService = new BoardTicketService(
                boardTicketRepo,
                boardRepo,
                boardService,
                noOpBus,
                memberSvc,
                memberIdentityFactory,
                memberNameResolver,
                boardAttachmentSvc);
        var procedureService = new ProcedureService(procedureRepo, noOpBus);

        var memberSeeder = new DemoMemberSeeder(
                accountRepo,
                stationMemberRepo,
                memberLookupService,
                memberGroupRepo,
                new MemberGroupSetRepository(),
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
                new EventRegistrationFieldService(new EventRegistrationFieldRepository(), memberEligibility),
                demoClock);
        var attendanceSeeder = new DemoAttendanceSeeder(attendanceRepo, stationMemberRepo, demoClock);
        var containerSvc =
                new InventoryContainerService(containerRepo, containerKindRepo, inventoryRepo, itemCustodyService);
        var fieldDefSvc = new InventoryFieldDefinitionService(fieldDefinitionRepo, artRepo, inventoryRepo);
        var inventorySeeder = new DemoInventorySeeder(
                inventoryRepo,
                artRepo,
                inventoryTagRepo,
                inventoryCheckRepo,
                accountRepo,
                containerSvc,
                fieldDefSvc,
                itemMovementService,
                procurementService,
                itemCustodyService);
        var clusterSeeder = new DemoClusterSeeder(
                accountRepo,
                passwordHasher,
                clusterService,
                clusterMemberService,
                clusterInventoryService,
                clusterProfileFieldService,
                clusterStationGroupService,
                new ClusterContentService(clusterRepo, stationRepo, stationMemberRepo, kbService, kbTrashService),
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
                newNotifier());
        var formSeeder = new DemoFormSeeder(formRepo, restrictionService);
        var notificationSeeder = new DemoNotificationSeeder(
                newNotifier(),
                inventoryRepo,
                boardService,
                boardTicketService,
                procedureService,
                lendingService,
                demoClock);
        var waitingListSeeder =
                new DemoWaitingListSeeder(waitingListRepo, memberGroupRepo, stationMemberRepo, accountRepo);
        var quizSeeder = new DemoQuizSeeder(quizCatalogRepo, quizTestRepo, quizService, quizImageService);
        var kbSeeder = new DemoKnowledgeBaseSeeder(kbService, kbContentService, knowledgeBaseRepo);
        var protocolSeeder = new DemoProtocolSeeder(testProtocolRepo, demoClock);
        var avatarSeeder = new DemoAvatarSeeder(avatarService, accountRepo, storageRoot.resolve("demo-avatars"));
        var federationSeeder = new DemoFederationSeeder(
                stationRepo,
                federationService,
                kbService,
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
                apiConfig,
                demoClock);
        var equipmentSeeder = new DemoEquipmentSeeder(equipmentNeedRepo, eventRepo, inventoryRepo, artRepo);
        var lendingSeeder = new DemoLendingSeeder(
                lendingService,
                new InventoryShareService(new InventoryShareRepository(), federationService, inventoryRepo, artRepo),
                inventoryRepo,
                artRepo,
                demoClock);
        var boardSeeder = new DemoBoardSeeder(
                boardRepo,
                boardTicketRepo,
                commentService,
                federatedBoardService,
                federationService,
                memberIdentityFactory,
                demoClock);
        var procedureSeeder = new DemoProcedureSeeder(procedureRepo);
        var selfCheckSeeder = new DemoSelfCheckSeeder(selfCheckRepo, inventoryRepo, itemCustodyService, demoClock);
        var demoStorageConfig = new Storage();
        var demoBackend = localStorage();
        var demoResolver = new StorageBackendResolver(demoBackend);
        var demoStorageSvc = new StorageService(demoResolver, demoBackend);
        var demoStorage = new MediaStorageService(demoStorageSvc, stationRepo, demoBackend);
        var demoMediaLibrary = new MediaLibraryService(
                mediaFileRepo,
                mediaMetaRepo,
                demoStorage,
                new ImageVariants(demoStorageSvc),
                new MediaReferenceRegistry(contentContainerRepo),
                new StorageQuotaService(storageUsageRepo, demoStorageConfig, noOpBus));
        var pageSeeder = new DemoPageSeeder(
                new PageService(
                        pageRepo,
                        contentBlocks(),
                        demoMediaLibrary,
                        new CellDescriptions(demoMediaLibrary, (stationId, pageUid) -> Optional.empty()),
                        stationMemberRepo,
                        avatarService,
                        stationRepo),
                pageRepo,
                demoMediaLibrary,
                formRepo,
                quizCatalogRepo);
        var newsSeeder = new DemoNewsSeeder(newsService, commentService, stationMemberRepo);
        var lostAndFoundSeederLocal = new DemoLostAndFoundSeeder(lostAndFoundService, demoClock);
        var checklistService = new ChecklistService(
                new ChecklistRepository(), stationMemberRepo, memberGroupRepo, userTagRepo, eventRegistrationRepo);
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
        var setupSeeder = new DemoSetupSeeder(stationRepo);
        var freshStationSeeder = new DemoFreshStationSeeder(stationRepo, accountRepo, stationMemberRepo);
        var demoInstance = mock(Demo.class);
        when(demoInstance.dev()).thenReturn(true);
        var twoFactorSeeder = new DemoTwoFactorSeeder(
                new TwoFactorRepository(), new TotpService(new TwoFactorSettings(), demoInstance));
        var videoSeeder = new DemoVideoSeeder(
                attendanceRepo,
                inventoryRepo,
                itemMovementService,
                eventServices.crud(),
                stationMailProviderRepo,
                lostAndFoundService,
                new LostAndFoundImageService(imageVariantWriter, stationRepo),
                formRepo,
                quizTestRepo,
                new QuizTestService(
                        quizTestRepo, new QuizQuestionSelector(quizCatalogRepo, quizTestRepo), restrictionService),
                accountRepo,
                stationMemberRepo,
                demoClock);

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
                        selfCheckSeeder,
                        avatarSeeder,
                        federationSeeder,
                        settingsSeeder,
                        checklistSeederLocal,
                        boardSeeder,
                        pageSeeder,
                        lendingSeeder,
                        equipmentSeeder,
                        notificationSeeder,
                        setupSeeder,
                        freshStationSeeder,
                        twoFactorSeeder,
                        videoSeeder),
                stationRepo,
                clusterRepo,
                new StorageBackendResolver(localStorage()),
                new TaskScheduler());
    }

    @Test
    @Order(1)
    void seedDataWithoutErrors() {
        demoService.resetAndSeed();
    }

    /**
     * Nothing that changes who is a member may run in the band that reads the roster.
     *
     * <p>Everything in {@link DemoSeeder#MODULES} runs at the same time. The waiting list ends with a
     * withdrawn applicant being deleted again, member and account both, and beside a seeder listing
     * the station's members that deletion lands between the listing and the write that follows it.
     * The write then points at somebody who is no longer there and the whole seed fails, which is
     * what it did: rarely, on whichever machine happened to interleave them that way.
     */
    @Test
    @Order(1)
    void theRosterSettlesBeforeTheParallelBand() {
        assertTrue(
                DemoSeeder.WAITING_LIST < DemoSeeder.MODULES,
                "the waiting list changes the roster, so it belongs before everything that reads it");
        var seeder = new DemoWaitingListSeeder(waitingListRepo, memberGroupRepo, stationMemberRepo, accountRepo);
        assertEquals(DemoSeeder.WAITING_LIST, seeder.order());
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

    /**
     * The demo has something to look at for both halves of sending a link.
     *
     * <p>Seeding without throwing says nothing about whether a link opens anything, and a fixed
     * token that nobody fetches is a string in a seeder rather than a demonstration. This opens both
     * the way a stranger would.
     */
    @Test
    @Order(4)
    void verifyThereIsSomethingToSend() {
        var station = stationRepo.findAll().stream()
                .filter(s -> "Jugendfeuerwehr Musterstadt".equals(s.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The demo station should exist"));

        var invitation = pageRepo.findByStation(station.id()).stream()
                .filter(p -> p.visibility() == PageVisibility.UNLISTED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("The demo should have a page reached by its link alone"));
        assertNull(invitation.parentId(), "a page reached by its link stands outside the tree");
        assertTrue(
                pageRepo.findListedByStation(station.id()).stream().noneMatch(p -> p.id() == invitation.id()),
                "and it is in no menu");

        var token = pageRepo.findShareToken(invitation.id())
                .orElseThrow(() -> new AssertionError("becoming reachable by a link mints one"));
        assertTrue(token.length() >= 40, "and it is a real one, not something anybody could type: " + token);
        assertEquals(
                invitation.id(), pageRepo.findByShareToken(token).orElseThrow().id(), "and the link opens that page");

        var poll = formRepo.findByStationAndPurpose(station.id(), FormPurpose.POLL).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("The demo should have a survey that can be sent"));
        assertEquals(Form.FormStatus.OPEN, poll.status(), "a survey nobody can answer demonstrates nothing");
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
     * The two full stations, which is what lets a feature be looked at inside an association and outside it.
     *
     * <p>They are seeded by the same seeders from the same data, so what is asserted is that the second one
     * really was built rather than half built, that it is the one inside the association, and that the same
     * person exists at both without the two colliding.
     */
    @Test
    @Order(6)
    void verifyBothFullStationsSeeded() {
        var musterstadt = stationByName("Jugendfeuerwehr Musterstadt");
        var nordstadt = stationByName("Jugendfeuerwehr Nordstadt");

        assertNull(musterstadt.clusterId(), "Musterstadt answers to nobody");
        assertNotNull(nordstadt.clusterId(), "Nordstadt answers to the association");

        assertTrue(accountRepo.findByEmail("max@mustermann.local").isPresent(), "Max at the first station");
        assertTrue(accountRepo.findByEmail("max@mustermann.nord.local").isPresent(), "Max at the second");

        assertEquals(
                stationMemberRepo.findByStation(musterstadt.id()).size(),
                stationMemberRepo.findByStation(nordstadt.id()).size(),
                "Both stations should carry the same members");
        assertTrue(
                inventoryRepo.findByStation(nordstadt.id()).size()
                        >= inventoryRepo.findByStation(musterstadt.id()).size(),
                "The twin should carry what the first carries, and the association's store on top");

        var federations = new FederationRepository();
        assertFalse(federations.findPartners(musterstadt.id()).isEmpty(), "Musterstadt has its partner");
        assertFalse(federations.findPartners(nordstadt.id()).isEmpty(), "Nordstadt has the same partner");
    }

    /**
     * The appointment seeded for today lands on the station's today, at the hour its clock shows, even
     * when the seed runs just after midnight there and the server's clock still reads yesterday.
     */
    @Test
    @Order(7)
    void todaysAppointmentIsOnTheStationsToday() {
        var stationToday = LocalDate.ofInstant(JUST_AFTER_MIDNIGHT, STATION_ZONE);
        assertNotEquals(
                LocalDate.ofInstant(JUST_AFTER_MIDNIGHT, ZoneOffset.UTC),
                stationToday,
                "the seed runs while the server's clock is still on the day before");

        var musterstadt = stationByName("Jugendfeuerwehr Musterstadt");
        var theorieabend = eventRepo.findByStation(musterstadt.id()).stream()
                .filter(event -> "Theorieabend".equals(event.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The demo should have an appointment today"));

        var start = theorieabend.startTime().atZone(STATION_ZONE);
        assertEquals(stationToday, start.toLocalDate(), "today's appointment falls on the station's today");
        assertEquals(LocalTime.of(16, 0), start.toLocalTime(), "at the hour the station's clock shows");
        assertTrue(
                eventRegistrationRepo.findByEvent(theorieabend.id()).stream()
                        .allMatch(registration -> stationToday.equals(registration.eventDate())),
                "and its answers are for that day");
    }

    private static Station stationByName(String name) {
        return stationRepo.findAll().stream()
                .filter(station -> name.equals(station.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The station '" + name + "' should exist"));
    }

    /**
     * The cluster seeder skips a lot of itself when the pieces it builds on are missing, which is right at
     * run time and useless in a test: a silent skip and a working seeder look identical from outside. These
     * assertions name the things that only exist if it ran the whole way through.
     *
     * <p>The federation partner and the mirror stay outside the cluster. Its storage room is checked in
     * all four places a station gets its numbers from, since a storage screen with none of them shows
     * nothing.
     */
    @Test
    void verifyClusterSeeded() {
        var cluster = clusterRepo.findAll().stream()
                .filter(c -> "Kreisverband Musterstadt".equals(c.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The demo cluster should exist"));

        assertTrue(cluster.usesInventory(), "The demo cluster should keep gear of its own");
        assertEquals(3, clusterRepo.findStationIds(cluster.id()).size(), "Three stations should be in the cluster");
        assertFalse(
                clusterApplicationRepo.findByCluster(cluster.id()).isEmpty(),
                "A station should be waiting to join the cluster");
        assertEquals(
                2,
                clusterProfileFieldRepo.findByCluster(cluster.id()).size(),
                "The cluster should ask two questions of its members");

        var room = clusterStorageQuotaService.findOverview(cluster.id());
        assertEquals(100L * 1024 * 1024 * 1024, room.poolBytes(), "The instance should have granted a pool");
        assertNotNull(room.defaults().quotaBytes(), "The cluster should say what a station it granted nothing gets");
        assertEquals(2, room.presets().size(), "The cluster should keep two tiers");
        assertEquals(
                41L * 1024 * 1024 * 1024,
                room.handedOut(),
                "Its own store and two of its stations should have been granted room");
        assertTrue(
                room.stations().stream()
                        .anyMatch(s -> s.ownStore() && s.granted().totalBytes() != null),
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
                notificationRepo.findRecent(Recipient.clusterMember(admin.id())).size(),
                "The administrator should have been told about the cluster's own business");
    }
}
