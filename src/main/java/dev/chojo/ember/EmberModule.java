/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.yubico.webauthn.RelyingParty;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.postgresql.mapper.PostgresqlMapper;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.File;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Database;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.conf.file.elements.Logging;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.handlers.BoardTicketChangedHandler;
import dev.chojo.ember.event.handlers.BulkMentionedInCommentHandler;
import dev.chojo.ember.event.handlers.CommentCreatedHandler;
import dev.chojo.ember.event.handlers.CommentDeletedHandler;
import dev.chojo.ember.event.handlers.EventCancelledHandler;
import dev.chojo.ember.event.handlers.EventCreatedHandler;
import dev.chojo.ember.event.handlers.EventDeletedHandler;
import dev.chojo.ember.event.handlers.EventRegistrationStatusHandler;
import dev.chojo.ember.event.handlers.EventsBatchCreatedHandler;
import dev.chojo.ember.event.handlers.ExchangeRequestedHandler;
import dev.chojo.ember.event.handlers.ExchangeStatusChangedHandler;
import dev.chojo.ember.event.handlers.FormDeletedHandler;
import dev.chojo.ember.event.handlers.FormPublishedHandler;
import dev.chojo.ember.event.handlers.LendingMessageSentHandler;
import dev.chojo.ember.event.handlers.LendingRequestedHandler;
import dev.chojo.ember.event.handlers.LendingStatusChangedHandler;
import dev.chojo.ember.event.handlers.MembersAddedToGroupHandler;
import dev.chojo.ember.event.handlers.MentionedInCommentHandler;
import dev.chojo.ember.event.handlers.NewsCreatedHandler;
import dev.chojo.ember.event.handlers.NewsDeletedHandler;
import dev.chojo.ember.event.handlers.ProcedureAssignedHandler;
import dev.chojo.ember.event.handlers.ProcedureItemCheckedHandler;
import dev.chojo.ember.event.handlers.ProcedureReopenedHandler;
import dev.chojo.ember.event.handlers.ProcedureResolvedHandler;
import dev.chojo.ember.event.handlers.ProcurementCreatedHandler;
import dev.chojo.ember.event.handlers.ProcurementFulfilledHandler;
import dev.chojo.ember.event.handlers.RegistrationDeadlineExpiredHandler;
import dev.chojo.ember.event.handlers.StorageWarningHandler;
import dev.chojo.ember.event.handlers.WaitlistPublicRegistrationHandler;
import dev.chojo.ember.feature.account.route.AccountDataRoutes;
import dev.chojo.ember.feature.account.route.AccountSessionRoutes;
import dev.chojo.ember.feature.account.route.AuthRoutes;
import dev.chojo.ember.feature.account.route.AvatarRoutes;
import dev.chojo.ember.feature.account.route.SessionRoutes;
import dev.chojo.ember.feature.attendance.route.AttendanceRoutes;
import dev.chojo.ember.feature.board.route.BoardRoutes;
import dev.chojo.ember.feature.board.route.BoardTicketAttachmentRoutes;
import dev.chojo.ember.feature.board.route.BoardTicketDetailRoutes;
import dev.chojo.ember.feature.board.route.BoardTicketHistoryRoutes;
import dev.chojo.ember.feature.board.route.BoardTicketLinkRoutes;
import dev.chojo.ember.feature.board.route.BoardTicketRoutes;
import dev.chojo.ember.feature.board.route.FederatedBoardRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketDetailRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketLinkRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardWebhookRoutes;
import dev.chojo.ember.feature.checklist.route.ChecklistRoutes;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes;
import dev.chojo.ember.feature.comment.route.NoteRoutes;
import dev.chojo.ember.feature.discovery.route.AdminDiscoveryRoutes;
import dev.chojo.ember.feature.discovery.route.PublicDiscoveryRoutes;
import dev.chojo.ember.feature.discovery.service.DiscoveryMaintenanceScheduler;
import dev.chojo.ember.feature.discovery.service.DiscoveryPingScheduler;
import dev.chojo.ember.feature.discovery.service.DiscoveryStationRefreshScheduler;
import dev.chojo.ember.feature.discovery.service.FederationPartnerSeeder;
import dev.chojo.ember.feature.events.route.EventRegistrationRoutes;
import dev.chojo.ember.feature.events.route.EventRoutes;
import dev.chojo.ember.feature.events.route.EventSharingRoutes;
import dev.chojo.ember.feature.events.route.EventStructureRoutes;
import dev.chojo.ember.feature.events.route.EventTemplateRoutes;
import dev.chojo.ember.feature.events.route.FederatedEventRoutes;
import dev.chojo.ember.feature.events.route.PublicEventRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.events.service.EventReminderChecker;
import dev.chojo.ember.feature.events.service.EventThresholdChecker;
import dev.chojo.ember.feature.federation.route.FederatedLendingRoutes;
import dev.chojo.ember.feature.federation.route.FederationRoutes;
import dev.chojo.ember.feature.federation.route.LendingRoutes;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes;
import dev.chojo.ember.feature.federation.service.FederationVersionBroadcaster;
import dev.chojo.ember.feature.feed.route.FeedMetricsRoutes;
import dev.chojo.ember.feature.feed.route.FeedTokenRoutes;
import dev.chojo.ember.feature.feed.route.UserFeedRoutes;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import dev.chojo.ember.feature.form.route.FormRoutes;
import dev.chojo.ember.feature.form.route.PublicFormRoutes;
import dev.chojo.ember.feature.insights.route.StationInsightsRoutes;
import dev.chojo.ember.feature.inventory.route.ExchangeRoutes;
import dev.chojo.ember.feature.inventory.route.InventoryCheckRoutes;
import dev.chojo.ember.feature.inventory.route.InventoryContainerRoutes;
import dev.chojo.ember.feature.inventory.route.InventoryFieldDefinitionRoutes;
import dev.chojo.ember.feature.inventory.route.InventoryRoutes;
import dev.chojo.ember.feature.inventory.route.ProcurementRoutes;
import dev.chojo.ember.feature.knowledgebase.route.FederatedKnowledgeBaseRoutes;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseAccessRoutes;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseCommentRoutes;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseRoutes;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseTagRoutes;
import dev.chojo.ember.feature.knowledgebase.route.PublicKnowledgeBaseRoutes;
import dev.chojo.ember.feature.knowledgebase.route.RemoteKnowledgeBaseRoutes;
import dev.chojo.ember.feature.legal.route.ConsentRoutes;
import dev.chojo.ember.feature.lostandfound.route.LostAndFoundRoutes;
import dev.chojo.ember.feature.mail.route.MailWebhookRoutes;
import dev.chojo.ember.feature.maps.route.AdminMapsRoutes;
import dev.chojo.ember.feature.maps.route.PublicMapsRoutes;
import dev.chojo.ember.feature.members.route.ManagedMemberRoutes;
import dev.chojo.ember.feature.members.route.MemberGroupRoutes;
import dev.chojo.ember.feature.members.route.MemberImportRoutes;
import dev.chojo.ember.feature.members.route.MemberRoutes;
import dev.chojo.ember.feature.members.route.ProfileFieldChangeRoutes;
import dev.chojo.ember.feature.members.route.ProfileFieldRoutes;
import dev.chojo.ember.feature.members.route.RegistrationCodeRoutes;
import dev.chojo.ember.feature.members.route.SavedFilterRoutes;
import dev.chojo.ember.feature.members.route.StationMemberInviteRoutes;
import dev.chojo.ember.feature.members.route.StationMemberRoutes;
import dev.chojo.ember.feature.members.route.TransferRoutes;
import dev.chojo.ember.feature.members.route.UserSettingsRoutes;
import dev.chojo.ember.feature.members.route.UserTagRoutes;
import dev.chojo.ember.feature.news.route.FederatedNewsRoutes;
import dev.chojo.ember.feature.news.route.NewsRoutes;
import dev.chojo.ember.feature.news.route.RemoteNewsRoutes;
import dev.chojo.ember.feature.notifications.route.NotificationRoutes;
import dev.chojo.ember.feature.page.route.PageRoutes;
import dev.chojo.ember.feature.page.route.PublicPageRoutes;
import dev.chojo.ember.feature.procedure.route.ProcedureRoutes;
import dev.chojo.ember.feature.protocol.route.FederatedTestProtocolRoutes;
import dev.chojo.ember.feature.protocol.route.RemoteTestProtocolRoutes;
import dev.chojo.ember.feature.protocol.route.TestProtocolRoutes;
import dev.chojo.ember.feature.quiz.route.AiRoutes;
import dev.chojo.ember.feature.quiz.route.FederatedQuizRoutes;
import dev.chojo.ember.feature.quiz.route.PublicQuizRoutes;
import dev.chojo.ember.feature.quiz.route.QuizAttemptRoutes;
import dev.chojo.ember.feature.quiz.route.QuizCatalogRoutes;
import dev.chojo.ember.feature.quiz.route.QuizQuestionRoutes;
import dev.chojo.ember.feature.quiz.route.QuizTestRoutes;
import dev.chojo.ember.feature.quiz.route.RemoteQuizRoutes;
import dev.chojo.ember.feature.station.route.DiscoveryRoutes;
import dev.chojo.ember.feature.station.route.PublicStationRoutes;
import dev.chojo.ember.feature.station.route.SetupRoutes;
import dev.chojo.ember.feature.station.route.StationApplicationRoutes;
import dev.chojo.ember.feature.station.route.StationManageRoutes;
import dev.chojo.ember.feature.station.route.StationRoutes;
import dev.chojo.ember.feature.station.transfer.AccountCredentialTableImporter;
import dev.chojo.ember.feature.station.transfer.AccountTableImporter;
import dev.chojo.ember.feature.station.transfer.DisabledModuleTableImporter;
import dev.chojo.ember.feature.station.transfer.StationTableImporter;
import dev.chojo.ember.feature.station.transfer.TableImporter;
import dev.chojo.ember.feature.statistics.route.StatisticsRoutes;
import dev.chojo.ember.feature.storage.route.StationStorageBackendRoutes;
import dev.chojo.ember.feature.storage.route.StorageRoutes;
import dev.chojo.ember.feature.storage.service.StorageReconciliationService;
import dev.chojo.ember.feature.storage.transfer.StationTransferAssetRoutes;
import dev.chojo.ember.feature.system.route.AdminSettingsRoutes;
import dev.chojo.ember.feature.system.route.ApiStatusRoutes;
import dev.chojo.ember.feature.system.route.DataRoutes;
import dev.chojo.ember.feature.system.route.DataTrackingRoutes;
import dev.chojo.ember.feature.system.route.InstallRoutes;
import dev.chojo.ember.feature.system.route.ProblemReportRoutes;
import dev.chojo.ember.feature.system.route.ProblemRoutes;
import dev.chojo.ember.feature.system.route.RequirementsRoutes;
import dev.chojo.ember.feature.system.route.SidebarCountRoutes;
import dev.chojo.ember.feature.system.route.SitemapRoutes;
import dev.chojo.ember.feature.system.route.UtilRoutes;
import dev.chojo.ember.feature.system.service.DemoAttendanceSeeder;
import dev.chojo.ember.feature.system.service.DemoBoardSeeder;
import dev.chojo.ember.feature.system.service.DemoChecklistSeeder;
import dev.chojo.ember.feature.system.service.DemoEventSeeder;
import dev.chojo.ember.feature.system.service.DemoFederationSeeder;
import dev.chojo.ember.feature.system.service.DemoFormSeeder;
import dev.chojo.ember.feature.system.service.DemoInventorySeeder;
import dev.chojo.ember.feature.system.service.DemoKnowledgeBaseSeeder;
import dev.chojo.ember.feature.system.service.DemoLendingSeeder;
import dev.chojo.ember.feature.system.service.DemoLostAndFoundSeeder;
import dev.chojo.ember.feature.system.service.DemoMediaSeeder;
import dev.chojo.ember.feature.system.service.DemoMemberSeeder;
import dev.chojo.ember.feature.system.service.DemoMirrorStationSeeder;
import dev.chojo.ember.feature.system.service.DemoNewsSeeder;
import dev.chojo.ember.feature.system.service.DemoNotificationSeeder;
import dev.chojo.ember.feature.system.service.DemoPageSeeder;
import dev.chojo.ember.feature.system.service.DemoProcedureSeeder;
import dev.chojo.ember.feature.system.service.DemoProtocolSeeder;
import dev.chojo.ember.feature.system.service.DemoQuizSeeder;
import dev.chojo.ember.feature.system.service.DemoSeeder;
import dev.chojo.ember.feature.system.service.DemoSessionSeeder;
import dev.chojo.ember.feature.system.service.DemoSettingsSeeder;
import dev.chojo.ember.feature.system.service.DemoSetupSeeder;
import dev.chojo.ember.feature.system.service.DemoStationSeeder;
import dev.chojo.ember.feature.system.service.DemoTwoFactorSeeder;
import dev.chojo.ember.feature.system.service.DemoWaitingListSeeder;
import dev.chojo.ember.feature.traffic.route.AdminTrafficRoutes;
import dev.chojo.ember.feature.traffic.route.StationTrafficRoutes;
import dev.chojo.ember.feature.twofactor.route.TwoFactorAdminRoutes;
import dev.chojo.ember.feature.twofactor.route.TwoFactorRoutes;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
import dev.chojo.ember.feature.waitinglist.route.WaitingListRoutes;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * Guice module that wires the entire Ember application.
 * Binds all route groups via multibinding, provides configuration objects,
 * sets up the database connection pool, runs SQL migrations, and configures SADU's query system.
 */
