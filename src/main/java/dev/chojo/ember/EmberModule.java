/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import de.chojo.sadu.core.updater.SqlVersion;
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
import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Changelog;
import dev.chojo.ember.conf.file.elements.Database;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.conf.file.elements.KnowledgeBase;
import dev.chojo.ember.conf.file.elements.Logging;
import dev.chojo.ember.conf.file.elements.MailImport;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.conf.file.elements.Updates;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.db.ProfileFieldMergeBackup;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.feature.account.route.AccountAdminRoutes;
import dev.chojo.ember.feature.account.route.AccountDataRoutes;
import dev.chojo.ember.feature.account.route.AccountSessionRoutes;
import dev.chojo.ember.feature.account.route.AuthRoutes;
import dev.chojo.ember.feature.account.route.AvatarRoutes;
import dev.chojo.ember.feature.account.route.SessionRoutes;
import dev.chojo.ember.feature.account.service.AuthCleanupSweeper;
import dev.chojo.ember.feature.attendance.handler.EventAnswerRecordedHandler;
import dev.chojo.ember.feature.attendance.route.AttendanceRoutes;
import dev.chojo.ember.feature.beacon.route.BeaconAdminRoutes;
import dev.chojo.ember.feature.beacon.route.BeaconIntakeRoutes;
import dev.chojo.ember.feature.beacon.service.BeaconMetricsService;
import dev.chojo.ember.feature.board.handler.BoardTicketChangedHandler;
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
import dev.chojo.ember.feature.board.service.BoardFeedDetails;
import dev.chojo.ember.feature.board.service.DueDateReminderChecker;
import dev.chojo.ember.feature.board.service.FederatedBoardDiscoveryService;
import dev.chojo.ember.feature.board.service.FederatedBoardNotificationService;
import dev.chojo.ember.feature.board.service.FederatedBoardStructureProxy;
import dev.chojo.ember.feature.board.service.FederatedTicketDetailProxy;
import dev.chojo.ember.feature.board.service.FederatedTicketProxy;
import dev.chojo.ember.feature.board.service.TicketCommentTarget;
import dev.chojo.ember.feature.checklist.route.ChecklistRoutes;
import dev.chojo.ember.feature.cluster.ClusterModule;
import dev.chojo.ember.feature.cluster.handler.ClusterApplicationResolvedHandler;
import dev.chojo.ember.feature.cluster.handler.ClusterApplicationSubmittedHandler;
import dev.chojo.ember.feature.cluster.handler.ClusterApplicationWithdrawnHandler;
import dev.chojo.ember.feature.cluster.handler.ClusterEventShareHandler;
import dev.chojo.ember.feature.cluster.handler.ClusterFieldValueChangedHandler;
import dev.chojo.ember.feature.cluster.handler.ClusterGovernanceHandler;
import dev.chojo.ember.feature.cluster.handler.ClusterMemberRoleChangedHandler;
import dev.chojo.ember.feature.cluster.handler.ClusterNewsShareHandler;
import dev.chojo.ember.feature.cluster.handler.ClusterQuotaChangedHandler;
import dev.chojo.ember.feature.cluster.handler.ClusterStationReleasedHandler;
import dev.chojo.ember.feature.cluster.service.AssociationProfileFields;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.handler.BulkMentionedInCommentHandler;
import dev.chojo.ember.feature.comment.handler.CommentCreatedHandler;
import dev.chojo.ember.feature.comment.handler.CommentDeletedHandler;
import dev.chojo.ember.feature.comment.handler.MentionedInCommentHandler;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes;
import dev.chojo.ember.feature.comment.route.NoteRoutes;
import dev.chojo.ember.feature.comment.service.CommentFeedDetails;
import dev.chojo.ember.feature.comment.service.CommentTarget;
import dev.chojo.ember.feature.content.service.BlockReferences;
import dev.chojo.ember.feature.content.service.CellDescriptions;
import dev.chojo.ember.feature.discovery.route.AdminDiscoveryRoutes;
import dev.chojo.ember.feature.discovery.route.PublicDiscoveryRoutes;
import dev.chojo.ember.feature.discovery.route.RemoteStationLogoRoutes;
import dev.chojo.ember.feature.discovery.service.DiscoveryMaintenanceScheduler;
import dev.chojo.ember.feature.discovery.service.DiscoveryPingScheduler;
import dev.chojo.ember.feature.discovery.service.DiscoveryStationRefreshScheduler;
import dev.chojo.ember.feature.discovery.service.FederationPartnerSeeder;
import dev.chojo.ember.feature.documents.route.DocumentRoutes;
import dev.chojo.ember.feature.equipment.route.EquipmentBrowseRoutes;
import dev.chojo.ember.feature.equipment.route.EquipmentNeedRoutes;
import dev.chojo.ember.feature.events.handler.EventCancelledHandler;
import dev.chojo.ember.feature.events.handler.EventChangedHandler;
import dev.chojo.ember.feature.events.handler.EventCreatedHandler;
import dev.chojo.ember.feature.events.handler.EventDateRestoredHandler;
import dev.chojo.ember.feature.events.handler.EventDeletedHandler;
import dev.chojo.ember.feature.events.handler.EventRegistrationStatusHandler;
import dev.chojo.ember.feature.events.handler.EventsBatchCreatedHandler;
import dev.chojo.ember.feature.events.handler.RegistrationDeadlineExpiredHandler;
import dev.chojo.ember.feature.events.route.EventAttachmentRoutes;
import dev.chojo.ember.feature.events.route.EventCancellationRoutes;
import dev.chojo.ember.feature.events.route.EventEmbedRoutes;
import dev.chojo.ember.feature.events.route.EventRegistrationRoutes;
import dev.chojo.ember.feature.events.route.EventRoutes;
import dev.chojo.ember.feature.events.route.EventSharingRoutes;
import dev.chojo.ember.feature.events.route.EventStructureRoutes;
import dev.chojo.ember.feature.events.route.EventTemplateRoutes;
import dev.chojo.ember.feature.events.route.FederatedEventRoutes;
import dev.chojo.ember.feature.events.route.PublicEventRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.events.service.EventBlockReferences;
import dev.chojo.ember.feature.events.service.EventCommentTarget;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventFeedDetails;
import dev.chojo.ember.feature.events.service.EventReminderChecker;
import dev.chojo.ember.feature.events.service.EventThresholdChecker;
import dev.chojo.ember.feature.events.service.FieldRegistrationSweeper;
import dev.chojo.ember.feature.events.service.RegistrationDeadlineChecker;
import dev.chojo.ember.feature.events.service.SettledRefusalSweeper;
import dev.chojo.ember.feature.federation.handler.FederationRequestAnsweredHandler;
import dev.chojo.ember.feature.federation.handler.FederationRequestReceivedHandler;
import dev.chojo.ember.feature.federation.handler.LendingMessageSentHandler;
import dev.chojo.ember.feature.federation.handler.LendingRequestedHandler;
import dev.chojo.ember.feature.federation.handler.LendingStatusChangedHandler;
import dev.chojo.ember.feature.federation.route.FederatedLendingRoutes;
import dev.chojo.ember.feature.federation.route.FederationRoutes;
import dev.chojo.ember.feature.federation.route.InventoryShareRoutes;
import dev.chojo.ember.feature.federation.route.LendingRoutes;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes;
import dev.chojo.ember.feature.federation.service.FederationVersionBroadcaster;
import dev.chojo.ember.feature.federation.service.LendingFeedDetails;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.RoutingFederationTransport;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.feed.route.FeedMetricsRoutes;
import dev.chojo.ember.feature.feed.route.FeedTokenRoutes;
import dev.chojo.ember.feature.feed.route.StationFeedUseRoutes;
import dev.chojo.ember.feature.feed.route.UserFeedRoutes;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import dev.chojo.ember.feature.form.handler.FormDeletedHandler;
import dev.chojo.ember.feature.form.handler.FormPublishedHandler;
import dev.chojo.ember.feature.form.route.FormRoutes;
import dev.chojo.ember.feature.form.route.PublicFormRoutes;
import dev.chojo.ember.feature.form.service.FormFeedDetails;
import dev.chojo.ember.feature.generator.route.DocumentGenerationRoutes;
import dev.chojo.ember.feature.generator.route.DocumentTemplateRoutes;
import dev.chojo.ember.feature.insights.route.StationInsightsRoutes;
import dev.chojo.ember.feature.insights.service.PageHitRecorder;
import dev.chojo.ember.feature.inventory.handler.ClusterItemIssuedHandler;
import dev.chojo.ember.feature.inventory.handler.ClusterItemLostHandler;
import dev.chojo.ember.feature.inventory.handler.MovementAdvancedHandler;
import dev.chojo.ember.feature.inventory.handler.MovementCancelledHandler;
import dev.chojo.ember.feature.inventory.handler.MovementDeclinedHandler;
import dev.chojo.ember.feature.inventory.handler.MovementStartedHandler;
import dev.chojo.ember.feature.inventory.handler.ProcurementCreatedHandler;
import dev.chojo.ember.feature.inventory.handler.ProcurementFulfilledHandler;
import dev.chojo.ember.feature.inventory.route.FederatedInventoryTagRoutes;
import dev.chojo.ember.feature.inventory.route.InventoryArtRoutes;
import dev.chojo.ember.feature.inventory.route.InventoryCheckRoutes;
import dev.chojo.ember.feature.inventory.route.InventoryContainerRoutes;
import dev.chojo.ember.feature.inventory.route.InventoryFieldDefinitionRoutes;
import dev.chojo.ember.feature.inventory.route.InventoryRoutes;
import dev.chojo.ember.feature.inventory.route.InventoryTagRoutes;
import dev.chojo.ember.feature.inventory.route.MovementFlowRoutes;
import dev.chojo.ember.feature.inventory.route.MovementRoutes;
import dev.chojo.ember.feature.inventory.route.ProcurementRoutes;
import dev.chojo.ember.feature.inventory.route.RemoteInventoryTagRoutes;
import dev.chojo.ember.feature.inventory.route.SelfCheckReviewRoutes;
import dev.chojo.ember.feature.inventory.route.SelfCheckRoutes;
import dev.chojo.ember.feature.inventory.service.FederatedItemTagService;
import dev.chojo.ember.feature.inventory.service.InventoryFeedDetails;
import dev.chojo.ember.feature.knowledgebase.route.FederatedKnowledgeBaseRoutes;
import dev.chojo.ember.feature.knowledgebase.route.KbFavouriteRoutes;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseAccessRoutes;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseCommentRoutes;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseRoutes;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseTagRoutes;
import dev.chojo.ember.feature.knowledgebase.route.PublicKnowledgeBaseRoutes;
import dev.chojo.ember.feature.knowledgebase.route.RemoteKnowledgeBaseRoutes;
import dev.chojo.ember.feature.knowledgebase.service.KbCommentTarget;
import dev.chojo.ember.feature.knowledgebase.service.KbTrashPurger;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.legal.route.ConsentRoutes;
import dev.chojo.ember.feature.lostandfound.route.LostAndFoundRoutes;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundFeedDetails;
import dev.chojo.ember.feature.mail.route.MailWebhookRoutes;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailWebhookService;
import dev.chojo.ember.feature.mailimport.route.MailImportRoutes;
import dev.chojo.ember.feature.mailimport.service.MailFilingService;
import dev.chojo.ember.feature.mailimport.service.MailImportPoller;
import dev.chojo.ember.feature.mailimport.service.StationMemberNaming;
import dev.chojo.ember.feature.maps.route.AdminMapsRoutes;
import dev.chojo.ember.feature.maps.route.PublicMapsRoutes;
import dev.chojo.ember.feature.media.route.MediaRoutes;
import dev.chojo.ember.feature.media.route.PublicMediaRoutes;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.handler.MembersAddedToGroupHandler;
import dev.chojo.ember.feature.members.route.ManagedMemberRoutes;
import dev.chojo.ember.feature.members.route.MemberGroupRoutes;
import dev.chojo.ember.feature.members.route.MemberGroupSetRoutes;
import dev.chojo.ember.feature.members.route.MemberImportRoutes;
import dev.chojo.ember.feature.members.route.MemberRoutes;
import dev.chojo.ember.feature.members.route.MemberTableRoutes;
import dev.chojo.ember.feature.members.route.ProfileFieldChangeRoutes;
import dev.chojo.ember.feature.members.route.ProfileFieldRoutes;
import dev.chojo.ember.feature.members.route.RegistrationCodeRoutes;
import dev.chojo.ember.feature.members.route.SavedFilterRoutes;
import dev.chojo.ember.feature.members.route.StationMemberInviteRoutes;
import dev.chojo.ember.feature.members.route.StationMemberRoutes;
import dev.chojo.ember.feature.members.route.TransferRoutes;
import dev.chojo.ember.feature.members.route.UserSettingsRoutes;
import dev.chojo.ember.feature.members.route.UserTagRoutes;
import dev.chojo.ember.feature.members.service.ExpiryReminderService;
import dev.chojo.ember.feature.members.service.ManagedLoginNoticeSweeper;
import dev.chojo.ember.feature.members.service.MemberFeedDetails;
import dev.chojo.ember.feature.members.service.ProfileFieldOwner;
import dev.chojo.ember.feature.members.service.StationMemberEligibility;
import dev.chojo.ember.feature.members.service.StationProfileFields;
import dev.chojo.ember.feature.news.handler.NewsCreatedHandler;
import dev.chojo.ember.feature.news.handler.NewsDeletedHandler;
import dev.chojo.ember.feature.news.route.AdminNewsRoutes;
import dev.chojo.ember.feature.news.route.FederatedNewsRoutes;
import dev.chojo.ember.feature.news.route.NewsRoutes;
import dev.chojo.ember.feature.news.route.RemoteNewsRoutes;
import dev.chojo.ember.feature.news.service.NewsBlockReferences;
import dev.chojo.ember.feature.news.service.NewsCommentTarget;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsFeedDetails;
import dev.chojo.ember.feature.notifications.route.NotificationRoutes;
import dev.chojo.ember.feature.notifications.service.NotificationDigest;
import dev.chojo.ember.feature.onboarding.route.OnboardingRoutes;
import dev.chojo.ember.feature.page.route.PageRoutes;
import dev.chojo.ember.feature.page.route.PublicPageRoutes;
import dev.chojo.ember.feature.page.route.SharedPageRoutes;
import dev.chojo.ember.feature.page.service.StationPageAddressing;
import dev.chojo.ember.feature.passkey.route.PasskeyAdminRoutes;
import dev.chojo.ember.feature.passkey.route.PasskeyRoutes;
import dev.chojo.ember.feature.procedure.handler.ProcedureAssignedHandler;
import dev.chojo.ember.feature.procedure.handler.ProcedureItemCheckedHandler;
import dev.chojo.ember.feature.procedure.handler.ProcedureReopenedHandler;
import dev.chojo.ember.feature.procedure.handler.ProcedureResolvedHandler;
import dev.chojo.ember.feature.procedure.route.ProcedureRoutes;
import dev.chojo.ember.feature.procedure.service.ProcedureFeedDetails;
import dev.chojo.ember.feature.protocol.route.FederatedTestProtocolRoutes;
import dev.chojo.ember.feature.protocol.route.RemoteTestProtocolRoutes;
import dev.chojo.ember.feature.protocol.route.TestProtocolRoutes;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.question.MemberEligibility;
import dev.chojo.ember.feature.quiz.route.AccountAiCredentialRoutes;
import dev.chojo.ember.feature.quiz.route.AiRoutes;
import dev.chojo.ember.feature.quiz.route.FederatedQuizRoutes;
import dev.chojo.ember.feature.quiz.route.PublicQuizRoutes;
import dev.chojo.ember.feature.quiz.route.QuizAttemptRoutes;
import dev.chojo.ember.feature.quiz.route.QuizCatalogRoutes;
import dev.chojo.ember.feature.quiz.route.QuizQuestionRoutes;
import dev.chojo.ember.feature.quiz.route.QuizTestRoutes;
import dev.chojo.ember.feature.quiz.route.RemoteQuizRoutes;
import dev.chojo.ember.feature.quiz.service.QuizFederationService;
import dev.chojo.ember.feature.station.route.DiscoveryRoutes;
import dev.chojo.ember.feature.station.route.FirstStationRoutes;
import dev.chojo.ember.feature.station.route.PublicStationRoutes;
import dev.chojo.ember.feature.station.route.SetupRoutes;
import dev.chojo.ember.feature.station.route.StationApplicationRoutes;
import dev.chojo.ember.feature.station.route.StationManageRoutes;
import dev.chojo.ember.feature.station.route.StationRoutes;
import dev.chojo.ember.feature.station.service.TransferTimeoutWatchdog;
import dev.chojo.ember.feature.station.transfer.AccountCredentialTableImporter;
import dev.chojo.ember.feature.station.transfer.AccountTableImporter;
import dev.chojo.ember.feature.station.transfer.DisabledModuleTableImporter;
import dev.chojo.ember.feature.station.transfer.StationTableImporter;
import dev.chojo.ember.feature.station.transfer.TableImporter;
import dev.chojo.ember.feature.statistics.route.StatisticsRoutes;
import dev.chojo.ember.feature.storage.handler.StorageWarningHandler;
import dev.chojo.ember.feature.storage.route.StationStorageBackendRoutes;
import dev.chojo.ember.feature.storage.route.StorageRoutes;
import dev.chojo.ember.feature.storage.service.StorageFeedDetails;
import dev.chojo.ember.feature.storage.service.StorageReconciliationService;
import dev.chojo.ember.feature.storage.transfer.StationTransferAssetRoutes;
import dev.chojo.ember.feature.system.route.AdminMonitoringCountRoutes;
import dev.chojo.ember.feature.system.route.AdminSettingsRoutes;
import dev.chojo.ember.feature.system.route.ApiStatusRoutes;
import dev.chojo.ember.feature.system.route.ChangelogRoutes;
import dev.chojo.ember.feature.system.route.DataRoutes;
import dev.chojo.ember.feature.system.route.DataTrackingRoutes;
import dev.chojo.ember.feature.system.route.DemoRoutes;
import dev.chojo.ember.feature.system.route.DevRoutes;
import dev.chojo.ember.feature.system.route.InstallRoutes;
import dev.chojo.ember.feature.system.route.ProblemReportRoutes;
import dev.chojo.ember.feature.system.route.ProblemRoutes;
import dev.chojo.ember.feature.system.route.PublicConfigRoutes;
import dev.chojo.ember.feature.system.route.RequirementsRoutes;
import dev.chojo.ember.feature.system.route.SidebarCountRoutes;
import dev.chojo.ember.feature.system.route.SitemapRoutes;
import dev.chojo.ember.feature.system.route.TaskStatusRoutes;
import dev.chojo.ember.feature.system.route.UpdateRoutes;
import dev.chojo.ember.feature.system.route.UtilRoutes;
import dev.chojo.ember.feature.system.service.ApiRequestLogger;
import dev.chojo.ember.feature.system.service.ApplicationLogWriter;
import dev.chojo.ember.feature.system.service.DemoAttendanceSeeder;
import dev.chojo.ember.feature.system.service.DemoAvatarSeeder;
import dev.chojo.ember.feature.system.service.DemoBoardSeeder;
import dev.chojo.ember.feature.system.service.DemoChecklistSeeder;
import dev.chojo.ember.feature.system.service.DemoClusterSeeder;
import dev.chojo.ember.feature.system.service.DemoEquipmentSeeder;
import dev.chojo.ember.feature.system.service.DemoEventSeeder;
import dev.chojo.ember.feature.system.service.DemoFederationSeeder;
import dev.chojo.ember.feature.system.service.DemoFormSeeder;
import dev.chojo.ember.feature.system.service.DemoFreshStationSeeder;
import dev.chojo.ember.feature.system.service.DemoInventorySeeder;
import dev.chojo.ember.feature.system.service.DemoKnowledgeBaseSeeder;
import dev.chojo.ember.feature.system.service.DemoLendingSeeder;
import dev.chojo.ember.feature.system.service.DemoLostAndFoundSeeder;
import dev.chojo.ember.feature.system.service.DemoMemberSeeder;
import dev.chojo.ember.feature.system.service.DemoMirrorStationSeeder;
import dev.chojo.ember.feature.system.service.DemoNewsSeeder;
import dev.chojo.ember.feature.system.service.DemoNotificationSeeder;
import dev.chojo.ember.feature.system.service.DemoPageSeeder;
import dev.chojo.ember.feature.system.service.DemoProcedureSeeder;
import dev.chojo.ember.feature.system.service.DemoProtocolSeeder;
import dev.chojo.ember.feature.system.service.DemoQuizSeeder;
import dev.chojo.ember.feature.system.service.DemoSeeder;
import dev.chojo.ember.feature.system.service.DemoSelfCheckSeeder;
import dev.chojo.ember.feature.system.service.DemoService;
import dev.chojo.ember.feature.system.service.DemoSessionSeeder;
import dev.chojo.ember.feature.system.service.DemoSettingsSeeder;
import dev.chojo.ember.feature.system.service.DemoSetupSeeder;
import dev.chojo.ember.feature.system.service.DemoStationSeeder;
import dev.chojo.ember.feature.system.service.DemoTwoFactorSeeder;
import dev.chojo.ember.feature.system.service.DemoVideoSeeder;
import dev.chojo.ember.feature.system.service.DemoWaitingListSeeder;
import dev.chojo.ember.feature.system.service.ProblemReportSweeper;
import dev.chojo.ember.feature.system.service.UpdateCheckService;
import dev.chojo.ember.feature.traffic.route.AdminTrafficRoutes;
import dev.chojo.ember.feature.traffic.route.StationTrafficRoutes;
import dev.chojo.ember.feature.traffic.service.StationTrafficRecorder;
import dev.chojo.ember.feature.twofactor.route.StepUpRoutes;
import dev.chojo.ember.feature.twofactor.route.TwoFactorAdminRoutes;
import dev.chojo.ember.feature.twofactor.route.TwoFactorRoutes;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.SecondFactorCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
import dev.chojo.ember.feature.waitinglist.handler.WaitlistInvitationAnsweredHandler;
import dev.chojo.ember.feature.waitinglist.handler.WaitlistPublicRegistrationHandler;
import dev.chojo.ember.feature.waitinglist.route.WaitingListRoutes;
import dev.chojo.ember.feature.waitinglist.service.WaitingListFeedDetails;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import dev.chojo.ember.lifecycle.ShutdownFlush;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import javax.sql.DataSource;

