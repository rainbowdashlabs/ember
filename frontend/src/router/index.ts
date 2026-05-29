/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createRouter, createWebHistory} from 'vue-router'
import {getItem} from '@/api/storage'
import {useConsentGuard} from '@/composables/useConsentGuard'
import i18n from '@/i18n'

// Layout shells are eagerly loaded (they wrap all child routes)
import DashboardView from '@/views/DashboardView.vue'
import AdminView from '@/views/AdminView.vue'
import HelpCenterStationView from '@/views/HelpCenterStationView.vue'
import HelpCenterAdminView from '@/views/HelpCenterAdminView.vue'

// Everything else is lazy-loaded for smaller initial bundle
const LoginView = () => import('@/views/LoginView.vue')
const ForgotPasswordView = () => import('@/views/ForgotPasswordView.vue')
const SetPasswordView = () => import('@/views/SetPasswordView.vue')
const HomeView = () => import('@/views/HomeView.vue')
const StationSelectView = () => import('@/views/StationSelectView.vue')
const StyleView = () => import('@/views/StyleView.vue')
const OverviewView = () => import('@/views/dashboardview/OverviewView.vue')
const StatisticsView = () => import('@/views/dashboardview/StatisticsView.vue')
const StationManageView = () => import('@/views/stationview/manage/StationView.vue')
const MembersCreateView = () => import('@/views/stationview/members/CreateView.vue')
const MembersListView = () => import('@/views/stationview/members/ListView.vue')
const MembersGroupsView = () => import('@/views/stationview/members/GroupsView.vue')
const MemberEditView = () => import('@/views/stationview/members/EditView.vue')
const MemberDetailView = () => import('@/views/stationview/members/DetailView.vue')
const MembersConfigView = () => import('@/views/stationview/manage/MembersConfigView.vue')
const InventoryOverviewView = () => import('@/views/stationview/inventory/OverviewView.vue')
const InventoryManageView = () => import('@/views/stationview/inventory/ManageView.vue')
const EventsView = () => import('@/views/stationview/events/IndexView.vue')
const EventsUpcomingView = () => import('@/views/stationview/events/UpcomingView.vue')
const EventsRegistrationsView = () => import('@/views/stationview/events/RegistrationsView.vue')
const AttendanceNewView = () => import('@/views/stationview/attendance/NewView.vue')
const AttendancePastView = () => import('@/views/stationview/attendance/PastView.vue')
const AttendanceConfigView = () => import('@/views/stationview/manage/AttendanceConfigView.vue')
const AttendanceConfigEditView = () => import('@/views/stationview/manage/AttendanceConfigEditView.vue')
const ProfileView = () => import('@/views/stationview/profile/IndexView.vue')
const ProfileManagedView = () => import('@/views/stationview/profile/ManagedView.vue')
const AdminOverviewView = () => import('@/views/adminview/AdminOverviewView.vue')
const AdminStatisticsView = () => import('@/views/adminview/AdminStatisticsView.vue')
const AdminStationsView = () => import('@/views/adminview/AdminStationsView.vue')
const AdminStationEditView = () => import('@/views/adminview/AdminStationEditView.vue')

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/login',
            name: 'login',
            component: LoginView,
        },
        {
            path: '/requirements',
            name: 'requirements',
            component: () => import('@/views/PostLoginRequirementsView.vue'),
        },
        {
            path: '/forgot-password',
            name: 'forgot-password',
            component: ForgotPasswordView,
        },
        {
            path: '/set-password',
            name: 'set-password',
            component: SetPasswordView,
        },
        {
            path: '/reset-password',
            name: 'reset-password',
            component: SetPasswordView,
        },
        {
            path: '/apply',
            name: 'apply',
            component: () => import('@/views/ApplyView.vue'),
        },
        {
            path: '/apply/verify',
            name: 'apply-verify',
            component: () => import('@/views/ApplyVerifyView.vue'),
        },
        {
            path: '/waiting-list/register',
            name: 'waitlist-register',
            component: () => import('@/views/WaitingListRegisterView.vue'),
        },
        {
            path: '/waiting-list/status',
            name: 'waitlist-status',
            component: () => import('@/views/WaitingListStatusView.vue'),
        },
        {
            path: '/privacy',
            name: 'privacy',
            component: () => import('@/views/PrivacyPolicyView.vue'),
        },
        {
            path: '/terms',
            name: 'terms',
            component: () => import('@/views/TermsOfServiceView.vue'),
        },
        {
            path: '/reconsent',
            name: 'reconsent',
            component: () => import('@/views/ReconsentView.vue'),
        },
        {
            path: '/imprint',
            name: 'imprint',
            component: () => import('@/views/ImprintView.vue'),
        },
        {
            path: '/patch-notes',
            name: 'patch-notes',
            component: () => import('@/views/PatchNotesView.vue'),
        },
        {
            path: '/station-select',
            name: 'station-select',
            component: StationSelectView,
        },
        {
            path: '/',
            name: 'home',
            component: HomeView,
        },
        {
            path: '/station',
            component: DashboardView,
            children: [
                {
                    path: '',
                    redirect: {name: 'dashboard-overview'},
                },
                {
                    path: 'dashboard/overview',
                    name: 'dashboard-overview',
                    component: OverviewView,
                },
                {
                    path: 'dashboard/statistics',
                    name: 'dashboard-statistics',
                    component: StatisticsView,
                },
                {
                    path: 'news',
                    name: 'news-list',
                    component: () => import('@/views/stationview/news/ListView.vue'),
                },
                {
                    path: 'news/create',
                    name: 'news-create',
                    component: () => import('@/views/stationview/news/EditView.vue'),
                },
                {
                    path: 'news/:id',
                    name: 'news-detail',
                    component: () => import('@/views/stationview/news/DetailView.vue'),
                },
                {
                    path: 'news/:id/edit',
                    name: 'news-edit',
                    component: () => import('@/views/stationview/news/EditView.vue'),
                },
                {
                    path: 'manage',
                    name: 'station-manage',
                    component: StationManageView,
                },
                {
                    path: 'manage/theme',
                    name: 'station-theme',
                    component: () => import('@/views/stationview/manage/StationThemeView.vue'),
                },
                {
                    path: 'manage/mailing',
                    name: 'station-mailing',
                    component: () => import('@/views/stationview/manage/StationMailingView.vue'),
                },
                {
                    path: 'manage/modules',
                    name: 'station-modules',
                    component: () => import('@/views/stationview/manage/StationModulesView.vue'),
                },
                {
                    path: 'manage/import',
                    name: 'station-import',
                    component: () => import('@/views/stationview/manage/StationImportView.vue'),
                },
                {
                    path: 'manage/attendance-config',
                    name: 'station-attendance-config',
                    component: AttendanceConfigView,
                },
                {
                    path: 'manage/attendance-config/edit/:id?',
                    name: 'station-attendance-config-edit',
                    component: AttendanceConfigEditView,
                },
                {
                    path: 'manage/members-config',
                    name: 'station-members-config',
                    component: MembersConfigView,
                },
                {
                    path: 'manage/federation',
                    name: 'station-federation',
                    component: () => import('@/views/stationview/manage/FederationView.vue'),
                },
                {
                    path: 'manage/federation/settings',
                    name: 'station-federation-settings',
                    component: () => import('@/views/stationview/manage/FederationSettingsView.vue'),
                },
                {
                    path: 'manage/federation/:id',
                    name: 'station-federation-partner',
                    component: () => import('@/views/stationview/manage/FederationPartnerView.vue'),
                },
                {
                    path: 'manage/discovery',
                    name: 'station-discovery',
                    component: () => import('@/views/stationview/manage/DiscoveryView.vue'),
                },
                {
                    path: 'members/create',
                    name: 'members-create',
                    component: MembersCreateView,
                },
                {
                    path: 'members/list',
                    name: 'members-list',
                    component: MembersListView,
                },
                {
                    path: 'members/import',
                    name: 'members-import',
                    component: () => import('@/views/stationview/members/ImportView.vue'),
                },
                {
                    path: 'members/import-team',
                    name: 'members-import-team',
                    component: () => import('@/views/stationview/members/TeamImportView.vue'),
                },
                {
                    path: 'members/detail/:id',
                    name: 'members-detail',
                    component: MemberDetailView,
                },
                {
                    path: 'members/edit/:id',
                    name: 'members-edit',
                    component: MemberEditView,
                },
                {
                    path: 'members/groups',
                    name: 'members-groups',
                    component: MembersGroupsView,
                },
                {
                    path: 'members/tags',
                    name: 'members-tags',
                    component: () => import('@/views/stationview/members/TagsView.vue'),
                },
                {
                    path: 'members/former',
                    name: 'members-former',
                    component: () => import('@/views/stationview/members/FormerView.vue'),
                },
                {
                    path: 'members/changes',
                    name: 'members-changes',
                    component: () => import('@/views/stationview/members/ChangesView.vue'),
                },
                {
                    path: 'members/waiting-lists',
                    name: 'waiting-lists',
                    component: () => import('@/views/stationview/members/waitinglist/ListView.vue'),
                },
                {
                    path: 'members/waiting-lists/:id',
                    name: 'waiting-list-detail',
                    component: () => import('@/views/stationview/members/waitinglist/DetailView.vue'),
                },
                {
                    path: 'members/waiting-lists/:id/fields',
                    name: 'waiting-list-fields',
                    component: () => import('@/views/stationview/members/waitinglist/FieldEditorView.vue'),
                },
                {
                    path: 'members/waiting-lists/:id/entries/:entryId',
                    name: 'waiting-list-entry',
                    component: () => import('@/views/stationview/members/waitinglist/EntryDetailView.vue'),
                },
                {
                    path: 'inventory/overview',
                    name: 'inventory-overview',
                    component: InventoryOverviewView,
                },
                {
                    path: 'inventory/manage',
                    name: 'inventory-manage',
                    component: InventoryManageView,
                },
                {
                    path: 'inventory/edit/:id',
                    name: 'inventory-edit',
                    component: () => import('@/views/stationview/inventory/EditView.vue'),
                },
                {
                    path: 'inventory/detail/:id',
                    name: 'inventory-detail',
                    component: () => import('@/views/stationview/inventory/DetailView.vue'),
                },
                {
                    path: 'inventory/requirements',
                    name: 'inventory-requirements',
                    component: () => import('@/views/stationview/inventory/RequirementsView.vue'),
                },
                {
                    path: 'inventory/checks',
                    name: 'inventory-checks',
                    component: () => import('@/views/stationview/inventory/CheckOverviewView.vue'),
                },
                {
                    path: 'inventory/checks/:memberId',
                    name: 'inventory-check-member',
                    component: () => import('@/views/stationview/inventory/CheckMemberView.vue'),
                },
                {
                    path: 'inventory/checks/:memberId/result',
                    name: 'inventory-check-result',
                    component: () => import('@/views/stationview/inventory/CheckResultView.vue'),
                },
                {
                    path: 'inventory/exchanges',
                    name: 'inventory-exchanges',
                    component: () => import('@/views/stationview/inventory/ExchangeView.vue'),
                },
                {
                    path: 'inventory/procurement',
                    name: 'inventory-procurement',
                    component: () => import('@/views/stationview/inventory/ProcurementView.vue'),
                },
                {
                    path: 'inventory/lending',
                    name: 'inventory-lending',
                    component: () => import('@/views/stationview/inventory/LendingView.vue'),
                },
                {
                    path: 'inventory/lending/request/new',
                    name: 'inventory-lending-create',
                    component: () => import('@/views/stationview/inventory/LendingCreateRequestView.vue'),
                },
                {
                    path: 'inventory/lending/request/:id',
                    name: 'inventory-lending-request',
                    component: () => import('@/views/stationview/inventory/LendingRequestView.vue'),
                },
                {
                    path: 'inventory/lending/blocks',
                    name: 'inventory-lending-blocks',
                    component: () => import('@/views/stationview/inventory/LendingBlocksView.vue'),
                },
                {
                    path: 'inventory/lending/blocks/create',
                    name: 'inventory-lending-blocks-create',
                    component: () => import('@/views/stationview/inventory/LendingBlocksCreateView.vue'),
                },
                {
                    path: 'inventory/lending/browse',
                    name: 'inventory-lending-browse',
                    component: () => import('@/views/stationview/inventory/LendingBrowseView.vue'),
                },
                {
                    path: 'inventory/my',
                    name: 'inventory-my',
                    component: () => import('@/views/stationview/profile/InventoryView.vue'),
                },
                {
                    path: 'inventory/members',
                    name: 'inventory-members',
                    component: () => import('@/views/stationview/inventory/MemberListView.vue'),
                },
                {
                    path: 'inventory/member/:memberId',
                    name: 'inventory-member',
                    component: () => import('@/views/stationview/inventory/MemberInventoryView.vue'),
                },
                {
                    path: 'inventory/item/:id',
                    name: 'inventory-item-detail',
                    component: () => import('@/views/stationview/inventory/ItemDetailView.vue'),
                },
                {
                    path: 'events',
                    name: 'events',
                    component: EventsView,
                },
                {
                    path: 'events/upcoming',
                    name: 'events-upcoming',
                    component: EventsUpcomingView,
                },
                {
                    path: 'events/registrations',
                    name: 'events-registrations',
                    component: EventsRegistrationsView,
                },
                {
                    path: 'events/:id',
                    name: 'event-detail',
                    component: () => import('@/views/stationview/events/EventDetailView.vue'),
                },
                {
                    path: 'events/new',
                    name: 'event-new',
                    component: () => import('@/views/stationview/events/EventEditView.vue'),
                },
                {
                    path: 'events/:id/edit',
                    name: 'event-edit',
                    component: () => import('@/views/stationview/events/EventEditView.vue'),
                },
                {
                    path: 'events/categories',
                    name: 'event-categories',
                    component: () => import('@/views/stationview/events/CategoryManageView.vue'),
                },
                {
                    path: 'events/templates',
                    name: 'event-templates',
                    component: () => import('@/views/stationview/events/TemplateManageView.vue'),
                },
                {
                    path: 'events/templates/:id',
                    name: 'event-template-edit',
                    component: () => import('@/views/stationview/events/TemplateEditView.vue'),
                },
                {
                    path: 'events/batch',
                    name: 'event-batch',
                    component: () => import('@/views/stationview/events/BatchCreateView.vue'),
                },
                {
                    path: 'forms',
                    name: 'forms-list',
                    component: () => import('@/views/stationview/forms/ListView.vue'),
                },
                {
                    path: 'forms/create',
                    name: 'forms-create',
                    component: () => import('@/views/stationview/forms/BuilderView.vue'),
                },
                {
                    path: 'forms/:id/edit',
                    name: 'forms-edit',
                    component: () => import('@/views/stationview/forms/BuilderView.vue'),
                },
                {
                    path: 'forms/:id/fill',
                    name: 'forms-fill',
                    component: () => import('@/views/stationview/forms/FillView.vue'),
                },
                {
                    path: 'forms/:id/analytics',
                    name: 'forms-analytics',
                    component: () => import('@/views/stationview/forms/AnalyticsView.vue'),
                },
                {
                    path: 'lost-and-found',
                    name: 'lost-and-found',
                    component: () => import('@/views/stationview/lostandfound/ListView.vue'),
                },
                {
                    path: 'profile',
                    name: 'profile',
                    component: ProfileView,
                },
                {
                    path: 'profile/managed',
                    name: 'profile-managed',
                    component: ProfileManagedView,
                },
                {
                    path: 'profile/absences',
                    name: 'profile-absences',
                    component: () => import('@/views/stationview/profile/AbsenceView.vue'),
                },
                {
                    path: 'profile/inventory',
                    name: 'profile-inventory',
                    component: () => import('@/views/stationview/profile/InventoryView.vue'),
                },
                {
                    path: 'profile/settings',
                    name: 'profile-settings',
                    redirect: { name: 'profile-theming' },
                },
                {
                    path: 'profile/settings/theming',
                    name: 'profile-theming',
                    component: () => import('@/views/stationview/profile/ThemingView.vue'),
                },
                {
                    path: 'profile/settings/sessions',
                    name: 'profile-sessions',
                    component: () => import('@/views/stationview/profile/SessionsView.vue'),
                },
                {
                    path: 'profile/settings/notifications',
                    name: 'profile-notifications',
                    component: () => import('@/views/stationview/profile/NotificationsView.vue'),
                },
                {
                    path: 'attendance/new',
                    name: 'attendance-new',
                    component: AttendanceNewView,
                },
                {
                    path: 'attendance/session/:id',
                    name: 'attendance-session',
                    component: () => import('@/views/stationview/attendance/SessionView.vue'),
                },
                {
                    path: 'attendance/past',
                    name: 'attendance-past',
                    component: AttendancePastView,
                },
                {
                    path: 'attendance/report',
                    name: 'attendance-report',
                    component: () => import('@/views/stationview/attendance/ReportView.vue'),
                },
                // Knowledge Base
                {
                    path: 'knowledge',
                    name: 'kb-browse',
                    component: () => import('@/views/stationview/knowledge/KnowledgeBaseView.vue'),
                },
                {
                    path: 'knowledge/file/:id',
                    name: 'kb-file',
                    component: () => import('@/views/stationview/knowledge/KbFileView.vue'),
                },
                {
                    path: 'knowledge/file/:id/versions',
                    name: 'kb-versions',
                    component: () => import('@/views/stationview/knowledge/KbVersionsView.vue'),
                },
                // Quiz
                {
                    path: 'quiz/catalogs',
                    name: 'quiz-catalogs',
                    component: () => import('@/views/stationview/quiz/CatalogListView.vue'),
                },
                {
                    path: 'quiz/catalogs/:id',
                    name: 'quiz-catalog-detail',
                    component: () => import('@/views/stationview/quiz/CatalogDetailView.vue'),
                },
                {
                    path: 'quiz/catalogs/:id/generate',
                    name: 'quiz-catalog-generate',
                    component: () => import('@/views/stationview/quiz/CatalogGenerateView.vue'),
                },
                {
                    path: 'quiz/catalogs/:id/mc-fill',
                    name: 'quiz-catalog-mc-fill',
                    component: () => import('@/views/stationview/quiz/McFillView.vue'),
                },
                {
                    path: 'quiz/catalogs/:id/import',
                    name: 'quiz-catalog-import',
                    component: () => import('@/views/stationview/quiz/CsvImportView.vue'),
                },
                {
                    path: 'quiz/tests',
                    name: 'quiz-tests',
                    component: () => import('@/views/stationview/quiz/TestListView.vue'),
                },
                {
                    path: 'quiz/tests/create',
                    name: 'quiz-test-create',
                    component: () => import('@/views/stationview/quiz/TestBuilderView.vue'),
                },
                {
                    path: 'quiz/tests/:id',
                    name: 'quiz-test-detail',
                    component: () => import('@/views/stationview/quiz/TestDetailView.vue'),
                },
                {
                    path: 'quiz/tests/:id/edit',
                    name: 'quiz-test-edit',
                    component: () => import('@/views/stationview/quiz/TestBuilderView.vue'),
                },
                {
                    path: 'quiz/tests/:id/take',
                    name: 'quiz-test-take',
                    component: () => import('@/views/stationview/quiz/TestTakeView.vue'),
                },
                {
                    path: 'quiz/tests/:id/evaluate/:attemptId',
                    name: 'quiz-test-evaluate',
                    component: () => import('@/views/stationview/quiz/TestEvaluateView.vue'),
                },
                {
                    path: 'quiz/training',
                    name: 'quiz-training',
                    component: () => import('@/views/stationview/quiz/TrainingView.vue'),
                },
                // Test Protocols
                {
                    path: 'protocols',
                    name: 'protocol-list',
                    component: () => import('@/views/stationview/protocol/ProtocolListView.vue'),
                },
                {
                    path: 'protocols/:id',
                    name: 'protocol-detail',
                    component: () => import('@/views/stationview/protocol/ProtocolDetailView.vue'),
                },
                {
                    path: 'protocols/runs',
                    name: 'protocol-run-list',
                    component: () => import('@/views/stationview/protocol/RunListView.vue'),
                },
                {
                    path: 'protocols/runs/:id',
                    name: 'protocol-run-detail',
                    component: () => import('@/views/stationview/protocol/RunDetailView.vue'),
                },
                {
                    path: 'protocols/runs/:id/grade/:memberId',
                    name: 'protocol-grade',
                    component: () => import('@/views/stationview/protocol/GradingView.vue'),
                },
                {
                    path: 'protocols/runs/:id/evaluation',
                    name: 'protocol-evaluation',
                    component: () => import('@/views/stationview/protocol/EvaluationView.vue'),
                },
                // -- Federated Boards --
                {
                    path: 'federation/boards',
                    name: 'federated-boards',
                    component: () => import('@/views/stationview/federation/FederatedBoardsView.vue'),
                },
                {
                    path: 'federation/boards/:partnerUid/:boardKey',
                    name: 'federated-board-view',
                    component: () => import('@/views/stationview/federation/FederatedBoardView.vue'),
                },
                {
                    path: 'federation/boards/:partnerUid/:boardKey/tickets/:ticketNumber',
                    name: 'federated-ticket-detail',
                    component: () => import('@/views/stationview/boards/TicketDetailView.vue'),
                },
                // -- Boards --
                {
                    path: 'boards',
                    name: 'board-overview',
                    component: () => import('@/views/stationview/boards/BoardOverviewView.vue'),
                },
                {
                    path: 'boards/manage',
                    name: 'board-manage',
                    component: () => import('@/views/stationview/boards/BoardListView.vue'),
                },
                {
                    path: 'boards/:boardKey',
                    name: 'board-view',
                    component: () => import('@/views/stationview/boards/BoardView.vue'),
                },
                {
                    path: 'boards/:boardKey/tickets/new',
                    name: 'ticket-create',
                    component: () => import('@/views/stationview/boards/TicketCreateView.vue'),
                },
                {
                    path: 'boards/:boardKey/tickets/:ticketNumber',
                    name: 'ticket-detail',
                    component: () => import('@/views/stationview/boards/TicketDetailView.vue'),
                },
                {
                    path: 'boards/:boardKey/archived',
                    name: 'board-archived',
                    component: () => import('@/views/stationview/boards/ArchivedView.vue'),
                },
                {
                    path: 'boards/:boardKey/backlog',
                    name: 'board-backlog',
                    component: () => import('@/views/stationview/boards/BacklogView.vue'),
                },
                {
                    path: 'boards/:boardKey/settings',
                    name: 'board-settings',
                    component: () => import('@/views/stationview/boards/BoardSettingsView.vue'),
                },
                {
                    path: ':pathMatch(.*)*',
                    name: 'station-not-found',
                    component: () => import('@/components/feedback/NotFoundContent.vue'),
                },
            ],
        },
        {
            path: '/admin',
            component: AdminView,
            children: [
                {
                    path: '',
                    redirect: {name: 'admin-overview'},
                },
                {
                    path: 'dashboard/overview',
                    name: 'admin-overview',
                    component: AdminOverviewView,
                },
                {
                    path: 'dashboard/statistics',
                    name: 'admin-statistics',
                    component: AdminStatisticsView,
                },
                {
                    path: 'stations',
                    name: 'admin-stations',
                    component: AdminStationsView,
                },
                {
                    path: 'stations/edit/:id?',
                    name: 'admin-station-edit',
                    component: AdminStationEditView,
                },
                {
                    path: 'stations/applications',
                    name: 'admin-station-applications',
                    component: () => import('@/views/adminview/AdminApplicationsView.vue'),
                },
                {
                    path: 'settings',
                    name: 'admin-settings',
                    component: () => import('@/views/adminview/AdminSettingsView.vue'),
                },
                {
                    path: 'settings/legal',
                    name: 'admin-legal',
                    component: () => import('@/views/adminview/AdminLegalView.vue'),
                },
                {
                    path: 'settings/mailing',
                    name: 'admin-mailing',
                    component: () => import('@/views/adminview/AdminMailingView.vue'),
                },
                {
                    path: 'problems',
                    name: 'admin-problems',
                    component: () => import('@/views/adminview/AdminProblemsView.vue'),
                },
                {
                    path: 'problem-reports',
                    name: 'admin-problem-reports',
                    component: () => import('@/views/adminview/AdminProblemReportsView.vue'),
                },
                {
                    path: 'api-status',
                    name: 'admin-api-status',
                    component: () => import('@/views/adminview/AdminApiStatusView.vue'),
                },
                {
                    path: 'api-status/detail',
                    name: 'admin-api-status-detail',
                    component: () => import('@/views/adminview/AdminApiStatusDetailView.vue'),
                },
            ],
        },
        {
            path: '/helpcenter/station',
            component: HelpCenterStationView,
            children: [
                {path: '', redirect: {name: 'help-welcome'}},
                // Basics
                {
                    path: 'basics',
                    name: 'help-welcome',
                    component: () => import('@/views/helpcenter/basics/WelcomeHelp.vue')
                },
                {
                    path: 'basics/overview',
                    name: 'help-basics-overview',
                    component: () => import('@/views/helpcenter/basics/OverviewHelp.vue')
                },
                {
                    path: 'basics/roles',
                    name: 'help-basics-roles',
                    component: () => import('@/views/helpcenter/basics/RolesHelp.vue')
                },
                {
                    path: 'basics/modules',
                    name: 'help-basics-modules',
                    component: () => import('@/views/helpcenter/basics/ModulesHelp.vue')
                },
                {
                    path: 'basics/hosting',
                    name: 'help-basics-hosting',
                    component: () => import('@/views/helpcenter/basics/HostingHelp.vue')
                },
                {
                    path: 'basics/federation',
                    name: 'help-basics-federation',
                    component: () => import('@/views/helpcenter/basics/FederationHelp.vue')
                },
                // Dashboard
                {
                    path: 'dashboard',
                    name: 'help-dashboard-module-overview',
                    component: () => import('@/views/helpcenter/dashboardview/ModuleOverviewHelp.vue')
                },
                {
                    path: 'dashboard/overview',
                    name: 'help-dashboard-overview',
                    component: () => import('@/views/helpcenter/dashboardview/OverviewHelp.vue')
                },
                {
                    path: 'dashboard/statistics',
                    name: 'help-dashboard-statistics',
                    component: () => import('@/views/helpcenter/dashboardview/StatisticsHelp.vue')
                },
                // News
                {
                    path: 'news',
                    name: 'help-news-module-overview',
                    component: () => import('@/views/helpcenter/stationview/news/OverviewHelp.vue')
                },
                {
                    path: 'news/:id',
                    name: 'help-news-detail',
                    redirect: { name: 'help-news-module-overview' },
                },
                {
                    path: 'news/create',
                    name: 'help-news-create',
                    redirect: { name: 'help-news-edit' },
                },
                {
                    path: 'news/:id/edit',
                    name: 'help-news-edit',
                    component: () => import('@/views/helpcenter/stationview/news/EditHelp.vue')
                },
                // Profile
                {
                    path: 'profile',
                    name: 'help-profile-module-overview',
                    component: () => import('@/views/helpcenter/stationview/profile/OverviewHelp.vue')
                },
                {
                    path: 'profile/absences',
                    name: 'help-profile-absences',
                    component: () => import('@/views/helpcenter/stationview/profile/AbsenceHelp.vue')
                },
                {
                    path: 'profile/managed',
                    name: 'help-profile-managed',
                    component: () => import('@/views/helpcenter/stationview/profile/ManagedHelp.vue')
                },
                {
                    path: 'profile/inventory',
                    name: 'help-profile-inventory',
                    component: () => import('@/views/helpcenter/stationview/profile/InventoryHelp.vue')
                },
                {
                    path: 'profile/settings',
                    name: 'help-profile-settings',
                    component: () => import('@/views/helpcenter/stationview/profile/SettingsHelp.vue')
                },
                {
                    path: 'profile/settings/theming',
                    name: 'help-profile-theming',
                    component: () => import('@/views/helpcenter/stationview/profile/ThemingHelp.vue')
                },
                {
                    path: 'profile/settings/sessions',
                    name: 'help-profile-sessions',
                    component: () => import('@/views/helpcenter/stationview/profile/SessionsHelp.vue')
                },
                {
                    path: 'profile/settings/notifications',
                    name: 'help-profile-notifications',
                    component: () => import('@/views/helpcenter/stationview/profile/NotificationsHelp.vue')
                },
                {
                    path: 'profile/feeds/rss',
                    name: 'help-profile-rss-feed',
                    component: () => import('@/views/helpcenter/stationview/profile/RssFeedHelp.vue')
                },
                {
                    path: 'profile/feeds/ical',
                    name: 'help-profile-ical-feed',
                    component: () => import('@/views/helpcenter/stationview/profile/IcalFeedHelp.vue')
                },
                {
                    path: 'profile/theme',
                    name: 'help-profile-theme',
                    component: () => import('@/views/helpcenter/stationview/profile/ThemeHelp.vue')
                },
                // Station management
                {
                    path: 'manage',
                    name: 'help-manage-module-overview',
                    component: () => import('@/views/helpcenter/stationview/manage/OverviewHelp.vue')
                },
                {
                    path: 'manage/attendance-config',
                    name: 'help-station-attendance-config',
                    component: () => import('@/views/helpcenter/stationview/manage/AttendanceConfigHelp.vue')
                },
                {
                    path: 'manage/attendance-config/edit/:id?',
                    name: 'help-station-attendance-config-edit',
                    component: () => import('@/views/helpcenter/stationview/manage/AttendanceConfigEditHelp.vue')
                },
                {
                    path: 'manage/members-config',
                    name: 'help-station-members-config',
                    component: () => import('@/views/helpcenter/stationview/manage/MembersConfigHelp.vue')
                },
                {
                    path: 'manage/mail-config',
                    name: 'help-station-mail-config',
                    redirect: { name: 'help-station-mailing' },
                },
                {
                    path: 'manage/theme',
                    name: 'help-station-theme',
                    component: () => import('@/views/helpcenter/stationview/manage/ThemeManageHelp.vue')
                },
                {
                    path: 'manage/mailing',
                    name: 'help-station-mailing',
                    component: () => import('@/views/helpcenter/stationview/manage/MailConfigHelp.vue')
                },
                {
                    path: 'manage/modules',
                    name: 'help-station-modules',
                    component: () => import('@/views/helpcenter/stationview/manage/ModulesHelp.vue')
                },
                {
                    path: 'manage/import',
                    name: 'help-station-import',
                    component: () => import('@/views/helpcenter/stationview/manage/ImportHelp.vue')
                },
                {
                    path: 'manage/federation',
                    name: 'help-station-federation',
                    component: () => import('@/views/helpcenter/stationview/manage/FederationHelp.vue')
                },
                {
                    path: 'manage/federation/settings',
                    name: 'help-station-federation-settings',
                    component: () => import('@/views/helpcenter/stationview/manage/FederationSettingsHelp.vue')
                },
                {
                    path: 'manage/federation/:id',
                    name: 'help-station-federation-partner',
                    component: () => import('@/views/helpcenter/stationview/manage/FederationPartnerHelp.vue')
                },
                {
                    path: 'manage/discovery',
                    name: 'help-station-discovery',
                    component: () => import('@/views/helpcenter/stationview/manage/DiscoveryHelp.vue')
                },
                // Members
                {
                    path: 'members',
                    name: 'help-members-module-overview',
                    component: () => import('@/views/helpcenter/stationview/members/OverviewHelp.vue')
                },
                {
                    path: 'members/create',
                    name: 'help-members-create',
                    component: () => import('@/views/helpcenter/stationview/members/CreateHelp.vue')
                },
                {
                    path: 'members/list',
                    name: 'help-members-list',
                    component: () => import('@/views/helpcenter/stationview/members/ListHelp.vue')
                },
                {
                    path: 'members/import',
                    name: 'help-members-import',
                    component: () => import('@/views/helpcenter/stationview/members/ImportHelp.vue')
                },
                {
                    path: 'members/import-team',
                    name: 'help-members-import-team',
                    component: () => import('@/views/helpcenter/stationview/members/TeamImportHelp.vue')
                },
                {
                    path: 'members/detail/:id?',
                    name: 'help-members-detail',
                    component: () => import('@/views/helpcenter/stationview/members/DetailHelp.vue')
                },
                {
                    path: 'members/edit/:id?',
                    name: 'help-members-edit',
                    component: () => import('@/views/helpcenter/stationview/members/EditHelp.vue')
                },
                {
                    path: 'members/groups',
                    name: 'help-members-groups',
                    component: () => import('@/views/helpcenter/stationview/members/GroupsHelp.vue')
                },
                {
                    path: 'members/tags',
                    name: 'help-members-tags',
                    component: () => import('@/views/helpcenter/stationview/members/TagsHelp.vue')
                },
                {
                    path: 'members/changes',
                    name: 'help-members-changes',
                    component: () => import('@/views/helpcenter/stationview/members/ChangesHelp.vue')
                },
                {
                    path: 'members/former',
                    name: 'help-members-former',
                    component: () => import('@/views/helpcenter/stationview/members/FormerHelp.vue')
                },
                {
                    path: 'members/waiting-lists',
                    name: 'help-waiting-lists',
                    component: () => import('@/views/helpcenter/stationview/members/WaitingListHelp.vue')
                },
                // Inventory
                {
                    path: 'inventory',
                    name: 'help-inventory-module-overview',
                    component: () => import('@/views/helpcenter/stationview/inventory/ModuleOverviewHelp.vue')
                },
                {
                    path: 'inventory/overview',
                    name: 'help-inventory-overview',
                    component: () => import('@/views/helpcenter/stationview/inventory/OverviewHelp.vue')
                },
                {
                    path: 'inventory/my',
                    name: 'help-inventory-my',
                    redirect: { name: 'help-profile-inventory' },
                },
                {
                    path: 'inventory/exchanges',
                    name: 'help-inventory-exchanges',
                    component: () => import('@/views/helpcenter/stationview/inventory/ExchangeHelp.vue')
                },
                {
                    path: 'inventory/members',
                    name: 'help-inventory-members',
                    component: () => import('@/views/helpcenter/stationview/inventory/MemberListHelp.vue')
                },
                {
                    path: 'inventory/item/:id',
                    name: 'help-inventory-item-detail',
                    redirect: { name: 'help-inventory-detail' },
                },
                {
                    path: 'inventory/detail/:id?',
                    name: 'help-inventory-detail',
                    component: () => import('@/views/helpcenter/stationview/inventory/DetailHelp.vue')
                },
                {
                    path: 'inventory/edit/:id?',
                    name: 'help-inventory-edit',
                    component: () => import('@/views/helpcenter/stationview/inventory/EditHelp.vue')
                },
                {
                    path: 'inventory/member/:memberId?',
                    name: 'help-inventory-member',
                    component: () => import('@/views/helpcenter/stationview/inventory/MemberInventoryHelp.vue')
                },
                {
                    path: 'inventory/manage',
                    name: 'help-inventory-manage',
                    component: () => import('@/views/helpcenter/stationview/inventory/ManageHelp.vue')
                },
                {
                    path: 'inventory/requirements',
                    name: 'help-inventory-requirements',
                    component: () => import('@/views/helpcenter/stationview/inventory/RequirementsHelp.vue')
                },
                {
                    path: 'inventory/checks',
                    name: 'help-inventory-checks',
                    component: () => import('@/views/helpcenter/stationview/inventory/CheckOverviewHelp.vue')
                },
                {
                    path: 'inventory/checks/:memberId',
                    name: 'help-inventory-check-member',
                    component: () => import('@/views/helpcenter/stationview/inventory/CheckMemberHelp.vue')
                },
                {
                    path: 'inventory/checks/:memberId/result',
                    name: 'help-inventory-check-result',
                    component: () => import('@/views/helpcenter/stationview/inventory/CheckResultHelp.vue')
                },
                {
                    path: 'inventory/procurement',
                    name: 'help-inventory-procurement',
                    component: () => import('@/views/helpcenter/stationview/inventory/ProcurementHelp.vue')
                },
                {
                    path: 'inventory/lending',
                    name: 'help-inventory-lending',
                    component: () => import('@/views/helpcenter/stationview/inventory/LendingHelp.vue')
                },
                {
                    path: 'inventory/lending/blocks',
                    name: 'help-inventory-lending-blocks',
                    component: () => import('@/views/helpcenter/stationview/inventory/LendingBlocksHelp.vue')
                },
                {
                    path: 'inventory/lending/blocks/create',
                    name: 'help-inventory-lending-blocks-create',
                    component: () => import('@/views/helpcenter/stationview/inventory/LendingBlocksCreateHelp.vue')
                },
                {
                    path: 'inventory/lending/browse',
                    name: 'help-inventory-lending-browse',
                    component: () => import('@/views/helpcenter/stationview/inventory/LendingBrowseHelp.vue')
                },
                {
                    path: 'inventory/lending/request/:id',
                    name: 'help-inventory-lending-request',
                    component: () => import('@/views/helpcenter/stationview/inventory/LendingRequestHelp.vue')
                },
                {
                    path: 'inventory/lending/request/new',
                    name: 'help-inventory-lending-create',
                    component: () => import('@/views/helpcenter/stationview/inventory/LendingCreateHelp.vue')
                },
                // Attendance
                {
                    path: 'attendance',
                    name: 'help-attendance-module-overview',
                    component: () => import('@/views/helpcenter/stationview/attendance/OverviewHelp.vue')
                },
                {
                    path: 'attendance/new',
                    name: 'help-attendance-new',
                    component: () => import('@/views/helpcenter/stationview/attendance/NewHelp.vue')
                },
                {
                    path: 'attendance/past',
                    name: 'help-attendance-past',
                    component: () => import('@/views/helpcenter/stationview/attendance/PastHelp.vue')
                },
                {
                    path: 'attendance/session/:id?',
                    name: 'help-attendance-session',
                    component: () => import('@/views/helpcenter/stationview/attendance/SessionHelp.vue')
                },
                {
                    path: 'attendance/report',
                    name: 'help-attendance-report',
                    component: () => import('@/views/helpcenter/stationview/attendance/ReportHelp.vue')
                },
                // Events
                {
                    path: 'events',
                    name: 'help-events-module-overview',
                    component: () => import('@/views/helpcenter/stationview/events/OverviewHelp.vue')
                },
                {
                    path: 'events/upcoming',
                    name: 'help-events-upcoming',
                    component: () => import('@/views/helpcenter/stationview/events/UpcomingHelp.vue')
                },
                {
                    path: 'events/registrations',
                    name: 'help-events-registrations',
                    component: () => import('@/views/helpcenter/stationview/events/RegistrationsHelp.vue')
                },
                {
                    path: 'events',
                    name: 'help-events',
                    component: () => import('@/views/helpcenter/stationview/events/IndexHelp.vue')
                },
                {
                    path: 'events/new',
                    name: 'help-event-new',
                    redirect: { name: 'help-event-edit' },
                },
                {
                    path: 'events/:id/edit',
                    name: 'help-event-edit',
                    component: () => import('@/views/helpcenter/stationview/events/EventEditHelp.vue')
                },
                {
                    path: 'events/:id',
                    name: 'help-event-detail',
                    component: () => import('@/views/helpcenter/stationview/events/EventDetailHelp.vue')
                },
                {
                    path: 'events/categories',
                    name: 'help-event-categories',
                    component: () => import('@/views/helpcenter/stationview/events/CategoriesHelp.vue')
                },
                {
                    path: 'events/templates',
                    name: 'help-event-templates',
                    redirect: { name: 'help-event-edit' },
                },
                {
                    path: 'events/templates/:id',
                    name: 'help-event-template-edit',
                    redirect: { name: 'help-event-templates' },
                },
                {
                    path: 'events/batch',
                    name: 'help-event-batch',
                    component: () => import('@/views/helpcenter/stationview/events/BatchHelp.vue')
                },
                // Forms
                {
                    path: 'forms',
                    name: 'help-forms-module-overview',
                    component: () => import('@/views/helpcenter/stationview/forms/OverviewHelp.vue')
                },
                {
                    path: 'forms/create',
                    name: 'help-forms-create',
                    component: () => import('@/views/helpcenter/stationview/forms/BuilderHelp.vue')
                },
                {
                    path: 'forms/:id/edit',
                    name: 'help-forms-edit',
                    component: () => import('@/views/helpcenter/stationview/forms/EditHelp.vue')
                },
                {
                    path: 'forms/:id/fill',
                    name: 'help-forms-fill',
                    component: () => import('@/views/helpcenter/stationview/forms/FillHelp.vue')
                },
                {
                    path: 'forms/:id/analytics',
                    name: 'help-forms-analytics',
                    component: () => import('@/views/helpcenter/stationview/forms/AnalyticsHelp.vue')
                },
                // Lost and Found
                {
                    path: 'lost-and-found',
                    name: 'help-lost-and-found',
                    component: () => import('@/views/helpcenter/stationview/lostandfound/ListHelp.vue')
                },
                // Quiz
                {
                    path: 'quiz',
                    name: 'help-quiz-module-overview',
                    component: () => import('@/views/helpcenter/stationview/quiz/OverviewHelp.vue')
                },
                {
                    path: 'quiz/catalogs',
                    name: 'help-quiz-catalogs',
                    component: () => import('@/views/helpcenter/stationview/quiz/CatalogListHelp.vue')
                },
                {
                    path: 'quiz/catalogs/:id',
                    name: 'help-quiz-catalog-detail',
                    component: () => import('@/views/helpcenter/stationview/quiz/CatalogDetailHelp.vue')
                },
                {
                    path: 'quiz/ai',
                    name: 'help-quiz-ai',
                    component: () => import('@/views/helpcenter/stationview/quiz/AiGenerationHelp.vue')
                },
                {
                    path: 'quiz/tests',
                    name: 'help-quiz-tests',
                    component: () => import('@/views/helpcenter/stationview/quiz/TestListHelp.vue')
                },
                {
                    path: 'quiz/tests/:id',
                    name: 'help-quiz-test-detail',
                    component: () => import('@/views/helpcenter/stationview/quiz/TestDetailHelp.vue')
                },
                {
                    path: 'quiz/training',
                    name: 'help-quiz-training',
                    component: () => import('@/views/helpcenter/stationview/quiz/TrainingHelp.vue')
                },
                {
                    path: 'quiz/catalogs/:id/generate',
                    name: 'help-quiz-catalog-generate',
                    component: () => import('@/views/helpcenter/stationview/quiz/CatalogGenerateHelp.vue')
                },
                {
                    path: 'quiz/catalogs/:id/mc-fill',
                    name: 'help-quiz-catalog-mc-fill',
                    redirect: { name: 'help-quiz-catalog-generate' },
                },
                {
                    path: 'quiz/catalogs/:id/import',
                    name: 'help-quiz-catalog-import',
                    component: () => import('@/views/helpcenter/stationview/quiz/CatalogImportHelp.vue')
                },
                {
                    path: 'quiz/tests/create',
                    name: 'help-quiz-test-create',
                    component: () => import('@/views/helpcenter/stationview/quiz/TestCreateHelp.vue')
                },
                {
                    path: 'quiz/tests/:id/edit',
                    name: 'help-quiz-test-edit',
                    component: () => import('@/views/helpcenter/stationview/quiz/TestEditHelp.vue')
                },
                {
                    path: 'quiz/tests/:id/evaluate/:attemptId',
                    name: 'help-quiz-test-evaluate',
                    component: () => import('@/views/helpcenter/stationview/quiz/TestEvaluateHelp.vue')
                },
                {
                    path: 'quiz/tests/:id/take',
                    name: 'help-quiz-test-take',
                    component: () => import('@/views/helpcenter/stationview/quiz/TestTakeHelp.vue')
                },
                // Test Protocols
                {
                    path: 'protocols',
                    name: 'help-protocol',
                    component: () => import('@/views/helpcenter/stationview/protocol/ProtocolHelp.vue')
                },
                {
                    path: 'protocols',
                    name: 'help-protocol-module-overview',
                    component: () => import('@/views/helpcenter/stationview/protocol/ModuleOverviewHelp.vue')
                },
                {
                    path: 'protocols/:id',
                    name: 'help-protocol-detail',
                    component: () => import('@/views/helpcenter/stationview/protocol/DetailHelp.vue')
                },
                {
                    path: 'protocols/runs',
                    name: 'help-protocol-run-list',
                    component: () => import('@/views/helpcenter/stationview/protocol/RunListHelp.vue')
                },
                {
                    path: 'protocols/runs/:id',
                    name: 'help-protocol-run-detail',
                    component: () => import('@/views/helpcenter/stationview/protocol/RunDetailHelp.vue')
                },
                {
                    path: 'protocols/runs/:id/evaluation',
                    name: 'help-protocol-evaluation',
                    component: () => import('@/views/helpcenter/stationview/protocol/EvaluationHelp.vue')
                },
                {
                    path: 'protocols/runs/:id/grade/:memberId',
                    name: 'help-protocol-grade',
                    component: () => import('@/views/helpcenter/stationview/protocol/GradingHelp.vue')
                },
                // Knowledge Base
                {
                    path: 'knowledge',
                    name: 'help-knowledge-module-overview',
                    component: () => import('@/views/helpcenter/stationview/knowledge/ModuleOverviewHelp.vue')
                },
                {
                    path: 'knowledge/browse',
                    name: 'help-knowledge-base',
                    component: () => import('@/views/helpcenter/stationview/knowledge/KnowledgeBaseHelp.vue')
                },
                {
                    path: 'knowledge/editor',
                    name: 'help-knowledge-editor',
                    component: () => import('@/views/helpcenter/stationview/knowledge/EditorHelp.vue')
                },
                {
                    path: 'knowledge/federated',
                    name: 'help-knowledge-federated',
                    component: () => import('@/views/helpcenter/stationview/knowledge/FederatedKbHelp.vue')
                },
                {
                    path: 'knowledge/file/:id',
                    name: 'help-kb-file',
                    component: () => import('@/views/helpcenter/stationview/knowledge/FileViewHelp.vue')
                },
                {
                    path: 'knowledge/file/:id/versions',
                    name: 'help-kb-versions',
                    component: () => import('@/views/helpcenter/stationview/knowledge/VersionsHelp.vue')
                },
                // Boards
                {
                    path: 'federation/boards',
                    name: 'help-federated-boards',
                    component: () => import('@/views/helpcenter/stationview/boards/FederatedBoardsHelp.vue'),
                },
                {
                    path: 'federation/boards/:partnerUid/:boardKey',
                    name: 'help-federated-board-view',
                    redirect: { name: 'help-federated-boards' },
                },
                {
                    path: 'federation/boards/:partnerUid/:boardKey/tickets/:ticketNumber',
                    name: 'help-federated-ticket-detail',
                    redirect: { name: 'help-federated-boards' },
                },
                {
                    path: 'boards',
                    name: 'help-board-overview',
                    component: () => import('@/views/helpcenter/stationview/boards/BoardsHelp.vue')
                },
                {
                    path: 'boards/manage',
                    name: 'help-board-manage',
                    component: () => import('@/views/helpcenter/stationview/boards/BoardManageHelp.vue')
                },
                {
                    path: 'boards/:boardKey',
                    name: 'help-board-view',
                    component: () => import('@/views/helpcenter/stationview/boards/BoardViewHelp.vue')
                },
                {
                    path: 'boards/:boardKey/archived',
                    name: 'help-board-archived',
                    component: () => import('@/views/helpcenter/stationview/boards/ArchivedHelp.vue')
                },
                {
                    path: 'boards/:boardKey/backlog',
                    name: 'help-board-backlog',
                    component: () => import('@/views/helpcenter/stationview/boards/BacklogHelp.vue')
                },
                {
                    path: 'boards/:boardKey/settings',
                    name: 'help-board-settings',
                    component: () => import('@/views/helpcenter/stationview/boards/BoardSettingsHelp.vue')
                },
                {
                    path: 'boards/:boardKey/tickets/new',
                    name: 'help-ticket-create',
                    component: () => import('@/views/helpcenter/stationview/boards/TicketCreateHelp.vue')
                },
                {
                    path: 'boards/:boardKey/tickets/:ticketNumber',
                    name: 'help-ticket-detail',
                    component: () => import('@/views/helpcenter/stationview/boards/TicketDetailHelp.vue')
                },
                {
                    path: ':pathMatch(.*)*',
                    name: 'helpcenter-station-not-found',
                    component: () => import('@/components/feedback/NotFoundContent.vue')
                },
            ],
        },
        {
            path: '/helpcenter/admin',
            component: HelpCenterAdminView,
            children: [
                {path: '', redirect: {name: 'help-admin-module-overview'}},
                {
                    path: 'dashboard/overview',
                    name: 'help-admin-module-overview',
                    component: () => import('@/views/helpcenter/adminview/OverviewHelp.vue')
                },
                {
                    path: 'dashboard/statistics',
                    name: 'help-admin-statistics',
                    component: () => import('@/views/helpcenter/adminview/StatisticsHelp.vue')
                },
                {
                    path: 'stations/overview',
                    name: 'help-admin-stations-module-overview',
                    component: () => import('@/views/helpcenter/adminview/StationsModuleOverviewHelp.vue')
                },
                {
                    path: 'stations',
                    name: 'help-admin-stations',
                    component: () => import('@/views/helpcenter/adminview/StationsHelp.vue')
                },
                {
                    path: 'stations/edit/:id?',
                    name: 'help-admin-station-edit',
                    component: () => import('@/views/helpcenter/adminview/StationEditHelp.vue')
                },
                {
                    path: 'stations/applications',
                    name: 'help-admin-station-applications',
                    component: () => import('@/views/helpcenter/adminview/ApplicationsHelp.vue')
                },
                {
                    path: 'settings',
                    name: 'help-admin-settings',
                    component: () => import('@/views/helpcenter/adminview/SettingsHelp.vue')
                },
                {
                    path: 'settings/legal',
                    name: 'help-admin-legal',
                    component: () => import('@/views/helpcenter/adminview/LegalHelp.vue')
                },
                {
                    path: 'settings/mailing',
                    name: 'help-admin-mailing',
                    component: () => import('@/views/helpcenter/adminview/MailingHelp.vue')
                },
                {
                    path: 'api-status',
                    name: 'help-admin-api-status',
                    component: () => import('@/views/helpcenter/adminview/ApiStatusHelp.vue')
                },
                {
                    path: 'api-status/detail',
                    name: 'help-admin-api-status-detail',
                    component: () => import('@/views/helpcenter/adminview/ApiStatusDetailHelp.vue')
                },
                {
                    path: 'problems',
                    name: 'help-admin-problems',
                    component: () => import('@/views/helpcenter/adminview/ProblemsHelp.vue')
                },
                {
                    path: 'problem-reports',
                    name: 'help-admin-problem-reports',
                    redirect: { name: 'help-admin-problems' }
                },
                {
                    path: ':pathMatch(.*)*',
                    name: 'helpcenter-admin-not-found',
                    component: () => import('@/components/feedback/NotFoundContent.vue')
                },
            ],
        },
        {
            path: '/public/station/:stationUid',
            component: () => import('@/views/public/PublicStationShell.vue'),
            meta: {public: true},
            children: [
                {
                    path: '',
                    name: 'public-station',
                    redirect: to => ({name: 'public-station-calendar', params: to.params}),
                },
                {
                    path: 'calendar',
                    name: 'public-station-calendar',
                    component: () => import('@/views/public/PublicStationCalendarView.vue'),
                },
                {
                    path: 'knowledge',
                    name: 'public-kb',
                    component: () => import('@/views/public/PublicKnowledgeBaseView.vue'),
                },
                {
                    path: 'knowledge/file/:id',
                    name: 'public-kb-file',
                    component: () => import('@/views/public/PublicKbFileView.vue'),
                },
            ],
        },
        {
            path: '/discovery',
            name: 'public-discovery',
            component: () => import('@/views/public/PublicDiscoveryView.vue'),
            meta: {public: true},
        },
        {
            path: '/style',
            name: 'style',
            component: StyleView,
        },
        {
            path: '/:pathMatch(.*)*',
            name: 'not-found',
            component: () => import('@/views/NotFoundView.vue'),
        },
    ],
})