public class EmberModule extends AbstractModule {
    private static final Logger log = LoggerFactory.getLogger(EmberModule.class);
    private final Conf conf;

    /**
     * Creates the module with the given application configuration.
     *
     * @param conf the loaded application configuration
     */
    public EmberModule(Conf conf) {
        this.conf = conf;
    }

    @Override
    protected void configure() {
        Multibinder<Routes> routesBinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesBinder.addBinding().to(AuthRoutes.class);
        routesBinder.addBinding().to(MemberRoutes.class);
        routesBinder.addBinding().to(SessionRoutes.class);
        routesBinder.addBinding().to(AccountSessionRoutes.class);
        routesBinder.addBinding().to(AvatarRoutes.class);
        routesBinder.addBinding().to(AccountDataRoutes.class);
        routesBinder.addBinding().to(StationRoutes.class);
        routesBinder.addBinding().to(StationMemberRoutes.class);
        routesBinder.addBinding().to(StationMemberInviteRoutes.class);
        routesBinder.addBinding().to(AttendanceRoutes.class);
        routesBinder.addBinding().to(InventoryRoutes.class);
        routesBinder.addBinding().to(ProfileFieldRoutes.class);
        routesBinder.addBinding().to(ProfileFieldChangeRoutes.class);
        routesBinder.addBinding().to(MemberGroupRoutes.class);
        routesBinder.addBinding().to(RegistrationCodeRoutes.class);
        routesBinder.addBinding().to(StationManageRoutes.class);
        routesBinder.addBinding().to(SetupRoutes.class);
        routesBinder.addBinding().to(EventStructureRoutes.class);
        routesBinder.addBinding().to(EventRegistrationRoutes.class);
        routesBinder.addBinding().to(EventSharingRoutes.class);
        routesBinder.addBinding().to(EventRoutes.class);
        routesBinder.addBinding().to(FederatedEventRoutes.class);
        routesBinder.addBinding().to(RemoteEventRoutes.class);
        routesBinder.addBinding().to(EventTemplateRoutes.class);
        routesBinder.addBinding().to(SavedFilterRoutes.class);
        routesBinder.addBinding().to(InventoryCheckRoutes.class);
        routesBinder.addBinding().to(ManagedMemberRoutes.class);
        routesBinder.addBinding().to(StationApplicationRoutes.class);
        routesBinder.addBinding().to(StatisticsRoutes.class);
        routesBinder.addBinding().to(MemberImportRoutes.class);
        routesBinder.addBinding().to(NewsRoutes.class);
        routesBinder.addBinding().to(FederatedNewsRoutes.class);
        routesBinder.addBinding().to(RemoteNewsRoutes.class);
        routesBinder.addBinding().to(PageRoutes.class);
        routesBinder.addBinding().to(PublicPageRoutes.class);
        routesBinder.addBinding().to(MailWebhookRoutes.class);
        routesBinder.addBinding().to(UserSettingsRoutes.class);
        routesBinder.addBinding().to(ExchangeRoutes.class);
        routesBinder.addBinding().to(ProcurementRoutes.class);
        routesBinder.addBinding().to(InventoryContainerRoutes.class);
        routesBinder.addBinding().to(InventoryFieldDefinitionRoutes.class);
        routesBinder.addBinding().to(UserTagRoutes.class);
        routesBinder.addBinding().to(NotificationRoutes.class);
        routesBinder.addBinding().to(FormRoutes.class);
        routesBinder.addBinding().to(PublicFormRoutes.class);
        routesBinder.addBinding().to(ConsentRoutes.class);
        routesBinder.addBinding().to(LostAndFoundRoutes.class);
        routesBinder.addBinding().to(StationTransferAssetRoutes.class);
        routesBinder.addBinding().to(TransferRoutes.class);
        routesBinder.addBinding().to(AdminSettingsRoutes.class);
        routesBinder.addBinding().to(InstallRoutes.class);
        routesBinder.addBinding().to(DataTrackingRoutes.class);
        routesBinder.addBinding().to(ProblemRoutes.class);
        routesBinder.addBinding().to(ProblemReportRoutes.class);
        routesBinder.addBinding().to(ApiStatusRoutes.class);
        routesBinder.addBinding().to(WaitingListRoutes.class);
        routesBinder.addBinding().to(QuizCatalogRoutes.class);
        routesBinder.addBinding().to(QuizQuestionRoutes.class);
        routesBinder.addBinding().to(QuizTestRoutes.class);
        routesBinder.addBinding().to(QuizAttemptRoutes.class);
        routesBinder.addBinding().to(FederatedQuizRoutes.class);
        routesBinder.addBinding().to(RemoteQuizRoutes.class);
        routesBinder.addBinding().to(PublicQuizRoutes.class);
        routesBinder.addBinding().to(AiRoutes.class);
        routesBinder.addBinding().to(KnowledgeBaseRoutes.class);
        routesBinder.addBinding().to(KnowledgeBaseAccessRoutes.class);
        routesBinder.addBinding().to(KnowledgeBaseTagRoutes.class);
        routesBinder.addBinding().to(KnowledgeBaseCommentRoutes.class);
        routesBinder.addBinding().to(FederatedKnowledgeBaseRoutes.class);
        routesBinder.addBinding().to(RemoteKnowledgeBaseRoutes.class);
        routesBinder.addBinding().to(PublicKnowledgeBaseRoutes.class);
        routesBinder.addBinding().to(PublicEventRoutes.class);
        routesBinder.addBinding().to(PublicStationRoutes.class);
        routesBinder.addBinding().to(UtilRoutes.class);
        routesBinder.addBinding().to(TestProtocolRoutes.class);
        routesBinder.addBinding().to(FederatedTestProtocolRoutes.class);
        routesBinder.addBinding().to(RemoteTestProtocolRoutes.class);
        routesBinder.addBinding().to(FederationRoutes.class);
        routesBinder.addBinding().to(RemoteFederationRoutes.class);
        routesBinder.addBinding().to(LendingRoutes.class);
        routesBinder.addBinding().to(FederatedLendingRoutes.class);
        routesBinder.addBinding().to(RemoteLendingRoutes.class);
        routesBinder.addBinding().to(DiscoveryRoutes.class);
        routesBinder.addBinding().to(PublicDiscoveryRoutes.class);
        routesBinder.addBinding().to(AdminDiscoveryRoutes.class);
        routesBinder.addBinding().to(PublicMapsRoutes.class);
        routesBinder.addBinding().to(AdminMapsRoutes.class);
        routesBinder.addBinding().to(FeedTokenRoutes.class);
        routesBinder.addBinding().to(UserFeedRoutes.class);
        routesBinder.addBinding().to(FeedMetricsRoutes.class);
        routesBinder.addBinding().to(EventCommentRoutes.class);
        routesBinder.addBinding().to(NoteRoutes.class);
        routesBinder.addBinding().to(BoardRoutes.class);
        routesBinder.addBinding().to(BoardTicketRoutes.class);
        routesBinder.addBinding().to(BoardTicketDetailRoutes.class);
        routesBinder.addBinding().to(BoardTicketLinkRoutes.class);
        routesBinder.addBinding().to(BoardTicketAttachmentRoutes.class);
        routesBinder.addBinding().to(BoardTicketHistoryRoutes.class);
        routesBinder.addBinding().to(FederatedBoardRoutes.class);
        routesBinder.addBinding().to(RemoteBoardWebhookRoutes.class);
        routesBinder.addBinding().to(RemoteBoardRoutes.class);
        routesBinder.addBinding().to(RemoteBoardTicketRoutes.class);
        routesBinder.addBinding().to(RemoteBoardTicketDetailRoutes.class);
        routesBinder.addBinding().to(RemoteBoardTicketLinkRoutes.class);
        routesBinder.addBinding().to(ChecklistRoutes.class);
        routesBinder.addBinding().to(RequirementsRoutes.class);
        routesBinder.addBinding().to(SidebarCountRoutes.class);
        routesBinder.addBinding().to(DataRoutes.class);
        routesBinder.addBinding().to(SitemapRoutes.class);
        routesBinder.addBinding().to(ProcedureRoutes.class);
        routesBinder.addBinding().to(StorageRoutes.class);
        routesBinder.addBinding().to(StationStorageBackendRoutes.class);
        routesBinder.addBinding().to(AdminTrafficRoutes.class);
        routesBinder.addBinding().to(StationTrafficRoutes.class);
        routesBinder.addBinding().to(StationInsightsRoutes.class);
        routesBinder.addBinding().to(TwoFactorRoutes.class);
        routesBinder.addBinding().to(TwoFactorAdminRoutes.class);

        Multibinder<TableImporter> tableImporterBinder = Multibinder.newSetBinder(binder(), TableImporter.class);
        tableImporterBinder.addBinding().to(StationTableImporter.class);
        tableImporterBinder.addBinding().to(AccountTableImporter.class);
        tableImporterBinder.addBinding().to(AccountCredentialTableImporter.class);
        tableImporterBinder.addBinding().to(DisabledModuleTableImporter.class);

        Multibinder<DemoSeeder> demoSeederBinder = Multibinder.newSetBinder(binder(), DemoSeeder.class);
        demoSeederBinder.addBinding().to(DemoStationSeeder.class);
        demoSeederBinder.addBinding().to(DemoMemberSeeder.class);
        demoSeederBinder.addBinding().to(DemoMirrorStationSeeder.class);
        demoSeederBinder.addBinding().to(DemoEventSeeder.class);
        demoSeederBinder.addBinding().to(DemoNewsSeeder.class);
        demoSeederBinder.addBinding().to(DemoLostAndFoundSeeder.class);
        demoSeederBinder.addBinding().to(DemoAttendanceSeeder.class);
        demoSeederBinder.addBinding().to(DemoInventorySeeder.class);
        demoSeederBinder.addBinding().to(DemoFormSeeder.class);
        demoSeederBinder.addBinding().to(DemoSessionSeeder.class);
        demoSeederBinder.addBinding().to(DemoWaitingListSeeder.class);
        demoSeederBinder.addBinding().to(DemoQuizSeeder.class);
        demoSeederBinder.addBinding().to(DemoKnowledgeBaseSeeder.class);
        demoSeederBinder.addBinding().to(DemoProtocolSeeder.class);
        demoSeederBinder.addBinding().to(DemoProcedureSeeder.class);
        demoSeederBinder.addBinding().to(DemoMediaSeeder.class);
        demoSeederBinder.addBinding().to(DemoFederationSeeder.class);
        demoSeederBinder.addBinding().to(DemoSettingsSeeder.class);
        demoSeederBinder.addBinding().to(DemoChecklistSeeder.class);
        demoSeederBinder.addBinding().to(DemoBoardSeeder.class);
        demoSeederBinder.addBinding().to(DemoPageSeeder.class);
        demoSeederBinder.addBinding().to(DemoLendingSeeder.class);
        demoSeederBinder.addBinding().to(DemoNotificationSeeder.class);
        demoSeederBinder.addBinding().to(DemoSetupSeeder.class);
        demoSeederBinder.addBinding().to(DemoTwoFactorSeeder.class);

        // Domain event handlers
        Multibinder<DomainEventHandler<?>> eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<>() {});
        eventBinder.addBinding().to(EventCreatedHandler.class);
        eventBinder.addBinding().to(EventsBatchCreatedHandler.class);
        eventBinder.addBinding().to(EventDeletedHandler.class);
        eventBinder.addBinding().to(EventRegistrationStatusHandler.class);
        eventBinder.addBinding().to(NewsCreatedHandler.class);
        eventBinder.addBinding().to(NewsDeletedHandler.class);
        eventBinder.addBinding().to(CommentCreatedHandler.class);
        eventBinder.addBinding().to(CommentDeletedHandler.class);
        eventBinder.addBinding().to(ExchangeRequestedHandler.class);
        eventBinder.addBinding().to(ExchangeStatusChangedHandler.class);
        eventBinder.addBinding().to(FormPublishedHandler.class);
        eventBinder.addBinding().to(FormDeletedHandler.class);
        eventBinder.addBinding().to(ProcurementCreatedHandler.class);
        eventBinder.addBinding().to(ProcurementFulfilledHandler.class);
        eventBinder.addBinding().to(RegistrationDeadlineExpiredHandler.class);
        eventBinder.addBinding().to(MembersAddedToGroupHandler.class);
        eventBinder.addBinding().to(LendingRequestedHandler.class);
        eventBinder.addBinding().to(LendingStatusChangedHandler.class);
        eventBinder.addBinding().to(LendingMessageSentHandler.class);
        eventBinder.addBinding().to(MentionedInCommentHandler.class);
        eventBinder.addBinding().to(BulkMentionedInCommentHandler.class);
        eventBinder.addBinding().to(BoardTicketChangedHandler.class);
        eventBinder.addBinding().to(EventCancelledHandler.class);
        eventBinder.addBinding().to(ProcedureAssignedHandler.class);
        eventBinder.addBinding().to(ProcedureResolvedHandler.class);
        eventBinder.addBinding().to(ProcedureReopenedHandler.class);
        eventBinder.addBinding().to(ProcedureItemCheckedHandler.class);
        eventBinder.addBinding().to(WaitlistPublicRegistrationHandler.class);
        eventBinder.addBinding().to(StorageWarningHandler.class);