/**
 * Guice module that wires the entire Ember application.
 * Binds all route groups via multibinding, provides configuration objects,
 * sets up the database connection pool, runs SQL migrations, and configures SADU's query system.
 */
public class EmberModule extends AbstractModule {
    private static final Logger log = LoggerFactory.getLogger(EmberModule.class);

    /** How long a start waits for a database that is not answering before giving up on it. */
    private static final Duration DATABASE_WAIT = Duration.ofMinutes(2);

    /** The longest pause between two attempts, so a wait of minutes is not one long sleep. */
    private static final Duration MAX_DATABASE_PAUSE = Duration.ofSeconds(10);

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
        routesBinder.addBinding().to(FirstStationRoutes.class);
        routesBinder.addBinding().to(StationMemberRoutes.class);
        routesBinder.addBinding().to(StationMemberInviteRoutes.class);
        routesBinder.addBinding().to(AttendanceRoutes.class);
        routesBinder.addBinding().to(InventoryRoutes.class);
        routesBinder.addBinding().to(ProfileFieldRoutes.class);
        routesBinder.addBinding().to(MemberTableRoutes.class);
        routesBinder.addBinding().to(DocumentRoutes.class);
        routesBinder.addBinding().to(DocumentTemplateRoutes.class);
        routesBinder.addBinding().to(DocumentGenerationRoutes.class);
        routesBinder.addBinding().to(MailImportRoutes.class);
        routesBinder.addBinding().to(AdminMonitoringCountRoutes.class);
        bind(MailFilingService.MemberNaming.class).to(StationMemberNaming.class);
        bind(MemberEligibility.class).to(StationMemberEligibility.class);
        bind(CellDescriptions.PageAddressing.class).to(StationPageAddressing.class);
        Multibinder<BlockReferences> blockReferencesBinder = Multibinder.newSetBinder(binder(), BlockReferences.class);
        blockReferencesBinder.addBinding().to(NewsBlockReferences.class);
        blockReferencesBinder.addBinding().to(EventBlockReferences.class);
        routesBinder.addBinding().to(ProfileFieldChangeRoutes.class);
        routesBinder.addBinding().to(MemberGroupRoutes.class);
        routesBinder.addBinding().to(MemberGroupSetRoutes.class);
        routesBinder.addBinding().to(RegistrationCodeRoutes.class);
        routesBinder.addBinding().to(StationManageRoutes.class);
        routesBinder.addBinding().to(SetupRoutes.class);
        routesBinder.addBinding().to(EquipmentNeedRoutes.class);
        routesBinder.addBinding().to(EquipmentBrowseRoutes.class);
        routesBinder.addBinding().to(EventStructureRoutes.class);
        routesBinder.addBinding().to(EventAttachmentRoutes.class);
        routesBinder.addBinding().to(EventEmbedRoutes.class);
        routesBinder.addBinding().to(EventRegistrationRoutes.class);
        routesBinder.addBinding().to(EventSharingRoutes.class);
        routesBinder.addBinding().to(EventCancellationRoutes.class);
        routesBinder.addBinding().to(EventRoutes.class);
        routesBinder.addBinding().to(FederatedEventRoutes.class);
        routesBinder.addBinding().to(RemoteEventRoutes.class);
        routesBinder.addBinding().to(EventTemplateRoutes.class);
        routesBinder.addBinding().to(SavedFilterRoutes.class);
        routesBinder.addBinding().to(InventoryCheckRoutes.class);
        routesBinder.addBinding().to(SelfCheckRoutes.class);
        routesBinder.addBinding().to(SelfCheckReviewRoutes.class);
        routesBinder.addBinding().to(ManagedMemberRoutes.class);
        routesBinder.addBinding().to(StationApplicationRoutes.class);
        routesBinder.addBinding().to(StatisticsRoutes.class);
        routesBinder.addBinding().to(MemberImportRoutes.class);
        routesBinder.addBinding().to(NewsRoutes.class);
        routesBinder.addBinding().to(AdminNewsRoutes.class);
        routesBinder.addBinding().to(FederatedNewsRoutes.class);
        routesBinder.addBinding().to(RemoteNewsRoutes.class);
        routesBinder.addBinding().to(MediaRoutes.class);
        routesBinder.addBinding().to(PublicMediaRoutes.class);
        routesBinder.addBinding().to(PageRoutes.class);
        routesBinder.addBinding().to(PublicPageRoutes.class);
        routesBinder.addBinding().to(SharedPageRoutes.class);
        routesBinder.addBinding().to(MailWebhookRoutes.class);
        routesBinder.addBinding().to(UserSettingsRoutes.class);
        routesBinder.addBinding().to(MovementRoutes.class);
        routesBinder.addBinding().to(MovementFlowRoutes.class);
        install(new ClusterModule());
        routesBinder.addBinding().to(ProcurementRoutes.class);
        routesBinder.addBinding().to(InventoryContainerRoutes.class);
        routesBinder.addBinding().to(InventoryFieldDefinitionRoutes.class);
        routesBinder.addBinding().to(InventoryArtRoutes.class);
        routesBinder.addBinding().to(InventoryTagRoutes.class);
        routesBinder.addBinding().to(FederatedInventoryTagRoutes.class);
        routesBinder.addBinding().to(RemoteInventoryTagRoutes.class);
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
        routesBinder.addBinding().to(BeaconIntakeRoutes.class);
        routesBinder.addBinding().to(BeaconAdminRoutes.class);
        routesBinder.addBinding().to(ProblemReportRoutes.class);
        routesBinder.addBinding().to(ApiStatusRoutes.class);
        routesBinder.addBinding().to(DemoRoutes.class);
        routesBinder.addBinding().to(DevRoutes.class);
        routesBinder.addBinding().to(PublicConfigRoutes.class);
        routesBinder.addBinding().to(UpdateRoutes.class);
        routesBinder.addBinding().to(TaskStatusRoutes.class);
        routesBinder.addBinding().to(ChangelogRoutes.class);
        routesBinder.addBinding().to(WaitingListRoutes.class);
        routesBinder.addBinding().to(QuizCatalogRoutes.class);
        routesBinder.addBinding().to(QuizQuestionRoutes.class);
        routesBinder.addBinding().to(QuizTestRoutes.class);
        routesBinder.addBinding().to(QuizAttemptRoutes.class);
        routesBinder.addBinding().to(FederatedQuizRoutes.class);
        routesBinder.addBinding().to(RemoteQuizRoutes.class);
        routesBinder.addBinding().to(PublicQuizRoutes.class);
        routesBinder.addBinding().to(AiRoutes.class);
        routesBinder.addBinding().to(AccountAiCredentialRoutes.class);
        routesBinder.addBinding().to(KnowledgeBaseRoutes.class);
        routesBinder.addBinding().to(KnowledgeBaseAccessRoutes.class);
        routesBinder.addBinding().to(KnowledgeBaseTagRoutes.class);
        routesBinder.addBinding().to(KnowledgeBaseCommentRoutes.class);
        routesBinder.addBinding().to(KbFavouriteRoutes.class);
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
        routesBinder.addBinding().to(PairRequestRoutes.class);
        routesBinder.addBinding().to(LendingRoutes.class);
        routesBinder.addBinding().to(InventoryShareRoutes.class);
        routesBinder.addBinding().to(FederatedLendingRoutes.class);
        routesBinder.addBinding().to(RemoteLendingRoutes.class);
        routesBinder.addBinding().to(DiscoveryRoutes.class);
        routesBinder.addBinding().to(PublicDiscoveryRoutes.class);
        routesBinder.addBinding().to(RemoteStationLogoRoutes.class);
        routesBinder.addBinding().to(AdminDiscoveryRoutes.class);
        routesBinder.addBinding().to(PublicMapsRoutes.class);
        routesBinder.addBinding().to(AdminMapsRoutes.class);
        routesBinder.addBinding().to(FeedTokenRoutes.class);
        routesBinder.addBinding().to(StationFeedUseRoutes.class);
        routesBinder.addBinding().to(OnboardingRoutes.class);
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
        routesBinder.addBinding().to(StepUpRoutes.class);
        routesBinder.addBinding().to(PasskeyRoutes.class);
        routesBinder.addBinding().to(PasskeyAdminRoutes.class);
        routesBinder.addBinding().to(TwoFactorAdminRoutes.class);
        routesBinder.addBinding().to(AccountAdminRoutes.class);

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
        demoSeederBinder.addBinding().to(DemoEquipmentSeeder.class);
        demoSeederBinder.addBinding().to(DemoClusterSeeder.class);
        demoSeederBinder.addBinding().to(DemoFormSeeder.class);
        demoSeederBinder.addBinding().to(DemoSessionSeeder.class);
        demoSeederBinder.addBinding().to(DemoWaitingListSeeder.class);
        demoSeederBinder.addBinding().to(DemoQuizSeeder.class);
        demoSeederBinder.addBinding().to(DemoKnowledgeBaseSeeder.class);
        demoSeederBinder.addBinding().to(DemoProtocolSeeder.class);
        demoSeederBinder.addBinding().to(DemoProcedureSeeder.class);
        demoSeederBinder.addBinding().to(DemoSelfCheckSeeder.class);
        demoSeederBinder.addBinding().to(DemoAvatarSeeder.class);
        demoSeederBinder.addBinding().to(DemoFederationSeeder.class);
        demoSeederBinder.addBinding().to(DemoSettingsSeeder.class);
        demoSeederBinder.addBinding().to(DemoChecklistSeeder.class);
        demoSeederBinder.addBinding().to(DemoBoardSeeder.class);
        demoSeederBinder.addBinding().to(DemoPageSeeder.class);
        demoSeederBinder.addBinding().to(DemoLendingSeeder.class);
        demoSeederBinder.addBinding().to(DemoNotificationSeeder.class);
        demoSeederBinder.addBinding().to(DemoSetupSeeder.class);
        demoSeederBinder.addBinding().to(DemoFreshStationSeeder.class);
        demoSeederBinder.addBinding().to(DemoTwoFactorSeeder.class);
        demoSeederBinder.addBinding().to(DemoVideoSeeder.class);

