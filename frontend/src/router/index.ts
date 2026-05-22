/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createRouter, createWebHistory} from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import SetPasswordView from '@/views/SetPasswordView.vue'
import DashboardView from '@/views/DashboardView.vue'
import OverviewView from '@/views/dashboardview/OverviewView.vue'
import StationManageView from '@/views/stationview/manage/StationView.vue'
import MembersCreateView from '@/views/stationview/members/CreateView.vue'
import MembersListView from '@/views/stationview/members/ListView.vue'
import MembersGroupsView from '@/views/stationview/members/GroupsView.vue'
import InventoryOverviewView from '@/views/stationview/inventory/OverviewView.vue'
import InventoryManageView from '@/views/stationview/inventory/ManageView.vue'
import EventsView from '@/views/stationview/events/IndexView.vue'
import EventsUpcomingView from '@/views/stationview/events/UpcomingView.vue'
import EventsRegistrationsView from '@/views/stationview/events/RegistrationsView.vue'
import AttendanceNewView from '@/views/stationview/attendance/NewView.vue'
import AttendancePastView from '@/views/stationview/attendance/PastView.vue'
import AttendanceConfigView from '@/views/stationview/manage/AttendanceConfigView.vue'
import AttendanceConfigEditView from '@/views/stationview/manage/AttendanceConfigEditView.vue'
import MembersConfigView from '@/views/stationview/manage/MembersConfigView.vue'
import MemberEditView from '@/views/stationview/members/EditView.vue'
import MemberDetailView from '@/views/stationview/members/DetailView.vue'
import ProfileView from '@/views/stationview/profile/IndexView.vue'
import ProfileManagedView from '@/views/stationview/profile/ManagedView.vue'
import AdminView from '@/views/AdminView.vue'
import AdminOverviewView from '@/views/adminview/AdminOverviewView.vue'
import AdminStationsView from '@/views/adminview/AdminStationsView.vue'
import AdminStationEditView from '@/views/adminview/AdminStationEditView.vue'
import StationSelectView from '@/views/StationSelectView.vue'
import HomeView from '@/views/HomeView.vue'
import StyleView from '@/views/StyleView.vue'
import HelpCenterStationView from '@/views/HelpCenterStationView.vue'
import HelpCenterAdminView from '@/views/HelpCenterAdminView.vue'
import {getItem} from '@/api/storage'
import {useConsentGuard} from '@/composables/useConsentGuard'
import i18n from '@/i18n'

const StatisticsView = () => import('@/views/dashboardview/StatisticsView.vue')