router.beforeEach((to) => {
    const publicRoutes = ['home', 'login', 'requirements', 'forgot-password', 'set-password', 'reset-password', 'apply', 'apply-verify', 'waitlist-register', 'waitlist-status', 'style', 'privacy', 'terms', 'imprint', 'patch-notes', 'not-found']
    if (publicRoutes.includes(to.name as string)) {
        return true
    }

    if (to.meta.public === true) {
        return true
    }

    if (to.path.startsWith('/helpcenter')) {
        return true
    }

    const token = getItem('session_token')
    if (!token) {
        return {name: 'login', query: {redirect: to.fullPath}}
    }

    // Block navigation if re-consent is needed (except to the reconsent page itself)
    const {needsReconsent} = useConsentGuard()
    if (needsReconsent.value && to.name !== 'reconsent') {
        return {name: 'reconsent'}
    }

    // Redirect to requirements if inactive for more than 1 hour
    const lastActivity = localStorage.getItem('ember_last_activity')
    const now = Date.now()
    localStorage.setItem('ember_last_activity', String(now))
    if (lastActivity && now - Number(lastActivity) > 3600000 && to.name !== 'requirements') {
        return {name: 'requirements', query: {redirect: to.fullPath}}
    }

    return true
})

router.afterEach((to) => {
    const t = i18n.global.t
    const te = i18n.global.te
    if (to.name === 'home') {
        document.title = 'Ember — Digitale Verwaltung für Jugendfeuerwehren'
        return
    }
    const key = `pages.${to.name as string}.title`
    const title = te(key) ? t(key) : ''
    document.title = title ? `${title} — Ember` : 'Ember'
})

export default router
