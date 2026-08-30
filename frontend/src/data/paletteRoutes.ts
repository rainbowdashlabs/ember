/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ClusterPermission} from '@/api/clusters'
import {StationModules, StationPermission} from '@/api/types'
import type {QuickSearchScope} from '@/composables/useQuickSearch'

/**
 * One entry in the command-palette route index. The shape is intentionally
 * flat - the palette filters and renders entries directly, no tree.
 *
 * <p>{@code labelKey} is the i18n key for the visible label (typically a
 * {@code sidebar.*} key already used by the matching {@code SidebarLink}).
 *
 * <p>{@code permission}, {@code anyPermission} and {@code module} together
 * gate visibility against the current session - entries the user cannot
 * reach are never surfaced.
 */
export interface PaletteRouteEntry {
    scope: QuickSearchScope
    to: string
    labelKey: string
    icon: string
    permission?: string
    anyPermission?: string[]
    module?: string
    /** What the caller must hold at the cluster, for entries of the cluster scope. */
    clusterPermission?: string
    clusterAnyPermission?: string[]
}

export const PALETTE_ROUTES: PaletteRouteEntry[] = [
    {scope: 'station', to: '/station/dashboard/overview', labelKey: 'sidebar.dashboard', icon: 'gauge'},
    {scope: 'station', to: '/station/dashboard/statistics', labelKey: 'sidebar.statistics', icon: 'chart-line', permission: StationPermission.STATION_STATISTICS},
    {scope: 'station', to: '/station/requirements', labelKey: 'sidebar.requirements', icon: 'clipboard-check'},
    {scope: 'station', to: '/station/news', labelKey: 'sidebar.news', icon: 'newspaper', module: StationModules.NEWS},

    {scope: 'station', to: '/station/profile', labelKey: 'sidebar.profile', icon: 'user'},
    {scope: 'station', to: '/station/profile/absences', labelKey: 'sidebar.absences', icon: 'calendar-days'},
    {scope: 'station', to: '/station/profile/managed', labelKey: 'sidebar.managedProfiles', icon: 'users'},
    {scope: 'station', to: '/station/profile/settings/notifications', labelKey: 'sidebar.notifications', icon: 'bell'},
    {scope: 'station', to: '/station/profile/settings/security', labelKey: 'sidebar.security', icon: 'shield'},
    {scope: 'station', to: '/station/profile/settings/sessions', labelKey: 'sidebar.sessions', icon: 'desktop'},

    {scope: 'station', to: '/station/members/list', labelKey: 'sidebar.members', icon: 'users', permission: StationPermission.MEMBER_READ},
    {scope: 'station', to: '/station/members/create', labelKey: 'sidebar.create', icon: 'user-plus', permission: StationPermission.MEMBER_EDIT},
    {scope: 'station', to: '/station/members/groups', labelKey: 'sidebar.groups', icon: 'layer-group', permission: StationPermission.MEMBER_MANAGE_GROUP},
    {scope: 'station', to: '/station/members/tags', labelKey: 'sidebar.tags', icon: 'hashtag', permission: StationPermission.MEMBER_MANAGE_TAGS},
    {scope: 'station', to: '/station/members/changes', labelKey: 'sidebar.changes', icon: 'bell', permission: StationPermission.MEMBER_CHANGES},
    {scope: 'station', to: '/station/members/former', labelKey: 'sidebar.formerMembers', icon: 'user-slash', permission: StationPermission.MEMBER_EDIT},
    {scope: 'station', to: '/station/members/waiting-lists', labelKey: 'sidebar.waitingLists', icon: 'clipboard-list', module: StationModules.WAITING_LIST, anyPermission: [StationPermission.WAITLIST_READ, StationPermission.WAITLIST_EDIT, StationPermission.WAITLIST_MANAGER, StationPermission.WAITLIST_ADD]},
    {scope: 'station', to: '/station/members/config', labelKey: 'sidebar.membersConfig', icon: 'users-gear', permission: StationPermission.MEMBER_FIELDS},
    {scope: 'station', to: '/station/members/type-permissions', labelKey: 'sidebar.typePermissions', icon: 'shield', permission: StationPermission.MEMBER_MANAGER},

    {scope: 'station', to: '/station/inventory', labelKey: 'sidebar.inventory', icon: 'boxes-stacked', module: StationModules.INVENTORY, anyPermission: [StationPermission.INVENTORY_READ, StationPermission.INVENTORY_EDIT, StationPermission.INVENTORY_MANAGER]},
    {scope: 'station', to: '/station/inventory/my', labelKey: 'sidebar.myInventory', icon: 'boxes-stacked', module: StationModules.INVENTORY},
    {scope: 'station', to: '/station/inventory/storage', labelKey: 'sidebar.inventoryStorage', icon: 'warehouse', module: StationModules.INVENTORY, permission: StationPermission.INVENTORY_READ},
    {scope: 'station', to: '/station/inventory/assign', labelKey: 'sidebar.inventoryAssign', icon: 'user-plus', module: StationModules.INVENTORY, permission: StationPermission.INVENTORY_ASSIGN},
    {scope: 'station', to: '/station/inventory/checks/member', labelKey: 'sidebar.inventoryCheckMember', icon: 'user-check', module: StationModules.INVENTORY, permission: StationPermission.INVENTORY_CHECK},
    {scope: 'station', to: '/station/inventory/checks/container', labelKey: 'sidebar.inventoryCheckContainer', icon: 'box-open', module: StationModules.INVENTORY, permission: StationPermission.INVENTORY_CHECK},
    {scope: 'station', to: '/station/inventory/members', labelKey: 'sidebar.inventoryMembers', icon: 'users', module: StationModules.INVENTORY, permission: StationPermission.INVENTORY_READ},
    {scope: 'station', to: '/station/inventory/manage', labelKey: 'sidebar.inventoryManage', icon: 'box-open', module: StationModules.INVENTORY, permission: StationPermission.INVENTORY_CREATE},
    {scope: 'station', to: '/station/inventory/exchanges', labelKey: 'sidebar.inventoryExchanges', icon: 'rotate', module: StationModules.INVENTORY},
    {scope: 'station', to: '/station/inventory/procurement', labelKey: 'sidebar.inventoryProcurement', icon: 'folder-plus', module: StationModules.INVENTORY, permission: StationPermission.INVENTORY_PROCUREMENT},
    {scope: 'station', to: '/station/inventory/requirements', labelKey: 'sidebar.inventoryRequirements', icon: 'clipboard-list', module: StationModules.INVENTORY, permission: StationPermission.INVENTORY_READ},
    {scope: 'station', to: '/station/inventory/lending', labelKey: 'sidebar.inventoryLending', icon: 'handshake', module: StationModules.INVENTORY, anyPermission: [StationPermission.INVENTORY_LENDING_REQUEST, StationPermission.INVENTORY_LENDING_MANAGER]},

    {scope: 'station', to: '/station/attendance/new', labelKey: 'sidebar.attendance', icon: 'clipboard-user', module: StationModules.ATTENDANCE, permission: StationPermission.ATTENDANCE_EDIT},
    {scope: 'station', to: '/station/attendance/past', labelKey: 'sidebar.pastAttendance', icon: 'clock-rotate-left', module: StationModules.ATTENDANCE, permission: StationPermission.ATTENDANCE_READ},
    {scope: 'station', to: '/station/attendance/report', labelKey: 'sidebar.attendanceReport', icon: 'chart-line', module: StationModules.ATTENDANCE, permission: StationPermission.ATTENDANCE_EXPORT},
    {scope: 'station', to: '/station/attendance/config', labelKey: 'sidebar.attendanceConfig', icon: 'gear', module: StationModules.ATTENDANCE, permission: StationPermission.ATTENDANCE_CONFIGURE},

    {scope: 'station', to: '/station/events/upcoming', labelKey: 'sidebar.events', icon: 'calendar-days', module: StationModules.EVENTS},
    {scope: 'station', to: '/station/events/registrations', labelKey: 'sidebar.pendingRegistrations', icon: 'clipboard-list', module: StationModules.EVENTS, permission: StationPermission.EVENT_REGISTRATION},
    {scope: 'station', to: '/station/events', labelKey: 'sidebar.manageEvents', icon: 'gears', module: StationModules.EVENTS, permission: StationPermission.EVENT_EDIT},
    {scope: 'station', to: '/station/events/categories', labelKey: 'sidebar.eventCategories', icon: 'folder-plus', module: StationModules.EVENTS, permission: StationPermission.EVENT_MANAGE_CATEGORY},
    {scope: 'station', to: '/station/events/templates', labelKey: 'sidebar.eventTemplates', icon: 'clipboard-list', module: StationModules.EVENTS, permission: StationPermission.EVENT_MANAGE_TEMPLATE},

    {scope: 'station', to: '/station/forms', labelKey: 'sidebar.forms', icon: 'square-poll-vertical', module: StationModules.FORMS},
    {scope: 'station', to: '/station/forms/create', labelKey: 'sidebar.formsCreate', icon: 'plus', module: StationModules.FORMS, permission: StationPermission.POLL_CREATE},

    {scope: 'station', to: '/station/lost-and-found', labelKey: 'sidebar.lostAndFound', icon: 'box-open', module: StationModules.LOST_AND_FOUND},

    {scope: 'station', to: '/station/quiz/catalogs', labelKey: 'sidebar.quizCatalogs', icon: 'book', module: StationModules.QUIZ, permission: StationPermission.TEST_CATALOG_VIEW},
    {scope: 'station', to: '/station/quiz/tests', labelKey: 'sidebar.quizTests', icon: 'file-lines', module: StationModules.QUIZ},
    {scope: 'station', to: '/station/quiz/training', labelKey: 'sidebar.quizTraining', icon: 'brain', module: StationModules.QUIZ},
    {scope: 'station', to: '/station/protocols', labelKey: 'sidebar.protocols', icon: 'clipboard-list', module: StationModules.TEST_PROTOCOL, permission: StationPermission.PROTOCOL_CREATE},
    {scope: 'station', to: '/station/protocols/runs', labelKey: 'sidebar.protocolRuns', icon: 'clipboard-check', module: StationModules.TEST_PROTOCOL, anyPermission: [StationPermission.PROTOCOL_TESTER, StationPermission.PROTOCOL_MANAGER]},

    {scope: 'station', to: '/station/boards', labelKey: 'sidebar.boards', icon: 'table-columns', module: StationModules.BOARDS, permission: StationPermission.BOARD_USE},
    {scope: 'station', to: '/station/boards/manage', labelKey: 'sidebar.boardManage', icon: 'gears', module: StationModules.BOARDS, permission: StationPermission.BOARD_EDIT},

    {scope: 'station', to: '/station/procedures', labelKey: 'sidebar.procedures', icon: 'list-check', module: StationModules.PROCEDURES, permission: StationPermission.PROCEDURE_READ},
    {scope: 'station', to: '/station/procedures/templates', labelKey: 'sidebar.procedureTemplates', icon: 'clipboard-list', module: StationModules.PROCEDURES, permission: StationPermission.PROCEDURE_MANAGER},

    {scope: 'station', to: '/station/knowledge', labelKey: 'sidebar.knowledgeBase', icon: 'book-open', module: StationModules.KNOWLEDGE_BASE},

    {scope: 'station', to: '/station/pages', labelKey: 'sidebar.pages', icon: 'file-lines', permission: StationPermission.PAGE_EDIT},
    {scope: 'station', to: '/station/media', labelKey: 'sidebar.media', icon: 'folder-open', permission: StationPermission.PAGE_EDIT},
    {scope: 'station', to: '/station/pages/forms', labelKey: 'sidebar.pagesForms', icon: 'clipboard-list', permission: StationPermission.PAGE_EDIT},
    {scope: 'station', to: '/station/pages/polls', labelKey: 'sidebar.pagesPolls', icon: 'square-poll-vertical', permission: StationPermission.PAGE_EDIT},

    {scope: 'station', to: '/station/manage', labelKey: 'sidebar.manage', icon: 'gears', anyPermission: [StationPermission.STATION_GENERAL, StationPermission.STATION_LOOK_AND_FEEL, StationPermission.STATION_MAIL, StationPermission.STATION_MODULES, StationPermission.STATION_IMPORT_EXPORT]},
    {scope: 'station', to: '/station/manage/theme', labelKey: 'sidebar.stationTheme', icon: 'palette', permission: StationPermission.STATION_LOOK_AND_FEEL},
    {scope: 'station', to: '/station/manage/mailing', labelKey: 'sidebar.stationMailing', icon: 'envelope', permission: StationPermission.STATION_MAIL},
    {scope: 'station', to: '/station/manage/modules', labelKey: 'sidebar.stationModules', icon: 'puzzle-piece', permission: StationPermission.STATION_MODULES},
    {scope: 'station', to: '/station/manage/import', labelKey: 'sidebar.stationImport', icon: 'file-import', permission: StationPermission.STATION_IMPORT_EXPORT},
    {scope: 'station', to: '/station/manage/storage/backend', labelKey: 'sidebar.storageBackend', icon: 'hard-drive', permission: StationPermission.STATION_ADMINISTRATOR},
    {scope: 'station', to: '/station/manage/security', labelKey: 'sidebar.stationSecurity', icon: 'shield', permission: StationPermission.STATION_ADMINISTRATOR},

    {scope: 'station', to: '/station/monitoring/traffic', labelKey: 'sidebar.stationTraffic', icon: 'tower-broadcast', permission: StationPermission.STATION_ADMINISTRATOR},
    {scope: 'station', to: '/station/monitoring/insights', labelKey: 'sidebar.stationInsights', icon: 'chart-pie', permission: StationPermission.STATION_ADMINISTRATOR},
    {scope: 'station', to: '/station/monitoring/feeds', labelKey: 'sidebar.stationFeeds', icon: 'rss', permission: StationPermission.STATION_ADMINISTRATOR},
    {scope: 'station', to: '/station/monitoring/storage', labelKey: 'sidebar.storage', icon: 'hard-drive', permission: StationPermission.STATION_MANAGER},

    {scope: 'station', to: '/station/federate', labelKey: 'sidebar.federation', icon: 'arrow-right-arrow-left', permission: StationPermission.STATION_FEDERATION},
    {scope: 'station', to: '/station/federate/settings', labelKey: 'sidebar.federationSettings', icon: 'gear', permission: StationPermission.STATION_FEDERATION},
    {scope: 'station', to: '/station/federate/discovery', labelKey: 'sidebar.discovery', icon: 'compass', permission: StationPermission.STATION_FEDERATION},

    {scope: 'admin', to: '/admin/dashboard/overview', labelKey: 'sidebar.overview', icon: 'house'},
    {scope: 'admin', to: '/admin/dashboard/statistics', labelKey: 'sidebar.statistics', icon: 'chart-line'},
    {scope: 'admin', to: '/admin/stations', labelKey: 'sidebar.manageStations', icon: 'building'},
    {scope: 'admin', to: '/admin/stations/applications', labelKey: 'sidebar.applications', icon: 'clipboard-list'},
    {scope: 'admin', to: '/admin/settings', labelKey: 'sidebar.general', icon: 'sliders'},
    {scope: 'admin', to: '/admin/settings/mailing', labelKey: 'sidebar.mailing', icon: 'envelope'},
    {scope: 'admin', to: '/admin/settings/security', labelKey: 'sidebar.security', icon: 'shield'},
    {scope: 'admin', to: '/admin/settings/security/tokens', labelKey: 'sidebar.securityTokens', icon: 'key'},
    {scope: 'admin', to: '/admin/settings/security/hibp', labelKey: 'sidebar.securityHibp', icon: 'user-shield'},
    {scope: 'admin', to: '/admin/settings/security/two-factor', labelKey: 'sidebar.securityTwoFactor', icon: 'mobile-screen'},
    {scope: 'admin', to: '/admin/settings/legal', labelKey: 'sidebar.legal', icon: 'scale-balanced'},
    {scope: 'admin', to: '/admin/2fa', labelKey: 'sidebar.twoFactor', icon: 'mobile-screen'},
    {scope: 'admin', to: '/admin/monitoring/storage', labelKey: 'sidebar.storageDashboard', icon: 'hard-drive'},
    {scope: 'admin', to: '/admin/monitoring/storage/backend', labelKey: 'sidebar.storageBackend', icon: 'hard-drive'},
    {scope: 'admin', to: '/admin/monitoring/storage/audit', labelKey: 'sidebar.storageAudit', icon: 'list-check'},
    {scope: 'admin', to: '/admin/monitoring/problems', labelKey: 'sidebar.problemLog', icon: 'bug'},
    {scope: 'admin', to: '/admin/monitoring/problem-reports', labelKey: 'sidebar.problemReports', icon: 'flag'},
    {scope: 'admin', to: '/admin/monitoring/api-status', labelKey: 'sidebar.apiStatus', icon: 'chart-line'},
    {scope: 'admin', to: '/admin/monitoring/feed-metrics', labelKey: 'sidebar.feedMetrics', icon: 'rss'},
    {scope: 'admin', to: '/admin/monitoring/traffic', labelKey: 'sidebar.adminTraffic', icon: 'tower-broadcast'},
    {scope: 'admin', to: '/admin/monitoring/discovery', labelKey: 'sidebar.adminDiscovery', icon: 'compass'},
    {scope: 'admin', to: '/admin/monitoring/maps', labelKey: 'sidebar.maps', icon: 'map-location-dot'},
    {scope: 'admin', to: '/admin/dev/data-tracking', labelKey: 'sidebar.dataTracking', icon: 'database'},

    // The association's own pages, gated on what it granted rather than on any station's rights
    {scope: 'cluster', to: '/cluster', labelKey: 'clusterSidebar.overview', icon: 'house'},
    {scope: 'cluster', to: '/cluster/settings', labelKey: 'clusterSidebar.settings', icon: 'gear',
        clusterPermission: ClusterPermission.CLUSTER_GENERAL},
    {scope: 'cluster', to: '/cluster/stations', labelKey: 'clusterSidebar.stationList', icon: 'building',
        clusterPermission: ClusterPermission.CLUSTER_STATIONS},
    {scope: 'cluster', to: '/cluster/applications', labelKey: 'clusterSidebar.applications',
        icon: 'clipboard-list', clusterPermission: ClusterPermission.CLUSTER_STATIONS},
    {scope: 'cluster', to: '/cluster/members', labelKey: 'clusterSidebar.memberList', icon: 'users',
        clusterPermission: ClusterPermission.CLUSTER_MEMBER_READ},
    {scope: 'cluster', to: '/cluster/members/manage', labelKey: 'clusterSidebar.memberManagement',
        icon: 'users-gear', clusterPermission: ClusterPermission.CLUSTER_MEMBER_MANAGER},
    {scope: 'cluster', to: '/cluster/knowledge', labelKey: 'clusterSidebar.knowledge', icon: 'book'},
    {scope: 'cluster', to: '/cluster/news', labelKey: 'clusterSidebar.news', icon: 'newspaper'},
    {scope: 'cluster', to: '/cluster/events', labelKey: 'clusterSidebar.events', icon: 'calendar'},
    {scope: 'cluster', to: '/cluster/inventory', labelKey: 'clusterSidebar.stock', icon: 'boxes-stacked',
        clusterPermission: ClusterPermission.CLUSTER_INVENTORY_READ},
    {scope: 'cluster', to: '/cluster/inventory/movements', labelKey: 'clusterSidebar.movements',
        icon: 'right-left', clusterPermission: ClusterPermission.CLUSTER_INVENTORY_READ},
    {scope: 'cluster', to: '/cluster/fields', labelKey: 'clusterSidebar.fieldList', icon: 'id-card',
        clusterPermission: ClusterPermission.CLUSTER_MEMBER_READ},
    {scope: 'cluster', to: '/cluster/modules', labelKey: 'clusterSidebar.modules', icon: 'puzzle-piece',
        clusterPermission: ClusterPermission.CLUSTER_MODULES},
    {scope: 'cluster', to: '/cluster/look-and-feel', labelKey: 'clusterSidebar.lookAndFeel', icon: 'palette',
        clusterPermission: ClusterPermission.CLUSTER_LOOK_AND_FEEL},
    {scope: 'cluster', to: '/cluster/storage', labelKey: 'clusterSidebar.storage', icon: 'hard-drive',
        clusterPermission: ClusterPermission.CLUSTER_STORAGE},
]
