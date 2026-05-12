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
import {getItem} from '@/api/storage'

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
                    path: 'members/changes',
                    name: 'members-changes',
                    component: () => import('@/views/stationview/members/ChangesView.vue'),
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
            ],
        },
        {
            path: '/style',
            name: 'style',
            component: StyleView,
        },
    ],
})

router.beforeEach((to) => {
    const publicRoutes = ['home', 'login', 'forgot-password', 'set-password', 'reset-password', 'apply', 'apply-verify', 'style']
    if (publicRoutes.includes(to.name as string)) {
        return true
    }

    const token = getItem('session_token')
    if (!token) {
        return {name: 'login'}
    }

    return true
})

export default router