        MapBinder<CommentEntityType, CommentTarget> commentTargets =
                MapBinder.newMapBinder(binder(), CommentEntityType.class, CommentTarget.class);
        commentTargets.addBinding(CommentEntityType.EVENT).to(EventCommentTarget.class);
        commentTargets.addBinding(CommentEntityType.KB).to(KbCommentTarget.class);
        commentTargets.addBinding(CommentEntityType.NEWS).to(NewsCommentTarget.class);
        commentTargets.addBinding(CommentEntityType.BOARD_TICKET).to(TicketCommentTarget.class);

        MapBinder<FieldOrigin, ProfileFieldOwner> profileFieldOwners =
                MapBinder.newMapBinder(binder(), FieldOrigin.class, ProfileFieldOwner.class);
        profileFieldOwners.addBinding(FieldOrigin.STATION).to(StationProfileFields.class);
        profileFieldOwners.addBinding(FieldOrigin.CLUSTER).to(AssociationProfileFields.class);

        Multibinder<DomainEventHandler<?>> eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<>() {});
        eventBinder.addBinding().to(EventCreatedHandler.class);
        eventBinder.addBinding().to(EventsBatchCreatedHandler.class);
        eventBinder.addBinding().to(EventDeletedHandler.class);
        eventBinder.addBinding().to(EventChangedHandler.class);
        eventBinder.addBinding().to(EventRegistrationStatusHandler.class);
        eventBinder.addBinding().to(NewsCreatedHandler.class);
        eventBinder.addBinding().to(ClusterNewsShareHandler.class);
        eventBinder.addBinding().to(ClusterEventShareHandler.class);
        eventBinder.addBinding().to(NewsDeletedHandler.class);
        eventBinder.addBinding().to(CommentCreatedHandler.class);
        eventBinder.addBinding().to(CommentDeletedHandler.class);
        eventBinder.addBinding().to(MovementStartedHandler.class);
        eventBinder.addBinding().to(MovementAdvancedHandler.class);
        eventBinder.addBinding().to(MovementDeclinedHandler.class);
        eventBinder.addBinding().to(MovementCancelledHandler.class);
        eventBinder.addBinding().to(ClusterApplicationSubmittedHandler.class);
        eventBinder.addBinding().to(ClusterApplicationWithdrawnHandler.class);
        eventBinder.addBinding().to(ClusterApplicationResolvedHandler.class);
        eventBinder.addBinding().to(ClusterStationReleasedHandler.class);
        eventBinder.addBinding().to(ClusterGovernanceHandler.class);
        eventBinder.addBinding().to(ClusterQuotaChangedHandler.class);
        eventBinder.addBinding().to(ClusterItemIssuedHandler.class);
        eventBinder.addBinding().to(ClusterItemLostHandler.class);
        eventBinder.addBinding().to(ClusterMemberRoleChangedHandler.class);
        eventBinder.addBinding().to(ClusterFieldValueChangedHandler.class);
        eventBinder.addBinding().to(FormPublishedHandler.class);
        eventBinder.addBinding().to(FormDeletedHandler.class);
        eventBinder.addBinding().to(ProcurementCreatedHandler.class);
        eventBinder.addBinding().to(ProcurementFulfilledHandler.class);
        eventBinder.addBinding().to(RegistrationDeadlineExpiredHandler.class);
        eventBinder.addBinding().to(MembersAddedToGroupHandler.class);
        eventBinder.addBinding().to(LendingRequestedHandler.class);
        eventBinder.addBinding().to(FederationRequestReceivedHandler.class);
        eventBinder.addBinding().to(FederationRequestAnsweredHandler.class);
        eventBinder.addBinding().to(LendingStatusChangedHandler.class);
        eventBinder.addBinding().to(LendingMessageSentHandler.class);
        eventBinder.addBinding().to(MentionedInCommentHandler.class);
        eventBinder.addBinding().to(BulkMentionedInCommentHandler.class);
        eventBinder.addBinding().to(BoardTicketChangedHandler.class);
        eventBinder.addBinding().to(EventCancelledHandler.class);
        eventBinder.addBinding().to(EventDateRestoredHandler.class);
        eventBinder.addBinding().to(EventAnswerRecordedHandler.class);
        eventBinder.addBinding().to(ProcedureAssignedHandler.class);
        eventBinder.addBinding().to(ProcedureResolvedHandler.class);
        eventBinder.addBinding().to(ProcedureReopenedHandler.class);
        eventBinder.addBinding().to(ProcedureItemCheckedHandler.class);
        eventBinder.addBinding().to(WaitlistPublicRegistrationHandler.class);
        eventBinder.addBinding().to(WaitlistInvitationAnsweredHandler.class);
        eventBinder.addBinding().to(StorageWarningHandler.class);

        bind(FederationTransport.class).to(RoutingFederationTransport.class);
        Multibinder<FederationServer> federationServers = Multibinder.newSetBinder(binder(), FederationServer.class);
        federationServers.addBinding().to(FederatedItemTagService.class);
        federationServers.addBinding().to(QuizFederationService.class);
        federationServers.addBinding().to(TestProtocolService.class);
        federationServers.addBinding().to(NewsFederationService.class);
        federationServers.addBinding().to(LendingService.class);
        federationServers.addBinding().to(EventFederationService.class);
        federationServers.addBinding().to(KnowledgeBaseFederationService.class);
        federationServers.addBinding().to(FederatedBoardDiscoveryService.class);
        federationServers.addBinding().to(FederatedBoardStructureProxy.class);
        federationServers.addBinding().to(FederatedTicketProxy.class);
        federationServers.addBinding().to(FederatedTicketDetailProxy.class);
        federationServers.addBinding().to(FederatedBoardNotificationService.class);

        Multibinder<FeedDetailsContributor> feedDetailsBinder =
                Multibinder.newSetBinder(binder(), FeedDetailsContributor.class);
        feedDetailsBinder.addBinding().to(NewsFeedDetails.class);
        feedDetailsBinder.addBinding().to(CommentFeedDetails.class);
        feedDetailsBinder.addBinding().to(EventFeedDetails.class);
        feedDetailsBinder.addBinding().to(MemberFeedDetails.class);
        feedDetailsBinder.addBinding().to(InventoryFeedDetails.class);
        feedDetailsBinder.addBinding().to(LostAndFoundFeedDetails.class);
        feedDetailsBinder.addBinding().to(LendingFeedDetails.class);
        feedDetailsBinder.addBinding().to(BoardFeedDetails.class);
        feedDetailsBinder.addBinding().to(StorageFeedDetails.class);
        feedDetailsBinder.addBinding().to(WaitingListFeedDetails.class);
        feedDetailsBinder.addBinding().to(FormFeedDetails.class);
        feedDetailsBinder.addBinding().to(ProcedureFeedDetails.class);

        Multibinder<TaskSource> taskSources = Multibinder.newSetBinder(binder(), TaskSource.class);
        taskSources.addBinding().to(PageHitRecorder.class);
        taskSources.addBinding().to(OutgoingPairRequestService.class);
        taskSources.addBinding().to(StationTrafficRecorder.class);
        taskSources.addBinding().to(ApiRequestLogger.class);
        taskSources.addBinding().to(ApplicationLogWriter.class);
        taskSources.addBinding().to(RegistrationDeadlineChecker.class);
        taskSources.addBinding().to(EventReminderChecker.class);
        taskSources.addBinding().to(EventThresholdChecker.class);
        taskSources.addBinding().to(FieldRegistrationSweeper.class);
        taskSources.addBinding().to(SettledRefusalSweeper.class);
        taskSources.addBinding().to(DueDateReminderChecker.class);
        taskSources.addBinding().to(ExpiryReminderService.class);
        taskSources.addBinding().to(ManagedLoginNoticeSweeper.class);
        taskSources.addBinding().to(WaitingListService.class);
        taskSources.addBinding().to(EmailService.class);
        taskSources.addBinding().to(MailWebhookService.class);
        taskSources.addBinding().to(NotificationDigest.class);
        taskSources.addBinding().to(MailImportPoller.class);
        taskSources.addBinding().to(AuthCleanupSweeper.class);
        taskSources.addBinding().to(ProblemReportSweeper.class);
        taskSources.addBinding().to(UpdateCheckService.class);
        taskSources.addBinding().to(DemoService.class);
        taskSources.addBinding().to(FederationPartnerSeeder.class);
        taskSources.addBinding().to(DiscoveryPingScheduler.class);
        taskSources.addBinding().to(DiscoveryStationRefreshScheduler.class);
        taskSources.addBinding().to(DiscoveryMaintenanceScheduler.class);
        taskSources.addBinding().to(FederationVersionBroadcaster.class);
        taskSources.addBinding().to(BeaconMetricsService.class);
        taskSources.addBinding().to(StorageReconciliationService.class);
        taskSources.addBinding().to(KbTrashPurger.class);
        taskSources.addBinding().to(TransferTimeoutWatchdog.class);
        taskSources.addBinding().to(FeedMetricsService.class);

        Multibinder<ShutdownFlush> flushes = Multibinder.newSetBinder(binder(), ShutdownFlush.class);
        flushes.addBinding().to(PageHitRecorder.class);
        flushes.addBinding().to(StationTrafficRecorder.class);
        flushes.addBinding().to(ApiRequestLogger.class);
        flushes.addBinding().to(FeedMetricsService.class);
        flushes.addBinding().to(ApplicationLogWriter.class);
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
    WebAuthnSettings webAuthnSettings(Auth auth) {
        return WebAuthnSettings.resolvedFrom(auth);
    }

    @Provides
    @Singleton
    PasskeySettings passkeySettings(Auth auth) {
        return auth.passkeys();
    }

    @Provides
    @Singleton
    RelyingParties webAuthnRelyingParties(
            WebAuthnSettings settings,
            Api api,
            WebAuthnCredentialStore fullStore,
            SecondFactorCredentialStore secondFactorStore) {
        return WebAuthnRelyingPartyFactory.build(settings, api, fullStore, secondFactorStore);
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
    KnowledgeBase knowledgeBase(File config) {
        return config.knowledgeBase();
    }

    @Provides
    @Singleton
    Attendance attendance(File config) {
        return config.attendance();
    }

    @Provides
    @Singleton
    MailImport mailImport(File config) {
        return config.mailImport();
    }

    @Provides
    @Singleton
    Updates updates(File config) {
        return config.updates();
    }

    @Provides
    @Singleton
    Changelog changelog(File config) {
        return config.changelog();
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
        return openPool(() -> DataSourceCreator.create(PostgreSql.get())
                .configure(config -> config.withConfig(database)
                        .currentSchema(database.schema())
                        .applicationName("Ember"))
                .create()
                .withMaximumPoolSize(database.poolSize())
                .withMinimumIdle(1)
                .build());
    }

    /**
     * Opens the pool, waiting for a database that is not answering yet.
     *
     * <p>The pool takes its first connection as it is built and dies where that is refused, which
     * is the whole start gone. A database is not always there when this one is: it is started
     * beside this and comes up in its own time, it is restarted under this while it runs, and it is
     * a machine on a network that has bad minutes. Each of those is over in seconds and none of
     * them is a reason to lose the process.
     *
     * <p>Bounded, because the other thing that refuses a connection is an address or a password
     * that is simply wrong, and an instance that hung on that forever would say less than one that
     * stops and shows the refusal. Every attempt is logged, so a wait is visible while it lasts.
     */
    private static DataSource openPool(Supplier<DataSource> open) {
        var giveUpAt = Instant.now().plus(DATABASE_WAIT);
        var pause = Duration.ofSeconds(1);
        while (true) {
            try {
                return open.get();
            } catch (RuntimeException e) {
                if (!Instant.now().plus(pause).isBefore(giveUpAt)) throw e;
                log.warn(
                        "The database is not answering yet, trying again in {}s: {}",
                        pause.toSeconds(),
                        e.getMessage());
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                pause = min(pause.multipliedBy(2), MAX_DATABASE_PAUSE);
            }
        }
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    /**
     * Migrates the schema and installs the thread-scoped query configuration as the default.
     *
     * <p>The migration is skipped only in full demo mode, which drops and migrates the schema on every
     * start anyway; everywhere else it must run before services whose constructors already query it.
     * Before 1.60 merges duplicate profile fields, their answers and definitions are copied to the data
     * volume, not to a table, because a table would travel with a station export. The configuration is
     * thread-scoped so that services grouping writes with {@code Transactions.run} reach their
     * repositories inside the same transaction.
     */
    @Provides
    @Singleton
    QueryConfiguration queryConfiguration(DataSource dataSource, Database database, Demo demo)
            throws SQLException, IOException {
        if (!demo.enabled()) {
            SqlUpdater.builder(dataSource, PostgreSql.get())
                    .setReplacements(new QueryReplacement("ember_schema", database.schema()))
                    .setSchemas(database.schema())
                    .preUpdateHook(
                            new SqlVersion(1, 60),
                            connection -> ProfileFieldMergeBackup.writeTo(connection, database.schema()))
                    .execute();
        }

        var config = QueryConfiguration.builder(dataSource)
                .setExceptionHandler(err -> log.error("Database query error", err))
                .setThrowExceptions(true)
                .setRowMapperRegistry(new RowMapperRegistry().register(PostgresqlMapper.getDefaultMapper()))
                .build();
        var scoped = Transactions.threadScoped(config);
        QueryConfiguration.setDefault(scoped);
        return scoped;
    }
}