        // Eager singletons - started on boot
        bind(EventThresholdChecker.class).asEagerSingleton();
        bind(EventReminderChecker.class).asEagerSingleton();
        bind(StorageReconciliationService.class).asEagerSingleton();
        bind(FederationVersionBroadcaster.class).asEagerSingleton();
        bind(FeedMetricsService.class).asEagerSingleton();
        // Discovery chain
        bind(FederationPartnerSeeder.class).asEagerSingleton();
        bind(DiscoveryPingScheduler.class).asEagerSingleton();
        bind(DiscoveryStationRefreshScheduler.class).asEagerSingleton();
        bind(DiscoveryMaintenanceScheduler.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    Conf conf() {
        return conf;
    }

    @Provides
    @Singleton
    File config() {
        var config = conf.main();
        log.info(config.toString());
        conf.save();
        return config;
    }

    @Provides
    @Singleton
    Database database(File config) {
        return config.database();
    }

    @Provides
    @Singleton
    Api api(File config) {
        return config.api();
    }

    @Provides
    @Singleton
    Mailing mailing(File config) {
        return config.mailing();
    }

    @Provides
    @Singleton
    Logging logging(File config) {
        return config.logging();
    }

    @Provides
    @Singleton
    Auth auth(File config) {
        return config.auth();
    }

    @Provides
    @Singleton
    TwoFactorSettings twoFactorSettings(Auth auth) {
        return auth.twoFactor();
    }

    @Provides
    @Singleton
    RelyingParty webAuthnRelyingParty(TwoFactorSettings twoFactor, Api api, WebAuthnCredentialStore store) {
        return WebAuthnRelyingPartyFactory.build(twoFactor, api, store);
    }

    @Provides
    @Singleton
    Demo demo(File config) {
        return config.demo();
    }

    @Provides
    @Singleton
    Storage storage(File config) {
        return config.storage();
    }

    @Provides
    @Singleton
    Metrics metrics(File config) {
        return config.metrics();
    }

    @Provides
    @Singleton
    Network network(File config) {
        return config.network();
    }

    @Provides
    @Singleton
    Federation federation(File config) {
        return config.federation();
    }

    @Provides
    @Singleton
    DataSource dataSource(Database database) {
        return DataSourceCreator.create(PostgreSql.get())
                .configure(config -> config.withConfig(database)
                        .currentSchema(database.schema())
                        .applicationName("Ember"))
                .create()
                .withMaximumPoolSize(database.poolSize())
                .withMinimumIdle(1)
                .build();
    }

    @Provides
    @Singleton
    QueryConfiguration queryConfiguration(DataSource dataSource, Database database, Demo demo)
            throws SQLException, IOException {
        // Skip the up-front migration only in full demo mode, where DemoService.resetAndSeed()
        // drops the schema and re-runs the migration on every start. In every other mode -
        // production, plain dev (DEMO_DEV=true without DEMO_ENABLED), and a fresh database under
        // either - the schema must be in place before Guice provisions services whose
        // constructors already query it.
        if (!demo.enabled()) {
            SqlUpdater.builder(dataSource, PostgreSql.get())
                    .setReplacements(new QueryReplacement("ember_schema", database.schema()))
                    .setSchemas(database.schema())
                    .execute();
        }

        var config = QueryConfiguration.builder(dataSource)
                .setExceptionHandler(err -> log.error("Database query error", err))
                .setThrowExceptions(true)
                .setRowMapperRegistry(new RowMapperRegistry().register(PostgresqlMapper.getDefaultMapper()))
                .build();
        QueryConfiguration.setDefault(config);
        return config;
    }
}