const AdminStatisticsView = () => import('@/views/adminview/AdminStatisticsView.vue')

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/login',
            name: 'login',
            component: LoginView,
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
                    component: () => import('@/views/stationview/profile/SettingsView.vue'),
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
            ],
        },
        {
            path: '/helpcenter/station',
            component: HelpCenterStationView,
            children: [
                {path: '', redirect: {name: 'help-welcome'}},
                // Basics
                {path: 'basics', name: 'help-welcome', component: () => import('@/views/helpcenter/basics/WelcomeHelp.vue')},
                {path: 'basics/overview', name: 'help-basics-overview', component: () => import('@/views/helpcenter/basics/OverviewHelp.vue')},
                {path: 'basics/roles', name: 'help-basics-roles', component: () => import('@/views/helpcenter/basics/RolesHelp.vue')},
                {path: 'basics/modules', name: 'help-basics-modules', component: () => import('@/views/helpcenter/basics/ModulesHelp.vue')},
                {path: 'basics/hosting', name: 'help-basics-hosting', component: () => import('@/views/helpcenter/basics/HostingHelp.vue')},
                // Dashboard
                {path: 'dashboard', name: 'help-dashboard-module-overview', component: () => import('@/views/helpcenter/dashboardview/ModuleOverviewHelp.vue')},
                {path: 'dashboard/overview', name: 'help-dashboard-overview', component: () => import('@/views/helpcenter/dashboardview/OverviewHelp.vue')},
                {path: 'dashboard/statistics', name: 'help-dashboard-statistics', component: () => import('@/views/helpcenter/dashboardview/StatisticsHelp.vue')},
                // News
                {path: 'news', name: 'help-news-overview', component: () => import('@/views/helpcenter/stationview/news/OverviewHelp.vue')},
                {path: 'news/list', name: 'help-news-list', component: () => import('@/views/helpcenter/stationview/news/ListHelp.vue')},
                {path: 'news/create', name: 'help-news-create', component: () => import('@/views/helpcenter/stationview/news/EditHelp.vue')},
                {path: 'news/:id/edit', name: 'help-news-edit', component: () => import('@/views/helpcenter/stationview/news/EditHelp.vue')},
                // Profile
                {path: 'profile', name: 'help-profile-overview', component: () => import('@/views/helpcenter/stationview/profile/OverviewHelp.vue')},
                {path: 'profile/index', name: 'help-profile', component: () => import('@/views/helpcenter/stationview/profile/IndexHelp.vue')},
                {path: 'profile/absences', name: 'help-profile-absences', component: () => import('@/views/helpcenter/stationview/profile/AbsenceHelp.vue')},
                {path: 'profile/managed', name: 'help-profile-managed', component: () => import('@/views/helpcenter/stationview/profile/ManagedHelp.vue')},
                {path: 'profile/inventory', name: 'help-profile-inventory', component: () => import('@/views/helpcenter/stationview/profile/InventoryHelp.vue')},
                {path: 'profile/settings', name: 'help-profile-settings', component: () => import('@/views/helpcenter/stationview/profile/SettingsHelp.vue')},
                {path: 'profile/theme', name: 'help-profile-theme', component: () => import('@/views/helpcenter/stationview/profile/ThemeHelp.vue')},
                // Station management
                {path: 'manage', name: 'help-manage-overview', component: () => import('@/views/helpcenter/stationview/manage/OverviewHelp.vue')},
                {path: 'manage/station', name: 'help-station-manage', component: () => import('@/views/helpcenter/stationview/manage/StationHelp.vue')},
                {path: 'manage/attendance-config', name: 'help-station-attendance-config', component: () => import('@/views/helpcenter/stationview/manage/AttendanceConfigHelp.vue')},
                {path: 'manage/attendance-config/edit/:id?', name: 'help-station-attendance-config-edit', component: () => import('@/views/helpcenter/stationview/manage/AttendanceConfigEditHelp.vue')},
                {path: 'manage/members-config', name: 'help-station-members-config', component: () => import('@/views/helpcenter/stationview/manage/MembersConfigHelp.vue')},
                {path: 'manage/mail-config', name: 'help-station-mail-config', component: () => import('@/views/helpcenter/stationview/manage/MailConfigHelp.vue')},
                {path: 'manage/theme', name: 'help-station-theme-manage', component: () => import('@/views/helpcenter/stationview/manage/ThemeManageHelp.vue')},
                // Members
                {path: 'members', name: 'help-members-overview', component: () => import('@/views/helpcenter/stationview/members/OverviewHelp.vue')},
                {path: 'members/create', name: 'help-members-create', component: () => import('@/views/helpcenter/stationview/members/CreateHelp.vue')},
                {path: 'members/list', name: 'help-members-list', component: () => import('@/views/helpcenter/stationview/members/ListHelp.vue')},
                {path: 'members/import', name: 'help-members-import', component: () => import('@/views/helpcenter/stationview/members/ImportHelp.vue')},
                {path: 'members/import-team', name: 'help-members-import-team', component: () => import('@/views/helpcenter/stationview/members/TeamImportHelp.vue')},
                {path: 'members/detail/:id?', name: 'help-members-detail', component: () => import('@/views/helpcenter/stationview/members/DetailHelp.vue')},
                {path: 'members/edit/:id?', name: 'help-members-edit', component: () => import('@/views/helpcenter/stationview/members/EditHelp.vue')},
                {path: 'members/groups', name: 'help-members-groups', component: () => import('@/views/helpcenter/stationview/members/GroupsHelp.vue')},
                {path: 'members/tags', name: 'help-members-tags', component: () => import('@/views/helpcenter/stationview/members/TagsHelp.vue')},
                {path: 'members/changes', name: 'help-members-changes', component: () => import('@/views/helpcenter/stationview/members/ChangesHelp.vue')},
                {path: 'members/former', name: 'help-members-former', component: () => import('@/views/helpcenter/stationview/members/FormerHelp.vue')},
                {path: 'members/waiting-lists', name: 'help-waiting-lists', component: () => import('@/views/helpcenter/stationview/members/WaitingListHelp.vue')},
                // Inventory
                {path: 'inventory', name: 'help-inventory-module-overview', component: () => import('@/views/helpcenter/stationview/inventory/ModuleOverviewHelp.vue')},
                {path: 'inventory/overview', name: 'help-inventory-overview', component: () => import('@/views/helpcenter/stationview/inventory/OverviewHelp.vue')},
                {path: 'inventory/my', name: 'help-inventory-my', component: () => import('@/views/helpcenter/stationview/profile/InventoryHelp.vue')},
                {path: 'inventory/exchanges', name: 'help-inventory-exchanges', component: () => import('@/views/helpcenter/stationview/inventory/ExchangeHelp.vue')},
                {path: 'inventory/members', name: 'help-inventory-members', component: () => import('@/views/helpcenter/stationview/inventory/MemberListHelp.vue')},
                {path: 'inventory/detail/:id?', name: 'help-inventory-detail', component: () => import('@/views/helpcenter/stationview/inventory/DetailHelp.vue')},
                {path: 'inventory/edit/:id?', name: 'help-inventory-edit', component: () => import('@/views/helpcenter/stationview/inventory/EditHelp.vue')},
                {path: 'inventory/member/:memberId?', name: 'help-inventory-member', component: () => import('@/views/helpcenter/stationview/inventory/MemberInventoryHelp.vue')},
                {path: 'inventory/manage', name: 'help-inventory-manage', component: () => import('@/views/helpcenter/stationview/inventory/ManageHelp.vue')},
                {path: 'inventory/requirements', name: 'help-inventory-requirements', component: () => import('@/views/helpcenter/stationview/inventory/RequirementsHelp.vue')},
                {path: 'inventory/checks', name: 'help-inventory-checks', component: () => import('@/views/helpcenter/stationview/inventory/CheckOverviewHelp.vue')},
                {path: 'inventory/checks/:memberId', name: 'help-inventory-check-member', component: () => import('@/views/helpcenter/stationview/inventory/CheckMemberHelp.vue')},
                {path: 'inventory/checks/:memberId/result', name: 'help-inventory-check-result', component: () => import('@/views/helpcenter/stationview/inventory/CheckResultHelp.vue')},
                {path: 'inventory/procurement', name: 'help-inventory-procurement', component: () => import('@/views/helpcenter/stationview/inventory/ProcurementHelp.vue')},
                // Attendance
                {path: 'attendance', name: 'help-attendance-overview', component: () => import('@/views/helpcenter/stationview/attendance/OverviewHelp.vue')},
                {path: 'attendance/new', name: 'help-attendance-new', component: () => import('@/views/helpcenter/stationview/attendance/NewHelp.vue')},
                {path: 'attendance/past', name: 'help-attendance-past', component: () => import('@/views/helpcenter/stationview/attendance/PastHelp.vue')},
                {path: 'attendance/session/:id?', name: 'help-attendance-session', component: () => import('@/views/helpcenter/stationview/attendance/SessionHelp.vue')},
                {path: 'attendance/report', name: 'help-attendance-report', component: () => import('@/views/helpcenter/stationview/attendance/ReportHelp.vue')},
                // Events
                {path: 'events/overview', name: 'help-events-overview', component: () => import('@/views/helpcenter/stationview/events/OverviewHelp.vue')},
                {path: 'events/upcoming', name: 'help-events-upcoming', component: () => import('@/views/helpcenter/stationview/events/UpcomingHelp.vue')},
                {path: 'events/registrations', name: 'help-events-registrations', component: () => import('@/views/helpcenter/stationview/events/RegistrationsHelp.vue')},
                {path: 'events', name: 'help-events', component: () => import('@/views/helpcenter/stationview/events/IndexHelp.vue')},
                {path: 'events/new', name: 'help-event-new', component: () => import('@/views/helpcenter/stationview/events/EventEditHelp.vue')},
                {path: 'events/:id/edit', name: 'help-event-edit', component: () => import('@/views/helpcenter/stationview/events/EventEditHelp.vue')},
                {path: 'events/detail/:id?', name: 'help-event-detail', component: () => import('@/views/helpcenter/stationview/events/EventDetailHelp.vue')},
                // Forms
                {path: 'forms', name: 'help-forms-overview', component: () => import('@/views/helpcenter/stationview/forms/OverviewHelp.vue')},
                {path: 'forms/list', name: 'help-forms-list', component: () => import('@/views/helpcenter/stationview/forms/ListHelp.vue')},
                {path: 'forms/create', name: 'help-forms-create', component: () => import('@/views/helpcenter/stationview/forms/BuilderHelp.vue')},
                {path: 'forms/fill/:id?', name: 'help-forms-fill', component: () => import('@/views/helpcenter/stationview/forms/FillHelp.vue')},
                {path: 'forms/analytics/:id?', name: 'help-forms-analytics', component: () => import('@/views/helpcenter/stationview/forms/AnalyticsHelp.vue')},
                // Lost and Found
                {path: 'lost-and-found', name: 'help-lost-and-found', component: () => import('@/views/helpcenter/stationview/lostandfound/ListHelp.vue')},
                // Quiz
                {path: 'quiz', name: 'help-quiz-overview', component: () => import('@/views/helpcenter/stationview/quiz/OverviewHelp.vue')},
                {path: 'quiz/catalogs', name: 'help-quiz-catalogs', component: () => import('@/views/helpcenter/stationview/quiz/CatalogListHelp.vue')},
                {path: 'quiz/catalog', name: 'help-quiz-catalog-detail', component: () => import('@/views/helpcenter/stationview/quiz/CatalogDetailHelp.vue')},
                {path: 'quiz/ai', name: 'help-quiz-ai', component: () => import('@/views/helpcenter/stationview/quiz/AiGenerationHelp.vue')},
                {path: 'quiz/tests', name: 'help-quiz-tests', component: () => import('@/views/helpcenter/stationview/quiz/TestListHelp.vue')},
                {path: 'quiz/tests/details', name: 'help-quiz-test-detail', component: () => import('@/views/helpcenter/stationview/quiz/TestDetailHelp.vue')},
                {path: 'quiz/training', name: 'help-quiz-training', component: () => import('@/views/helpcenter/stationview/quiz/TrainingHelp.vue')},
                // Knowledge Base
                {path: 'knowledge', name: 'help-knowledge-base', component: () => import('@/views/helpcenter/stationview/knowledge/KnowledgeBaseHelp.vue')},
                {path: 'knowledge/editor', name: 'help-knowledge-editor', component: () => import('@/views/helpcenter/stationview/knowledge/EditorHelp.vue')},
                {path: ':pathMatch(.*)*', name: 'helpcenter-station-not-found', component: () => import('@/components/feedback/NotFoundContent.vue')},
            ],
        },
        {
            path: '/helpcenter/admin',
            component: HelpCenterAdminView,
            children: [
                {path: '', redirect: {name: 'help-admin-overview'}},
                {path: 'dashboard/overview', name: 'help-admin-overview', component: () => import('@/views/helpcenter/adminview/OverviewHelp.vue')},
                {path: 'dashboard/statistics', name: 'help-admin-statistics', component: () => import('@/views/helpcenter/adminview/StatisticsHelp.vue')},
                {path: 'stations', name: 'help-admin-stations', component: () => import('@/views/helpcenter/adminview/StationsHelp.vue')},
                {path: 'stations/edit/:id?', name: 'help-admin-station-edit', component: () => import('@/views/helpcenter/adminview/StationEditHelp.vue')},
                {path: 'stations/applications', name: 'help-admin-station-applications', component: () => import('@/views/helpcenter/adminview/ApplicationsHelp.vue')},
                {path: 'settings', name: 'help-admin-settings', component: () => import('@/views/helpcenter/adminview/SettingsHelp.vue')},
                {path: ':pathMatch(.*)*', name: 'helpcenter-admin-not-found', component: () => import('@/components/feedback/NotFoundContent.vue')},
            ],
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
    const publicRoutes = ['home', 'login', 'forgot-password', 'set-password', 'reset-password', 'apply', 'apply-verify', 'waitlist-register', 'waitlist-status', 'style', 'privacy', 'terms', 'imprint', 'patch-notes', 'not-found']
    if (publicRoutes.includes(to.name as string)) {
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
